# 焦点名称与浮起参考

`home-concept.png` 为内置 ImageGen 生成的单版视觉参考，不是应用截图。
输入参考为 `docs/qa-090/light-home-final.png`。保持五列、浅色背景和两行半，
只在选中项下方居中显示名称；柔和下投影替代环绕光晕，环境光局部回应焦点。
模拟器中的正式实现见 `docs/qa-092/`，生成图不用于商店或官网实机素材。

主要提示：Refine the supplied actual Still TV screenshot; preserve its five columns,
off-white background, generous row spacing, app order, artwork and top-right controls.
Only the selected Settings card shows a centered caption. Target 14% enlargement,
slight lift, downward soft shadow, no luminous halo, a very faint localized blue-gray
ambient wash, and a gently fading bottom continuation row. Flat 16:9 UI, no new sections.

生成图对放大比例的表达不作为测量依据；实际实现使用1.14倍放大。
按用户最终选择，保留此前首页的轻微浮起：首页和搜索选中卡片的底边均上移约5px
（1920×1080基准，随屏幕尺寸等比适配），不会因搜索卡片较大而增加上浮距离。
