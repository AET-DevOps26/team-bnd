#!/usr/bin/env bash

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

npx --yes @redocly/cli@2.30.3 join \
  "$SCRIPT_DIR/spring-openapi.yaml" \
  "$SCRIPT_DIR/genai-openapi.yaml" \
  -o "$SCRIPT_DIR/openapi.yaml"
