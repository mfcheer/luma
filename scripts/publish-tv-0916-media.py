#!/usr/bin/env python3
"""Capture Still 0.9.16 native screenshots and demo video on Xiaomi TV真机,
encode WebP and video assets for the official website, and update all metadata.
"""
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'website/assets'
OUT = ROOT / 'output/website-tv-0916'
OUT.mkdir(parents=True, exist_ok=True)

SERIAL = '192.168.31.223:5555'
ADB = 'adb'
BASE = [ADB, '-s', SERIAL]
PKG = 'com.jianping.tv'


def adb(*args, timeout=20):
    return subprocess.check_output([*BASE, *args], text=True, timeout=timeout).strip()


def key(code, pause=0.5, long=False):
    cmd = [*BASE, 'shell', 'input', 'keyevent']
    if long:
        cmd.append('--longpress')
    cmd.append(str(code))
    subprocess.run(cmd, check=True)
    time.sleep(pause)


def tree():
    for _ in range(5):
        subprocess.run([*BASE, 'shell', 'rm', '-f', '/sdcard/still-tree.xml'], check=False)
        time.sleep(0.3)
        res = adb('shell', 'uiautomator', 'dump', '/sdcard/still-tree.xml', timeout=20)
        if 'dumped to' in res:
            xml_data = adb('shell', 'cat', '/sdcard/still-tree.xml')
            if xml_data.strip():
                try:
                    return ET.fromstring(xml_data)
                except Exception:
                    pass
        time.sleep(0.6)
    raise AssertionError('Failed to get fresh TV UI hierarchy')


def focus_node():
    for _ in range(5):
        t = tree()
        sel = next((n for n in t.iter('node') if n.get('focused') == 'true'), None)
        if sel is not None:
            return sel
        time.sleep(0.4)
    raise AssertionError('No focused view on TV screen')


def focus_desc():
    n = focus_node()
    return n.get('content-desc') or n.get('text') or ''


def shot(name):
    time.sleep(0.5)
    raw = subprocess.check_output([*BASE, 'exec-out', 'screencap', '-p'], timeout=20)
    im = Image.open(io.BytesIO(raw))
    assert im.size == (1920, 1080), f"Invalid size: {im.size}"
    (OUT / f"{name}.png").write_bytes(raw)
    im.save(DEST / f"{name}.webp", 'WEBP', quality=92, method=6)
    print(f"Captured & encoded: {name}.webp", flush=True)


def ensure_awake():
    key(224, 0.5)  # WAKEUP
    p = adb('shell', 'dumpsys', 'power')
    if 'mWakefulness=Asleep' in p:
        key(26, 1.5)  # POWER
    assert 'mWakefulness=Awake' in adb('shell', 'dumpsys', 'power'), 'TV is not awake'


def home_top():
    ensure_awake()
    for _ in range(3):
        key(4, 0.2)
    adb('shell', 'am', 'start', '-n', f"{PKG}/.ui.MainActivity")
    time.sleep(0.6)
    # Scroll all the way up to top
    for _ in range(8):
        key(19, 0.15)
    # Move down to row 0 first card
    key(20, 0.3)
    for _ in range(5):
        key(21, 0.15)
    time.sleep(0.4)


def open_settings():
    home_top()
    # Up to header
    for _ in range(5):
        key(19, 0.15)
    time.sleep(0.3)
    # Header: find '设置' button
    for _ in range(4):
        if focus_desc() == '设置':
            break
        key(22, 0.25)
    assert focus_desc() == '设置', f"Not on settings button: {focus_desc()}"
    key(23, 0.8)  # Open settings
    time.sleep(0.8)


def focus_setting_wallpaper():
    for _ in range(8):
        desc = focus_desc()
        if '外观与壁纸' in desc:
            return
        key(20, 0.25)
    for _ in range(8):
        desc = focus_desc()
        if '外观与壁纸' in desc:
            return
        key(19, 0.25)
    raise AssertionError("Could not focus 外观与壁纸 in settings")


def cycle_wallpaper_to(target_name):
    open_settings()
    focus_setting_wallpaper()
    for _ in range(12):
        desc = focus_desc()
        if target_name in desc:
            print(f"Current wallpaper matched target: {desc}", flush=True)
            return
        key(22, 0.5)  # cycle right
    raise AssertionError(f"Could not cycle wallpaper to {target_name}")


def open_card_menu():
    home_top()
    node = focus_node()
    bounds = node.get('bounds')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', bounds))
    x = (x1 + x2) // 2
    y = (y1 + y2) // 2
    adb('shell', 'input', 'swipe', str(x), str(y), str(x), str(y), '850')
    time.sleep(0.8)
    key(19, 0.4)  # Focus menu item


def open_search():
    home_top()
    for _ in range(5):
        key(19, 0.15)
    # Header: find '搜索'
    for _ in range(4):
        if focus_desc() == '搜索':
            break
        key(21, 0.25)
    key(23, 0.8)  # Enter search
    adb('shell', 'input', 'text', 'jkj')
    time.sleep(0.8)
    key(4, 0.6)  # Dismiss keyboard, focus results


