# Still 0.9.10 发布验证

2026-11-20（北京时间）。

- 外观主题使用与“始终显示名称”一致的开关；按确定切换浅色／深色，保存选择并保留设置项焦点。
- versionCode 28，versionName 0.9.10；递增版本便于 0.9.9 客户端检查更新。
- `./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain` 成功；22 项测试通过，Lint 0 错误、38 个警告。
- APK 包名 com.jianping.tv；签名证书 SHA-256 与官网 0.9.9 一致：ab4f40cc3090987f0d2734e169762a40fa4426bc912fdcdc721d2c1332e44b43。
- APK 大小 629924 字节；SHA-256 `3e00ad4af869e7e22daddbc622f596191dac5dc5b77ccab90fd836dc8fc860b3`。
- 官网下载链接、两份更新清单及校验文件同步更新；检查更新说明保持默认收起。
- 构建日志：`output/theme-0910-build.log`。
- 本轮未操作电视；遥控器实机体验及真实升级验收仍待完成。
