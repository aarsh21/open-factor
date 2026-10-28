import hashlib
import io
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
