#!/usr/bin/env python3
"""Run the training UI smoke flow through ADB on an explicit disposable emulator.
No source-app/account access; leaves its test workout in Open Factor's history.
"""
import argparse,json,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--media',action='store_true',help='Also check imported original videos offline, all Chin-up camera views and lifecycle pause');p.add_argument('--parity-actions',action='store_true',help='Check global shortcuts, editable session targets, warm-up insertion and live settings');a=p.parse_args()
if not a.serial.startswith('emulator-'):raise SystemExit('Use a disposable emulator for this smoke test')
a.output.mkdir(parents=True,exist_ok=True)
def adb(*args,raw=False):
 r=subprocess.run(['adb','-s',a.serial,*args],stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True,timeout=20)
 return r.stdout if raw else r.stdout.decode()
def nodes():
 for attempt in range(6):
  try:
   result=adb('shell','uiautomator','dump','/sdcard/of-smoke.xml')
   if 'dumped to:' not in result:raise ValueError('Android UI tree is not ready')
   return list(ET.fromstring(adb('shell','cat','/sdcard/of-smoke.xml')).iter('node'))
  except (subprocess.CalledProcessError,ET.ParseError,ValueError):
   if attempt==5:raise
   time.sleep(.5)
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
 nodes()
 (a.output/(name+'.png')).write_bytes(adb('exec-out','screencap','-p',raw=True))
 (a.output/(name+'.xml')).write_text(adb('shell','cat','/sdcard/of-smoke.xml'))
def enter(value,text,attr='text'):
 tap(value,attr)
 focused=[n for n in nodes() if n.get('class')=='android.widget.EditText' and n.get('focused')=='true']
 assert len(focused)==1, 'Expected one focused input'
 old=focused[0].get('text','')
 adb('shell','input','keyevent','123')
 for _ in range(len(old)+1):adb('shell','input','keyevent','67')
 if text:adb('shell','input','text',text)
 focused=[n for n in nodes() if n.get('class')=='android.widget.EditText' and n.get('focused')=='true']
 assert len(focused)==1 and focused[0].get('text','')==text, 'Replacement input differs'
 adb('shell','input','keyevent','4')
def launch():adb('shell','am','start','-n','org.openfactor.app/.MainActivity')
def persisted():
 xml=ET.fromstring(adb('shell','run-as','org.openfactor.app','cat','shared_prefs/open_factor.xml'))
 return json.loads(next(n.text for n in xml if n.get('name')=='data'))
# Clear only a leftover active Open Factor smoke workout, through its UI.
adb('shell','am','force-stop','org.openfactor.app');launch();find('Dashboard')
if any(n.get('text') == 'Resume Workout' for n in nodes()):
 tap('Resume Workout');tap('Workout options','content-desc');tap('Discard Workout');tap('Discard')
 adb('shell','am','force-stop','org.openfactor.app');launch();find('Dashboard')
capture('phone-dashboard')
if a.parity_actions:
 for tab in ['Dashboard','Workout','Exercises','Settings']:
  tap(tab,last=True)
  def center_x(node):
   left,top,right,bottom=map(int,re.findall(r'\d+',node.get('bounds')))
   return (left+right)/2
  plus=center_x(find('Shortcuts','content-desc')[0])
  assert center_x(find('Workout')[-1])<plus<center_x(find('Exercises')[-1]), 'Plus is not in the middle of the navigation pill'
  tap('Shortcuts','content-desc');find('New Program');find('History')
  capture('shortcuts-'+tab.lower());tap('Close shortcuts','content-desc')
 assert 'android.permission.INTERNET' not in adb('shell','dumpsys','package','org.openfactor.app'), 'Installed app still has network permission'
 tap('Dashboard',last=True)
 shortcuts_before=persisted()
 tap('Shortcuts','content-desc');tap('History');find('Workout History');capture('shortcut-history');tap('Back','content-desc')
 tap('Shortcuts','content-desc');tap('New Workout');find('Create Workout');find('Workout name');find('Save Workout');capture('shortcut-new-workout');tap('Back','content-desc')
 assert persisted()==shortcuts_before, 'Canceled shortcut forms changed training data'
 tap('Shortcuts','content-desc');tap('New Program');find('Create Program');capture('shortcut-new-program');tap('Back','content-desc');find('Leave program draft?')
 tap('Keep Draft' if shortcuts_before.get('programDraft') else 'Discard Draft');find('Programs');tap('Back','content-desc')
 assert persisted()==shortcuts_before, 'Opening and closing program shortcut changed prior training data'
 tap('Shortcuts','content-desc');tap('Empty Workout');find('Build your workout');capture('shortcut-empty-workout')
 active_id=persisted()['active']['id'];adb('shell','input','keyevent','4');tap('Shortcuts','content-desc');tap('Resume Workout');find('Build your workout')
 assert persisted()['active']['id']==active_id, 'Resume shortcut replaced the active workout'
