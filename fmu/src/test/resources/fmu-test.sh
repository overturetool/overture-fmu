#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 || $# -gt 2 || ( $# -eq 2 && "${2:-}" != "--show-plot" ) ]]; then
    echo "Usage: $0 <fmu-file> [--show-plot]" >&2
    exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(git -C "$SCRIPT_DIR" rev-parse --show-toplevel)"
VENV="$REPO_ROOT/.venv"

if [[ ! -x "$VENV/bin/python" ]]; then
    python3 -m venv "$VENV"
fi

if ! "$VENV/bin/python" -c 'import fmpy' >/dev/null 2>&1; then
    "$VENV/bin/python" -m pip install "fmpy[complete]"
fi

FMU_FILE="$1"
if [[ $# -eq 2 ]]; then
    "$VENV/bin/fmpy" --validate simulate "$FMU_FILE" --show-plot
else
    "$VENV/bin/fmpy" --validate simulate "$FMU_FILE"
fi