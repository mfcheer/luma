# Still 0.9.11 发布验证

2026-11-22（北京时间）。

- 支持添加常用网页快捷方式至电视桌面，手机局域网扫码配网添加。
- 采用独立轻量浏览进程（`:browser`）加载网页，遥控器一键退出并释放全部内存，永不影响桌面流畅度。
- 内置主流站点高清矢量微质感 Logo 库（百度、GitHub、B站、Alist、群晖等），支持通用站点与个人主页 Favicon / Touch-Icon 自动抓取与持久化缓存。
- 手机配网端去全大写，规范展示为 `Still TV` 与 `Web` 标签。
- 修复 Android 6.0 (API 23) 兼容性（去除 removeIf 与 getWebChromeClient 调用，保证 minSdk 23 严谨通过）。
- versionCode 29，versionName 0.9.11；递增版本便于已安装客户端检查更新。
- `./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain` 全量通过；31 项单元测试 100% 通过，Lint 0 错误。
- APK 包名 com.jianping.tv；签名证书 SHA-256 与官网历史版本完全一致：`ab4f40cc3090987f0d2734e169762a40fa4426bc912fdcdc721d2c1332e44b43`。
- APK 大小 969923 字节；SHA-256 `8d360a396ec28f868c723d992ef0988c430bfec113b350379a5d71e86ee718de`。
- 官网下载链接、两份更新清单（`website/update.json` 与 `website/downloads/update.json`）及校验和文件严格同步更新；功能说明保持默认收起。
