#!/usr/bin/env python3
"""Generate metadata only; original catalog and MP4 files are never changed."""
import argparse
import hashlib
import json
from pathlib import Path
import re


def generate(reference: Path, output: Path):
    catalog = json.loads((reference / 'catalog.json').read_text())
    expected = {Path(value).name for exercise in catalog for value in exercise.get('videoObjectPaths', [])}
    paths = {path.name: path for path in (reference / 'videos').glob('*.mp4')}
    if len(expected) != 1831 or set(paths) != expected:
        raise ValueError('The catalog and local files must contain the same exact 1,831 videos')
    records = {}
    for name, path in sorted(paths.items()):
        if not re.fullmatch(r'[0-9a-f]{32}_[a-z]\.mp4', name): raise ValueError('Unexpected video filename')
        size = path.stat().st_size
        if not 12 <= size <= 32 * 1024 * 1024: raise ValueError('Video exceeds upload bound')
        with path.open('rb') as stream:
            if stream.read(12)[4:8] != b'ftyp': raise ValueError('Not an MP4 file')
            stream.seek(0)
            records[name] = {'bytes': size, 'sha256': hashlib.file_digest(stream, 'sha256').hexdigest()}
    result = {'schema': 1, 'count': len(records), 'total_bytes': sum(value['bytes'] for value in records.values()),
              'source_catalog_sha256': hashlib.sha256((reference / 'catalog.json').read_bytes()).hexdigest(), 'videos': records}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({key: value for key, value in result.items() if key != 'videos'}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--reference', required=True, type=Path)
    parser.add_argument('--output', type=Path, default=Path(__file__).parent / 'video-index.json')
    args = parser.parse_args()
    generate(args.reference, args.output)
