# Still 0.8.3 移到最前（2026-10-22）

范围仅为长按菜单新增“移到最前”。菜单顺序为移动位置、移到最前、应用信息；复用现有菜单样式。置前立即保存，焦点跟随到首页首位，其余应用相对顺序不变。已在首位时重复执行不会复制应用或改变其他顺序；应用已被卸载或入口变更时，不保存过期入口。

版本 Code 17，包名、签名、最低 API 23、权限和依赖保持不变。

## 回归

专用 Android TV API 33 / ARM64 / 1920×1080 模拟器，28 个启动应用（含 24 个测试应用）。`scripts/move-first-083-smoke.py` 使用实际遥控器按键，浅深主题分别检查：

- 从末行置前，完整顺序等于选中应用加上原列表其余内容，焦点和卡片边界回到首位。
- 强停后重新打开，顺序和首位应用保留；已在首位时重复置前保持顺序不变。
- 菜单取消不保存，应用信息仍可选中；原手动排序取消和保存正常，首页返回首行正常。
- 完成后恢复完整原偏好；名称开关和开机开关不变。

结果见 [move-first-smoke.json](move-first-smoke.json)，菜单截图为 `light-menu.png`、`dark-menu.png`，均来自测试模拟器，未替换官网的旧版实机素材。

构建和 Lint 通过，0 错误、16 项既有警告，见 [build.txt](build.txt)。[APK 信息](apk-badging.txt) 与[签名](apk-signature.txt) 已核对。

## 交付

[Still 0.8.3 APK](../../website/downloads/Still-0.8.3.apk)，本地副本 `output/Still-0.8.3-debug.apk`。SHA-256：`fa8d23419031d3b686b166ad674a04a7ccf7a1ade4ebfbae1b5100b381e8613b`。

小米 Android 9 电视已在待机中同签名覆盖更新到 0.8.3，41 应用顺序及完整偏好前后相同，安装后仍为 Asleep / display OFF，见 [tv-update.json](tv-update.json)。本版菜单按键尚未在电视实测。
