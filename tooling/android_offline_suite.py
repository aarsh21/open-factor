#!/usr/bin/env python3
"""Run independent offline UI cases on ONE already-installed, explicitly pinned APK.

Requires an exclusive disposable-emulator lease. Never installs, builds, invokes
pm clear, changes Health grants or operates a provider. Originals and evidence remain
in a fresh locally excluded .amp/in/artifacts directory (mode 0700). Every case
uses synthetic data; finally restores the stopped app's complete private tree,
owned external files and exact settings. Interrupted recovery retains its archive.
This is representative local acceptance, not source-app parity certification.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import signal
import subprocess
import sys
import tarfile
import time
import traceback

from android_offline_contract import UI, fixture

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'org.openfactor.app'
SETTINGS = [('system', 'font_scale'), ('system', 'accelerometer_rotation'),
            ('system', 'user_rotation'), ('secure', 'ui_night_mode'),
            ('global', 'animator_duration_scale'), ('global', 'wifi_on'),
            ('global', 'mobile_data')]
CASES = {
    'contract': ('android_offline_contract.py', []),
    'workout-media': ('android_smoke.py', ['--parity-actions', '--media']),
    'program': ('android_program_smoke.py', []),
    'onboarding': ('android_onboarding_smoke.py', []),
    'composition': ('android_composition_smoke.py', []),
    'custom': ('android_custom_exercise_smoke.py', ['--replace-synthetic-store']),
    'library': ('android_library_smoke.py', ['--replace-synthetic-store']),
    'metrics': ('android_metric_logging_smoke.py', ['--replace-synthetic-store']),
    'body': ('android_body_data_smoke.py', []),
    'charts': ('android_body_charts_smoke.py', []),
    'full-body': ('android_full_body_smoke.py', ['--illustrated']),
    'photos': ('android_progress_photos_smoke.py', []),
    'photo-sheet': ('android_photo_sheet_smoke.py', []),
    'portability': ('android_data_portability_smoke.py', []),
    'health-ui': ('android_health_connect_smoke.py', []),
}
# These are the existing children's file namespaces, not all of shared storage.
EXTERNAL = {
    '/sdcard': ('of-',),
    '/sdcard/Download': ('of-', 'ofphoto-', 'offullbody-', 'ofsheet-', 'open-factor-body-smoke-'),
    '/data/local/tmp': ('of-',),
}


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def save(path, data):
    temporary = path.with_suffix('.tmp')
    temporary.write_text(json.dumps(data, indent=2) + '\n')
    temporary.replace(path)


def archive_manifest(path):
    """Compare bytes, types, modes and membership, not volatile atime/mtime."""
    result = {}
    with tarfile.open(path) as archive:
        for member in archive:
            name = member.name.removeprefix('./').rstrip('/')
            if name in ('', '.'):
                continue
            if name.startswith('/') or '..' in Path(name).parts or name in result:
                raise ValueError('Unsafe or duplicate private archive path')
            entry = {'mode': member.mode, 'uid': member.uid, 'gid': member.gid,
                     'type': member.type.decode()}
            if member.isfile():
                entry['sha256'] = hashlib.file_digest(archive.extractfile(member), 'sha256').hexdigest()
            elif member.issym():
                entry['target'] = member.linkname
            elif not member.isdir():
                raise ValueError('Unsupported private archive member: ' + name)
            result[name] = entry
    return result


class Device:
    def __init__(self, serial):
        self.serial = serial

    def adb(self, *args, raw=False):
        run = subprocess.run(['adb', '-s', self.serial, *args], stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, check=True, timeout=120)
        return run.stdout if raw else run.stdout.decode()

    def stop(self):
        self.adb('shell', 'am', 'force-stop', PACKAGE)

    def archive(self, path):
        self.stop()
        with path.open('wb') as stream:
            subprocess.run(['adb', '-s', self.serial, 'exec-out', 'run-as', PACKAGE,
                            'tar', '-cf', '-', '.'], stdout=stream, check=True, timeout=180)
        return archive_manifest(path)

    def apk_hash(self, package):
        run = subprocess.run(['adb', '-s', self.serial, 'shell', 'pm', 'path', package],
                             capture_output=True, text=True, timeout=30)
        paths = run.stdout.splitlines()
        if not paths and run.returncode in (0, 1):
            return 'absent', None
        if run.returncode or len(paths) != 1 or not paths[0].startswith('package:'):
            raise AssertionError('Expected one installed APK for ' + package)
        path = paths[0].removeprefix('package:')
        return self.adb('shell', 'sha256sum', path).split()[0], path

    def offline(self):
        for _ in range(30):
            text = self.adb('shell', 'dumpsys', 'connectivity')
            if 'Active default network: none' in text:
                return text
            time.sleep(.5)
        raise AssertionError('An active default network remains')

    def settings(self):
        # `settings get` prints "null" both for absence and a literal null value.
        # Preserve presence independently rather than deleting a present value.
        namespaces = {namespace: dict(line.split('=', 1) for line in
                      self.adb('shell', 'settings', 'list', namespace).splitlines() if '=' in line)
                      for namespace, _ in SETTINGS}
        return {f'{namespace}/{key}': namespaces[namespace].get(key) for namespace, key in SETTINGS}

    def owned_external(self, include_unrelated_downloads=False):
        paths = []
        for directory, prefixes in EXTERNAL.items():
            for name in self.adb('shell', 'ls', '-1', directory).splitlines():
                if name.startswith(prefixes):
                    path = directory + '/' + name
                    self.adb('shell', 'test', '-f', shlex.quote(path))  # Never recursively delete external directories.
                    paths.append(path)
        if include_unrelated_downloads:
            # Protect unrelated SAF documents, but never delete new unowned files.
            paths += self.adb('exec-out', 'find', '/sdcard/Download', '-type', 'f', '-print0').rstrip('\0').split('\0')
        return sorted(set(paths) - {''})

    def private_write(self, local, remote, destination):
        self.adb('push', str(local), remote)
        try:
            self.adb('shell', 'run-as', PACKAGE, 'cp', remote, destination)
            assert self.adb('shell', 'run-as', PACKAGE, 'sha256sum', destination).split()[0] == digest(local)
        finally:
            self.adb('shell', 'rm', '-f', remote)

    def replace_tree(self, archive, expected, transfer):
        # Android cache directories have a distinct cache GID and setgid bit.
        # Even extracting directory headers over existing directories loses that
        # metadata under run-as. Preserve inodes and extract ONLY files/links.
        check = archive.parent / 'verification.tar'
        current = self.archive(check)
        members = archive.parent / 'file-members.tar'
        with tarfile.open(archive) as saved, tarfile.open(members, 'w') as output:
            for member in saved:
                if not member.isdir():
                    output.addfile(member, saved.extractfile(member) if member.isfile() else None)
        self.adb('push', str(members), transfer)
        try:
            # find does not follow symlinks. No original directory is recreated.
            self.adb('shell', 'run-as', PACKAGE, 'sh', '-c',
                     shlex.quote('find . -type f -delete && find . -type l -delete'))
            for name in sorted(current, key=lambda p: len(Path(p).parts), reverse=True):
                if current[name]['type'] == '5' and name not in expected:
                    self.adb('shell', 'run-as', PACKAGE, 'rmdir', shlex.quote(name))
            for name in sorted(expected, key=lambda p: len(Path(p).parts)):
                entry = expected[name]
                if entry['type'] == '5' and name not in current:
                    self.adb('shell', 'run-as', PACKAGE, 'mkdir', '-m', oct(entry['mode'])[2:], shlex.quote(name))
            self.adb('shell', 'run-as', PACKAGE, 'tar', '-xof', transfer)
            assert self.archive(check) == expected, 'Private tree bytes/membership/modes/ownership differ'
        finally:
            self.adb('shell', 'rm', '-f', transfer)
            members.unlink(missing_ok=True)
            check.unlink(missing_ok=True)


def run_child(command, log, timeout):
    with log.open('w') as stream:
        child = subprocess.Popen(command, stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            return child.wait(timeout=timeout)
        finally:
            # A child can leave screenrecord alive on failure. Reap the entire
            # owned group before touching recovery, including on SIGINT/SIGTERM.
            try:
                os.killpg(child.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                child.wait(timeout=5)
            except subprocess.TimeoutExpired:
                os.killpg(child.pid, signal.SIGKILL)
                child.wait()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--leased-emulator', action='store_true', required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--apk-sha256', required=True)
    parser.add_argument('--test-apk-sha256', required=True, help='Exact installed test APK hash, or absent')
    parser.add_argument('--source-revision', required=True, help='Exact local HEAD used for the run; not a build attestation')
    parser.add_argument('--expect-provider', choices=['available', 'unavailable', 'update-required'])
    parser.add_argument('--cases', nargs='+', choices=[*CASES, 'recovery-probe', 'recovery-check'], default=list(CASES))
    parser.add_argument('--case-timeout', type=int, default=2400)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('Use an explicitly leased disposable emulator, never a phone')
    if sys.flags.optimize or os.environ.get('PYTHONOPTIMIZE'):
        parser.error('Assertions must remain enabled in this suite and its children')
    if len(set(args.cases)) != len(args.cases) or args.case_timeout <= 0:
        parser.error('Cases must be unique and timeout positive')
    if 'health-ui' in args.cases and args.expect_provider is None:
        parser.error('--expect-provider is required for health-ui; no provider action is inferred')
    output = args.output.resolve()
    if not output.is_relative_to(ROOT / '.amp/in/artifacts'):
        parser.error('Private evidence must remain under this checkout .amp/in/artifacts/')
    excluded = subprocess.run(['git', 'check-ignore', '-q', str(output / 'private-baseline.tar')], cwd=ROOT)
    if excluded.returncode:
        parser.error('Locally exclude /.amp/in/ before running')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    if head != args.source_revision:
        parser.error('Source revision does not equal this checkout HEAD')
    os.umask(0o077)
    output.mkdir(parents=True, exist_ok=False)
    device = Device(args.serial)
    ui = UI(device.adb, output)
    transfer = '/data/local/tmp/of-suite-' + str(time.time_ns())
    result = {'status': 'failed', 'serial': args.serial, 'cases': {}, 'restoration': 'not started',
              'scope': 'Representative local UI flows, not full source parity; source pin is not a build attestation',
              'source_revision': head, 'apk_sha256': args.apk_sha256,
              'test_apk_sha256': args.test_apk_sha256, 'selected_cases': args.cases}
    save(output / 'result.json', result)
    paths = subprocess.check_output(['git', 'ls-files', '-z', 'openfactor', 'tooling'], cwd=ROOT).decode().split('\0')
    paths += [str(p.relative_to(ROOT)) for p in (ROOT / 'tooling').glob('android_offline*.py')]
    source = {p: digest(ROOT / p) for p in sorted(set(paths)) if p and (ROOT / p).is_file()}
    save(output / 'source-files.json', source)
    result['source_manifest_sha256'] = digest(output / 'source-files.json')
    expected_apks = {PACKAGE: args.apk_sha256, PACKAGE + '.test': args.test_apk_sha256}

    def pins():
        for package, expected in expected_apks.items():
            assert device.apk_hash(package)[0] == expected, f'Installed artifact drift: {package}'
        assert all((ROOT / p).is_file() and digest(ROOT / p) == value for p, value in source.items()), 'Source/test files changed during the run'

    # No mutation until exact installed artifacts and recovery inputs are saved.
    pins()
    package_dump = device.adb('shell', 'dumpsys', 'package', PACKAGE)
    assert 'android.permission.INTERNET' not in package_dump
    (output / 'installed-package.txt').write_text(package_dump)
    for package, expected in expected_apks.items():
        _, path = device.apk_hash(package)
        if path:
            local = output / (package + '.apk')
            device.adb('pull', path, str(local))
            assert digest(local) == expected
    settings = device.settings()
    external = {}
    external_dir = output / 'private-external'
    external_dir.mkdir()
    owned_before = device.owned_external()
    for index, path in enumerate(device.owned_external(include_unrelated_downloads=True)):
        local = external_dir / str(index)
        device.adb('pull', path, str(local))
        external[path] = {'file': str(local.relative_to(output)), 'sha256': digest(local)}
    baseline = output / 'private-baseline.tar'
    original = device.archive(baseline)
    save(output / 'recovery.json', {'private_archive_sha256': digest(baseline), 'private_files': original,
                                  'settings': settings, 'external': external, 'owned_external': owned_before,
                                  'apks': expected_apks})

    def interrupted(signum, frame):
        raise InterruptedError(f'Signal {signum}; recovering baseline')

    for signum in (signal.SIGINT, signal.SIGTERM):
        signal.signal(signum, interrupted)
    try:
        device.adb('shell', 'svc', 'wifi', 'disable')
        device.adb('shell', 'svc', 'data', 'disable')
        device.offline()
        for name in args.cases:
            case = output / name
            case.mkdir()
            record = {'status': 'failed'}
            result['cases'][name] = record
            save(output / 'result.json', result)
            prepared = False
            try:
                pins()
                device.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
                # Independent minimal fixture; never pass incoming private data to children.
                synthetic = case / 'fixture.tar'
                fixture(synthetic, original, onboarding=name == 'onboarding')
                device.replace_tree(synthetic, archive_manifest(synthetic), transfer)
                (case / 'connectivity-before.txt').write_text(device.offline())
                prepared = True
                if name == 'recovery-probe':
                    # Intentionally fail after actual private mutation. This must
                    # produce a failed suite with a separately passed restoration.
                    device.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
                    device.adb('push', str(synthetic), '/sdcard/of-suite-planted-output.tar')
                    raise AssertionError('Planted failure after synthetic private-tree replacement')
                ui.output = case
                ui.canonicalize()
                save(case / 'state-before.json', ui.state())
                if name == 'recovery-check':
                    assert ui.state()['preferences']['displayName'] == 'Offline Suite'
                    before_restart = ui.state()
                    ui.launch()
                    ui.target('Dashboard')
                    assert ui.state() == before_restart
                    ui.capture('synthetic-restarted-dashboard')
                    record.update(status='passed', synthetic_fixture_restart_equal=True)
                    continue
                script, flags = CASES[name]
                command = [sys.executable, str(ROOT / 'tooling' / script), '--serial', args.serial,
                           '--output', str(case), *flags]
                if name == 'health-ui':
                    command += ['--apk-sha256', args.apk_sha256, '--expect-provider', args.expect_provider,
                                '--leased-ui-only']
                record['command'] = command
                print('Running', name, flush=True)
                record['exit_code'] = run_child(command, case / 'run.log', args.case_timeout)
                child_result = case / 'result.json'
                record['observations'] = json.loads(child_result.read_text()) if child_result.exists() else None
                assert record['exit_code'] == 0, 'Child failed; see run.log'
                assert record['observations'] and record['observations'].get('status') == 'passed', 'Missing passing child result'
                before_restart = ui.state()
                save(case / 'state-after.json', before_restart)
                ui.launch()
                ui.nodes()
                assert ui.state() == before_restart, 'Persisted data changed across restart'
                save(case / 'state-restarted.json', ui.state())
                record['restart_state_equal'] = True
                record['status'] = 'passed'
            except Exception as error:
                record['status'] = 'failed'
                record['error'] = f'{type(error).__name__}: {error}'
                (case / 'exception.txt').write_text(traceback.format_exc())
                if not prepared or isinstance(error, InterruptedError):
                    raise
                if name != 'recovery-probe':
                    try:
                        save(case / 'state-failed.json', ui.state())
                        ui.capture('failed-ui')
                    except Exception as capture_error:
                        record['capture_error'] = str(capture_error)
            finally:
                # A child cannot claim green after changing APK/network/source.
                try:
                    pins()
                    (case / 'connectivity-after.txt').write_text(device.offline())
                except Exception as error:
                    record['status'] = 'failed'
                    record['boundary_error'] = str(error)
                    raise
                finally:
                    save(output / 'result.json', result)
    except BaseException as error:
        result['error'] = f'{type(error).__name__}: {error}'
    finally:
        # Keep the first recovery attempt synchronous even if interrupted again.
        for signum in (signal.SIGINT, signal.SIGTERM):
            signal.signal(signum, signal.SIG_IGN)
        recovery_errors = []
        try:
            assert digest(baseline) == json.loads((output / 'recovery.json').read_text())['private_archive_sha256']
            device.replace_tree(baseline, original, transfer)
            result['private_tree_restored'] = True
        except Exception as error:
            recovery_errors.append('private tree: ' + str(error))
        try:
            for path in set(device.owned_external()) - set(external):
                device.adb('shell', 'rm', '-f', shlex.quote(path))
            for path, receipt in external.items():
                local = output / receipt['file']
                assert digest(local) == receipt['sha256']
                device.adb('push', str(local), path)
                assert device.adb('shell', 'sha256sum', shlex.quote(path)).split()[0] == receipt['sha256']
            assert device.owned_external() == owned_before
            assert device.owned_external(include_unrelated_downloads=True) == sorted(external), 'Unrecognized new external files retained for manual review'
            result['owned_external_restored'] = True
        except Exception as error:
            recovery_errors.append('external files: ' + str(error))
        try:
            for key, value in settings.items():
                namespace, key = key.split('/')
                if key in ('wifi_on', 'mobile_data'):
                    device.adb('shell', 'svc', 'wifi' if key == 'wifi_on' else 'data',
                               'enable' if value == '1' else 'disable')
                if value is None:
                    device.adb('shell', 'settings', 'delete', namespace, key)
                else:
                    device.adb('shell', 'settings', 'put', namespace, key, value)
            for _ in range(20):
                if device.settings() == settings:
                    break
                time.sleep(.5)
            assert device.settings() == settings, 'System settings differ'
            result['settings_restored'] = True
        except Exception as error:
            recovery_errors.append('settings: ' + str(error))
        try:
            for package, expected in expected_apks.items():
                assert device.apk_hash(package)[0] == expected, 'APK changed; manual signature-compatible recovery required'
            device.stop()
            running = device.adb('shell', 'ps', '-A')
            assert not any(line.split()[-1] == PACKAGE for line in running.splitlines() if line.split())
            result['app_stopped_and_apks_unchanged'] = True
        except Exception as error:
            recovery_errors.append('artifacts/process: ' + str(error))
        result['restoration'] = 'failed' if recovery_errors else 'passed'
        result['recovery_errors'] = recovery_errors
        result['status'] = ('passed' if not recovery_errors and not result.get('error')
                            and len(result['cases']) == len(args.cases)
                            and all(c['status'] == 'passed' for c in result['cases'].values()) else 'failed')
        save(output / 'result.json', result)
    print(json.dumps(result, indent=2))
    return 0 if result['status'] == 'passed' else 1


if __name__ == '__main__':
    sys.exit(main())
