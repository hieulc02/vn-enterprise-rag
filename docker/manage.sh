#!/usr/bin/env bash
set -euo pipefail # Exit immediately on command/pipeline failure or unset variables

cd "$(dirname "$0")" # Move to the script's directory, where `$0` is the path to the script itself

ENV="${1:-dev}"
PROFILE="${2:-all}"
shift 2 || true

if [ "$ENV" = "prod" ]; then
    FILES="-f compose.yaml -f compose.prod.yaml"
else
    FILES="-f compose.yaml"
fi

if [ "$PROFILE" = "all" ]; then
    PROFILES="--profile worker --profile extraction --profile ingestion --profile retrieval"
else
    PROFILES="--profile $PROFILE"
fi

# pass all remaining argument directly to docker compose
# `exec` replaces the Bash shell process in memory with the `docker compose` process itself and `$@` expands to the positional parameters
# shellcheck disable=SC2086
exec docker compose $PROFILES $FILES "$@"







