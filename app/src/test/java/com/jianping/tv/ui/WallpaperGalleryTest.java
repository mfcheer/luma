package com.jianping.tv.ui;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.jianping.tv.R;
import org.junit.Test;

public class WallpaperGalleryTest {

  @Test
  public void themeThumbnail_resolvesAllKnownThemes() {
    String[] themes = {
      "sonoma",
      "big_sur",
      "catalina",
      "mojave",
      "sequoia",
      "patagonia",
      "yosemite",
      "pure_dark",
      "pure_light"
    };

    for (String theme : themes) {
      int resId = WallpaperGalleryView.themeThumbnail(theme);
      assertNotEquals("Theme " + theme + " must have a non-zero thumbnail resource", 0, resId);
    }
  }

  @Test
  public void themeThumbnail_fallsBackToSonomaOnUnknownOrNull() {
    int nullRes = WallpaperGalleryView.themeThumbnail(null);
    int unknownRes = WallpaperGalleryView.themeThumbnail("unknown_theme_key");
    org.junit.Assert.assertEquals(R.drawable.thumb_wallpaper_sonoma, nullRes);
    org.junit.Assert.assertEquals(R.drawable.thumb_wallpaper_sonoma, unknownRes);
  }
}
