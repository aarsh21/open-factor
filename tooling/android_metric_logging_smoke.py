#!/usr/bin/env python3
"""Log every retained catalog metric combination on an explicitly disposable emulator.

Seeds blank synthetic session rows through the debug store, then enters/completes
actuals through the UI and checks persisted values, restart, finish and history.
Requires --replace-synthetic-store because the fixture replaces this app's data.
"""
import argparse
from datetime import date, timedelta
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET


parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--replace-synthetic-store', action='store_true', required=True)
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Use an explicitly disposable emulator, never a physical phone')
args.output.mkdir(parents=True, exist_ok=True)


def adb(*command, raw=False, input=None):
    result = subprocess.check_output(['adb', '-s', args.serial, *command], input=input, timeout=40)
    return result if raw else result.decode()


def nodes():
    for attempt in range(8):
        try:
            output = adb('shell', 'uiautomator', 'dump', '/sdcard/of-metrics.xml')
            if 'dumped to:' not in output:
                raise RuntimeError('UI tree was not refreshed')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/of-metrics.xml')).iter('node'))
        except (subprocess.CalledProcessError, ET.ParseError, RuntimeError):
            if attempt == 7:
                raise
            time.sleep(.4)


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))


def find(label, attr='text', prefix=False):
    for _ in range(5):
        found = [n for n in nodes() if (n.get(attr, '').startswith(label) if prefix else n.get(attr, '').strip() == label)]
        if found:
            return found
        time.sleep(.3)
    capture('failure')
    raise AssertionError((label, attr))


def tap(label, attr='text', prefix=False):
    n = find(label, attr, prefix)[-1]
    left, top, right, bottom = bounds(n)
    assert bottom > top and right > left, (label, n.attrib)
    adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))


def back():
    adb('shell', 'input', 'keyevent', '4')


