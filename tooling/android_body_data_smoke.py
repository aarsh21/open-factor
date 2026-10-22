#!/usr/bin/env python3
"""Exercise dated body data and Android's JSON document flow offline.

Use a disposable emulator with Open Factor installed. Leaves synthetic body records
and a synthetic empty session; never opens a source app or a physical device.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Use an explicitly disposable emulator, never a physical phone')
args.output.mkdir(parents=True, exist_ok=True)


def adb(*command, raw=False):
    result = subprocess.check_output(['adb', '-s', args.serial, *command], timeout=40)
    return result if raw else result.decode()


def nodes():
    for attempt in range(8):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/of-body-ui.xml')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/of-body-ui.xml')).iter('node'))
        except (subprocess.CalledProcessError, ET.ParseError):
            if attempt == 7:
                raise
            time.sleep(.4)


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))


def find(label, attr='text'):
    for _ in range(6):
        matches = [n for n in nodes() if n.get(attr, '').strip().casefold() == label.casefold()]
        if matches:
            return matches
        time.sleep(.3)
    capture('failure')
    raise AssertionError((label, attr))


def reachable(label, attr='text', last=False):
    for _ in range(12):
        current = nodes()
        found = [n for n in current if n.get(attr, '').strip().casefold() == label.casefold() and bounds(n)[3] - bounds(n)[1] >= 40]
        if found:
            return found[-1 if last else 0]
        scrolls = [n for n in current if n.get('scrollable') == 'true']
        if not scrolls:
            break
        left, top, right, bottom = bounds(max(scrolls, key=lambda n: bounds(n)[3] - bounds(n)[1]))
        x = (left + right) // 2
        adb('shell', 'input', 'swipe', str(x), str(top + (bottom - top) * 4 // 5), str(x), str(top + (bottom - top) // 5), '300')
    find(label, attr)
    raise AssertionError('Control not reachable: ' + label)


def tap(label, attr='text', last=False):
    left, top, right, bottom = bounds(reachable(label, attr, last))
    adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))


def enter(label, value, attr='text', last=False):
    tap(label, attr, last)
    fields = [n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true']
    assert len(fields) == 1, label
    old = fields[0].get('text', '')
    if old:
        # Compose may retain the cursor after a date dialog; clear both sides.
        # Separate input invocations let each edit reach the input connection.
        counts = ' '.join(str(i) for i in range(len(old) + 1))
        adb('shell', f'for i in {counts}; do input keyevent 67; input keyevent 112; done')
    if value:
        adb('shell', 'input', 'text', value.replace(' ', '%s'))
    fields = [n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true']
    assert len(fields) == 1 and fields[0].get('text', '') == value, (label, fields)
    adb('shell', 'input', 'keyevent', '4')


def capture(name):
    tree = nodes()
    (args.output / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p', raw=True))
    (args.output / f'{name}.xml').write_text(adb('shell', 'cat', '/sdcard/of-body-ui.xml'))
    return tree


def state():
    xml = ET.fromstring(adb('shell', 'run-as', 'org.openfactor.app', 'cat', 'shared_prefs/open_factor.xml'))
    return json.loads(next(n.text for n in xml if n.get('name') == 'data'))


def launch():
    adb('shell', 'am', 'force-stop', 'org.openfactor.app')
    adb('shell', 'am', 'start', '-n', 'org.openfactor.app/.MainActivity')


def back():
    adb('shell', 'input', 'keyevent', '4')


def training(data):
    return {k: data.get(k) for k in ('active', 'history', 'routines', 'programs', 'activeProgramId', 'programDraft', 'workoutDraft', 'gyms')}


def settings():
    launch()
    tap('Settings', last=True)


def offline():
    connectivity = adb('shell', 'dumpsys', 'connectivity')
    assert 'Active default network: none' in connectivity
    return connectivity


wifi = adb('shell', 'settings', 'get', 'global', 'wifi_on').strip()
mobile = adb('shell', 'settings', 'get', 'global', 'mobile_data').strip()
font_scale = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
try:
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    for _ in range(20):
        if 'Active default network: none' in adb('shell', 'dumpsys', 'connectivity'):
            break
        time.sleep(.5)
    (args.output / 'connectivity.txt').write_text(offline())
    package = adb('shell', 'dumpsys', 'package', 'org.openfactor.app')
    assert 'android.permission.INTERNET' not in package
    (args.output / 'package.txt').write_text(package)
    apk = adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')
    (args.output / 'apk-sha256.txt').write_text(adb('shell', 'sha256sum', apk))
    launch()
    if any(n.get('text') == 'Get Started' for n in nodes()):
        tap('Set up later')
        tap('Set up later', last=True)
    find('Dashboard')
    # Existing records are not disposable fixtures. Require an empty body-data store.
    assert not state().get('scaleWeights') and not state().get('bodyMeasurements'), 'Use an emulator with no body records'
    if not state().get('active'):
        tap('Shortcuts', 'content-desc')
        tap('Empty Workout')
        find('Start Workout')
        assert state().get('active') is None, 'Opening composition started workout timing'
        tap('Start Workout')
        find('Workout options', 'content-desc')
        tap('Workout options', 'content-desc')
        tap('Minimize Workout')
    original_training = training(state())
    tap('Shortcuts', 'content-desc')
    capture('shortcuts')
    tap('Log Weight')
    capture('weight-empty')
    tap('Save')
    find('Enter a positive weight (at most 5000 kg).')
    assert not state()['scaleWeights']
    # Typed calendar dates outside the native picker's range must not crash it.
    enter('Date (YYYY-MM-DD)', '1800-01-02')
    tap('Choose date', 'content-desc')
    find('Set date')
    tap('Cancel')
    enter('Date (YYYY-MM-DD)', '2026-10-20')
    enter('Weight', '82.35')
    enter('Body Fat (optional)', '101')
    tap('Save')
    assert not state()['scaleWeights']
    enter('Body Fat (optional)', '18.7')
    capture('weight-kg')
    tap('Save')
    find('82.35 kg')
    first = state()['scaleWeights'][0]
    assert first['date'] == '2026-10-20' and first['weightKg'] == 82.35 and first['bodyFatPercent'] == 18.7
    tap('Log Weight')
    enter('Date (YYYY-MM-DD)', '2026-10-18')
    tap('lb')
    enter('Weight', '174.25')
    capture('weight-lb')
    tap('Save')
    data = state()
    second = next(w for w in data['scaleWeights'] if w['id'] != first['id'])
    # International avoirdupois conversion: 174.25 × 0.45359237 kg.
    assert abs(second['weightKg'] - 79.0384704725) < 1e-7 and second.get('bodyFatPercent') is None
    capture('weight-history-kg')
    # Edit date and clear optional body fat without duplicating the record.
    tap('Edit weight 2026-10-20', 'content-desc')
    enter('Date (YYYY-MM-DD)', '2026-10-19')
    enter('Body Fat (optional)', '')
    tap('Save')
    assert len(state()['scaleWeights']) == 2
    first = next(w for w in state()['scaleWeights'] if w['id'] == first['id'])
    assert first['date'] == '2026-10-19' and first.get('bodyFatPercent') is None and first['weightKg'] == 82.35
    settings()
    tap('Units')
    tap('Pounds (lb)')
    tap('Body Data')
    find('181.55 lb')
    find('174.25 lb')
    capture('weight-history-lb')
    # An unchanged save in converted units must not round the underlying kg value.
    tap('Edit weight 2026-10-19', 'content-desc')
    tap('Save')
    assert next(w for w in state()['scaleWeights'] if w['id'] == first['id'])['weightKg'] == 82.35
    tap('Measurements')
    capture('measurements-empty')
    tap('Log Measurements')
    tap('Save')
    find('Enter at least one measurement')
    enter('Date (YYYY-MM-DD)', '2026-10-17')
    enter('Neck', '39.25')
    capture('measurements-upper')
    enter('Waist', '86.5')
    enter('Left Bicep', '34.75')
    enter('Right Bicep', '35.25')
    capture('measurements-arms')
    enter('Left Calf', '39.5')
    enter('Right Ankle', '22.75')
    capture('measurements-legs')
    # Configuration recreation must preserve every unsaved field, including off-screen values.
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    tap('Save')
    measures = state()['bodyMeasurements'][0]
    assert measures['date'] == '2026-10-17'
    assert measures['centimeters'] == {'neck': 39.25, 'waist': 86.5, 'leftBicep': 34.75, 'rightBicep': 35.25, 'leftCalf': 39.5, 'rightAnkle': 22.75}
    adb('shell', 'settings', 'put', 'system', 'font_scale', font_scale)
    capture('measurements-history')
    tap('Edit measurements 2026-10-17', 'content-desc')
    enter('Neck', '')
    tap('Save')
    assert len(state()['bodyMeasurements']) == 1 and 'neck' not in state()['bodyMeasurements'][0]['centimeters']
    persisted = state()
    launch()
    assert state() == persisted
    tap('Settings', last=True)
    tap('Body Data')
    find('181.55 lb')
    # Enlarged type uses the same working routes, including a native date picker.
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    tap('Edit weight 2026-10-19', 'content-desc')
    capture('weight-font-130')
    tap('Choose date', 'content-desc')
    find('Set date')
    capture('date-picker-font-130')
    tap('Cancel')
    back()
    adb('shell', 'settings', 'put', 'system', 'font_scale', font_scale)
    settings()
    # Use the actual Android Storage Access Framework, not a private test-only export route.
    tap('Export Backup')
    edit_fields = [n for n in nodes() if n.get('class') == 'android.widget.EditText']
    assert edit_fields, 'Android document creation did not open'
    filename = f'open-factor-body-smoke-{time.time_ns()}.json'
    enter(edit_fields[0].get('resource-id'), filename, 'resource-id', last=True)
    tap('Save', last=True)
    find('Export Backup')
    capture('settings-body-data')
    exported = json.loads(adb('shell', 'cat', '/sdcard/Download/' + filename))
    assert exported == state()
    tap('Body Data')
    tap('Edit weight 2026-10-19', 'content-desc')
    tap('Delete weight', 'content-desc')
    tap('Cancel')
    assert len(state()['scaleWeights']) == 2
    tap('Delete weight', 'content-desc')
    capture('delete-confirmation')
    tap('Delete')
    assert len(state()['scaleWeights']) == 1
    tap('Measurements')
    tap('Edit measurements 2026-10-17', 'content-desc')
    tap('Delete measurements', 'content-desc')
    tap('Delete')
    assert not state()['bodyMeasurements']
    settings()
    tap('Restore Backup')
    tap(filename)
    find('Restore Backup?')
    capture('restore-confirmation')
    tap('Restore')
    find('Restore Backup')
    assert state() == exported
    launch()
    assert state() == exported
    assert training(state()) == original_training
    # Latest value is selected by record date, not by list insertion/edit order.
    reachable('Scale Weight')
    find('181.55 lb · 2026-10-19')
    capture('dashboard-body-data')
    tap('Scale Weight')
    find('181.55 lb')
    capture('restored-history')
    back()
    tap('Shortcuts', 'content-desc')
    tap('Log Measurements')
    find('Full Body')
    back()
    find('Log Measurements')
    assert state() == exported
    (args.output / 'final-state.json').write_text(json.dumps(state(), indent=2) + '\n')
    offline()
    result = {'status': 'passed', 'serial': args.serial, 'offline_without_internet_permission': True,
              'kg_and_lb_entry_and_display': True, 'optional_fat_validation_and_removal': True,
              'calendar_dates_and_edit_identity': True, 'asymmetric_full_body_measurements': True,
              'enlarged_type_and_native_date_picker': True, 'unsaved_measurements_survive_recreation': True,
              'restart_persistence': True,
              'android_document_export_and_restore': True, 'confirmed_and_cancelled_deletion': True,
              'dashboard_settings_and_both_shortcuts': True,
              'training_unchanged': True, 'source_parity_certification': False}
    (args.output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result, indent=2))
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', font_scale)
    if wifi in ('0', '1'):
        adb('shell', 'svc', 'wifi', 'enable' if wifi == '1' else 'disable')
    if mobile in ('0', '1'):
        adb('shell', 'svc', 'data', 'enable' if mobile == '1' else 'disable')
