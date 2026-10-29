#!/usr/bin/env python3
"""Check compact photo sheets, caller return and complete backups on an offline emulator.

Preserves existing records and private photos. Leaves one synthetic photo record.
Uses real document pickers; never uses a phone or modifies original exercise assets.
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
            dumped = adb('shell', 'uiautomator', 'dump', '/sdcard/of-photo-sheet-ui.xml')
            if 'dumped to:' not in dumped:
                raise RuntimeError('UI hierarchy unavailable; do not read a stale dump')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/of-photo-sheet-ui.xml')).iter('node'))
        except (ET.ParseError, subprocess.CalledProcessError, RuntimeError):
            if attempt == 7:
                raise
            time.sleep(.3)


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))


def capture(name):
    nodes()
    (args.output / (name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p', raw=True))
    (args.output / (name+'.xml')).write_text(adb('shell', 'cat', '/sdcard/of-photo-sheet-ui.xml'))


def target(label, attr='text', last=False, scroll=True):
    for _ in range(16):
        tree = nodes()
        found = [n for n in tree if n.get(attr, '').casefold() == label.casefold() and bounds(n)[3] > bounds(n)[1] and bounds(n)[2] > bounds(n)[0]]
        if found:
            return found[-1 if last else 0]
        scrolls = [n for n in tree if n.get('scrollable') == 'true']
        if scroll and scrolls:
            left, top, right, bottom = bounds(scrolls[-1])
            # Small, slow drags do not skip Settings entries at enlarged text sizes.
            adb('shell', 'input', 'swipe', str((left+right)//2), str(top+(bottom-top)*3//5), str((left+right)//2), str(top+(bottom-top)*2//5), '600')
        else:
            time.sleep(.2)
    capture('failure')
    raise AssertionError((label, attr))


def tap(label, attr='text', last=False):
    left, top, right, bottom = bounds(target(label, attr, last))
    adb('shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2))


def back():
    adb('shell', 'input', 'keyevent', '4')


def enter(label, value, attr='text', last=False):
    tap(label, attr, last)
    field = next(n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true')
    counts = ' '.join(str(i) for i in range(len(field.get('text', ''))+1))
    adb('shell', f'for i in {counts}; do input keyevent 67; input keyevent 112; done')
    if value:
        adb('shell', 'input', 'text', value)
    back()
    nodes()


def state():
    root = ET.fromstring(adb('shell', 'run-as', 'org.openfactor.app', 'cat', 'shared_prefs/open_factor.xml'))
    from storage_snapshot import snapshot_data
    return snapshot_data(root, adb)


def private(reference):
    return adb('exec-out', 'run-as', 'org.openfactor.app', 'cat', 'no_backup/progress-photos/'+reference, raw=True)


def private_names():
    return set(adb('shell', 'run-as', 'org.openfactor.app', 'ls', 'no_backup/progress-photos').split())


def photo_bytes(data):
    return {ref: private(ref) for record in data.get('progressPhotos', []) for ref in record['photos'].values()}


def launch():
    adb('shell', 'am', 'force-stop', 'org.openfactor.app')
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', 'org.openfactor.app/.MainActivity')


def settings():
    launch(); tap('Settings', last=True)


def history():
    settings(); tap('Body Data'); tap('Photos')


def record_date(day):
    tap('Change photo date', 'content-desc')
    enter('Date (YYYY-MM-DD)', day)
    tap('Apply date')
    target('/'.join(reversed(day.split('-'))))


def choose(filename):
    target('Show roots', 'content-desc')
    tree = nodes()
    if any(n.get('content-desc') == 'List view' for n in tree):
        tap('List view', 'content-desc'); tree = nodes()
    if not any(n.get('text') == filename for n in tree):
        tap('Show roots', 'content-desc'); tap('Downloads', last=True)
    tap(filename)


def dismiss(method, close='Close photos'):
    if method == 'close':
        tap(close, 'content-desc')
    elif method == 'back':
        back()
    else:
        left, top, right, bottom = bounds(target(close, 'content-desc'))
        height = max(bounds(n)[3] for n in nodes())
        if method == 'scrim':
            left, top, right, bottom = bounds(target('Close sheet', 'content-desc'))
            display = adb('shell', 'dumpsys', 'window', 'displays')
            safe_top = max([0] + [int(y) for y in re.findall(r'type=(?:statusBars|displayCutout) frame=\[0,0\]\[\d+,(\d+)\]', display)])
            density = int(adb('shell', 'wm', 'density').split()[-1]) / 160
            assert bottom - max(top, safe_top) >= 48 * density - 1, 'Outside dismissal needs a reachable 48dp strip below system insets'
            adb('shell', 'input', 'tap', str((left+right)//2), str((max(top, safe_top)+bottom)//2))
        elif method == 'drag':
            if close == 'Close measurements':
                left, top, right, bottom = bounds(target('Drag handle', 'content-desc'))
                start = (top+bottom)//2
            else:
                # Photos uses a compact custom handle without Material's label.
                start = top-15
            adb('shell', 'input', 'swipe', str((left+right)//2), str(start), str((left+right)//2), str(height-80), '600')
        else:
            raise AssertionError(method)


def png(color, marker, tag):
    def chunk(kind, payload):
        return struct.pack('>I', len(payload))+kind+payload+struct.pack('>I', zlib.crc32(kind+payload))
    width, height = 480, 720
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            edge = x in range(25, 31) or x in range(449, 455) or y in range(25, 31) or y in range(689, 695) or abs(x-y//2-marker*20) < 4
            rows.extend((240, 240, 240) if edge else color)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))+chunk(b'tEXt', b'Fixture\0'+tag.encode())+chunk(b'IDAT', zlib.compress(rows))+chunk(b'IEND', b'')


wifi = adb('shell', 'settings', 'get', 'global', 'wifi_on').strip()
mobile = adb('shell', 'settings', 'get', 'global', 'mobile_data').strip()
font = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
baseline = state()
original_photos = photo_bytes(baseline)
year = min([2000]+[int(r['date'][:4])-1 for r in baseline.get('progressPhotos', [])])
day1, day2 = f'{year}-03-02', f'{year}-03-04'
tag = f'ofsheet-{time.time_ns()}'
downloads = []
try:
    adb('shell', 'svc', 'wifi', 'disable'); adb('shell', 'svc', 'data', 'disable')
    connectivity = adb('shell', 'dumpsys', 'connectivity')
    assert 'Active default network: none' in connectivity
    (args.output/'connectivity.txt').write_text(connectivity)
    apk = adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')
    (args.output/'apk-sha256.txt').write_text(adb('shell', 'sha256sum', apk))
    # Serialize additive legacy preference defaults before strict shape checks.
    # Choosing the current unit must not change any existing preference or record.
    previous = baseline
    settings(); tap('Units'); tap('Pounds (lb)' if previous['preferences']['pounds'] else 'Kilograms (kg)')
    baseline = state()
    assert {k:v for k,v in baseline.items() if k != 'preferences'} == {k:v for k,v in previous.items() if k != 'preferences'}
    assert all(baseline['preferences'][key] == value for key,value in previous['preferences'].items())
    fixtures = {}
    for i, (name, color) in enumerate([('front', (139,49,49)), ('side', (43,112,86)), ('back', (55,93,155)), ('replacement', (137,114,34))]):
        fixtures[name] = png(color, i, tag)
        path = args.output/f'{tag}-{name}.png'
        path.write_bytes(fixtures[name]); downloads.append(path.name)
        adb('push', str(path), '/sdcard/Download/'+path.name)
    history(); tap('Log Photos'); capture('photo-sheet-empty')
    tree = nodes()
    slots = [bounds(target('Add '+p+' photo', 'content-desc')) for p in ('Front','Side','Back')]
    assert all(abs((r-l)-(b-t)) <= 2 for l,t,r,b in slots), slots
    assert slots[0][3] < bounds(target('Save'))[1]
    height = max(bounds(n)[3] for n in tree)
    assert bounds(target('Close photos', 'content-desc'))[1] > height//2, 'Empty editor should be compact'
    tap('Save'); target('Attach at least one photo'); assert state() == baseline
    record_date(day1)
    tap('Change photo date', 'content-desc'); enter('Date (YYYY-MM-DD)', '2026-02-30')
    apply = target('Apply date')
    root = ET.fromstring(adb('shell', 'cat', '/sdcard/of-photo-sheet-ui.xml'))
    parents = {child:parent for parent in root.iter() for child in parent}
    apply = next(n for n in root.iter() if n.get('text') == 'Apply date')
    assert parents[apply].get('enabled') == 'false'
    capture('photo-date-invalid'); tap('Cancel'); target('/'.join(reversed(day1.split('-'))))
    tap('Add Front photo', 'content-desc'); target('Show roots', 'content-desc'); back()
    target('Add Front photo', 'content-desc'); assert state() == baseline
    for position in ('Front','Side','Back'):
        tap('Add '+position+' photo', 'content-desc'); choose(f'{tag}-{position.lower()}.png')
        target('View '+position+' photo', 'content-desc')
    capture('photo-sheet-populated')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    target('View Back photo', 'content-desc'); capture('photo-sheet-font-130')
    tap('Save'); target('Edit photos '+day1, 'content-desc')
    saved = state()
    record = next(r for r in saved['progressPhotos'] if r['date'] == day1)
    assert record['photos'] == {p:hashlib.sha256(fixtures[p]).hexdigest() for p in ('front','side','back')}
    assert saved['progressPhotos'][1:] == baseline.get('progressPhotos', [])
    assert {k:v for k,v in saved.items() if k != 'progressPhotos'} == {k:v for k,v in baseline.items() if k != 'progressPhotos'}
    adb('shell', 'settings', 'put', 'system', 'font_scale', font)
    for position in ('front','side','back'):
        adb('shell', 'rm', '/sdcard/Download/'+f'{tag}-{position}.png')
        assert private(record['photos'][position]) == fixtures[position]
    history(); tap('Edit photos '+day1, 'content-desc')
    tap('View Front photo', 'content-desc'); target('Front · '+day1); capture('photo-full-view'); back()
    target('Change photo date', 'content-desc'); record_date(day2)
    tap('Remove Side photo', 'content-desc'); tap('Save'); target('Edit photos '+day2, 'content-desc')
    edited = state()
    changed = next(r for r in edited['progressPhotos'] if r['id'] == record['id'])
    assert changed['date'] == day2 and set(changed['photos']) == {'front','back'}
    assert record['photos']['side'] not in private_names()
    replacement = hashlib.sha256(fixtures['replacement']).hexdigest()
    # Every dismissal path must discard imported replacements but keep saved images.
    for method in ('close','back','scrim','drag'):
        tap('Edit photos '+day2, 'content-desc')
        tap('Replace Back photo', 'content-desc'); choose(f'{tag}-replacement.png')
        target('View Back photo', 'content-desc'); assert replacement in private_names()
        dismiss(method); target('Edit photos '+day2, 'content-desc')
        assert state() == edited and replacement not in private_names(), method
        assert all(private(ref) == data for ref,data in original_photos.items())
    tap('Edit photos '+day2, 'content-desc')
    tap('Replace Back photo', 'content-desc'); choose(f'{tag}-replacement.png')
    target('View Back photo', 'content-desc'); tap('Save'); target('Edit photos '+day2, 'content-desc')
    final = state()
    final_record = next(r for r in final['progressPhotos'] if r['id'] == record['id'])
    assert final_record['photos']['back'] == replacement and record['photos']['back'] not in private_names()
    capture('photo-history-edited')
    settings(); tap('Export Backup')
    filename = tag+'-backup.json'; downloads.append(filename)
    edit = next(n for n in nodes() if n.get('class') == 'android.widget.EditText')
    enter(edit.get('resource-id'), filename, 'resource-id', last=True); tap('Save', last=True); target('Export Backup')
    exported = json.loads(adb('shell', 'cat', '/sdcard/Download/'+filename))
    assert {k:v for k,v in exported.items() if k != 'photoAssets'} == final
    assets = {ref:base64.b64decode(data) for ref,data in exported['photoAssets'].items()}
    assert assets == photo_bytes(final)
    invalid = json.loads(json.dumps(exported)); invalid['photoAssets'].pop(replacement)
    invalid_name = tag+'-invalid.json'; downloads.append(invalid_name)
    path = args.output/invalid_name; path.write_text(json.dumps(invalid)); adb('push', str(path), '/sdcard/Download/'+invalid_name)
    tap('Restore Backup'); choose(invalid_name); target('Restore Backup'); assert state() == final
    history(); tap('Edit photos '+day2, 'content-desc'); tap('Delete photo record', 'content-desc')
    capture('photo-delete-confirm'); tap('Cancel'); target('Progress Photos'); assert state() == final
    tap('Delete photo record', 'content-desc'); tap('Delete'); target('Body Data')
    deleted = state(); assert deleted['progressPhotos'] == baseline.get('progressPhotos', [])
    assert photo_bytes(deleted) == original_photos and replacement not in private_names()
    settings(); tap('Restore Backup'); choose(filename); target('Restore Backup?'); capture('photo-restore-confirm')
    tap('Cancel'); assert state() == deleted
    tap('Restore Backup'); choose(filename); target('Restore Backup?'); tap('Restore'); target('Restore Backup')
    launch(); assert state() == final and photo_bytes(state()) == assets
    # Quick-log forms retain the actual caller; history edits retain Body Data.
    # Root-specific labels differ, so an unconditional Dashboard/Body Data return fails.
    for root, label, method in [('Settings','GENERAL','close'), ('Workout','Active Program','back'), ('Exercises','Search exercises','scrim'), ('Dashboard','Customize Dashboard','drag')]:
        launch(); tap(root, last=True)
        tap('Shortcuts', 'content-desc'); tap('Log Photos'); target('Progress Photos')
        if root == 'Settings':
            capture('photo-quick-settings')
        dismiss(method); target(label, 'content-desc' if root == 'Dashboard' else 'text'); assert state() == final, (root, method)
        if root == 'Settings':
            capture('photo-return-settings')
        tap('Shortcuts', 'content-desc'); tap('Log Measurements'); target('Full Body')
        assert not any(n.get('class') == 'android.widget.EditText' and n.get('text') for n in nodes()), 'Dismissed quick-log drafts must not reopen'
        enter('Visual Body Fat', '37.25')
        if root == 'Exercises':
            adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3'); target('37.25')
        if method == 'back':
            # Native text input consumes Back to clear focus after the IME is hidden.
            assert any(n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true' for n in nodes())
            back()
            assert not any(n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true' for n in nodes())
        if method == 'scrim':
            capture('full-body-quick-font-130')
        dismiss(method, close='Close measurements')
        target(label, 'content-desc' if root == 'Dashboard' else 'text'); assert state() == final, (root, 'measurements')
        adb('shell', 'settings', 'put', 'system', 'font_scale', font)
    settings(); tap('Shortcuts', 'content-desc'); tap('Log Measurements'); target('Full Body')
    capture('full-body-quick-normal'); dismiss('scrim', close='Close measurements'); target('GENERAL')
    history(); tap('Edit photos '+day2, 'content-desc'); capture('photo-sheet-restored')
    tap('Close photos', 'content-desc'); target('Body Data'); target('Photos')
    assert state() == final and photo_bytes(state()) == assets
    assert {k:v for k,v in final.items() if k != 'progressPhotos'} == {k:v for k,v in baseline.items() if k != 'progressPhotos'}
    # Restore a public backup with an asymmetric hidden middle field/pose. A
    # filtered-index implementation would overwrite Side or the next measurement.
    hidden = json.loads(json.dumps(exported))
    hidden['preferences']['dataVisibility']['hiddenBodyMetrics'] = ['bodyFat', 'neck', 'sidePhoto', 'visualBodyFat', 'waist', 'weight']
    hidden_record = next(r for r in hidden['progressPhotos'] if r['id'] == record['id'])
    side_hash = hashlib.sha256(fixtures['side']).hexdigest()
    hidden_record['photos']['side'] = side_hash
    hidden['photoAssets'][side_hash] = base64.b64encode(fixtures['side']).decode()
    measurement = dict(id=tag+'-measurements', date=day1,
                       centimeters=dict(neck=31.12345678, waist=83.98765432, leftBicep=27.1111, rightBicep=30.2222),
                       visualBodyFatPercent=13.5)
    hidden['bodyMeasurements'].insert(0, measurement)
    hidden_name = tag+'-visibility.json'; downloads.append(hidden_name)
    path = args.output/hidden_name; path.write_text(json.dumps(hidden)); adb('push', str(path), '/sdcard/Download/'+hidden_name)
    settings(); tap('Restore Backup'); choose(hidden_name); target('Restore Backup?'); tap('Restore'); target('Restore Backup')
    hidden_state = state(); hidden_bytes = photo_bytes(hidden_state)
    tap('Body Data'); tap('Measurements'); tap('Edit measurements '+day1, 'content-desc')
    target('Visual Body Fat'); target('Shoulders')
    assert not any(n.get('content-desc') == 'Neck (cm)' for n in nodes())
    capture('measurements-hidden-neck-waist')
    enter('Right Bicep', '29.3333'); tap('Save'); target('Body Data')
    measured = next(r for r in state()['bodyMeasurements'] if r['id'] == measurement['id'])
    assert measured == {**measurement, 'centimeters': {**measurement['centimeters'], 'rightBicep':29.3333}}
    tap('Photos'); tap('Edit photos '+day2, 'content-desc')
    assert not any(n.get('content-desc') in ('View Side photo', 'Add Side photo') for n in nodes())
    target('View Front photo', 'content-desc'); target('View Back photo', 'content-desc')
    capture('photo-hidden-side')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3'); target('View Back photo', 'content-desc'); capture('photo-hidden-side-130')
    # Date-only Save must retain every hidden hash and all original image bytes.
    tap('Save'); target('Body Data')
    assert next(r for r in state()['progressPhotos'] if r['id'] == record['id']) == hidden_record
    assert photo_bytes(state()) == hidden_bytes
    tap('Edit photos '+day2, 'content-desc'); tap('Remove Back photo', 'content-desc'); tap('Save'); target('Body Data')
    assert next(r for r in state()['progressPhotos'] if r['id'] == record['id']) == {
        **hidden_record, 'photos': {p:ref for p,ref in hidden_record['photos'].items() if p != 'back'}}
    assert private(side_hash) == fixtures['side']
    adb('shell', 'settings', 'put', 'system', 'font_scale', font)
    hidden['preferences']['dataVisibility']['hiddenBodyMetrics'] = ['backPhoto', 'frontPhoto', 'sidePhoto']
    path.write_text(json.dumps(hidden)); adb('push', str(path), '/sdcard/Download/'+hidden_name)
    settings(); tap('Restore Backup'); choose(hidden_name); target('Restore Backup?'); tap('Restore'); target('Restore Backup')
    all_hidden = state(); history(); tap('Edit photos '+day2, 'content-desc')
    target('All photo slots are hidden in Data Visibility. Existing photos stay saved.')
    assert not any(n.get('content-desc', '').startswith(('Add Front', 'Add Side', 'Add Back', 'View Front', 'View Side', 'View Back')) for n in nodes())
    capture('photo-all-hidden'); tap('Save'); target('Body Data')
    assert state() == all_hidden and photo_bytes(state()) == hidden_bytes
    settings(); tap('Restore Backup'); choose(filename); target('Restore Backup?'); tap('Restore'); target('Restore Backup')
    assert state() == final and photo_bytes(state()) == assets
    assert 'Active default network: none' in adb('shell', 'dumpsys', 'connectivity')
    result = {'status':'passed', 'serial':args.serial, 'offline':True, 'square_slots_and_compact_sheet':True,
              'dated_input_validation':True, 'picker_cancel_and_three_private_originals':True,
              'date_slot_replace_remove_reopen':True, 'close_back_scrim_drag_discard':True,
              'enlarged_text_draft_recreation':True, 'full_view_after_source_deletion':True,
              'complete_native_export_restore_cancel_restart':True, 'invalid_restore_atomic':True,
              'quick_photo_and_measurement_caller_context':True, 'history_edit_return':True,
              'full_body_close_back_scrim_drag_discard':True, 'full_body_reachable_scrim_normal_and_130':True,
              'hidden_measurement_precision_and_pose_hashes_preserved':True, 'all_photo_slots_hidden_save_preserves_records':True,
              'existing_training_body_and_photos_preserved':True, 'source_save_semantics_claimed':False}
    (args.output/'result.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2), flush=True)
except BaseException:
    capture('failure')
    raise
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', font)
    adb('shell', 'svc', 'wifi', 'enable' if wifi == '1' else 'disable')
    adb('shell', 'svc', 'data', 'enable' if mobile == '1' else 'disable')
    for filename in downloads:
        adb('shell', 'rm', '-f', '/sdcard/Download/'+filename)
