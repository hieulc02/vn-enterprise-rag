import pytest
import orjson as json
from unittest.mock import patch
from app.pipeline.validation import DocumentValidation


@pytest.fixture
def validator(mock_llm, config):
    with patch("os.makedirs"), patch("os.chmod"):
        val = DocumentValidation(llm_provider=mock_llm, config=config)
        val.cache.clear()
        yield val


class TestDocumentValidation:

    def test_init_sets_directory_permissions(self, mock_llm, config):
        with patch("os.makedirs") as mock_makedirs, patch("os.chmod") as mock_chmod:
            DocumentValidation(llm_provider=mock_llm, config=config)

            mock_makedirs.assert_called_once_with(config.cached_dir, exist_ok=True)
            import stat

            mock_chmod.assert_called_once_with(config.cached_dir, stat.S_IRWXU)

    def test_generate_cache_key_ignores_id(self, validator):
        item1 = {"id": "doc1_sec1", "text": "Introduction"}
        item2 = {"id": "doc1_sec5", "text": "Introduction"}

        key1 = validator._generate_cache_key("prompt", item1)
        key2 = validator._generate_cache_key("prompt", item2)

        assert key1 == key2

    @pytest.mark.asyncio
    async def test_validate_batching_logic(self, validator, mock_llm):
        items = [{"id": str(i), "text": f"text_{i}"} for i in range(5)]

        async def mock_generate(*args, **kwargs):
            chunk = json.loads(kwargs["contents"])
            return {item["id"]: True for item in chunk}

        mock_llm.generate_json.side_effect = mock_generate

        result = await validator.validate_all("prompt", items)
        assert mock_llm.generate_json.call_count == 3

        assert len(result) == 5

    @pytest.mark.asyncio
    async def test_validate_partial_cache_hits(self, validator, mock_llm):
        item_cached = {"id": "1", "text": "Cached Text"}
        item_miss = {"id": "2", "text": "Miss Text"}

        cache_key_1 = validator._generate_cache_key("prompt", item_cached)
        validator.cache.set(cache_key_1, True)

        mock_llm.generate_json.return_value = {"2": False}

        result = await validator.validate_all("prompt", [item_cached, item_miss])

        mock_llm.generate_json.assert_called_once()

        llm_contents = json.loads(mock_llm.generate_json.call_args.kwargs["contents"])
        assert len(llm_contents) == 1
        assert llm_contents[0]["id"] == "2"
        assert result == {"1": True, "2": False}

    @pytest.mark.asyncio
    async def test_validate_fallback_when_llm_calls_fail(
        self, validator, mock_llm, caplog
    ):
        items = [{"id": "bad1", "text": "fail"}, {"id": "bad2", "text": "fail"}]

        mock_llm.generate_json.side_effect = Exception("LLM outage")

        result = await validator.validate_all("prompt", items)

        assert mock_llm.generate_json.call_count == validator.config.max_retries
        assert result == {"bad1": True, "bad2": True}
        assert "Document validation failed" in caplog.text

    @pytest.mark.asyncio
    async def test_validate_empty_list_return_empty_dict(self, validator, mock_llm):
        result = await validator.validate_all("prompt", [])
        assert result == {}
        mock_llm.generate_json.assert_not_called()
