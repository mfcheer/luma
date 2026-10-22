#!/usr/bin/env python3
"""Build/run a separate native instrumentation APK on the dedicated emulator only."""
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SDK = Path.home() / 'Library/Android/sdk'
BT = SDK / 'build-tools/35.0.0'
ADB = [str(SDK / 'platform-tools/adb'), '-s', 'emulator-5554']
WORK = Path.home() / '.cache/jianping-tools/scroll-edge-probe'
label = sys.argv[1]
assert label.replace('-', '').isalnum(), 'Simple artifact label required'
installed = subprocess.check_output([*ADB, 'shell', 'pm', 'list', 'packages'], text=True)
assert installed.count('package:com.jianping.fixture.app') == 24, 'Dedicated fixture emulator required'
for name in ('classes', 'dex', 'res'):
    (WORK / name).mkdir(parents=True, exist_ok=True)
jar = str(SDK / 'platforms/android-35/android.jar')
subprocess.run(['javac', '-source', '8', '-target', '8', '-classpath', jar, '-d', str(WORK / 'classes'), str(ROOT / 'scripts/ScrollEdgeProbe.java')], check=True)
subprocess.run([str(BT / 'd8'), '--lib', jar, '--output', str(WORK / 'dex'), str(WORK / 'classes/com/jianping/qa/ScrollEdgeProbe.class')], check=True)
(WORK / 'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.jianping.scrollqa"><uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35"/><application android:label="Still scroll probe"/><instrumentation android:name="com.jianping.qa.ScrollEdgeProbe" android:targetPackage="com.jianping.tv" android:functionalTest="true"/></manifest>''')
apk = WORK / 'probe.apk'
subprocess.run([str(BT / 'aapt'), 'package', '-f', '-M', str(WORK / 'AndroidManifest.xml'), '-I', jar, '-S', str(WORK / 'res'), '-F', str(apk)], check=True)
with zipfile.ZipFile(apk, 'a') as z:
    z.write(WORK / 'dex/classes.dex', 'classes.dex')
subprocess.run([str(BT / 'apksigner'), 'sign', '--ks', str(Path.home() / '.android/debug.keystore'), '--ks-pass', 'pass:android', '--key-pass', 'pass:android', str(apk)], check=True)
subprocess.run([*ADB, 'install', '-r', '--no-incremental', str(apk)], check=True)
try:
    subprocess.run([*ADB, 'shell', 'am', 'force-stop', 'com.jianping.tv'], check=True)
    output = subprocess.check_output([*ADB, 'shell', 'am', 'instrument', '-w', '-e', 'label', label, 'com.jianping.scrollqa/com.jianping.qa.ScrollEdgeProbe'], text=True)
    print(output)
    assert 'PASS:' in output and 'failure=' not in output, output
    target = ROOT / 'docs/qa-081/scroll' / label
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists(): shutil.rmtree(target)
    subprocess.run([*ADB, 'pull', '/sdcard/Android/data/com.jianping.tv/files/scroll-probe/' + label, str(target)], check=True)
finally:
    subprocess.run([*ADB, 'uninstall', 'com.jianping.scrollqa'], check=True)
    subprocess.run([*ADB, 'shell', 'am', 'start', '-n', 'com.jianping.tv/.ui.MainActivity'], check=True)
