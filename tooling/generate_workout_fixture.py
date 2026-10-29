#!/usr/bin/env python3
"""Create a deterministic, self-contained synthetic backup for a DISPOSABLE test install.

Restore Backup replaces the destination's data. This tool only creates a new file;
it never talks to a device, changes the app, or overwrites an existing file.
"""
import argparse
from datetime import date, datetime, timedelta, timezone
import json
from pathlib import Path
import random


def generate(catalog, workouts=2_000, exercises=6, sets=4, seed=21, end_date=date(2026, 10, 29)):
    if not 1 <= workouts <= 20_000 or not 1 <= exercises <= 20 or not 1 <= sets <= 20:
        raise ValueError('Use 1–20000 workouts, 1–20 exercises, and 1–20 working sets')
    if workouts * exercises * (sets + 1) > 1_000_000:
        raise ValueError('Limit fixtures to one million set rows')
    eligible = [e for e in catalog if e.get('properties', {}).get('Metrics') in ('Weight, Reps', 'Reps')]
    if not eligible:
        raise ValueError('Catalog contains no weight/reps or reps exercises')
    rng = random.Random(seed)
    chosen = rng.sample(eligible, min(len(eligible), max(12, exercises)))
    history = []
    day = end_date
    for index in range(workouts):
        while day.weekday() >= 5:
            day -= timedelta(days=1)
        started = int(datetime.combine(day, datetime.min.time(), timezone.utc).timestamp() * 1_000) + 12 * 3_600_000
        entries = []
        for number in range(exercises):
            exercise = chosen[(index * 3 + number) % len(chosen)]
            prefix = f'synthetic-{index}-exercise-{number}'
            rows = [dict(id=prefix+'-warmup', weightKg=20.0, reps=10, completed=True, warmup=True)]
            for row in range(sets):
                rows.append(dict(id=f'{prefix}-set-{row}', weightKg=round(20 + rng.randrange(25) * 2.5, 1),
                                 reps=rng.randrange(6, 16), rir=rng.randrange(4), completed=True, warmup=False,
                                 targetRepsMin=6, targetRepsMax=15, targetRir=2))
            entries.append(dict(id=prefix, exerciseId=exercise['id'], sets=rows, restSeconds=90,
                                notes='Synthetic performance fixture — not real training.'))
        history.append(dict(id=f'synthetic-workout-{index}', name=f'Synthetic {("Upper", "Lower", "Full body")[index % 3]} {index+1}',
                            startedAt=started, finishedAt=started+(45+index % 30)*60_000,
                            exercises=entries, notes='Generated test data', pausedDurationMs=(index % 5)*60_000))
        day -= timedelta(days=1)
    readings = [dict(id=f'synthetic-weight-{index}', date=str(end_date-timedelta(days=index)),
                     weightKg=round(80 + rng.uniform(-2, 2), 2), bodyFatPercent=18.0)
                for index in range(min(workouts, 3_650))]
    return dict(schemaVersion=1, history=history,
                preferences=dict(displayName='Synthetic performance test', weeklyWorkoutGoal=5),
                onboarding=dict(completed=True, name='Synthetic performance test'), scaleWeights=readings,
                routines=[dict(id='synthetic-template', name='Synthetic test template',
                               exerciseIds=[e['id'] for e in chosen[:exercises]])])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--catalog', type=Path, default=Path(__file__).resolve().parents[1] / 'openfactor/app/src/main/assets/catalog.json')
    parser.add_argument('--workouts', type=int, default=2_000)
    parser.add_argument('--exercises', type=int, default=6)
    parser.add_argument('--sets', type=int, default=4, help='Working sets per exercise, plus one warmup')
    parser.add_argument('--seed', type=int, default=21)
    parser.add_argument('--end-date', type=date.fromisoformat, default=date(2026, 10, 29))
    args = parser.parse_args()
    try:
        data = generate(json.loads(args.catalog.read_text()), args.workouts, args.exercises, args.sets, args.seed, args.end_date)
        with args.output.open('x', encoding='utf-8') as output:
            json.dump(data, output, ensure_ascii=False, separators=(',', ':'))
            output.write('\n')
    except (ValueError, OSError) as error:
        parser.error(str(error))
    print(f'Created {args.output}: {args.workouts:,} synthetic workouts, {args.workouts * args.exercises * args.sets:,} working sets, {args.output.stat().st_size:,} bytes.')
    print('DISPOSABLE TEST INSTALL ONLY: Restore Backup replaces destination data. Nothing was installed or imported.')


if __name__ == '__main__':
    main()
