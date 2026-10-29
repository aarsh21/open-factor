#!/usr/bin/env python3
"""Check library filters, exercise defaults and scoped return using synthetic data."""
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


def field(label, value, attr='text'):
    tap(label, attr)
    focused = [n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true']
    assert len(focused) == 1, label
    old = focused[0].get('text', '')
    adb('shell', 'for i in ' + ' '.join(str(i) for i in range(len(old) + 3)) + '; do input keyevent 67; input keyevent 112; done')
    if value:
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
    from storage_snapshot import snapshot_data
    return snapshot_data(xml, adb)


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


def select_scope(current, target):
    for _ in range(8):
        if matching(current):
            tap(current)
            tap(target)
            return
        row = next(n for n in nodes() if n.get('scrollable') == 'true' and bounds(n)[2] - bounds(n)[0] > 2 * (bounds(n)[3] - bounds(n)[1]))
        left, top, right, bottom = bounds(row)
        inset = (right - left) // 5
        adb('shell', 'input', 'swipe', str(left + inset), str((top + bottom) // 2), str(right - inset), str((top + bottom) // 2), '300')
    raise AssertionError('Library scope was not reachable')


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
               history=[dict(id='history', name='Prior', startedAt=1000, finishedAt=2000,
                             exercises=[dict(id='old-entry', exerciseId='synthetic-001', notes='Old note', restSeconds=31,
                                             sets=[dict(id='old-set', reps=7, completed=True)])])],
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
# Canonicalize the deliberately minimal legacy fixture through a reversible UI action.
# Subsequent snapshots compare values, not absent legacy fields versus encoded defaults.
tap('Needle 000')
tap('Info')
tap('Toggle favorite', 'content-desc')
tap('Toggle favorite', 'content-desc')
tap('Close Info', 'content-desc')
tap('Back', 'content-desc')
before = state()
assert not before['favorites'] and not before['exerciseDefaults']
assert before['history'][0]['exercises'][0]['notes'] == 'Old note'
assert before['history'][0]['exercises'][0]['restSeconds'] == 31
assert before['history'][0]['exercises'][0]['sets'][0]['reps'] == 7
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
tap('Info')
assert matching('Instructions') and not matching('Add Exercise')
capture('03-info-sheet')
tap('Close Info', 'content-desc')
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

# Defaults save/cancel and validation must affect new entries, never historical records.
select_resistance('', 'All Resistance')
tap('Needle 000')
capture('10-exercise-settings-default')
tap('Exercise Note')
field('Note', 'Discard this edit')
tap('Cancel')
assert state() == before
tap('Exercise Note')
field('Note', 'Brace then pause')
tap('Save')
tap('Rest Timer')
field('Rest seconds', '1801')
assert any(n.get('enabled') == 'false' and any(c.get('text') == 'Save' for c in n.iter('node')) for n in nodes())
capture('11-invalid-rest')
field('1801', '75')
tap('Save')
tap('Do Not Recommend')
saved = state()
assert saved['exerciseDefaults']['synthetic-000'] == dict(note='Brace then pause', restSeconds=75, doNotRecommend=True)
assert {k: v for k, v in saved.items() if k != 'exerciseDefaults'} == {k: v for k, v in before.items() if k != 'exerciseDefaults'}
capture('12-saved-defaults')

# A partially edited note survives OS process recreation, but remains unsaved until Save.
tap('Exercise Note')
field('Brace then pause', 'Unsaved replacement')
adb('shell', 'input', 'keyevent', '3')
time.sleep(1)
old_pid = adb('shell', 'pidof', 'org.openfactor.app').strip()
adb('shell', 'am', 'kill', 'org.openfactor.app')
time.sleep(1)
assert not adb('shell', 'pidof org.openfactor.app || true').strip()
adb('shell', 'am', 'start', '-n', 'org.openfactor.app/.MainActivity')
assert matching('Unsaved replacement') and adb('shell', 'pidof', 'org.openfactor.app').strip() != old_pid
assert state() == saved
capture('13-recreated-unsaved-note')
tap('Cancel')
assert matching('Brace then pause')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
time.sleep(2)
capture('14-settings-large-text')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
tap('Back', 'content-desc')
select_scope('All', 'Deprioritized')
assert matching('Needle 000') and not matching('Needle 001')
capture('15-deprioritized')
select_scope('Deprioritized', 'Performed')
assert matching('Needle 001') and not matching('Needle 000')
capture('16-performed')

# A library scope must not hide the same exercise from the independent workout picker.
tap('Shortcuts', 'content-desc')
tap('Empty Workout')
tap('Add Exercises', 'content-desc')
field('Search exercises', 'Needle 000')
tap('Needle 000', first=True)
assert matching('Instructions') and matching('Add Exercise')
tap('Add Exercise')
entry = state()['workoutDraft']['exercises'][0]
assert entry['notes'] == '' and entry['restSeconds'] == 75
assert entry['sets'][0]['reps'] is None
tap('Start Workout')
assert state()['active']['exercises'][0] == entry
assert matching('Pinned exercise note') and matching('Brace then pause')
assert state()['history'] == before['history']
assert state()['scaleWeights'] == before['scaleWeights'] and state()['progressPhotos'] == before['progressPhotos']
capture('17-defaults-in-logger')

# A workout-entry note is independent of the pin and survives process recreation.
tap('Exercise options for Needle 000', 'content-desc')
tap('Exercise notes')
field('Exercise notes', 'Workout entry observation')
tap('Save')
assert state()['active']['exercises'][0]['notes'] == 'Workout entry observation'
assert state()['exerciseDefaults']['synthetic-000']['note'] == 'Brace then pause'
assert matching('Brace then pause') and matching('Workout entry observation')
capture('19-pin-and-workout-note')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
time.sleep(2)
capture('20-pin-and-workout-note-large-text')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
active = state()['active']
adb('shell', 'input', 'keyevent', '3')
time.sleep(1)
old_pid = adb('shell', 'pidof', 'org.openfactor.app').strip()
adb('shell', 'am', 'kill', 'org.openfactor.app')
time.sleep(1)
assert not adb('shell', 'pidof org.openfactor.app || true').strip()
adb('shell', 'am', 'start', '-n', 'org.openfactor.app/.MainActivity')
assert matching('Brace then pause') and matching('Workout entry observation')
assert adb('shell', 'pidof', 'org.openfactor.app').strip() != old_pid
assert state()['active'] == active

# Editing a pin updates its separate live display, never the workout entry.
adb('shell', 'input', 'keyevent', '4')
select_scope('Performed', 'All')
field('Needle', 'Needle 000')
tap('Needle 000', first=True)
tap('Exercise Note')
assert matching('This note stays pinned to the exercise and is not copied into workout history.')
field('Brace then pause', 'Updated pinned reminder')
capture('21-pin-editor-contract')
tap('Save')
assert state()['active'] == active and state()['history'] == before['history']
tap('Back', 'content-desc')
tap('Dashboard')
tap('Resume Empty')
assert matching('Updated pinned reminder') and matching('Workout entry observation')
assert not matching('Brace then pause')
capture('22-updated-pin-existing-workout')

# Clearing overrides removes the pin but preserves the entry note and rest timer.
active = state()['active']
adb('shell', 'input', 'keyevent', '4')
tap('Exercises')
tap('Needle 000', first=True)
tap('Rest Timer')
field('75', '')
tap('Save')
assert matching('Default · 120s')
tap('Exercise Note')
field('Updated pinned reminder', '')
tap('Save')
tap('Do Not Recommend')
assert 'synthetic-000' not in state()['exerciseDefaults']
assert state()['active'] == active and state()['history'] == before['history']
capture('18-cleared-defaults-existing-entry-unchanged')
tap('Back', 'content-desc')
tap('Dashboard')
tap('Resume Empty')
assert not matching('Pinned exercise note') and not matching('Updated pinned reminder')
assert matching('Workout entry observation')
capture('23-cleared-pin-retains-workout-note')
field('Needle 000 set 1 reps (Reps)', '9', 'content-desc')
tap('Complete set 1 for Needle 000', 'content-desc')
tap('Workout options', 'content-desc')
tap('Finish Workout')
tap('Finish')
assert matching('Workout entry observation')
assert not matching('Brace then pause') and not matching('Updated pinned reminder')
finished = state()['history'][0]
assert finished['exercises'][0]['notes'] == 'Workout entry observation'
assert state()['history'][1:] == before['history']
assert state()['scaleWeights'] == before['scaleWeights'] and state()['progressPhotos'] == before['progressPhotos']
capture('24-history-has-only-workout-note')
adb('shell', 'am', 'force-stop', 'org.openfactor.app')
adb('shell', 'am', 'start', '-n', 'org.openfactor.app/.MainActivity')
assert state()['history'][0] == finished
(args.output / 'apk-sha256.txt').write_text(adb('shell', 'sha256sum', adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')))
(args.output / 'result.json').write_text(json.dumps(dict(status='passed', serial=args.serial,
    query_resets_scroll=True, detail_return_preserves_scroll=True, resistance_entity_alternatives=True,
    sheet_search_cancel=True, combined_filter_empty_and_clear=True, recreation=True,
    read_only_filters_do_not_mutate_data=True, defaults_save_cancel_validation=True,
    unsaved_note_recreation=True, performed_and_deprioritized_scopes=True,
    info_sheet_and_contextual_add=True, rest_default_in_new_entry=True,
    pinned_note_separate_from_workout_notes=True, live_pin_update_and_removal=True,
    only_workout_notes_enter_history=True,
    clear_defaults_preserves_existing_entry=True,
    body_history_unchanged=True, offline=True), indent=2) + '\n')
print((args.output / 'result.json').read_text())
