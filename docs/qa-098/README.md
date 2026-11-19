# Still 0.9.8：手动在线更新与下载安装

设置页“设备”分组新增“检查更新”入口，遥控器一键直达。支持直接在线下载并安装新版本，免去重复拔插 U 盘拷贝安装包的繁琐流程。下载过程实时展示进度与体积，并由系统弹出安装确认界面。

## 功能设计与返修优化

### 1. 手动检查更新与生命周期保护
- 设置页“设备”分组的“设备与启动信息”后增加“检查更新”，展示当前已安装版本号（如 `v0.9.8`）。
- 点击后弹出 TV 原生风格对话框，异步请求 HTTPS 清单文件（`https://mfcheer.github.io/luma/downloads/update.json`）。
- **生命周期防护**：`MainActivity` 保存唯一 `updateDialog` 引用，防止重复弹窗；在 `onDestroy()` 中主动 `dismiss()` 与 `cancel()`，杜绝 Window 泄漏与悬挂下载；异步回调严格核验 Activity 有效性，销毁后不执行任何 UI 操作。
- 若已是最新版本，展示“当前已是最新版”提示；若发现新版，展示新旧版本代号对比、安装包大小及更新日志，主焦点默认落在“下载并更新”。

### 2. 系统最低版本 (minSdk) 兼容拦截与规范提示
- 在解析清单时严格校验 `minSdk`（1~100 整数边界）。
- 检查更新后立即比对当前系统 `Build.VERSION.SDK_INT` 与清单 `minSdk`：
  - 若系统版本低于要求，直接展示“设备系统暂不支持”提示，统一采用 **API 等级**展示（如“要求最低系统版本 API 23，当前设备系统为 API 21”），避免将 API 等级与 Android 版本混淆。**禁用并隐藏下载更新入口**，仅提供确认关闭按钮。
  - APK 安装前同步核查安装包内嵌的 `minSdkVersion`，杜绝不兼容版本进入下载或安装。

### 3. 严格 JSON 解析与防截断/类型强制转换
- 规避 Android 与 JVM 原生 `JSONObject.getInt / optInt / optLong` 的隐式类型强转与截断漏洞：
  - `versionCode` 严格核验原始类型为整型 Number（禁止 `Double`、`Float`、`BigDecimal` 小数及 `String`/`Boolean`/`null`），校验取值在 `1` 至 `Integer.MAX_VALUE` 范围内，杜绝 `4294967322` 溢出截断为 `26` 或 `26.9` 截断为 `26`。
  - `size` 字段严格限制为整型 Number，禁止 `"oops"` 字符串、布尔值或显式 null 回落至默认 0，范围在 `0` 至 `50MB` 之间；仅在清单真正未提供 `size` 字段时缺省为 0。
  - `minSdk` 字段严格限制为整型 Number，禁止 `"oops"` 或超大数值（如 `4294967319`）回落至默认 23，范围严格在 `1` 至 `100` 之间；仅在真正缺失时缺省为 23。
  - `releaseNotes`、`versionName`、`downloadUrl`、`sha256` 均做严格字符串类型及格式/安全性校验。

### 4. 下载、进度与安全文件名
- 采用 HTTPS 安全流式下载至应用内部缓存的临时文件，限制最大包体不超过 50MB。
- **安全文件名**：不再使用外部字符串 `versionName` 拼接本地文件名，统一采用强校验的整数 `versionCode` 生成本地缓存文件：`still_update_{versionCode}.tmp` 与 `still_update_{versionCode}.apk`，杜绝任何路径注入风险。
- 点击“下载并更新”前，核查存储可用空间（包体大小 + 10MB 安全留白）；若空间不足则明确拦截。
- 界面实时展示百分比与已下载字节量，进度条平滑填充；支持随时遥控器点击“取消下载”并安全清理临时文件。
- 断网或超时后，旧版完整保留可用，并提供“重试”与“取消”入口。

### 5. 安装移交与状态持久性恢复
- 下载完成后校验文件的 SHA-256 哈希值；校验通过后原子重命名为目标 APK。
- 深度校验：结构合法性、包名匹配 `com.jianping.tv`、版本代号不低于当前版本、数字签名证书与当前运行应用完全一致。
- 校验通过后，通过轻量级原生 `StillFileProvider`（`content://com.jianping.tv.fileprovider/...`）调起系统安装确认界面。
- **状态持久与异常重试**：
  - `MainActivity` 在 `onCreate` 与 `onActivityResult` 中调起系统安装器时，**仅在安装器成功启动后才清理 `pendingInstallApk = null`**；若启动过程中发生异常，保留待安装文件引用与状态。
  - 授权返回或 Activity 被系统回收重建时，`pendingInstallApk` 通过 `onSaveInstanceState` 完整持久化，确保安装器异常或权限授权往返后依然可无缝重试。
  - 对 `requestInstallPermission` 调用进行完整异常捕获，若未知来源设置 Intent 无法解析，自动尝试系统安全设置或常规设置，并在异常时保留已核验的 `pendingInstallApk`。

