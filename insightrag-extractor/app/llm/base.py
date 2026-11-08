from abc import ABC, abstractmethod


class BaseLLMProvider(ABC):

    @abstractmethod
    async def count_tokens(self, text: str) -> int:
        pass

    @abstractmethod
    def input_token_limit(self) -> int:
        pass

    @abstractmethod
    def output_token_limit(self) -> int:
        pass

    @abstractmethod
    async def generate_json(self, prompt: str, temperature: float = 0.0, **kwargs: any):
        pass
