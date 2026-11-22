# Still 0.9.14 发布验证

2026-11-22（北京时间）。

- 局域网极速快传与隔空安装助手（Air Install）：
  1. 首页原生卡片与大屏入口：新增「隔空安装」卡片，纯白底板搭配 1:1 Apple iOS AirDrop 官方矢量图标与天蓝环境光辉映；设置项提供「首页显示隔空安装」持久化开关。
  2. 原生流式 HTTP 接收服务：随弹窗打开即时绑定动态端口、关闭或退出即刻释放端口与线程；采用 8KB 缓冲区流式直写磁盘防 OOM，预检机身可用存储，异常断开自动清理 `.tmp` 缓存。
  3. 大屏三段式状态机弹窗：等待态（高反差大号二维码 + 局域网网址 + 连接指示）、传输态（就地更新实时进度条、百分比、速率与文件名，防止焦点跳变）、就绪态（原生提取 APK 高清图标、名称、版本与体积，遥控器自动聚焦「立即安装」主按键）。
  4. Apple tvOS 胶囊按钮体系：统一 42dp 高度、120dp 宽度，全圆角 Pill 造型，次级按钮采用半透明磨砂底色（`0x18FFFFFF`）与 1.5dp 柔和边缘微光（`0x33FFFFFF`），获焦无缝高亮。
  5. 极简自适应上传端：适配手机全屏大触点选包与完美对称边距，电脑端支持直接拖拽 APK 释放上传。
- versionCode 32，versionName 0.9.14；递增版本便于已安装客户端在线检查更新。
- `./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain` 全量通过；39 项单元测试 100% 通过，Lint 0 错误。
- APK 包名 com.jianping.tv；签名证书 SHA-256 与官网历史版本完全一致：`ab4f40cc3090987f0d2734e169762a40fa4426bc912fdcdc721d2c1332e44b43`。
- APK 大小 1023954 字节；SHA-256 `df78a6c0d6887ba2433ca8ecd7e43f3a9e5590fe8d17a1bb1eb8da037a61c266`。
- 官网下载链接、两份更新清单（`website/update.json` 与 `website/downloads/update.json`）及校验和文件严格同步更新。
