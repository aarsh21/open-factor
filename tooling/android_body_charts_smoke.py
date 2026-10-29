#!/usr/bin/env python3
"""Check raw body charts offline on a disposable emulator, preserving unrelated app data.

Creates four synthetic weigh-ins and one full-body record through the real UI.
Leaves those records for review; never touches a phone, exercise assets or existing smoke scripts.
"""
import argparse
import calendar
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
if not re.fullmatch(r'emulator-\d+', args.serial):
    raise SystemExit('Use a disposable emulator, never a physical phone')
args.output.mkdir(parents=True, exist_ok=True)


def adb(*command, raw=False):
    result = subprocess.check_output(['adb', '-s', args.serial, *command], timeout=60)
    return result if raw else result.decode()


def nodes():
    for attempt in range(8):
        try:
            dumped = adb('shell', 'uiautomator', 'dump', '/sdcard/of-chart-ui.xml')
            if 'dumped to:' not in dumped:
                raise RuntimeError('UI hierarchy unavailable; do not read a stale dump')
            return list(ET.fromstring(adb('shell', 'cat', '/sdcard/of-chart-ui.xml')).iter('node'))
        except (ET.ParseError, subprocess.CalledProcessError, RuntimeError):
            if attempt == 7:
                raise
            time.sleep(.3)


def bounds(node):
    return tuple(map(int, re.findall(r'\d+', node.get('bounds'))))


