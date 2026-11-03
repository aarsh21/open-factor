import contextlib
import hashlib
from http.client import HTTPResponse
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import MagicMock, patch
from urllib.request import Request

import import_licensed_previews as previews


PNG = b'\x89PNG\r\n\x1a\nnew image'
API_URL = 'https://wger.de/api/v2/exerciseimage/?limit=1000'


def http_response(body, headers=()):
    """Exercise real HTTPResponse framing/EOF behavior without a network socket."""
    socket = MagicMock()
    raw_headers = ''.join(f'{key}: {value}\r\n' for key, value in headers).encode('utf-8')
    socket.makefile.return_value = io.BytesIO(b'HTTP/1.1 200 OK\r\n' + raw_headers + b'\r\n' + body)
    response = HTTPResponse(socket)
    response.begin()
    return response


def snapshot(root):
    return {str(path.relative_to(root)): path.read_bytes() for path in root.rglob('*') if path.is_file()}


@contextlib.contextmanager
def refresh_fixture():
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        (root / 'catalog.json').write_text(json.dumps([dict(id='catalog-id', name='Barbell bench press')]))
        (root / 'previews').mkdir()
        (root / 'previews/unrelated.png').write_bytes(b'leave this alone')
        sources = [dict(id=image_id, exercise=73, license=1, license_author='Everkinetic',
                        is_ai_generated=False, image=f'https://wger.de/image-{image_id}.png')
                   for image_id in (68, 70)]
        payloads = {API_URL: json.dumps(dict(next=None, results=sources)).encode()}
        entries = []
        for source in sources:
            asset = f"previews/wger-{source['id']}.png"
            old = PNG + str(source['id']).encode() + b' old'
            (root / asset).write_bytes(old)
            entries.append(dict(asset=asset, bytes=len(old), sha256=hashlib.sha256(old).hexdigest(),
                                author='Everkinetic', license_url=previews.LICENSE, exercise_id='catalog-id'))
            payloads[source['image']] = PNG + str(source['id']).encode()
        manifest = root / 'exercise-previews.json'
        manifest.write_text(json.dumps(dict(entries=entries)))

        def fetch(url):
            value = payloads[url]
            if isinstance(value, BaseException):
                raise value
            return value

        with (patch.object(previews, 'ASSETS', root), patch.object(previews, 'MANIFEST', manifest),
              patch.object(previews, 'MAPPINGS', {'Barbell bench press': (73, [68, 70])}),
              patch.object(previews, 'fetch', side_effect=fetch)):
            yield root, payloads


