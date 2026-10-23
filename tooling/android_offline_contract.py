#!/usr/bin/env python3
"""Suite-only synthetic settings/Health-provenance SAF contract. No provider actions.

Run through android_offline_suite.py, which owns the lease, artifact pins and exact
original recovery. This child deliberately leaves its synthetic fixture behind.
"""
import argparse
import base64
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import tarfile
import time
import xml.etree.ElementTree as ET
import zlib

PACKAGE = 'org.openfactor.app'


def png(color):
    def chunk(kind, payload):
        return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', zlib.crc32(kind + payload))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', 31, 47, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress((b'\0' + bytes(color) * 31) * 47)) + chunk(b'IEND', b''))


def xml(data):
    root = ET.Element('map')
    ET.SubElement(root, 'string', name='data').text = json.dumps(data)
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def fixture(path, original, onboarding=False):
    # Blank body/photo collections satisfy older children's explicit prerequisites.
    # Asymmetric unrelated-data coverage belongs to the contract below.
    data = {'schemaVersion': 1, 'onboarding': {'completed': not onboarding, 'deferred': onboarding},
            'preferences': {'displayName': 'Offline Suite', 'pounds': False}}
    owner = original['shared_prefs/open_factor.xml']
    directories = {name: value for name, value in original.items() if value['type'] == '5'}
    for name in ['shared_prefs', 'no_backup', 'no_backup/progress-photos']:
        directories.setdefault(name, {**owner, 'mode': 0o700})
    with tarfile.open(path, 'w') as archive:
        for name, value in directories.items():
            entry = tarfile.TarInfo(name)
            entry.type = tarfile.DIRTYPE
            entry.mode, entry.uid, entry.gid = value['mode'], value['uid'], value['gid']
            archive.addfile(entry)
        payload = xml(data)
        entry = tarfile.TarInfo('shared_prefs/open_factor.xml')
        entry.mode = 0o600
        entry.uid, entry.gid = owner['uid'], owner['gid']
        entry.size = len(payload)
        archive.addfile(entry, io.BytesIO(payload))


