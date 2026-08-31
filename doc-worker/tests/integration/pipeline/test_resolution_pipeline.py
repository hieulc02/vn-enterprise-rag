import asyncio
import uuid
import pytest

import numpy as np

from unittest.mock import MagicMock, AsyncMock
from app.pipeline.resolution import EntityResolutionPipeline
from app.models.adjudicator import EntitiesAdjudicator
from tests.exceptions.llm_exception import ServiceUnavailableError


@pytest.fixture
def registry():
    registry = MagicMock()
    registry.type_to_indices = {"ORGANIZATION": [1, 2]}
    registry.RELATIONSHIP_NAMESPACE = uuid.UUID("7a2b9c31-4e8f-4d6a-9b12-8c5e3f2a1d9b")
    registry.entities = {}
    return registry


@pytest.fixture
def embeder():
    embeder = MagicMock()
    embeder.aembed = AsyncMock(
        return_value=[np.array([1.0, 0.0]), np.array([0.0, 1.0])]
    )
    return embeder


@pytest.fixture
def adjudicator():
    adjudicator = MagicMock()
    adjudicator.adjudicate = AsyncMock()
    return adjudicator


@pytest.fixture
def pipeline(registry, embeder, adjudicator):
    return EntityResolutionPipeline(
        registry=registry, embedder=embeder, adjudicator=adjudicator
    )


