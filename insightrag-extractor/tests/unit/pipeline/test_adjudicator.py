import pytest
import asyncio

from unittest.mock import patch, DEFAULT
from app.pipeline.adjudicator import EntityAdjudicator
from app.models.adjudicator import EntitiesAdjudicator


@pytest.fixture
def adjudicator(mock_llm, config):
    adj = EntityAdjudicator(llm_provider=mock_llm, config=config)
    adj.cache.clear()
    return adj


class TestEntityAdjudicator:

    @pytest.mark.asyncio
    async def test_adjudicate_cache_key_with_idempotent_calls(
        self, adjudicator, mock_llm
    ):
        mock_payload = EntitiesAdjudicator(analysis="match", is_same_entity=True)
        mock_llm.generate_json.return_value = mock_payload.model_dump()
        result1 = await adjudicator.adjudicate("A", "Context", "B", "Context")
        mock_llm.generate_json.reset_mock()
        result2 = await adjudicator.adjudicate("B", "Context", "A", "Context")
        mock_llm.generate_json.assert_not_called()

        assert result1.analysis == result2.analysis
        assert result1.is_same_entity == result2.is_same_entity

    @pytest.mark.asyncio
    async def test_adjudicate_cache_hit_bypasss_llm(self, adjudicator, mock_llm):
        mock_payload = EntitiesAdjudicator(analysis="cache result", is_same_entity=True)

        with patch.object(
            adjudicator.cache, "get", return_value=mock_payload.model_dump()
        ):
            result = await adjudicator.adjudicate("A", "Context", "B", "Context")

        mock_llm.generate_json.assert_not_called()
        assert type(result).__name__ == "EntitiesAdjudicator"
        assert result.analysis == "cache result"
        assert result.is_same_entity == True

    @pytest.mark.asyncio
    async def test_adjudicate_cache_miss_calls_llm_and_writes(
        self, adjudicator, mock_llm
    ):
        mock_payload = EntitiesAdjudicator(analysis="llm reason", is_same_entity=False)
        mock_llm.generate_json.return_value = mock_payload.model_dump()

        with patch.multiple(adjudicator.cache, get=DEFAULT, set=DEFAULT) as mocks:
            mocks["get"].return_value = None

            result = await adjudicator.adjudicate("A", "Context", "B", "Context")
            mock_llm.generate_json.assert_called_once()
            mocks["set"].assert_called_once()
            assert result.analysis == "llm reason"

    @pytest.mark.asyncio
    async def test_adjudicate_retries_on_transient_failure(
        self, adjudicator, mock_llm, caplog
    ):
        success_result = EntitiesAdjudicator(
            analysis="llm reason", is_same_entity=False
        )
        mock_llm.generate_json.side_effect = [
            Exception("Time out 1"),
            Exception("Time out 2"),
            success_result.model_dump(),
        ]

        result = await adjudicator.adjudicate("A", "Context", "B", "Context")
        assert mock_llm.generate_json.call_count == 3
        assert result.analysis == success_result.analysis
        assert result.is_same_entity == success_result.is_same_entity

        assert "Retry LLM API calling 1/3" in caplog.text
        assert "Retry LLM API calling 2/3" in caplog.text

    @pytest.mark.asyncio
    async def test_adjudicate_exhaustion_of_retries(
        self, adjudicator, mock_llm, caplog
    ):
        mock_llm.generate_json.side_effect = Exception("Fatal API error")

        result = await adjudicator.adjudicate("A", "Context", "B", "Context")
        assert mock_llm.generate_json.call_count == adjudicator.config.max_retries
        assert result is None
        assert "Entity adjudicate failed" in caplog.text

    @pytest.mark.asyncio
    async def test_adjudicate_semaphore_throttling(self, adjudicator, mock_llm, caplog):
        active_calls = 0
        max_active_calls = 0

        async def slow_llm_call(*args, **kwargs):
            nonlocal active_calls, max_active_calls
            active_calls += 1

            max_active_calls = max(max_active_calls, active_calls)
            await asyncio.sleep(0.1)

            active_calls -= 1
            return EntitiesAdjudicator(
                analysis="llm reason", is_same_entity=True
            ).model_dump()

        mock_llm.generate_json.side_effect = slow_llm_call

        import time

        start_time = time.time()

        tasks = [
            adjudicator.adjudicate(f"Entity A{i}", "Context", f"Entity_B{i}", "Context")
            for i in range(5)
        ]

        await asyncio.gather(*tasks)

        end_time = time.time()

        total_duration = end_time - start_time

        # with `max_conncurent`=2, 5 tasks should execute in 3 batches:
        # batch 1 (2 tasks) -> 0.1s
        # batch 2 (2 tasks) -> 0.1s
        # batch 3 (1 tasks) -> 0.1s
        # total time should be roughly 0.3 seconds, and max_active_calls must be 2.

        assert max_active_calls == 2
        assert total_duration >= 0.3
