from abc import ABC, abstractmethod
from typing import Any


class Tokenizer(ABC):

    @abstractmethod
    def __init__(self, **kwargs: Any) -> None: ...

    # @abstractmethod
    # def num_prompt_tokens(self, message) -> int:
    #     raise NotImplementedError

    @abstractmethod
    def num_tokens(self, text: str) -> int:
        raise NotImplementedError
