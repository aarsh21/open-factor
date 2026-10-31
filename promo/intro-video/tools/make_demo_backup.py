#!/usr/bin/env python3
"""Write a believable demo backup for recording the intro film on a disposable emulator.

Ten weeks of an Upper/Lower plan (Mon, Tue, Thu, Fri) with steady progress. All values are
illustrative demo data. Restore it only on a throwaway install: Restore Backup replaces data.
"""
from datetime import date, datetime, timedelta, timezone
import json
from pathlib import Path
import random
import sys

BENCH = '19f5c6f170d8808bb424e98de4472a7e'
ROW = '1a15c6f170d880f89beacd8c81156f2d'
OHP = '1a05c6f170d880c5ad11c99b302b1bf9'
PULLDOWN = '1a15c6f170d8800d8fc9d2c235673bf6'
LATERAL = '1a05c6f170d880e28b6bfbd0e217fcbc'
PUSHDOWN = '1a15c6f170d880479d38c49a76cb6b10'
SQUAT = '1a25c6f170d8803d8231d083fdd65458'
RDL = '1a35c6f170d880bebc64f4dce0d5f5f1'
LEG_PRESS = '1a25c6f170d88079a926dde776766181'
HIP_THRUST = '1a35c6f170d88059b804ff16ff621a1c'
INCLINE_DB = '1a05c6f170d8806b911acf558c5af4f6'

# exercise: (start kg, end kg, reps, working sets)
DAYS = {
    'Upper A': [(BENCH, 80.0, 97.5, 5, 3), (ROW, 60.0, 75.0, 8, 3), (LATERAL, 8.0, 12.0, 12, 3),
                (PUSHDOWN, 25.0, 35.0, 12, 3)],
    'Lower A': [(SQUAT, 100.0, 125.0, 5, 3), (RDL, 80.0, 100.0, 8, 3), (LEG_PRESS, 160.0, 200.0, 10, 3)],
    'Upper B': [(OHP, 45.0, 55.0, 6, 3), (PULLDOWN, 55.0, 70.0, 10, 3), (INCLINE_DB, 26.0, 32.0, 10, 3),
                (LATERAL, 8.0, 12.0, 15, 2)],
    'Lower B': [(RDL, 85.0, 105.0, 6, 3), (HIP_THRUST, 100.0, 140.0, 10, 3), (LEG_PRESS, 170.0, 210.0, 12, 3)],
}
SCHEDULE = {0: 'Upper A', 1: 'Lower A', 3: 'Upper B', 4: 'Lower B'}
NOTES = {BENCH: 'Pause the first rep. Elbows at 45°.', SQUAT: 'Belt on for the top set.'}


def plate(kg, step=2.5):
    return round(round(kg / step) * step, 1)


def build(end=date(2026, 10, 30), weeks=10, seed=7):
    rng = random.Random(seed)
    start = end - timedelta(weeks=weeks) + timedelta(days=1)
    total = sum(1 for i in range((end - start).days + 1) if (start + timedelta(days=i)).weekday() in SCHEDULE)
    history, n = [], 0
    for i in range((end - start).days + 1):
        day = start + timedelta(days=i)
        name = SCHEDULE.get(day.weekday())
        if not name:
            continue
        progress = n / max(1, total - 1)
        n += 1
        began = int(datetime.combine(day, datetime.min.time(), timezone.utc).timestamp() * 1000) + 17 * 3_600_000
        entries = []
        for k, (ex, lo, hi, reps, count) in enumerate(DAYS[name]):
            top = plate(lo + (hi - lo) * progress, 2.5 if hi >= 20 else 1.0)
            rows = [dict(id=f'demo-{n}-{k}-w', weightKg=plate(top * 0.6), reps=8, completed=True, warmup=True)]
            for s in range(count):
                rows.append(dict(id=f'demo-{n}-{k}-{s}', weightKg=top, reps=max(1, reps - (s == count - 1 and rng.random() < 0.3)),
                                 rir=max(0, 2 - s), completed=True, warmup=False))
            entries.append(dict(id=f'demo-{n}-{k}', exerciseId=ex, sets=rows, restSeconds=120 if k == 0 else 90,
                                notes=NOTES.get(ex, '') if progress > 0.9 else ''))
        minutes = 52 + rng.randrange(18)
        history.append(dict(id=f'demo-workout-{n}', name=name, startedAt=began,
                            finishedAt=began + minutes * 60_000, exercises=entries, notes=''))
    history.reverse()
    weights = [dict(id=f'demo-weight-{d}', date=str(end - timedelta(days=d)),
                    weightKg=round(79.6 + d * 0.035 + rng.uniform(-0.35, 0.35), 1))
               for d in range(weeks * 7)]
    routines = [dict(id=f'demo-{name.lower().replace(" ", "-")}', name=name, exerciseIds=[e[0] for e in rows])
                for name, rows in DAYS.items()]
    return dict(schemaVersion=1, history=history, routines=routines, favorites=[BENCH, SQUAT],
                preferences=dict(displayName='Sam', weeklyWorkoutGoal=4, restSeconds=120),
                onboarding=dict(completed=True, name='Sam'), scaleWeights=weights)


if __name__ == '__main__':
    out = Path(sys.argv[1])
    out.write_text(json.dumps(build(), ensure_ascii=False, separators=(',', ':')) + '\n')
    print(f'Wrote {out}')
