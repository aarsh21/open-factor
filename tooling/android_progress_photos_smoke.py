#!/usr/bin/env python3
"""Verify local progress-photo attachment and complete JSON backup on a disposable emulator.

Requires adb and Python 3. Uses synthetic PNGs, deletes only its own Download files,
and requires an empty progress-photo history. Existing training/body metrics stay intact.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET
import zlib

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Use a disposable emulator, never a physical phone')
args.output.mkdir(parents=True, exist_ok=True)


def adb(*command, raw=False):
    result = subprocess.check_output(['adb', '-s', args.serial, *command], timeout=60)
    return result if raw else result.decode()


def nodes():
    for attempt in range(8):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/of-photo-ui.xml')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/of-photo-ui.xml')).iter('node'))
        except (ET.ParseError, subprocess.CalledProcessError):
            if attempt == 7:
                raise
            time.sleep(.3)


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))


def target(label, attr='text', last=False):
    for _ in range(12):
        tree = nodes()
        found = [n for n in tree if n.get(attr, '').casefold() == label.casefold() and bounds(n)[3] - bounds(n)[1] >= 30]
        if found:
            return found[-1 if last else 0]
        scrolls = [n for n in tree if n.get('scrollable') == 'true']
        if scrolls:
            left, top, right, bottom = bounds(max(scrolls, key=lambda n: bounds(n)[3] - bounds(n)[1]))
            x = (left + right) // 2
            adb('shell', 'input', 'swipe', str(x), str(top + (bottom-top)*4//5), str(x), str(top + (bottom-top)//5), '350')
        else:
            time.sleep(.3)
    capture('failure')
    raise AssertionError((label, attr))


def tap(label, attr='text', last=False):
    left, top, right, bottom = bounds(target(label, attr, last))
    adb('shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2))


def enter(label, value, attr='text', last=False):
    tap(label, attr, last)
    focused = [n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true']
    assert len(focused) == 1
    old = focused[0].get('text', '')
    if old:
        counts = ' '.join(str(i) for i in range(len(old)+1))
        adb('shell', f'for i in {counts}; do input keyevent 67; input keyevent 112; done')
    adb('shell', 'input', 'text', value)
    adb('shell', 'input', 'keyevent', '4')


def capture(name):
    nodes()
    (args.output / (name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p', raw=True))
    (args.output / (name+'.xml')).write_text(adb('shell', 'cat', '/sdcard/of-photo-ui.xml'))


def state():
    root = ET.fromstring(adb('shell', 'run-as', 'org.openfactor.app', 'cat', 'shared_prefs/open_factor.xml'))
    return json.loads(next(n.text for n in root if n.get('name') == 'data'))


def private(reference):
    return adb('exec-out', 'run-as', 'org.openfactor.app', 'cat', 'no_backup/progress-photos/'+reference, raw=True)


def private_names():
    return set(adb('shell', 'run-as', 'org.openfactor.app', 'ls', 'no_backup/progress-photos').split())


def launch():
    adb('shell', 'am', 'force-stop', 'org.openfactor.app')
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', 'org.openfactor.app/.MainActivity')


def back():
    adb('shell', 'input', 'keyevent', '4')


def settings():
    launch()
    tap('Settings', last=True)


def choose(filename):
    target('Show roots', 'content-desc')
    tree = nodes()
    if any(n.get('content-desc') == 'List view' for n in tree):
        tap('List view', 'content-desc')
        tree = nodes()
    if not any(n.get('text') == filename for n in tree):
        roots = [n for n in tree if n.get('content-desc') in ('Show roots', 'Show navigation drawer')]
        if roots:
            tap(roots[0].get('content-desc'), 'content-desc')
            tap('Downloads', last=True)
    tap(filename)


def png(color, marker):
    def chunk(kind, payload):
        return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind+payload))
    width, height = 480, 720
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            white = x in range(25, 31) or x in range(449, 455) or y in range(25, 31) or y in range(689, 695) or abs(x - y//2 - marker*20) < 4
            rows.extend((240, 240, 240) if white else color)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')


wifi = adb('shell', 'settings', 'get', 'global', 'wifi_on').strip()
mobile = adb('shell', 'settings', 'get', 'global', 'mobile_data').strip()
font = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
try:
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    connectivity = adb('shell', 'dumpsys', 'connectivity')
    assert 'Active default network: none' in connectivity
    (args.output / 'connectivity.txt').write_text(connectivity)
    package = adb('shell', 'dumpsys', 'package', 'org.openfactor.app')
    assert 'android.permission.INTERNET' not in package
    (args.output / 'package.txt').write_text(package)
    apk = adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')
    (args.output / 'apk-sha256.txt').write_text(adb('shell', 'sha256sum', apk))
    fixtures = {}
    for i, (name, color) in enumerate([('front',(139,49,49)), ('side',(43,112,86)), ('back',(55,93,155)), ('replacement',(137,114,34))]):
        image = png(color, i)
        path = args.output / ('ofphoto-'+name+'.png')
        path.write_bytes(image)
        fixtures[name] = image
        adb('push', str(path), '/sdcard/Download/'+path.name)
        adb('shell', 'am', 'broadcast', '-a', 'android.intent.action.MEDIA_SCANNER_SCAN_FILE', '-d', 'file:///sdcard/Download/'+path.name)
    settings()
    baseline = state()
    assert not baseline.get('progressPhotos'), 'Use an emulator with no progress photos'
    tap('Body Data'); tap('Photos'); capture('photos-empty-history')
    tap('Log Photos'); capture('photos-empty-editor')
    tap('Save'); target('Attach at least one photo')
    assert state() == baseline
    enter('Date (YYYY-MM-DD)', '2026-10-15')
    tap('Add Front photo', 'content-desc'); target('Show roots', 'content-desc'); back()
    assert state() == baseline
    for position in ('Front', 'Side', 'Back'):
        tap('Add '+position+' photo', 'content-desc')
        choose('ofphoto-'+position.lower()+'.png')
        target('View '+position+' photo', 'content-desc')
    capture('photos-three-views')
    assert state() == baseline
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    capture('photos-font-130')
    tap('Save')
    target('Edit photos 2026-10-15', 'content-desc')
    saved = state()['progressPhotos'][0]
    assert len(state()['progressPhotos']) == 1 and saved['photos'] == {p: hashlib.sha256(fixtures[p]).hexdigest() for p in ('front','side','back')}
    adb('shell', 'settings', 'put', 'system', 'font_scale', font)
    capture('photos-history')
    # Remove the selected source files; reopening must use durable private copies.
    for name in ('front', 'side', 'back'):
        adb('shell', 'rm', '/sdcard/Download/ofphoto-'+name+'.png')
        assert private(saved['photos'][name]) == fixtures[name]
    settings(); tap('Body Data'); tap('Photos'); tap('Edit photos 2026-10-15', 'content-desc')
    tap('View Front photo', 'content-desc'); target('Front · 2026-10-15'); capture('photos-full-view'); back()
    target('Progress Photos')
    tap('Remove Side photo', 'content-desc'); back()
    assert state()['progressPhotos'][0] == saved
    assert set(saved['photos'].values()) <= private_names()
    tap('Edit photos 2026-10-15', 'content-desc')
    enter('Date (YYYY-MM-DD)', '2026-10-16')
    tap('Remove Side photo', 'content-desc'); tap('Save')
    target('Edit photos 2026-10-16', 'content-desc')
    changed = state()['progressPhotos'][0]
    assert changed['id'] == saved['id'] and 'side' not in changed['photos']
    assert saved['photos']['side'] not in private_names()
    tap('Edit photos 2026-10-16', 'content-desc')
    tap('Replace Back photo', 'content-desc'); choose('ofphoto-replacement.png')
    target('View Back photo', 'content-desc'); back()
    assert state()['progressPhotos'][0] == changed
    assert hashlib.sha256(fixtures['replacement']).hexdigest() not in private_names()
    tap('Edit photos 2026-10-16', 'content-desc')
    tap('Replace Back photo', 'content-desc'); choose('ofphoto-replacement.png')
    target('View Back photo', 'content-desc'); tap('Save')
    target('Edit photos 2026-10-16', 'content-desc')
    final_record = state()['progressPhotos'][0]
    assert final_record['photos']['back'] == hashlib.sha256(fixtures['replacement']).hexdigest()
    assert saved['photos']['back'] not in private_names()
    capture('photos-edited-history')
    settings(); tap('Export Backup')
    filename = f'ofphoto-backup-{time.time_ns()}.json'
    edit = [n for n in nodes() if n.get('class') == 'android.widget.EditText'][0]
    enter(edit.get('resource-id'), filename, 'resource-id', last=True)
    tap('Save', last=True); target('Export Backup')
    capture('photos-backup-settings')
    exported = json.loads(adb('shell', 'cat', '/sdcard/Download/'+filename))
    metadata = {k:v for k,v in exported.items() if k != 'photoAssets'}
    assert metadata == state()
    assets = exported['photoAssets']
    assert set(assets) == set(final_record['photos'].values())
    for reference, encoded in assets.items():
        assert base64.b64decode(encoded) == private(reference)
    # Native restore rejects a missing attachment before it can replace any records.
    invalid = json.loads(json.dumps(exported)); invalid['photoAssets'].pop(next(iter(assets)))
    invalid_path = args.output / 'ofphoto-invalid.json'; invalid_path.write_text(json.dumps(invalid))
    adb('push', str(invalid_path), '/sdcard/Download/ofphoto-invalid.json')
    tap('Restore Backup'); choose('ofphoto-invalid.json'); target('Restore Backup')
    assert state() == metadata
    tap('Body Data'); tap('Photos'); tap('Edit photos 2026-10-16', 'content-desc')
    tap('Delete photo record', 'content-desc'); tap('Cancel'); assert state() == metadata
    tap('Delete photo record', 'content-desc'); capture('photos-delete-confirm'); tap('Delete')
    target('No progress photos yet'); assert not private_names()
    settings(); tap('Restore Backup'); choose(filename); target('Restore Backup?'); capture('photos-restore-confirm')
    tap('Cancel'); assert not state()['progressPhotos']
    tap('Restore Backup'); choose(filename); target('Restore Backup?'); tap('Restore'); target('Restore Backup')
    assert state() == metadata
    launch(); assert state() == metadata
    for reference, encoded in assets.items():
        assert base64.b64decode(encoded) == private(reference)
    assert {k:v for k,v in state().items() if k != 'progressPhotos'} == {k:v for k,v in baseline.items() if k != 'progressPhotos'}
    tap('Shortcuts', 'content-desc'); tap('Log Photos'); target('Progress Photos'); back()
    target('Edit photos 2026-10-16', 'content-desc'); capture('photos-restored-history')
    assert state() == metadata
    assert 'Active default network: none' in adb('shell', 'dumpsys', 'connectivity')
    result = {'status':'passed', 'serial':args.serial, 'offline':True, 'actual_local_picker':True,
              'private_original_bytes_after_source_removal':True, 'three_views_and_full_reopen':True,
              'date_edit_and_slot_removal':True, 'replacement_and_back_discard':True,
              'unsaved_draft_font_recreation':True, 'native_json_export_restore_and_cancel':True,
              'invalid_restore_unchanged':True, 'record_deletion_cleans_private_files':True,
              'process_restart':True, 'training_and_metrics_unchanged':True, 'source_save_parity':False}
    (args.output/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    (args.output/'final-state.json').write_text(json.dumps(state(),indent=2)+'\n')
    print(json.dumps(result,indent=2))
finally:
    adb('shell','settings','put','system','font_scale',font)
    if wifi in ('0','1'): adb('shell','svc','wifi','enable' if wifi == '1' else 'disable')
    if mobile in ('0','1'): adb('shell','svc','data','enable' if mobile == '1' else 'disable')
