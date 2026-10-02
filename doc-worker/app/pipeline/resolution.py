import asyncio
import logging
import uuid
import zlib

from collections import defaultdict
from collections.abc import Iterable

from typing import Any, Callable

import numpy as np

from embedding.transformer import Embedder
from pipeline.adjudicator import EntityAdjudicator

from models.chunk import DocumentChunk
from models.extraction import (
    LocalRelationship,
    TableContext,
    TableRowDTO,
    TableCell,
    GraphEntity,
    ChunkGraph,
)
from models.entity import Entity
from models.relationship import Relationship
from models.graph import DocumentGraph
from models.adjudicator import EntitiesAdjudicator
from models.resolution import EntityInstance
from models.extraction import PropertyItem

from pipeline.registry import EntityRegistry

from utils.string import parse_numeric_value

logger = logging.getLogger(__name__)


class EntityResolutionPipeline:

    def __init__(
        self,
        registry: EntityRegistry,
        embedder: Embedder,
        adjudicator: EntityAdjudicator,
    ):
        self.registry = registry
        self.embedder = embedder
        self.adjudicator = adjudicator

    def _generate_relationship_id(self, source_id: str, target_id: str, rel_type: str):
        canonical_string = f"{source_id}:{target_id}:{rel_type}"
        return str(uuid.uuid5(self.registry.RELATIONSHIP_NAMESPACE, canonical_string))

    def _process_orphan_entities(
        self, entities: list[Entity], connected_entity_ids: set[str]
    ):
        return [
            entity
            for entity in entities
            if entity.properties or entity.id in connected_entity_ids
        ]

    @staticmethod
    def _gen_entity_context(context: str, properties: dict) -> str:
        entity_context: list[str] = []

        if context:
            entity_context.append(context)
        elif properties:
            entity_context.extend(
                value for _, value in properties.items() if isinstance(value, str)
            )

        return " | ".join(entity_context)

    @staticmethod
    def _gen_relationship_context(
        source_context: str,
        edge_context: str,
        target_context: str,
        properties: dict,
    ) -> str:
        relationship_context: list[str] = []

        if source_context:
            relationship_context.append(source_context)
        if edge_context:
            relationship_context.append(edge_context)
        if target_context:
            relationship_context.append(target_context)
        if properties:
            relationship_context.extend(
                value for _, value in properties.items() if isinstance(value, str)
            )

        return " | ".join(relationship_context)

    async def _consolidate_graph(
        self,
        file_key: str,
        chunks: list[DocumentChunk],
        extracted_chunks: list[ChunkGraph],
        chunk_id_map: dict[str, dict[str, str]],
    ):
        merged_relationships: dict[tuple, Relationship] = {}
        connected_entity_ids: set[str] = set()

        entities: list[Entity] = list(self.registry.entities.values())
        relationships: list[Relationship] = []

        for extracted_chunk in extracted_chunks:
            chunk_id = extracted_chunk.chunk_id
            for relationship in extracted_chunk.relationships:

                global_source = chunk_id_map[chunk_id].get(relationship.source_local_id)
                global_target = chunk_id_map[chunk_id].get(relationship.target_local_id)

                if not (global_source and global_target):
                    continue

                connected_entity_ids.add(global_source)
                connected_entity_ids.add(global_target)

                relationship_properties_dict = (
                    {prop.key: prop.value for prop in relationship.properties}
                    if relationship.properties
                    else {}
                )

                source_entity = self.registry.entities.get(global_source)
                target_entity = self.registry.entities.get(global_target)

                relationship.description = self._gen_relationship_context(
                    source_entity.title if source_entity else "",
                    relationship.description if relationship.description else "",
                    target_entity.title if target_entity else "",
                    relationship_properties_dict,
                )

                edge_key = (global_source, global_target, relationship.type)

                if edge_key not in merged_relationships:
                    merged_relationships[edge_key] = Relationship(
                        id=self._generate_relationship_id(
                            global_source, global_target, relationship.type
                        ),
                        short_id=None,
                        source=global_source,
                        target=global_target,
                        type=relationship.type,
                        description=relationship.description,
                        source_chunk_ids=[chunk_id],
                        properties=relationship_properties_dict,
                    )

                else:
                    merged_relationship = merged_relationships[edge_key]
                    merged_relationship.source_chunk_ids.add(chunk_id)
                    merged_relationship.properties.update(relationship_properties_dict)

                    if (
                        merged_relationship.description
                        and relationship.description
                        not in merged_relationship.description
                    ):
                        merged_relationship.description = f"{merged_relationship.description} | {relationship.description}"
                    else:
                        merged_relationship.description = relationship.description

        relationships = list(merged_relationships.values())

        await self._batch_embed_relationships_description(relationships)

        entities = self._process_orphan_entities(entities, connected_entity_ids)

        return DocumentGraph(
            document_id=file_key,
            chunks=chunks,
            entities=entities,
            relationships=relationships,
        )

    async def _batch_embed_relationships_description(
        self, relationships: list[Relationship]
    ):
        valid_relationships = [
            relationship for relationship in relationships if relationship.description
        ]

        new_embeddings = await self.embedder.aembed(
            [relationship.description for relationship in valid_relationships]
        )

        for valid_relationship, embedding in zip(valid_relationships, new_embeddings):
            valid_relationship.properties["embedding"] = embedding.tolist()

    async def _batch_update_embeddings(self, dirty_uids: set[str]):
        if not dirty_uids:
            return

        dirty_uids_list = list(dirty_uids)

        updated_entities = [self.registry.entities[uid] for uid in dirty_uids_list]
        for entity in updated_entities:
            if not entity.description.startswith(f"{entity.title} :"):
                entity.description = f"{entity.title} : {entity.description}"

        updated_contexts = [
            self._gen_entity_context(entity.description, entity.properties)
            for entity in updated_entities
        ]

        new_embeddings = await self.embedder.aembed(
            updated_contexts, norm_embedding=True
        )

        for uid, embedding in zip(dirty_uids_list, new_embeddings):
            self.registry.update_embedding(uid, embedding)

    async def resolve_entities(
        self,
        file_key: str,
        chunk_graphs: list[ChunkGraph],
        chunks: list[DocumentChunk],
        auto_merge_threshold: float = 0.93,
        llm_review_threshold: float = 0.82,
    ):

        chunk_id_map: dict[str, dict[str, str]] = {
            chunk.chunk_id: {} for chunk in chunks
        }
        chunk_texts: dict[str, str] = {chunk.chunk_id: chunk.text for chunk in chunks}
        dirty_set: set[str] = set()

        group_entities, entity_instances = await asyncio.to_thread(
            self._extract_and_group_entities, chunk_graphs
        )

        if not group_entities:
            return await self._consolidate_graph(
                file_key, chunks, chunk_graphs, chunk_id_map
            )

        await self._embed_instances(entity_instances)

        llm_tasks, review_items = await self._route_entity_groups(
            group_entities=group_entities,
            chunk_id_map=chunk_id_map,
            chunk_texts=chunk_texts,
            dirty_set=dirty_set,
            auto_merge_threshold=auto_merge_threshold,
            llm_review_threshold=llm_review_threshold,
        )

        if llm_tasks:
            await self._process_llm_adjudicate(
                llm_tasks, review_items, chunk_id_map, dirty_set
            )

        await self._batch_update_embeddings(dirty_set)
        return await self._consolidate_graph(
            file_key, chunks, chunk_graphs, chunk_id_map
        )

    async def _route_entity_groups(
        self,
        group_entities: dict[str, list[EntityInstance]],
        chunk_id_map: dict[str, dict[str, str]],
        chunk_texts: dict[str, str],
        dirty_set: set[str],
        auto_merge_threshold: float,
        llm_review_threshold: float,
    ):
        llm_tasks = []
        review_items = []

        for norm_entity, instances in group_entities.items():

            await asyncio.sleep(0)

            first_instance = instances[0]

            if (
                first_instance.entity.type == "Concept"
                and first_instance.entity.custom_type
            ):
                entity_type = first_instance.entity.custom_type
            else:
                entity_type = first_instance.entity.type

            if (
                entity_type not in self.registry.type_to_indices
                or first_instance.entity.is_tabular_data
            ):
                new_uuid = self._create_entity(
                    instances, entity_type, first_instance.norm_embedding
                )
                await self._map_entity_props(
                    instances, new_uuid, chunk_id_map, dirty_set
                )
                continue

            candidates = self.registry.search_vector_candidates(
                first_instance.norm_embedding, entity_type, top_k=1
            )

            if not candidates:
                new_uuid = self._create_entity(
                    instances, entity_type, first_instance.norm_embedding
                )
                await self._map_entity_props(
                    instances, new_uuid, chunk_id_map, dirty_set
                )
                continue

            best_uid, best_score = candidates[0]
            logger.debug(
                f"Candidate match for {norm_entity}: {best_uid} (Score: {best_score})"
            )

            if best_score >= auto_merge_threshold:
                await self._map_entity_props(
                    instances, best_uid, chunk_id_map, dirty_set
                )
            elif llm_review_threshold <= best_score < auto_merge_threshold:
                candidate_entity = self.registry.entities[best_uid]
                candidate_context = self._gen_entity_context(
                    candidate_entity.description, candidate_entity.properties
                )

                candidate_texts = "\n".join(
                    [
                        chunk_texts.get(cid, "")
                        for cid in candidate_entity.source_chunk_ids
                    ]
                )

                task = self.adjudicator.adjudicate(
                    entity_a_name=first_instance.context,
                    entity_a_context=chunk_texts.get(first_instance.chunk_id, ""),
                    entity_b_name=candidate_context,
                    entity_b_context=candidate_texts,
                )

                llm_tasks.append(task)
                review_items.append(
                    {
                        "instances": instances,
                        "raw_cont": first_instance.context,
                        "embedding": first_instance.norm_embedding,
                        "type": entity_type,
                        "candidate": best_uid,
                    }
                )
            else:
                new_uuid = self._create_entity(
                    instances, entity_type, first_instance.norm_embedding
                )
                await self._map_entity_props(
                    instances, new_uuid, chunk_id_map, dirty_set
                )

        return llm_tasks, review_items

    def _extract_and_group_entities(
        self, chunk_graphs: list[ChunkGraph]
    ) -> tuple[dict[str, list[EntityInstance]], list[EntityInstance]]:

        group_entities = defaultdict(list)
        entity_instances: list[EntityInstance] = []

        for chunk_graph in chunk_graphs:
            chunk_id = chunk_graph.chunk_id

            if chunk_graph.contains_table:
                tabular_entities, tabular_relationships = extract_table_to_entities(
                    chunk_graph
                )
                chunk_graph.entities.extend(tabular_entities)
                chunk_graph.relationships.extend(tabular_relationships)

            for entity in chunk_graph.entities:

                if not entity.description:
                    entity.description = entity.gen_fallback_description()

                entity_properties_dict = {
                    prop.key: prop.value for prop in entity.properties
                }

                entity_delegate = entity.name if entity.name else entity.description
                entity_delegate = self._gen_entity_context(
                    entity_delegate, entity_properties_dict
                )

                norm_entity = self.registry.normalize_string(entity_delegate)

                entity_context = entity.description if entity.description else None

                entity_instance = EntityInstance(
                    chunk_id=chunk_id,
                    norm_entity=norm_entity,
                    entity=entity,
                    context=entity_context,
                )
                if not entity.is_tabular_data:
                    entity_instances.append(entity_instance)
                group_entities[norm_entity].append(entity_instance)

        return group_entities, entity_instances

    def _create_entity(
        self,
        instances: list[EntityInstance],
        entity_type: str,
        embedding: np.ndarray,
    ) -> str:

        first_instance = instances[0]
        first_entity = first_instance.entity

        labels = first_entity.labels or []
        if not labels:
            labels.append("Entity")
            if entity_type:
                entity_type = entity_type.replace(" ", "")
                labels.append(entity_type)

        return self.registry.add_entity(
            name=first_entity.name,
            entity_type=entity_type,
            labels=labels,
            entity_description=first_entity.description,
            chunk_id=first_instance.chunk_id,
            normalized_embedding=embedding,
            properties=first_entity.properties,
        )

    @staticmethod
    def _should_add_new_entity_description(
        similarity_score: float,
        description_merge_threshold: float = 0.93,
    ) -> bool:
        return similarity_score < description_merge_threshold

    @staticmethod
    def _merge_description(desc_components: list[str]) -> str | None:
        cleaned_desc = [d.strip().rstrip(".") for d in desc_components]

        if not cleaned_desc:
            return None

        return " | ".join(cleaned_desc)

    @staticmethod
    def _evaluate_similarity_context(
        seen_contexts_list: list[np.ndarray], target_embedding: np.ndarray
    ) -> float:
        if not seen_contexts_list:
            return 0.0

        seen_contexts_matrix = np.array(seen_contexts_list)
        return float(max(np.dot(seen_contexts_matrix, target_embedding)))

    async def _map_entity_props(
        self,
        instances: list[EntityInstance],
        uid: str,
        chunk_id_map: dict,
        dirty_set: set[str],
    ):
        is_dirty = False
        target_entity = self.registry.entities[uid]
        target_embedding = target_entity.properties.get("embedding", [])

        desc_components: list[str] = (
            [target_entity.description] if target_entity.description else []
        )
        seen_embeddings_list: list[np.ndarray] = []
        if target_embedding:
            seen_embeddings_list.append(np.asarray(target_embedding, dtype=np.float32))

        for instance in instances:

            await asyncio.sleep(0)

            chunk_id_map[instance.chunk_id][instance.entity.local_id] = uid

            if merge_entity_properties(
                target_entity.properties,
                instance.entity.properties,
                self.registry.normalize_string,
            ):
                is_dirty = True

            if instance.norm_embedding is not None:
                instance_embedding = np.asarray(
                    instance.norm_embedding, dtype=np.float32
                )

                if instance.context and instance.context not in desc_components:
                    similarity_score = await asyncio.to_thread(
                        self._evaluate_similarity_context,
                        seen_embeddings_list,
                        instance_embedding,
                    )

                    if self._should_add_new_entity_description(similarity_score):
                        desc_components.append(instance.context)
                        is_dirty = True

                    seen_embeddings_list.append(instance_embedding)

            if instance.entity.name and self.registry.add_alias(
                uid, instance.entity.name, instance.chunk_id
            ):
                is_dirty = True

        if is_dirty:
            new_description = self._merge_description(desc_components)
            if target_entity.description != new_description:
                target_entity.description = new_description
                dirty_set.add(uid)

    async def _embed_instances(self, entity_instances: list[EntityInstance]):
        valid_instances = [
            instance for instance in entity_instances if instance.context
        ]

        if not valid_instances:
            return

        try:
            norm_embeddings = await self.embedder.aembed(
                [instance.context for instance in valid_instances],
                norm_embedding=True,
            )

            for instance, embedding in zip(valid_instances, norm_embeddings):
                instance.norm_embedding = embedding
        except Exception as e:
            logger.error(
                f"Critical Error: Failed to embed {len(valid_instances)} entity instances: {e}"
            )
            raise

    async def _process_llm_adjudicate(
        self,
        llm_tasks: list,
        review_items: list,
        chunk_id_map: dict,
        dirty_set: set[str],
    ):

        llm_results: list[EntitiesAdjudicator] = await asyncio.gather(
            *llm_tasks, return_exceptions=True
        )

        for review_item, llm_result in zip(review_items, llm_results):
            instances = review_item["instances"]

            if isinstance(llm_result, Exception):
                logger.error(
                    f"Adjudication failed for {review_item['raw_cont']}: {llm_result}"
                )
                llm_result = None

            if llm_result and llm_result.is_same_entity:
                await self._map_entity_props(
                    instances, review_item["candidate"], chunk_id_map, dirty_set
                )
            else:
                new_uid = self._create_entity(
                    instances, review_item["type"], review_item["embedding"]
                )
                await self._map_entity_props(
                    instances, new_uid, chunk_id_map, dirty_set
                )


