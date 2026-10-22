#!/usr/bin/env python3
"""Run the installed Open Factor UI flows without a network on a disposable emulator.

The emulator must already have Open Factor installed and setup completed/deferred.
Creates synthetic workouts/programs. Never operates on physical/source devices.
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if not args.serial.startswith('emulator-'):
    raise SystemExit('Use a disposable emulator, never a physical phone')
args.output.mkdir(parents=True, exist_ok=True)


def adb(*command):
    return subprocess.check_output(['adb', '-s', args.serial, *command], timeout=30).decode()


wifi = adb('shell', 'settings', 'get', 'global', 'wifi_on').strip()
mobile = adb('shell', 'settings', 'get', 'global', 'mobile_data').strip()
results = {}
try:
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    for _ in range(20):
        connectivity = adb('shell', 'dumpsys', 'connectivity')
        if 'Active default network: none' in connectivity:
            break
        time.sleep(.5)
    else:
        raise AssertionError('Emulator still has an active default network')
    package = adb('shell', 'dumpsys', 'package', 'org.openfactor.app')
    (args.output / 'installed-package.txt').write_text(package)
    assert 'android.permission.INTERNET' not in package, 'Installed app can access the Internet'
    apk = adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')
    (args.output / 'installed-apk-sha256.txt').write_text(adb('shell', 'sha256sum', apk))
    tools = Path(__file__).resolve().parent
    for name, script, flags in [
        ('workout', 'android_smoke.py', ['--parity-actions', '--media']),
        ('program', 'android_program_smoke.py', []),
        ('onboarding', 'android_onboarding_smoke.py', []),
    ]:
        connectivity = adb('shell', 'dumpsys', 'connectivity')
        (args.output / f'{name}-connectivity.txt').write_text(connectivity)
        assert 'Active default network: none' in connectivity
        command = [sys.executable, str(tools / script), '--serial', args.serial,
                   '--output', str(args.output / name), *flags]
        print('Running', ' '.join(command), flush=True)
        with (args.output / f'{name}.log').open('w') as log:
            subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
        results[name] = json.loads((args.output / name / 'result.json').read_text())
    (args.output / 'result.json').write_text(json.dumps({
        'status': 'passed', 'serial': args.serial,
        'installed_app_has_no_internet_permission': True,
        'all_flows_without_default_network': True, 'flows': results,
        'scope': 'Local Open Factor flows; not a full source-app parity certification',
    }, indent=2) + '\n')
    print((args.output / 'result.json').read_text())
finally:
    if wifi in ('0', '1'):
        adb('shell', 'svc', 'wifi', 'enable' if wifi == '1' else 'disable')
    if mobile in ('0', '1'):
        adb('shell', 'svc', 'data', 'enable' if mobile == '1' else 'disable')
