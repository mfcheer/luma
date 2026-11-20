# Still 0.9.9 在线更新闭环验证

验证时间：2026-11-20 01:35（北京时间）。

结论：本地设计、开发和自动验证完成；真实电视安装与遥控器体验尚未验收。本次未推送、未修改官网下载包或线上更新清单。

## 交付

- 设计文档：`/Users/mfcheer/code/AndroidTV/docs/qa-099/在线更新闭环设计.md`
- 测试包：`/Users/mfcheer/code/AndroidTV/output/Still-0.9.9-debug.apk`
- 包信息：`package: name='com.jianping.tv' versionCode='27' versionName='0.9.9' platformBuildVersionName='15' platformBuildVersionCode='35' compileSdkVersion='35' compileSdkVersionCodename='15'`
- 大小：661,144 字节。
- SHA-256：`6184c5f9d99c63fd1831427aba0e0d05d770402c011a1202c4a54f3148c88600`。
- 与官网 0.9.8 的签名连续性一致：`ab4f40cc3090987f0d2734e169762a40fa4426bc912fdcdc721d2c1332e44b43`。

## 自动验证

命令：`./gradlew assembleDebug testDebugUnitTest lintDebug --console=plain`，最终进程退出码 0，BUILD SUCCESSFUL。

- AppUpdateVerificationTest：3 项通过，无失败、无错误。
- UpdateDownloaderTest：4 项通过，无失败、无错误。
- UpdateSessionTest：15 项通过，无失败、无错误。

合计 22 项，覆盖主备地址、重试、取消与迟到回调、重复任务防护、界面退出、持久化恢复、授权和安装器往返、安装前重校验、包名/版本/签名/最低系统限制、损坏文件、HTTP/超时/断网提示、文件清理、实际版本确认、旧版首次升级衔接、全新安装不误报、深浅主题窗口创建和焦点不因进度刷新改变。

静态检查：0 个错误、38 个警告。警告包含项目现有资源/固定方向/兼容 API 提示，以及新增状态保存的同步写入提示、应用级上下文单例提示和中文文案国际化提示；并非零警告。同步保存用于外部安装页面与进程终止之间的持久性，单例通过应用上下文创建。

独立回归：AppUpdateTest、AppOrderTest、AppSearchTest、WakeCycleTest、SearchAliasesTest、ArgbBlurTest 均退出 0。其中别名测试 553 项检查通过，待机逻辑覆盖 1000 次循环。

证据：`output/update-099-build.log`、`output/update-099-regression.log`、`app/build/test-results/testDebugUnitTest/`、`app/build/reports/lint-results-debug.html`。

## 验证限制与发布前门槛

- USB 调试设备列表为空，本轮没有操作用户电视、手机或系统安装器。
- 状态测试以可控制的下载回调模拟网络完成与失败；下载器测试模拟 HTTPS 连接；包结构信息由 Android 测试环境模拟。自动验证不能代替真实系统的覆盖安装。
- 本地 APK 的版本、签名及完整性已静态核验；未完成电视端真实下载、新旧包升级、拒绝权限后恢复、安装取消后恢复，以及排序/名称/主题的真实升级保留检查。
- 进程存活时支持离开窗口继续下载；进程被杀后，未完成下载由用户重新开始，不支持断点续传。
- 用户已经安装的 0.9.8 仍使用旧更新流程；此次改进随安装 0.9.9 生效，不会远程改变旧客户端。
- 初次测试依赖下载发生校验不一致，最终从 Maven 官方仓库下载并以其 SHA-512 校验后完成测试，未跳过校验。

实机验收步骤见设计文档末尾，当前保留官网 0.9.8 作为现行版本。
