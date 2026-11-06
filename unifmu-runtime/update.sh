#!/bin/sh
set -eu

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    echo "Usage: $0 <unifmu-version> [output-directory]" >&2
    exit 2
fi

VERSION="${1#v}"
SCRIPT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
OUTPUT_DIR="${2:-$SCRIPT_DIR/target/generated-fmu}"
VERSION_FILE="$OUTPUT_DIR/.unifmu-version"

complete() {
    [ -s "$1/resources/libs/unifmu-java-runtime-$VERSION.jar" ] &&
        [ -s "$1/binaries/linux64/unifmu.so" ] &&
        [ -s "$1/binaries/aarch64-linux/unifmu.so" ] &&
        [ -s "$1/binaries/darwin64/unifmu.dylib" ] &&
        [ -s "$1/binaries/win64/unifmu.dll" ]
}

if [ -f "$VERSION_FILE" ] && [ "$(cat "$VERSION_FILE")" = "$VERSION" ] && complete "$OUTPUT_DIR"; then
    echo "UniFMU $VERSION binaries and Java runtime are present; skipping generation."
    exit 0
fi

case "$(uname -s)" in
    Darwin) PLATFORM=apple-darwin ;;
    Linux) PLATFORM=unknown-linux-gnu ;;
    *) echo "Unsupported host; run this script in a Linux Docker container." >&2; exit 1 ;;
esac
case "$(uname -m)" in
    arm64|aarch64) ARCH=aarch64 ;;
    x86_64|amd64) ARCH=x86_64 ;;
    *) echo "Unsupported architecture: $(uname -m)" >&2; exit 1 ;;
esac

TARGET_DIR="$SCRIPT_DIR/target/unifmu-bootstrap/$VERSION/$ARCH-$PLATFORM"
ARCHIVE_NAME="unifmu-$ARCH-$PLATFORM-$VERSION.zip"
ARCHIVE="$TARGET_DIR/$ARCHIVE_NAME"
EXTRACT_DIR="$TARGET_DIR/extracted"
TOOL="$EXTRACT_DIR/unifmu"
DOWNLOAD_URL="https://github.com/INTO-CPS-Association/unifmu/releases/download/v$VERSION/$ARCHIVE_NAME"

mkdir -p "$TARGET_DIR"
if [ ! -s "$ARCHIVE" ]; then
    echo "Downloading UniFMU $VERSION for $ARCH-$PLATFORM to $ARCHIVE"
    curl --fail --location --silent --show-error "$DOWNLOAD_URL" --output "$ARCHIVE.tmp"
    mv "$ARCHIVE.tmp" "$ARCHIVE"
fi

mkdir -p "$EXTRACT_DIR"
unzip -oq "$ARCHIVE" -d "$EXTRACT_DIR"
chmod +x "$TOOL"

GENERATED_DIR="$TARGET_DIR/generated"
"$TOOL" generate java "$GENERATED_DIR" fmi2

if ! complete "$GENERATED_DIR"; then
    echo "UniFMU generation did not produce all expected binaries and the Java runtime JAR." >&2
    exit 1
fi
mkdir -p "$OUTPUT_DIR"
cp -R "$GENERATED_DIR/." "$OUTPUT_DIR/"
printf '%s\n' "$VERSION" > "$VERSION_FILE"
