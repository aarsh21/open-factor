"""Host-side control-flow tests, not certification of the native Android UI."""
import copy
from contextlib import ExitStack
import unittest
from unittest.mock import call, patch
import xml.etree.ElementTree as ET

import android_onboarding_smoke as smoke


def snapshot(*, completed=False, deferred=False, stage=0):
    return {
        'onboarding': {'completed': completed, 'deferred': deferred,
                       'profileDraft': {'stage': stage}},
        'preferences': {'displayName': 'Existing athlete', 'pounds': True,
                        'palette': 'MOSS', 'autoRest': True},
        'history': [{'id': 'saved-workout'}],
        'active': {'id': 'active-workout'},
        'programs': [{'id': 'saved-program'}],
        'programDraft': {'id': 'unsaved-program'},
        'activeProgramId': 'saved-program',
        'gyms': [{'id': 'gym'}],
        'scaleWeights': [{'id': 'historical-weight', 'date': '2026-01-01', 'weightKg': 70},
                        {'id': 'today-weight', 'date': '2026-11-04', 'weightKg': 71}],
        'healthConnect': {'lastSyncedAtMillis': None},
    }


class SetupNavigationTest(unittest.TestCase):
    def test_first_launch_waits_for_current_ui_before_reading_the_new_store(self):
        splash = ET.Element('node', text='Android splash')
        ready = ET.Element('node', text='Make it yours')
        expected = snapshot()
        with patch.object(smoke, 'launch') as launch, \
                patch.object(smoke, 'nodes', side_effect=[[splash], [ready]]), \
                patch.object(smoke.time, 'sleep') as sleep, \
                patch.object(smoke, 'state', return_value=expected) as state:
            self.assertIs(expected, smoke.initial_state())
        launch.assert_called_once_with()
        sleep.assert_called_once_with(.3)
        state.assert_called_once_with()

    def test_initial_launch_fails_closed_if_only_splash_or_unrelated_ui_is_visible(self):
        with patch.object(smoke, 'launch'), patch.object(smoke, 'nodes', return_value=[]), \
                patch.object(smoke.time, 'sleep'), patch.object(smoke, 'state') as state:
            with self.assertRaisesRegex(AssertionError, 'became ready'):
                smoke.initial_state()
        state.assert_not_called()

    def test_review_uses_settings_profile_then_review(self):
        with patch.object(smoke, 'tap') as tap:
            smoke.review_setup()
        self.assertEqual([call('Settings', last=True), call('Profile'), call('Review setup')], tap.call_args_list)

    def test_first_run_enters_directly_without_settings(self):
        data = snapshot()
        with patch.object(smoke, 'state', return_value=data), patch.object(smoke, 'review_setup') as review, \
                patch.object(smoke, 'expect_stage') as expect, patch.object(smoke, 'tap') as tap:
            smoke.enter_setup(data)
        review.assert_not_called()
        expect.assert_called_once_with(0)
        tap.assert_not_called()

    def test_completed_and_deferred_installs_use_profile_review(self):
        for completed, deferred in [(True, False), (False, True), (True, True)]:
            with self.subTest(completed=completed, deferred=deferred):
                before = snapshot(completed=completed, deferred=deferred)
                with patch.object(smoke, 'state', return_value=snapshot(stage=1)), \
                        patch.object(smoke, 'review_setup') as review, \
                        patch.object(smoke, 'expect_stage') as expect, patch.object(smoke, 'tap') as tap:
                    smoke.enter_setup(before)
                review.assert_called_once_with()
                self.assertEqual([call(1), call(0)], expect.call_args_list)
                tap.assert_called_once_with('Back', 'content-desc')

    def test_unfinished_last_stage_goes_back_without_discarding_or_restarting(self):
        data = snapshot(stage=2)
        with patch.object(smoke, 'state', return_value=data), patch.object(smoke, 'review_setup') as review, \
                patch.object(smoke, 'expect_stage') as expect, patch.object(smoke, 'tap') as tap:
            smoke.enter_setup(data)
        review.assert_not_called()
        self.assertEqual([call(2), call(1), call(0)], expect.call_args_list)
        self.assertEqual([call('Back', 'content-desc')] * 2, tap.call_args_list)

    def test_stage_assertion_checks_native_heading_progress_and_durable_stage(self):
        with patch.object(smoke, 'find') as find, patch.object(smoke, 'state', return_value=snapshot(stage=1)):
            smoke.expect_stage(1)
            with self.assertRaises(AssertionError):
                smoke.expect_stage(2)
        self.assertEqual([call('Your starting point'), call('Setup step 2 of 3', 'content-desc')],
                         find.call_args_list[:2])

    def test_metric_reset_clears_fields_in_their_existing_units_before_switching(self):
        for feet in [False, True]:
            data = snapshot(stage=1)
            data['onboarding']['profileDraft']['heightInFeet'] = feet
            with self.subTest(feet=feet), patch.object(smoke, 'state', return_value=data), \
                    patch.object(smoke, 'clear_field') as clear, patch.object(smoke, 'tap') as tap, \
                    patch.object(smoke, 'expect_stage') as expect:
                smoke.prepare_metric_body()
            self.assertEqual([call('Profile weight')] +
                             ([call('Profile height feet'), call('Profile height inches')] if feet
                              else [call('Profile height')]), clear.call_args_list)
            self.assertEqual([call('Back', 'content-desc'), call('Continue'), call('Kilograms'),
                              call('Centimeters')], tap.call_args_list)
            self.assertEqual([call(0), call(1)], expect.call_args_list)

    def test_duration_control_roundtrip_does_not_clamp_nonstandard_values(self):
        for seconds in [0, 5, 120, 137, 1785, 1786, 1799, 1800]:
            current = seconds

            def tap(label, attr):
                nonlocal current
                self.assertEqual('content-desc', attr)
                current = max(0, min(1800, current + (15 if label == 'Longer rest timer' else -15)))

            with self.subTest(seconds=seconds), patch.object(smoke, 'tap', side_effect=tap):
                smoke.exercise_rest_controls(seconds)
                self.assertEqual(seconds, current)

    def test_home_respects_an_existing_active_workout(self):
        with patch.object(smoke, 'find') as find, \
                patch.object(smoke, 'nodes', return_value=[ET.Element('node', text='Resume workout')]), \
                patch.object(smoke, 'tap') as tap:
            smoke.workout_home(snapshot())
            smoke.workout_home({'active': None})
        self.assertEqual([call('Resume workout'), call('Start workout')], find.call_args_list)
        tap.assert_not_called()

    def test_restart_minimizes_a_saved_session_without_finishing_or_discarding_it(self):
        session = ET.Element('node', {'content-desc': 'Minimize workout'})
        with patch.object(smoke, 'find') as find, patch.object(smoke, 'nodes', return_value=[session]), \
                patch.object(smoke, 'tap') as tap:
            smoke.workout_home(snapshot())
        tap.assert_called_once_with('Minimize workout', 'content-desc')
        find.assert_called_once_with('Resume workout')

    def test_optional_panel_returns_without_any_connect_or_provider_action(self):
        before = snapshot()
        for text in ['Reads weight, scale body fat and daily steps for the last 30 days.',
                     'Health Connect needs Android 9 or newer and a supported profile.']:
            with self.subTest(text=text), ExitStack() as stack:
                tap = stack.enter_context(patch.object(smoke, 'tap'))
                stack.enter_context(patch.object(smoke, 'find'))
                stack.enter_context(patch.object(smoke, 'capture'))
                stack.enter_context(patch.object(smoke, 'nodes', return_value=[ET.Element('node', text=text)]))
                stack.enter_context(patch.object(smoke, 'state', return_value=copy.deepcopy(before)))
                smoke.optional_health_panel(before)
                self.assertEqual([call('Settings', last=True), call('Health Connect'),
                                  call('Back', 'content-desc')], tap.call_args_list)


