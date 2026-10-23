"use strict";
const preview = document.getElementById("theme-preview");
const controls = [...document.querySelectorAll("[data-theme]")];
const previewStatus = document.getElementById("preview-status");
let latestRequest = 0;
controls.forEach((button) => {
  button.addEventListener("click", async () => {
    const request = ++latestRequest;
    const dark = button.dataset.theme === "dark";
    const next = new Image();
    next.src = dark ? "./assets/home-dark.webp?v=090" : "./assets/home-light.webp?v=090";
    preview.setAttribute("aria-busy", "true");
    try {
      await next.decode();
      if (request !== latestRequest) return;
      preview.src = next.src;
      preview.alt = `Still 0.9.0 ${dark ? "深色" : "浅色"}首页，小米电视实机截屏，带搜索入口、五列应用卡片与底部渐隐续行`;
      controls.forEach((item) =>
        item.setAttribute("aria-pressed", String(item === button)),
      );
      previewStatus.textContent = `已切换为${dark ? "深色" : "浅色"}实机画面`;
    } catch {
      if (request === latestRequest)
        previewStatus.textContent = "暂时无法加载这张预览，请稍后重试。";
    } finally {
      if (request === latestRequest) preview.removeAttribute("aria-busy");
    }
  });
});
