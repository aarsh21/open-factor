#!/usr/bin/env python3
"""Check current profile setup and floating navigation on a disposable emulator.

Leaves a synthetic local profile, but preserves workout/program/body records.
Never accesses source apps or grants Health Connect access.
Run with --serial emulator-... --output /private/evidence/path.
"""
import json
import subprocess
import time
from contextlib import contextmanager

from android_program_smoke import (
    E, adb, bounds, capture, configure, enter, find, launch, nodes, state, tap,
)

STAGES = ('Make it yours', 'Your starting point', 'Ready, set, log.')
NAV_LABELS = ('Dashboard', 'Workout', 'Exercises', 'Settings')
PROFILE_PREFERENCES = {
    'displayName', 'pounds', 'heightInFeet', 'weeklyWorkoutGoal',
    'rateEffortAfterExercise', 'autoRest', 'restSeconds',
}


def assert_preserved(before, after, *, profile_saved=False):
    """Only profile fields may change; even existing weigh-ins must remain exact."""
    for key in before.keys() | after.keys():
        if key in ('onboarding', 'preferences'):
            continue
        assert before.get(key) == after.get(key), 'Changed unrelated data: ' + key
    old = before['preferences']
    new = after['preferences']
    for key in old.keys() | new.keys():
        if profile_saved and key in PROFILE_PREFERENCES:
            continue
        assert old.get(key) == new.get(key), 'Changed preference: ' + key


def expect_stage(stage):
    find(STAGES[stage])
    find(f'Setup step {stage + 1} of 3', 'content-desc')
    assert state()['onboarding']['profileDraft']['stage'] == stage


def review_setup():
    """The supported entry on both deferred and completed installations."""
    tap('Settings', last=True)
    tap('Profile')
    tap('Review setup')


def enter_setup(data):
    if data['onboarding']['completed'] or data['onboarding']['deferred']:
        review_setup()
    stage = state()['onboarding']['profileDraft']['stage']
    expect_stage(stage)
    # A caller may have left a partial draft on any of the three pages.
    for previous in range(stage - 1, -1, -1):
        tap('Back', 'content-desc')
        expect_stage(previous)


def clear_field(description):
    """ADB input text cannot enter an empty string; clear the focused field instead."""
    tap(description, 'content-desc')
    fields = [n for n in nodes() if n.get('class') == 'android.widget.EditText'
              and n.get('focused') == 'true']
    assert len(fields) == 1, 'Expected one focused profile field'
    length = len(fields[0].get('text', ''))
    assert length <= 250, 'Unexpectedly long profile field'
    if length:
        adb('shell', 'input', 'keyevent', '123')
        adb('shell', 'for i in ' + ' '.join(str(i) for i in range(length + 3))
            + '; do input keyevent 67; done')
    fields = [n for n in nodes() if n.get('class') == 'android.widget.EditText'
              and n.get('focused') == 'true']
    assert len(fields) == 1 and fields[0].get('text', '') == ''
    adb('shell', 'input', 'keyevent', '4')


def assert_enabled(label, expected):
    # The text can be a child of the actual disabled button in UIAutomator.
    find(label)
    root = E.fromstring(adb('shell', 'cat', '/sdcard/of-program.xml'))
    parents = {child: parent for parent in root.iter() for child in parent}
    matches = [n for n in root.iter('node') if n.get('text') == label]
    assert len(matches) == 1, (label, len(matches))
    node = matches[0]
    enabled = True
    while node is not None:
        enabled = enabled and node.get('enabled') != 'false'
        node = parents.get(node)
    assert enabled == expected, (label, 'enabled', enabled, expected)


def workout_home(data):
    if data.get('active') is not None:
        # Process restart opens a saved session. Minimize; never finish/discard it.
        for _ in range(30):
            visible = nodes()
            if any(n.get('content-desc') == 'Minimize workout' for n in visible):
                tap('Minimize workout', 'content-desc')
                break
            if any(n.get('text') == 'Resume workout' for n in visible):
                break
            time.sleep(.3)
        find('Resume workout')
    else:
        find('Start workout')


def optional_health_panel(before):
    """Open the manual native panel, never the auto-connect/consent action."""
    tap('Settings', last=True)
    tap('Health Connect')
    find('Health Connect')
    # Both provider-capable and unavailable emulator images have a native panel.
    texts = [n.get('text', '') for n in nodes()]
    assert any(text.startswith(('Reads weight, scale body fat',
                                'Health Connect needs Android 9')) for text in texts), texts
    capture('health-connect-optional-panel')
    tap('Back', 'content-desc')
    find('Profile')
    assert_preserved(before, state())


def prepare_metric_body():
    # Invalid resumed input disables unit switches. Clear optional fields in their
    # existing units first, then re-enter the page at its top to choose metric.
    inherited = state()['onboarding']['profileDraft']
    clear_field('Profile weight')
    if inherited['heightInFeet']:
        clear_field('Profile height feet')
        clear_field('Profile height inches')
    else:
        clear_field('Profile height')
    tap('Back', 'content-desc')
    expect_stage(0)
    tap('Continue')
    expect_stage(1)
    tap('Kilograms')
    tap('Centimeters')


