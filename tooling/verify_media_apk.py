#!/usr/bin/env python3
"""Verify the original-static-media APK and its exact backend video references.

Checks the artifact bytes, not the build output directory. This verifier does not
establish a media license, validate an APK signature, or claim remote availability.
Use apksigner and deployed-service checks separately.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SHA256 = re.compile(r"[0-9a-f]{64}")
VIDEO = re.compile(r"videos/([0-9a-f]{32})_([a-z])[.]mp4")
PHOTO = re.compile(r"reference/exercises/(?:preferred_)?([0-9a-f]{32})_([a-z])[.]webp")
EXPECTED_COUNTS = {
    "body": 4, "catalog.json": 1, "equipment": 297, "exercises": 1832,
    "fonts": 2, "licenses": 1, "markdown": 1213, "muscle_groups": 44,
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def canonical_path(value: str) -> str:
    require(isinstance(value, str) and bool(value) and "\\" not in value and "\x00" not in value,
            f"Invalid asset path: {value!r}")
    path = PurePosixPath(value)
    require(not path.is_absolute() and ".." not in path.parts and path.as_posix() == value,
            f"Noncanonical asset path: {value!r}")
    return value


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def verify(apk_path: Path, index_path: Path | None = None, reference: Path | None = None) -> dict:
    require(apk_path.stat().st_size <= 128 * 1024 * 1024,
            "Static-media APK exceeds 128 MiB; rebuild clean and check for retained video data")
    index_path = index_path or ROOT / "backend/media/video-index.json"
    index_bytes = index_path.read_bytes()
    index = json.loads(index_bytes)
    records = index.get("videos", {})
    require(isinstance(records, dict), "Backend video records must be an object")
    require(index.get("schema") == 1 and index.get("count") == len(records) == 1831,
            "Backend index must contain exactly 1,831 original video objects")
    for filename, entry in records.items():
        require(VIDEO.fullmatch("videos/" + filename) is not None, f"Invalid backend video object: {filename}")
        require(isinstance(entry.get("bytes"), int) and 12 <= entry["bytes"] <= 32 * 1024 * 1024,
                f"Invalid indexed video size: {filename}")
        require(SHA256.fullmatch(entry.get("sha256", "")) is not None, f"Invalid video digest: {filename}")
    require(index.get("total_bytes") == sum(entry["bytes"] for entry in records.values()), "Backend total bytes differ")

    with zipfile.ZipFile(apk_path) as apk:
        infos = apk.infolist()
        names = [info.filename for info in infos]
        require(len(names) == len(set(names)), "APK contains duplicate ZIP entry names")
        require(sum(info.file_size for info in infos) <= 256 * 1024 * 1024,
                "APK uncompressed contents exceed 256 MiB")
        for name in names:
            canonical_path(name.rstrip("/"))
            require(not name.lower().endswith((".mp4", ".m4v", ".mov", ".webm")),
                    f"Video payload is forbidden in the static APK: {name}")
        required = {"AndroidManifest.xml", "classes.dex", "assets/media-pack.json",
                    "assets/reference/catalog.json", "assets/catalog.json", "assets/equipment.json"}
        require(required.issubset(names), f"Missing required APK entries: {sorted(required - set(names))}")
        pack_bytes = apk.read("assets/media-pack.json")
        pack = json.loads(pack_bytes)
        require(pack.get("schema") == 1 and pack.get("mode") == "static" and not pack.get("preparing"),
                "Expected a completed schema-1 static media pack")
        require(pack.get("bundled_videos") == 0 and pack.get("video_delivery") == "remote-or-cache",
                "Static media pack must use on-demand video delivery")
        entries = pack.get("files")
        require(isinstance(entries, list) and bool(entries), "Media pack has no file manifest")
        require(all(isinstance(entry.get("bytes"), int) and entry["bytes"] >= 0 for entry in entries),
                "Media manifest contains invalid file lengths")
        require(sum(entry["bytes"] for entry in entries) <= 64 * 1024 * 1024,
                "Static media manifest exceeds 64 MiB")
        manifest_paths: set[str] = set()
        transformed = 0
        for entry in entries:
            asset = canonical_path(entry["path"])
            require(asset.startswith("reference/") and not asset.startswith("reference/videos/"),
                    f"Unexpected media manifest path: {asset}")
            require(asset not in manifest_paths, f"Duplicate media manifest path: {asset}")
            manifest_paths.add(asset)
            require(SHA256.fullmatch(entry.get("sha256", "")) is not None and
                    SHA256.fullmatch(entry.get("source_sha256", "")) is not None, f"Invalid media digest: {asset}")
            require(isinstance(entry.get("bytes"), int) and entry["bytes"] >= 0 and
                    isinstance(entry.get("source_bytes"), int) and entry["source_bytes"] >= 0,
                    f"Invalid media byte count: {asset}")
            member = "assets/" + asset
            require(member in names, f"Manifest asset is absent from APK: {asset}")
            info = apk.getinfo(member)
            require(info.file_size == entry["bytes"], f"Packaged asset length differs: {asset}")
            data = apk.read(member)
            require(sha(data) == entry["sha256"], f"Packaged asset checksum differs: {asset}")
            require(not (len(data) >= 12 and data[4:8] == b"ftyp"), f"Disguised MP4 payload: {asset}")
            if entry.get("transform") == "original":
                require(entry["sha256"] == entry["source_sha256"] and entry["bytes"] == entry["source_bytes"],
                        f"Original asset was modified: {asset}")
            else:
                require(entry.get("transform") == "equipment-thumbnail" and
                        asset.startswith("reference/equipment/") and asset.endswith(".png"),
                        f"Unapproved media transformation: {asset}")
                transformed += 1
            if reference:
                original = reference / asset.removeprefix("reference/")
                require(original.is_file() and not original.is_symlink(), f"Missing original source: {asset}")
                require(original.stat().st_size == entry["source_bytes"] and
                        sha(original.read_bytes()) == entry["source_sha256"], f"Original source hash differs: {asset}")
        actual_paths = {name.removeprefix("assets/") for name in names
                        if name.startswith("assets/reference/") and not name.endswith("/")}
        require(actual_paths == manifest_paths,
                f"Reference assets differ from manifest: missing={sorted(manifest_paths - actual_paths)}, extra={sorted(actual_paths - manifest_paths)}")
        actual_counts = dict(Counter(path.split("/")[1] for path in manifest_paths))
        require(actual_counts == pack.get("counts") == EXPECTED_COUNTS, "Original media category counts differ")
        require(transformed == 297, "Expected exactly 297 equipment thumbnails; photos/anatomy must remain original")
        static_bytes = sum(entry["bytes"] for entry in entries)
        require(static_bytes == pack.get("static_bytes") and static_bytes <= 64 * 1024 * 1024,
                "Static byte count differs or exceeds 64 MiB")

        catalog_bytes = apk.read("assets/reference/catalog.json")
        catalog_hash = sha(catalog_bytes)
        require(catalog_hash == pack.get("source_catalog_sha256") == index.get("source_catalog_sha256"),
                "Original catalog does not match the media pack and backend index")
        catalog = json.loads(catalog_bytes)
        public = json.loads(apk.read("assets/catalog.json"))
        require(isinstance(catalog, list) and len(catalog) == pack.get("exercise_count") == 1345,
                "Expected the complete 1,345-exercise original catalog")
        ids = [exercise["id"] for exercise in catalog]
        require(len(ids) == len(set(ids)) and len(public) == 1345 and
                set(ids) == {exercise["id"] for exercise in public},
                "Original and public catalog exercise identities differ")
        photos = instructions = mapped_photos = 0
        objects: set[str] = set()
        for exercise in catalog:
            frames = exercise.get("frames", [])
            clips = exercise.get("clips", [])
            object_paths = exercise.get("videoObjectPaths", [])
            photos += bool(frames)
            mapped_photos += len(frames)
            require(len(frames) == len(clips) == len(object_paths), f"Camera order/coverage differs for {exercise['id']}")
            for frame, clip, object_path in zip(frames, clips, object_paths):
                require(frame in manifest_paths, f"Photo pointer is not bundled: {frame}")
                match = VIDEO.fullmatch(object_path)
                photo_match = PHOTO.fullmatch(frame)
                require(match is not None and photo_match is not None and
                        match.group(1) == photo_match.group(1) == exercise["id"] and
                        match.group(2) == photo_match.group(2), f"Camera photo/video association differs: {frame}")
                require(clip == "reference/" + object_path, f"Clip pointer differs from original object: {clip}")
                require(object_path.removeprefix("videos/") in records, f"Clip missing from backend index: {object_path}")
                objects.add(object_path)
            instruction = exercise.get("instructions", "")
            if instruction:
                instructions += 1
                require(instruction in manifest_paths, f"Instruction pointer is not bundled: {instruction}")
        require(photos == pack.get("exercises_with_photos") == 619 and mapped_photos == 1831,
                "Original exercise photo coverage differs")
        require(instructions == pack.get("exercises_with_instructions") == 1213, "Instruction coverage differs")
        require(objects == set(pack.get("video_objects", [])) == {"videos/" + name for name in records},
                "Catalog, APK manifest and backend video object sets differ")
        require(len(pack.get("video_objects", [])) == len(objects) == 1831, "Expected exactly 1,831 distinct clip objects")

        allowed = {"assets/catalog.json", "assets/catalog-provenance.json", "assets/equipment.json",
                   "assets/exercise-previews.json", "assets/fonts/DM-Sans.ttf", "assets/fonts/OFL.txt",
                   "assets/licenses/CC-BY-SA-3.0.txt", "assets/media-pack.json",
                   "assets/dexopt/baseline.prof", "assets/dexopt/baseline.profm"}
        allowed.update("assets/" + path for path in manifest_paths)
        previews = json.loads(apk.read("assets/exercise-previews.json"))
        for entry in previews["entries"]:
            asset = canonical_path(entry["asset"])
            require(asset.startswith("previews/") and entry["exercise_id"] in ids and
                    entry["author"] == "Everkinetic" and entry["license"] == "CC BY-SA 3.0",
                    f"Unreviewed fallback preview: {asset}")
            data = apk.read("assets/" + asset)
            require(len(data) == entry["bytes"] and sha(data) == entry["sha256"], f"Fallback preview checksum differs: {asset}")
            allowed.add("assets/" + asset)
        unexpected = {name for name in names if name.startswith("assets/") and not name.endswith("/")} - allowed
        require(not unexpected, f"Unreviewed APK assets: {sorted(unexpected)}")
        corrupt = apk.testzip()
        require(corrupt is None, f"Corrupt ZIP member: {corrupt}")
    with apk_path.open("rb") as stream:
        apk_hash = hashlib.file_digest(stream, "sha256").hexdigest()
    return {
        "artifact": apk_path.name, "bytes": apk_path.stat().st_size, "sha256": apk_hash,
        "mode": "static", "zip_integrity": "passed", "manifest_hashes": "passed",
        "manifest_files": len(entries), "static_bytes": static_bytes, "category_counts": actual_counts,
        "exercise_count": len(catalog), "exercises_with_original_photos": photos,
        "mapped_original_photos": mapped_photos, "instruction_files": instructions,
        "bundled_videos": 0, "original_video_references": len(objects),
        "source_catalog_sha256": catalog_hash, "backend_index_sha256": sha(index_bytes),
        "backend_objects_match": True, "remote_availability_checked": False,
        "original_source_bytes_checked": reference is not None,
        "signature_checked": False,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--backend-index", type=Path, default=ROOT / "backend/media/video-index.json")
    parser.add_argument("--reference", type=Path, help="Also compare every source hash against the untouched imported directory")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = verify(args.apk, args.backend_index, args.reference)
    rendered = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered)
    print(rendered, end="")


if __name__ == "__main__":
    main()
