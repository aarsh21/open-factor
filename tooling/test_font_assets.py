import io
import json
from pathlib import Path
import runpy
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PROFILE = runpy.run_path(str(Path(__file__).with_name("verify_font_assets.py")))


class FontAssetsTest(unittest.TestCase):
    def archive(self, change=None, omit=None):
        base = ROOT / "openfactor/app/src/main"
        entries = {name: (base / name).read_bytes() for name in PROFILE["FONT_HASHES"]}
        entries["assets/fonts/geist-provenance.json"] = (base / "assets/fonts/geist-provenance.json").read_bytes()
        if change:
            change(entries)
        stream = io.BytesIO()
        with zipfile.ZipFile(stream, "w") as archive:
            for name, data in entries.items():
                if name != omit:
                    archive.writestr(name, data)
        stream.seek(0)
        return zipfile.ZipFile(stream)

    def test_source_and_packaged_bytes_match_pinned_official_release(self):
        PROFILE["verify_sources"](ROOT)
        with self.archive() as archive:
            PROFILE["verify_fonts"](archive)

    def test_r8_resource_path_shortening_preserves_source_identity(self):
        def optimize(files):
            files["res/a.ttf"] = files.pop("res/font/geist_sans.ttf")
            files["res/b.ttf"] = files.pop("res/font/geist_serif_regular.ttf")
        with self.archive(optimize) as archive:
            PROFILE["verify_fonts"](archive)

    def test_altered_font_or_missing_license_cannot_pass(self):
        for name in PROFILE["FONT_HASHES"]:
            with self.subTest(name=name):
                with self.archive(lambda files: files.update({name: b"altered"})) as archive:
                    with self.assertRaises(ValueError):
                        PROFILE["verify_fonts"](archive)
        with self.archive(omit="assets/fonts/OFL-Geist.txt") as archive:
            with self.assertRaises(ValueError):
                PROFILE["verify_fonts"](archive)

    def test_declared_resource_size_is_checked_before_reading_untrusted_bytes(self):
        class OversizedArchive:
            def namelist(self):
                return [*PROFILE["FONT_HASHES"], "assets/fonts/geist-provenance.json"]
            def getinfo(self, name):
                from types import SimpleNamespace
                return SimpleNamespace(file_size=2**32)
            def read(self, name):
                raise AssertionError("Oversized font must be rejected before allocation")
        with self.assertRaises(ValueError):
            PROFILE["verify_fonts"](OversizedArchive())

    def test_receipt_cannot_substitute_an_unpinned_source_or_omit_a_family(self):
        def alter(files, field):
            name = "assets/fonts/geist-provenance.json"
            receipt = json.loads(files[name])
            if field == "source":
                receipt["files"][0]["url"] = "https://example.invalid/font.ttf"
            else:
                receipt["files"].pop()
            files[name] = json.dumps(receipt).encode()
        for field in ("source", "missing"):
            with self.archive(lambda files: alter(files, field)) as archive:
                with self.assertRaises(ValueError):
                    PROFILE["verify_fonts"](archive)


if __name__ == "__main__":
    unittest.main()
