#!/usr/bin/env python3
"""Build/run a native instrumentation helper only on the dedicated 24-fixture emulator."""
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
WORK = ROOT/'output/search-094/usage-probe'
OUT = ROOT/'docs/qa-094'
def run(args,**kwargs):return subprocess.check_output([str(a) for a in args],text=True,**kwargs)
assert run(ADB+['shell','pm','list','packages']).count('package:com.jianping.fixture.app')==24
for name in ('classes','dex','res'):(WORK/name).mkdir(parents=True,exist_ok=True)
jar=SDK/'platforms/android-35/android.jar'
classes=ROOT/'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
run([JAVA/'javac','-source','8','-target','8','-classpath',str(jar)+os.pathsep+str(classes),'-d',WORK/'classes',ROOT/'scripts/SearchUsageProbe.java'])
run([BT/'d8','--lib',jar,'--classpath',classes,'--output',WORK/'dex',*sorted((WORK/'classes').rglob('*.class'))])
(WORK/'AndroidManifest.xml').write_text('<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.jianping.searchqa"><uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35"/><application android:label="Still search QA"/><instrumentation android:name="com.jianping.qa.SearchUsageProbe" android:targetPackage="com.jianping.tv" android:functionalTest="true"/></manifest>')
apk=WORK/'probe.apk'
run([BT/'aapt','package','-f','-M',WORK/'AndroidManifest.xml','-I',jar,'-S',WORK/'res','-F',apk])
with zipfile.ZipFile(apk,'a') as z:z.write(WORK/'dex/classes.dex','classes.dex')
run([BT/'apksigner','sign','--ks',Path.home()/'.android/debug.keystore','--ks-pass','pass:android','--key-pass','pass:android',apk])
run(ADB+['install','-r','--no-incremental',apk])
try:
 run(ADB+['shell','am','force-stop','com.jianping.tv'])
 output=run(ADB+['shell','am','instrument','-w','com.jianping.searchqa/com.jianping.qa.SearchUsageProbe'])
 (OUT/'usage-integration.txt').write_text(output)
 print(output)
 assert 'PASS:' in output and 'failure=' not in output
finally:
 run(ADB+['uninstall','com.jianping.searchqa'])