class PreservationTest(unittest.TestCase):
    def test_profile_save_allows_only_owned_preferences_and_onboarding(self):
        before = snapshot()
        after = copy.deepcopy(before)
        after['onboarding'] = {'completed': True}
        after['preferences'].update(displayName='Synthetic Setup', pounds=False,
                                    heightInFeet=False, weeklyWorkoutGoal=4,
                                    rateEffortAfterExercise=True, autoRest=False, restSeconds=120)
        smoke.assert_preserved(before, after, profile_saved=True)
        with self.assertRaisesRegex(AssertionError, 'Changed preference'):
            smoke.assert_preserved(before, after)

    def test_unrelated_training_body_and_provider_records_cannot_change(self):
        before = snapshot()
        for key in before.keys() - {'onboarding', 'preferences'}:
            with self.subTest(key=key):
                after = copy.deepcopy(before)
                after[key] = None
                with self.assertRaisesRegex(AssertionError, 'Changed unrelated data: ' + key):
                    smoke.assert_preserved(before, after, profile_saved=True)
        after = copy.deepcopy(before)
        after['scaleWeights'][1]['weightKg'] = 75.5
        with self.assertRaisesRegex(AssertionError, 'scaleWeights'):
            smoke.assert_preserved(before, after, profile_saved=True)

    def test_unrelated_preferences_and_new_data_keys_are_protected(self):
        before = snapshot()
        after = copy.deepcopy(before)
        after['preferences']['palette'] = 'CHARCOAL'
        with self.assertRaisesRegex(AssertionError, 'palette'):
            smoke.assert_preserved(before, after, profile_saved=True)
        after = copy.deepcopy(before)
        after['futureRecords'] = [{'id': 'unexpected'}]
        with self.assertRaisesRegex(AssertionError, 'futureRecords'):
            smoke.assert_preserved(before, after, profile_saved=True)


