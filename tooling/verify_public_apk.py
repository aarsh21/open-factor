#!/usr/bin/env python3
"""Validate a distributable public APK and write its exact SHA-256 receipt.

This is an artifact/provenance check, not a substitute for installation or UI tests.
The private reference pack must never enter a public artifact accidentally.
"""
import argparse
import hashlib
import json
from pathlib import Path
import zipfile


def verify(path: Path) -> dict:
    if path.stat().st_size > 50 * 1024 * 1024:
        raise ValueError("Public APK exceeds 50 MiB; rebuild clean to remove stale private-media ZIP data")
    with zipfile.ZipFile(path) as apk:
        names = apk.namelist()
        private = [name for name in names if name.startswith("assets/reference/")]
        if private:
            raise ValueError(f"Private reference media is present ({len(private)} entries)")
        for required in (
            "AndroidManifest.xml", "classes.dex", "assets/catalog.json",
            "assets/exercise-previews.json", "assets/licenses/CC-BY-SA-3.0.txt",
            "assets/fonts/DM-Sans.ttf", "assets/fonts/OFL.txt",
        ):
            if required not in names:
                raise ValueError(f"Missing APK entry: {required}")
        corrupt = apk.testzip()
        if corrupt:
            raise ValueError(f"Corrupt APK entry: {corrupt}")
        catalog = json.loads(apk.read("assets/catalog.json"))
        ids = [exercise["id"] for exercise in catalog]
        if len(ids) != len(set(ids)):
            raise ValueError("Exercise catalog contains duplicate IDs")
        if len(ids) != 1345:
            raise ValueError(f"Unexpected public catalog size: {len(ids)}")
        manifest = json.loads(apk.read("assets/exercise-previews.json"))
        allowed_assets = {
            "assets/catalog.json", "assets/catalog-provenance.json", "assets/equipment.json",
            "assets/exercise-previews.json", "assets/licenses/CC-BY-SA-3.0.txt",
            "assets/fonts/DM-Sans.ttf", "assets/fonts/OFL.txt",
            "assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm",
            "assets/media-pack.json",
        }
        for entry in manifest["entries"]:
            asset = "assets/" + entry["asset"]
            if not entry["asset"].startswith("previews/") or ".." in entry["asset"]:
                raise ValueError(f"Unexpected preview path: {entry['asset']}")
            if entry["exercise_id"] not in ids:
                raise ValueError(f"Unknown preview exercise: {entry['exercise_id']}")
            if entry["author"] != "Everkinetic" or entry["license"] != "CC BY-SA 3.0":
                raise ValueError(f"Unexpected preview attribution: {asset}")
            data = apk.read(asset)
            if len(data) != entry["bytes"] or hashlib.sha256(data).hexdigest() != entry["sha256"]:
                raise ValueError(f"Preview bytes differ from provenance manifest: {asset}")
            allowed_assets.add(asset)
        unexpected = sorted(name for name in names if name.startswith("assets/") and not name.endswith("/") and name not in allowed_assets)
        if unexpected:
            raise ValueError(f"Unreviewed assets in public APK: {unexpected}")
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return {
        "artifact": path.name,
        "bytes": path.stat().st_size,
        "sha256": digest,
        "exercise_count": len(ids),
        "private_reference_entries": 0,
        "licensed_preview_images": len(manifest["entries"]),
        "preview_hashes": "passed",
        "zip_integrity": "passed",
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = verify(args.apk)
    rendered = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered)
    print(rendered, end="")


if __name__ == "__main__":
    main()
