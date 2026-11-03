"""ADB and recovery-archive primitives shared by the offline suite and its contract.

This module never imports test cases or mutates a device at import time.
"""
import hashlib
from pathlib import Path
import shlex
import subprocess
import tarfile
import time

PACKAGE = 'org.openfactor.app'
SETTINGS = [('system', 'font_scale'), ('system', 'accelerometer_rotation'),
            ('system', 'user_rotation'), ('secure', 'ui_night_mode'),
            ('global', 'animator_duration_scale'), ('global', 'wifi_on'),
            ('global', 'mobile_data')]
# These are the existing children's file namespaces, not all of shared storage.
EXTERNAL = {
    '/sdcard': ('of-',),
    '/sdcard/Download': ('of-', 'ofphoto-', 'offullbody-', 'ofsheet-', 'open-factor-body-smoke-'),
    '/data/local/tmp': ('of-',),
}


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


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
                      for namespace in dict.fromkeys(namespace for namespace, _ in SETTINGS)}
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