class AnimationSettingTest(unittest.TestCase):
    def test_original_value_including_literal_null_is_restored_after_failure(self):
        for old in ['1.5', 'null']:
            with self.subTest(old=old), patch.object(smoke, 'adb', return_value=f'other=1\nanimator_duration_scale={old}\n') as adb:
                with self.assertRaisesRegex(RuntimeError, 'UI failed'):
                    with smoke.reduced_motion():
                        raise RuntimeError('UI failed')
                self.assertEqual(call('shell', 'settings', 'put', 'global', 'animator_duration_scale', old),
                                 adb.call_args_list[-1])

    def test_absent_setting_is_deleted_instead_of_written_as_null(self):
        with patch.object(smoke, 'adb', return_value='other=1\n') as adb:
            with smoke.reduced_motion():
                pass
        self.assertEqual([
            call('shell', 'settings', 'list', 'global'),
            call('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0'),
            call('shell', 'settings', 'delete', 'global', 'animator_duration_scale'),
        ], adb.call_args_list)


class UIHierarchyTest(unittest.TestCase):
    def test_button_validation_detects_disabled_parent_not_just_enabled_text(self):
        for enabled in [False, True]:
            xml = '<hierarchy><node enabled="' + str(enabled).lower() + '"><node text="Continue" enabled="true"/></node></hierarchy>'
            with self.subTest(enabled=enabled), patch.object(smoke, 'find'), patch.object(smoke, 'adb', return_value=xml):
                smoke.assert_enabled('Continue', enabled)
                with self.assertRaises(AssertionError):
                    smoke.assert_enabled('Continue', not enabled)

    def test_clear_field_does_not_send_an_invalid_empty_adb_text_command(self):
        old = ET.Element('node', {'class': 'android.widget.EditText', 'focused': 'true', 'text': '75.5'})
        empty = ET.Element('node', {'class': 'android.widget.EditText', 'focused': 'true', 'text': ''})
        with patch.object(smoke, 'tap') as tap, patch.object(smoke, 'nodes', side_effect=[[old], [empty]]), \
                patch.object(smoke, 'adb') as adb:
            smoke.clear_field('Profile weight')
        tap.assert_called_once_with('Profile weight', 'content-desc')
        self.assertNotIn('text', [argument for invocation in adb.call_args_list for argument in invocation.args])
        self.assertEqual(call('shell', 'input', 'keyevent', '4'), adb.call_args_list[-1])

    def test_navigation_ignores_screen_title_and_uses_clickable_tab_ancestors(self):
        root = ET.Element('hierarchy')
        ET.SubElement(root, 'node', text='Workout', bounds='[0,0][100,100]')
        for index, label in enumerate(smoke.NAV_LABELS):
            tab = ET.SubElement(root, 'node', clickable='true', selected=str(label == 'Workout').lower(),
                                bounds=f'[{index * 100},1800][{index * 100 + 100},1900]')
            ET.SubElement(tab, 'node', text=label, bounds=f'[{index * 100},1850][{index * 100 + 100},1880]')
        with patch.object(smoke, 'nodes'), patch.object(smoke, 'adb', return_value=ET.tostring(root, encoding='unicode')):
            targets = smoke.nav_targets('Workout')
            self.assertEqual((100, 1800, 200, 1900), targets['Workout'])
            with self.assertRaises(AssertionError):
                smoke.nav_targets('Settings')


if __name__ == '__main__':
    unittest.main()
