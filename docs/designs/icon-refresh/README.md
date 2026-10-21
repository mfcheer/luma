# Still 图标更新

用户选择第三个显示的生成结果：蓝色 S。生成方式：内置 image_gen 工具，未使用 CLI/API fallback。

方形图标采用中央 S 与墨蓝背景；电视横幅采用相同 S 加 Still 字标。原始图和三版探索保存在本目录，运行资源按密度导出；应用逻辑、排序和设置保持不变。

生产资源：

- app/src/main/res/drawable-nodpi/still_icon_v2.png：512×512 标准源。
- app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png：48/72/96/144/192px，透明圆角旧系统回退。
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml：API26+自适应图标，含安全留白。
- app/src/main/res/drawable-xhdpi/still_tv_banner.png：320×180px TV横幅。

适配参考：[Android 自适应图标](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)、[TV 图标与横幅](https://developer.android.com/training/tv/get-started/create#banner)。

## 完整提示词

### 折光 S（备选）

文件：folded-s.png

```text
Use case: logo-brand. Create one production app-icon asset for Still, an elegant minimal Android TV home launcher operated with a remote. Square 1024 x 1024. Direction: Folded S. The entire canvas is a full-bleed deep ink blue-black field (#111923); there is NO inset rounded-square tile and NO outer mockup, device, typography, label or presentation board. Center one bold, instantly recognizable capital S silhouette, built from a single broad ivory satin-metal ribbon with a restrained fold and soft depth. The S must have clean, generous negative spaces and remain legible at 48px. Symbol spans roughly 56% of canvas width and 62% height, all important parts in the central safe zone. Warm-white front face, quiet silver-blue shaded sides, tiny soft shadow, no glowing outline, no rainbow, no chrome shine, no glass sphere, no empty white blob. Restrained premium industrial design, confident simple form, crisp contours. Only the S mark on the solid dark field.
```

### 静窗（备选）

文件：quiet-window.png

```text
Use case: logo-brand. Create one production app-icon asset for Still, a quiet, minimal Android TV home launcher. Square 1024 x 1024, full-bleed uniform deep charcoal-navy background #101925. Direction: Quiet Window. Center a single distinctive architectural screen/window symbol: a substantial soft rectangular ivory outline with very rounded corners, wider than tall, interrupted on its lower-right side by a short inward floating horizontal bar. It suggests a calm home screen and an open doorway, not a play button. The mark is bold and geometric, about 59% canvas width and 43% height, with balanced thick strokes, large clear negative space, and a subtly luminous cool white ceramic surface. A very faint muted icy-blue reflection only along the lower edge gives depth, without a halo or neon. No S, no letter or word, no house roof, no television antenna, no buttons, no decorative dots, no additional nested app tile, no frame surrounding the canvas, no presentation sheet. App icon only. It must read beautifully as a simple original silhouette at 48px, premium and understated.
```

### 蓝色 S（用户选择）

文件：layered-s.png

```text
Use case: logo-brand. Create one production app icon for Still, a refined minimal Android TV home launcher. Square 1024 x 1024, full-bleed deep ink background #101925, no surrounding mockup and no inset rounded-square tile. Direction: Layered S. Center a bold original geometric capital S mark made from two interlocking broad, softly rounded angular bands. An unmistakable S silhouette, not a chain, not a number 5, not a currency sign. Use a restrained icy cyan to rich cerulean palette with a small satin-white edge highlight, very shallow depth, clean manufactured surfaces. The upper and lower sweeps have clearly separated negative spaces. Mark width about 55% canvas, height 61%, generous safe margins. This is more graphic and geometric than an organic ribbon; no text, label, slogan or brand word. No gradients in the background, no sparkles, no extruded glossy plastic, no glass orb, no lens flare, no neon halo, no play triangle. Prioritize a memorable strong silhouette that remains clear and sophisticated at 48 pixels.
```

### 蓝色 S TV 横幅

文件：tv-banner.png

```text
Use case: logo-brand. Asset type: production Android TV launcher banner, landscape 1280 x 720, 16:9. Use the attached blue S icon as the exact brand reference; preserve its distinctive S silhouette, facets and cyan-to-blue colours. Recompose it into a clean horizontal banner, not a mockup. Full-bleed dark ink background matching the reference. Place the blue S mark on the left, approximately 290 pixels wide and 320 pixels high, with generous clear margins. To its right, set the exact word 'Still' in an exceptionally clean white modern sans-serif, medium weight, about 150 pixels high, balanced optical spacing. The combined S symbol and wordmark form one centered horizontal group across the image. Only the S mark and 'Still', no slogan, no rounded tile around the S, no lines, extra symbols, glow, star, frame, device, shadow outside the canvas, or presentation decoration. The word is capital S then lowercase t i l l. Keep all artwork inside the central 80% width and 70% height so it is clearly readable on a television tile. This is the final banner image to ship, not a concept board.
```

## 验证证据

- 已安装到API33 Android TV模拟器，系统应用列表实际显示新的蓝色S和Still横幅，正常及聚焦放大均完整。
- 实际截图：[系统应用列表](../../screenshots/still-071-launcher.png)、[系统应用信息与版本](../../screenshots/still-071-app-info.png)。此版本的TV系统应用信息页不展示图标，因此该截图只用于核对应用版本，不作为圆形图标实测证据。
- Android9/API28使用API26+自适应资源；真实小米电视未连接，仍需在电视上核对厂商桌面的裁切。
