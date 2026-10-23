#!/usr/bin/env python3
"""Remote-only regression on the dedicated fixture emulator; restores all preferences.

Exercises focus-only/all names, shared Home/search cards, repeat navigation, scroll
boundaries, menu cancellation, launch/return, label geometry and finite idle motion.
Never targets, wakes or modifies a physical TV.
"""
import json
import re
import runpy
import time
from pathlib import Path
from PIL import ImageChops

q = runpy.run_path(str(Path(__file__).with_name('theme-080-smoke.py')))
adb, key, tree = (q[n] for n in ('adb', 'key', 'tree'))
PKG, ROOT = q['PKG'], q['ROOT']
OUT = ROOT / 'docs/qa-092'


def bounds(n):
    return list(map(int, re.findall(r'\d+', n.get('bounds'))))


def card_labels(state):
    return [(card, n) for card in state.iter('node')
            if card.get('long-clickable') == 'true' and card.get('package') == PKG
            for n in card.iter('node') if n.get('text') == card.get('content-desc')]


def assert_names(count):
    state = tree()
    labels = card_labels(state)
    assert len(labels) == count, [(a.get('content-desc'), b.attrib) for a, b in labels]
    if count == 1:
        card, label = labels[0]
        assert card.get('focused') == 'true'
        a, b = bounds(card), bounds(label)
        assert abs((a[0]+a[2]) - (b[0]+b[2])) <= 2, 'Caption must be centered'
        assert a[1] <= b[1] < b[3] <= a[3], 'Caption must remain inside its fixed slot'
    return state


def shot(name):
    state = tree()
    q['image']().save(OUT / (name + '.png'))
    (OUT / (name + '.xml')).write_text(q['ET'].tostring(state, encoding='unicode'))


def assert_baseline(unit):
    state, image = tree(), q['image']()
    cards = [n for n in state.iter('node') if n.get('long-clickable') == 'true'
             and n.get('package') == PKG and n.get('content-desc', '').startswith('测试应用')]
    selected = next(n for n in cards if n.get('focused') == 'true')
    resting = next(n for n in cards if n.get('focused') != 'true'
                   and bounds(n)[1] == bounds(selected)[1])
    def bottom(card):
        x0,y0,x1,y1 = bounds(card)
        # Restrict sampling to artwork: the caption intentionally sits below it.
        pixels = [y for y in range(max(0,y0), min(1080,y0+round(90*unit)))
                  for x in range(x0,x1)
                  if (lambda r,g,b: b-r>18 and g-r>8 and r<100 and b<160)(*image.getpixel((x,y)))]
        assert pixels
        return max(pixels)
    a,b = bottom(selected),bottom(resting)
    assert abs((a-b) + 5) <= 1, ('Selected artwork bottom must lift 5px on both pages', a,b)
    return {'selectedBottom':a,'restingBottom':b,'differencePixels':a-b}


def idle():
    time.sleep(.9)
    a = q['image']().crop((50, 170, 1870, 1010))
    time.sleep(.55)
    b = q['image']().crop((50, 170, 1870, 1010))
    assert ImageChops.difference(a, b).getbbox() is None, 'Focus/light animations must stop at idle'


def configure(original_xml, light, names=None):
    xml = q['ET'].fromstring(original_xml)
    for n in list(xml):
        if n.get('name') in ('appearance', 'showAppNames'):
            xml.remove(n)
    q['ET'].SubElement(xml, 'string', name='appearance').text = 'light' if light else 'dark'
    if names is not None:
        q['ET'].SubElement(xml, 'boolean', name='showAppNames', value=str(names).lower())
    q['seed'](xml)
    q['start']()


def search(query):
    key(4)
    key(19)
    assert q['desc']() == '搜索'
    key(23, .7)
    adb('shell', 'input', 'text', query)
    time.sleep(.4)
    assert q['desc']() == '搜索应用名称或拼音首字母'


