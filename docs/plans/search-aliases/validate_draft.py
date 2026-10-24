#!/usr/bin/env python3
"""Validate review data only. Does not implement matching or touch the app/device."""
import collections
import json
from pathlib import Path
import re
import unicodedata

BASE = Path(__file__).resolve().parent

def normalize(value):
    return ''.join(c for c in unicodedata.normalize('NFKC', value).lower()
                   if unicodedata.category(c).startswith('L') or unicodedata.category(c) == 'Nd')

def main():
    data = json.loads((BASE / 'apps.draft.json').read_text())
    sources = json.loads((BASE / 'sources.json').read_text())
    report = json.loads((BASE / 'review-report.json').read_text())
    records = data['records']
    assert data['status'] == 'review_only_not_loaded_by_app'
    assert len({r['id'] for r in records}) == len(records)
    packages = {}
    tokens = collections.defaultdict(list)
    overrides = 0
    for record in records:
        assert record['review'] == 'pending_product_review'
        assert record['terms'] and record['terms'][0]['text'] == record['name']
        assert record['identityStatus'] in ('bound_draft', 'pending_binding', 'pending_label_review')
        if record['identityStatus'] == 'pending_binding':
            assert not record['bindings']
        if record['identityStatus'] == 'bound_draft':
            assert record['bindings']
            assert all(b['evidence'] != 'device_package_observed_label_pending' for b in record['bindings'])
        seen = set()
        for binding in record['bindings']:
            package = binding['packageName']
            assert re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+', package), package
            assert package not in packages, (package, record['id'], packages.get(package))
            packages[package] = record['id']
            assert binding['sourceIds']
            assert all(s in sources for s in binding['sourceIds'])
        assert all(s in sources for s in record['sourceIds'])
        for term in record['terms']:
            n = normalize(term['text'])
            assert len(n) >= 2 and n == term['normalized'], term
            assert n not in seen, (record['id'], n)
            seen.add(n)
            assert term['pinyin'] and term['initials']
            assert term['pinyin'] == term['pinyin'].lower()
            assert term['initials'] == term['initials'].lower()
            assert term['match'] in ('exact', 'partial')
            if term['kind'] == 'abbreviation_proposal':
                assert term['match'] == 'exact'
            if term['phoneticReview'] == 'music_phrase_override':
                overrides += 1
                assert record['category'] == '音乐K歌'
                assert 'yinyue' in term['pinyin']
                assert 'yinle' in term['libraryBaseline']['pinyin']
            for field in ('normalized', 'pinyin', 'initials'):
                tokens[term[field]].append((record['id'], term['text'], field))
    actual_collisions = {token for token, hits in tokens.items() if len({h[0] for h in hits}) > 1}
    assert actual_collisions == {c['token'] for c in report['crossProductCollisions']}
    by_id = {r['id']: r for r in records}
    find = lambda app, term: next(t for t in by_id[app]['terms'] if t['text'] == term)
    assert find('netease-music-tv', '网易云音乐')['pinyin'] == 'wangyiyunyinyue'
    assert find('qq-music-tv', 'QQ音乐')['initials'] == 'qqyy'
    assert find('ihuman-pinyin', '拼音乐园')['pinyin'] == 'pinyinleyuan'
    assert find('mango-tv', 'MGTV')['match'] == 'exact'
    assert 'yt' not in {t['normalized'] for t in by_id['smarttube']['terms']}
    assert by_id['xigua-legacy-tv']['lifecycle'] == 'official_shutdown_2025-06-30'
    stats = report['statistics']
    assert stats['records'] == len(records)
    assert stats['terms'] == sum(len(r['terms']) for r in records)
    assert stats['exactPackageBindings'] == len(packages)
    assert stats['phoneticOverrides'] == overrides
    assert stats['identityStatusCounts'] == dict(collections.Counter(r['identityStatus'] for r in records))
    assert stats['categories'] == dict(collections.Counter(r['category'] for r in records))
    for source_id, source in sources.items():
        assert source['supports'] and source['checkedOn'] == '2026-10-24'
        if source_id.startswith('device-'):
            assert (BASE / source['url']).is_file(), source
        else:
            assert source['url'].startswith('https://')
    print(f"PASS: {len(records)} products, {stats['terms']} terms, {len(packages)} unique package records")
    print(f"Review required: {len(records) - stats['identityStatusCounts']['bound_draft']} product identities; {len(actual_collisions)} documented cross-product token collisions")
    print('Scope: draft data consistency only; no app search, usage ranking or device tests performed.')

if __name__ == '__main__':
    main()
