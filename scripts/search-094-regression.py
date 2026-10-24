#!/usr/bin/env python3
"""Run original search/long-menu contracts with local usage preserved on the fixture emulator."""
import os
import runpy
import subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASE=[str(Path.home()/'Library/Android/sdk/platform-tools/adb'),'-s','emulator-5554']
PKG='com.jianping.tv';USAGE='shared_prefs/app_usage_v1.xml'
def adb(*args):return subprocess.check_output([*BASE,*args],text=True).strip()
assert adb('shell','pm','list','packages').count('package:com.jianping.fixture.app')==24
had_usage=subprocess.run([*BASE,'shell','run-as',PKG,'test','-f',USAGE]).returncode==0
if had_usage:adb('shell','run-as',PKG,'cp',USAGE,'files/qa094-regression-usage.xml')
os.environ['STILL_EXPECT_VERSION']='0.9.4'
try:
 for filename,folder in [('search-090-smoke.py','search-regression'),('search-menu-091-smoke.py','menu-regression')]:
  os.environ['STILL_QA_OUT']='docs/qa-094/'+folder
  (ROOT/os.environ['STILL_QA_OUT']).mkdir(parents=True,exist_ok=True)
  module=runpy.run_path(str(ROOT/'scripts'/filename))
  helper=module['q'];seed=helper['seed']
  def clean_seed(xml,seed=seed):
   seed(xml)
   adb('shell','run-as',PKG,'rm','-f',USAGE)
  helper['seed']=clean_seed
  adb('shell','am','force-stop',PKG)
  adb('shell','run-as',PKG,'rm','-f',USAGE)
  module['main']()
finally:
 adb('shell','am','force-stop',PKG)
 if had_usage:
  adb('shell','run-as',PKG,'cp','files/qa094-regression-usage.xml',USAGE)
  adb('shell','run-as',PKG,'rm','files/qa094-regression-usage.xml')
 else:adb('shell','run-as',PKG,'rm','-f',USAGE)
 adb('shell','am','start','-n',PKG+'/.ui.MainActivity')
print('PASS: original usage aggregates restored after legacy UI/menu regression',flush=True)