else:
 tap('Workout',last=True);tap('New workout','content-desc');tap('Empty Workout')
tap('Add Exercise');tap('Search exercises');adb('shell','input','text','barbell% sbench% spress'.replace('% s','%s'))
adb('shell','input','keyevent','4')  # Expose results on short API-26 emulator viewports.
tap('Add ','content-desc',prefix=True);find('Complete set','content-desc',prefix=True)
if a.parity_actions:
 before=persisted();exercise=before['active']['exercises'][0]
 tap('Targets');find('Workout Targets');enter('Set 1 Reps Min target','12','content-desc');enter('Set 1 Reps Max target','7','content-desc')
 tap('Save');find('Workout Targets');assert persisted()==before, 'Invalid target edit changed saved data';capture('targets-invalid');tap('Cancel')
 assert persisted()==before, 'Canceled target edit changed saved data'
 tap('Targets');enter('Set 1 Reps Min target','6','content-desc');enter('Set 1 Reps Max target','10','content-desc');enter('Set 1 RIR target','2','content-desc')
 enter('Set 2 Reps Min target','12','content-desc');enter('Set 2 Reps Max target','17','content-desc');enter('Set 2 RIR target','0','content-desc');enter('Set Rest Timers (seconds)','137');capture('targets-edited');tap('Save')
 edited=persisted();saved=edited['active']['exercises'][0]
 assert [(s.get('targetRepsMin'),s.get('targetRepsMax'),s.get('targetRir')) for s in saved['sets']]==[(6,10,2),(12,17,0),(None,None,None)]
 assert saved['restSeconds']==137
 for old,new in zip(exercise['sets'],saved['sets']):
  assert all(old.get(k)==new.get(k) for k in ['id','weightKg','reps','rir','completed','warmup']), 'Target edit altered actuals'
 assert all(before.get(k)==edited.get(k) for k in ['history','programs','programDraft'])
 tap('Targets');find('6');capture('targets-reopened');tap('Cancel')
 tap('Warm Up');tap('Add empty warm-up set');warmed=persisted()['active']['exercises'][0]['sets'];assert warmed[0]['warmup'] and warmed[0]['reps']==0 and warmed[1:]==saved['sets'];capture('warmup-added')
 tap('Set 1 options','content-desc',prefix=True);tap('Remove Set');assert persisted()['active']['exercises'][0]['sets']==saved['sets']
 tap('Workout options','content-desc');tap('Workout settings');find('Exercise Auto-Next');capture('live-workout-settings');tap('Done')
 tap('Workout options','content-desc');tap('Gym settings');find('Gym Profiles');capture('live-gym-settings');tap('Back','content-desc');find('Targets')
 assert persisted()['active']==edited['active'], 'Settings navigation changed active workout'
