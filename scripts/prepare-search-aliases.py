#!/usr/bin/env python3
"""Compile only reviewed package bindings/terms. --check verifies the checked-in offline asset."""
import argparse
import json
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'docs/plans/search-aliases'
ASSET = ROOT / 'app/src/main/assets/search-aliases.tsv'
COVERAGE = ROOT / 'docs/qa-094/search-coverage.md'


def norm(value):
    return ''.join(c for c in unicodedata.normalize('NFKC', value).lower()
                   if unicodedata.category(c).startswith('L') or unicodedata.category(c) == 'Nd')


def compile_data():
    data = json.loads((BASE / 'apps.reviewed.json').read_text())
    sources = json.loads((BASE / 'sources.reviewed.json').read_text())
    assert data['status'] == 'reviewed_source'
    lines = ['#still-search\t1\t' + data['dictionaryVersion']]
    used_packages = set()
    supported, pending = [], []
    unique_terms = 0
    for record in data['records']:
        bindings = [b for b in record['bindings'] if b['review'] == 'approved']
        if record['review'] != 'approved' or not bindings:
            pending.append(record['name'])
            continue
        seen = set()
        terms = []
        for term in record['terms']:
            assert term['review'] == 'approved'
            text = norm(term['text'])
            assert text == term['normalized'] and text not in seen and len(text) >= 2
            seen.add(text)
            assert re.fullmatch(r'[a-z0-9]+', term['pinyin'])
            assert re.fullmatch(r'[a-z0-9]+', term['initials'])
            rank = 1 if term['kind'] == 'product_name' else 3 if term['kind'] == 'historical_alias' else 2
            assert all('\t' not in v and '\n' not in v for v in (text, term['pinyin'], term['initials']))
            terms.append((str(rank), term['match'], text, term['pinyin'], term['initials'], 'reviewed'))
        unique_terms += len(terms)
        for binding in bindings:
            assert binding['evidence'] in ('device_observed', 'device_catalog_observed', 'store_listing', 'official_source_manifest')
            assert binding['sourceIds'] and all(s in sources for s in binding['sourceIds'])
            package = binding['packageName']
            assert re.fullmatch(r'[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+', package)
            assert package not in used_packages
            used_packages.add(package)
            lines += ['\t'.join((package, *term)) for term in terms]
        supported.append(record)
    coverage = ['# Still 0.9.4 内置检索词覆盖', '',
                f"词库版本 `{data['dictionaryVersion']}`：{len(supported)} 个产品、{len(used_packages)} 个准确包名、{unique_terms} 个独立词条。", '',
                '仅收录已审核的包名与别名。未安装的应用不会出现在搜索中；商店/包名身份核对不等于电视播放兼容性保证。', '',
                '| 类别 | 产品 | 别名/简称 | 已核实包名 |', '|---|---|---|---|']
    for r in supported:
        coverage.append('| ' + ' | '.join([r['category'], r['name'],
                        '、'.join(t['text'] for t in r['terms'][1:]) or '原名称',
                        '、'.join('`'+b['packageName']+'`' for b in r['bindings'] if b['review']=='approved')]) + ' |')
    coverage += ['', '## 未发布的候选条目', '', '、'.join(pending), '',
                 '这些项目的具体 TV 包名仍待核实，不进入本版内置别名词库；用户已安装时仍可使用原名称与现有拼音检索。', '',
                 '来源与审核记录保存在 `docs/plans/search-aliases/apps.reviewed.json` 和 `sources.reviewed.json`，原始评审草案继续保留。', '']
    return '\n'.join(lines)+'\n', '\n'.join(coverage)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    asset, coverage = compile_data()
    for path, content in ((ASSET, asset), (COVERAGE, coverage)):
        if args.check:
            assert path.read_text() == content, f'{path}: regenerate with scripts/prepare-search-aliases.py'
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
    print(('PASS: ' if args.check else 'Generated: ') + coverage.splitlines()[2])


if __name__ == '__main__':
    main()
