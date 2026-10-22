#!/usr/bin/env python3
"""Dedicated 28-app emulator: cold preparation, cached restart, warm return and cache recovery."""
import hashlib
import json
import re
import runpy
import subprocess
import tempfile
import time
from pathlib import Path

qa = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key, tree, prefs = (qa[n] for n in ('adb','key','tree','prefs'))
PKG = qa['PKG']; OUT = qa['ROOT'] / 'docs/qa-081'; OUT.mkdir(exist_ok=True)
BASE = qa['BASE']
CACHE = 'cache/catalog-v1'
original = prefs()
assert len(json.loads(original['appOrder'])) == 28
assert sum('com.jianping.fixture.' in i for i in json.loads(original['appOrder'])) == 24
assert 'versionName=0.8.1' in adb('shell','dumpsys','package',PKG)
results = {}


def pid():
    result = subprocess.run([*BASE, 'shell', 'pidof', PKG], capture_output=True, text=True)
    return result.stdout.strip()


def logs(): return adb('logcat','-d','-s','StillCatalog:I')


def wait_refresh():
    for _ in range(100):
        log = logs()
        if 'Unable to refresh' in log: raise AssertionError(log)
        matches = re.findall(r'refresh apps=(\d+) prepared=(\d+) reused=(\d+) queryMs=(\d+) artworkMs=(\d+)',log)
        if matches:
            v = list(map(int,matches[-1]))
            return dict(zip(('apps','prepared','reused','queryMs','artworkMs'),v))
        time.sleep(.1)
    raise AssertionError('Catalog did not finish: '+logs())


def wait_home():
    for _ in range(8):
        f = qa['focused']()
        if f.get('package') == PKG and f.get('long-clickable') == 'true': return
        time.sleep(.2)
    raise AssertionError('No launchable home card')


def wait_index():
    for _ in range(80):
        try:
            index=json.loads(adb('shell','run-as',PKG,'cat',CACHE+'/catalog.json'))
            if len(index['apps'])==28: return index
        except (subprocess.CalledProcessError, ValueError): pass
        time.sleep(.15)
    raise AssertionError('Atomic snapshot was not committed')


def boot(name, prepared, disk):
    adb('shell','am','force-stop',PKG)
    adb('logcat','-c')
    adb('shell','am','start','-W','-n',PKG+'/.ui.MainActivity')
    result=wait_refresh();wait_home()
    assert result['apps']==28 and result['prepared']==prepared, result
    assert ('diskReady' in logs()) == disk, logs()
    results[name]=result
    (OUT/(name+'-log.txt')).write_text(logs())
    return result


try:
    adb('shell','am','force-stop',PKG)
    adb('shell','run-as',PKG,'rm','-rf',CACHE)
    boot('fresh',28,False);wait_index()
    print('PASS: empty-cache preparation completes; all 28 launchers and atomic disk snapshot ready',flush=True)

    key(22);assert qa['desc']()=='YouTube'
    focused=qa['focused']().get('bounds');before=logs();process_id=pid()
    for _ in range(4):
        adb('shell','am','start','-W','-n','com.jianping.fixture.app24/com.jianping.fixture.MainActivity')
        assert any(n.get('package')=='com.jianping.fixture.app24' for n in tree().iter('node'))
        key(4,.5);wait_home()
        assert qa['desc']()=='YouTube' and qa['focused']().get('bounds')==focused
        assert pid()==process_id
        assert logs()==before, 'A warm return must not rescan or preprocess artwork'
    (OUT/'home.png').write_bytes(subprocess.check_output([*BASE,'exec-out','screencap','-p']))
    print('PASS: 4 launch/Back cycles keep the same process, exact focused card/bounds and zero catalog work',flush=True)

    boot('disk',0,True)
    # Verify a genuine stopped-process kill while another application is foreground.
    key(22)
    for _ in range(3): key(20)
    bookmark=qa['desc']();bounds=qa['focused']().get('bounds')
    adb('shell','am','start','-W','-n','com.jianping.fixture.app24/com.jianping.fixture.MainActivity')
    tree();adb('logcat','-c');adb('shell','am','kill',PKG)
    for _ in range(20):
        if not pid(): break
        time.sleep(.1)
    assert not pid(), 'Background process was not killed'
    key(4,.6);r=wait_refresh();wait_home()
    assert r['prepared']==0 and r['reused']==28 and 'diskReady' in logs()
    assert qa['desc']()==bookmark and qa['focused']().get('bounds')==bounds, (bookmark,qa['desc'](),bounds,qa['focused']().get('bounds'))
    results['background-process-death']=r
    (OUT/'background-process-death-log.txt').write_text(logs())
    print('PASS: process death behind another app restores cached wall, exact selected card and scroll on Back',flush=True)

    index=wait_index();item=index['apps'][0]
    cache_key=hashlib.sha256(('1:'+item['id']+':'+item['fingerprint']).encode()).hexdigest()
    adb('shell','am','force-stop',PKG)
    adb('shell','run-as',PKG,'rm',CACHE+'/'+cache_key+'.png')
    boot('one-missing-card',1,False);wait_index()
    print('PASS: one missing bitmap rebuilds only that app; remaining 27 visuals are reused',flush=True)

    adb('shell','am','force-stop',PKG)
    with tempfile.NamedTemporaryFile() as f:
        f.write(b'invalid json');f.flush();adb('push',f.name,'/data/local/tmp/still081-bad-index')
    adb('shell','run-as',PKG,'cp','/data/local/tmp/still081-bad-index',CACHE+'/catalog.json')
    boot('corrupt-index',0,False);wait_index()
    print('PASS: corrupt index recovers from per-app assets without decoding icons again',flush=True)

    for k in ('appOrder','showAppNames','bootEnabled','appearance'):
        assert prefs().get(k)==original.get(k),k
    (OUT/'timings.json').write_text(json.dumps(results,ensure_ascii=False,indent=2))
    print('PASS: all original order, name, boot and appearance preferences preserved',flush=True)
except BaseException:
    qa['image']().save(OUT/'cache-failure.png')
    (OUT/'cache-failure-log.txt').write_text(logs())
    raise
finally:
    adb('shell','rm','-f','/data/local/tmp/still081-bad-index')
    adb('shell','am','start','-n',PKG+'/.ui.MainActivity')
