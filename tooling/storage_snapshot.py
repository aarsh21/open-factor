"""Read debug storage snapshots; public restore backups never need this resolver."""
import hashlib
import json
import re


def resolve_snapshot(data, read_private, preferences_name='open_factor'):
    key = data.get('storageHistory')
    if key is None:
        return data
    if not isinstance(key, str) or re.fullmatch(r'[0-9a-f]{64}', key) is None:
        raise ValueError('Invalid history archive identity')
    owner = hashlib.sha256(preferences_name.encode('utf-8')).hexdigest()
    contents = read_private(f'files/workout-history/{owner}/{key}.json')
    if isinstance(contents, str):
        contents = contents.encode('utf-8')
    if hashlib.sha256(contents).hexdigest() != key:
        raise ValueError('History archive checksum mismatch')
    history = json.loads(contents)
    if not isinstance(history, list):
        raise ValueError('History archive must contain a list')
    return {k: v for k, v in data.items() if k != 'storageHistory'} | {'history': history}


def snapshot_data(root, adb, package='org.openfactor.app'):
    data = json.loads(next(node.text for node in root if node.get('name') == 'data'))
    return resolve_snapshot(data, lambda path: adb('exec-out', 'run-as', package, 'cat', path, raw=True))
