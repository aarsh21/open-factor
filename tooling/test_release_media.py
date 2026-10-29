import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from reuse_release_media import extract_pack, reuse


class ReleaseMediaTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.apk = self.root / 'baseline.apk'
        self.output = self.root / 'generated'

    def archive(self, entries=None, mode='static'):
        data = b'unchanged original media'
        entries = entries if entries is not None else [dict(path='reference/exercises/photo.webp',
            bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), source_sha256='a' * 64,
            source_bytes=100, transform='equipment-thumbnail')]
        manifest = dict(schema=1, mode=mode, files=entries)
        raw = json.dumps(manifest, indent=2).encode()
        with zipfile.ZipFile(self.apk, 'w') as apk:
            apk.writestr('assets/media-pack.json', raw)
            for name in {entry['path'] for entry in entries}:
                apk.writestr('assets/' + name, data)
            apk.writestr('classes.dex', b'never extract or execute APK code')
            apk.writestr('assets/unreviewed-file', b'never copy unrelated assets')
        return raw, data

    def test_only_manifest_assets_are_copied_and_original_receipt_is_unchanged(self):
        raw, data = self.archive()
        extract_pack(self.apk, self.output)
        self.assertEqual(raw, (self.output / 'media-pack.json').read_bytes())
        self.assertEqual(data, (self.output / 'reference/exercises/photo.webp').read_bytes())
        self.assertEqual({'media-pack.json', 'reference/exercises/photo.webp'},
            {path.relative_to(self.output).as_posix() for path in self.output.rglob('*') if path.is_file()})

    def test_reuse_requires_the_exact_reviewed_digest_before_verification_or_writing(self):
        self.archive()
        with patch('reuse_release_media.subprocess.run') as verifier:
            for digest in ['invalid', 'a' * 64]:
                with self.assertRaises(ValueError):
                    reuse(self.apk, self.output, digest)
            verifier.assert_not_called()
        self.assertFalse(self.output.exists())

    def test_full_media_verifier_runs_in_isolated_python_before_staging(self):
        self.archive()
        digest = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        receipt = subprocess.CompletedProcess([], 0, json.dumps(dict(manifest_files=1, static_bytes=23)))
        with patch('reuse_release_media.subprocess.run', return_value=receipt) as verifier:
            result = reuse(self.apk, self.output, digest)
        args = verifier.call_args.args[0]
        self.assertEqual('-I', args[1])
        self.assertEqual('verify_media_apk.py', Path(args[2]).name)
        self.assertEqual(str(self.apk), args[3])
        self.assertEqual(digest, result['baseline_sha256'])
        self.assertTrue(result['provenance_preserved'])

    def test_verifier_failure_leaves_output_untouched(self):
        self.archive()
        digest = hashlib.sha256(self.apk.read_bytes()).hexdigest()
        with patch('reuse_release_media.subprocess.run', side_effect=subprocess.CalledProcessError(1, ['verify'])):
            with self.assertRaises(subprocess.CalledProcessError):
                reuse(self.apk, self.output, digest)
        self.assertFalse(self.output.exists())

    def test_unsafe_noncanonical_and_nonreference_paths_are_rejected(self):
        for name in ['reference/../escape', 'reference//photo', 'reference/./photo',
                     '/reference/photo', 'reference\\photo', 'classes.dex']:
            entry = dict(path=name, bytes=23, sha256=hashlib.sha256(b'unchanged original media').hexdigest())
            self.archive([entry])
            with self.assertRaisesRegex(ValueError, 'Unsafe'):
                extract_pack(self.apk, self.output)
            self.assertFalse(self.output.exists())

    def test_duplicate_paths_lengths_and_digests_are_validated_before_output(self):
        data = b'unchanged original media'
        entry = dict(path='reference/photo', bytes=len(data), sha256=hashlib.sha256(data).hexdigest())
        for entries in [[entry, entry], [dict(entry, bytes=100)], [dict(entry, bytes=-1)], [dict(entry, sha256='b' * 64)]]:
            self.archive(entries)
            with self.assertRaises(ValueError):
                extract_pack(self.apk, self.output)
            self.assertFalse(self.output.exists())

    def test_mode_and_entry_count_are_bounded(self):
        for mode in ['source', 'full']:
            self.archive(mode=mode)
            with self.assertRaises(ValueError):
                extract_pack(self.apk, self.output)
        self.archive([])
        with self.assertRaises(ValueError):
            extract_pack(self.apk, self.output)
        with zipfile.ZipFile(self.apk, 'w') as apk:
            apk.writestr('assets/media-pack.json', json.dumps(dict(schema=1, mode='static', files=[{}] * 5001)))
        with self.assertRaisesRegex(ValueError, 'entry count'):
            extract_pack(self.apk, self.output)

    def test_unmanaged_outputs_and_symlinks_are_not_overwritten(self):
        self.archive()
        self.output.mkdir()
        personal = self.output / 'keep.txt'
        personal.write_text('keep')
        with self.assertRaisesRegex(ValueError, 'unmanaged'):
            extract_pack(self.apk, self.output)
        self.assertEqual('keep', personal.read_text())
        (self.output / 'media-pack.json').write_text('{"schema":1}')
        (self.output / 'link').symlink_to(personal)
        with self.assertRaisesRegex(ValueError, 'symlinks'):
            extract_pack(self.apk, self.output)
        linked = self.root / 'linked-output'
        linked.symlink_to(self.output, target_is_directory=True)
        with self.assertRaises(ValueError):
            extract_pack(self.apk, linked)

    def test_old_managed_assets_are_removed_on_mode_change(self):
        self.output.mkdir()
        (self.output / 'media-pack.json').write_text('{"schema":1,"mode":"source"}')
        old = self.output / 'old-video.mp4'
        old.write_bytes(b'obsolete')
        self.archive()
        extract_pack(self.apk, self.output)
        self.assertFalse(old.exists())

    @unittest.skipUnless(os.environ.get('OPENFACTOR_MEDIA_BASELINE_SAMPLE'), 'No public baseline APK requested')
    def test_published_baseline_passes_full_verification_and_preserves_all_asset_receipts(self):
        result = reuse(Path(os.environ['OPENFACTOR_MEDIA_BASELINE_SAMPLE']), self.output,
            '2c804aebbe3877edc7a728348422e05890fa0a47389fa14760e8ce4f23fb941c')
        self.assertEqual(3394, result['manifest_files'])
        self.assertEqual(32986897, result['static_bytes'])
        self.assertEqual(3394, len(json.loads((self.output / 'media-pack.json').read_text())['files']))


if __name__ == '__main__':
    unittest.main()