def merge_entity_properties(
    target_dict: dict[str, Any],
    new_properties: Iterable[Any],
    normalized_string: Callable[[str], str],
) -> bool:
    is_dirty = False

    existing_normalized_values = {
        normalized_string(value)
        for value in list(target_dict.values())
        if isinstance(value, str)
    }

    for prop in new_properties:
        key, value = prop.key, prop.value

        if not isinstance(value, str):
            if key not in target_dict or target_dict[key] != value:
                target_dict[key] = value
                is_dirty = True
            continue

        norm_value = normalized_string(value)

        if norm_value in existing_normalized_values:
            continue

        if key in target_dict:
            target_dict[key] = f"{target_dict[key]} | {value}"
        else:
            target_dict[key] = value

        existing_normalized_values.add(norm_value)
        is_dirty = True

    return is_dirty


def _gen_local_id(val: str):
    return abs(zlib.crc32(val.encode("utf-8")))


def _create_cell_entity(
    cell: TableCell,
    table_context: str,
    row_item: str,
    cell_local_id: str,
    clean_value: Any,
) -> tuple[GraphEntity, str]:
    properties: list[PropertyItem] = [
        PropertyItem(key="value", value=clean_value),
    ]

    labels: list[str] = []

    cell_label = (
        cell.custom_header_type if cell.header_type == "Concept" else cell.header_type
    )

    if table_context:
        properties.append(PropertyItem(key="table_context", value=table_context))

    if row_item:
        properties.append(PropertyItem(key="row_context", value=row_item))

    if cell.column_header:
        properties.append(PropertyItem(key="column_context", value=cell.column_header))

    if cell.is_numeric:
        labels.append("Observation")
        if cell_label:
            labels.append(cell_label)

        properties.append(PropertyItem(key="is_numeric", value=cell.is_numeric))
        cell_relationship_type = "HAS_OBSERVATION"
    else:
        if cell_label:
            labels.append(cell_label)
        cell_relationship_type = "RELATED_TO"

    entity = GraphEntity(
        local_id=cell_local_id,
        type=cell.header_type,
        custom_type=cell.custom_header_type,
        properties=properties,
        labels=labels,
        is_tabular_data=True,
    )

    return entity, cell_relationship_type


