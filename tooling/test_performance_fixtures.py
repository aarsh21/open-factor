import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from datetime import date

from generate_workout_fixture import generate
from storage_snapshot import resolve_snapshot


CATALOG = [dict(id=f'exercise-{i}', properties={'Metrics': 'Weight, Reps'}) for i in range(15)]


class PerformanceFixtureTest(unittest.TestCase):
    def test_deterministic_complete_unique_workouts(self):
        data = generate(CATALOG, workouts=2_000)
        self.assertEqual(data, generate(CATALOG, workouts=2_000))
        self.assertEqual(2_000, len(data['history']))
        self.assertEqual(2_000, len({workout['id'] for workout in data['history']}))
        ids = set()
        for workout in data['history']:
            self.assertGreater(workout['finishedAt'], workout['startedAt'])
            self.assertEqual(6, len(workout['exercises']))
            for entry in workout['exercises']:
                self.assertEqual(4, sum(not row['warmup'] for row in entry['sets']))
                for row in entry['sets']:
                    self.assertNotIn(row['id'], ids)
                    ids.add(row['id'])
        self.assertNotIn('storageHistory', data)

    def test_sizes_and_catalog_are_validated(self):
        for kwargs in [dict(workouts=0), dict(workouts=20_001), dict(sets=0), dict(exercises=21), dict(workouts=20_000, exercises=20, sets=20)]:
            with self.assertRaises(ValueError):
                generate(CATALOG, **kwargs)
        with self.assertRaises(ValueError):
            generate([])

    def test_weekends_do_not_create_future_records(self):
        end = date(2026, 11, 1)
        data = generate(CATALOG, workouts=10, end_date=end)
        self.assertTrue(all(reading['date'] <= str(end) for reading in data['scaleWeights']))
        self.assertEqual(10, len(data['history']))
        self.assertEqual(10, len({workout['startedAt'] for workout in data['history']}))

    def test_cli_refuses_to_overwrite_existing_output(self):
        script = Path(__file__).with_name('generate_workout_fixture.py')
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'fixture.json'
            command = [sys.executable, '-I', str(script), '--output', str(output), '--workouts', '10']
            subprocess.run(command, check=True, capture_output=True)
            original = output.read_bytes()
            failed = subprocess.run(command, capture_output=True)
            self.assertNotEqual(0, failed.returncode)
            self.assertEqual(original, output.read_bytes())
            self.assertEqual(10, len(json.loads(original)['history']))


class StorageSnapshotTest(unittest.TestCase):
    def test_inline_backups_need_no_extra_files(self):
        original = dict(schemaVersion=1, history=[{'id': 'old'}])
        self.assertIs(original, resolve_snapshot(original, lambda _: self.fail('Unexpected archive read')))

    def test_archive_is_verified_and_internal_reference_is_not_exported(self):
        raw = b'[{"id":"saved"}]'
        key = hashlib.sha256(raw).hexdigest()
        owner = hashlib.sha256(b'open_factor').hexdigest()
        snapshot = dict(schemaVersion=1, history=[], storageHistory=key)

        def read(path):
            self.assertEqual(f'files/workout-history/{owner}/{key}.json', path)
            return raw

        self.assertEqual(dict(schemaVersion=1, history=[dict(id='saved')]), resolve_snapshot(snapshot, read))
        self.assertEqual([], snapshot['history'])

    def test_corruption_and_path_traversal_are_rejected(self):
        with self.assertRaises(ValueError):
            resolve_snapshot(dict(storageHistory='a'*64), lambda _: b'[]')
        with self.assertRaises(ValueError):
            resolve_snapshot(dict(storageHistory='../outside'), lambda _: self.fail('Invalid path was read'))
        raw = b'{}'
        with self.assertRaises(ValueError):
            resolve_snapshot(dict(storageHistory=hashlib.sha256(raw).hexdigest()), lambda _: raw)


if __name__ == '__main__':
    unittest.main()
