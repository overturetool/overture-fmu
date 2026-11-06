#!/bin/sh
set -eu
PROJECT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
JAR='@FMU_JAR@'
case "${1:-}" in
    ""|-*) ;;
    *) JAR="$1"; shift ;;
esac
if [ ! -f "$JAR" ]; then
    echo "Exporter JAR not found: $JAR" >&2
    echo "Usage: $0 [path/to/exporter.jar] [pack-options]" >&2
    exit 2
fi
exec java -jar "$JAR" pack "$PROJECT_DIR" "$@"
