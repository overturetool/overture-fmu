#!/usr/bin/env bash
set -euo pipefail

if [[ $# -gt 1 ]]; then
    echo "Usage: $0 [debug-port]" >&2
    exit 2
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
FMU_DIR="$SCRIPT_DIR/fmu"
OUTPUT_DIR="$FMU_DIR/target/FmuPackerTest/mainCreatesFmuInTargetFromResourceModel"
FMU_FILE="$OUTPUT_DIR/test-model.fmu"
DESTINATION="/Users/kel/data/au/into-cps-association/example-single_watertank/FMUs/test-model.fmu"
DEBUG_PORT="${1:-5005}"
RUNTIME_JAR="$FMU_DIR/target/vdmfmu-jar-with-dependencies.jar"

mvn -q -f "$SCRIPT_DIR/pom.xml" -pl fmu -am -DskipTests package

(
    cd "$FMU_DIR"
    java -cp "$RUNTIME_JAR" org.overturetool.fmi.fmu.unifmu.FmuPacker \
        "src/test/resources/model" \
        "target/FmuPackerTest/mainCreatesFmuInTargetFromResourceModel" \
        "test-model" \
        "--debug=$DEBUG_PORT"
)

mkdir -p "$(dirname "$DESTINATION")"
cp "$FMU_FILE" "$DESTINATION"
printf 'Copied debug FMU to %s\n' "$DESTINATION"
