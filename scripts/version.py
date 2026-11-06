#!/usr/bin/env python3
"""Read the Maven version or prepare a stable release version."""

import argparse
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parent.parent
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
STABLE_VERSION = re.compile(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)")


def parse_version(value):
    match = STABLE_VERSION.fullmatch(value.removeprefix("v"))
    if not match:
        raise ValueError(f"Expected a stable version such as 0.3.1 or v0.3.1, got {value!r}")
    return tuple(map(int, match.groups()))


def select_version(current, tags, requested):
    current_version = parse_version(current)
    released = []
    for tag in tags:
        candidate = tag.removeprefix("Release/").removeprefix("v")
        if STABLE_VERSION.fullmatch(candidate):
            released.append(parse_version(candidate))
    latest = max(released, default=(-1, -1, -1))
    if requested:
        version = parse_version(requested)
        if version < current_version or version <= latest:
            raise ValueError("Release version must not precede the POM revision and must exceed existing stable tags")
    elif latest != (-1, -1, -1) and current_version[:2] > latest[:2]:
        version = (*current_version[:2], 0)
    else:
        major, minor, patch = max(current_version, latest)
        version = (major, minor, patch + 1)
    return ".".join(map(str, version))


def properties(manifest):
    element = ET.fromstring(manifest).find("m:properties", NS)
    if element is None:
        raise ValueError("Root pom.xml has no properties")
    result = {}
    for name in ("revision", "sha1", "changelist"):
        child = element.find(f"m:{name}", NS)
        if child is None:
            raise ValueError(f"Root pom.xml has no {name} property")
        result[name] = child.text or ""
    return result


def set_version(manifest, version):
    parse_version(version)
    properties(manifest)
    for name, value in (("revision", version), ("sha1", ""), ("changelist", "")):
        pattern = rf"<{name}(?:\s*/>|>[^<]*</{name}>)"
        manifest, count = re.subn(pattern, f"<{name}>{value}</{name}>", manifest)
        if count != 1:
            raise ValueError(f"Expected exactly one {name} property")
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--prepare", action="store_true", help="Update the root POM for a release")
    mode.add_argument("--build-version", help="Validate and normalize an explicit build version without editing the POM")
    parser.add_argument("--version", help="Explicit release version (requires --prepare)")
    args = parser.parse_args()
    if args.version is not None and not args.prepare:
        parser.error("--version requires --prepare")
    try:
        if args.build_version is not None:
            print(".".join(map(str, parse_version(args.build_version))))
            return
        path = ROOT / "pom.xml"
        manifest = path.read_text(encoding="utf-8")
        props = properties(manifest)
        if args.prepare:
            tags = subprocess.check_output(["git", "tag", "--list"], cwd=ROOT, text=True).splitlines()
            version = select_version(props["revision"], tags, args.version)
            path.write_text(set_version(manifest, version), encoding="utf-8")
        else:
            version = props["revision"] + props["sha1"] + props["changelist"]
        print(version)
    except ValueError as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
