from dataclasses import dataclass
from llm.base import BaseLLMProvider


@dataclass
class ChunkerConfig:
    token_limit: int
    token_per_chunk: int
    token_overlap: int = 100

    @classmethod
    def from_llm(
        cls,
        llm_provider: BaseLLMProvider,
        target_chunk_size: int = 1200,
        overlap: int = 100,
    ):
        limit_per_chunk = int(
            llm_provider.output_token_limit()
            / int(getattr(llm_provider, "MODEL_LIMIT_RATIO", 1))
        )

        return cls(
            token_limit=llm_provider.input_token_limit(),
            token_per_chunk=min(target_chunk_size, limit_per_chunk),
            token_overlap=overlap,
        )