def _process_cells(
    row: TableRowDTO,
    chunk_id: str,
    table_context: str,
    row_local_id: str,
    entities: dict[str, GraphEntity],
    relationships: dict[str, LocalRelationship],
):
    for cell in row.cells:
        clean_value = parse_numeric_value(cell.value)

        if not cell.is_numeric:
            cell.value.strip()

        if not clean_value and not cell.is_numeric and clean_value != 0.0:
            continue

        cell_str = f"{chunk_id}:{cell.column_header}:{row.row_concept}"
        cell_local_id = f"tb_col_{_gen_local_id(cell_str)}"

        cell_entity, cell_relationship_type = _create_cell_entity(
            cell, table_context, row.row_concept, cell_local_id, clean_value
        )

        entities[cell_local_id] = cell_entity

        cell_rel_id = f"rel_{row_local_id}_{cell_local_id}"
        relationships[cell_rel_id] = LocalRelationship(
            source_local_id=row_local_id,
            target_local_id=cell_local_id,
            type=cell_relationship_type,
            description=row.row_concept,
        )


def _build_row_description(
    row: TableRowDTO, table_context: TableContext, table_name: str
) -> str:

    breadcrumb = f"{table_name} > {table_context.primary_subject}"

    row_identity = f"{row.row_concept}"
    if row.code:
        row_identity = f"{row_identity} (Mã số: {row.code})"
    if row.note_reference:
        row_identity = f"{row_identity} - Thuyết minh: {row.note_reference}"

    cell_strings = [f"{cell.column_header}: {cell.value}" for cell in row.cells]
    cells_serialized = " | ".join(cell_strings)

    return f"{breadcrumb} | {row_identity} | {cells_serialized}"