def main():
    OUT.mkdir(exist_ok=True)
    original_xml, original = q['prefs_xml'](), q['prefs']()
    assert q['BASE'][-1].startswith('emulator-')
    assert len(json.loads(original['appOrder'])) == 28
    assert 'versionName=0.9.2' in adb('shell', 'dumpsys', 'package', PKG)
    adb('shell', 'run-as', PKG, 'cp', 'shared_prefs/launcher.xml', 'files/qa092-backup.xml')
    checks = []
    try:
        for light in (True, False):
            theme = 'light' if light else 'dark'
            # Missing setting proves the new default without deleting app/order data.
            configure(original_xml, light)
            assert_names(1)
            key(22, .7)
            assert q['desc']() == 'YouTube'
            selected = q['focused']().get('bounds')
            assert_names(1)
            shot(theme+'-home')
            key(20, .6)
            baseline_home = assert_baseline(2)
            key(19, .6)
            key(19, .5)
            assert q['desc']() == '搜索'
            assert_names(0)
            shot(theme+'-header')
            key(20, .6)
            assert q['focused']().get('bounds') == selected, 'Labels must not reflow the grid'
            adb('shell', 'input', 'keyevent', *(['22', '21'] * 10))
            time.sleep(.7)
            assert q['desc']() == 'YouTube'
            assert_names(1)
            idle()
            # All rows, including the last incomplete row, retain a visible caption.
            for row in range(5):
                key(20, .4)
                state = assert_names(1)
                label = card_labels(state)[0][1]
                assert 165 <= bounds(label)[1] < bounds(label)[3] <= 988
                if row in (1, 4): shot(theme+'-row-'+str(row+2))
            for _ in range(4): key(19, .15)
            assert_names(1)
            idle()
            key(4, .6)
            assert_names(1)
            key(23, .5, long=True)
            state = tree()
            labels = card_labels(state)
            assert len(labels) == 1, 'Context selection must preserve its application caption'
            shot(theme+'-home-menu')
            key(4, .5)
            assert_names(1)
            search('cs')
            assert_names(0)
            key(4, .6)
            assert_names(1)
            for _ in range(3): key(20, .15)
            state = assert_names(1)
            viewport = next(n for n in state.iter('node') if n.get('class') == 'android.widget.ScrollView')
            assert bounds(card_labels(state)[0][1])[3] <= bounds(viewport)[3]-29, 'Search caption must clear viewport clipping'
            baseline_search = assert_baseline(2.4)
            result = q['desc'](), q['focused']().get('bounds')
            shot(theme+'-search-grid')
            key(23, .5, long=True)
            assert len(card_labels(tree())) == 1
            shot(theme+'-search-menu')
            key(4, .5)
            assert_names(1)
            assert (q['desc'](), q['focused']().get('bounds')) == result
            key(23, .5)
            assert any(n.get('package', '').startswith('com.jianping.fixture.')
                       for n in tree().iter('node')), 'The fixture activity must open'
            key(4, .7)
            assert_names(1)
            assert (q['desc'](), q['focused']().get('bounds')) == result
            key(4, .5)
            search('YouTube')
            assert_names(0)
            shot(theme+'-search-input')
            key(4, .6)
            state = assert_names(1)
            count = next(n for n in state.iter('node') if n.get('text') == '找到 1 个应用')
            card, caption = card_labels(state)[0]
            assert bounds(card)[1] - 20 > bounds(count)[3], 'Lift must clear the count text'
            assert q['desc']() == 'YouTube'
            shot(theme+'-search-single')
            idle()
            key(4, .5)
            # Existing explicitly enabled preference stays enabled across upgrades.
            configure(original_xml, light, True)
            assert len(card_labels(tree())) > 5
            search('cs'); key(4, .6)
            assert len(card_labels(tree())) >= 5
            shot(theme+'-always-names-search')
            key(4, .5)
            q['appearance'](); key(19)
            assert q['desc']().startswith('始终显示应用名称，')
            key(23); key(4, .5)
            assert_names(1)
            q['start'](); assert_names(1)
            assert q['prefs']()['appOrder'] == original['appOrder']
            assert q['prefs']().get('bootEnabled') == original.get('bootEnabled')
            checks.append({'theme':theme,'defaultFocusOnly':True,'captionCentered':True,
                'headerAndInputHideCaptions':True,'repeatFocusAndIdle':True,
                'allRowsCaptionVisible':True,'menusPreserveCaption':True,
                'searchLaunchReturn':True,'countClearance':True,
                'existingAlwaysNamesPreserved':True,'settingToggleAndRestart':True})
            checks[-1]['homeBaseline'] = baseline_home
            checks[-1]['searchBaseline'] = baseline_search
            print('PASS: '+theme+' focus-only captions, rapid focus, scroll, menus, search, settings and idle', flush=True)
    except BaseException:
        shot('failure')
        raise
    finally:
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'run-as', PKG, 'cp', 'files/qa092-backup.xml', 'shared_prefs/launcher.xml')
        adb('shell', 'run-as', PKG, 'rm', 'files/qa092-backup.xml')
        q['start']()
        assert q['prefs']() == original
    (OUT / 'focus-smoke.json').write_text(json.dumps({'checks':checks,
        'preferencesRestored':True,'physicalTvTouched':False}, indent=2)+'\n')


if __name__ == '__main__':
    main()
