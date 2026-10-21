#!/usr/bin/env python3
"""Exercise a manual program through native UI on a disposable emulator.
Leaves one explicitly synthetic program/workout in Open Factor. Never accesses source apps.
"""
import argparse,json,re,subprocess,time,xml.etree.ElementTree as E
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
if not a.serial.startswith('emulator-'):raise SystemExit('Use a disposable emulator')
a.output.mkdir(parents=True,exist_ok=True)
def adb(*args,raw=False):
 r=subprocess.run(['adb','-s',a.serial,*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=25)
 return r.stdout if raw else r.stdout.decode()
def nodes():
 for attempt in range(8):
  try:
   result=adb('shell','uiautomator','dump','/sdcard/of-program.xml')
   if 'dumped to:' not in result:raise ValueError('UI tree not ready')
   return list(E.fromstring(adb('shell','cat','/sdcard/of-program.xml')).iter('node'))
  except (subprocess.CalledProcessError,E.ParseError,ValueError):
   if attempt==7:raise
   time.sleep(.5)
def find(t,attr='text',prefix=False):
 for _ in range(6):
  ns=nodes();found=[n for n in ns if (n.get(attr,'').startswith(t) if prefix else n.get(attr,'').strip()==t)]
  if found:return found
  time.sleep(.3)
 (a.output/'failure.xml').write_text(adb('shell','cat','/sdcard/of-program.xml'));raise AssertionError((t,attr))
def tap(t,attr='text',prefix=False,last=False):
 n=find(t,attr,prefix)[-1 if last else 0];x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','tap',str((x+r)//2),str((y+b)//2))
def enter(t,value,attr='text'):
 tap(t,attr);adb('shell','input','keyevent','123',*(['67']*25));adb('shell','input','text',str(value).replace(' ','%s'));adb('shell','input','keyevent','4')
def capture(name):
 nodes();(a.output/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',raw=True));(a.output/(name+'.xml')).write_text(adb('shell','cat','/sdcard/of-program.xml'))
def state():
 root=E.fromstring(adb('shell','run-as','org.openfactor.app','cat','shared_prefs/open_factor.xml'));return json.loads(next(n.text for n in root if n.get('name')=='data'))
def launch():
 adb('shell','am','force-stop','org.openfactor.app');adb('shell','am','start','-n','org.openfactor.app/.MainActivity')
def library():
 tap('Workout',last=True);tap('Programs')
launch();find('Dashboard');assert state().get('active') is None,'Finish/discard active test workout first'
library()
for old in list(state().get('programs',[])):
 if old['name'].startswith('Synthetic Program '):
  tap('Delete '+old['name'],'content-desc');tap('Delete')
if state().get('programDraft'):
 tap('Discard Draft')
tap('Create program','content-desc')
name='Synthetic Program '+str(int(time.time()))
enter('Name',name);tap('Next');tap('Program color 5','content-desc');tap('Next');find('Rest Day');capture('program-rest')
tap('Add Day',prefix=True);tap('Add Exercises');tap('Search exercises');adb('shell','input','text','chin-up');adb('shell','input','keyevent','4');tap('Add ','content-desc',prefix=True)
capture('program-exercise')
for _ in range(4):
 if any(n.get('text')=='7 Cycles' for n in nodes()):break
 adb('shell','input','swipe','540','1300','540','700','300')
else:raise AssertionError('Program target card not found')
tap('7 Cycles');find('Target set 1 minimum reps','content-desc')
enter('Target set 1 minimum reps','6','content-desc');enter('Target set 1 maximum reps','10','content-desc');enter('Target set 1 RIR','2','content-desc');enter('Rest timer override (seconds)','45');capture('program-targets');tap('Save')
draft=state()['programDraft'];assert len(draft['days'])==2;planned=draft['days'][1]['exercises'][0]
assert len(planned['cycles'])==7
assert all(c['sets'][0]['repsMin']==6 and c['sets'][0]['repsMax']==10 and c['sets'][0]['rir']==2 and c['restSeconds']==45 for c in planned['cycles'])
tap('7 Cycles');tap('Cycle 2');enter('Target set 1 minimum reps','8','content-desc');enter('Target set 1 maximum reps','12','content-desc');enter('Target set 1 RIR','1','content-desc');tap('Cycle 3');tap('Save')
cycles=state()['programDraft']['days'][1]['exercises'][0]['cycles']
assert cycles[1]['sets'][0]['repsMin']==8 and cycles[1]['sets'][0]['repsMax']==12 and cycles[1]['sets'][0]['rir']==1
assert cycles[0]['sets'][0]['repsMin']==6 and cycles[2]['sets'][0]['repsMin']==6
tap('Rename');enter('Name','Upper');tap('Save');tap('Save to Library');find(name);tap('Activate Program')
program=next(p for p in state()['programs'] if p['name']==name);assert state()['activeProgramId']==program['id']
launch();library();find(name);capture('program-library');tap('Cycle 2');tap('Start Upper')
w=state()['active'];assert w['programId']==program['id'] and w['programCycle']==1
s=w['exercises'][0]['sets'][0];assert s['reps']==8 and s['targetRepsMin']==8 and s['targetRepsMax']==12 and s['targetRir']==1 and s.get('rir') is None
find('Targets');tap('Targets');find('Set 1: 8–12 reps · 1 RIR');capture('program-session-targets');tap('Done');tap('Complete set 1','content-desc',prefix=True)
w=state()['active'];remaining=(w['restUntil']-int(time.time()*1000))/1000;assert 0<remaining<=45
capture('program-session');tap('Finish Workout');tap('Finish');find('Workout Complete');capture('program-summary')
w=state()['history'][0];assert w['programId']==program['id'] and w['exercises'][0]['sets'][0]['completed']
launch();library();find(name);assert state().get('active') is None
(a.output/'result.json').write_text(json.dumps({'status':'passed','serial':a.serial,'synthetic_program':name,'draft_targets_seven_cycles':True,'cycle_switch_preserves_edits':True,'library_activation_restart':True,'planned_targets_to_session':True,'exercise_rest_override':True,'completed_program_history':True},indent=2)+'\n');print((a.output/'result.json').read_text())