def _create_row_entity(
    row: TableRowDTO, table_context: TableContext, row_local_id: str
) -> GraphEntity:
    labels: list[str] = ["Subject"]

    table_name = (
        table_context.document_title
        if table_context.document_title
        else table_context.section_heading
    )

    row_description = _build_row_description(row, table_context, table_name)

    primary_label = row.custom_row_type if row.row_type == "Concept" else row.row_type
    if primary_label:
        labels.append(primary_label)

    row_name = f"{table_name} > {row.row_concept}"

    return GraphEntity(
        name=row_name,
        local_id=row_local_id,
        type=row.row_type,
        custom_type=row.custom_row_type,
        description=row_description,
        labels=labels,
        is_tabular_data=True,
    )


def _build_table_description(table_context: TableContext) -> str:

    section_heading = (
        f"{table_context.reference_code}. {table_context.section_heading}"
        if table_context.reference_code
        else table_context.section_heading
    )

    table_components = [
        table_context.document_title,
        table_context.context_modifier,
        section_heading,
        table_context.primary_subject,
    ]

    valid_components = [str(c) for c in table_components if c]
    return " > ".join(valid_components)


def _create_table_context_entity(
    context: TableContext, table_name: str, table_local_id: str
) -> GraphEntity:

    table_description = _build_table_description(context)

    return GraphEntity(
        local_id=table_local_id,
        name=table_name,
        type="Document",
        custom_type=None,
        description=table_description,
        # is_tabular_data=True,
    )


