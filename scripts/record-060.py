#!/usr/bin/env python3
"""Record real emulator interaction; leave the favorites untouched (reorder is cancelled)."""
import subprocess,time
from pathlib import Path
root=Path(__file__).resolve().parents[1]
a='/Users/mfcheer/Library/Android/sdk/platform-tools/adb';base=[a,'-s','emulator-5554']
def adb(*args):return subprocess.check_output([*base,*args])
def key(code,pause=.45,long=False):
 adb('shell','input','keyevent',*(['--longpress'] if long else []),str(code));time.sleep(pause)
adb('shell','am','force-stop','com.jianping.tv');adb('shell','am','start','-n','com.jianping.tv/.ui.MainActivity');time.sleep(1.2)
record=subprocess.Popen([*base,'shell','screenrecord','--time-limit','20','--bit-rate','4000000','/sdcard/still060.mp4'],stdout=subprocess.DEVNULL,stderr=subprocess.PIPE)
time.sleep(.8)
key(22);key(22,.7);key(20);key(20,.8);key(19);key(19,.7)
key(23,.6,True);key(23,.5);key(22,.7);key(4,.6)
for _ in range(6):key(20,.16)
for _ in range(5):key(22,.12)
key(23,.9);key(4,.8)
record.wait(timeout=25)
if record.returncode:raise RuntimeError(record.stderr.read().decode())
folder=root/'docs/recordings';folder.mkdir(exist_ok=True)
adb('pull','/sdcard/still060.mp4',str(folder/'still-060-interactions.mp4'))
print('Recorded real input, no concept animation')
