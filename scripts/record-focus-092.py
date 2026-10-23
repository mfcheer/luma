#!/usr/bin/env python3
"""Capture native emulator navigation at original speed; restore preferences afterward."""
import json
import runpy
import subprocess
import time
from pathlib import Path

q = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key = q['adb'], q['key']
PKG, ROOT = q['PKG'], q['ROOT']
OUT = ROOT / 'docs/qa-092'


def main():
    original_xml, original = q['prefs_xml'](), q['prefs']()
    assert q['BASE'][-1].startswith('emulator-')
    adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/record092-backup.xml')
    recorder = None
    try:
        xml = q['ET'].fromstring(original_xml)
        for n in list(xml):
            if n.get('name') in ('appearance', 'showAppNames'): xml.remove(n)
        q['ET'].SubElement(xml, 'string', name='appearance').text = 'light'
        q['ET'].SubElement(xml, 'boolean', name='showAppNames', value='false')
        q['seed'](xml); q['start'](); key(22, .9)
        assert q['desc']() == 'YouTube'
        q['image']().save(OUT / 'light-home.png')
        # Native dark fixture has a measurable boundary against the light surface.
        for _ in range(3): key(22, .2)
        time.sleep(.6)
        image = q['image']()
        region = image.crop((1470, 160, 1840, 400))
        pixels = [(x,y) for y in range(region.height) for x in range(region.width)
                  if (lambda r,g,b: r<100 and g<120 and 35<b<160)(*region.getpixel((x,y)))]
        assert pixels
        width = max(x for x,y in pixels)-min(x for x,y in pixels)+1
        assert 270 <= width <= 276, ('Expected 1.14x of 240px resting width', width)
        (OUT / 'focus-geometry.json').write_text(json.dumps({
            'restingWidthPixels':240,'selectedWidthPixels':width,'scale':width/240,
            'measuredFrom':'native emulator screenshot; temporary generic fixture',
            'physicalTvTouched':False},indent=2)+'\n')
        q['start'](); key(22, .8)
        adb('shell', 'rm', '-f', '/sdcard/still-focus-092.mp4')
        recorder = subprocess.Popen([*q['BASE'], 'shell', 'screenrecord', '--time-limit', '40',
            '--bit-rate', '6000000', '/sdcard/still-focus-092.mp4'],
            stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        time.sleep(.6)
        recorder_pid = adb('shell', 'pidof', 'screenrecord')
        assert recorder_pid.isdigit(), 'Only this emulator recording may be running'
        for code in (22, 22, 21, 21): key(code, .8)
        adb('shell', 'input', 'keyevent', *(['22', '21'] * 5)); time.sleep(.8)
        for _ in range(3): key(20, .5)
        key(19, .5); key(4, .7)
        key(19, .3); key(23, .8)
        adb('shell', 'input', 'text', 'YouTube'); time.sleep(.6)
        key(4, .7); key(23, .7, long=True); key(4, .6); key(4, .6)
        key(22, 1.0)
        assert q['desc']() == 'YouTube', 'Finish on a stable Home focus, outside activity transitions'
        time.sleep(.8)
        adb('shell', 'kill', '-2', recorder_pid)
        recorder.wait(timeout=12)
        assert recorder.returncode == 0, recorder.stderr.read().decode()
        adb('pull', '/sdcard/still-focus-092.mp4', str(OUT / 'focus-navigation.mp4'))
    finally:
        if recorder is not None and recorder.poll() is None:
            recorder.terminate(); recorder.wait(timeout=5)
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'run-as', PKG, 'cp', 'files/record092-backup.xml', 'shared_prefs/launcher.xml')
        adb('shell', 'run-as', PKG, 'rm', 'files/record092-backup.xml')
        q['start'](); assert q['prefs']() == original
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',
        str(OUT/'focus-navigation.mp4'),'-vf','fps=0.6,scale=480:-1,tile=4x2',
        '-frames:v','1',str(OUT/'motion-contact.jpg')],check=True)
    media = subprocess.check_output(['ffprobe','-v','error','-show_entries',
        'format=duration:stream=codec_name,width,height','-of','json',str(OUT/'focus-navigation.mp4')],text=True)
    (OUT/'recording-media.json').write_text(media)
    print('PASS: measured focus geometry; native navigation recording; stable final Home; preferences restored')


if __name__ == '__main__':
    main()
