#!/usr/bin/env python3
"""Reuse only the verified static assets of a checksum-pinned, previously signed APK.

Signature verification is a separate caller responsibility. No APK code is extracted
or executed, and original provenance/transform receipts remain byte-for-byte intact.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
import zipfile


MAX_STATIC_BYTES = 64 * 1024 * 1024


def extract_pack(apk_path: Path, output: Path) -> None:
    """Stage an already verified archive into a dedicated generated directory."""
    if output.is_symlink() or apk_path.resolve().is_relative_to(output.resolve()):
        raise ValueError("Generated output must be separate from the input APK")
    marker = output / "media-pack.json"
    if output.exists() and any(output.iterdir()):
        if not marker.is_file() or json.loads(marker.read_text()).get("schema") != 1:
            raise ValueError("Refusing to replace an unmanaged output directory")
        if any(path.is_symlink() for path in output.rglob("*")):
            raise ValueError("Generated output must not contain symlinks")
    with zipfile.ZipFile(apk_path) as apk:
        infos = apk.infolist()
        if len({info.filename for info in infos}) != len(infos):
            raise ValueError("APK contains duplicate ZIP entries")
        if apk.getinfo("assets/media-pack.json").file_size > 4 * 1024 * 1024:
            raise ValueError("Media manifest exceeds 4 MiB")
        raw_manifest = apk.read("assets/media-pack.json")
        manifest = json.loads(raw_manifest)
        if manifest.get("schema") != 1 or manifest.get("mode") != "static" or manifest.get("preparing"):
            raise ValueError("Expected a completed static media manifest")
        entries = manifest.get("files", [])
        if not entries or len(entries) > 5000:
            raise ValueError("Invalid static media entry count")
        expected = {"media-pack.json"}
        total = 0
        # Validate every path, size and digest before writing any output.
        for entry in entries:
            name = entry["path"]
            path = PurePosixPath(name)
            if (not name.startswith("reference/") or "\\" in name or "\x00" in name or
                    ".." in path.parts or path.as_posix() != name or name in expected):
                raise ValueError("Unsafe or duplicate static media path")
            size = entry["bytes"]
            if not isinstance(size, int) or size < 0 or apk.getinfo("assets/" + name).file_size != size:
                raise ValueError("Static media size differs")
            total += size
            if total > MAX_STATIC_BYTES:
                raise ValueError("Static media exceeds 64 MiB")
            data = apk.read("assets/" + name)
            if hashlib.sha256(data).hexdigest() != entry["sha256"]:
                raise ValueError("Static media checksum differs")
            expected.add(name)
        output.mkdir(parents=True, exist_ok=True)
        marker.write_text('{"schema":1,"preparing":true}\n')
        for entry in entries:
            target = output / entry["path"]
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(apk.read("assets/" + entry["path"]))
        for path in output.rglob("*"):
            if path.is_file() and path.relative_to(output).as_posix() not in expected:
                path.unlink()
        marker.write_bytes(raw_manifest)


def reuse(apk_path: Path, output: Path, expected_sha256: str) -> dict:
    if not re.fullmatch(r"[0-9a-f]{64}", expected_sha256):
        raise ValueError("Supply the pinned APK SHA-256")
    if apk_path.stat().st_size > 128 * 1024 * 1024:
        raise ValueError("Baseline APK exceeds 128 MiB")
    with apk_path.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != expected_sha256:
            raise ValueError("Baseline APK checksum differs from the reviewed pin")
    verifier = Path(__file__).with_name("verify_media_apk.py")
    receipt = subprocess.run([sys.executable, "-I", str(verifier), str(apk_path)],
                             check=True, capture_output=True, text=True)
    result = json.loads(receipt.stdout)
    extract_pack(apk_path, output)
    return {"baseline_sha256": expected_sha256, "manifest_files": result["manifest_files"],
            "static_bytes": result["static_bytes"], "provenance_preserved": True}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--sha256", required=True)
    args = parser.parse_args()
    print(json.dumps(reuse(args.apk, args.output, args.sha256), indent=2))


if __name__ == "__main__":
    main()
