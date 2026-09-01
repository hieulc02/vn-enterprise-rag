from typing import Callable, Awaitable, Any


from parsers.base import BaseParser
from models.domain import ParsedBlock

DocumentHandler = Callable[[Any, dict[int, int]], Awaitable[list[ParsedBlock]]]


class ParserRegistry:

    def __init__(self):
        self._parsers: dict[str, BaseParser] = {}
        self._handlers: dict[str, DocumentHandler] = {}

    def register(self, doc_type: str, parser: BaseParser, handler: DocumentHandler):
        self._parsers[doc_type] = parser
        self._handlers[doc_type] = handler

    def get_pipeline(self, doc_type: str) -> tuple[BaseParser, DocumentHandler]:
        if doc_type not in self._parsers:
            raise KeyError(f"No pipeline registered for document type: {doc_type}")

        return self._parsers[doc_type], self._handlers[doc_type]

    def has_type(self, doc_type: str) -> bool:
        return doc_type in self._parsers


import re
import uuid
import numpy as np

from collections import defaultdict

from unidecode import unidecode

from models.entity import Entity
from models.extraction import PropertyItem


class EntityRegistry:

    ENTITY_NAMESPACE = uuid.UUID("c6dc584d-460a-4ef5-80e5-c8617841259c")
    RELATIONSHIP_NAMESPACE = uuid.UUID("7a2b9c31-4e8f-4d6a-9b12-8c5e3f2a1d9b")
    _INITIAL_CAPACITY = 1000

    def __init__(self):
        self.entities: dict[str, Entity] = {}
        self.type_to_indices = defaultdict(list)
        self._uids: list[str] = []
        self._matrix_cache: np.ndarray | None = None
        self._current_size: int = 0

    @staticmethod
    def normalize_string(text: str) -> str:
        if not text:
            return ""

        text = unidecode(text)
        text = re.sub(r"[^\w\s]", "", text).upper()
        return re.sub(r"\s+", " ", text).strip()

    def _generate_entity_id(
        self, name: str, entity_type: str, props_dict: dict[str, str]
    ):

        norm_name = self.normalize_string(name).lower()
        norm_type = self.normalize_string(entity_type).lower()

        sorted_props = [
            str(value).strip().lower()
            for _, value in sorted(props_dict.items())
            if isinstance(value, str)
        ]

        id_components = [norm_name, norm_type] + sorted_props
        id_string = "|".join(filter(None, id_components))

        return str(uuid.uuid5(self.ENTITY_NAMESPACE, id_string))

    def add_entity(
        self,
        name: str,
        entity_type: str,
        labels: list[str],
        entity_description: str,
        chunk_id: str,
        normalized_embedding: np.ndarray,
        properties: list[PropertyItem],
    ) -> str:
        properties_list = properties or []

        if normalized_embedding is not None:
            properties_list.append(
                PropertyItem(key="embedding", value=list(normalized_embedding))
            )
        entity_properties_dict = {prop.key: prop.value for prop in properties_list}

        new_uid = self._generate_entity_id(
            name=name,
            entity_type=entity_type,
            props_dict=entity_properties_dict,
        )

        entity = Entity(
            id=new_uid,
            short_id=None,
            title=name,
            labels=labels,
            description=entity_description,
            source_chunk_ids={chunk_id},
            properties=entity_properties_dict,
        )

        self.entities[new_uid] = entity

        current_idx = self._current_size
        self._uids.append(new_uid)

        if normalized_embedding is not None:
            if self._matrix_cache is None:
                embed_dim = normalized_embedding.shape[0]
                self._matrix_cache = np.zeros((self._INITIAL_CAPACITY, embed_dim))
            elif current_idx >= len(self._matrix_cache):
                new_matrix = np.zeros(
                    (len(self._matrix_cache) * 2, self._matrix_cache.shape[1])
                )
                new_matrix[:current_idx] = self._matrix_cache
                self._matrix_cache = new_matrix

            self._matrix_cache[current_idx] = normalized_embedding

        self._current_size += 1
        self.type_to_indices[entity_type].append(current_idx)

        return new_uid

    @staticmethod
    def _is_better_entity_name(
        current_name: str | None, candidate_name: str | None
    ) -> bool:

        def score(name: str):
            if not name:
                return (0, 0, 0)
            return (len(name.split()), len(name), sum(1 for c in name if c.isupper()))

        return score(candidate_name) > score(current_name)

    def add_alias(self, uid: str, alias_name: str, chunk_id: str) -> bool:
        title_upgraded = False

        if not alias_name:
            return title_upgraded

        if entity := self.entities.get(uid):
            if self._is_better_entity_name(entity.title, alias_name):
                entity.aliases.add(entity.title)
                entity.title = alias_name
                title_upgraded = True
            elif alias_name != entity.title:
                if alias_name not in entity.aliases:
                    entity.aliases.add(alias_name)

            if chunk_id not in entity.source_chunk_ids:
                entity.source_chunk_ids.add(chunk_id)

        return title_upgraded

    @staticmethod
    def _normalize_vector(embedding: np.ndarray) -> np.ndarray:
        return embedding / (np.linalg.norm(embedding) + 1e-10)

    def search_vector_candidates(
        self, normalized_embedding: np.ndarray, entity_type: str, top_k: int = 3
    ) -> list[tuple[str, float]]:
        if not self._uids or normalized_embedding is None:
            return []

        valid_indices = self.type_to_indices[entity_type]

        sub_matrix = self._matrix_cache[valid_indices]

        scores = np.dot(sub_matrix, normalized_embedding)

        k = min(top_k, len(scores))
        if k == 0:
            return []
        # re-arrange and sort k-items indices to the right and slice it off
        top_k_sub_indices = np.argpartition(scores, -k)[-k:]
        # grap the scores, sort in descending
        top_k_sub_indices = top_k_sub_indices[np.argsort(-scores[top_k_sub_indices])]

        return [
            (self._uids[valid_indices[i]], float(scores[i])) for i in top_k_sub_indices
        ]

    def update_embedding(self, uid: str, new_embedding: np.ndarray):

        if "embedding" in self.entities[uid].properties:
            self.entities[uid].properties["embedding"] = new_embedding.tolist()
        # try:
        #     idx = self._uids.index(uid)

        #     if idx is not None:
        #         norm_embedding = new_embedding / (np.linalg.norm(new_embedding) + 1e-10)
        #         self._matrix_cache[idx] = norm_embedding
        # except ValueError:
        #     pass
