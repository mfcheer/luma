# Still 0.9.16 发布验证

2026-11-24（北京时间）。

### 一、 核心发布内容与优化特性

1. **扩充 3 套顶级摄影级自然风光大片**：
   - 响应大屏自然风光偏好，精选引入 3 套 4K 实拍级自然风景切片及低算力预高斯磨砂层（`_blur.webp`）：
     - **Patagonia（巴塔哥尼亚雪峰）**：安第斯 Fitz Roy 锯齿雪峰与翡翠冰川湖面，雪线与流云分明，空灵壮阔；
     - **Sequoia（加州红杉晨曦）**：加州红杉原始古树穿透金色晨雾形成壮丽丁达尔光束；
     - **Yosemite（优胜美地苍谷）**：半圆顶与酋长岩巨石俯瞰，夕照苍松绝壁。
   - 壁纸库完整覆盖：**7 款摄影大片（Patagonia、Sequoia、Yosemite、Sonoma、Big Sur、Catalina、Mojave）+ 2 款经典纯色（深色、浅色）**；
   - 设置项「外观与壁纸」支持遥控器左右键或确定键平滑轮播、即时生效与持久化。

2. **全天候高对比度文字自适应（任何风景背景均清晰）**：
   - **基准主题收敛**：日间风景统一运行于暗场基座（Dark Palette），消除浅色模式在暗罩上的深灰字融色缺陷；
   - **应用卡片标题（`AppCardView.java`）**：
     - 未获焦：`0xEEFFFFFF`（93% 柔白）+ `4px` 半径沉降立体纯黑阴影（`0xB3000000`）；
     - 获焦放大：`0xFFFFFFFF`（100% 纯白）+ `6px` 半径扩散悬浮投影（`0xCC000000`）；
     - 配合五段非线性底部暗罩（Bottom Scrim），应用文字轮廓分明、稳坐台座，彻底告别看不清/看不到。
   - **顶部时钟与日期（`MainActivity.java`）**：
     - 时钟：`0xFFFFFFFF` + 加厚 `6px` 大半径暗晕（`0xD9000000`）；
     - 日期：`0xF2FFFFFF` + `4px` 暗影（`0xCC000000`），置于高亮雪峰与反光云天依然边缘锋利。

3. **顶部状态图标微透悬浮暗盾**：
   - 搜索、隔空安装与系统设置按键在未获焦状态下加装 `0x2E000000`（18% 微透圆角暗盾，18dp 圆角），图标固定为 `0xEEFFFFFF`，在极亮雪山或反光天空下提供恒定稳定的对比度；
   - 获焦状态平滑亮起 Apple 质感高光毛玻璃。

---

### 二、 真机全流程回归与官网发布产物

1. **小米电视真机（192.168.31.223:5555 / MiTV-ANSP0 / Android 9）实测**：
   - 全新 0.9.16 安装包顺利部署升级；
   - 电视真机重新截取全量自然风光与黑曜石毛玻璃下滑实景（`home-light.webp`, `home-dark.webp`, `scrolled-light.webp`, `scrolled-dark.webp`, `settings-light.webp`, `settings-dark.webp`, `menu-light.webp`, `search-light.webp`, `video-poster.webp`）；
   - 重新录制真机交互演示视频 `website/assets/still-tv-demo.mp4` 及动图 `website/assets/still-tv-preview.gif`；
   - 电视渲染监测：单帧耗时 13ms，0 掉帧，60fps 满帧流畅。

2. **版本号与校验和**：
   - `versionCode 34`，`versionName 0.9.16`；
   - APK 文件：`website/downloads/Still-0.9.16.apk`；
   - APK 大小：`3240763` 字节；
   - APK SHA-256：`c91f59daf2f1093d538de54c6a278028f76688d20b42f95d6134dcc589b79632`；
   - 签名证书 SHA-256：`ab4f40cc3090987f0d2734e169762a40fa4426bc912fdcdc721d2c1332e44b43`（与历史版本一致）；
   - `website/downloads/SHA256SUMS.txt`、`website/update.json`、`website/downloads/update.json` 及 `website/index.html` 同步更新完毕。
