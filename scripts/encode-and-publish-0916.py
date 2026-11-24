#!/usr/bin/env python3
"""Encode the recorded TV video and preview GIF, update media.json and cache-busters."""
import hashlib
import json
import re
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'website/assets'
OUT = ROOT / 'output/website-tv-0916'
raw_mp4 = OUT / 'raw-still-tv-demo.mp4'
assert raw_mp4.exists(), f"{raw_mp4} does not exist"

print("=== Step 5: Encode Demo MP4 & Preview GIF ===", flush=True)
final_mp4 = DEST / 'still-tv-demo.mp4'
subprocess.run([
    'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
    '-i', str(raw_mp4),
    '-an',
    '-c:v', 'libx264',
    '-crf', '23',
    '-preset', 'veryfast',
    '-pix_fmt', 'yuv420p',
    '-movflags', '+faststart',
    str(final_mp4)
], check=True)
print(f"Encoded {final_mp4} ({final_mp4.stat().st_size} bytes)", flush=True)

final_gif = DEST / 'still-tv-preview.gif'
subprocess.run([
    'ffmpeg', '-hide_banner', '-loglevel', 'error', '-y',
    '-i', str(raw_mp4),
    '-t', '16',
    '-filter_complex', 'fps=10,scale=768:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128[p];[b][p]paletteuse=dither=bayer:bayer_scale=3',
    '-loop', '0',
    str(final_gif)
], check=True)
print(f"Generated {final_gif} ({final_gif.stat().st_size} bytes)", flush=True)

print("=== Step 6: Update media.json Metadata ===", flush=True)
def digest(filename):
    return hashlib.sha256((DEST / filename).read_bytes()).hexdigest()

probe = json.loads(subprocess.check_output([
    'ffprobe', '-v', 'error',
    '-show_entries', 'format=duration,size:stream=codec_name,width,height',
    '-of', 'json', str(final_mp4)
], text=True))

shots = (
    'home-light', 'home-dark', 'scrolled-light', 'scrolled-dark',
    'settings-light', 'settings-dark', 'menu-light', 'search-light'
)

media_json_path = DEST / 'media.json'
old_media = json.loads(media_json_path.read_text())

meta = {
    'device': 'Xiaomi TV 5 Pro 55 / MiTV-ANSP0',
    'android': 9,
    'api': 28,
    'sourceResolution': [1920, 1080],
    'applicationCount': old_media.get('applicationCount', 45),
    'screenshots': {
        'version': '0.9.16',
        'captureDate': '2026-11-24',
        'source': 'Direct ADB screencap on the real television; WebP encoding only',
        'files': {f"{n}.webp": digest(f"{n}.webp") for n in (*shots, 'video-poster')}
    },
    'legacyScreenshots': old_media.get('legacyScreenshots'),
    'animatedPreview': {
        'file': 'still-tv-preview.gif',
        'version': '0.9.16',
        'captureDate': '2026-11-24',
        'source': 'Native Patagonia landscape clip; 10 fps, 768px wide, original speed',
        'sha256': digest('still-tv-preview.gif')
    },
    'video': {
        **probe,
        'file': 'still-tv-demo.mp4',
        'version': '0.9.16',
        'captureDate': '2026-11-24',
        'source': 'Direct ADB screenrecord on the real television showing natural landscapes and adaptive lighting',
        'sha256': digest('still-tv-demo.mp4')
    },
    'videoEdit': 'Single continuous native TV screenrecord showing home card browsing, smooth smoked glass scroll, search, wallpaper switching in settings, and context menu; original playback speed; no frame interpolation; audio omitted',
    'comparison': old_media.get('comparison', {})
}
meta['comparison']['stillVersion'] = '0.9.16'
meta['comparison']['stillSource'] = 'Full Still 0.9.16 screenshot captured on the same TV on 2026-11-24'
meta['comparison']['files']['home-light.webp'] = digest('home-light.webp')

media_json_path.write_text(json.dumps(meta, indent=2, ensure_ascii=False) + '\n')
print("Updated media.json successfully", flush=True)

print("=== Step 7: Update Website HTML/JS Cache-Busters ===", flush=True)
index_path = ROOT / 'website/index.html'
site_js_path = ROOT / 'website/site.js'

index_text = index_path.read_text()
site_js_text = site_js_path.read_text()

for name in [f"{n}.webp" for n in (*shots, 'video-poster')] + ['still-tv-demo.mp4']:
    d = digest(name)[:12]
    pattern = re.escape(name) + r'\?v=[^"\s]+'
    replacement = f"{name}?v=0916dev-{d}"
    index_text = re.sub(pattern, replacement, index_text)
    site_js_text = re.sub(pattern, replacement, site_js_text)

# Update site.js version tag in index.html
site_js_path.write_text(site_js_text)
js_hash = hashlib.sha256(site_js_path.read_bytes()).hexdigest()[:12]
index_text = re.sub(r'site\.js\?v=[^"\s]+', f"site.js?v=0916dev-{js_hash}", index_text)

# Update index.html download link and version string
index_text = re.sub(r'Still-0\.9\.\d+\.apk', 'Still-0.9.16.apk', index_text)
index_text = re.sub(r'Still 0\.9\.\d+ 预览版', 'Still 0.9.16 预览版', index_text)
index_text = re.sub(r'0\.9\.\d+ 预览版 · Android 6\.0\+ · 约 [\d\.]+ MB', '0.9.16 预览版 · Android 6.0+ · 约 3.1 MB', index_text)
index_text = re.sub(r'Still 0\.9\.\d+ 开发版', 'Still 0.9.16 预览版', index_text)

index_path.write_text(index_text)
print("Updated website/index.html and website/site.js successfully", flush=True)