class UI:
    def __init__(self, adb, output):
        self.adb, self.output = adb, output
        self.tree = '/sdcard/of-suite-ui.xml'

    def nodes(self):
        for _ in range(8):
            dumped = self.adb('shell', 'uiautomator', 'dump', self.tree)
            if 'dumped to:' in dumped:
                return list(ET.fromstring(self.adb('shell', 'cat', self.tree)).iter('node'))
            time.sleep(.3)
        raise AssertionError('No fresh UI hierarchy')

    @staticmethod
    def bounds(node):
        return tuple(map(int, re.findall(r'\d+', node.get('bounds', ''))))

    def target(self, label, attr='text', last=False):
        for _ in range(24):
            tree = self.nodes()
            found = [n for n in tree if n.get(attr, '').casefold() == label.casefold()
                     and len(self.bounds(n)) == 4 and self.bounds(n)[3] - self.bounds(n)[1] >= 25]
            if found:
                return found[-1 if last else 0]
            scrolls = [n for n in tree if n.get('scrollable') == 'true']
            if scrolls:
                left, top, right, bottom = self.bounds(scrolls[-1])
                self.adb('shell', 'input', 'swipe', str(right-8), str(top+(bottom-top)*3//4),
                         str(right-8), str(top+(bottom-top)//3), '400')
            else:
                time.sleep(.2)
        raise AssertionError('Cannot find ' + label)

    def tap(self, label, attr='text', last=False):
        node = self.target(label, attr, last)
        left, top, right, bottom = self.bounds(node)
        self.adb('shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2))

    def capture(self, name):
        self.nodes()
        (self.output / (name+'.xml')).write_text(self.adb('shell', 'cat', self.tree))
        (self.output / (name+'.png')).write_bytes(self.adb('exec-out', 'screencap', '-p', raw=True))

    def state(self):
        root = ET.fromstring(self.adb('exec-out', 'run-as', PACKAGE, 'cat', 'shared_prefs/open_factor.xml'))
        return json.loads(next(n.text for n in root if n.get('name') == 'data'))

    def launch(self):
        self.adb('shell', 'am', 'force-stop', PACKAGE)
        self.adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', PACKAGE+'/.MainActivity')

    def settings(self):
        self.launch()
        self.tap('Settings', last=True)

    def canonicalize(self):
        self.settings()
        self.tap('Units')
        self.tap('Kilograms (kg)')
        self.launch()
        self.target('Dashboard')

    def choose(self, name):
        self.tap('Show roots', 'content-desc')
        self.tap('Downloads', last=True)
        self.tap(name)

    def save_as(self, name):
        field = next(n for n in self.nodes() if n.get('class') == 'android.widget.EditText')
        left, top, right, bottom = self.bounds(field)
        self.adb('shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2))
        self.adb('shell', 'input', 'keyevent', '123')
        for _ in range(len(field.get('text', ''))+1):
            self.adb('shell', 'input', 'keyevent', '67')
        self.adb('shell', 'input', 'text', name)
        focused = [n for n in self.nodes() if n.get('class') == 'android.widget.EditText'
                   and n.get('focused') == 'true']
        assert len(focused) == 1 and focused[0].get('text') == name
        self.adb('shell', 'input', 'keyevent', '4')
        self.tap('Save', last=True)


def contract(device, ui):
    # No incoming private records are read. Use a different value on both sides of
    # every retention assertion, including zero vs absent body fat and provenance.
    data = ui.state()
    photos = [png((139, 49, 49)), png((43, 112, 86))]
    assets = {hashlib.sha256(p).hexdigest(): p for p in photos}
    data.update(
        scaleWeights=[{'id': 'imported', 'date': '2026-01-07', 'weightKg': 73.125,
                       'bodyFatPercent': 0.0, 'healthConnectOrigin': 'org.example.synthetic.health'},
                      {'id': 'local', 'date': '2026-01-09', 'weightKg': 91.75}],
        bodyMeasurements=[{'id': 'asymmetric', 'date': '2026-01-08',
                           'centimeters': {'leftBicep': 31.125, 'rightBicep': 39.75}, 'visualBodyFatPercent': 0.0}],
        progressPhotos=[{'id': 'unrelated-photo', 'date': '2026-01-06',
                         'photos': dict(zip(['front', 'back'], assets))}],
        history=[{'id': 'retained-session', 'name': 'Unrelated saved workout', 'startedAt': 1000,
                  'finishedAt': 73000, 'exercises': [], 'notes': 'Keep these unrelated notes'}],
        programs=[{'id': 'retained-program', 'name': 'Unrelated plan', 'cycleCount': 3,
                   'color': 4294913866, 'days': [{'id': 'rest', 'name': 'Rest', 'exercises': []}]}],
        activeProgramId='retained-program')
    toggles = [('Reps in reserve', 'showRir'), ('Exercise Auto-Next', 'exerciseAutoNext'),
               ('Keep Alive', 'keepAlive'), ('Workout Timer', 'showWorkoutTimer'),
               ('Bodyweight Contribution', 'showBodyweightContribution')]
    initial = dict(zip([key for _, key in toggles], [False, True, False, True, False]))
    data['preferences'].update(initial, restSeconds=137, autoRest=False, distanceImperial=True,
                               restAfterLastWarmupSet=False, restBetweenExercises=True,
                               supersetAutoNext=False, restBetweenSidesPercent=37)
    device.stop()
    local = ui.output / 'synthetic-preferences.xml'
    local.write_bytes(xml(data))
    device.private_write(local, '/data/local/tmp/of-suite-contract-transfer', 'shared_prefs/open_factor.xml')
    for ref, payload in assets.items():
        local = ui.output / ref
        local.write_bytes(payload)
        device.private_write(local, '/data/local/tmp/of-suite-contract-transfer', 'no_backup/progress-photos/'+ref)
    ui.canonicalize()
    before = ui.state()
    # This independently expected input catches a codec that silently drops fields
    # during the normalization above, rather than accepting a lossy roundtrip.
    assert before['scaleWeights'] == data['scaleWeights']
    assert before['bodyMeasurements'] == data['bodyMeasurements']
    assert before['progressPhotos'] == data['progressPhotos']
    assert all(before['preferences'][key] == value for key, value in initial.items())
    assert before['preferences']['supersetAutoNext'] is False
    assert type(before['preferences']['restBetweenSidesPercent']) is int and before['preferences']['restBetweenSidesPercent'] == 37
    ui.settings()
    ui.tap('Workout')  # First match is the Settings row, not the bottom nav tab.
    for label, key in toggles:
        ui.tap(label)
        current = ui.state()
        assert current['preferences'][key] is (not initial[key]), key
        assert {k: v for k, v in current.items() if k != 'preferences'} == {
            k: v for k, v in before.items() if k != 'preferences'}, 'Unrelated data changed'
    ui.capture('workout-settings')
    ui.tap('Done')
    expected = ui.state()
    expected_preferences = {**before['preferences'], **{key: not value for key, value in initial.items()}}
    assert expected['preferences'] == expected_preferences
    assert expected_preferences['restAfterLastWarmupSet'] is False
    assert expected_preferences['restBetweenExercises'] is True
    ui.launch()
    ui.target('Dashboard')
    assert ui.state() == expected
    tag = 'of-suite-' + str(time.time_ns())
    ui.settings()
    ui.tap('Data Export')
    ui.tap('Granular Export')
    ui.tap('Workout Settings')
    ui.tap('Export')
    ui.target('Save')
    scoped_name = tag + '-workout-settings.json'
    ui.save_as(scoped_name)
    ui.target('Data Export')
    scoped = json.loads(device.adb('shell', 'cat', '/sdcard/Download/'+scoped_name))
    (ui.output / 'scoped-workout-settings.json').write_text(json.dumps(scoped, indent=2)+'\n')
    assert 'schemaVersion' not in scoped
    assert scoped['data'] == {'workoutSettings': {
        'pounds': False, 'restSeconds': 137, 'autoRest': False, 'showRir': True,
        'exerciseAutoNext': False, 'keepAlive': True, 'showWorkoutTimer': False,
        'showBodyweightContribution': True, 'distanceImperial': True,
        'restAfterLastWarmupSet': False, 'restBetweenExercises': True,
        'supersetAutoNext': False, 'restBetweenSidesPercent': 37}}
    assert scoped['data']['workoutSettings']['supersetAutoNext'] is False
    assert type(scoped['data']['workoutSettings']['restBetweenSidesPercent']) is int
    assert ui.state() == expected
    ui.settings()
    ui.tap('Export Backup')
    ui.target('Save')
    ui.capture('native-save')
    name = tag + '-complete.json'
    ui.save_as(name)
    ui.target('Export Backup')
    exported = json.loads(device.adb('shell', 'cat', '/sdcard/Download/'+name))
    (ui.output / 'exported.json').write_text(json.dumps(exported, indent=2)+'\n')
    assert {k: v for k, v in exported.items() if k != 'photoAssets'} == expected
    assert {ref: base64.b64decode(value) for ref, value in exported['photoAssets'].items()} == assets
    empty = ui.output / (tag+'-empty.json')
    empty.write_text(json.dumps({'schemaVersion': 1, 'onboarding': {'completed': True}}))
    device.adb('push', str(empty), '/sdcard/Download/'+empty.name)
    ui.tap('Restore Backup')
    ui.choose(empty.name)
    ui.target('Restore Backup?')
    ui.tap('Restore')
    ui.target('Restore Backup')
    assert not any(ui.state()[key] for key in ['scaleWeights', 'bodyMeasurements', 'progressPhotos', 'history', 'programs'])
    assert not device.adb('shell', 'run-as', PACKAGE, 'ls', 'no_backup/progress-photos').split()
    ui.tap('Restore Backup')
    ui.choose(name)
    ui.target('Restore Backup?')
    ui.capture('native-restore')
    ui.tap('Restore')
    ui.target('Restore Backup')
    ui.launch()
    ui.target('Dashboard')
    assert ui.state() == expected
    for ref, payload in assets.items():
        assert device.adb('exec-out', 'run-as', PACKAGE, 'cat', 'no_backup/progress-photos/'+ref, raw=True) == payload
    ui.capture('restored-dashboard')
    return {'status': 'passed', 'workout_setting_toggles_and_restart': True,
            'unrelated_asymmetric_records_retained': True, 'opposite_rest_boundary_preferences': True,
            'scoped_workout_settings_exact_fields_and_values': True,
            'health_provenance_and_local_absence_roundtrip': True, 'native_saf_empty_restore_restart': True,
            'private_photo_bytes_roundtrip': True, 'provider_actions': 'none'}


if __name__ == '__main__':
    from android_offline_suite import Device
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    # The aggregate creates this file only after safeguarding the baseline.
    assert (args.output.parent / 'recovery.json').is_file(), 'Run through android_offline_suite.py'
    device = Device(args.serial)
    result = contract(device, UI(device.adb, args.output))
    (args.output / 'result.json').write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))
