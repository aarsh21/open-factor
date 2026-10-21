#!/usr/bin/env python3
"""Run the training UI smoke flow through ADB on an explicit disposable emulator.
No source-app/account access; leaves its test workout in Open Factor's history.
"""
import argparse,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):raise SystemExit('Use a disposable emulator for this smoke test')
a.output.mkdir(parents=True,exist_ok=True)
def adb(*args,raw=False):
 r=subprocess.run(['adb','-s',a.serial,*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=20)
 return r.stdout if raw else r.stdout.decode()
def nodes():
 adb('shell','uiautomator','dump','/sdcard/of-smoke.xml')
 return list(ET.fromstring(adb('shell','cat','/sdcard/of-smoke.xml')).iter('node'))
def find(value,attr='text',prefix=False):
 for attempt in range(6):
  ns=nodes()
  matches=[n for n in ns if (n.get(attr,'').startswith(value) if prefix else n.get(attr)==value)]
  if matches:return matches
  time.sleep(.3)
 (a.output/'failure.xml').write_text(ET.tostring(ET.fromstring(adb('shell','cat','/sdcard/of-smoke.xml')),encoding='unicode'))
 raise AssertionError(f'Could not find {attr}={value!r}')
def tap(value,attr='text',prefix=False,last=False):
 n=find(value,attr,prefix)[-1 if last else 0]; x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def capture(name):
 (a.output/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',raw=True))
 (a.output/(name+'.xml')).write_text(adb('shell','cat','/sdcard/of-smoke.xml'))
def launch():adb('shell','am','start','-n','org.openfactor.app/.MainActivity')
launch();find('Dashboard');capture('phone-dashboard')
tap('Workout');tap('New workout','content-desc');tap('Empty Workout')
tap('Add Exercise');tap('Search exercises');adb('shell','input','text','barbell% sbench% spress'.replace('% s','%s'))
tap('Add ','content-desc',prefix=True);find('Complete set','content-desc')
tap('Complete set','content-desc');find('Mark set incomplete','content-desc');capture('phone-session')
# Persist the active session across a genuine process restart.
adb('shell','am','force-stop','org.openfactor.app');launch();tap('Workout');tap('Resume Workout')
find('Mark set incomplete','content-desc')
tap('Finish Workout');tap('Finish');find('Workout Complete');capture('phone-summary')
xml=ET.fromstring(adb('shell','run-as','org.openfactor.app','cat','shared_prefs/open_factor.xml'))
state=json.loads(next(n.text for n in xml if n.get('name')=='data'))
assert state.get('active') is None
assert len(state['history'])>=1
assert any(s['completed'] for e in state['history'][0]['exercises'] for s in e['sets'])
# Capture the two explicitly requested Nuvio-style surfaces and exercise library.
adb('shell','am','force-stop','org.openfactor.app');launch();tap('Exercises',last=True);find('Search exercises');capture('phone-exercises')
tap('Settings');find('Settings');capture('phone-settings')
(a.output/'result.json').write_text(json.dumps({'status':'passed','serial':a.serial,'history_count':len(state['history']),'active_session_restart':True,'completed_workout_persisted':True},indent=2)+'\n')
print((a.output/'result.json').read_text())
