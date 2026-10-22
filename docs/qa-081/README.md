# Still 0.8.1 验证（2026-10-22）

范围：修复真机首次加载慢、应用返回重复读取、默认桌面点击无可见反馈，以及连续向下滚动时顶部图标上半部闪亮。版本Code15，minSdk23，包名和调试签名不变，无新增权限、依赖或网络访问。

用户实机信息：小米电视5 Pro 55英寸，MiTV-ANSP0，Android9/API28，系统报告ABI为armeabi-v7a/armeabi，Build.DISPLAY为23.6.5.3226。用户照片确认基础显示，用户进一步实测从其他应用按Home可回Still；不能据此推断开机、待机唤醒全部通过。照片仅留在本地忽略目录，未加入仓库。

以下自动验证在连接真机前完成：Android TV API33 / ARM64 / 1920×1080 / JianpingTest / Apple M1硬件渲染，28个启动应用含24个独立测试应用。该阶段没有连接或改动用户电视，下列耗时和QA目录截图仅属于模拟器。之后已通过用户授权的ADB在小米电视覆盖安装0.8.1、保留原偏好，并采集41个真实应用的浅深主题与交互素材；详见[实机展示与官网](../../docs/实机展示与官网.md)。受控加载性能、系统重启和开机仍未在真机完整验证。

## 加载与应用业务

- `catalog-smoke.txt`：6项通过。空缓存准备28项；连续4次打开其他应用并Back返回，进程、选中卡片与边界保持，目录工作为零；强停后磁盘恢复；其他应用在前台时实际杀死Still进程，Back恢复原焦点及滚动位置；缺一张图片只重建1项、复用27项；损坏索引从已有单项素材恢复，偏好不变。
- `timings.json` / `disk-log.txt`：本轮一个样本磁盘目录准备51ms，后续查询复用28项，图标准备0ms。首次空缓存仍需生成图标；API/ART/文件缓存会影响耗时。`baseline-timing.txt`是单次旧版观测，不构成受控性能基准，也不据此承诺电视加速比例。
- `all-apps-smoke.txt`：缓存与桌面入口修复后的完整4组业务回归通过，包含全量应用、旧收藏迁移、排序保存/取消/重启、真实包更新/卸载/新装、整理期间包变化、外部应用及应用信息返回时的原焦点恢复；最后还原原偏好和测试应用。随后仅调整GridWallView的裁剪/渐隐，最终包再完成下述滚动定向复核；未对该纯显示改动重复安装/卸载流程。`package-events-log.txt`记录受影响包重建，其余素材复用。
- 初始完整业务测试发现应用信息返回后失去焦点，已修复外部操作前的焦点交接，重跑完整脚本通过；失败记录保留在`all-apps-initial-focus.txt`。早期缓存脚本对杀死后`pidof`的非零退出处理不当，已修正测试脚本，保留`catalog-initial-script-pid.txt`，不将脚本错误记作产品通过。
- `blur-test.txt` / `order-test.txt`：滑动窗口模糊与原半径2参考算法逐像素相同，覆盖随机ARGB、透明边缘、单像素维度和实际预览尺寸；纯Java顺序归并检查通过。

素材继续按原640dpi读取，卡片384×250、预览128×83缓存。`artwork-comparison.jpg`对照旧版和缓存版；最终包首屏区域[50,145,1870,1010]排除时钟后RGB像素完全相同（`artwork-pixel-check.json`），未以降低图标密度换取提速。

## 默认桌面

`home-entry-smoke.txt`最终4项通过：点击显示状态/操作面板、关闭回原设置行；系统入口打开实际可见的默认桌面选择页；“打开其他桌面”仅列出真实Android TV Home并显式打开，过滤FallbackHome；报告列出解析组件和候选，偏好保持不变。截图：`home-options.png`、`home-system-result.png`、`other-home-choices.png`、`other-home.png`、`device-info.png`。

原生选择器支持路径在模拟器验证通过；小米固件不提供/不显示选择页时的说明和回退已实现，但本次没有真实固件来验证该分支。模拟器的解析组件、选择页标记也可能不一致，实际从其他应用按Home的结果优先。测试没有修改默认桌面、禁用原厂桌面或改动开机设置。

系统解析没有唯一目标时可能返回解析入口，见[PackageManager.resolveActivity](https://developer.android.com/reference/android/content/pm/PackageManager#resolveActivity(android.content.Intent,%20int))。系统设置入口参考[HOME_SETTINGS](https://developer.android.com/reference/android/provider/Settings#ACTION_HOME_SETTINGS)和[MANAGE_DEFAULT_APPS_SETTINGS](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_DEFAULT_APPS_SETTINGS)，不能假定所有固件都显示对应界面。

## 顶部跨行闪动

根因在GridWallView：旧版整行对齐时将顶部渐隐从1直接切成0，又让裁剪上边界跳动16逻辑像素。312px行高附近的实际采样为311px→312px→313px，渐隐值为1→0→1，造成上半部突然变亮再变暗。此问题在API33也能确定复现，并非仅凭照片推测。

修复后裁剪固定在视口，渐隐范围按到最近整行边界的距离连续收放，并从真实视口顶部覆盖上浮余量。48逻辑像素最大范围、完整行的悬浮空间、原底部渐隐保留；未新增位图分配或常驻动画。Android9原生渐隐的坐标与范围行为核对了[AOSP View源码](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/core/java/android/view/View.java)。

`ScrollEdgeProbe.java`由独立测试APK通过Instrumentation直接定位两处行边界，按单像素偏移取样实际窗口；测试APK不包含于产品，执行后卸载。`scroll/before`、`scroll/after`保存旧/新版截图及渐隐值；联系图`scroll/before-contact.png`、`scroll/after-contact.png`可见旧版中间一张明显变亮，新版连续。浅色区域[95,164,1825,300]的相邻帧平均RGB差，旧版跨边界约25.7/25.8，新版约1.96/2.16，与旁边普通单像素步进接近。该测量证明边界连续性，不是帧率指标。

`scroll-smoke.txt`最终通过：浅深色两种主题均完成连续上下与快速反向，回首行保持原焦点/边界；动画落稳后两张闲置画面像素相同；跨行整理/撤销和外部应用返回保持原焦点/偏移；深色也通过两处行边界单像素连续性检查。主题切换即时生效，原偏好完整还原。`scroll-edge-metrics.json`保存原始区域和数值。

`scroll-light.mp4`、`scroll-dark.mp4`是真实按键与原生窗口录像（本地忽略文件），`scroll-light-contact.jpg`为逐秒抽帧。只用于观察滚动状态，不代表电视帧率。最终公开预览为`../screenshots/still-081-home.png`和`still-081-settings.png`。

## 构建与交付

`build.txt`：assembleDebug/lintDebug成功，0错误、16项既有警告。`apk-badging.txt`与`apk-signature.txt`核对版本、最低API和同一签名；未修改包名或申请新权限。最终源码包含缓存、桌面入口与滚动修复。

交付：`output/Still-0.8.1-debug.apk`，SHA-256 `d81908aabd0530302cb8bce50efbc6ff87fccb959d189b05b4240af0fd93dd55`；校验同步到`output/SHA256SUMS.txt`。同签名覆盖安装保留设置与应用顺序。
