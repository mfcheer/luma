# Still 0.9.13 发布验证

2026-11-22（北京时间）。

- 网页快捷方式名称自动提取与三阶段校准：
  1. 失焦预填：配网页面与手动添加弹窗输入 URL 失焦时自动尝试拉取 `<title>` 预填；
  2. 后台同步解析：下载图标时复用 HTML 流提取标题并清洗 HTML 实体字符，自动替换 URL 占位名称；
  3. WebView 运行时实时回写：`WebChromeClient` 补齐 `onReceivedTitle`，支持 SPA 单页应用动态标题回写更新。
- 编程状态屏专属透明高清矢量 Logo：针对 `:8791`、`/board` 与状态屏特征内置四色（珊瑚红、琥珀橙、皇家蓝、薄荷绿）AI 轨迹莫比乌斯环与状态指示点，完全透明无底衬。
- 纯白卡片图标智能融底消边算法：对外部带浅灰/近白底板的图标自动采样边缘并执行平滑融底消减，彻底消除卡片发灰方框与双重圆角冲突。
- versionCode 31，versionName 0.9.13；递增版本便于已安装客户端在线检查更新。
- `./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain` 全量通过；34 项单元测试 100% 通过，Lint 0 错误。
- APK 包名 com.jianping.tv；签名证书 SHA-256 与官网历史版本完全一致：`ab4f40cc3090987f0d2734e169762a40fa4426bc912fdcdc721d2c1332e44b43`。
- APK 大小 970991 字节；SHA-256 `1d5a725f7fbe355f2537b3ae7f92bfb210d3270956cc9edf2b3a430d84a7d8ba`。
- 官网下载链接、两份更新清单（`website/update.json` 与 `website/downloads/update.json`）及校验和文件严格同步更新。
