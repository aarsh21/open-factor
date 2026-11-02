#!/usr/bin/env python3
"""Verify the exact, unmodified Geist v1.9.0 resources and their bundled OFL receipt."""
import hashlib
import json
from pathlib import Path
import zipfile

FONT_HASHES = {
    "res/font/geist_sans.ttf": "73894e0448cae90a92b6c2f8732b7bb9acb7b94c418bff559dad4a18e1de9659",
    "res/font/geist_serif_regular.ttf": "6c33e2138c7544455f7807ee11356d636ab23944fe214d6cefd3fd5b1e45e171",
    "assets/fonts/OFL-Geist.txt": "930853ee1daa68554d9e35c8a9175affb74f699fad9a5da6ee5ebe76379d9137",
}
FONT_ASSETS = {"assets/fonts/OFL-Geist.txt", "assets/fonts/geist-provenance.json"}
FONT_SIZES = {"res/font/geist_sans.ttf": 169056, "res/font/geist_serif_regular.ttf": 132452, "assets/fonts/OFL-Geist.txt": 4368}


def packaged_bytes(apk: zipfile.ZipFile, source: str, names: list[str]) -> bytes:
    if source in names:
        candidates = [source]
    elif source.startswith("res/font/"):
        # R8 shortens resource paths, not font bytes; provenance describes the source identity.
        candidates = [name for name in names if name.startswith("res/") and name.endswith(".ttf")
                      and apk.getinfo(name).file_size == FONT_SIZES[source]]
    else:
        candidates = []
    matches = []
    for name in candidates:
        if names.count(name) != 1 or apk.getinfo(name).file_size != FONT_SIZES[source]:
            raise ValueError(f"Duplicate or incorrectly sized Geist resource: {name}")
        data = apk.read(name)
        if hashlib.sha256(data).hexdigest() == FONT_HASHES[source]:
            matches.append(data)
    if len(matches) != 1:
        raise ValueError(f"Bundled Geist font or license differs from the pinned source: {source}")
    return matches[0]


def verify_fonts(apk: zipfile.ZipFile) -> None:
    names = apk.namelist()
    resources = {name: packaged_bytes(apk, name, names) for name in FONT_HASHES}
    receipt_name = "assets/fonts/geist-provenance.json"
    if names.count(receipt_name) != 1 or apk.getinfo(receipt_name).file_size > 16384:
        raise ValueError("Missing, duplicate or oversized Geist provenance")
    receipt = json.loads(apk.read(receipt_name))
    if receipt.get("release") != "v1.9.0" or receipt.get("repository") != "https://github.com/vercel/geist-font":
        raise ValueError("Unexpected Geist font provenance")
    records = receipt.get("files", [])
    if len(records) != len(FONT_HASHES) or {entry.get("file") for entry in records} != set(FONT_HASHES):
        raise ValueError("Incomplete Geist font provenance")
    for entry in records:
        if entry.get("sha256") != FONT_HASHES[entry["file"]] or len(resources[entry["file"]]) != entry.get("bytes"):
            raise ValueError("Geist font provenance does not describe the packaged bytes")
        if not entry.get("url", "").startswith("https://raw.githubusercontent.com/vercel/geist-font/v1.9.0/"):
            raise ValueError("Geist provenance must use the pinned official release")


def verify_sources(root: Path) -> None:
    directory = root / "openfactor/app/src/main"
    class SourceArchive:
        def namelist(self):
            return [*FONT_HASHES, "assets/fonts/geist-provenance.json"]
        def getinfo(self, name):
            from types import SimpleNamespace
            return SimpleNamespace(file_size=(directory / name).stat().st_size)
        def read(self, name):
            return (directory / name).read_bytes()
    verify_fonts(SourceArchive())


if __name__ == "__main__":
    verify_sources(Path(__file__).resolve().parents[1])
    print("Bundled Geist resources, license and provenance verified")
