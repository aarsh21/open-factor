import hashlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
from urllib.error import HTTPError, URLError

sys.path.insert(0, str(Path(__file__).resolve().parents[3] / 'tooling'))
import upload_exercise_media as uploader


class Reply(io.BytesIO):
    def __init__(self, payload=b'', headers=None, status=200):
        super().__init__(payload)
        self.headers = headers or {}
        self.status = status


class UploaderTest(unittest.TestCase):
    def receipt_fixture(self, directory):
        entries = {f'{i:032x}_a.mp4': {'bytes': 32, 'sha256': 'a' * 64} for i in range(1831)}
        index = Path(directory) / 'index.json'
        index.write_text(json.dumps({'schema': 1, 'count': 1831, 'videos': entries}))
        records = [{'filename': name, 'status': 'uploaded-and-verified', **value} for name, value in list(entries.items())[:-1]]
        receipt = Path(directory) / 'receipt.json'
        uploader.atomic_report(receipt, {'origin': 'https://example.com', 'read_prefix': '/v1',
            'source_index_sha256': hashlib.sha256(index.read_bytes()).hexdigest(), 'files': records})
        args = ['upload_exercise_media.py', '--base-url', 'https://example.com', '--videos', directory,
                '--index', str(index), '--upload', '--verify-existing', '--resume-report', str(receipt),
                '--report', str(receipt), '--batch-size', '1']
        return entries, receipt, args

    def test_resume_only_transfers_pending_object_and_checkpoints_complete_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            entries, receipt, args = self.receipt_fixture(directory)
            pending = list(entries)[-1]
            result = {'filename': pending, 'status': 'existing-and-verified', **entries[pending]}
            with patch.object(sys, 'argv', args), patch.dict(os.environ, {'MEDIA_ADMIN_TOKEN': 'test-token-' * 8}), \
                 patch.object(uploader, 'transfer', return_value=result) as transfer, patch.object(sys, 'stdout', io.StringIO()):
                uploader.main()
            transfer.assert_called_once()
            self.assertEqual(transfer.call_args.args[2], pending)
            self.assertTrue(transfer.call_args.kwargs['verify_existing'])
            final = json.loads(receipt.read_text())
            self.assertEqual(final['completed_files'], 1831)
            self.assertEqual(final['remaining_files'], 0)
            self.assertEqual({item['filename'] for item in final['files']}, set(entries))
            self.assertEqual(final['bytes'], 1831 * 32)
            self.assertEqual(final['failures'], [])

    def test_tampered_resume_checksum_cannot_skip_verification_or_overwrite_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            _, receipt, args = self.receipt_fixture(directory)
            changed = json.loads(receipt.read_text())
            changed['files'][0]['sha256'] = 'b' * 64
            uploader.atomic_report(receipt, changed)
            original = receipt.read_bytes()
            with patch.object(sys, 'argv', args), patch.dict(os.environ, {'MEDIA_ADMIN_TOKEN': 'test-token-' * 8}), \
                 patch.object(uploader, 'transfer') as transfer, patch.object(sys, 'stderr', io.StringIO()):
                with self.assertRaises(SystemExit) as failure: uploader.main()
            self.assertEqual(failure.exception.code, 2)
            transfer.assert_not_called()
            self.assertEqual(receipt.read_bytes(), original)

    def test_failed_checkpoint_replace_keeps_previous_complete_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'receipt.json'
            uploader.atomic_report(path, {'completed_files': 1})
            original = path.read_bytes()
            with patch.object(Path, 'replace', side_effect=OSError('interrupted rename')):
                with self.assertRaises(OSError): uploader.atomic_report(path, {'completed_files': 2})
            self.assertEqual(path.read_bytes(), original)
            uploader.atomic_report(path, {'completed_files': 2})
            self.assertIn('"completed_files": 2', path.read_text())

    def test_lost_put_receipt_still_requires_full_readback_instead_of_head_only_skip(self):
        data = b'\x00\x00\x00\x18ftypisom-original-video-payload'
        expected = {'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
        stored, calls = False, []

        def request(value, **_):
            nonlocal stored
            method = value.get_method() if not isinstance(value, str) else 'GET'
            calls.append(method)
            if method == 'HEAD':
                if not stored: raise HTTPError('http://localhost/video', 404, 'missing', {}, None)
                return Reply(headers={'Content-Length': str(len(data)), 'X-Content-SHA256': expected['sha256']})
            if method == 'PUT':
                stored = True
                raise URLError('receipt lost after durable upload')
            return Reply(data)

        with tempfile.TemporaryDirectory() as directory:
            (Path(directory) / 'video.mp4').write_bytes(data)
            with patch.object(uploader, 'urlopen', side_effect=request), patch.object(uploader.time, 'sleep'):
                result = uploader.transfer('http://localhost', Path(directory), 'video.mp4', expected, 'test-token', True)
        self.assertEqual(result['status'], 'uploaded-and-verified')
        self.assertEqual(calls.count('PUT'), 1)
        self.assertEqual(calls.count('GET'), 1)

    def test_changed_local_original_cannot_reach_network(self):
        with tempfile.TemporaryDirectory() as directory:
            (Path(directory) / 'video.mp4').write_bytes(b'changed-source')
            with patch.object(uploader, 'urlopen') as request:
                with self.assertRaisesRegex(ValueError, 'checksum'):
                    uploader.transfer('https://example.com', Path(directory), 'video.mp4', {'bytes': 14, 'sha256': '0' * 64}, 'test-token', True)
                request.assert_not_called()


if __name__ == '__main__': unittest.main()
