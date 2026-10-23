#!/usr/bin/env python3
"""Check pre-start composition and contextual exercise actions on a disposable emulator.

Uses synthetic data only, never opens the source app. Start with no workout or
program drafts. Captures include UI trees and actual persisted state assertions.
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
    raise SystemExit('Use a disposable emulator, never a physical device')
args.output.mkdir(parents=True, exist_ok=True)


def adb(*command, raw=False):
    result = subprocess.check_output(['adb', '-s', args.serial, *command], timeout=40)
    return result if raw else result.decode()


def nodes():
    for attempt in range(8):
        try:
            output = adb('shell', 'uiautomator', 'dump', '/sdcard/of-composition.xml')
            if 'dumped to:' not in output:
                raise RuntimeError('UI tree was not refreshed')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/of-composition.xml')).iter('node'))
        except (subprocess.CalledProcessError, ET.ParseError, RuntimeError):
            if attempt == 7:
                raise
            time.sleep(.4)


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))


def find(label, attr='text', prefix=False):
    for _ in range(6):
        matches = [n for n in nodes() if (n.get(attr, '').startswith(label) if prefix else n.get(attr, '').strip() == label)]
        if matches:
            return matches
        time.sleep(.3)
    capture('failure')
    raise AssertionError((label, attr))


def tap(label, attr='text', last=False, prefix=False):
    for _ in range(10):
        tree = nodes()
        matches = [n for n in tree if (n.get(attr, '').startswith(label) if prefix else n.get(attr, '').strip() == label) and bounds(n)[3] - bounds(n)[1] >= 35]
        if matches:
            left, top, right, bottom = bounds(matches[-1 if last else 0])
            adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
            return
        scrolls = [n for n in tree if n.get('scrollable') == 'true']
        if not scrolls:
            break
        left, top, right, bottom = bounds(max(scrolls, key=lambda n: bounds(n)[3] - bounds(n)[1]))
        x = (left + right) // 2
        adb('shell', 'input', 'swipe', str(x), str(top + (bottom - top) * 4 // 5), str(x), str(top + (bottom - top) // 5), '300')
    find(label, attr, prefix)
    raise AssertionError('Control clipped: ' + label)


def enter(label, value):
    tap(label)

    def focused():
        fields = [n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true']
        assert len(fields) == 1, label
        return fields[0].get('text', '')

    for _ in range(3):
        old = focused()
        if not old:
            break
        # The IME can retain the insertion point after focus. Clear both sides,
        # then observe the actual field before sending replacement text.
        adb('shell', 'for i in ' + ' '.join(str(i) for i in range(len(old) + 2)) + '; do input keyevent 67; input keyevent 112; done')
    assert focused() == '', (label, 'Old text remains', focused())
    adb('shell', 'input', 'text', value.replace(' ', '%s'))
    assert focused() == value, (label, focused())
    back()


def back():
    adb('shell', 'input', 'keyevent', '4')


def capture(name):
    nodes()
    (args.output / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p', raw=True))
    (args.output / f'{name}.xml').write_text(adb('shell', 'cat', '/sdcard/of-composition.xml'))


def state():
    xml = ET.fromstring(adb('shell', 'run-as', 'org.openfactor.app', 'cat', 'shared_prefs/open_factor.xml'))
    return json.loads(next(n.text for n in xml if n.get('name') == 'data'))


def launch():
    adb('shell', 'am', 'force-stop', 'org.openfactor.app')
    adb('shell', 'am', 'start', '-n', 'org.openfactor.app/.MainActivity')


def open_shortcuts():
    tap('Shortcuts', 'content-desc')
    left, top, right, bottom = bounds(find('Drag handle', 'content-desc')[0])
    adb('shell', 'input', 'swipe', str((left+right)//2), str((top+bottom)//2),
        str((left+right)//2), '150', '500')


def create_custom(name):
    enter('Exercise Name', name)
    tap('Trackable Metric 1', 'content-desc')
    tap('Reps')
    tap('Next')
    tap('Skip')
    tap('Next')
    enter('Body Weight Contribution', '0')
    tap('Next')
    tap('Create')


adb('shell', 'svc', 'wifi', 'disable')
adb('shell', 'svc', 'data', 'disable')
launch()
if any(n.get('text') == 'Get Started' for n in nodes()):
    tap('Set up later')
    tap('Set up later', last=True)
find('Dashboard')
before = state()
assert not before.get('active') and not before.get('workoutDraft') and not before.get('programDraft'), 'Use a disposable empty training store'
tap('Shortcuts', 'content-desc')
tap('Empty Workout')
capture('empty-composition')
assert state().get('active') is None, 'Opening Empty Workout must not start timing or create a live session'
find('Start Workout')
assert state()['workoutDraft']['exercises'] == []

tap('Add Exercises', 'content-desc')
enter('Search exercises', 'chin-up')
tap('Chin-Up')
find('Instructions')
back()
find('chin-up')
tap('Chin-Up')
tap('Add Exercise', last=True)
find('1 Exercise')
draft = state()['workoutDraft']
assert len(draft['exercises']) == 1 and state().get('active') is None
capture('populated-composition')

# Custom Cancel and creation return to the requesting composition, with filters intact.
tap('Add Exercises', 'content-desc')
tap('Create custom exercise', 'content-desc')
find('Create Custom Exercise')
back()
find('chin-up')
tap('Create custom exercise', 'content-desc')
create_custom('Synthetic Draft Exercise')
find('Synthetic Draft Exercise')
tap('Add Exercise', last=True)
find('2 Exercises')
draft = state()['workoutDraft']
custom_id = next(e['id'] for e in state()['customExercises'] if e['name'] == 'Synthetic Draft Exercise')
assert draft['exercises'][-1]['exerciseId'] == custom_id and state().get('active') is None
tap('Composition options', 'content-desc')
tap('Rename Workout')
enter('Name', 'Synthetic Composition')
tap('Save')
draft = state()['workoutDraft']
assert draft['name'] == 'Synthetic Composition'
tap('Composition options', 'content-desc')
tap('Re-order')
tap('Move Synthetic Draft Exercise up', 'content-desc')
assert state()['workoutDraft']['exercises'] == list(reversed(draft['exercises']))
draft = state()['workoutDraft']
capture('reordered-composition')

launch()
find('Dashboard')
tap('Shortcuts', 'content-desc')
tap('Empty Workout')
assert state()['workoutDraft'] == draft and state().get('active') is None
find('Synthetic Composition')
before_start = int(time.time() * 1000)
tap('Start Workout')
find('Workout options', 'content-desc')
started = state()['active']
assert state().get('workoutDraft') is None
assert before_start <= started['startedAt'] <= int(time.time() * 1000)
assert started['exercises'] == draft['exercises'] and started['gymId'] == draft['gymId']
assert state().get('history') == before.get('history')

# Custom header Back and system Back both retain the live picker/session.
tap('Add Exercise', 'content-desc')
enter('Search exercises', 'dip')
tap('Create custom exercise', 'content-desc')
tap('Back', 'content-desc')
find('dip')
tap('Create custom exercise', 'content-desc')
back()
find('dip')
tap('Create custom exercise', 'content-desc')
create_custom('Synthetic Session Exercise')
tap('Add Exercise', last=True)
find('Workout options', 'content-desc')
active = state()['active']
session_custom = next(e['id'] for e in state()['customExercises'] if e['name'] == 'Synthetic Session Exercise')
assert active['id'] == started['id'] and active['exercises'][-1]['exerciseId'] == session_custom
capture('contextual-session')
tap('Workout options', 'content-desc')
tap('Minimize Workout')

# Editing a program while a session exists must not change that session.
open_shortcuts()
tap('New Program')
enter('Name', 'Synthetic Context Plan')
tap('Next')
tap('Next')
tap('Add Day', prefix=True)
tap('Add Exercises')
tap('Create custom exercise', 'content-desc')
find('Create Custom Exercise')
back()
find('Add Exercise')
tap('Create custom exercise', 'content-desc')
create_custom('Synthetic Plan Exercise')
capture('program-custom-detail')
tap('Add Exercise', last=True)
plan = state()['programDraft']
plan_custom = next(e['id'] for e in state()['customExercises'] if e['name'] == 'Synthetic Plan Exercise')
assert plan['days'][-1]['exercises'][-1]['exerciseId'] == plan_custom
assert state()['active'] == active, 'Program detail Add changed the live session'
tap('Add Exercises')
enter('Search exercises', 'chin-up')
tap('Chin-Up')
back()
find('chin-up')
tap('Chin-Up')
tap('Add Exercise', last=True)
assert len(state()['programDraft']['days'][-1]['exercises']) == 2
assert state()['active'] == active
capture('contextual-program')
launch()
assert state()['active'] == active and state()['programDraft']['id'] == plan['id']
assert state().get('scaleWeights') == before.get('scaleWeights')
assert state().get('bodyMeasurements') == before.get('bodyMeasurements')
assert 'Active default network: none' in adb('shell', 'dumpsys', 'connectivity')
(args.output / 'apk-sha256.txt').write_text(adb('shell', 'sha256sum', adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')))
(args.output / 'result.json').write_text(json.dumps({
    'status': 'passed',
    'serial': args.serial,
    'composition_has_no_running_session': True,
    'composition_order_and_restart_preserved': True,
    'explicit_start_clock': True,
    'draft_session_and_program_custom_context': True,
    'program_detail_add_preserves_live_session': True,
    'picker_search_back_restoration': True,
    'body_records_unchanged': True,
    'offline': True,
}, indent=2) + '\n')
print((args.output / 'result.json').read_text())
