# 暖灰雾面首页效果图

日期：2026-10-22。生成方式：内置 ImageGen；非 CLI/API fallback。

用户要求先出一张暖灰雾面主题效果图。本轮只生成一张，以现有 Still 0.7.2 首页截图作为结构和真实图标参考，保留五列、两行半、关闭应用名称、右上时间/日期/星期和设置入口。该图片为设计预览，尚未实现为应用主题。

图片：[home-v1.png](home-v1.png)。实际生成尺寸1672×941，16:9。运行效果参考：[当前深色首页](../../screenshots/still-072-home.png)。

## 完整生成提示词

```text
Use case: ui-mockup.
Asset type: one realistic, production-quality Android TV launcher home screen concept, 1920 x 1080 landscape 16:9, app content only.
Direction name: Warm Gray Matte / 暖灰雾面.
Input image 1 is the existing Still 0.7.2 home screen, used as the structural and content reference. Re-theme this exact interface into a refined light warm-gray theme. Preserve its five-column grid, two fully visible rows and a faded partial third row, card sizes, generous horizontal and vertical spacing, the enlarged raised selected YouTube card at row 1 column 2, original application order, actual app marks, and absence of app-name labels. The existing repeated nine-square icons are actual test-app fixtures: keep them rather than inventing attractive entertainment apps. Do not add a title, Still wordmark, navigation tabs, sidebar, widgets, advertisements, content recommendations, page indicators, scroll-position line or extra buttons.
Background: luminous but restrained warm pearl-gray matte surface, approximately #E7E5DF, with an extremely broad, low-contrast ivory light falloff. It should feel calm, soft and expensive, not stark white, beige paper, brushed metal, a marble texture, frosted-glass slabs, fabric or a photograph. Around and behind the selected YouTube card, add the faintest broad blush-tinted light, derived from its red icon, fading smoothly into the neutral warm-gray field. No glowing ring, neon, obvious colored spot or disconnected oval light pool.
Cards: retain the authentic marks, aspect ratios and brand colors from the reference, including original dark/navy app surfaces where present. Do not recolor YouTube, Google Play or the blue remote icon into monochrome. Adapt the surrounding shadows and hairline edges for a bright environment. The selected YouTube tile keeps a crisp porcelain-white face, clean rounded corners, a subtle neutral edge, and a physically plausible broad soft gray contact shadow beneath its lifted position. It is approximately 20% larger and slightly higher than adjacent cards. Make the raised depth convincing and restrained, with no thick outline, glossy extrusion, heavy drop shadow or border-on-border effect. Resting cards use much quieter shadows and consistent corner treatment.
Third row: retain recognizable icon silhouettes, mild blur and a gentle fade into the same warm background toward the bottom edge, matching the reference's partially visible row. No opaque masking bar or sudden cutoff.
Header: keep the compact clock/date group and the single settings icon in the top-right, in exactly the existing positions. Change the text and settings glyph to readable charcoal/slate gray suitable for this light theme, time stronger than the smaller secondary date line. For mock data, current-date anchor is October 22, 2026, Thursday. Exact time text: "10:08". Exact date text on the line beneath: "2026年10月22日  星期四". Use precise, clean sans-serif typography with correct Chinese glyphs. Keep the left/top area otherwise empty.
Composition: full-bleed straight-on interface, no television bezel, room, remote control, browser chrome, device mockup, perspective angle, annotation, watermark, explanation or palette swatches. This is a single focused design, not an options board. Preserve the existing layout and authentic app content; the requested variation is the light-theme surface, shadows, focal lighting and header colors.
```
