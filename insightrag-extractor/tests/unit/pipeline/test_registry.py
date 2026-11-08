import pytest

import numpy as np

from app.pipeline.registry import EntityRegistry
from app.models.extraction import PropertyItem


@pytest.fixture
def entity_registry():
    return EntityRegistry()


@pytest.fixture
def dummy_embedding():
    return np.array([0.1, 0.5, 0.9])


@pytest.fixture
def populated_registry():

    registry = EntityRegistry()

    v_perfect = np.array([1.0, 0.0, 0.0])
    v_partial = np.array([0.5, 0.866, 0.0])
    v_zero = np.array([0.0, 1.0, 0.0])

    uid_org1 = registry.add_entity("org_1", "ORGANIZATION", [], "", "c1", v_perfect, [])
    uid_org2 = registry.add_entity("org_2", "ORGANIZATION", [], "", "c2", v_partial, [])
    uid_org3 = registry.add_entity("org_3", "ORGANIZATION", [], "", "c3", v_zero, [])

    uid_p1 = registry.add_entity("p1", "PERSON", [], "", "c4", v_perfect, [])

    uids = {
        "org_perfect": uid_org1,
        "org_partial": uid_org2,
        "org_zero": uid_org3,
        "person_perfect": uid_p1,
    }

    return registry, uids


