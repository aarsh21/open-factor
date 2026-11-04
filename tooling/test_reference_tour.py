import argparse
import contextlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

import build_reference_tour as tour


class ReferenceTourTest(unittest.TestCase):
    def test_timeouts_must_be_finite_positive_seconds(self):
        self.assertEqual(2.5, tour.positive_timeout('2.5'))
        for value in ['0', '-1', 'nan', 'inf', '-inf']:
            with self.subTest(value=value), self.assertRaises(argparse.ArgumentTypeError):
                tour.positive_timeout(value)

    def test_duration_probes_are_bounded_and_reject_invalid_media_lengths(self):
        with patch.object(tour.subprocess, 'check_output', return_value=b'12.5') as probe:
            self.assertEqual(12.5, tour.duration(Path('clip.mp4'), timeout=17))
            self.assertEqual(17, probe.call_args.kwargs['timeout'])
        for value in [b'0', b'-1', b'nan', b'inf']:
            with self.subTest(value=value), patch.object(tour.subprocess, 'check_output', return_value=value):
                with self.assertRaisesRegex(ValueError, 'Invalid media duration'):
                    tour.duration(Path('clip.mp4'))

    def arguments(self, root):
        clips = root / 'clips'
        clips.mkdir()
        (clips / 'clip.mp4').write_bytes(b'fixture')
        manifest = root / 'manifest.json'
        manifest.write_text(json.dumps({'chapters': [
            {'id': 'workout', 'title': 'Workout', 'clips': ['clip.mp4']}
        ], 'known_limits': []}))
        exclusions = root / 'exclusions.json'
        exclusions.write_text('{}')
        return ['tour', str(manifest), '--clips', str(clips), '--exclusions', str(exclusions),
                '--output', str(root / 'output'), '--probe-timeout', '17', '--encode-timeout', '120']

    def test_probe_and_encoding_limits_are_forwarded_to_every_subprocess(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(sys, 'argv', self.arguments(root)), \
                    patch.object(tour, 'duration', return_value=1.5) as duration, \
                    patch.object(tour.subprocess, 'run') as encode, \
                    contextlib.redirect_stdout(io.StringIO()):
                tour.main()
            self.assertEqual(3, duration.call_count)
            self.assertTrue(all(call.kwargs['timeout'] == 17 for call in duration.call_args_list))
            self.assertEqual(2, encode.call_count)
            self.assertTrue(all(call.kwargs['timeout'] == 120 and call.kwargs['check']
                                for call in encode.call_args_list))
            result = json.loads((root / 'output/index.json').read_text())
            self.assertEqual('assembled-review-required', result['status'])
            self.assertEqual(1.5, result['duration'])

    def test_encoding_timeout_does_not_publish_a_success_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(sys, 'argv', self.arguments(root)), \
                    patch.object(tour, 'duration', return_value=1.5), \
                    patch.object(tour.subprocess, 'run', side_effect=subprocess.TimeoutExpired('ffmpeg', 120)):
                with self.assertRaises(subprocess.TimeoutExpired):
                    tour.main()
            self.assertFalse((root / 'output/index.json').exists())

    def test_failed_refresh_invalidates_all_old_indexes_before_overwriting_media(self):
        for fail_at in (1, 2):
            with self.subTest(encode=fail_at), tempfile.TemporaryDirectory() as directory:
                root = Path(directory)
                arguments = self.arguments(root)
                output = root / 'output'
                output.mkdir()
                indexes = [output / name for name in ('index.json', 'index.html', 'INDEX.md')]
                for index in indexes:
                    index.write_text('previous completed build')
                (output / '01-workout.mp4').write_bytes(b'previous chapter')
                calls = 0

                def encode(command, **kwargs):
                    nonlocal calls
                    calls += 1
                    self.assertTrue(all(not index.exists() for index in indexes))
                    Path(command[-1]).write_bytes(b'partially replaced media')
                    if calls == fail_at:
                        raise subprocess.TimeoutExpired('ffmpeg', kwargs['timeout'])
                    return subprocess.CompletedProcess(command, 0)

                with patch.object(sys, 'argv', arguments), \
                        patch.object(tour, 'duration', return_value=1.5), \
                        patch.object(tour.subprocess, 'run', side_effect=encode), \
                        contextlib.redirect_stdout(io.StringIO()):
                    with self.assertRaises(subprocess.TimeoutExpired):
                        tour.main()
                self.assertTrue(all(not index.exists() for index in indexes))

    def test_completion_receipt_is_not_published_when_a_human_index_write_fails(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            arguments = self.arguments(root)
            original = tour.write_text_atomic

            def publish(path, contents):
                if path.name == 'index.html':
                    raise OSError('publication failed')
                original(path, contents)

            with patch.object(sys, 'argv', arguments), \
                    patch.object(tour, 'duration', return_value=1.5), \
                    patch.object(tour.subprocess, 'run'), \
                    patch.object(tour, 'write_text_atomic', side_effect=publish), \
                    contextlib.redirect_stdout(io.StringIO()):
                with self.assertRaisesRegex(OSError, 'publication failed'):
                    tour.main()
            self.assertFalse((root / 'output/index.json').exists())

    def test_atomic_metadata_write_keeps_old_bytes_and_cleans_temporary_on_error(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            target = root / 'index.json'
            target.write_text('previous metadata')
            with patch.object(Path, 'replace', side_effect=OSError('replace failed')):
                with self.assertRaisesRegex(OSError, 'replace failed'):
                    tour.write_text_atomic(target, 'new metadata')
            self.assertEqual('previous metadata', target.read_text())
            self.assertEqual([target], list(root.iterdir()))


if __name__ == '__main__':
    unittest.main()
