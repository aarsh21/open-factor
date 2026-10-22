#!/usr/bin/env python3
"""Check library query/scroll and resistance filters using disposable synthetic data."""
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
            output = adb('shell', 'uiautomator', 'dump', '/sdcard/of-library.xml')
            if 'dumped to:' not in output:
                raise RuntimeError('UI tree was not refreshed')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/of-library.xml')).iter('node'))
        except (subprocess.CalledProcessError, ET.ParseError, RuntimeError):
            if attempt == 7:
                raise
            time.sleep(.4)


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))


def matching(label, attr='text'):
    return [n for n in nodes() if n.get(attr, '').strip() == label]


def tap(label, attr='text', first=False):
    for _ in range(5):
        found = matching(label, attr)
        if found:
            break
        time.sleep(.3)
    assert found, (label, attr)
    left, top, right, bottom = bounds(found[0 if first else -1])
    assert right > left and bottom > top, (label, 'not visible')
    adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))


def field(label, value):
    tap(label)
    focused = [n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true']
    assert len(focused) == 1, label
    old = focused[0].get('text', '')
    adb('shell', 'for i in ' + ' '.join(str(i) for i in range(len(old) + 3)) + '; do input keyevent 67; input keyevent 112; done')
    adb('shell', 'input', 'text', value.replace(' ', '%s'))
    focused = [n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true']
    assert focused[0].get('text') == value, (label, value)
    adb('shell', 'input', 'keyevent', '4')


def capture(name):
    nodes()
    (args.output / f'{name}.xml').write_text(adb('shell', 'cat', '/sdcard/of-library.xml'))
    (args.output / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p', raw=True))
    print('Captured ' + name, flush=True)


def state():
    xml = ET.fromstring(adb('shell', 'run-as', 'org.openfactor.app', 'cat', 'shared_prefs/open_factor.xml'))
    return json.loads(next(n.text for n in xml if n.get('name') == 'data'))


def scroll_list():
    scroll = max((n for n in nodes() if n.get('scrollable') == 'true'), key=lambda n: bounds(n)[3] - bounds(n)[1])
    left, top, right, bottom = bounds(scroll)
    adb('shell', 'input', 'swipe', str((left + right) // 2), str(top + (bottom - top) * 4 // 5), str((left + right) // 2), str(top + (bottom - top) // 5), '300')


def open_resistance():
    for _ in range(4):
        found = matching('Resistance filter', 'content-desc')
        if found and bounds(found[0])[2] - bounds(found[0])[0] > 90:
            tap('Resistance filter', 'content-desc')
            return
        row = next(n for n in nodes() if n.get('scrollable') == 'true' and bounds(n)[2] - bounds(n)[0] > 2 * (bounds(n)[3] - bounds(n)[1]))
        left, top, right, bottom = bounds(row)
        # Keep gestures away from Android's left/right system Back edges.
        inset = (right - left) // 5
        adb('shell', 'input', 'swipe', str(right - inset), str((top + bottom) // 2), str(left + inset), str((top + bottom) // 2), '300')
    raise AssertionError('Resistance filter was not reachable')


def select_resistance(query, label):
    open_resistance()
    field('Filter equipment by name', query)
    tap(label, first=True)


root = Path(__file__).resolve().parents[1]
index = json.loads((root / 'openfactor/app/src/main/assets/equipment.json').read_text())['uuidIndex']
ids = {v['name']: key for key, v in index.items() if v.get('type') == 'equipment'}
custom = [dict(id=f'synthetic-{i:03}', name=f'Needle {i:03}' if i < 20 else f'Other {i:03}',
               muscle='Other', muscles=[], equipment='Dumbbells', custom=True,
               properties={'Metrics': 'Reps'}, searchBoost=1000,
               browseCategories=['Bodyweight & Personal'],
               resistanceEquipment=[[ids['Dumbbells']]], supportEquipment=[]) for i in range(100)]
custom[0].update(resistanceEquipment=[[ids['Barbell'], ids['Weight plates']], [ids['Dumbbells']]],
                 supportEquipment=[[ids['Flat bench']]])
custom[1].update(resistanceEquipment=[[ids['Barbell']]])
custom[2].update(resistanceEquipment=[[]])
fixture = dict(schemaVersion=1, onboarding={'completed': True}, customExercises=custom,
               gyms=[dict(id='all', name='Everything Gym', equipmentIds=list(ids.values()))],
               scaleWeights=[dict(id='existing', date='2026-10-21', weightKg=83.0)],
               bodyMeasurements=[], progressPhotos=[],
               preferences=dict(autoRest=False))
adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
adb('shell', 'am', 'force-stop', 'org.openfactor.app')
xml = ET.Element('map')
ET.SubElement(xml, 'string', name='data').text = json.dumps(fixture)
adb('shell', 'run-as', 'org.openfactor.app', 'mkdir', '-p', 'shared_prefs')
adb('shell', 'run-as', 'org.openfactor.app', 'tee', 'shared_prefs/open_factor.xml', input=ET.tostring(xml, encoding='utf-8'))
adb('shell', 'am', 'start', '-n', 'org.openfactor.app/.MainActivity')
tap('Exercises')
before = state()
assert matching('Needle 000')
tap(next(n.get('text') for n in nodes() if re.fullmatch(r'See \d+ More', n.get('text', ''))))
for _ in range(3):
    scroll_list()
assert not matching('Needle 000'), 'Fixture did not scroll away from the first result'
capture('01-scrolled-library')
field('Search exercises', 'Needle')
capture('02-new-query')
assert matching('Needle 000'), 'A new query retained the old scroll offset and hid the first result'
print('New query exposes the first result', flush=True)

# Returning from detail with the same query must retain the user's reading position.
scroll_list()
visible = [n.get('text') for n in nodes() if re.fullmatch(r'Needle \d{3}', n.get('text', ''))]
assert visible and 'Needle 000' not in visible
tap(visible[0])
tap('Back', 'content-desc')
assert matching(visible[0]) and not matching('Needle 000'), 'Detail return reset unchanged search position'
capture('03-returned-scroll')

open_resistance()
field('Filter equipment by name', 'Barbell')
capture('04-resistance-sheet')
tap('Barbell', first=True)
assert matching('Needle 000') and matching('Needle 001') and not matching('Needle 002')
capture('05-filtered-alternatives')

# Sheet search/dismissal does not change the selected entity or global query.
open_resistance()
field('Filter equipment by name', 'no-such-equipment')
assert matching('No matching equipment')
capture('06-equipment-empty')
tap('Close Resistance', 'content-desc')
assert matching('Needle 000') and matching('Needle 001') and not matching('Needle 002')
select_resistance('Dumbbells', 'Dumbbells')
assert matching('Needle 000') and not matching('Needle 001') and not matching('Needle 002')
field('Needle', 'Needle 000')
assert matching('Needle 000')
select_resistance('Bodyweight', 'Bodyweight Only')
assert matching('No exercises found')
capture('07-intersection-empty')
tap('Clear Filters')
assert matching('Needle 000') and matching('Search exercises')
capture('08-cleared-filters')

# The selected entity and query survive real background process recreation.
field('Search exercises', 'Needle')
select_resistance('Bodyweight', 'Bodyweight Only')
assert matching('Needle 002') and not matching('Needle 000')
adb('shell', 'input', 'keyevent', '3')
time.sleep(1)
old_pid = adb('shell', 'pidof', 'org.openfactor.app').strip()
adb('shell', 'am', 'kill', 'org.openfactor.app')
time.sleep(1)
assert not adb('shell', 'pidof org.openfactor.app || true').strip()
adb('shell', 'am', 'start', '-n', 'org.openfactor.app/.MainActivity')
assert matching('Needle 002') and not matching('Needle 000')
assert adb('shell', 'pidof', 'org.openfactor.app').strip() != old_pid
assert state() == before, 'Read-only library filtering mutated persisted training/body/exercise data'
assert 'Active default network: none' in adb('shell', 'dumpsys', 'connectivity')
capture('09-recreated-filter')
(args.output / 'apk-sha256.txt').write_text(adb('shell', 'sha256sum', adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')))
(args.output / 'result.json').write_text(json.dumps(dict(status='passed', serial=args.serial,
    query_resets_scroll=True, detail_return_preserves_scroll=True, resistance_entity_alternatives=True,
    sheet_search_cancel=True, combined_filter_empty_and_clear=True, recreation=True,
    persisted_data_unchanged=True, offline=True), indent=2) + '\n')
print((args.output / 'result.json').read_text())
