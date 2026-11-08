# syntax=docker/dockerfile:1
ARG PYTHON_VERSION=3.13
FROM ghcr.io/astral-sh/uv:python${PYTHON_VERSION}-trixie-slim AS builder

ENV UV_COMPILE_BYTECODE=1 \
    UV_COMPILE_BYTECODE_TIMEOUT=300 \
    UV_LINK_MODE=copy \
    UV_NO_DEV=1 \
    UV_PYTHON_DOWNLOADS=0

WORKDIR /app

# Cache dependencies layer
RUN --mount=type=cache,target=/root/.cache/uv \
    --mount=type=bind,source=uv.lock,target=uv.lock \
    --mount=type=bind,source=pyproject.toml,target=pyproject.toml \
    uv sync --locked --no-install-project

COPY pyproject.toml uv.lock ./
COPY app/ ./app/
COPY scripts/ ./scripts/

RUN --mount=type=cache,target=/root/.cache/uv \
    uv sync --locked


FROM python:${PYTHON_VERSION}-slim-trixie    

RUN groupadd --system --gid 999 nonroot \
 && useradd --system --gid 999 --uid 999 --create-home nonroot

WORKDIR /app

COPY --from=builder --chown=nonroot:nonroot /app/.venv /app/.venv
COPY --from=builder --chown=nonroot:nonroot /app/scripts /app/scripts

# Place executables in the environment at the front of the path
ENV PATH="/app/.venv/bin:$PATH" \
    PYTHONUNBUFFERED=1 \
    # Route HuggingFace cache to writable directory
    HF_HOME="/app/.cache/huggingface"

RUN mkdir -p /app/.cache/huggingface \
 && chown -R nonroot:nonroot /app/.cache

USER nonroot

RUN python scripts/download_models.py

COPY --from=builder --chown=nonroot:nonroot /app/app /app/app

CMD [ "python", "app/__main__.py" ]