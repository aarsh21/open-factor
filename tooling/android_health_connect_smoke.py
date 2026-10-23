#!/usr/bin/env python3
"""Inspect optional Health Connect entries on an explicitly leased offline emulator.

Never taps a transfer or permission control, changes grants, or accesses a health
store. Saves a complete private-app archive and restores the original preferences
and font setting. Run only after the native owner allocates the emulator.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import tarfile
import time
import xml.etree.ElementTree as ET

from android_offline_suite import Device, archive_manifest

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--apk-sha256', required=True)
parser.add_argument('--expect-provider', required=True,
                    choices=['available', 'unavailable', 'update-required'])
parser.add_argument('--leased-ui-only', action='store_true', required=True)
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Use an explicitly leased disposable emulator, never a phone')
artifact_root = Path(__file__).resolve().parents[1] / '.amp/in/artifacts'
if not args.output.resolve().is_relative_to(artifact_root):
    raise SystemExit('Keep private backups and captures under .amp/in/artifacts/')
args.output.mkdir(parents=True, exist_ok=True)
package = 'org.openfactor.app'
tag = f'of-health-{time.time_ns()}'
ui_file = f'/sdcard/{tag}.xml'
remote_prefs = f'/data/local/tmp/{tag}.xml'
actions = ['Import weight and scale body fat', 'Read daily steps',
           'Export weight and scale body fat', 'Export finished workouts']


def adb(*command, raw=False):
    result = subprocess.check_output(['adb', '-s', args.serial, *command], timeout=60)
    return result if raw else result.decode()


def nodes():
    for attempt in range(8):
        try:
            dumped = adb('shell', 'uiautomator', 'dump', ui_file)
            if 'dumped to:' not in dumped:
                raise ValueError('No fresh UI tree')
            return ET.fromstring(adb('shell', 'cat', ui_file))
        except (ET.ParseError, ValueError, subprocess.CalledProcessError):
            if attempt == 7:
                raise
            time.sleep(.3)


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds', ''))))


def target(label, scroll=True):
    for _ in range(18):
        root = nodes()
        found = [n for n in root.iter('node') if
                 (n.get('text') == label or n.get('content-desc') == label)
                 and len(bounds(n)) == 4 and bounds(n)[3] - bounds(n)[1] >= 25]
        if found:
            return root, found[-1]
        scrolls = [n for n in root.iter('node') if n.get('scrollable') == 'true']
        if scroll and scrolls:
            left, top, right, bottom = bounds(scrolls[-1])
            # Drag in the empty edge padding, never over a transfer button.
            adb('shell', 'input', 'swipe', str(right-8),
                str(top+(bottom-top)*4//5), str(right-8),
                str(top+(bottom-top)//4), '450')
        else:
            time.sleep(.2)
    capture('failure')
    raise AssertionError(f'Cannot find {label}')


def scroll_to_end():
    previous = None
    for _ in range(8):
        root = nodes()
        signature = ET.tostring(root)
        if signature == previous:
            return
        previous = signature
        scrolls = [n for n in root.iter('node') if n.get('scrollable') == 'true']
        if not scrolls:
            return
        left, top, right, bottom = bounds(scrolls[-1])
        adb('shell', 'input', 'swipe', str(right-8),
            str(top+(bottom-top)*4//5), str(right-8),
            str(top+(bottom-top)//4), '450')
    raise AssertionError('Scroll content did not reach its end')


def tap(label):
    # No test path may accidentally invoke an already-authorized transfer.
    assert label not in actions + ['Review permissions'], label
    _, node = target(label)
    left, top, right, bottom = bounds(node)
    adb('shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2))


def capture(name):
    nodes()
    (args.output / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p', raw=True))
    (args.output / f'{name}.xml').write_text(adb('shell', 'cat', ui_file))


def archive():
    return adb('exec-out', 'run-as', package, 'tar', '-cf', '-', '.', raw=True)


def user_files(contents):
    with tarfile.open(fileobj=io.BytesIO(contents)) as saved:
        return {member.name.removeprefix('./'): hashlib.sha256(saved.extractfile(member).read()).hexdigest()
                for member in saved if member.isfile() and
                member.name.removeprefix('./').split('/')[0] in
                ['shared_prefs', 'files', 'no_backup', 'databases']}


def replace_preferences(contents):
    adb('shell', 'am', 'force-stop', package)
    local = args.output / 'preferences-under-test.xml'
    local.write_bytes(contents)
    adb('push', str(local), remote_prefs)
    adb('shell', f"run-as {package} sh -c 'cat {remote_prefs} > shared_prefs/open_factor.xml'")


def checkpoint(original, onboarding):
    root = ET.fromstring(original)
    value = next(n for n in root if n.get('name') == 'data')
    data = json.loads(value.text)
    data['onboarding'] = {**data.get('onboarding', {}), **onboarding}
    value.text = json.dumps(data)
    return ET.tostring(root, encoding='utf-8', xml_declaration=True)


def launch():
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', f'{package}/.MainActivity')


def inspect_panel(name):
    text = {
        'available': 'Health Connect is available. Access is checked for each action.',
        'unavailable': 'Health Connect is not supported on this device or profile. Local training, body data and backups still work.',
        'update-required': 'Install or update Health Connect, then return here. You can keep using Open Factor without it.',
    }[args.expect_provider]
    target('Optional device-local transfers', scroll=False)
    capture(name + '-top')
    target(text)
    for label in actions:
        root, node = target(label)
        parents = {child: parent for parent in root.iter() for child in parent}
        while node in parents and node.get('class') != 'android.widget.Button' and node.get('clickable') != 'true':
            node = parents[node]
        assert node.get('enabled') == str(args.expect_provider == 'available').lower(), label
    target('No transfer has run.')
    capture(name + '-actions')
    scroll_to_end()
    capture(name + '-footer')
    adb('shell', 'input', 'keyevent', '4')


# Reject an online fixture. Network settings remain untouched throughout this run.
connectivity = adb('shell', 'dumpsys', 'connectivity')
assert 'Active default network: none' in connectivity, 'Allocate an offline emulator'
(args.output / 'connectivity.txt').write_text(connectivity)
apk_paths = adb('shell', 'pm', 'path', package).splitlines()
assert len(apk_paths) == 1, 'Expected the pinned single public APK'
apk_hash = adb('shell', 'sha256sum', apk_paths[0].removeprefix('package:')).split()[0]
assert apk_hash == args.apk_sha256, 'Installed APK does not match the reviewed build'
font = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
adb('shell', 'am', 'force-stop', package)
backup = archive()
(args.output / 'private-app-backup.tar').write_bytes(backup)
baseline_files = user_files(backup)
original = adb('exec-out', 'run-as', package, 'cat', 'shared_prefs/open_factor.xml', raw=True)
(args.output / 'original-preferences.xml').write_bytes(original)
assert baseline_files['shared_prefs/open_factor.xml'] == hashlib.sha256(original).hexdigest()
restored = False
try:
    for scale, name in [('1.0', 'normal'), ('1.3', 'large')]:
        adb('shell', 'settings', 'put', 'system', 'font_scale', scale)
        replace_preferences(checkpoint(original, {'completed': True}))
        launch()
        tap('Settings')
        target('Health Connect')
        capture(f'{name}-settings-entry')
        tap('Health Connect')
        inspect_panel(f'{name}-settings-panel')
        target('Health Connect')  # Dismissal returns to Settings, not onboarding.

        replace_preferences(checkpoint(original, {'step': 2, 'completed': False, 'deferred': False}))
        launch()
        target('Optionally sync your data')
        capture(f'{name}-onboarding-entry')
        tap('Health Connect')
        inspect_panel(f'{name}-onboarding-panel')
        target('Optionally sync your data', scroll=False)
        current = ET.fromstring(adb('shell', 'run-as', package, 'cat', 'shared_prefs/open_factor.xml'))
        data = json.loads(next(n.text for n in current if n.get('name') == 'data'))
        assert data['onboarding']['step'] == 2 and not data['onboarding']['completed']
        original_data = json.loads(next(n.text for n in ET.fromstring(original) if n.get('name') == 'data'))
        assert {k: v for k, v in data.items() if k != 'onboarding'} == {
            k: v for k, v in original_data.items() if k != 'onboarding'}

        adb('shell', 'am', 'start', '-W', '-n', f'{package}/.HealthConnectRationaleActivity',
            '-a', 'androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE')
        target('Health Connect privacy', scroll=False)
        capture(f'{name}-rationale-top')
        target("You can deny or revoke any grant in Android's Health Connect settings. Revoking access does not erase saved body records or prior exports. Open Factor never schedules health transfers and does not delete Health Connect records.")
        scroll_to_end()
        capture(f'{name}-rationale-bottom')
finally:
    try:
        replace_preferences(original)
    finally:
        if font == 'null':
            adb('shell', 'settings', 'delete', 'system', 'font_scale')
        else:
            adb('shell', 'settings', 'put', 'system', 'font_scale', font)
        adb('shell', 'rm', '-f', ui_file, remote_prefs)
    after = archive()
    (args.output / 'private-app-before-restoration.tar').write_bytes(after)
    actual_files = user_files(after)
    changed = sorted(name for name in set(actual_files) | set(baseline_files)
                     if actual_files.get(name) != baseline_files.get(name))
    (args.output / 'private-file-drift.json').write_text(json.dumps(changed, indent=2) + '\n')
    backup_path = args.output / 'private-app-backup.tar'
    Device(args.serial).replace_tree(backup_path, archive_manifest(backup_path), '/data/local/tmp/' + tag + '-restore.tar')
    assert user_files(archive()) == baseline_files, 'Private app files changed; use the complete saved archive for recovery'
    assert set(changed) <= {'files/profileInstalled'}, 'Unexpected user-data change during UI-only inspection'
    assert 'Active default network: none' in adb('shell', 'dumpsys', 'connectivity')
    assert adb('shell', 'settings', 'get', 'system', 'font_scale').strip() == font
    restored = True

result = {'status': 'passed', 'serial': args.serial, 'apk_sha256': apk_hash,
          'provider_state': args.expect_provider, 'font_scales': [1.0, 1.3],
          'settings_entry': True, 'onboarding_entry_and_return': True, 'rationale': True,
          'transfer_and_permission_controls_never_tapped': True,
          'native_grant_denial_and_store_transfers': 'not exercised',
          'private_files_and_font_restored': restored}
(args.output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
