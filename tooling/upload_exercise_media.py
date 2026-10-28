#!/usr/bin/env python3
"""Validate and upload catalog-bound exercise videos; dry run unless --upload.

Standard library only. Credentials come from an environment variable or private
file, never a command-line token. Every new upload is read back and SHA-verified.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


def load_token(env_file, token_file):
    if token_file:
        return token_file.read_text().strip()
    if env_file:
        for line in env_file.read_text().splitlines():
            if line.startswith('MEDIA_ADMIN_TOKEN='):
                return line.split('=', 1)[1].strip().strip('"').strip("'")
        raise ValueError('MEDIA_ADMIN_TOKEN is absent from the private environment file')
    return os.environ.get('MEDIA_ADMIN_TOKEN', '')


def transfer(base, source, name, expected, token, upload):
    path = source / name
    if path.is_symlink() or not path.is_file() or path.stat().st_size != expected['bytes']:
        raise ValueError(f'Source length does not match index: {name}')
    with path.open('rb') as stream:
        local_hash = hashlib.file_digest(stream, 'sha256').hexdigest()
    if local_hash != expected['sha256']:
        raise ValueError(f'Source checksum does not match index: {name}')
    if not upload:
        return {'filename': name, 'status': 'validated', 'bytes': expected['bytes']}
    url = f'{base}/videos/{name}'
    verify_readback = False
    for attempt in range(4):
        try:
            exists = False
            try:
                with urlopen(Request(url, method='HEAD'), timeout=45) as response:
                    exists = int(response.headers.get('Content-Length', '-1')) == expected['bytes'] and response.headers.get('X-Content-SHA256') == local_hash
            except HTTPError as error:
                code = error.code
                error.close()
                if code != 404: raise
            if exists and not verify_readback:
                return {'filename': name, 'status': 'already-present', 'bytes': expected['bytes']}
            if not exists:
                payload = path.read_bytes()  # Index entries are capped at 32 MiB.
                request = Request(f'{base}/admin/videos/{name}', data=payload, method='PUT',
                                  headers={'Authorization': f'Bearer {token}', 'Content-Type': 'video/mp4'})
                # A lost PUT receipt still requires full readback on the next retry.
                verify_readback = True
                with urlopen(request, timeout=120) as response:
                    result = json.load(response)
                    if response.status != 201 or result.get('sha256') != local_hash: raise ValueError(f'Upload receipt mismatch: {name}')
            with urlopen(Request(url, method='HEAD'), timeout=45) as response:
                if int(response.headers.get('Content-Length', '-1')) != expected['bytes'] or response.headers.get('X-Content-SHA256') != local_hash:
                    raise ValueError(f'Uploaded HEAD verification failed: {name}')
            remote_hash, remote_bytes = hashlib.sha256(), 0
            with urlopen(url, timeout=120) as response:
                while block := response.read(1024 * 1024):
                    remote_hash.update(block)
                    remote_bytes += len(block)
                    if remote_bytes > expected['bytes']: raise ValueError(f'Readback exceeded expected bytes: {name}')
            if remote_bytes != expected['bytes'] or remote_hash.hexdigest() != local_hash:
                raise ValueError(f'Uploaded full-body checksum verification failed: {name}')
            return {'filename': name, 'status': 'uploaded-and-verified', 'bytes': remote_bytes}
        except (HTTPError, URLError, TimeoutError, ConnectionError, http.client.IncompleteRead) as error:
            retryable = not isinstance(error, HTTPError) or error.code in (408, 429, 500, 502, 503, 504)
            if isinstance(error, HTTPError): error.close()
            if not retryable or attempt == 3:
                # Do not include exception/header contents, which may contain authorization details.
                raise RuntimeError(f'Media transfer failed for {name}; HTTP {getattr(error, "code", "network")}') from None
            time.sleep(2 ** attempt)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', required=True)
    parser.add_argument('--videos', required=True, type=Path)
    parser.add_argument('--index', type=Path, default=Path(__file__).resolve().parents[1] / 'backend/media/video-index.json')
    parser.add_argument('--env-file', type=Path)
    parser.add_argument('--token-file', type=Path)
    parser.add_argument('--workers', type=int, choices=range(1, 5), default=3)
    parser.add_argument('--filename', action='append', help='Limit to explicitly selected indexed filenames')
    parser.add_argument('--upload', action='store_true')
    parser.add_argument('--allow-local-http', action='store_true')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    endpoint = urlparse(args.base_url)
    local = endpoint.hostname in ('127.0.0.1', 'localhost', '::1')
    if endpoint.scheme != 'https' and not (args.allow_local_http and local and endpoint.scheme == 'http'):
        parser.error('Uploads require HTTPS; --allow-local-http permits loopback integration tests only')
    if not endpoint.netloc or endpoint.username or endpoint.password or endpoint.query or endpoint.fragment or endpoint.path not in ('', '/'):
        parser.error('Use a service origin without credentials, path, query or fragment')
    index = json.loads(args.index.read_text())
    records = index['videos']
    if index.get('schema') != 1 or index.get('count') != len(records) or len(records) != 1831:
        parser.error('Expected the complete 1,831-video index')
    selected = sorted(set(args.filename or records))
    for name in selected:
        if not re.fullmatch(r'[0-9a-f]{32}_[a-z]\.mp4', name) or name not in records or not 12 <= records[name]['bytes'] <= 32 * 1024 * 1024:
            parser.error('Invalid selected catalog entry')
    token = load_token(args.env_file, args.token_file) if args.upload else ''
    if args.upload and len(token) < 32: parser.error('Set MEDIA_ADMIN_TOKEN or provide its private file')
    results = []
    with ThreadPoolExecutor(max_workers=args.workers) as executor:
        jobs = {executor.submit(transfer, args.base_url.rstrip('/'), args.videos, name, records[name], token, args.upload): name for name in selected}
        for job in as_completed(jobs):
            result = job.result()
            results.append(result)
            print(f'{len(results)}/{len(selected)} {result["status"]} {result["filename"]}', flush=True)
    report = {'origin': args.base_url, 'upload_requested': args.upload, 'files': sorted(results, key=lambda item: item['filename']), 'bytes': sum(item['bytes'] for item in results)}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'files': len(results), 'bytes': report['bytes'], 'upload_requested': args.upload}))


if __name__ == '__main__':
    main()
