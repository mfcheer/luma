# Still 0.9.12 发布验证

2026-11-22（北京时间）。

- 网页快捷方式全面重构为纯白极简卡片视觉：背景采用统一纯白（`#FFFFFF`），彻底移除多余彩色渐变、光晕、圆圈底衬与线框描边。
- 纯粹还原图标：大尺寸居中展示原始网站高清 Favicon / Apple Touch Icon 与内置高清微质感矢量 Logo。
- 移除卡片右上角“Web”角标胶囊与底部域名文本，不添加额外的人工形状修饰，保持电视卡片纯粹高级的视觉质感。
- versionCode 30，versionName 0.9.12；递增版本便于已安装客户端在线检查更新。
- `./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain` 全量通过；31 项单元测试 100% 通过，Lint 0 错误。
- APK 包名 com.jianping.tv；签名证书 SHA-256 与官网历史版本完全一致：`ab4f40cc3090987f0d2734e169762a40fa4426bc912fdcdc721d2c1332e44b43`。
- APK 大小 966451 字节；SHA-256 `5a739453fa834de2caa446b991503e32d5fc06b4e822de7f6e0e06a08b9cdca2`。
- 官网下载链接、两份更新清单（`website/update.json` 与 `website/downloads/update.json`）及校验和文件严格同步更新。