class TestEntityRegistry:

    @pytest.mark.parametrize(
        "input_str, expected",
        [
            ("Thuế GTGT", "THUE GTGT"),
            ("Mã số: VNĐ_123.456", "MA SO VND_123456"),
            ("  Báo    cáo   \t\t tài \n chính \r  ", "BAO CAO TAI CHINH"),
            (None, ""),
        ],
    )
    def test_normalize_string_edge_case(self, input_str, expected):
        assert EntityRegistry.normalize_string(input_str) == expected

    def test_generate_entity_id_is_deterministic(self, entity_registry, fake):
        name_a = " Cong ty TNHH "
        name_b = "Công ty TNHH"
        entity_type = "ORGANIZATION"

        address_a = "quận 7, TP.HCM"
        address_b = "QUẬN 7, TP.HCM "

        phone = fake.phone_number()

        props_a = {"address": address_a, "phone": phone}
        props_b = {"phone": phone, "address": address_b}

        id_a = entity_registry._generate_entity_id(name_a, entity_type, props_a)
        id_b = entity_registry._generate_entity_id(name_b, entity_type, props_b)

        assert id_a == id_b

    def test_generate_entity_id_structural_collision(self, entity_registry):
        id_a = entity_registry._generate_entity_id(
            "Google", "PERSON", {"company": "Apple"}
        )
        id_b = entity_registry._generate_entity_id(
            "Apple", "PERSON", {"company": "Google"}
        )

        assert id_a != id_b

    def test_add_entity_and_registry_attribute(
        self, entity_registry, dummy_embedding, fake
    ):
        name = fake.company()
        entity_type = "ORGANIZATION"
        phone = fake.phone_number()
        address = fake.address()
        entity_description = f"{name} | {phone} | {address}"
        labels = ["Entity", entity_type]

        chunk_id = "test_chunk_id"

        properties: list[PropertyItem] = [
            PropertyItem(key="phone", value=phone),
            PropertyItem(key="address", value=address),
        ]

        uid = entity_registry.add_entity(
            name=name,
            entity_type=entity_type,
            labels=labels,
            entity_description=entity_description,
            chunk_id=chunk_id,
            normalized_embedding=dummy_embedding,
            properties=properties,
        )

        entity = entity_registry.entities[uid]

        assert entity.id == uid
        assert entity.title == name
        assert entity.labels == labels
        assert chunk_id in entity.source_chunk_ids
        assert entity.properties["phone"] == phone
        assert entity.properties["address"] == address
        assert entity.properties["embedding"] == dummy_embedding.tolist()

        assert uid in entity_registry._uids
        assert entity_registry._current_size == 1
        assert entity_registry.type_to_indices[entity_type] == [0]

        assert entity_registry._matrix_cache is not None
        assert entity_registry._matrix_cache.shape == (
            entity_registry._INITIAL_CAPACITY,
            3,
        )
        assert np.array_equal(entity_registry._matrix_cache[0], dummy_embedding)

    def test_add_entity_when_capacity_reached(
        self, entity_registry, dummy_embedding, fake
    ):
        entity_registry._INITIAL_CAPACITY = 2

        for i in range(2):
            entity_registry.add_entity(
                name=fake.company(),
                entity_type="ORGANIZATION",
                labels=[],
                entity_description=None,
                chunk_id=f"chunk-{i}",
                normalized_embedding=dummy_embedding,
                properties=[],
            )

        assert entity_registry._matrix_cache.shape[0] == 2

        new_embedding = np.array([0.2, 0.6, 1.0])

        entity_registry.add_entity(
            name=fake.company(),
            entity_type="ORGANIZATION",
            labels=[],
            entity_description=None,
            chunk_id=f"chunk-3",
            normalized_embedding=new_embedding,
            properties=[],
        )

        assert entity_registry._matrix_cache.shape[0] == 4
        assert entity_registry._current_size == 3

        assert np.array_equal(entity_registry._matrix_cache[2], new_embedding)
        assert np.array_equal(entity_registry._matrix_cache[3], [0.0, 0.0, 0.0])

    def test_add_entity_without_embedding(self, entity_registry, fake):
        uid = entity_registry.add_entity(
            name=fake.company(),
            entity_type="ORGANIZATION",
            labels=[],
            entity_description=None,
            chunk_id="chunk_test",
            normalized_embedding=None,
            properties=[],
        )

        assert entity_registry._current_size == 1
        assert "embedding" not in entity_registry.entities[uid].properties
        assert entity_registry._matrix_cache is None

    TITLE_UPGRADE_CASES = [
        ("Công ty Cổ phần", "CÔNG TY CỔ PHẦN"),
        ("Tập đoàn Vingroup", "Tập Đoàn VinGroup"),
        ("TNHH-MTV", "TNHH MTV"),
    ]

    TITLE_DOWNGRADE_CASES = [
        ("TNHH MTV", "TNHH-MTV"),
        ("CÔNG TY CỔ PHẦN", "Công ty Cổ phần"),
        (None, ""),
    ]

    @pytest.mark.parametrize("current_name, candidate_name", TITLE_UPGRADE_CASES)
    def test_is_better_entity_name_identifies_upgrades(
        self, entity_registry, current_name, candidate_name
    ):
        entity_registry._is_better_entity_name(
            current_name=current_name, candidate_name=candidate_name
        ) is True

    @pytest.mark.parametrize("current_name, candidate_name", TITLE_DOWNGRADE_CASES)
    def test_is_better_entity_name_reject_downgrades(
        self, entity_registry, current_name, candidate_name
    ):
        entity_registry._is_better_entity_name(
            current_name=current_name, candidate_name=candidate_name
        ) is False

    @pytest.mark.parametrize("current_name, candidate_name", TITLE_UPGRADE_CASES)
    def test_add_alias_and_upgrade_title(
        self, entity_registry, dummy_embedding, current_name, candidate_name
    ):
        uid = entity_registry.add_entity(
            name=current_name,
            entity_type="ORGANIZATION",
            labels=[],
            entity_description=None,
            chunk_id="chunk_1",
            normalized_embedding=dummy_embedding,
            properties=[],
        )
        is_title_upgrade = entity_registry.add_alias(uid, candidate_name, "chunk_2")
        assert is_title_upgrade is True

        entity = entity_registry.entities[uid]

        assert entity_registry.entities[uid].title == candidate_name
        assert current_name in entity.aliases

        assert "chunk_1" in entity.source_chunk_ids
        assert "chunk_2" in entity.source_chunk_ids

    @pytest.mark.parametrize("current_name, candidate_name", TITLE_DOWNGRADE_CASES[:2])
    def test_add_alias_keep_org_title(
        self, entity_registry, dummy_embedding, current_name, candidate_name
    ):
        uid = entity_registry.add_entity(
            name=current_name,
            entity_type="ORGANIZATION",
            labels=[],
            entity_description=None,
            chunk_id="chunk_test",
            normalized_embedding=dummy_embedding,
            properties=[],
        )
        is_title_upgrade = entity_registry.add_alias(uid, candidate_name, "chunk_test")
        assert is_title_upgrade is False

        entity = entity_registry.entities[uid]

        assert entity_registry.entities[uid].title == current_name
        assert candidate_name in entity.aliases

    def test_add_alias_names_deduplication(self, entity_registry, dummy_embedding):

        uid = entity_registry.add_entity(
            name="name_unq",
            entity_type="ORGANIZATION",
            labels=[],
            entity_description=None,
            chunk_id="chunk_test",
            normalized_embedding=dummy_embedding,
            properties=[],
        )

        def add_dupl_aliases(i: int) -> bool:
            return entity_registry.add_alias(
                uid, "name_better_but_dupl", f"chunk_dupl_{i}"
            )

        for i in range(2):
            add_dupl_aliases(i)

        assert len(entity_registry.entities[uid].aliases) == 1
        assert entity_registry.entities[uid].title == "name_better_but_dupl"
        assert entity_registry.entities[uid].aliases == {"name_unq"}

    def test_add_alias_ignores_empty_or_none_value(
        self, entity_registry, dummy_embedding
    ):
        uid = entity_registry.add_entity(
            name="valid name",
            entity_type="ORGANIZATION",
            labels=[],
            entity_description=None,
            chunk_id="chunk_1",
            normalized_embedding=dummy_embedding,
            properties=[],
        )
        result_empty = entity_registry.add_alias(uid, "", "chunk_2")
        result_none = entity_registry.add_alias(uid, None, "chunk_3")

        assert result_empty is False
        assert result_none is False

        entity = entity_registry.entities[uid]

        assert entity_registry.entities[uid].title == "valid name"
        assert "chunk_2" not in entity.aliases

        assert len(entity.aliases) == 0

    def test_add_alias_with_non_exists_uid(self, entity_registry):
        non_exists_uid = "non-exists-uid"
        is_title_upgrade = entity_registry.add_alias(
            non_exists_uid, "candidate_name", "chunk_test"
        )
        assert is_title_upgrade is False

    def test_search_vector_candidates_sorted_by_scores(self, populated_registry):

        registry, uids = populated_registry
        search_vector = np.array([1.0, 0.0, 0.0])

        results = registry.search_vector_candidates(
            search_vector, "ORGANIZATION", top_k=3
        )

        assert len(results) == 3

        assert results[0][0] == uids["org_perfect"]
        assert results[0][1] == pytest.approx(1.0)

        assert results[1][0] == uids["org_partial"]
        assert results[1][1] == pytest.approx(0.5)

        assert results[2][0] == uids["org_zero"]
        assert results[2][1] == pytest.approx(0.0)

    def test_search_vector_isolates_by_entity_type(self, populated_registry):

        registry, uids = populated_registry
        search_vector = np.array([1.0, 0.0, 0.0])

        results = registry.search_vector_candidates(search_vector, "PERSON", top_k=3)

        assert len(results) == 1
        assert results[0][0] == uids["person_perfect"]
        assert results[0][1] == pytest.approx(1.0)

    def test_search_vector_exceed_available_entities(self, populated_registry):

        registry, _ = populated_registry
        search_vector = np.array([1.0, 0.0, 0.0])

        results = registry.search_vector_candidates(
            search_vector, "ORGANIZATION", top_k=10
        )

        assert len(results) == 3

    def test_search_vector_with_none_value_vector(self, populated_registry):
        registry, _ = populated_registry

        results = registry.search_vector_candidates(None, "ORGANIZATION", top_k=3)

        assert results == []
