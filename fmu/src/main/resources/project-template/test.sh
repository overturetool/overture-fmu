#!/bin/sh
set -eu
if [ "$#" -gt 1 ]; then
    echo "Usage: $0 [path/to/exporter.jar]" >&2
    exit 2
fi
PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
sh "$PROJECT_DIR/pack.sh" "$@" --name test-model
VENV="$PROJECT_DIR/dist/.fmpy-venv"
if [ ! -x "$VENV/bin/python" ]; then
    python3 -m venv "$VENV"
fi
"$VENV/bin/python" -m pip install "fmpy[complete]"
"$VENV/bin/python" -m fmpy simulate "$PROJECT_DIR/dist/test-model.fmu" --stop-time 8 --show-plot
