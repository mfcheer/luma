#!/usr/bin/env python3
"""Build/run original-desktop integration checks on the dedicated emulator only."""
import os
import shutil
import subprocess
import zipfile
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
SDK = Path.home()/'Library/Android/sdk'
BT = SDK/'build-tools/35.0.0'
JAVA = Path.home()/'Library/Java/JavaVirtualMachines/openjdk-21/Contents/Home/bin'
ADB = [str(SDK/'platform-tools/adb'),'-s','emulator-5554']
WORK = ROOT/'output/other-home-095/probe'
OUT = ROOT/'docs/qa-095'
OUT.mkdir(parents=True, exist_ok=True)
def run(args,**kwargs):return subprocess.check_output([str(a) for a in args],text=True,**kwargs)
assert run(ADB+['shell','getprop','ro.build.version.sdk']) == '33\n'
for name in ('classes','dex','res'):(WORK/name).mkdir(parents=True,exist_ok=True)
jar=SDK/'platforms/android-35/android.jar'
classes=ROOT/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
run([JAVA/'javac','-source','8','-target','8','-classpath',str(jar)+os.pathsep+str(classes),'-d',WORK/'classes',ROOT/'scripts/OtherHomeProbe.java'])
run([BT/'d8','--lib',jar,'--classpath',classes,'--output',WORK/'dex',*sorted((WORK/'classes').rglob('*.class'))])
(WORK/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.jianping.homeqa"><uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35"/><application android:label="Still search QA"/><instrumentation android:name="com.jianping.qa.OtherHomeProbe" android:targetPackage="com.jianping.tv" android:functionalTest="true"/></manifest>')
apk=WORK/'probe.apk'
run([BT/'aapt','package','-f','-M',WORK/'AndroidManifest.xml','-I',jar,'-S',WORK/'res','-F',apk])
with zipfile.ZipFile(apk,'a') as z:z.write(WORK/'dex/classes.dex','classes.dex')
run([BT/'apksigner','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','pass:android','--key-pass','pass:android',apk])
run(ADB+['install','-r','--no-incremental',apk])
try:
 run(ADB+['shell','am','force-stop','com.jianping.tv'])
 output=run(ADB+['shell','am','instrument','-w','com.jianping.homeqa/com.jianping.qa.OtherHomeProbe'])
 (OUT/'home-integration.txt').write_text(output)
 print(output)
 assert 'PASS:' in output and 'failure=' not in output
finally:
 run(ADB+['uninstall','com.jianping.homeqa'])
