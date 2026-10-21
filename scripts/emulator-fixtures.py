#!/usr/bin/env python3
"""Install 24 clearly named test apps on emulator-5554 only, for large-grid QA.
Requires JDK, Android SDK 35 and an existing emulator-5554.
Never shipped in the launcher APK.
"""
import subprocess, zipfile, shutil
from pathlib import Path
base=Path.home()/'.cache/jianping-tools/fixtures'
sdk=Path.home()/'Library/Android/sdk'; bt=sdk/'build-tools/35.0.0'; adb=sdk/'platform-tools/adb'
for folder in ['src/com/jianping/fixture','classes','dex','res/drawable']:
    (base/folder).mkdir(parents=True,exist_ok=True)
source=base/'src/com/jianping/fixture/MainActivity.java'
source.write_text('package com.jianping.fixture; public class MainActivity extends android.app.Activity { @Override public void onCreate(android.os.Bundle b) { super.onCreate(b); android.widget.TextView t=new android.widget.TextView(this); t.setText("Emulator fixture - press Back"); t.setTextSize(30); t.setGravity(17); setContentView(t); } }')
shutil.copyfile(Path(__file__).resolve().parents[1]/'app/src/main/res/drawable/apps_icon.xml',base/'res/drawable/apps_icon.xml')
subprocess.run(['javac','-source','8','-target','8','-classpath',str(sdk/'platforms/android-35/android.jar'),'-d',str(base/'classes'),str(source)],check=True)
subprocess.run([str(bt/'d8'),'--lib',str(sdk/'platforms/android-35/android.jar'),'--output',str(base/'dex'),str(base/'classes/com/jianping/fixture/MainActivity.class')],check=True)
for i in range(1,25):
    manifest=f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.jianping.fixture.app{i:02d}" android:versionCode="1"><uses-sdk android:minSdkVersion="23" android:targetSdkVersion="35"/><application android:label="测试应用 {i:02d}" android:icon="@drawable/apps_icon"><activity android:name="com.jianping.fixture.MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter></activity></application></manifest>'''
    (base/'AndroidManifest.xml').write_text(manifest)
    apk=base/f'fixture-{i:02d}.apk'
    subprocess.run([str(bt/'aapt'),'package','-f','-M',str(base/'AndroidManifest.xml'),'-I',str(sdk/'platforms/android-35/android.jar'),'-S',str(base/'res'),'-F',str(apk)],check=True,stdout=subprocess.DEVNULL)
    with zipfile.ZipFile(apk,'a') as z:z.write(base/'dex/classes.dex','classes.dex')
    subprocess.run([str(bt/'apksigner'),'sign','--ks',str(Path.home()/'.android/debug.keystore'),'--ks-pass','pass:android','--key-pass','pass:android',str(apk)],check=True,stdout=subprocess.DEVNULL)
    subprocess.run([str(adb),'-s','emulator-5554','install','-r',str(apk)],check=True,stdout=subprocess.DEVNULL)
print('Installed 24 emulator-only fixture apps')