def exercise_rest_controls(seconds):
    # Choose the order that cannot clamp at 0 or 1800, even for odd durations.
    first, second = (('Shorter rest timer', 'Longer rest timer') if seconds > 1785
                     else ('Longer rest timer', 'Shorter rest timer'))
    tap(first, 'content-desc')
    tap(second, 'content-desc')


def complete_setup(before):
    enter_setup(before)
    clear_field('Profile name')
    assert_enabled('Continue', False)
    capture('name-validation')
    enter('Profile name', 'Synthetic Setup', 'content-desc')
    tap('Gender Prefer not to say', 'content-desc')
    assert_enabled('Continue', True)
    capture('make-it-yours')
    tap('Continue')
    expect_stage(1)
    find('Health Connect')
    prepare_metric_body()
    enter('Profile weight', '0', 'content-desc')
    enter('Profile height', '0', 'content-desc')
    assert_enabled('Continue', False)
    capture('body-validation')
    raw = state()['onboarding']['profileDraft']
    assert raw['name'] == 'Synthetic Setup' and raw['weightText'] == raw['heightText'] == '0'

    # Skip preserves invalid raw text and stage without applying ANY profile fields.
    tap('Skip for now')
    workout_home(before)
    deferred = state()
    assert deferred['onboarding']['deferred']
    assert deferred['onboarding']['profileDraft'] == raw
    assert_preserved(before, deferred)
    launch()
    workout_home(before)
    assert state()['onboarding']['profileDraft'] == raw
    optional_health_panel(before)
    review_setup()
    expect_stage(1)
    resumed = state()['onboarding']
    assert not resumed['deferred'] and resumed['profileDraft'] == raw
    capture('resumed-starting-point')

    # Validate weight without creating/replacing today's (possibly existing) reading.
    # Empty optional weight is valid and withProfileSetup leaves all records alone.
    clear_field('Profile weight')
    assert_enabled('Continue', False)  # Height is still invalid.
    enter('Profile height', '180', 'content-desc')
    assert_enabled('Continue', True)
    capture('starting-point')
    tap('Continue')
    expect_stage(2)
    draft = state()['onboarding']['profileDraft']
    goal = draft['weeklyGoal']
    while goal != 4:
        tap('More workouts per week' if goal < 4 else 'Fewer workouts per week', 'content-desc')
        goal += 1 if goal < 4 else -1
    find('Weekly workout goal: 4', 'content-desc')
    if not draft['rateEffort']:
        tap('Rate effort after exercise')
    if not draft['autoRest']:
        tap('Automatic rest timer')
    # Exercise duration controls, restoring their original value before disabling rest.
    exercise_rest_controls(draft['restSeconds'])
    tap('Automatic rest timer')
    training = state()['onboarding']['profileDraft']
    assert training['weeklyGoal'] == 4 and training['rateEffort'] and not training['autoRest']
    assert training['restSeconds'] == draft['restSeconds']
    capture('training-choices')
    tap('Back', 'content-desc')
    expect_stage(1)
    assert state()['onboarding']['profileDraft']['heightText'] == '180'
    tap('Continue')
    expect_stage(2)
    launch()
    expect_stage(2)
    assert state()['onboarding']['profileDraft'] == training
    tap('Start training')
    workout_home(before)
    saved = state()
    setup, preferences = saved['onboarding'], saved['preferences']
    assert setup['completed'] and not setup['deferred'] and setup.get('profileDraft') is None
    assert setup['heightCm'] == '180' and setup['gender'] == 'UNSPECIFIED'
    assert preferences['displayName'] == 'Synthetic Setup'
    assert not preferences['pounds'] and not preferences['heightInFeet']
    assert preferences['weeklyWorkoutGoal'] == 4 and preferences['rateEffortAfterExercise']
    assert not preferences['autoRest'] and preferences['restSeconds'] == draft['restSeconds']
    assert_preserved(before, saved, profile_saved=True)
    launch()
    workout_home(before)
    assert state()['preferences'] == preferences
    assert_preserved(before, state(), profile_saved=True)
    capture('completed-workout-home')

    # Completed installs bypass first run; Review setup restarts at page 1, then a
    # no-edit completion must not fabricate a weigh-in from a historical baseline.
    review_setup()
    expect_stage(0)
    assert state()['onboarding']['profileDraft']['name'] == 'Synthetic Setup'
    tap('Continue')
    expect_stage(1)
    tap('Continue')
    expect_stage(2)
    tap('Start training')
    workout_home(before)
    assert state()['preferences'] == preferences
    assert_preserved(before, state(), profile_saved=True)

    # Profile has explicit Save and discard protection, not the removed setup row.
    tap('Settings', last=True)
    tap('Profile')
    enter('Profile name', 'Discarded profile', 'content-desc')
    tap('Back', 'content-desc')
    find('Discard profile changes?')
    tap('Discard')
    assert state()['preferences'] == preferences
    tap('Profile')
    enter('Profile height', '0', 'content-desc')
    assert_enabled('Save', False)
    enter('Profile height', '180', 'content-desc')
    assert_enabled('Save', True)
    tap('Save')
    find('Profile')
    assert state()['preferences'] == preferences
    assert_preserved(before, state(), profile_saved=True)
    tap('Workout', last=True)


