# Still 0.9.15 发布验证

2026-11-24（北京时间）。

- 启动与恢复焦点/滚动跳动修复（Focus-Jump Fix）：
  1. 阻断动态装载抢焦：`GridWallView.setEntries` 期间使用 `setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS)`，并在 `finally` 块中恢复 `FOCUS_AFTER_DESCENDANTS`，彻底杜绝中间卡片在 `addView` 时被系统排版流程提前抢占焦点而触发底层 `ScrollView` 错误滚动至末行。
  2. 相同应用列表跳过重复排版：新增 `sameEntries(List<AppEntry>)` 精准比对机制，`MainActivity.onResume()` 及图标监听器回调时，若应用列表内容与顺序未变，直接跳过冗余重建，杜绝界面重排闪烁与首屏焦点晃动。
  3. 滚动边界保护：`scrollForRow()` 增加 `visibleHeight > 0` 预检，在根容器未完成初次测量前跳过计算，防止因视口高度为 0 导致错误落点计算。
  4. 顶部操作栏焦点链路加固：移除冗余设置项覆盖，确保遥控器在搜索、排序与设置间流转平滑准确。
  5. 小米电视 5 Pro 55 英寸（Android 9 / 192.168.31.223:5555）实机真机验证：冷启动及从第三方应用返回，焦点均立即稳定停留在首行第 0 项卡片，彻底消除了先跳到底部再弹回顶部的视觉抖动。
- versionCode 33，versionName 0.9.15；递增版本便于已安装客户端在线检查更新。
- `./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain` 全量通过；39 项单元测试 100% 通过，Lint 0 错误。
- APK 包名 com.jianping.tv；签名证书 SHA-256 与官网历史版本完全一致：`ab4f40cc3090987f0d2734e169762a40fa4426bc912fdcdc721d2c1332e44b43`。
- APK 大小 1024237 字节；SHA-256 `47ef21ab54037c76a2ef233912499384b47668410d8efaeb9040d305fd13c63f`。
- 官网下载链接、两份更新清单（`website/update.json` 与 `website/downloads/update.json`）及校验和文件严格同步更新。