def capture(name):
    nodes()
    (args.output / (name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p', raw=True))
    (args.output / (name+'.xml')).write_text(adb('shell', 'cat', '/sdcard/of-chart-ui.xml'))


def target(label, attr='text', scroll=True, last=False):
    for _ in range(14):
        tree = nodes()
        found = [n for n in tree if n.get(attr, '').casefold() == label.casefold() and bounds(n)[3]-bounds(n)[1] >= 30]
        if found:
            return found[-1 if last else 0]
        scrolls = [n for n in tree if n.get('scrollable') == 'true']
        if scroll and scrolls:
            left, top, right, bottom = bounds(scrolls[-1])
            adb('shell', 'input', 'swipe', str((left+right)//2), str(top+(bottom-top)*3//5), str((left+right)//2), str(top+(bottom-top)*2//5), '600')
        else:
            time.sleep(.2)
    capture('failure')
    raise AssertionError((label, attr))


def tap(label, attr='text', last=False):
    left, top, right, bottom = bounds(target(label, attr, last=last))
    adb('shell', 'input', 'tap', str((left+right)//2), str((top+bottom)//2))


def scroll_top():
    for _ in range(3):
        scrolls = [n for n in nodes() if n.get('scrollable') == 'true']
        if not scrolls:
            return
        left, top, right, bottom = bounds(scrolls[-1])
        adb('shell', 'input', 'swipe', str((left+right)//2), str(top+(bottom-top)//5), str((left+right)//2), str(top+(bottom-top)*4//5), '250')


def check_selection(value, day):
    target('Selected entry')
    root = ET.fromstring(adb('shell', 'cat', '/sdcard/of-chart-ui.xml'))
    parents = {child: parent for parent in root.iter() for child in parent}
    heading = next(n for n in root.iter() if n.get('text') == 'Selected entry')
    texts = [n.get('text') for n in parents[heading].iter()]
    assert value in texts and str(day) in texts, texts


def check_range_checked(label):
    target(label, scroll=False)
    root = ET.fromstring(adb('shell', 'cat', '/sdcard/of-chart-ui.xml'))
    parents = {child: parent for parent in root.iter() for child in parent}
    node = next(n for n in root.iter() if n.get('text') == label)
    while node is not None:
        if node.get('checkable') == 'true':
            assert node.get('checked') == 'true', node.attrib
            return
        node = parents.get(node)
    raise AssertionError('No range chip: '+label)


def enter(label, value):
    tap(label)
    field = next(n for n in nodes() if n.get('class') == 'android.widget.EditText' and n.get('focused') == 'true')
    counts = ' '.join(str(i) for i in range(len(field.get('text', ''))+1))
    adb('shell', f'for i in {counts}; do input keyevent 67; input keyevent 112; done')
    if value:
        adb('shell', 'input', 'text', value)
    adb('shell', 'input', 'keyevent', '4')


def state():
    root = ET.fromstring(adb('shell', 'run-as', 'org.openfactor.app', 'cat', 'shared_prefs/open_factor.xml'))
    from storage_snapshot import snapshot_data
    return snapshot_data(root, adb)


def photo_integrity(data):
    references = {value for record in data.get('progressPhotos', []) for value in record['photos'].values()}
    hashes = {ref: adb('shell', 'run-as', 'org.openfactor.app', 'sha256sum', 'no_backup/progress-photos/'+ref).split()[0] for ref in references}
    assert all(reference == digest for reference, digest in hashes.items())
    return hashes


def launch():
    adb('shell', 'am', 'force-stop', 'org.openfactor.app')
    adb('shell', 'am', 'start', '-W', '-f', '0x10008000', '-n', 'org.openfactor.app/.MainActivity')


def settings():
    launch()
    tap('Settings', last=True)
    scroll_top()


def units(pounds):
    settings(); tap('Units'); tap('Pounds (lb)' if pounds else 'Kilograms (kg)')
    tap('Back', 'content-desc')


def open_charts(measurements=False):
    settings(); tap('Body Data')
    if measurements:
        tap('Measurements')
    tap('View Charts')


def number(value):
    return format(Decimal(str(value)).quantize(Decimal('.01'), rounding=ROUND_HALF_UP), 'f').rstrip('0').rstrip('.')


wifi = adb('shell', 'settings', 'get', 'global', 'wifi_on').strip()
mobile = adb('shell', 'settings', 'get', 'global', 'mobile_data').strip()
font = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
baseline = state()
try:
    adb('shell', 'svc', 'wifi', 'disable'); adb('shell', 'svc', 'data', 'disable')
    connectivity = adb('shell', 'dumpsys', 'connectivity')
    assert 'Active default network: none' in connectivity
    (args.output / 'connectivity.txt').write_text(connectivity)
    apk = adb('shell', 'pm', 'path', 'org.openfactor.app').strip().removeprefix('package:')
    (args.output / 'apk-sha256.txt').write_text(adb('shell', 'sha256sum', apk))
    # Let the installed codec serialize legacy defaults before comparing stored shapes.
    units(baseline['preferences']['pounds'])
    baseline = state()
    assert 'bodyCharts' not in baseline['preferences'].get('dismissedTutorials', []), 'Reset the body chart tutorial before this first-entry check'
    photos_before = photo_integrity(baseline)
    today = date.fromisoformat(adb('shell', 'date', '+%F').strip())
    monday = today-timedelta(days=today.weekday())
    first = today.replace(day=1)
    units(False)
    oldest_year = min([today.year]+[date.fromisoformat(r['date']).year for r in baseline.get('scaleWeights', [])])-2
    fixtures = [(date(oldest_year, 1, 1), '55.25', '18.5'),
                (first-timedelta(days=1), '57', ''), (monday, '61.75', '0'), (monday, '64', '')]
    tap('Body Data')
    for day, weight, fat in fixtures:
        tap('Log Weight'); enter('Date (YYYY-MM-DD)', day.isoformat())
        enter('Weight', weight)
        if fat:
            enter('Body Fat (optional)', fat)
        tap('Save')
        target('View Charts')
    tap('Measurements'); tap('Log Measurements')
    tap('Change measurement date', 'content-desc')
    enter('Date (YYYY-MM-DD)', monday.isoformat())
    tap('Apply date')
    enter('Waist', '84.6'); enter('Left Bicep', '31.1'); enter('Right Bicep', '34.2')
    tap('Save'); target('View Charts')
    saved = state()
    assert len(saved['scaleWeights']) == len(baseline.get('scaleWeights', []))+4
    assert saved['scaleWeights'][4:] == baseline.get('scaleWeights', [])
    assert saved['bodyMeasurements'][1:] == baseline.get('bodyMeasurements', [])
    for key in set(baseline)-{'scaleWeights', 'bodyMeasurements', 'preferences'}:
        assert saved[key] == baseline[key], key
    tap('Scale Weight'); tap('View Charts')
    target('About body charts'); capture('chart-first-entry'); tap('Got it')
    target('Body Charts', scroll=False)
    expected_tutorials = sorted(set(baseline['preferences'].get('dismissedTutorials', [])) | {'bodyCharts'})
    saved['preferences']['dismissedTutorials'] = expected_tutorials
    assert state() == saved
    baseline['preferences']['dismissedTutorials'] = expected_tutorials
    adb('shell', 'input', 'keyevent', '4'); target('Body Data'); tap('View Charts')
    assert not any(n.get('text') == 'Got it' for n in nodes()), 'Dismissed tutorial must not reopen on entry'
    tap('About body charts', 'content-desc'); target('Got it')
    adb('shell', 'input', 'keyevent', '4'); target('Body Charts')
    assert state() == saved
    for label, months in [('1W', None), ('1M', 1), ('3M', 3), ('6M', 6), ('1Y', 12), ('All', 0)]:
        tap(label)
        check_range_checked(label)
        if months is None:
            start, end = monday, monday+timedelta(days=6)
        elif months == 0:
            days = [date.fromisoformat(r['date']) for r in saved['scaleWeights']]
            start, end = min(days), max(days)
        else:
            year, month = divmod(first.year*12+first.month-months, 12)
            start, end = date(year, month+1, 1), today.replace(day=calendar.monthrange(today.year, today.month)[1])
        count = sum(start.isoformat() <= r['date'] <= end.isoformat() for r in saved['scaleWeights'])
        target(f'{start} – {end}', scroll=False)
        target(f'Scale Weight chart, {count} saved entries', 'content-desc', scroll=False)
        if label in ('1W', '1M', 'All'):
            capture('weight-'+label.lower())
    # All data remain separately selectable, including same-day readings.
    tap(f'Select Scale Weight {monday} 64 kg', 'content-desc')
    scroll_top()
    check_selection('64 kg', monday)
    capture('weight-selected-row')
    # Tapping the earliest date near its plotted value selects the old 55.25 kg reading.
    open_charts(); tap('All')
    plot = target(f'Scale Weight chart, {len(saved["scaleWeights"])} saved entries', 'content-desc')
    left, top, right, bottom = bounds(plot)
    adb('shell', 'input', 'tap', str(left+15), str(bottom-25))
    check_selection('55.25 kg', fixtures[0][0])
    capture('weight-selected-dot')
    tap('About body charts', 'content-desc'); capture('chart-help'); tap('Got it')
    tap('Scale Weight'); tap('Scale Body Fat'); tap('All')
    fat_count = sum('bodyFatPercent' in r for r in saved['scaleWeights'])
    target(f'Scale Body Fat chart, {fat_count} saved entries', 'content-desc')
    tap(f'Select Scale Body Fat {monday} 0 %', 'content-desc'); scroll_top(); check_selection('0 %', monday)
    capture('fat-zero-selected')
    open_charts(True); tap('All')
    neck_count = sum('neck' in r['centimeters'] for r in saved['bodyMeasurements'])
    if neck_count:
        target(f'Neck chart, {neck_count} saved entries', 'content-desc')
    else:
        target('No entries in this range')
    capture('neck')
    tap('Neck'); tap('Left Bicep')
    target('Recorded values · cm'); target('31.1 cm')
    scroll_top()
    capture('left-bicep')
    tap('Left Bicep'); tap('Right Bicep'); target('34.2 cm')
    assert state() == saved
    units(True); open_charts(); tap('All')
    target(f'Select Scale Weight {monday} {number(64*2.2046226218)} lb', 'content-desc')
    scroll_top()
    capture('weight-pounds')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    target('All', scroll=False)
    capture('chart-font-130')
    tap('About body charts', 'content-desc'); capture('chart-help-130'); tap('Got it')
    for label in ('1W', '1M', '3M', '6M', '1Y', 'All'):
        tap(label)
        check_range_checked(label)
    adb('shell', 'input', 'keyevent', '4'); target('Body Data'); tap('View Charts')
    target('All', scroll=False)
    check_range_checked('All')
    units(baseline['preferences']['pounds'])
    launch(); open_charts(); tap('All')
    assert state()['scaleWeights'] == saved['scaleWeights']
    assert state()['bodyMeasurements'] == saved['bodyMeasurements']
    final = state()
    for key in set(baseline)-{'scaleWeights', 'bodyMeasurements'}:
        assert final[key] == baseline[key], key
    assert photo_integrity(final) == photos_before
    (args.output / 'final-state.json').write_text(json.dumps(final, indent=2))
    result = dict(status='passed', serial=args.serial, offline=True, all_six_ranges=True,
                  first_entry_tutorial_dismissal_and_reentry=True, manual_help_back=True,
                  same_day_readings=True, missing_vs_zero=True, kg_lb_and_cm=True,
                  point_and_row_selection=True, font_recreation=True, range_reopen=True,
                  process_restart=True, unrelated_data_unchanged=True, photo_bytes_unchanged=True,
                  source_trend_parity=False)
    (args.output / 'result.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result, indent=2))
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', font if font != 'null' else '1.0')
    if state()['preferences']['pounds'] != baseline['preferences']['pounds']:
        units(baseline['preferences']['pounds'])
    if wifi in ('1', '2'):
        adb('shell', 'svc', 'wifi', 'enable')
    if mobile == '1':
        adb('shell', 'svc', 'data', 'enable')
