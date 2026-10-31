import json
from pathlib import Path
import tempfile
import unittest

from prepare_media_pack import prepare


class PrepareMediaPackTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.source = self.root / 'source'
        self.output = self.root / 'generated'

    def managed_output(self):
        self.output.mkdir()
        (self.output / 'media-pack.json').write_text('{"schema":1}')

    def test_source_mode_removes_stale_generated_media(self):
        self.managed_output()
        stale = self.output / 'reference/videos/old.mp4'
        stale.parent.mkdir(parents=True)
        stale.write_bytes(b'old generated video')
        result = prepare(self.source, self.output, 'source')
        self.assertEqual('source', result['mode'])
        self.assertFalse(stale.exists())
        self.assertEqual([], json.loads((self.output / 'media-pack.json').read_text())['files'])

    def test_unmanaged_output_is_preserved(self):
        self.output.mkdir()
        personal = self.output / 'keep.txt'
        personal.write_text('keep')
        with self.assertRaisesRegex(ValueError, 'unmanaged'):
            prepare(self.source, self.output, 'source')
        self.assertEqual('keep', personal.read_text())

    def test_invalid_manifest_cannot_claim_ownership_of_an_output_directory(self):
        self.output.mkdir()
        marker = self.output / 'media-pack.json'
        personal = self.output / 'keep.txt'
        personal.write_text('keep')
        for value in [{}, [], None, 1, '', {'schema': 2}]:
            raw = json.dumps(value)
            marker.write_text(raw)
            with self.assertRaisesRegex(ValueError, 'schema'):
                prepare(self.source, self.output, 'source')
            self.assertEqual('keep', personal.read_text())
            self.assertEqual(raw, marker.read_text())

    def test_symlink_output_cannot_delete_files_in_another_directory(self):
        self.managed_output()
        personal = self.output / 'keep.txt'
        personal.write_text('keep')
        linked = self.root / 'linked-output'
        linked.symlink_to(self.output, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            prepare(self.source, linked, 'source')
        self.assertEqual('keep', personal.read_text())
        self.assertEqual('{"schema":1}', (self.output / 'media-pack.json').read_text())

    def test_symlink_manifest_is_not_an_ownership_marker(self):
        self.output.mkdir()
        external_marker = self.root / 'external-marker.json'
        external_marker.write_text('{"schema":1}')
        (self.output / 'media-pack.json').symlink_to(external_marker)
        personal = self.output / 'keep.txt'
        personal.write_text('keep')
        with self.assertRaisesRegex(ValueError, 'symlink'):
            prepare(self.source, self.output, 'source')
        self.assertEqual('keep', personal.read_text())
        self.assertEqual('{"schema":1}', external_marker.read_text())

    def test_nested_symlinks_are_rejected_before_any_generated_file_is_removed(self):
        self.managed_output()
        stale = self.output / 'old.mp4'
        stale.write_bytes(b'old generated video')
        external = self.root / 'external'
        external.mkdir()
        personal = external / 'keep.txt'
        personal.write_text('keep')
        (self.output / 'reference').symlink_to(external, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, 'symlink'):
            prepare(self.source, self.output, 'source')
        self.assertEqual(b'old generated video', stale.read_bytes())
        self.assertEqual('keep', personal.read_text())

    def test_source_and_output_cannot_overlap(self):
        for source, output in [(self.source, self.source),
                               (self.source, self.source / 'generated'),
                               (self.output / 'source', self.output)]:
            with self.assertRaisesRegex(ValueError, 'separate'):
                prepare(source, output, 'source')


if __name__ == '__main__':
    unittest.main()
