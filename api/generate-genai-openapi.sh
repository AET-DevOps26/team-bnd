#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$ROOT_DIR/services/genai"
uv run python -c "from app.main import app; import yaml; print(yaml.dump(app.openapi()))" > "$ROOT_DIR/api/genai-openapi.yaml"