def nav_targets(expected=None):
    nodes()
    root = E.fromstring(adb('shell', 'cat', '/sdcard/of-program.xml'))
    parents = {child: parent for parent in root.iter() for child in parent}
    found, selected = {}, []
    for node in root.iter('node'):
        label = node.get('text')
        if label not in NAV_LABELS:
            continue
        target = node
        while target in parents and not (target.get('clickable') == 'true' or target.get('selected') == 'true'):
            target = parents[target]
        if target.get('clickable') == 'true' or target.get('selected') == 'true':
            found[label] = bounds(target)
        if target.get('selected') == 'true':
            selected.append(label)
    assert len(found) == 4, found
    if expected is not None:
        assert selected == [expected], (expected, selected)
    return found


@contextmanager
def reduced_motion():
    # Preserve absence separately: `settings get` returns "null" for either case.
    settings = dict(line.split('=', 1) for line in
                    adb('shell', 'settings', 'list', 'global').splitlines() if '=' in line)
    old = settings.get('animator_duration_scale')
    try:
        adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0')
        yield
    finally:
        if old is None:
            adb('shell', 'settings', 'delete', 'global', 'animator_duration_scale')
        else:
            adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', old)


def check_navigation(args):
    nav = nav_targets('Workout')

    def click(label):
        left, top, right, bottom = nav[label]
        adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
        assert nav == nav_targets(label)

    record = subprocess.Popen(
        ['adb', '-s', args.serial, 'shell', 'screenrecord', '--time-limit', '20', '/sdcard/of-nav-motion.mp4'],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )
    try:
        time.sleep(.8)
        click('Settings')
        adb('shell', 'input', 'swipe', '540', '1400', '540', '450', '450')
        assert nav == nav_targets('Settings')
        click('Exercises')
        adb('shell', 'input', 'swipe', '540', '1400', '540', '700', '400')
        assert nav == nav_targets('Exercises')
        click('Dashboard')
        click('Workout')
        record.communicate(timeout=30)
        assert record.returncode == 0, 'Navigation screenrecord failed'
    finally:
        if record.poll() is None:
            record.terminate()
            record.communicate(timeout=10)
    adb('pull', '/sdcard/of-nav-motion.mp4', str(args.output / 'navigation-motion.mp4'))
    capture('workout')
    tap('Settings', last=True)
    for _ in range(5):
        adb('shell', 'input', 'swipe', '540', '1400', '540', '400', '300')
    row = find('Licenses & Attribution')[0]
    capture('settings-bottom')
    navtop = min(value[1] for value in nav_targets().values())
    assert bounds(row)[3] < navtop, (bounds(row), navtop)
    assert nav == nav_targets()
    with reduced_motion():
        for destination in NAV_LABELS:
            click(destination)
        click('Exercises')
        find('Search exercises')
        capture('reduced-motion')
        assert bounds(find('Search exercises')[0])[3] < min(v[1] for v in nav_targets().values())
        enter('Search exercises', 'chin-up')
        find('Chin-Up')
        capture('exercise-search')
        tap('Clear search', 'content-desc')
        click('Dashboard')
        find('Dashboard')


def initial_state():
    # am start returns before the first composition creates a fresh durable store.
    launch()
    for _ in range(30):
        visible = nodes()
        if any(n.get('content-desc') == 'Minimize workout' for n in visible):
            tap('Minimize workout', 'content-desc')
            continue
        if any(n.get('text') in (*STAGES, 'Start workout', 'Resume workout') for n in visible):
            return state()
        time.sleep(.3)
    raise AssertionError('Neither setup nor Workout home became ready')


def main():
    args = configure(__doc__)
    before = initial_state()
    complete_setup(before)
    check_navigation(args)
    assert_preserved(before, state(), profile_saved=True)
    result = {
        'status': 'passed', 'serial': args.serial, 'onboarding_flow': True,
        'defer_restart_resume': True, 'validation_recovery': True,
        'completed_review_setup': True, 'profile_save_discard': True,
        'optional_health_panel_return': True, 'health_connect_provider_sync_tested': False,
        'training_data_preserved': True, 'weight_records_preserved': True,
        'preserved_record_counts': {key: len(before.get(key, []))
                                    for key in ('history', 'programs', 'scaleWeights')},
        'fixed_navigation_targets': True, 'last_row_clearance': True,
        'reduced_motion_destinations': True,
    }
    text = json.dumps(result, indent=2) + '\n'
    (args.output / 'result.json').write_text(text)
    print(text, end='')


if __name__ == '__main__':
    main()