def _fallback_table_relationship(table_local_id: str, chunk: ChunkGraph):

    table_relationship = None

    local_orgs = [
        org_ent for org_ent in chunk.entities if org_ent.type == "Organization"
    ]

    if len(local_orgs) == 1:
        local_relationships = [
            org_rel
            for org_rel in chunk.relationships
            if (
                org_rel.target_local_id == local_orgs[0].local_id
                and org_rel.type == "REPORTED_BY"
            )
        ]

        if not local_relationships:
            table_relationship = LocalRelationship(
                source_local_id=table_local_id,
                target_local_id=local_orgs[0].local_id,
                type="REPORTED_BY",
            )

    return table_relationship


def extract_table_to_entities(
    chunk: ChunkGraph,
) -> tuple[list[GraphEntity], list[LocalRelationship]]:
    if not chunk.tabular_data:
        return [], []

    entities: dict[str, GraphEntity] = {}
    relationships: dict[str, LocalRelationship] = {}
    concept_to_id_map: dict[str, str] = {}

    table_context = chunk.tabular_data.table_context
    chunk_id = chunk.chunk_id

    table_name = (
        table_context.document_title
        if table_context.document_title
        else table_context.section_heading
    )
    table_name = table_name or table_context.primary_subject

    table_local_id = f"tb_context_{_gen_local_id(f"{chunk_id}:{table_name}")}"

    entities[table_local_id] = _create_table_context_entity(
        table_context, table_name, table_local_id
    )

    table_relationship = _fallback_table_relationship(table_local_id, chunk)
    if table_relationship:
        table_rel_id = f"rel_{table_local_id}"
        relationships[table_rel_id] = table_relationship

    for row in chunk.tabular_data.line_items:

        hash_str = f"{row.row_concept}:{row.code}" if row.code else row.row_concept
        row_local_id = f"tb_row_{_gen_local_id(f"{chunk_id}:{table_name}:{hash_str}")}"

        if row.row_concept:
            concept_to_id_map[row.row_concept] = row_local_id

        if row.parent_row_concept and not row.cells:
            parent_local_id = concept_to_id_map.get(row.parent_row_concept)

            if row.custom_row_type:
                new_prop = PropertyItem(key=row.custom_row_type, value=row.row_concept)
            else:
                new_prop = PropertyItem(key=row.row_type, value=row.row_concept)

            if parent_local_id and parent_local_id in entities:
                entities[parent_local_id].properties.append(new_prop)
                continue

        if row_local_id not in entities:
            entities[row_local_id] = _create_row_entity(
                row, table_context, row_local_id
            )

        if row.parent_row_concept:
            parent_local_id = concept_to_id_map.get(row.parent_row_concept)
            relationship_type = "IS_CHILD_OF"
        else:
            parent_local_id = table_local_id
            relationship_type = "COMPONENT_OF"

        if parent_local_id and parent_local_id != row_local_id:
            row_rel_id = f"rel_{row_local_id}_{parent_local_id}"
            if row_rel_id not in relationships:
                relationships[row_rel_id] = LocalRelationship(
                    source_local_id=row_local_id,
                    target_local_id=parent_local_id,
                    type=relationship_type,
                )

        _process_cells(row, chunk_id, table_name, row_local_id, entities, relationships)

    return list(entities.values()), list(relationships.values())
