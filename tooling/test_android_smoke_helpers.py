import importlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tarfile
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import android_offline_contract as contract
import android_offline_device as device
import android_offline_suite as suite
import android_program_smoke as program


class SmokeImportsTest(unittest.TestCase):
    def test_program_and_onboarding_import_without_parsing_arguments_or_touching_a_device(self):
        code = (
            'import sys; sys.path.insert(0, sys.argv[1]); '
            'import android_program_smoke, android_onboarding_smoke; '
            "assert not hasattr(android_program_smoke, 'a')"
        )
        result = subprocess.run([sys.executable, '-I', '-c', code, str(Path(__file__).resolve().parent)],
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('', result.stdout)

    def test_onboarding_reuses_the_program_helpers_instead_of_executing_source_text(self):
        onboarding = importlib.import_module('android_onboarding_smoke')
        for name in ['adb', 'bounds', 'capture', 'configure', 'enter', 'find', 'launch', 'nodes', 'state', 'tap']:
            self.assertIs(getattr(program, name), getattr(onboarding, name))

    def test_configure_preserves_the_cli_arguments_and_validates_the_emulator_serial(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(program, 'a', None, create=True):
            output = Path(directory) / 'evidence'
            with patch.object(sys, 'argv', ['smoke', '--serial', 'emulator-5554', '--output', str(output)]):
                args = program.configure('test smoke')
            self.assertEqual('emulator-5554', args.serial)
            self.assertEqual(output, args.output)
            self.assertIs(args, program.a)
            self.assertTrue(output.is_dir())
            for serial in ['physical-device', 'emulator-not-a-port']:
                with self.subTest(serial=serial), patch.object(sys, 'argv', [
                    'smoke', '--serial', serial, '--output', str(output)
                ]), patch.object(sys, 'stderr', io.StringIO()), self.assertRaises(SystemExit) as error:
                    program.configure()
                self.assertEqual(2, error.exception.code)

    def test_tap_preserves_clip_safety_checks_and_coordinate_calculation(self):
        node = ET.Element('node', bounds='[10,20][110,80]')
        with patch.object(program, 'ensure_visible') as visible, patch.object(program, 'find', return_value=[node]), patch.object(program, 'adb') as adb:
            program.tap('Save')
            visible.assert_called_once_with('Save', 'text', False)
            adb.assert_called_once_with('shell', 'input', 'tap', '60', '50')
        node.set('bounds', '[10,20][110,25]')
        with patch.object(program, 'ensure_visible'), patch.object(program, 'find', return_value=[node]), patch.object(program, 'adb') as adb:
            with self.assertRaisesRegex(AssertionError, 'clipped'):
                program.tap('Save')
            adb.assert_not_called()


class OfflineRecoveryHelpersTest(unittest.TestCase):
    def test_contract_and_suite_share_one_device_implementation_without_a_cycle(self):
        self.assertIs(device.Device, contract.Device)
        self.assertIs(device.Device, suite.Device)
        self.assertIs(device.archive_manifest, suite.archive_manifest)
        self.assertIs(device.digest, suite.digest)

    def test_fixture_and_archive_manifest_preserve_recovery_owner_and_metadata(self):
        original = {
            'shared_prefs': dict(mode=0o700, uid=10123, gid=10123, type='5'),
            'shared_prefs/open_factor.xml': dict(mode=0o600, uid=10123, gid=10123, type='0'),
        }
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'fixture.tar'
            contract.fixture(path, original)
            manifest = device.archive_manifest(path)
            with tarfile.open(path) as archive:
                root = ET.fromstring(archive.extractfile('shared_prefs/open_factor.xml').read())
                state = json.loads(root.find('string').text)
            self.assertEqual('Offline Suite', state['preferences']['displayName'])
            self.assertTrue(state['onboarding']['completed'])
            self.assertEqual(10123, manifest['shared_prefs/open_factor.xml']['uid'])
            self.assertEqual(0o600, manifest['shared_prefs/open_factor.xml']['mode'])
            self.assertEqual('5', manifest['no_backup/progress-photos']['type'])
            contract.fixture(path, original, onboarding=True)
            with tarfile.open(path) as archive:
                root = ET.fromstring(archive.extractfile('shared_prefs/open_factor.xml').read())
                state = json.loads(root.find('string').text)
            self.assertFalse(state['onboarding']['completed'])
            self.assertTrue(state['onboarding']['deferred'])

    def test_archive_manifest_rejects_unsafe_duplicate_and_special_members(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'archive.tar'
            for names, member_type in [(['../escape'], tarfile.REGTYPE),
                                       (['/absolute'], tarfile.REGTYPE),
                                       (['same', 'same'], tarfile.REGTYPE),
                                       (['fifo'], tarfile.FIFOTYPE)]:
                with self.subTest(names=names, member_type=member_type):
                    with tarfile.open(path, 'w') as archive:
                        for name in names:
                            entry = tarfile.TarInfo(name)
                            entry.type = member_type
                            archive.addfile(entry, io.BytesIO() if entry.isfile() else None)
                    with self.assertRaises(ValueError):
                        device.archive_manifest(path)

    def test_settings_reads_each_namespace_once_and_preserves_null_presence(self):
        values = {
            'system': 'font_scale=1.0\naccelerometer_rotation=0\nuser_rotation=null\n',
            'secure': 'ui_night_mode=2\n',
            'global': 'wifi_on=1\nmobile_data=0\n',
        }
        instance = device.Device('emulator-5554')
        with patch.object(instance, 'adb', side_effect=lambda *args: values[args[-1]]) as adb:
            settings = instance.settings()
        self.assertEqual(3, adb.call_count)
        self.assertEqual(['system', 'secure', 'global'], [call.args[-1] for call in adb.call_args_list])
        self.assertEqual('null', settings['system/user_rotation'])
        self.assertIsNone(settings['global/animator_duration_scale'])
        self.assertEqual('1', settings['global/wifi_on'])
        self.assertEqual(len(device.SETTINGS), len(settings))

    def test_device_adb_keeps_the_serial_timeout_and_raw_bytes_contract(self):
        result = SimpleNamespace(stdout=b'output\n')
        with patch.object(device.subprocess, 'run', return_value=result) as run:
            instance = device.Device('emulator-5554')
            self.assertEqual('output\n', instance.adb('shell', 'command'))
            self.assertEqual(b'output\n', instance.adb('exec-out', 'command', raw=True))
        self.assertEqual(['adb', '-s', 'emulator-5554', 'exec-out', 'command'], run.call_args.args[0])
        self.assertEqual(120, run.call_args.kwargs['timeout'])
        self.assertTrue(run.call_args.kwargs['check'])


if __name__ == '__main__':
    unittest.main()
