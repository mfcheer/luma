# Still 0.9.2 焦点名称与交互回归

final result: passed

## 视觉依据与最终选择

用户提供的Apple TV照片用于参考仅选中显示名称、卡片浮起和边缘续行；生成参考见 `docs/designs/focus-092/home-concept.png`，不是运行截图。最终保留首页此前的轻微上浮：首页、搜索都按1080p统一让选中卡片底边上移约5px，放大1.14倍。搜索卡片较大，但不会再增加上浮距离。

本报告的像素与回归证据来自专用API33 ARM64模拟器，1920×1080，28个入口包含24个测试应用；重复九宫格是测试APK素材。官网与README的公开展示素材另外从真实小米电视0.9.2采集，使用设备实际41个应用，不含模拟器测试应用。

## 逐步回归

| 步骤 | 场景与结果 | 证据 |
| --- | --- | --- |
| 1 | 首页通过：只显示选中名称，居中且不改变卡片位置；相邻卡片保持安静 | `docs/qa-092/light-home.png`、`dark-home.png` |
| 2 | 顶部与快速往返通过：移到顶部隐藏名称，回到网格恢复；多次方向反转后只保留当前名称，空闲像素稳定 | `light-header.png`、`dark-header.png`、`focus-smoke.json` |
| 3 | 上下滚动通过：检查全部六行，选中名称保持可见；完整顶行不泛白，部分续行渐隐；抽帧未发现此前的顶部亮条 | `light-row-3.png`、`light-row-6.png`、对应深色图、`scroll-frames.jpg` |
| 4 | 搜索通过：输入时不抢焦点，选择后只显示当前名称；放大图标与数量文字分离，名称在动态视口内完整可见 | `light-search-input.png`、`light-search-single.png`、`light-search-grid.png`、对应深色图 |
| 5 | 两页底边通过：浅深主题中首页674/679px、搜索938/943px，均上移5px；选中宽度274px、普通240px | `focus-smoke.json`、`focus-geometry.json` |
| 6 | 长按菜单通过：保留来源名称、隔离焦点，取消后恢复原应用及偏移 | `light-home-menu.png`、`light-search-menu.png`、对应深色图 |
| 7 | 设置与升级通过：缺省仅选中显示，显式开启始终显示的偏好保留；遥控器开关即时生效并跨重启保存，排序不变 | `focus-smoke.json`、`light-always-names-search.png`、对应深色图 |
| 8 | 搜索恢复通过：两主题实际启动应用、终止后台Still进程并返回，恢复查询、焦点、偏移以及原首页位置；拼音、英文、空结果及清空正常 | `search-regression/search-smoke.json` |

## 动效与交付

单次切换用260ms的克制落点，快速切换120ms无回弹；名称延后淡入，背景只对停留应用跟进。有限动画结束后没有持续渲染循环。上下使用固定视口的透明遮罩，搜索输入法布局不再重复开启系统原生渐隐。

`docs/qa-092/focus-navigation.mp4` 为最终原生交互录屏，约19.07秒，1920×1080 H.264，未加速，结尾回到稳定首页；抽帧及元信息同目录。录屏不作为电视实际帧率证明。

`assembleDebug` / `lintDebug`通过，0错误、17项既有警告；完整记录见 `docs/qa-092/README.md`。本地交付 `output/Still-0.9.2-debug.apk`，与0.9.1相同签名，SHA-256见 `docs/qa-092/apk-sha256.txt`。

## 验证范围

完成模拟器视觉、遥控器、状态恢复回归，原偏好完整还原。电视核对0.9.2安装成功、41应用顺序及完整偏好不变、默认Home不变，结束时保持Asleep，见 `docs/qa-092/tv-update.json`。后续实机采集覆盖浅深首页、滚动、设置、jkj搜索和菜单，见 `docs/qa-092/tv-media.json`；结束恢复原设置并息屏。实机素材不代替GPU性能基准、搜狗TV输入法全流程与开机行为验证。未据此认定全设备性能或完整无障碍合规。上一版官网和搜索菜单回归保存在 `docs/qa-091/design-qa-archived.md`。
