#!/usr/bin/env python3
"""Stage an efficient local reference pack without changing imported originals.

Only generated output belongs in an APK. The imported pack and generated output
retain their original ownership; this script does not grant redistribution rights.
Source mode needs only Python. Static/full modes use ImageMagick for equipment
thumbnails and keep exercise photos/anatomy/instruction bytes unchanged.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import subprocess

SCHEMA = 1
EQUIPMENT_EDGE = 256
STATIC_LIMIT = 64 * 1024 * 1024
FONT_NAMES = {"DMSans-VariableFont_opsz,wght.ttf", "MacroSans-VF.ttf"}


def digest(path: Path) -> str:
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".partial")
    temporary.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n")
    temporary.replace(path)


def safe_relative(value: str) -> Path:
    relative = PurePosixPath(value)
    if relative.is_absolute() or ".." in relative.parts or "\\" in value:
        raise ValueError(f"Unsafe asset path: {value}")
    return Path(*relative.parts)


def prepare(source: Path, output: Path, mode: str, max_static_bytes: int = STATIC_LIMIT) -> dict:
    source, output = source.resolve(), output.resolve()
    if source == output or source in output.parents or output in source.parents:
        raise ValueError("Generated output must be separate from the imported source directory")
    if mode == "auto":
        mode = "static" if (source / "catalog.json").is_file() else "source"
    if mode not in {"source", "static", "full"}:
        raise ValueError(f"Unknown media mode: {mode}")
    marker = output / "media-pack.json"
    if output.exists() and any(output.iterdir()) and not marker.is_file():
        raise ValueError(f"Refusing to change an unmanaged output directory: {output}")
    previous = json.loads(marker.read_text()) if marker.is_file() else {}
    if previous and previous.get("schema") != SCHEMA:
        raise ValueError("Unsupported generated pack schema; choose a new output directory")
    output.mkdir(parents=True, exist_ok=True)
    if not marker.is_file():
        # A failed first conversion remains recognizable as our output and can be retried safely.
        write_json(marker, {"schema": SCHEMA, "preparing": True, "files": []})
    manifest: dict = {"schema": SCHEMA, "mode": mode, "files": [], "equipment_edge_px": EQUIPMENT_EDGE}
    expected = {"media-pack.json"}
    if mode == "source":
        manifest.update(static_bytes=0, bundled_videos=0, video_delivery="none",
                        ownership="Reference media is excluded from this source-only build.")
    else:
        catalog_path = source / "catalog.json"
        if not catalog_path.is_file():
            raise ValueError(f"The {mode} mode requires an imported reference/catalog.json")
        magick = shutil.which("magick")
        if not magick:
            raise ValueError("ImageMagick 'magick' is required to stage equipment previews; install it or select source mode")
        catalog = json.loads(catalog_path.read_text())
        if not isinstance(catalog, list) or not catalog:
            raise ValueError("Imported catalog must contain exercise records")
        old_files = {entry["path"]: entry for entry in previous.get("files", [])}
        selected: dict[str, str] = {"catalog.json": "original"}
        # These are all source photos and all anatomical SVGs, including camera views.
        for directory in ("exercises", "body", "muscle_groups", "licenses"):
            for path in (source / directory).rglob("*"):
                if path.is_file():
                    selected[path.relative_to(source).as_posix()] = "original"
        for path in (source / "equipment").glob("*.png"):
            selected[path.relative_to(source).as_posix()] = "equipment-thumbnail"
        for name in FONT_NAMES:
            if (source / "fonts" / name).is_file():
                selected[f"fonts/{name}"] = "original"
        for exercise in catalog:
            for asset in exercise.get("frames", []) + ([exercise["instructions"]] if exercise.get("instructions") else []):
                relative = safe_relative(asset)
                if not relative.parts or relative.parts[0] != "reference":
                    raise ValueError(f"Unexpected imported static path: {asset}")
                local = Path(*relative.parts[1:]).as_posix()
                if not (source / local).is_file():
                    raise ValueError(f"Catalog points to a missing static asset: {asset}")
                selected[local] = "original"
        if mode == "full":
            for path in (source / "videos").glob("*.mp4"):
                selected[path.relative_to(source).as_posix()] = "original"
            for exercise in catalog:
                for clip in exercise.get("clips", []) + exercise.get("videoObjectPaths", []):
                    if clip and not (source / safe_relative(clip.removeprefix("reference/"))).is_file():
                        raise ValueError(f"Full-video mode is missing a catalog clip: {clip}")
        for relative, transform in sorted(selected.items()):
            original = source / safe_relative(relative)
            if original.is_symlink():
                raise ValueError(f"Source assets must not be symlinks: {relative}")
            packaged = f"reference/{relative}"
            target = output / packaged
            target.parent.mkdir(parents=True, exist_ok=True)
            source_sha = digest(original)
            cached = old_files.get(packaged, {})
            reusable = (target.is_file() and cached.get("source_sha256") == source_sha
                        and cached.get("transform") == transform
                        and previous.get("equipment_edge_px") == EQUIPMENT_EDGE
                        and cached.get("sha256") == digest(target))
            if not reusable:
                temporary = target.with_name(target.stem + ".partial" + target.suffix)
                if transform == "equipment-thumbnail":
                    subprocess.run([magick, str(original), "-resize", f"{EQUIPMENT_EDGE}x{EQUIPMENT_EDGE}>",
                                    "-strip", "-define", "png:compression-level=9", str(temporary)], check=True)
                else:
                    shutil.copyfile(original, temporary)
                temporary.replace(target)
            expected.add(packaged)
            manifest["files"].append({"path": packaged, "bytes": target.stat().st_size,
                                      "sha256": digest(target), "source_sha256": source_sha,
                                      "source_bytes": original.stat().st_size, "transform": transform})
        static_bytes = sum(entry["bytes"] for entry in manifest["files"] if not entry["path"].startswith("reference/videos/"))
        if static_bytes > max_static_bytes:
            raise ValueError(f"Static reference pack exceeds its budget: {static_bytes} > {max_static_bytes} bytes")
        counts = Counter(entry["path"].split("/")[1] for entry in manifest["files"])
        provenance_path = source / "provenance.json"
        provenance = json.loads(provenance_path.read_text()) if provenance_path.is_file() else {}
        manifest.update(
            static_bytes=static_bytes, counts=dict(counts), exercise_count=len(catalog),
            exercises_with_photos=sum(bool(exercise.get("frames")) for exercise in catalog),
            exercises_with_instructions=sum(bool(exercise.get("instructions")) for exercise in catalog),
            bundled_videos=counts.get("videos", 0),
            video_delivery="bundled" if mode == "full" else "remote-or-cache",
            video_objects=sorted({path for exercise in catalog for path in exercise.get("videoObjectPaths", [])}),
            source_apk_sha256=provenance.get("source_apk_sha256"),
            source_catalog_sha256=digest(catalog_path),
            ownership="Imported media retains original ownership and is not relicensed as GPL. Distribution requires appropriate rights.",
        )
    # Sync only this script's generated directory; mode changes cannot leave old videos behind.
    for path in output.rglob("*"):
        if path.is_file() and path.relative_to(output).as_posix() not in expected:
            path.unlink()
    write_json(marker, manifest)
    return {key: value for key, value in manifest.items() if key not in {"files", "video_objects"}}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--mode", choices=("auto", "source", "static", "full"), default="auto")
    parser.add_argument("--max-static-bytes", type=int, default=STATIC_LIMIT)
    args = parser.parse_args()
    print(json.dumps(prepare(args.source, args.output, args.mode, args.max_static_bytes), indent=2))


if __name__ == "__main__":
    main()
