# Still 0.9.8 独立 QA 复核

复核日期：2026-11-19（北京时间）。结论：本地代码与构建检查通过；电视端完整更新流程尚未验收，未发布线上版本。

## 本轮实际验证

- 阅读最终 AppUpdate 严格类型与范围校验；先前发现的数值截断、错误类型默认值问题已修复。
- 阅读 MainActivity：onCreate 与 onActivityResult 均在安装器成功启动后清理 pendingInstallApk，异常保留；不兼容提示使用 API 等级。
- `./gradlew assembleDebug test lint` 成功；Lint 报告为 0 errors、34 warnings。Gradle testDebugUnitTest/testReleaseUnitTest 为 NO-SOURCE，不能当作已执行业务单元测试。
- 下述独立 AppUpdateTest 实际运行通过，覆盖 JSON 入口、范围、哈希、空间判断和文件路径边界。
- 另用临时 JVM 探针从 JSON 入口验证 versionCode/size/minSdk 各自的 Long 正负溢出、超长整数、false、null、小数和数字字符串，共 21 项全部拒绝。使用 JSON-Java 测试实现，并非 Android 设备执行。
- website/downloads、output、本轮 Gradle 构建三份 APK 逐字节一致；两份更新清单大小与哈希、SHA256SUMS 均一致。
- APK 为 708699 字节，SHA-256：`746cdbd5308fb853c6f072861fe70fcf464f0421aaecb9bca259ed8976058466`。
- apksigner 实际核验 0.9.8 与本地 0.9.7 的签名证书 SHA-256 均为 `ab4f40cc3090987f0d2734e169762a40fa4426bc912fdcdc721d2c1332e44b43`。

## 可复制的独立测试命令

在项目根目录、Bash 中运行（先执行 Gradle 构建，使用现有本地 JSON 测试依赖）：

```bash
set -e
qa_out=$(mktemp -d /tmp/still-update-test.XXXXXX)
qa_cp="/Users/mfcheer/Library/Android/sdk/platforms/android-35/android.jar:app/build/test-libs/json-20240303.jar:app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes:app/build/intermediates/compile_and_runtime_not_namespaced_r_class_jar/debug/processDebugResources/R.jar"
javac -cp "$qa_cp" scripts/AppUpdateTest.java -d "$qa_out"
java -cp "$qa_out:app/build/test-libs/*:$qa_cp" AppUpdateTest
```

## 验证边界与交付条件

- 未操作真实电视/手机，未实测网络中断、空间不足、权限授权往返、安装器异常或遥控器焦点；这些分支的代码检查不等于设备验收。
- 未实测从 0.9.7 覆盖安装后排序、别名与设置保留；证书一致仅证明必要的升级签名条件满足。
- 未推送或发布；线上清单及 APK 可访问性尚未验证。电视需先安装一次包含更新入口的 0.9.8，后续新版发布后才能使用在线升级。
- 当前完成的是手动检查、下载进度及系统确认安装；未加入后台自动检查或静默安装。
