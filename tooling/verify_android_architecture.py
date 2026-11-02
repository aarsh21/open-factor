#!/usr/bin/env python3
"""Focused native ownership gate; compilation and consumer tests prove the module boundary."""
import argparse
import json
import re
from pathlib import Path

KOTLIN_SOURCE = re.compile(r'""".*?"""|"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|/\*.*?\*/|//[^\n]*', re.S)


def source_violations(relative: str, text: str, legacy_roots: set[str]) -> list[str]:
    code = KOTLIN_SOURCE.sub(" ", text)
    errors = []
    if relative.startswith("core/src/main/"):
        if "/features/" not in relative:
            errors.append("core source needs a feature owner")
        if re.search(r"\b(?:android|androidx|coil3)\s*\.", code) or "@Composable" in code:
            errors.append("core must not depend on Android, Compose, or UI adapters")
        if re.search(r"\b(?:WorkoutViewModel|BuildConfig|LocalContext)\b", code):
            errors.append("core must not reach into the app host")
    prefix = "app/src/main/java/org/openfactor/app/"
    if relative.startswith(prefix):
        local = relative.removeprefix(prefix)
        if "/" not in local and local not in legacy_roots:
            errors.append("new app source needs a feature, navigation, or host owner; the flat root is frozen")
        elif "/" in local and not (local.startswith("features/") or local.startswith("navigation/")):
            errors.append("unregistered app source role")
        if local.startswith("navigation/") and re.search(r"\b(?:val|var)\s+route\s*:\s*String\b", code):
            errors.append("navigation must use typed destinations, not string routes")
    return [f"{relative}: {error}" for error in errors]


def validate(root: Path) -> list[str]:
    project = root / "openfactor"
    profile = json.loads((project / "architecture.json").read_text())
    errors = []
    for base in (project / "core/src/main", project / "app/src/main/java"):
        for path in sorted(base.rglob("*.kt")):
            errors.extend(source_violations(path.relative_to(project).as_posix(), path.read_text(), set(profile["legacyAppRootSources"])))
    core_build = (project / "core/build.gradle.kts").read_text()
    if re.search(r'project\s*\(|com\.android|compose', core_build):
        errors.append("core/build.gradle.kts: core cannot depend on an app project or Android/Compose")
    coordinates = re.findall(r'(?:api|implementation|compileOnly)\("([^"]+)"\)', core_build)
    for coordinate in coordinates:
        if not any(coordinate.startswith(prefix) for prefix in profile["coreDependencyPrefixes"]):
            errors.append(f"core/build.gradle.kts: unowned core dependency {coordinate}")
    consumer = (project / "coreConsumer/build.gradle.kts").read_text()
    projects = re.findall(r'project\("([^"]+)"\)', consumer)
    if projects != [":core"]:
        errors.append("coreConsumer must consume only the public core project, never the app")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors = validate(args.root)
    for error in errors:
        print(error)
    if not errors:
        print("Android architecture profile passed: portable core, isolated consumer, owned new source, typed navigation")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
