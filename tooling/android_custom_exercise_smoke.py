#!/usr/bin/env python3
"""Exercise the five-step custom wizard offline on an explicitly disposable emulator.

Uses synthetic body/training fixtures, real UI edits, persisted-state assertions,
process recreation, gym availability, contextual Add, and metric-aware logging.
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
            output = adb('shell', 'uiautomator', 'dump', '/sdcard/of-custom.xml')
            if 'dumped to:' not in output:
                raise RuntimeError('UI tree was not refreshed')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/of-custom.xml')).iter('node'))
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
    for _ in range(12):
        tree = nodes()
        matches = [n for n in tree if (n.get(attr, '').startswith(label) if prefix else n.get(attr, '').strip() == label) and bounds(n)[3] - bounds(n)[1] >= 30]
        if matches:
            left, top, right, bottom = bounds(matches[-1])
            adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
            return
        scrolls = [n for n in tree if n.get('scrollable') == 'true' and bounds(n)[3] - bounds(n)[1] > 120]
        if not scrolls:
            break
        left, top, right, bottom = bounds(max(scrolls, key=lambda n: bounds(n)[3] - bounds(n)[1]))
        x = (left + right) // 2
        adb('shell', 'input', 'swipe', str(x), str(top + (bottom - top) * 4 // 5), str(x), str(top + (bottom - top) // 5), '280')
    find(label, attr, prefix)
    raise AssertionError('Control clipped: ' + label)


def back():
    adb('shell', 'input', 'keyevent', '4')


def disabled(label):
    return any(n.get('enabled') == 'false' and any(child.get('text') == label for child in n.iter('node')) for n in nodes())


def enter(label, value, attr='text', prefix=False):
    tap(label, attr, prefix)
    fields = [n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true']
    assert len(fields) == 1, label
    old = fields[0].get('text', '')
    adb('shell', 'for i in ' + ' '.join(str(i) for i in range(len(old) + 3)) + '; do input keyevent 67; input keyevent 112; done')
    if value:
        adb('shell', 'input', 'text', value.replace(' ', '%s'))
    fields = [n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true']
    assert fields[0].get('text') == value, (label, fields[0].get('text'), value)
    back()


def capture(name):
    nodes()
    (args.output / f'{name}.xml').write_text(adb('shell', 'cat', '/sdcard/of-custom.xml'))
    (args.output / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p', raw=True))
    print('Captured ' + name, flush=True)


def state():
    xml = ET.fromstring(adb('shell', 'run-as', 'org.openfactor.app', 'cat', 'shared_prefs/open_factor.xml'))
    return json.loads(next(n.text for n in xml if n.get('name') == 'data'))


def launch():
    adb('shell', 'am', 'start', '-n', 'org.openfactor.app/.MainActivity')


def equipment(name):
    assert len([n for n in nodes() if n.get('class') == 'android.widget.EditText']) == 1
    enter('android.widget.EditText', name, 'class')
    tap(name)


root = Path(__file__).resolve().parents[1]
index = json.loads((root / 'openfactor/app/src/main/assets/equipment.json').read_text())['uuidIndex']
equipment_ids = {v['name']: key for key, v in index.items() if v.get('type') == 'equipment'}
today = date.fromisoformat(adb('shell', 'date', '+%F').strip())
fixture = dict(schemaVersion=1, onboarding={'completed': True},
               scaleWeights=[dict(id='prior', date=str(today - timedelta(days=1)), weightKg=83.0), dict(id='future', date=str(today + timedelta(days=1)), weightKg=102.0)],
               bodyMeasurements=[], progressPhotos=[],
               gyms=[dict(id='all', name='Everything Gym', equipmentIds=list(equipment_ids.values())), dict(id='limited', name='Limited Gym', equipmentIds=[equipment_ids['Dumbbells']])],
               preferences=dict(showRir=False, autoRest=False))
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
adb('shell', 'am', 'force-stop', 'org.openfactor.app')
xml = ET.Element('map')
ET.SubElement(xml, 'string', name='data').text = json.dumps(fixture)
adb('shell', 'run-as', 'org.openfactor.app', 'mkdir', '-p', 'shared_prefs')
adb('shell', 'run-as', 'org.openfactor.app', 'tee', 'shared_prefs/open_factor.xml', input=ET.tostring(xml, encoding='utf-8'))
launch()
tap('Shortcuts', 'content-desc')
tap('Empty Workout')
before = state()
tap('Add Exercises', 'content-desc')
tap('Create custom exercise', 'content-desc')
find('Build your exercise')
assert disabled('Next')
capture('01-basics-blank')
enter('Exercise Name', 'Synthetic Supported Hold')
assert disabled('Next'), 'Name alone must not silently choose Weight/Reps'
tap('Trackable Metric 1', 'content-desc')
capture('02-metric-sheet')
tap('Weight (Assistance)')
tap('Trackable Metric 2', 'content-desc')
tap('Weight')
find('Choose different metric types for the two fields.')
assert disabled('Next')
tap('Trackable Metric 2', 'content-desc')
tap('Duration')
tap('Next')
find('0 primary, 0 secondary')
tap('Select Lats', 'content-desc')
tap('Select Biceps', 'content-desc')
tap('Select Biceps', 'content-desc')
find('1 primary, 1 secondary')
capture('03-muscles')
back()
find('Synthetic Supported Hold')
tap('Next')
find('1 primary, 1 secondary')
tap('Next')
find('Bodyweight Only')
tap('Edit Resistance', 'content-desc')
equipment('Dumbbells')
tap('Cancel')
find('Bodyweight Only')
tap('Edit Resistance', 'content-desc')
equipment('Dumbbells')
tap('Done')
tap('Add Alternate Resistance')
equipment('Barbell')
equipment('Weight plates')
capture('04-equipment-sheet')
tap('Done')
tap('Add Support')
equipment('Flat bench')
tap('Done')
capture('05-equipment')
tap('Next')
assert disabled('Next'), 'Contribution is an explicit required input'
tap('Increase Range of Motion', 'content-desc')
tap('Increase Range of Motion', 'content-desc')
for _ in range(4):
    tap('Increase Stability', 'content-desc')
enter('Body Weight Contribution', '101')
find('Enter a percentage from 0 to 100')
assert disabled('Next')
capture('06-invalid-contribution')
enter('Body Weight Contribution', '73')
find('60.6 kg at your current weight')
capture('07-final-details')
tap('Primary Joint Action', 'content-desc')
enter('Search Primary Joint Action', 'Shoulder Adduction')
tap('Shoulder Adduction')
enter('Alternate Names', 'Synthetic Cable Hold, Alternate Hold')
enter('Instructions and technique notes', 'Hold the support steady')
tap('Next')
find('Does everything look correct?')
capture('08-review')
assert not state().get('customExercises'), 'Wizard edits and review must not save prematurely'

# Android saves the wizard and routing state when the app is backgrounded.
adb('shell', 'input', 'keyevent', '3')
time.sleep(1)
old_pid = adb('shell', 'pidof', 'org.openfactor.app').strip()
adb('shell', 'am', 'kill', 'org.openfactor.app')
time.sleep(1)
assert not adb('shell', 'pidof org.openfactor.app || true').strip(), 'Process remained alive after background kill'
launch()
find('Does everything look correct?')
assert adb('shell', 'pidof', 'org.openfactor.app').strip() != old_pid, 'Recreation did not restart the process'
tap('Create')
find('Synthetic Supported Hold')
created = state()['customExercises']
assert len(created) == 1
custom = created[0]
assert custom['properties']['Metrics'] == 'Assistance weight, Duration'
assert custom['properties']['Type'] == 'Multi-joint (compound)'
assert custom['properties']['Region'] == 'Upper body'
assert custom['properties']['Range of Motion'] == '2' and custom['properties']['Stability'] == '4'
assert custom['properties']['Primary Joint Action'] == 'Shoulder Adduction'
assert custom['muscles'] == ['Lats'] and custom['secondary'] == ['Biceps']
assert custom['aliases'] == ['Synthetic Cable Hold', 'Alternate Hold']
assert custom['bodyweight'] == .73
assert [set(group) for group in custom['resistanceEquipment']] == [{equipment_ids['Dumbbells']}, {equipment_ids['Barbell'], equipment_ids['Weight plates']}]
assert [set(group) for group in custom['supportEquipment']] == [{equipment_ids['Flat bench']}]
tap('Details')
tap('Support Equipment')
find('Flat bench')
capture('09-saved-details')
tap('Add Exercise')
assert state()['workoutDraft']['exercises'][0]['exerciseId'] == custom['id'] and state().get('active') is None
tap('Add Exercises', 'content-desc')
enter('Search exercises', 'Synthetic Cable Hold')
find('Synthetic Supported Hold')
tap('Everything Gym')
tap('Limited Gym')
find('No exercises found')
capture('10-gym-exclusion')
tap('Limited Gym')
tap('Everything Gym')
find('Synthetic Supported Hold')
tap('Done')
tap('Start Workout')
find('Workout options', 'content-desc')
enter('Synthetic Supported Hold set 1 assistance weight (', '17.25', 'content-desc', True)
enter('Synthetic Supported Hold set 1 duration (', '83.5', 'content-desc', True)
tap('Complete set 1 for Synthetic Supported Hold', 'content-desc')
row = state()['active']['exercises'][0]['sets'][0]
assert row['assistanceKg'] == 17.25 and row['durationSeconds'] == 83.5 and row['completed']
assert row.get('weightKg') is None and row.get('reps') is None
capture('11-custom-logger')
adb('shell', 'am', 'force-stop', 'org.openfactor.app')
launch()
assert state()['customExercises'] == created
assert state()['active']['exercises'][0]['sets'][0] == row
for key in ['scaleWeights', 'bodyMeasurements', 'progressPhotos']:
    assert state().get(key) == before.get(key), (key, 'Custom creation changed body/photo data')
assert 'Active default network: none' in adb('shell', 'dumpsys', 'connectivity')
(args.output / 'state.json').write_text(json.dumps(state(), indent=2) + '\n')
(args.output / 'apk-sha256.txt').write_text(adb('shell', 'sha256sum', adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')))
(args.output / 'result.json').write_text(json.dumps(dict(status='passed', serial=args.serial, five_step_flow=True, no_save_before_create=True, recreation=True, required_fields_and_recovery=True, structured_metadata_and_restart=True, gym_requirements=True, alias_search=True, contextual_add=True, metric_logging=True, body_and_photos_unchanged=True, offline=True), indent=2) + '\n')
print((args.output / 'result.json').read_text())