### 6. 遥控器 D-pad 与更新日志滚动
- 弹窗内更新日志 `notesScroll` 赋予独立 View ID 并开启触点/焦点支持。
- 配置完整的 D-pad 导航链：按钮按向上键聚焦至更新日志，支持上下方向键直接滚动长日志；在日志上按向下键流畅平移聚焦至操作按钮，按返回键安全退出。

### 7. 纯净与克制原则
- 新增 `android.permission.INTERNET` 与 `android.permission.REQUEST_INSTALL_PACKAGES` 权限，仅用于用户主动发起的手动版本更新。
- **坚决不做后台自动检查、静默安装、统计打点或任何推荐流**；不主动发起任何非用户触发的网络请求。

## 验证记录

- 版本 0.9.8，versionCode 26，minSdk 23，compileSdk/targetSdk 35。
- **构建与 Lint**：`./gradlew assembleDebug test lint` 构建成功，Lint 0 错误。
- **单测说明**：`./gradlew test` 目前状态为 `NO-SOURCE`（工程架构规范所有业务测试均以独立 Java/Python 脚本组织在 `scripts/` 下执行，不依赖 Gradle 单测任务）。
- **更新独立自动化测试 (`scripts/AppUpdateTest.java`)** 全量通过：
  - **JSON 入口直接驱动类型与范围校验**：覆盖 `versionCode=4294967322` 溢出拒绝、`26.9` 小数截断拒绝、字符串 `"26"` 拒绝、布尔与 null 拒绝、Integer.MAX_VALUE 边界通过；`size="oops"`/布尔/null/小数拒绝，0与50MB边界通过，缺失缺省0；`minSdk="oops"`/`4294967319`溢出/布尔/null/小数拒绝，1与100边界通过，缺失缺省23；`releaseNotes` 非字符串/null拒绝；`versionName` 穿越拒绝、非字符串拒绝；`downloadUrl` 非HTTPS拒绝；`sha256` 非64位十六进制拒绝。
  - 真实 JSON 解析（含转义引号 `\"`、换行 `\n`、特殊符号及完整字段）及双向序列化。
  - 畸形 JSON 语法错误、空输入、缺失必填键拒绝。
  - `versionName` 字符安全校验（包含路径穿越 `../`、斜杠、空格、换行符均抛出异常拒绝）。
  - HTTPS 协议强制（拒绝 HTTP、FTP、FILE、JS 等协议）。
  - `isSystemCompatible` 系统兼容性判定（支持自定义 API Level 与当前运行时判定）。
  - SHA-256 计算与数据篡改探测。
  - 存储空间安全留白判断。
  - `StillFileProvider.resolveFileForUri` 真实路径边界测试（正常文件名解析、路径穿越尝试 `../../` 限制在 `baseDir` 内部、空/点路径安全返回 null）。
- **既有单元测试回归**：
  - `AppOrderTest`：排序维护与首尾移动通过。
  - `AppSearchTest`：中文/拼音/别名索引与搜索排序通过。
  - `NetworkMonitorTest`：网络状态与类型监听通过。
  - `SearchAliasesTest`：词典与别名解析通过。
  - `WakeCycleTest`：待机休眠轮询通过。
  - `ArgbBlurTest`：环境光模糊计算通过。
- **签名连续性核验**：
  使用 `apksigner` 核验，Signer #1 证书 SHA-256 摘要为：
  `ab4f40cc3090987f0d2734e169762a40fa4426bc912fdcdc721d2c1332e44b43`
  与 0.9.7、0.9.5 完全一致，同证书覆盖升级可保留既有排序、应用重命名与设置。
- **未验项目**：本轮未连接或操作用户真实电视与手机，未通过 ADB 发送控制指令；电视实机在线下载与系统安装弹窗交互待用户在真实设备上体验复核。

## 本地发布产物

- 官网下载包：`website/downloads/Still-0.9.8.apk`（708,699 字节）
- 输出归档包：`output/Still-0.9.8-debug.apk`（与官网下载包哈希逐字节一致）
- SHA-256 校验和：
  ```text
  746cdbd5308fb853c6f072861fe70fcf464f0421aaecb9bca259ed8976058466
  ```
- 更新清单文件：`website/downloads/update.json` 与 `website/update.json` 已生成完备。
- 线上说明：当前发布物已在本地准备就绪，**尚未推送远程仓库或公开发布**，待用户进一步确认。