tap('Complete set','content-desc',prefix=True);find('Mark set incomplete','content-desc',prefix=True);capture('phone-session')
tap('Workout options','content-desc');tap('Pause Workout');find('Paused')
paused=persisted()['active']
assert paused['pausedAt']>0
assert paused['selectedExerciseId'] in [e['id'] for e in paused['exercises']]
# Persist the active session across a genuine process restart.
adb('shell','am','force-stop','org.openfactor.app');launch();find('Resume Workout');capture('phone-dashboard-active');tap('Resume Workout')
find('Mark set incomplete','content-desc',prefix=True)
find('Paused')
assert persisted()['active']['pausedAt']==paused['pausedAt'], 'Pause did not survive restart'
tap('Workout options','content-desc');tap('Resume Workout')
assert persisted()['active']['pausedDurationMs']>0, 'Resume did not account for paused time'
assert any(n.get('text','').strip().startswith('Rest ') for n in nodes()), 'Rest timer did not survive restart'
tap('Finish Workout');tap('Finish');find('Workout Complete');capture('phone-summary')
state=persisted()
assert state.get('active') is None
assert len(state['history'])>=1
assert any(s['completed'] for e in state['history'][0]['exercises'] for s in e['sets'])
# Capture the two explicitly requested Nuvio-style surfaces and exercise library.
adb('shell','am','force-stop','org.openfactor.app');launch();tap('Exercises',last=True);find('Search exercises');capture('phone-exercises')
if a.media:
 wifi=adb('shell','settings','get','global','wifi_on').strip()
 mobile=adb('shell','settings','get','global','mobile_data').strip()
 try:
  adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
  for _ in range(10):
   connectivity=adb('shell','dumpsys','connectivity')
   if 'Active default network: none' in connectivity:break
   time.sleep(.5)
  else:raise AssertionError('The emulator still has an active network')
  tap('Search exercises');adb('shell','input','text','chin-up');adb('shell','input','keyevent','4');tap('Chin-Up')
  find('Play exercise video','content-desc');capture('media-photo-preview')
  if a.parity_actions:
   phrase='While standing under a pull-up bar'
   assert any(phrase in n.get('text','') for n in nodes()), 'Expected installed Chin-Up instructions'
   tap('Collapse Setting up','content-desc');find('Expand Setting up','content-desc')
   assert not any(phrase in n.get('text','') for n in nodes()), 'Collapsed instructions remain visible';capture('instructions-collapsed')
   tap('Expand Setting up','content-desc');find('Collapse Setting up','content-desc')
   assert any(phrase in n.get('text','') for n in nodes()), 'Expanded instructions did not return'
  for view in range(1,4):
   tap(f'Camera view {view}','content-desc');find('Play exercise video','content-desc')
   tap('Play exercise video','content-desc');find('android.widget.VideoView','class')
   recording=subprocess.Popen(['adb','-s',a.serial,'shell','screenrecord','--time-limit','5',f'/sdcard/of-media-{view}.mp4'],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
   time.sleep(2);capture(f'media-playing-view-{view}')
   assert recording.wait(timeout=15)==0, 'Screen recording failed'
   adb('pull',f'/sdcard/of-media-{view}.mp4',str(a.output/f'media-playing-view-{view}.mp4'))
   assert not any('unavailable' in n.get('text','').lower() for n in nodes()), 'Video fell back to a photo'
  if a.parity_actions:
   def native_position():
    start=time.monotonic();n=find('Exercise video, view ','content-desc',prefix=True)[0];end=time.monotonic()
    position,duration=map(float,re.findall(r'\d+\.\d+',n.get('content-desc')))
    assert duration>4, 'Native video has no measurable duration'
    return (start+end)/2,position,duration
   assert not any(n.get('class')=='android.widget.SeekBar' or 'Exercise video progress:' in n.get('content-desc','') for n in nodes()), 'Unwanted player bar is visible'
   for label in ['.2X','.5X']:
    assert not any(n.get('content-desc')=='Playback speed '+label for n in nodes()), 'Unwanted slow playback option remains'
   tap('Camera view 1','content-desc');tap('Play exercise video','content-desc');find('android.widget.VideoView','class')
   rates=[]
   for label,low,high in [('1X',.65,1.35),('2.5X',1.8,3.2),('5X',3.8,6.2)]:
    tap('Playback speed '+label,'content-desc');tap('Replay exercise video','content-desc');time.sleep(.2)
    start,first,duration=native_position();time.sleep(.5);end,last,_=native_position()
    assert end-start<duration/5, 'Playback sample spans more than one possible loop'
    delta=(last-first)%duration
    rate=delta/(end-start)
    assert low<rate<high, (label,rate,first,last,end-start)
    rates.append({'selection':label,'native_position_delta_seconds':delta,'wall_seconds':end-start,'observed_rate':rate})
    capture('media-speed-'+label.replace('.',''))
   (a.output/'native-playback-rates.json').write_text(json.dumps(rates,indent=2)+'\n')
   tap('Pause exercise video','content-desc');_,first,duration=native_position();time.sleep(2);_,last,_=native_position()
   assert abs(last-first)<.3, ('Video advanced while explicitly paused',first,last)
   capture('media-paused')
   tap('Playback speed 1X','content-desc');tap('Camera view 3','content-desc');tap('Play exercise video','content-desc');find('android.widget.VideoView','class')
  adb('shell','input','keyevent','3');time.sleep(2);launch()
  find('Play exercise video','content-desc');capture('media-paused-after-background')
  assert not any(n.get('class')=='android.widget.VideoView' for n in nodes()), 'Video continued after backgrounding'
  tap('Play exercise video','content-desc');find('android.widget.VideoView','class')
  adb('shell','input','keyevent','4')
 finally:
  if wifi in ('0','1'):adb('shell','svc','wifi','enable' if wifi=='1' else 'disable')
  if mobile in ('0','1'):adb('shell','svc','data','enable' if mobile=='1' else 'disable')
tap('Settings');find('Settings');capture('phone-settings')
(a.output/'result.json').write_text(json.dumps({'status':'passed','serial':a.serial,'history_count':len(state['history']),'active_session_restart':True,'pause_resume_restart':True,'completed_workout_persisted':True,'offline_media_camera_and_lifecycle_checks':a.media,'shortcuts_targets_warmup_and_live_settings':a.parity_actions},indent=2)+'\n')
print((a.output/'result.json').read_text())
