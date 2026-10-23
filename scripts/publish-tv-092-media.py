#!/usr/bin/env python3
"""Encode the verified native TV capture for GitHub Pages; no UI retouching."""
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'output/real-tv-presentation-092'
DEST = ROOT / 'website/assets'
summary = json.loads((SOURCE/'capture-summary.json').read_text())
assert summary['completed'] and summary['version']=='0.9.2'
assert summary['finalPowerState']=='Asleep' and summary['apps']==41
original = json.loads((DEST/'media.json').read_text())
assert hashlib.sha256((DEST/'comparison-xiaomi.webp').read_bytes()).hexdigest()==original['comparison']['files']['comparison-xiaomi.webp']
shots = ('home-light','home-dark','scrolled-light','scrolled-dark','settings-light','settings-dark','menu-light','search-light')
for name in shots:
    im = Image.open(SOURCE/(name+'.png'))
    assert im.size==(1920,1080)
    im.save(DEST/(name+'.webp'),'WEBP',quality=92,method=6)
shutil.copyfile(DEST/'home-light.webp',DEST/'video-poster.webp')
clips = [SOURCE/(n+'.mp4') for n in ('tour-light','tour-search','tour-dark')]
assert all(p.exists() for p in clips)
playlist=SOURCE/'concat.txt'
playlist.write_text(''.join("file '"+str(p).replace("'", "'\\''")+"'\n" for p in clips))
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(playlist),'-an','-c:v','libx264','-crf','23','-preset','medium','-pix_fmt','yuv420p','-vsync','vfr','-movflags','+faststart',str(DEST/'still-tv-demo.mp4')],check=True)
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-i',str(clips[0]),'-t','30','-filter_complex','fps=10,scale=768:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=3','-loop','0',str(DEST/'still-tv-preview.gif')],check=True)
def digest(name): return hashlib.sha256((DEST/name).read_bytes()).hexdigest()
probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-show_entries','format=duration,size:stream=codec_name,width,height','-of','json',str(DEST/'still-tv-demo.mp4')],text=True))
meta={k:original[k] for k in ('device','android','api','applicationCount','sourceResolution')}
meta['screenshots']={'version':'0.9.2','captureDate':'2026-10-23','source':'Direct ADB screencap on the real television; WebP encoding only','files':{n+'.webp':digest(n+'.webp') for n in (*shots,'video-poster')}}
meta['legacyScreenshots']={'version':'0.8.1','captureDate':'2026-10-22','files':['moving-light.webp'],'usage':'Historical unused move draft; not shown on the current website'}
meta['animatedPreview']={'file':'still-tv-preview.gif','version':'0.9.2','captureDate':'2026-10-23','source':'Native light-theme clip (up to 30 seconds); 10 fps, 768px wide, original speed','sha256':digest('still-tv-preview.gif')}
meta['video']={**probe,'file':'still-tv-demo.mp4','version':'0.9.2','captureDate':'2026-10-23','source':'Direct ADB screenrecord on the real television','sha256':digest('still-tv-demo.mp4')}
meta['videoEdit']='Three native clips (light navigation, search menu, dark navigation) joined at original playback speed; no frame interpolation; audio omitted'
comparison=original['comparison']; comparison.update(stillVersion='0.9.2',stillSource='Full Still 0.9.2 screenshot captured on the same TV on 2026-10-23',source='Existing user-approved full native screenshot retained; updated full Still screenshot from same real TV; WebP encoding only')
comparison['files']['home-light.webp']=digest('home-light.webp'); meta['comparison']=comparison
(DEST/'media.json').write_text(json.dumps(meta,indent=2,ensure_ascii=False)+'\n')
# Content-addressed URLs prevent previously cached screenshots/video from surviving an update.
for page_name in ('index.html','site.js'):
    page=ROOT/'website'/page_name
    text=page.read_text()
    for name in [n+'.webp' for n in (*shots,'video-poster')]+['still-tv-demo.mp4']:
        text=re.sub(re.escape(name)+r'\?v=[^"\s]+',name+'?v=092-'+digest(name)[:12],text)
    page.write_text(text)
page=ROOT/'website/index.html'
page.write_text(re.sub(r'site\.js\?v=[^"\s]+','site.js?v=092-'+hashlib.sha256((ROOT/'website/site.js').read_bytes()).hexdigest()[:12],page.read_text()))
shutil.copyfile(SOURCE/'capture-summary.json',ROOT/'docs/qa-092/tv-media.json')
print(json.dumps({'video':probe,'screenshots':len(shots),'nativeComparisonUnchanged':True},indent=2))
