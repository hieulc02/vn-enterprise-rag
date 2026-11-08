from config.config import AppSettings

from tokenizer.tokenizer import Tokenizer
from llm.enums import LLMProviderType


def get_tokenizer(settings: AppSettings) -> Tokenizer:
    match settings.LLM_PROVIDER:
        case LLMProviderType.GEN_AI:
            from tokenizer.genai import GoogleGenAITokenizer

            gen_ai_settings = settings.gen_ai

            return GoogleGenAITokenizer(
                model_name=gen_ai_settings.INGESTION_MODEL_NAME,
            )
        case _:
            raise ValueError(f"Unsupported LLM tokenizer: {settings.LLM_PROVIDER}")
