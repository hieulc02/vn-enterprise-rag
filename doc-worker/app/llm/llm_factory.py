from config.config import AppSettings
from llm.base import BaseLLMProvider
from llm.enums import LLMProviderType


def get_llm_provider(settings: AppSettings, model_name: str) -> BaseLLMProvider:

    match settings.LLM_PROVIDER:
        case LLMProviderType.GEN_AI:
            from llm.genai import GoogleGenAIProvider

            gen_ai_settings = settings.gen_ai

            return GoogleGenAIProvider(
                api_key=gen_ai_settings.GEN_AI_API_KEY,
                model_name=model_name,
            )
        case _:
            raise ValueError(f"Unsupported LLM provider: {settings.LLM_PROVIDER}")
