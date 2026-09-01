from google.genai import local_tokenizer

from .tokenizer import Tokenizer


class GoogleGenAITokenizer(Tokenizer):

    def __init__(self, model_name: str = "gemini-3.5-flash"):
        self.tokenizer = local_tokenizer.LocalTokenizer(model_name=model_name)

    def num_tokens(self, text: str) -> int:
        return self.tokenizer.count_tokens(text).total_tokens
