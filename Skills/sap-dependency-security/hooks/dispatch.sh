#!/usr/bin/env bash
set -euo pipefail

SCRIPT_PATH="${BASH_SOURCE[0]}"
SCRIPT_DIR="${SCRIPT_PATH%/*}"
if [ "$SCRIPT_DIR" = "$SCRIPT_PATH" ]; then
  SCRIPT_DIR="."
fi
SCRIPT_DIR="$(cd "$SCRIPT_DIR" && pwd)"
INPUT_PAYLOAD="$(cat)"

if command -v node >/dev/null 2>&1; then
  if printf '%s' "$INPUT_PAYLOAD" | node "$SCRIPT_DIR/validator.mjs"; then
    exit 0
  fi
fi

printf '{}\n'
exit 0
