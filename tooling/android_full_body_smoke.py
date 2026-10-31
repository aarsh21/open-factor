#!/usr/bin/env python3
"""Exercise Full Body input/reference, private-record preservation and native JSON backup.

Uses a disposable emulator offline. Leaves two synthetic dated records for review;
preserves all existing records, photos, training and settings. No private art is included.
"""
import argparse
import base64
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--illustrated', action='store_true', help='Expect explicitly supplied private reference assets')
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
            adb('shell', 'uiautomator', 'dump', '/sdcard/of-full-body-ui.xml')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/of-full-body-ui.xml')).iter('node'))
        except (ET.ParseError, subprocess.CalledProcessError):
            if attempt == 7:
                raise
            time.sleep(.3)


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))


def capture(name):
    nodes()
    (args.output / (name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p', raw=True))
    (args.output / (name+'.xml')).write_text(adb('shell', 'cat', '/sdcard/of-full-body-ui.xml'))


def target(label, attr='text', last=False):
    for _ in range(18):
        tree = nodes()
        found = [n for n in tree if n.get(attr, '').casefold() == label.casefold() and bounds(n)[3]-bounds(n)[1] >= 30]
        if found:
            return found[-1 if last else 0]
        scrolls = [n for n in tree if n.get('scrollable') == 'true']
        if scrolls:
            left, top, right, bottom = bounds(scrolls[-1])
            adb('shell', 'input', 'swipe', str((left+right)//2), str(top+(bottom-top)*3//4), str((left+right)//2), str(top+(bottom-top)//3), '500')
        else:
            time.sleep(.2)
    capture('failure')
    raise AssertionError((label, attr))


def tap(label, attr='text', last=False):
    left, top, right, bottom = bounds(target(label, attr, last))
    adb('shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2))


def enter(label, value, attr='text', hide=True, last=False):
    tap(label, attr, last)
    field = next(n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true')
    counts = ' '.join(str(i) for i in range(len(field.get('text', ''))+1))
    adb('shell', f'for i in {counts}; do input keyevent 67; input keyevent 112; done')
    if value:
        adb('shell', 'input', 'text', value)
    if hide:
        back()


def back():
    adb('shell', 'input', 'keyevent', '4')


def state():
    root = ET.fromstring(adb('shell', 'run-as', 'org.openfactor.app', 'cat', 'shared_prefs/open_factor.xml'))
    from storage_snapshot import snapshot_data
    return snapshot_data(root, adb)


def photo_bytes(data):
    refs = {ref for record in data.get('progressPhotos', []) for ref in record['photos'].values()}
    return {ref: adb('exec-out', 'run-as', 'org.openfactor.app', 'cat', 'no_backup/progress-photos/'+ref, raw=True) for ref in refs}


def settings():
    adb('shell', 'am', 'force-stop', 'org.openfactor.app')
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', 'org.openfactor.app/.MainActivity')
    tap('Settings', last=True)


def history():
    settings(); tap('Body Data'); tap('Measurements')


def history_top():
    target('Body Data')
    for _ in range(12):
        tree = nodes()
        if any(n.get('text') == 'View Charts' for n in tree):
            return
        scroll = next(n for n in tree if n.get('scrollable') == 'true' and bounds(n)[3]-bounds(n)[1] > 500)
        left, top, right, bottom = bounds(scroll)
        adb('shell', 'input', 'swipe', str((left+right)//2), str(top+(bottom-top)//3), str((left+right)//2), str(top+(bottom-top)*3//4), '500')
    raise AssertionError('History did not return to its actions')


def save():
    tap('Save'); history_top()


def record_date(day):
    tap('Change measurement date', 'content-desc')
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


def reference(suffix=''):
    tap('Reference'); target('Visual Body Fat reference')
    if args.illustrated:
        for label in ('3–4%', '5–7%', '8–12%', '13–17%', '18–23%', '24–29%', '30–34%', '35–39%', '40% +'):
            target('Visual body fat reference '+label, 'content-desc')
        assert not any(n.get('text') == 'Image unavailable' for n in nodes())
    else:
        target('Reference illustrations are not bundled in this build. These are the captured male-reference range labels, not a personal estimate.')
        for label in ('3–4%', '5–7%', '8–12%', '13–17%', '18–23%', '24–29%', '30–34%', '35–39%', '40% +'):
            target(label)
    capture(('reference-private' if args.illustrated else 'reference-public')+suffix)
    back(); target('Full Body')


wifi = adb('shell', 'settings', 'get', 'global', 'wifi_on').strip()
mobile = adb('shell', 'settings', 'get', 'global', 'mobile_data').strip()
font = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
baseline = state()
photos = photo_bytes(baseline)
year = min([2001]+[int(r['date'][:4])-1 for r in baseline.get('bodyMeasurements', [])])
day1, day2 = f'{year}-02-03', f'{year}-02-04'
try:
    adb('shell', 'svc', 'wifi', 'disable'); adb('shell', 'svc', 'data', 'disable')
    connectivity = adb('shell', 'dumpsys', 'connectivity')
    assert 'Active default network: none' in connectivity
    (args.output/'connectivity.txt').write_text(connectivity)
    apk = adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')
    (args.output/'apk-sha256.txt').write_text(adb('shell', 'sha256sum', apk))
    history(); tap('Log Measurements'); capture('full-body-empty')
    tap('Save'); target('Enter at least one measurement'); assert state() == baseline
    enter('Visual Body Fat', '100.01'); tap('Save'); target('Visual body fat must be 0–100%, or blank.')
    assert state() == baseline
    enter('Visual Body Fat', '26.1251234'); reference()
    # Reference dismisses without calculating, selecting or modifying the typed value.
    target('26.1251234')
    record_date(day1)
    tap('Change measurement date', 'content-desc'); enter('Date (YYYY-MM-DD)', '2026-02-30')
    target('Apply date')
    root = ET.fromstring(adb('shell', 'cat', '/sdcard/of-full-body-ui.xml'))
    parents = {child: parent for parent in root.iter() for child in parent}
    apply_xml = next(n for n in root.iter() if n.get('text') == 'Apply date')
    assert parents[apply_xml].get('enabled') == 'false'
    tap('Cancel'); target('/'.join(reversed(day1.split('-'))))
    enter('Visual Body Fat', '26.1251234', hide=False); capture('full-body-keyboard')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    target('26.1251234'); capture('full-body-font-130')
    reference('-130'); save()
    first = state()['bodyMeasurements'][0]
    assert first['date'] == day1 and first['visualBodyFatPercent'] == 26.1251234 and not first['centimeters']
    capture('full-body-history-130')
    adb('shell', 'settings', 'put', 'system', 'font_scale', font)
    history(); tap('Edit measurements '+day1, 'content-desc'); target('26.125123')
    save(); assert state()['bodyMeasurements'][0] == first
    # Second record exercises 0%, all three field groups, asymmetric sides and clearing fat.
    tap('Log Measurements'); record_date(day2); enter('Visual Body Fat', '0')
    fields = [('Neck','neck',31.25), ('Shoulders','shoulders',107.5), ('Bust','bust',88.75), ('Chest','chest',91.125),
              ('Waist','waist',84.25), ('Hips','hips',96.5), ('Left Bicep','leftBicep',30.1), ('Right Bicep','rightBicep',32.2),
              ('Left Forearm','leftForearm',24.3), ('Right Forearm','rightForearm',25.4), ('Left Wrist','leftWrist',15.5), ('Right Wrist','rightWrist',16.6),
              ('Left Thigh','leftThigh',51.7), ('Right Thigh','rightThigh',52.8), ('Left Calf','leftCalf',34.9), ('Right Calf','rightCalf',35.1),
              ('Left Ankle','leftAnkle',21.2), ('Right Ankle','rightAnkle',22.3)]
    for label, key, value in fields:
        enter(label, str(value))
        if label in ('Hips', 'Right Wrist', 'Right Ankle'):
            capture('full-body-'+key)
    save()
    second = state()['bodyMeasurements'][0]
    assert second['centimeters'] == {key:value for _,key,value in fields} and second['visualBodyFatPercent'] == 0
    tap('Edit measurements '+day2, 'content-desc'); enter('Visual Body Fat', ''); save()
    assert 'visualBodyFatPercent' not in state()['bodyMeasurements'][0]
    tap('Edit measurements '+day2, 'content-desc'); enter('Visual Body Fat', '0'); save()
    saved = state()
    assert saved['bodyMeasurements'][2:] == baseline.get('bodyMeasurements', [])
    assert {k:v for k,v in saved.items() if k != 'bodyMeasurements'} == {k:v for k,v in baseline.items() if k != 'bodyMeasurements'}
    # Both history summaries, chart separation, manual edits and discard are real routes.
    tap('Edit measurements '+day2, 'content-desc'); target('Close measurements', 'content-desc'); back(); capture('full-body-history'); history_top()
    tap('Edit measurements '+day1, 'content-desc'); enter('Visual Body Fat', '19.5'); tap('Close measurements', 'content-desc')
    history_top(); assert state() == saved
    tap('Edit measurements '+day1, 'content-desc'); tap('Delete measurements', 'content-desc'); capture('full-body-delete-confirm')
    tap('Cancel'); target('Full Body'); back(); history_top(); assert state() == saved
    tap('View Charts')
    if 'bodyCharts' not in saved['preferences'].get('dismissedTutorials', []):
        target('About body charts'); capture('chart-first-entry'); tap('Got it')
        saved['preferences']['dismissedTutorials'] = sorted(set(saved['preferences'].get('dismissedTutorials', [])) | {'bodyCharts'})
        assert state() == saved, 'Dismissing chart help changed unrelated saved data'
    tap('Neck'); tap('Visual Body Fat'); tap('All'); target('26.13 %'); target('0 %')
    capture('visual-fat-chart'); assert state() == saved
    settings(); tap('Export Backup'); tap('Choose file')
    filename = f'offullbody-{time.time_ns()}.json'
    edit = next(n for n in nodes() if n.get('class') == 'android.widget.EditText')
    enter(edit.get('resource-id'), filename, 'resource-id', last=True)
    tap('Save', last=True); target('Export Backup')
    exported = json.loads(adb('shell', 'cat', '/sdcard/Download/'+filename))
    assert {k:v for k,v in exported.items() if k != 'photoAssets'} == saved
    assert {ref:base64.b64decode(value) for ref,value in exported.get('photoAssets', {}).items()} == photos
    invalid = json.loads(json.dumps(exported)); invalid['bodyMeasurements'][0]['visualBodyFatPercent'] = 100.01
    invalid_name = 'offullbody-invalid.json'; path = args.output/invalid_name; path.write_text(json.dumps(invalid))
    adb('push', str(path), '/sdcard/Download/'+invalid_name)
    tap('Restore Backup'); choose(invalid_name); target('Restore Backup'); assert state() == saved
    history(); tap('Edit measurements '+day1, 'content-desc'); tap('Delete measurements', 'content-desc'); tap('Delete'); history_top()
    deleted = state(); assert len(deleted['bodyMeasurements']) == len(saved['bodyMeasurements'])-1
    settings(); tap('Restore Backup'); choose(filename); target('Restore Backup?'); capture('full-body-restore-confirm')
    tap('Cancel'); assert state() == deleted
    tap('Restore Backup'); choose(filename); target('Restore Backup?'); tap('Restore'); target('Restore Backup'); assert state() == saved
    history(); assert state() == saved and photo_bytes(state()) == photos
    tap('Edit measurements '+day1, 'content-desc'); target('26.125123'); capture('full-body-restored')
    back(); history_top()
    assert 'Active default network: none' in adb('shell', 'dumpsys', 'connectivity')
    (args.output/'final-state.json').write_text(json.dumps(state(), indent=2))
    result = dict(status='passed', serial=args.serial, offline=True, reference_illustrated=args.illustrated,
                  visual_only_and_all_18_fields=True, scale_fat_separate=True, zero_and_blank_distinct=True,
                  unchanged_precision=True, date_validation=True, font_and_keyboard_recreation=True,
                  close_discard=True, delete_cancel=True, native_backup_restore_and_cancel=True,
                  invalid_restore_atomic=True, process_restart=True, training_and_photos_unchanged=True,
                  source_estimator_or_save_parity=False)
    (args.output/'result.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', font if font != 'null' else '1.0')
    if wifi in ('1','2'): adb('shell', 'svc', 'wifi', 'enable')
    if mobile == '1': adb('shell', 'svc', 'data', 'enable')
