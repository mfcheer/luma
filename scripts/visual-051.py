#!/usr/bin/env python3
"""Capture normalized light/dark icon states on the dedicated emulator, after still-smoke.py."""
import subprocess, time
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/'docs/audit-home'
a='/Users/mfcheer/Library/Android/sdk/platform-tools/adb'
def adb(*args):return subprocess.check_output([a,'-s','emulator-5554',*args])
def key(n):adb('shell','input','keyevent',str(n));time.sleep(.8)
def shot(name):
 (p/name).write_bytes(adb('exec-out','screencap','-p'))
adb('shell','am','force-stop','com.jianping.tv');adb('shell','am','start','-n','com.jianping.tv/.ui.MainActivity');time.sleep(1.2)
shot('play-store-051.png')
key(22);shot('settings-icon-051.png')
key(22);shot('youtube-051.png');time.sleep(1);shot('youtube-idle-051.png')
key(22);shot('square-icon-051.png')
key(21);shot('after-051.png')
