# Still 0.5 Design QA

final result: blocked

Native Android TV implementation of the selected home and settings images. User-approved changes: remove upper-left wordmark; rename app Still; preserve actual installed app icons and native system typography. No browser or website is involved.

## Evidence and normalization

Source: docs/designs/still-home-reference.png and still-settings-reference.png, 1672×941.
Implementation: docs/screenshots/still-home.png, still-settings.png and still-settings-toggle.png, 1920×1080 Android TV API 33 at dpi 320; logical UI frame 960×540. Both full views normalized to 960×540 per panel in still-home-comparison.jpg and still-settings-comparison.jpg. Cropped controls compared in still-details-comparison.jpg. The source has Bilibili selected; emulator has YouTube selected. Installed app artwork, clock, and fixture names differ intentionally; layout and focus states are equivalent. No claim of exact artwork matching.

## Findings and comparison history

1. [P2, fixed] Focus border clipped at upper edge. Disabled row and tile padding clipping and removed vertical translation; revised still-home.png shows complete rounded border.
2. [P2, fixed] Switch contrast on pearl focus surface. Use darker checked track and darker off thumb while focused; revised still-settings-toggle.png shows the toggle state on the pale row. State remains exposed in accessibility content description.
3. [P2, fixed] Focus frame noticeably wider than source proportions. Reduced icon frame from 96 to 86 logical px while retaining 82 px real artwork, for a 91 px focused border. Final still-home-comparison.jpg and still-details-comparison.jpg confirm the tighter, fully rounded frame.

## Required fidelity surfaces

- Typography: Android sans-serif / system Chinese fallback, regular weight, 14 px app labels, 17 px settings, 22 px title, light 18 px clock. System font avoids bundling Apple fonts. Text fits; long labels truncate to one line.
- Layout: centered hero, 6×2 grid, separate stationary preview, compact centered settings groups, consistent row heights and margins. Upper-left wordmark removed as requested.
- Color: dark graphite, subdued icon-colored ambient behind hero, pearl focused setting row, monochrome controls. Dynamic light intentionally dims relative to concept to preserve text contrast.
- Images: generated Still mark is a real raster asset; runtime icons are cloned installed app Drawables; settings use Material vector paths. Some third-party icons supplied by the emulator are low resolution / non-square and appear less crisp than conceptual square artwork.
- Copy: labels reflect real actions. Opening settings reveals desktop/application/device groups and a default-off startup switch. Homepage has no brand or duplicate hero title.

## Interaction checklist

Passed still-smoke.py: brand-free header, icon-only hero, grouped settings, persisted startup switch and stable focus, refresh focus, diagnostics, 28 favorites, page navigation, All Apps, long press, external launch/return. Actual Xiaomi Home/boot takeover remains unverified.

Final comparison: no actionable P0/P1/P2 findings remain. Full-view and detail comparisons opened after the final capture. Third-row and last-page captures checked: hero stays fixed, full rows remain visible, All Apps is reachable, and no stale partial label remains at the edge.

P3 follow-up: some installed application assets (notably emulator YouTube) are low resolution and non-square; a future banner-aware preview can improve them without replacing their brand artwork. Native Android Switch metrics and system Chinese glyphs differ slightly from the concept. Real 4K TV viewing distance, system scaling and input latency remain device QA tasks.

Build and Android Lint passed (0 errors; 16 warnings, including existing draw allocation, unused resources and platform guidance). Remote smoke passed on the final APK; no crash-buffer entries. APK badge verified Still / 0.5.0 / API 23, same debug certificate as the previous build.


## 2026-10-21 用户反馈后复查

原先的通过结论偏重结构和功能，对图标内容适配及光影层次的验收过宽。已在本次重新捕获首页并复查，证据和具体发现见 docs/audit-home/findings.md、01-home.png、comparison.jpg。重新打开三项 P2：真实图标的可见面积不一致、横版图标与方形焦点边框脱节、预览附近光影层次不足。尚未修改代码或验证修复，当前视觉结论改为 blocked；既有功能回归结果不受影响。
