#!/usr/bin/env python3
"""Check onboarding and floating navigation on a disposable emulator.
Leaves a synthetic local profile; preserves existing workout/program data. Never accesses source apps.
Run with --serial emulator-... --output /private/evidence/path.
"""
import sys,subprocess,time,json,re
from pathlib import Path
src=Path(__file__).with_name('android_program_smoke.py').read_text();exec(src.split("launch();find('Dashboard');assert")[0])
serial=a.serial
before=state();launch();find('Dashboard');tap('Settings');tap('Set up Open Factor');find('Get Started');capture('welcome');tap('Get Started');find('Go to Basics');capture('overview');tap('Go to Basics');find('Optionally sync your data');capture('sync');tap('Health Connect');find('Continue without syncing');tap('Continue without syncing');tap('Next')
enter('Name','Synthetic Setup');enter('Height (cm) · optional','0');tap('Next');assert state()['onboarding']['step']==3;capture('basics-error');enter('Height (cm) · optional','180');enter('Body weight (kg) · optional','75.5');capture('basics')
# Defer and restart without losing this partial setup or training data.
tap('Set up later');find('Finish setup later?');tap('Set up later',last=True);launch();find('Dashboard');assert state()['onboarding']['step']==3 and state()['onboarding']['deferred'];tap('Settings');tap('Set up Open Factor');find('Your training profile');assert state()['onboarding']['name']=='Synthetic Setup';tap('Next');find('Go to Gym & Equipment');capture('gym-overview');tap('Go to Gym & Equipment');find('Choose your equipment');capture('gym');tap('Next');find('Go to Program');tap('Go to Program');find('Create from scratch');capture('program');tap('Set up program later');find('Dashboard')
d=state();assert d['onboarding']['completed'];assert d['preferences']['displayName']=='Synthetic Setup';assert d['onboarding']['heightCm']=='180';assert d['onboarding']['weightKg']=='75.5';assert d['history']==before['history'] and d['programs']==before['programs'] and d.get('programDraft')==before.get('programDraft');launch();find('Dashboard');capture('dashboard')
# Fixed navigation hit areas, content clearance, and a short reusable motion clip.
def nav_targets():
 nodes()
 root=E.fromstring(adb('shell','cat','/sdcard/of-program.xml'));parents={c:p for p in root.iter() for c in p}
 found={}
 for n in root.iter('node'):
  label=n.get('text')
  if label not in ['Dashboard','Workout','Exercises','Settings']:continue
  target=n
  while target in parents and not (target.get('clickable')=='true' or target.get('selected')=='true'):
   target=parents[target]
  if target.get('clickable')=='true' or target.get('selected')=='true':found[label]=bounds(target)
 assert len(found)==4,found
 return found
nav=nav_targets()
# Capture a compact scripted interaction without UI-tree delays inside the recording.
def click_xy(label):
 x,y,r,b=nav[label];adb('shell','input','tap',str((x+r)//2),str((y+b)//2));time.sleep(.45)
record=subprocess.Popen(['adb','-s',serial,'shell','screenrecord','--time-limit','12','/sdcard/of-nav-motion.mp4'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
time.sleep(.8)
click_xy('Settings');adb('shell','input','swipe','540','1400','540','450','450');time.sleep(.4);click_xy('Exercises');adb('shell','input','swipe','540','1400','540','700','400');time.sleep(.4);click_xy('Dashboard');click_xy('Workout');record.wait(timeout=20)
adb('pull','/sdcard/of-nav-motion.mp4',str(a.output/'navigation-motion.mp4'));nodes();capture('workout');tap('Settings');
for _ in range(5):adb('shell','input','swipe','540','1400','540','400','300')
find('Licenses & Attribution');capture('settings-bottom')
# Footer row must be reachable above the anchored navigation.
row=find('Licenses & Attribution')[0];navtop=min(bounds(n)[1] for n in nodes() if n.get('text') in nav)
assert bounds(row)[3]<navtop,(bounds(row),navtop)
now=nav_targets();assert nav==now,(nav,now)
# Disable system animation and prove every destination remains accessible. Always restore the setting.
old=adb('shell','settings','get','global','animator_duration_scale').strip()
try:
 adb('shell','settings','put','global','animator_duration_scale','0');tap('Exercises');find('Search exercises');capture('reduced-motion');assert bounds(find('Search exercises')[0])[3]<min(v[1] for v in nav_targets().values());enter('Search exercises','chin-up');find('Chin-Up');capture('exercise-search');tap('Clear search','content-desc');tap('Dashboard');find('Dashboard')
finally:adb('shell','settings','put','global','animator_duration_scale',old)
(a.output/'result.json').write_text(json.dumps({'status':'passed','serial':serial,'onboarding_flow':True,'defer_restart_resume':True,'validation_recovery':True,'training_data_preserved':True,'fixed_navigation_targets':True,'last_row_clearance':True,'reduced_motion_destinations':True},indent=2)+'\n');print((a.output/'result.json').read_text())