def enter(description, value, attr='content-desc'):
    tap(description, attr, True)
    fields = [n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true']
    assert len(fields) == 1, description
    old = fields[0].get('text', '')
    adb('shell', 'for i in ' + ' '.join(str(i) for i in range(len(old) + 2)) + '; do input keyevent 67; input keyevent 112; done')
    if value:
        adb('shell', 'input', 'text', value.replace(' ', '%s'))
    back()


def capture(name):
    nodes()
    (args.output / f'{name}.xml').write_text(adb('shell', 'cat', '/sdcard/of-metrics.xml'))
    (args.output / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p', raw=True))


def state():
    xml = ET.fromstring(adb('shell', 'run-as', 'org.openfactor.app', 'cat', 'shared_prefs/open_factor.xml'))
    return json.loads(next(n.text for n in xml if n.get('name') == 'data'))


def launch():
    adb('shell', 'am', 'force-stop', 'org.openfactor.app')
    adb('shell', 'am', 'start', '-n', 'org.openfactor.app/.MainActivity')


def select(name, reverse=False):
    for _ in range(24):
        tree = nodes()
        matches = [n for n in tree if n.get('content-desc') == f'Select {name}']
        if matches:
            tap(f'Select {name}', 'content-desc')
            return
        start, end = ('210', '920') if reverse else ('920', '210')
        adb('shell', 'input', 'swipe', start, '340', end, '340', '250')
    raise AssertionError(('Exercise strip', name))


catalog = json.loads((Path(__file__).resolve().parents[1] / 'openfactor/app/src/main/assets/catalog.json').read_text())
groups = {}
for exercise in catalog:
    groups.setdefault(exercise['properties']['Metrics'], exercise)
chin = next(e for e in catalog if e['name'].casefold() == 'chin-up')
cases = [chin] + [e for key, e in groups.items() if key != 'Reps']
assert len(cases) == 18
today = date.fromisoformat(adb('shell', 'date', '+%F').strip())
body = [
    {'id': 'prior', 'date': str(today - timedelta(days=1)), 'weightKg': 83.0},
    {'id': 'future', 'date': str(today + timedelta(days=1)), 'weightKg': 102.0},
]
entries = [dict(id=f'entry-{i}', exerciseId=e['id'], sets=[dict(id=f'set-{i}', weightKg=None, reps=None)]) for i, e in enumerate(cases)]
fixture = dict(schemaVersion=1, onboarding={'completed': True}, scaleWeights=body,
               preferences=dict(showRir=False, exerciseAutoNext=False, autoRest=False),
               active=dict(id='metric-workout', name='Synthetic Metrics', startedAt=int(time.time()*1000), exercises=entries, selectedExerciseId='entry-0'))
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
adb('shell', 'am', 'force-stop', 'org.openfactor.app')
xml = ET.Element('map')
ET.SubElement(xml, 'string', name='data').text = json.dumps(fixture)
adb('shell', 'run-as', 'org.openfactor.app', 'tee', 'shared_prefs/open_factor.xml', input=ET.tostring(xml, encoding='utf-8'))
launch()
tap('Resume Synthetic Metrics')
capture('reps-only-blank')
assert any('83 kg' in n.get('text', '') and '+79.2 kg (95%)' in n.get('text', '') for n in nodes()), 'Bodyweight must use the dated prior record, not future weight'
assert not any('weight (' in n.get('content-desc', '') for n in nodes()), 'Chin-Up must not require an external load'
tap('Complete set 1 for Chin-Up', 'content-desc')
assert not state()['active']['exercises'][0]['sets'][0].get('completed'), 'Blank reps completed a set'

results = []
for i, exercise in enumerate(cases):
    name = re.sub(r'(^|[\s(\-/])([^\W\d_])', lambda match: match[1] + match[2].upper(), exercise['name'])
    if i:
        select(name)
    tokens = exercise['properties']['Metrics'].split(', ')
    expected = {}
    for token in tokens:
        if token == 'None':
            continue
        description = f'{name} set 1 {token.lower()} ('
        if token.startswith('Reps'):
            value, key, canonical = '13', 'reps', 13
        elif token == 'Assistance weight':
            value, key, canonical = '17.25', 'assistanceKg', 17.25
        elif token.startswith('Weight'):
            value, key, canonical = '42.5', 'weightKg', 42.5
        elif token.startswith('Duration'):
            value, key, canonical = '83.5', 'durationSeconds', 83.5
        elif token == 'Distance long':
            value, key, canonical = '2.312', 'distanceMeters', 2312.0
        else:
            value, key, canonical = '17.25', 'distanceMeters', 17.25
        enter(description, value)
        if i == 0 and token == 'Reps':
            enter(description, '')
            assert state()['active']['exercises'][i]['sets'][0]['reps'] is None, 'Clearing reps retained stale actuals'
            enter(description, '0')
            tap(f'Complete set 1 for {name}', 'content-desc')
            assert not state()['active']['exercises'][i]['sets'][0].get('completed'), 'Invalid reps completed a set'
            capture('invalid-reps')
            enter(description, value)
        expected[key] = canonical
    row = state()['active']['exercises'][i]['sets'][0]
    for key in ['reps', 'weightKg', 'assistanceKg', 'durationSeconds', 'distanceMeters']:
        assert row.get(key) == expected.get(key), (name, key, row, expected)
    tap(f'Complete set 1 for {name}', 'content-desc')
    assert state()['active']['exercises'][i]['sets'][0]['completed']
    if i in [0, 1, 4, 11, 17]:
        capture(f'metric-{i:02d}-completed')
    results.append(dict(exercise=name, metrics=tokens, actuals=expected))

# RIR is optional for rep-based rows and absent from duration/distance rows.
tap('Workout options', 'content-desc')
tap('Workout settings')
switches = [n for n in nodes() if n.get('checkable') == 'true']
assert len(switches) == 2
left, top, right, bottom = bounds(switches[0])
adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
tap('Done')
assert state()['preferences']['showRir']
assert not any('reps in reserve' in n.get('content-desc', '') for n in nodes())
name = '45° Cable Rear Delt Fly'
select(name, reverse=True)
enter(f'{name} set 1 reps in reserve', '3')
assert state()['active']['exercises'][1]['sets'][0]['rir'] == 3
capture('optional-rir')
tap(f'Exercise options for {name}', 'content-desc')
tap('Exercise notes')
enter('Exercise notes', 'Do not save', 'text')
tap('Cancel')
assert not state()['active']['exercises'][1].get('notes')
tap(f'Exercise options for {name}', 'content-desc')
tap('Exercise notes')
enter('Exercise notes', 'Seat 3', 'text')
tap('Save')
assert state()['active']['exercises'][1]['notes'] == 'Seat 3'
tap('Workout options', 'content-desc')
tap('Workout notes')
enter('Workout notes', 'Synthetic session note', 'text')
tap('Save')
before_units = state()['active']
tap('Workout options', 'content-desc')
tap('Minimize Workout')
tap('Settings')
tap('Units')
tap('Pounds (lb)')
tap('Dashboard')
tap('Resume Synthetic Metrics')
assert state()['active'] == before_units, 'Unit change altered canonical actuals'
weight = find(f'{name} set 1 weight per side (lb / side)', 'content-desc')[0]
assert weight.get('text') == '93.696', weight.attrib
capture('pounds-rir')

saved = state()['active']
assert state()['scaleWeights'] == body
launch()
tap('Resume Synthetic Metrics')
assert state()['active'] == saved
tap('Workout options', 'content-desc')
tap('Finish Workout')
tap('Finish')
find('Workout Complete')
history = state()['history'][0]
assert history['exercises'] == saved['exercises'] and not state().get('active')
capture('finished-summary')
launch()
assert state()['history'][0] == history and state()['scaleWeights'] == body
assert 'Active default network: none' in adb('shell', 'dumpsys', 'connectivity')
(args.output / 'result.json').write_text(json.dumps(dict(status='passed', serial=args.serial, cases=results, cleared_actuals=True, optional_rir=True, units_preserve_actuals=True, notes_save_cancel=True, restart=True, history=True, body_unchanged=True, offline=True), indent=2) + '\n')
(args.output / 'apk-sha256.txt').write_text(adb('shell', 'sha256sum', adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')))
print(f'Passed {len(results)} metric combinations, blank/invalid recovery, restart, finish, history and body isolation')
