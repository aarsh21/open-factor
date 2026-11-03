"""Exercise recovery on isolated temporary files, never an Android device."""
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import tarfile
import tempfile
import unittest

import android_offline_device as device


class LocalDevice(device.Device):
    def __init__(self, root, sandbox):
        super().__init__('emulator-local-test')
        self.root = root
        self.sandbox = sandbox
        self.commands = []

    def archive(self, path):
        with tarfile.open(path, 'w') as archive:
            archive.add(self.root, arcname='.')
        return device.archive_manifest(path)

    def adb(self, *args, raw=False):
        self.commands.append(args)
        if args[0] == 'push':
            destination = Path(args[2])
            assert destination.resolve().is_relative_to(self.sandbox)
            shutil.copyfile(args[1], destination)
            return ''
        if args[:3] == ('shell', 'rm', '-f'):
            target = Path(args[3])
            assert target.resolve().is_relative_to(self.sandbox)
            target.unlink(missing_ok=True)
            return ''
        assert args[:3] == ('shell', 'run-as', device.PACKAGE), args
        command = shlex.split(' '.join(args[3:]))
        assert command[0] in {'id', 'stat', 'chmod', 'mkdir', 'rmdir', 'tar', 'sh'}, command
        if command[0] == 'sh':
            assert command == ['sh', '-c', 'find . -type f -delete && find . -type l -delete']
        result = subprocess.check_output(command, cwd=self.root, timeout=10)
        return result if raw else result.decode()


class DirectoryRecoveryTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.sandbox = Path(temporary.name)
        self.root = self.sandbox / 'app'
        self.root.mkdir()
        self.evidence = self.sandbox / 'evidence'
        self.evidence.mkdir()
        self.device = LocalDevice(self.root, self.sandbox)
        self.archive = self.evidence / 'baseline.tar'
        self.transfer = str(self.sandbox / 'transfer.tar')

    def baseline(self, mode=0o700):
        directory = self.root / 'private directory'
        directory.mkdir()
        (directory / 'record').write_bytes(b'original data')
        directory.chmod(mode)
        return directory, self.device.archive(self.archive)

    def assert_no_destructive_command(self):
        self.assertFalse(any(args[:4] == ('shell', 'run-as', device.PACKAGE, 'sh')
                             for args in self.device.commands))

    def test_changed_directory_mode_and_file_bytes_are_both_restored(self):
        directory, expected = self.baseline()
        directory.chmod(0o755)
        (directory / 'record').write_bytes(b'changed data')
        self.device.replace_tree(self.archive, expected, self.transfer)
        self.assertEqual(0o700, directory.stat().st_mode & 0o7777)
        self.assertEqual(b'original data', (directory / 'record').read_bytes())
        self.assertEqual(expected, self.device.archive(self.evidence / 'after.tar'))

    def test_restrictive_baseline_modes_are_restored_only_after_extraction(self):
        directory, expected = self.baseline(0o500)
        directory.chmod(0o750)
        (directory / 'record').write_bytes(b'changed data')
        self.device.replace_tree(self.archive, expected, self.transfer)
        self.assertEqual(0o500, directory.stat().st_mode & 0o7777)
        self.assertEqual(b'original data', (directory / 'record').read_bytes())

    def test_missing_directories_are_recreated_with_exact_modes(self):
        directory, expected = self.baseline(0o2750)
        shutil.rmtree(directory)
        self.device.replace_tree(self.archive, expected, self.transfer)
        self.assertEqual(expected, self.device.archive(self.evidence / 'after.tar'))

    def test_changed_ownership_fails_before_deleting_any_file(self):
        directory, expected = self.baseline()
        (directory / 'record').write_bytes(b'preserve current bytes')
        expected['private directory']['uid'] = os.getuid() + 1
        with self.assertRaisesRegex(ValueError, 'directory ownership'):
            self.device.replace_tree(self.archive, expected, self.transfer)
        self.assertEqual(b'preserve current bytes', (directory / 'record').read_bytes())
        self.assert_no_destructive_command()

    def test_unrecreatable_missing_directory_group_fails_before_file_deletion(self):
        directory, expected = self.baseline()
        shutil.rmtree(directory)
        survivor = self.root / 'unrelated'
        survivor.write_bytes(b'preserve current bytes')
        expected['private directory']['gid'] = os.getgid() + 1
        with self.assertRaisesRegex(ValueError, 'recreate directory ownership'):
            self.device.replace_tree(self.archive, expected, self.transfer)
        self.assertEqual(b'preserve current bytes', survivor.read_bytes())
        self.assert_no_destructive_command()

    def test_directory_that_replaced_a_baseline_file_is_removed_before_extraction(self):
        record = self.root / 'record'
        record.write_bytes(b'original file')
        expected = self.device.archive(self.archive)
        record.unlink()
        record.mkdir()
        (record / 'temporary').write_bytes(b'changed tree')
        self.device.replace_tree(self.archive, expected, self.transfer)
        self.assertEqual(b'original file', record.read_bytes())
        self.assertEqual(expected, self.device.archive(self.evidence / 'after.tar'))


if __name__ == '__main__':
    unittest.main()
