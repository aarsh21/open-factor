#!/usr/bin/env python3
"""Exercise scoped SAF exports, visibility, tutorial reset and offline help.

Use only an allocated disposable emulator with an installed debug APK. Restores
the exact incoming preference XML, private photo bytes, font and network state.
Artifacts can contain private baseline data and must remain locally excluded.
"""
import argparse
import base64
from datetime import date, timedelta
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
parser.add_argument('--output', required=True, type=Path)
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Use an allocated disposable emulator, never a phone')
args.output.mkdir(parents=True, exist_ok=True)
package = 'org.openfactor.app'


def adb(*command, raw=False, data=None):
    result = subprocess.run(['adb', '-s', args.serial, *command], input=data,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=90, check=True)
    return result.stdout if raw else result.stdout.decode()


def nodes():
    for attempt in range(8):
        dumped = adb('shell', 'uiautomator', 'dump', '/sdcard/of-portability-ui.xml')
        if 'dumped to:' in dumped:
            try:
                return list(ET.fromstring(adb('shell', 'cat', '/sdcard/of-portability-ui.xml')).iter('node'))
            except ET.ParseError:
                pass
        time.sleep(.3)
    raise AssertionError('No fresh UI hierarchy')


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))


def capture(name):
    nodes()
    (args.output / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', raw=True))
    (args.output / (name + '.xml')).write_text(adb('shell', 'cat', '/sdcard/of-portability-ui.xml'))


def target(label, attr='text', scroll=True, last=False):
    for _ in range(26):
        tree = nodes()
        found = [n for n in tree if n.get(attr, '').casefold() == label.casefold()
                 and bounds(n)[3] > bounds(n)[1] and bounds(n)[2] > bounds(n)[0]]
        if found:
            return found[-1 if last else 0]
        scrolls = [n for n in tree if n.get('scrollable') == 'true']
        if scroll and scrolls:
            left, top, right, bottom = bounds(scrolls[-1])
            adb('shell', 'input', 'swipe', str((left+right)//2), str(top+(bottom-top)*3//5),
                str((left+right)//2), str(top+(bottom-top)*2//5), '450')
        else:
            time.sleep(.2)
    capture('failure')
    raise AssertionError((label, attr))


def tap(label, attr='text', last=False):
    left, top, right, bottom = bounds(target(label, attr, last=last))
    adb('shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2))


def back():
    adb('shell', 'input', 'keyevent', '4')


def launch():
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', package+'/.MainActivity')


def settings():
    launch()
    tap('Settings', last=True)


def state():
    xml = ET.fromstring(adb('exec-out', 'run-as', package, 'cat', 'shared_prefs/open_factor.xml'))
    from storage_snapshot import snapshot_data
    return snapshot_data(xml, adb, package)


def write_private(path, payload):
    local = args.output / 'private-transfer'
    remote = '/data/local/tmp/of-portability-transfer'
    local.write_bytes(payload)
    try:
        adb('push', str(local), remote)
        adb('shell', 'run-as', package, 'cp', remote, path)
        assert adb('exec-out', 'run-as', package, 'cat', path, raw=True) == payload
    finally:
        adb('shell', 'rm', '-f', remote)
        local.unlink()


def write_state(data):
    adb('shell', 'am', 'force-stop', package)
    xml = ET.fromstring(original_xml)
    next(n for n in xml if n.get('name') == 'data').text = json.dumps(data)
    write_private('shared_prefs/open_factor.xml', ET.tostring(xml))


def enter_field(node, value):
    left, top, right, bottom = bounds(node)
    adb('shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2))
    adb('shell', 'input', 'keyevent', '123')
    for _ in range(len(node.get('text', ''))+1):
        adb('shell', 'input', 'keyevent', '67')
    adb('shell', 'input', 'text', value)
    back()


def save_as(filename):
    field = next(n for n in nodes() if n.get('class') == 'android.widget.EditText')
    enter_field(field, filename)
    tap('Save', last=True)


def choose(filename):
    target('Show roots', 'content-desc')
    tap('Show roots', 'content-desc')
    tap('Downloads', last=True)
    tap(filename)


def open_chart():
    settings(); tap('Body Data'); tap('View Charts')


def photo_fixture(color):
    def chunk(kind, payload):
        return struct.pack('>I', len(payload))+kind+payload+struct.pack('>I', zlib.crc32(kind+payload))
    rows = (b'\0'+bytes(color)*31)*47
    return (b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR', struct.pack('>IIBBBBB', 31, 47, 8, 2, 0, 0, 0))
            +chunk(b'IDAT', zlib.compress(rows))+chunk(b'IEND', b''))


def unchanged_records(expected):
    actual = state()
    assert {k: v for k, v in actual.items() if k != 'preferences'} == {k: v for k, v in expected.items() if k != 'preferences'}
    for reference, payload in fixture_photos.items():
        assert adb('exec-out', 'run-as', package, 'cat', 'no_backup/progress-photos/'+reference, raw=True) == payload


original_xml = adb('exec-out', 'run-as', package, 'cat', 'shared_prefs/open_factor.xml', raw=True)
baseline = state()
recovery = args.output / 'private-baseline'
recovery.mkdir(exist_ok=True)
(recovery / 'open_factor.xml').write_bytes(original_xml)
original_files = {ref: adb('exec-out', 'run-as', package, 'cat', 'no_backup/progress-photos/'+ref, raw=True)
                  for ref in adb('shell', 'run-as', package, 'ls', 'no_backup/progress-photos').split()
                  if re.fullmatch('[a-f0-9]{64}', ref)}
original_photos = {ref: original_files[ref] for record in baseline.get('progressPhotos', []) for ref in record['photos'].values()}
for reference, payload in original_files.items():
    (recovery / reference).write_bytes(payload)
font = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
wifi = adb('shell', 'settings', 'get', 'global', 'wifi_on').strip()
mobile = adb('shell', 'settings', 'get', 'global', 'mobile_data').strip()
tag = 'of-portability-'+str(time.time_ns())
downloads = []
today = date.fromisoformat(adb('shell', 'date', '+%Y-%m-%d').strip())
fixture = json.loads(json.dumps(baseline))
fixture['onboarding'] = {'completed': True}
fixture['active'] = None
fixture['workoutDraft'] = None
fixture['programDraft'] = None
fixture['preferences']['pounds'] = False
fixture['preferences']['displayName'] = 'Portability Fixture'
fixture['preferences']['dismissedTutorials'] = []
fixture['preferences']['dataVisibility'] = {'hiddenBodyMetrics': [], 'showPeriod': False}
fixture['scaleWeights'] = [{'id': 'weight-'+str(offset), 'date': str(today+timedelta(days=offset)), 'weightKg': weight,
                           'bodyFatPercent': 0.0 if offset == 0 else None}
                          for offset, weight in [(-7, 71.125), (-6, 82.25), (0, 93.375), (1, 104.5)]]
fixture['bodyMeasurements'] = [{'id': 'measurement', 'date': str(today), 'centimeters': {'waist': 81.125, 'hips': 94.75}, 'visualBodyFatPercent': 0.0}]
fixture_photos = dict(original_photos)
positions = {}
for position, color in [('front', (139, 49, 49)), ('side', (43, 112, 86)), ('back', (55, 93, 155))]:
    image = photo_fixture(color)
    reference = hashlib.sha256(image).hexdigest()
    fixture_photos[reference] = image
    positions[position] = reference
fixture.setdefault('progressPhotos', []).append({'id': 'synthetic-portability-photos', 'date': str(today), 'photos': positions})
fixture['history'] = [{'id': 'workout-'+str(offset), 'name': 'Synthetic '+str(offset),
                       'startedAt': int(adb('shell', 'date -d "'+str(today+timedelta(days=offset))+' 12:00:00" +%s').strip())*1000,
                       'finishedAt': None, 'exercises': [], 'notes': 'Synthetic notes '+str(offset)} for offset in [-7, -6, 0, 1]]
fixture['programs'] = [{'id': key, 'name': key, 'cycleCount': 1, 'color': 4294913866,
                        'days': [{'id': key+'-rest', 'name': 'Rest', 'exercises': []}]}
                       for key in ['inactive-portability', 'active-portability']]
fixture['activeProgramId'] = 'active-portability'
try:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'svc', 'wifi', 'disable'); adb('shell', 'svc', 'data', 'disable')
    write_state(fixture)
    for reference, image in fixture_photos.items():
        write_private('no_backup/progress-photos/'+reference, image)
    # Normalize the fixture once through the public preference writer before
    # checking that visibility and tutorials leave canonical records unchanged.
    settings(); tap('Units'); tap('Kilograms (kg)'); tap('Back', 'content-desc')
    tap('Data Export'); target('Quick Export'); capture('quick-normal')
    before = state()
    tap('Last 7 Days'); capture('timeframes-normal')
    for label in ['Last 14 Days', 'Last 30 Days', 'This Month', 'Last Month', 'All Time']:
        target(label, scroll=False)
    back()
    tap('Export'); target('Save'); capture('native-save'); back(); target('Data Export'); assert state() == before
    tap('Export'); target('Save')
    cancelled = tag+'-cancelled.json'
    enter_field(next(n for n in nodes() if n.get('class') == 'android.widget.EditText'), cancelled)
    # This DocumentsUI version has no Cancel or Navigate up action. Back returns
    # RESULT_CANCELED, including after a filename edit and keyboard dismissal.
    back(); target('Data Export'); assert state() == before
    adb('shell', 'test', '!', '-e', '/sdcard/Download/'+cancelled)
    tap('Export'); target('Save')
    name = tag+'-quick.json'; downloads.append(name); save_as(name); target('Data Export')
    quick = json.loads(adb('shell', 'cat', '/sdcard/Download/'+name))
    assert set(quick['data']) == {'muscleGroups', 'trainingPrograms', 'workoutLog'}
    assert [p['id'] for p in quick['data']['trainingPrograms']] == ['active-portability']
    assert [w['id'] for w in quick['data']['workoutLog']] == ['workout--6', 'workout-0']
    assert quick['data']['workoutLog'][0]['notes'] == 'Synthetic notes -6'
    assert 'schemaVersion' not in quick and state() == before
    tap('Granular Export'); capture('granular-empty-normal')
    actions = [n for n in nodes() if n.get('clickable') == 'true'
               and any(child.get('text') == 'Export' for child in n.iter('node'))]
    assert len(actions) == 1 and actions[0].get('enabled') == 'false'
    tap('Scale Weight'); capture('granular-selected-normal'); tap('Export'); target('Save')
    name = tag+'-granular.json'; downloads.append(name); save_as(name); target('Data Export')
    granular = json.loads(adb('shell', 'cat', '/sdcard/Download/'+name))
    assert set(granular['data']) == {'scaleWeight'}
    assert [(r['id'], r['weightKg']) for r in granular['data']['scaleWeight']] == [('weight--6', 82.25), ('weight-0', 93.375)]
    assert granular['data']['scaleWeight'][1]['bodyFatPercent'] == 0.0 and state() == before
    settings(); tap('Restore Backup'); choose(name)
    target('Could not read backup: Unsupported backup version'); capture('scoped-restore-rejected')
    assert state() == before
    settings(); tap('Data Visibility'); capture('visibility-normal'); tap('Body Metrics Visibility'); capture('body-visibility-normal')
    tap('Scale Weight'); target('Default Metric'); capture('default-metric-normal'); tap('Done')
    tap('Side Photo'); tap('Waist'); unchanged_records(before)
    hidden = state(); assert set(hidden['preferences']['dataVisibility']['hiddenBodyMetrics']) == {'sidePhoto', 'waist'}
    launch(); assert state() == hidden
    settings(); tap('Data Visibility'); tap('Body Metrics Visibility'); target('Side Photo'); capture('body-hidden-normal')
    settings(); tap('Data Export'); tap('Granular Export'); tap('Body Metrics'); tap('Export'); target('Save')
    name = tag+'-hidden-body.json'; downloads.append(name); save_as(name); target('Data Export')
    body = json.loads(adb('shell', 'cat', '/sdcard/Download/'+name))['data']
    assert set(body) == {'bodyMetrics'} and len(body['bodyMetrics']) == 1
    assert body['bodyMetrics'][0]['centimeters'] == {'waist': 81.125, 'hips': 94.75}
    assert state() == hidden
    settings(); tap('Data Visibility'); tap('Period Visibility'); capture('period-unavailable-normal')
    assert all(n.get('enabled') == 'false' for n in nodes() if n.get('class') == 'android.widget.Switch')
    assert state() == hidden
    open_chart(); target('About body charts'); capture('tutorial-first-normal'); tap('Got it')
    dismissed = {**hidden, 'preferences': {**hidden['preferences'], 'dismissedTutorials': ['bodyCharts']}}
    assert state() == dismissed
    tap('Back', 'content-desc'); target('Body Data'); tap('View Charts'); target('Recorded values · kg')
    assert not any(n.get('text') == 'About body charts' for n in nodes())
    tap('About body charts', 'content-desc'); target('About body charts'); tap('Got it')
    tap('Back', 'content-desc'); target('Body Data'); tap('Back', 'content-desc')
    tap('Tutorials'); tap('Reset Tutorials'); target('Reset Tutorials?'); capture('reset-confirm-normal'); tap('Cancel')
    assert state() == dismissed
    tap('Reset Tutorials'); tap('Reset'); assert state() == hidden
    unchanged_records(before)
    tap('Back', 'content-desc'); tap('Body Data'); tap('View Charts'); target('About body charts'); tap('Got it')
    final = state()
    assert final == dismissed
    settings(); tap('Export Backup'); tap('Choose file'); target('Save')
    name = tag+'-complete.json'; downloads.append(name); save_as(name); target('Export Backup')
    complete = json.loads(adb('shell', 'cat', '/sdcard/Download/'+name))
    assert complete['preferences'] == final['preferences']
    assert {ref: base64.b64decode(value) for ref, value in complete['photoAssets'].items()} == fixture_photos
    empty = {'schemaVersion': 1, 'onboarding': {'completed': True}}
    empty_name = tag+'-empty.json'; downloads.append(empty_name)
    empty_path = args.output / empty_name; empty_path.write_text(json.dumps(empty))
    adb('push', str(empty_path), '/sdcard/Download/'+empty_name)
    tap('Restore Backup'); choose(empty_name); target('Restore Backup?'); tap('Restore'); target('Restore Backup')
    assert not state()['progressPhotos'] and not state()['preferences']['dismissedTutorials']
    assert not set(fixture_photos).intersection(adb('shell', 'run-as', package, 'ls', 'no_backup/progress-photos').split())
    tap('Restore Backup'); choose(name); target('Restore Backup?'); tap('Restore'); target('Restore Backup')
    launch(); assert state() == final
    open_chart(); target('Recorded values · kg'); unchanged_records(final)
    settings(); tap('Knowledge Base & Support'); capture('help-normal')
    enter_field(next(n for n in nodes() if n.get('class') == 'android.widget.EditText'), 'troubleshoot')
    target('Troubleshoot files and missing data'); capture('help-search-normal')
    assert not any(n.get('text') == 'Restore on another device' for n in nodes())
    enter_field(next(n for n in nodes() if n.get('class') == 'android.widget.EditText'), 'nothing-matches-of')
    target('No matching articles. Try backup, photos, export, visibility or charts.'); capture('help-empty-normal')
    settings(); tap('Knowledge Base & Support')
    tap('Restore on another device'); capture('help-restore-normal')
    assert 'Active default network: none' in adb('shell', 'dumpsys', 'connectivity')
    for scale in ['1.3']:
        adb('shell', 'settings', 'put', 'system', 'font_scale', scale)
        settings(); tap('Data Export'); capture('quick-font-130'); tap('Granular Export'); capture('granular-font-130')
        tap('Scale Weight'); capture('granular-selected-font-130')
        settings(); tap('Data Visibility'); tap('Body Metrics Visibility'); capture('visibility-font-130')
        settings(); tap('Tutorials'); capture('tutorials-font-130'); tap('Reset Tutorials'); capture('reset-font-130'); tap('Cancel')
        settings(); tap('Knowledge Base & Support'); tap('Restore on another device'); capture('help-font-130')
    result = {'status': 'passed', 'serial': args.serial, 'offline': True,
              'native_save_and_back_cancel': True, 'separate_native_cancel_control': False,
              'independent_quick_and_granular_contents': True,
              'scoped_document_rejected_by_restore': True, 'hidden_records_and_photo_bytes_retained': True,
              'tutorial_first_dismiss_manual_reopen_reset': True, 'complete_backup_empty_store_restore_restart': True,
              'normal_and_130_percent_captures': True, 'source_executed_outcomes_claimed': False}
    (args.output/'result.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2), flush=True)
except BaseException:
    capture('failure')
    raise
finally:
    adb('shell', 'am', 'force-stop', package)
    write_private('shared_prefs/open_factor.xml', original_xml)
    adb('shell', 'run-as', package, 'mkdir', '-p', 'no_backup/progress-photos')
    for reference, payload in original_files.items():
        write_private('no_backup/progress-photos/'+reference, payload)
        assert adb('exec-out', 'run-as', package, 'cat', 'no_backup/progress-photos/'+reference, raw=True) == payload
    for reference in set(fixture_photos) - set(original_files):
        adb('shell', 'run-as', package, 'rm', '-f', 'no_backup/progress-photos/'+reference)
    assert set(adb('shell', 'run-as', package, 'ls', 'no_backup/progress-photos').split()) == set(original_files)
    assert adb('exec-out', 'run-as', package, 'cat', 'shared_prefs/open_factor.xml', raw=True) == original_xml
    adb('shell', 'settings', 'put', 'system', 'font_scale', font)
    adb('shell', 'svc', 'wifi', 'enable' if wifi == '1' else 'disable')
    adb('shell', 'svc', 'data', 'enable' if mobile == '1' else 'disable')
    for filename in downloads:
        adb('shell', 'rm', '-f', '/sdcard/Download/'+filename)
    adb('shell', 'rm', '-f', '/sdcard/of-portability-ui.xml')
    print('Exact preference XML and private originals restored. SHA-256 '+hashlib.sha256(original_xml).hexdigest(), flush=True)
