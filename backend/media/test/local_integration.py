#!/usr/bin/env python3
"""Verify a loopback Docker deployment using one exact original video.

Start the isolated compose project as documented first. This is intentionally not
a deployment tool and refuses non-loopback endpoints. It writes no credentials.
"""
import argparse
import hashlib
import json
from pathlib import Path
import sys
from urllib.error import HTTPError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

project = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(project / 'tooling'))
from upload_exercise_media import load_token, transfer


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api', default='http://127.0.0.1:18310')
    parser.add_argument('--s3', default='http://127.0.0.1:18311')
    parser.add_argument('--reference', type=Path, default=project / 'openfactor/app/src/main/assets/reference')
    parser.add_argument('--report', type=Path, default=Path(__file__).resolve().parents[1] / '.local/integration-report.json')
    args = parser.parse_args()
    for endpoint in (args.api, args.s3):
        if urlparse(endpoint).hostname not in ('127.0.0.1', 'localhost', '::1'): parser.error('Integration tests only use loopback')
    backend = Path(__file__).resolve().parents[1]
    index = json.loads((backend / 'video-index.json').read_text())
    name, expected = next(iter(sorted(index['videos'].items())))
    data = (args.reference / 'videos' / name).read_bytes()
    checks = []

    def check(label, condition):
        if not condition: raise AssertionError(label)
        checks.append(label)

    with urlopen(args.api + '/readyz') as response: check('ready bucket', response.status == 200)
    try:
        urlopen(Request(args.api + '/admin/videos/' + name, method='PUT', data=data))
        raise AssertionError('Anonymous upload accepted')
    except HTTPError as error: check('anonymous upload rejected', error.code == 401)
    result = transfer(args.api, args.reference / 'videos', name, expected, load_token(backend / '.env', None), True)
    check('indexed original upload', result['status'] in ('uploaded-and-verified', 'already-present'))
    with urlopen(args.api + '/videos/' + name) as response:
        body = response.read()
        tag = response.headers['ETag']
        check('full original SHA-256', len(body) == expected['bytes'] and hashlib.sha256(body).hexdigest() == expected['sha256'])
        check('immutable cache contract', 'immutable' in response.headers['Cache-Control'])
    with urlopen(Request(args.api + '/videos/' + name, method='HEAD')) as response:
        check('HEAD length/hash', int(response.headers['Content-Length']) == len(data) and response.headers['X-Content-SHA256'] == expected['sha256'])
        check('HEAD has no body', response.read() == b'')
    for value, start, end in [('bytes=0-1023', 0, 1023), ('bytes=-307', len(data) - 307, len(data) - 1), ('bytes=2048-', 2048, len(data) - 1)]:
        with urlopen(Request(args.api + '/videos/' + name, headers={'Range': value})) as response:
            check(f'206 exact range {value}', response.status == 206 and response.read() == data[start:end + 1] and response.headers['Content-Range'] == f'bytes {start}-{end}/{len(data)}')
    try:
        urlopen(Request(args.api + '/videos/' + name, headers={'Range': f'bytes={len(data)}-'}))
        raise AssertionError('Invalid range accepted')
    except HTTPError as error: check('416 unsatisfiable range', error.code == 416 and error.headers['Content-Range'] == f'bytes */{len(data)}')
    try:
        urlopen(Request(args.api + '/videos/' + name, headers={'If-None-Match': tag}))
        raise AssertionError('Conditional cache ignored')
    except HTTPError as error: check('304 conditional ETag', error.code == 304 and error.read() == b'')
    try:
        urlopen(args.s3 + '/open-factor-videos/videos/' + name)
        raise AssertionError('Private bucket readable anonymously')
    except HTTPError as error: check('RustFS bucket denies unsigned reads', error.code in (401, 403))
    report = {'environment': 'isolated local Docker RustFS + Hono/Bun', 'video': name, 'bytes': len(data), 'sha256': expected['sha256'], 'upload_status': result['status'], 'indexed_videos': index['count'], 'checks': checks, 'passed': len(checks)}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__': main()