class LicensedPreviewsTest(unittest.TestCase):
    def test_remote_provenance_must_match_the_reviewed_join_author_and_license(self):
        reviewed = dict(id=68, exercise=73, license=1, license_author='Everkinetic', is_ai_generated=False)
        previews.validate_source(reviewed, 73)
        for changes in [dict(exercise=74), dict(license=2), dict(license_author='Other'), dict(is_ai_generated=True)]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                previews.validate_source(reviewed | changes, 73)

    def test_fetch_rejects_unrelated_and_local_urls_before_opening(self):
        for url in ['file:///tmp/private.png', 'http://wger.de/image.png',
                    'https://example.com/image.png', 'https://wger.de.evil.test/image.png',
                    'https://user:secret@wger.de/image.png', 'https://wger.de:444/image.png']:
            with self.subTest(url=url), patch.object(previews.urllib.request, 'build_opener') as opener:
                with self.assertRaises(ValueError):
                    previews.fetch(url)
                opener.assert_not_called()

    def test_redirect_cannot_downgrade_or_change_origin(self):
        request = Request('https://wger.de/image.png')
        handler = previews.WgerRedirectHandler()
        for destination in ['http://wger.de/image.png', 'https://example.com/image.png', 'file:///tmp/private.png']:
            with self.subTest(destination=destination), self.assertRaises(ValueError):
                handler.redirect_request(request, None, 302, 'Found', {}, destination)
        redirect = handler.redirect_request(request, None, 302, 'Found', {}, 'https://wger.de/new.png')
        self.assertEqual('https://wger.de/new.png', redirect.full_url)

    def test_fetch_is_bounded_and_keeps_a_timeout(self):
        response = MagicMock()
        response.__enter__.return_value = response
        response.read.return_value = b'original'
        response.headers.get_all.return_value = []
        opener = MagicMock()
        opener.open.return_value = response
        with patch.object(previews.urllib.request, 'build_opener', return_value=opener):
            self.assertEqual(b'original', previews.fetch('https://wger.de/image.png'))
            self.assertEqual(45, opener.open.call_args.kwargs['timeout'])
            response.read.assert_called_once_with(previews.MAX_DOWNLOAD_BYTES + 1)
            with patch.object(previews, 'MAX_DOWNLOAD_BYTES', 3):
                with self.assertRaisesRegex(ValueError, 'exceeds'):
                    previews.fetch('https://wger.de/image.png')

    def test_fetch_rejects_a_truncated_real_http_response(self):
        # A bounded read silently returns all 17 available bytes, not IncompleteRead.
        with http_response(PNG, [('Content-Length', '1000')]) as response:
            self.assertEqual(PNG, response.read(previews.MAX_DOWNLOAD_BYTES + 1))
            self.assertEqual(1000 - len(PNG), response.length)
        opener = MagicMock()
        opener.open.return_value = http_response(PNG, [('Content-Length', '1000')])
        with patch.object(previews.urllib.request, 'build_opener', return_value=opener):
            with self.assertRaisesRegex(ValueError, 'Incomplete.*Content-Length'):
                previews.fetch('https://wger.de/image.png')

    def test_fetch_rejects_invalid_or_oversized_declared_lengths_before_reading(self):
        cases = [[('Content-Length', value)] for value in
                 ('', '-1', '+17', '1.5', 'invalid', '17, 17', '１７', str(previews.MAX_DOWNLOAD_BYTES + 1))]
        cases.append([('Content-Length', str(len(PNG)))] * 2)
        for headers in cases:
            with self.subTest(headers=headers):
                response = http_response(PNG, headers)
                opener = MagicMock()
                opener.open.return_value = response
                with (patch.object(previews.urllib.request, 'build_opener', return_value=opener),
                      patch.object(response, 'read', wraps=response.read) as read):
                    with self.assertRaises(ValueError):
                        previews.fetch('https://wger.de/image.png')
                    read.assert_not_called()

    def test_fetch_accepts_complete_fixed_length_eof_and_chunked_bodies(self):
        cases = [(PNG, [('Content-Length', str(len(PNG)))]), (PNG, []),
                 (b'11\r\n' + PNG + b'\r\n0\r\n\r\n', [('Transfer-Encoding', 'chunked')])]
        for body, headers in cases:
            with self.subTest(headers=headers):
                opener = MagicMock()
                opener.open.return_value = http_response(body, headers)
                with patch.object(previews.urllib.request, 'build_opener', return_value=opener):
                    self.assertEqual(PNG, previews.fetch('https://wger.de/image.png'))

    def test_fetch_still_bounds_bodies_without_a_declared_length(self):
        opener = MagicMock()
        opener.open.return_value = http_response(PNG)
        with (patch.object(previews.urllib.request, 'build_opener', return_value=opener),
              patch.object(previews, 'MAX_DOWNLOAD_BYTES', len(PNG) - 1)):
            with self.assertRaisesRegex(ValueError, 'exceeds'):
                previews.fetch('https://wger.de/image.png')

    def test_failed_download_preserves_assets_and_manifest_after_another_image_changes(self):
        with refresh_fixture() as (root, payloads):
            before = snapshot(root)
            payloads['https://wger.de/image-70.png'] = OSError('download failed')
            with self.assertRaisesRegex(OSError, 'download failed'):
                previews.refresh()
            self.assertEqual(before, snapshot(root))
            self.assertFalse(list(root.glob('.licensed-previews-*')))
            with contextlib.redirect_stdout(io.StringIO()):
                previews.verify()

    def test_successful_refresh_publishes_original_bytes_and_compatible_attribution(self):
        with refresh_fixture() as (root, payloads):
            previews.refresh()
            manifest = json.loads(previews.MANIFEST.read_text())
            self.assertEqual(2, len(manifest['entries']))
            self.assertEqual('CC BY-SA 3.0', manifest['license'])
            self.assertEqual('Everkinetic', manifest['author'])
            self.assertEqual(previews.LICENSE, manifest['license_url'])
            for entry in manifest['entries']:
                self.assertEqual(f"previews/wger-{entry['wger_image_id']}.png", entry['asset'])
                self.assertEqual(payloads[entry['source_url']], (root / entry['asset']).read_bytes())
                self.assertEqual('None; original unmodified PNG', entry['changes'])
                self.assertEqual('https://wger.de/en/exercise/73/view/', entry['source_page'])
            self.assertEqual(b'leave this alone', (root / 'previews/unrelated.png').read_bytes())
            self.assertFalse(list(root.glob('.licensed-previews-*')))
            with contextlib.redirect_stdout(io.StringIO()):
                previews.verify()

    def test_manifest_staging_failure_preserves_existing_set(self):
        with refresh_fixture() as (root, _):
            before = snapshot(root)
            write_text = Path.write_text

            def fail_staging(path, *args, **kwargs):
                if path.name == 'exercise-previews.json' and path != previews.MANIFEST:
                    raise OSError('manifest staging failed')
                return write_text(path, *args, **kwargs)

            with patch.object(Path, 'write_text', autospec=True, side_effect=fail_staging):
                with self.assertRaisesRegex(OSError, 'manifest staging failed'):
                    previews.refresh()
            self.assertEqual(before, snapshot(root))
            self.assertFalse(list(root.glob('.licensed-previews-*')))

    def test_backup_failure_preserves_existing_set(self):
        with refresh_fixture() as (root, _):
            before = snapshot(root)
            copy2 = previews.shutil.copy2

            def fail_manifest_backup(source, destination):
                if source == previews.MANIFEST:
                    raise OSError('backup failed')
                return copy2(source, destination)

            with patch.object(previews.shutil, 'copy2', side_effect=fail_manifest_backup):
                with self.assertRaisesRegex(OSError, 'backup failed'):
                    previews.refresh()
            self.assertEqual(before, snapshot(root))
            self.assertFalse(list(root.glob('.licensed-previews-*')))

    def test_publication_failure_rolls_back_replacements_and_new_files(self):
        for failure_target in ('wger-70.png', 'exercise-previews.json'):
            with self.subTest(failure_target=failure_target), refresh_fixture() as (root, _):
                # Include a newly introduced asset so rollback must remove it.
                (root / 'previews/wger-70.png').unlink()
                manifest = json.loads(previews.MANIFEST.read_text())
                manifest['entries'] = manifest['entries'][:1]
                previews.MANIFEST.write_text(json.dumps(manifest))
                before = snapshot(root)
                replace = Path.replace

                def fail_publication(path, target):
                    if path.parent.name != 'backups' and Path(target).name == failure_target:
                        raise OSError('publication failed')
                    return replace(path, target)

                with patch.object(Path, 'replace', autospec=True, side_effect=fail_publication):
                    with self.assertRaisesRegex(OSError, 'publication failed'):
                        previews.refresh()
                self.assertEqual(before, snapshot(root))
                self.assertFalse(list(root.glob('.licensed-previews-*')))
                with contextlib.redirect_stdout(io.StringIO()):
                    previews.verify()

    def test_rollback_failure_is_reported_and_retains_recovery_copies(self):
        with refresh_fixture() as (root, _):
            old_asset = (root / 'previews/wger-68.png').read_bytes()
            old_manifest = previews.MANIFEST.read_bytes()
            replace = Path.replace

            def fail_publication_and_rollback(path, target):
                if Path(target) == previews.MANIFEST or path.parent.name == 'backups':
                    raise OSError('replacement unavailable')
                return replace(path, target)

            with patch.object(Path, 'replace', autospec=True, side_effect=fail_publication_and_rollback):
                with self.assertRaisesRegex(RuntimeError, 'rollback incomplete.*Recovery files retained'):
                    previews.refresh()
            staging, = root.glob('.licensed-previews-*')
            self.assertEqual(old_asset, (staging / 'backups/0').read_bytes())
            self.assertEqual(old_manifest, previews.MANIFEST.read_bytes())
            self.assertEqual(b'leave this alone', (root / 'previews/unrelated.png').read_bytes())

    def test_offline_verification_rejects_altered_bytes_and_attribution(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            asset = root / 'previews/wger-68.png'
            asset.parent.mkdir()
            asset.write_bytes(b'original')
            entry = dict(asset='previews/wger-68.png', sha256=hashlib.sha256(b'original').hexdigest(),
                         bytes=8, author='Everkinetic', license_url=previews.LICENSE, exercise_id='catalog-id')
            manifest = root / 'exercise-previews.json'
            with patch.object(previews, 'ASSETS', root), patch.object(previews, 'MANIFEST', manifest):
                manifest.write_text(json.dumps(dict(entries=[entry])))
                with contextlib.redirect_stdout(io.StringIO()):
                    previews.verify()
                for changes in [dict(sha256='a' * 64), dict(bytes=9), dict(author='Other'), dict(license_url='other')]:
                    manifest.write_text(json.dumps(dict(entries=[entry | changes])))
                    with self.subTest(changes=changes), self.assertRaises(ValueError):
                        previews.verify()

    def test_provenance_validation_still_runs_with_optimized_python(self):
        code = (
            'import sys; sys.path.insert(0, sys.argv[1]); '
            'from import_licensed_previews import validate_source; '
            "validate_source(dict(id=68, exercise=73, license=2, license_author='Other', is_ai_generated=False), 73)"
        )
        result = subprocess.run([sys.executable, '-I', '-O', '-c', code, str(Path(__file__).resolve().parent)],
                                capture_output=True, text=True, timeout=30)
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Unreviewed license or author', result.stderr)


if __name__ == '__main__':
    unittest.main()