def main():
    print("=== Step 1: Verification & Pre-check ===", flush=True)
    ensure_awake()
    model = adb('shell', 'getprop', 'ro.product.model')
    print(f"Device connected: {model}", flush=True)
    pkg_dump = adb('shell', 'dumpsys', 'package', PKG)
    assert 'versionName=0.9.16' in pkg_dump, 'Still 0.9.16 not installed on TV'

    print("=== Step 2: Capture Screenshots (Light / Patagonia) ===", flush=True)
    cycle_wallpaper_to('Patagonia')
    key(4, 0.6)  # Close settings back to home

    # 1. home-light (Patagonia)
    home_top()
    shot('home-light')

    # 2. scrolled-light (Patagonia scrolled down)
    for _ in range(3):
        key(20, 0.5)
    shot('scrolled-light')
    key(4, 0.4)

    # 3. settings-light (Patagonia settings modal)
    open_settings()
    focus_setting_wallpaper()
    shot('settings-light')
    key(4, 0.6)

    # 4. menu-light (Patagonia card long-press menu)
    open_card_menu()
    shot('menu-light')
    key(4, 0.6)

    # 5. search-light (Search interface)
    open_search()
    shot('search-light')
    key(4, 0.6)

    print("=== Step 3: Capture Screenshots (Dark / Catalina) ===", flush=True)
    cycle_wallpaper_to('Catalina')
    key(4, 0.6)

    # 6. home-dark (Catalina night bay)
    home_top()
    shot('home-dark')

    # 7. scrolled-dark (Catalina scrolled down)
    for _ in range(3):
        key(20, 0.5)
    shot('scrolled-dark')
    key(4, 0.4)

    # 8. settings-dark (Catalina settings modal)
    open_settings()
    focus_setting_wallpaper()
    shot('settings-dark')
    key(4, 0.6)

    # Restore to Patagonia
    cycle_wallpaper_to('Patagonia')
    key(4, 0.6)
    home_top()

    # 9. video-poster.webp
    shutil.copyfile(DEST / 'home-light.webp', DEST / 'video-poster.webp')
    print("Copied video-poster.webp", flush=True)

    print("=== Step 4: Record Native TV Demo Video ===", flush=True)
    remote_video = '/sdcard/still-tv-demo-0916.mp4'
    adb('shell', 'rm', '-f', remote_video)

    # Start screenrecord
    rec_proc = subprocess.Popen(
        [*BASE, 'shell', 'screenrecord', '--time-limit', '80', '--bit-rate', '6000000', remote_video],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.PIPE
    )
    time.sleep(1.0)
    rec_pid = adb('shell', 'pidof', 'screenrecord')
    print(f"Screenrecord started with PID: {rec_pid}", flush=True)

    try:
        # Action sequence:
        # 1. Start at Patagonia home screen; pause 1.5s
        home_top()
        time.sleep(1.5)

        # 2. Navigate across cards in first row (right x 4, left x 4)
        for _ in range(4):
            key(22, 0.8)
        for _ in range(4):
            key(21, 0.8)

        # 3. Scroll down into application list (down x 3, pause, up x 3)
        for _ in range(3):
            key(20, 0.8)
        time.sleep(1.2)
        for _ in range(3):
            key(19, 0.8)
        time.sleep(1.0)

        # 4. Navigate up to search, type query, view results, dismiss
        for _ in range(3):
            key(19, 0.2)
        for _ in range(2):
            key(21, 0.2)
        key(23, 0.8)  # Open search
        adb('shell', 'input', 'text', 'jkj')
        time.sleep(1.2)
        key(4, 0.8)  # Dismiss IME
        time.sleep(1.0)
        key(4, 0.8)  # Exit search
        time.sleep(0.8)

        # 5. Open Settings and cycle through wallpapers
        for _ in range(3):
            key(19, 0.2)
        key(22, 0.25)
        key(23, 0.8)  # Open settings
        time.sleep(0.8)

        focus_setting_wallpaper()
        time.sleep(1.0)

        # Showcase the rich wallpapers:
        # Yosemite, Dark, Light, Sonoma, Big Sur, Catalina, Mojave, Sequoia, Patagonia
        for _ in range(9):
            key(22, 1.3)  # Cycle right with 1.3s pause to show each landscape
        time.sleep(1.0)

        # Back to Home
        key(4, 0.8)
        time.sleep(1.0)

        # 6. Long press first card to show context menu
        home_top()
        time.sleep(0.5)
        open_card_menu()
        time.sleep(1.5)
        key(4, 0.8)  # Dismiss menu
        time.sleep(1.0)

        # Finish on home screen
        home_top()
        time.sleep(1.5)

    finally:
        print("Stopping screenrecord...", flush=True)
        if rec_pid:
            try:
                adb('shell', 'kill', '-2', rec_pid)
            except Exception as e:
                print(f"Kill signal notice: {e}")
        rec_proc.wait(timeout=20)

    raw_mp4 = OUT / 'raw-still-tv-demo.mp4'
    print("Pulling recorded video from TV...", flush=True)
    adb('pull', remote_video, str(raw_mp4), timeout=120)
    adb('shell', 'rm', '-f', remote_video)

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
    # Use first 16 seconds for preview gif (browsing cards and smooth scroll down on Patagonia)
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


if __name__ == '__main__':
    main()
