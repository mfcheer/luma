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
    next.src = dark ? "./assets/home-dark.webp?v=097dev-1213f1831b8e" : "./assets/home-light.webp?v=097dev-a7e7e99fef36";
    preview.setAttribute("aria-busy", "true");
    try {
      await next.decode();
      if (request !== latestRequest) return;
      preview.src = next.src;
      preview.alt = `Still 0.9.7 开发版 ${dark ? "深色" : "浅色"}首页，小米电视实机截屏，选中应用显示名称，五列卡片与底部渐隐续行`;
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


// Dual features mobile tabs
const featureTabs = [...document.querySelectorAll(".dual-features-tabs .tab-btn")];
const featureStage = document.querySelector(".dual-features-stage");
const featureCards = [...document.querySelectorAll(".dual-feature-card")];

if (featureTabs.length && featureStage) {
  featureTabs.forEach((tab, index) => {
    tab.addEventListener("click", () => {
      featureTabs.forEach((t) => {
        t.classList.remove("active");
        t.setAttribute("aria-selected", "false");
      });
      tab.classList.add("active");
      tab.setAttribute("aria-selected", "true");
      if (featureCards[index]) {
        featureCards[index].scrollIntoView({ behavior: "smooth", block: "nearest", inline: "center" });
      }
    });
  });

  featureStage.addEventListener("scroll", () => {
    const scrollLeft = featureStage.scrollLeft;
    const cardWidth = featureCards[0] ? featureCards[0].offsetWidth : 300;
    const activeIndex = Math.round(scrollLeft / (cardWidth + 14));
    featureTabs.forEach((t, i) => {
      const isCurrent = i === activeIndex;
      t.classList.toggle("active", isCurrent);
      t.setAttribute("aria-selected", String(isCurrent));
    });
  }, { passive: true });
}