@pytest.mark.asyncio
class TestResolutionPipeline:

    async def test_embed_instances_maps_to_correct_instances(self, pipeline):
        valid_instance_1 = MagicMock(context="text 1", norm_embedding=None)
        valid_instance_2 = MagicMock(context="text 2", norm_embedding=None)
        invalid_instace = MagicMock(context=None, norm_embedding=None)

        instances = [valid_instance_1, valid_instance_2, invalid_instace]

        await pipeline._embed_instances(instances)

        assert valid_instance_1.norm_embedding is not None
        assert valid_instance_2.norm_embedding is not None
        assert invalid_instace.norm_embedding is None

    async def test_route_entity_groups_respects_thresholds(self, pipeline):

        pipeline._map_entity_props = MagicMock()
        pipeline._create_entity = MagicMock(return_value="new-uid")
        pipeline._gen_entity_context = MagicMock(return_value="cand-context")

        group_entities = {
            "auto_merge_entity": [
                MagicMock(
                    entity=MagicMock(type="ORGANIZATION", is_tabular_data=False),
                    norm_embedding=None,
                )
            ],
            "llm_review_entity": [
                MagicMock(
                    entity=MagicMock(
                        type="ORGANIZATION",
                        is_tabular_data=False,
                        context="context",
                        chunk_id="test_chunk",
                    ),
                    norm_embedding=None,
                )
            ],
            "create_entity": [
                MagicMock(
                    entity=MagicMock(type="ORGANIZATION", is_tabular_data=False),
                    norm_embedding=None,
                )
            ],
        }

        pipeline.registry.search_vector_candidates.side_effect = [
            [("uid_1", 0.95)],
            [("uid_2", 0.82)],
            [("uid_3", 0.50)],
        ]
        pipeline.registry.entities = {
            "uid_2": MagicMock(
                description="close_context",
                source_chunk_ids="test_chunk",
                properties={},
            )
        }

        llm_tasks, review_items = pipeline._route_entity_groups(
            group_entities=group_entities,
            chunk_id_map={},
            chunk_texts={},
            dirty_set=set(),
            auto_merge_threshold=0.93,
            llm_review_threshold=0.82,
        )

        pipeline._map_entity_props.assert_any_call(
            group_entities["auto_merge_entity"], "uid_1", {}, set()
        )

        assert len(llm_tasks) == 1
        assert len(review_items) == 1
        assert review_items[0]["candidate"] == "uid_2"

        for task in llm_tasks:
            await task

        pipeline._create_entity.assert_called_with(
            group_entities["create_entity"],
            "ORGANIZATION",
            group_entities["create_entity"][0].norm_embedding,
        )

    async def test_process_llm_adjudicate_handle_failure_gracefully(self, pipeline):

        pipeline._map_entity_props = MagicMock()
        pipeline._create_entity = MagicMock(return_value="new-uid")

        success_task = asyncio.sleep(
            0, result=EntitiesAdjudicator(analysis="Match", is_same_entity=True)
        )

        async def fail_task():
            raise ServiceUnavailableError(503, "LLM Service is unavailable")

        llm_tasks = [success_task, fail_task()]

        review_items = [
            {
                "instances": ["i1"],
                "raw_cont": "success context",
                "embedding": None,
                "type": "ORGANIZATION",
                "candidate": "uid-1",
            },
            {
                "instances": ["i2"],
                "raw_cont": "fail context",
                "embedding": None,
                "type": "ORGANIZATION",
                "candidate": "uid-2",
            },
        ]

        await pipeline._process_llm_adjudicate(llm_tasks, review_items, {}, set())

        pipeline._map_entity_props.assert_any_call(["i1"], "uid-1", {}, set())

        pipeline._create_entity.assert_called_with(["i2"], "ORGANIZATION", None)
        pipeline._map_entity_props.assert_any_call(["i2"], "new-uid", {}, set())

    async def test_consolidate_graph_merges_duplicate_edges(self, pipeline):

        chunk_id_map = {
            "chunk_1": {
                "local_ent_1": "global_ent_1",
                "local_ent_2": "global_ent_2",
            },
            "chunk_2": {
                "local_ent_1": "global_ent_1",
                "local_ent_2": "global_ent_2",
            },
        }

        extracted_chunks = [
            MagicMock(
                chunk_id="chunk_1",
                relationships=[
                    MagicMock(
                        source_local_id="local_ent_1",
                        target_local_id="local_ent_2",
                        type="RELATED_TO",
                        description="rel_desc 1",
                    )
                ],
                properties={},
            ),
            MagicMock(
                chunk_id="chunk_2",
                relationships=[
                    MagicMock(
                        source_local_id="local_ent_1",
                        target_local_id="local_ent_2",
                        type="RELATED_TO",
                        description="rel_desc 2",
                        properties={},
                    )
                ],
            ),
        ]

        graph = await pipeline._consolidate_graph(
            "consolidate_rel", [], extracted_chunks, chunk_id_map
        )

        assert len(graph.relationships) == 1

        merged_relationship = graph.relationships[0]

        assert "chunk_1" in merged_relationship.source_chunk_ids
        assert "chunk_2" in merged_relationship.source_chunk_ids

        assert "rel_desc 1" in merged_relationship.description
        assert "rel_desc 2" in merged_relationship.description

    async def test_consolidate_graph_merges_edges_with_empty_or_null_description(
        self,
        pipeline,
    ):

        chunk_id_map = {
            "chunk_1": {
                "local_ent_1": "global_ent_1",
                "local_ent_2": "global_ent_2",
            },
            "chunk_2": {
                "local_ent_1": "global_ent_1",
                "local_ent_2": "global_ent_2",
            },
        }

        extracted_chunks = [
            MagicMock(
                chunk_id="chunk_1",
                relationships=[
                    MagicMock(
                        source_local_id="local_ent_1",
                        target_local_id="local_ent_2",
                        type="RELATED_TO",
                        description=None,
                    )
                ],
                properties={},
            ),
            MagicMock(
                chunk_id="chunk_2",
                relationships=[
                    MagicMock(
                        source_local_id="local_ent_1",
                        target_local_id="local_ent_2",
                        type="RELATED_TO",
                        description="test_merge_desc",
                        properties={},
                    )
                ],
            ),
        ]

        graph = await pipeline._consolidate_graph(
            "merge_rel", [], extracted_chunks, chunk_id_map
        )

        assert len(graph.relationships) == 1

        merged_relationship = graph.relationships[0]

        assert "test_merge_desc" in merged_relationship.description
