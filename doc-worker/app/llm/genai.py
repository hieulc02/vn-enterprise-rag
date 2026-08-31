from typing import Any

from google import genai
from google.genai import types
from llm.base import BaseLLMProvider


class GoogleGenAIProvider(BaseLLMProvider):

    MODEL_LIMIT_RATIO = 8

    def __init__(self, api_key: str, model_name: str = "gemini-3.5-flash"):
        self.model_name = model_name
        self.client = genai.Client(api_key=api_key)
        self.model_limit_ratio = self.MODEL_LIMIT_RATIO

        self._input_limit: int | None = None
        self._output_limit: int | None = None

    async def init(self):
        try:
            model_info = await self.client.aio.models.get(model=self.model_name)
            self._input_limit = model_info.input_token_limit
            self._output_limit = model_info.output_token_limit
        except Exception as e:
            raise RuntimeError(f"Fail to fetch LLM model for {self.model_name}: {e}")

    def input_token_limit(self) -> int:
        if self._input_limit is None:
            raise RuntimeError("LLM not initialized")
        return self._input_limit

    def output_token_limit(self) -> int:
        if self._input_limit is None:
            raise RuntimeError("LLM not initialized")
        return self._output_limit

    async def count_tokens(self, text: str) -> int:
        response = await self.client.aio.models.count_tokens(
            model=self.model_name, contents=text
        )

        return response.total_tokens

    async def generate_json(self, prompt, temperature=0, **kwargs):

        contents = kwargs.get("contents", "")
        response_schema = kwargs.get("response_schema", None)

        response = await self.client.aio.models.generate_content(
            model=self.model_name,
            contents=contents,
            config=types.GenerateContentConfig(
                max_output_tokens=self._output_limit,
                system_instruction=prompt,
                temperature=temperature,
                response_mime_type="application/json",
                response_schema=response_schema,
            ),
        )

        try:
            if response_schema:
                return response.parsed
            else:
                import orjson as json

                return json.loads(response.text)
        except json.JSONDecodeError as e:
            raise ValueError(f"Invalid response from LLM: {response.text}") from e
