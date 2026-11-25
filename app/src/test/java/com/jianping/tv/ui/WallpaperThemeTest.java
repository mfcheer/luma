package com.jianping.tv.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.jianping.tv.R;
import org.junit.Test;

public class WallpaperThemeTest {

  @Test
  public void testAllThemesCountAndOrder() {
    assertEquals(9, WallpaperTheme.ALL.size());
    assertEquals("sonoma", WallpaperTheme.ALL.get(0).key);
    assertEquals("big_sur", WallpaperTheme.ALL.get(1).key);
    assertEquals("catalina", WallpaperTheme.ALL.get(2).key);
    assertEquals("mojave", WallpaperTheme.ALL.get(3).key);
    assertEquals("sequoia", WallpaperTheme.ALL.get(4).key);
    assertEquals("patagonia", WallpaperTheme.ALL.get(5).key);
    assertEquals("yosemite", WallpaperTheme.ALL.get(6).key);
    assertEquals("pure_dark", WallpaperTheme.ALL.get(7).key);
    assertEquals("pure_light", WallpaperTheme.ALL.get(8).key);
  }

  @Test
  public void testThumbnailResourcesAreValid() {
    for (WallpaperTheme theme : WallpaperTheme.ALL) {
      assertNotNull(theme.key);
      assertNotNull(theme.name);
      assertTrue("Thumbnail resource must be non-zero for " + theme.key, theme.thumbRes != 0);
    }
  }

  @Test
  public void testLandscapeVsPureThemes() {
    for (int i = 0; i < 7; i++) {
      WallpaperTheme theme = WallpaperTheme.ALL.get(i);
      assertFalse(theme.pure);
      assertFalse(theme.light);
      assertTrue("Landscape wallpaperRes must be non-zero for " + theme.key, theme.wallpaperRes != 0);
      assertTrue("Landscape blurRes must be non-zero for " + theme.key, theme.blurRes != 0);
    }

    WallpaperTheme pureDark = WallpaperTheme.find("pure_dark");
    assertTrue(pureDark.pure);
    assertFalse(pureDark.light);
    assertEquals(0, pureDark.wallpaperRes);
    assertEquals(0, pureDark.blurRes);

    WallpaperTheme pureLight = WallpaperTheme.find("pure_light");
    assertTrue(pureLight.pure);
    assertTrue(pureLight.light);
    assertEquals(0, pureLight.wallpaperRes);
    assertEquals(0, pureLight.blurRes);
  }

  @Test
  public void testFindFallbackAndIndex() {
    assertEquals("sonoma", WallpaperTheme.find("sonoma").key);
    assertEquals("patagonia", WallpaperTheme.find("patagonia").key);
    assertEquals("sonoma", WallpaperTheme.find("invalid_key").key);
    assertEquals("sonoma", WallpaperTheme.find(null).key);

    assertEquals(0, WallpaperTheme.indexOf("sonoma"));
    assertEquals(5, WallpaperTheme.indexOf("patagonia"));
    assertEquals(8, WallpaperTheme.indexOf("pure_light"));
    assertEquals(0, WallpaperTheme.indexOf("invalid_key"));
  }

  @Test
  public void testThemeThumbnailHelper() {
    assertEquals(R.drawable.thumb_wallpaper_sonoma, WallpaperGalleryView.themeThumbnail("sonoma"));
    assertEquals(R.drawable.thumb_wallpaper_patagonia, WallpaperGalleryView.themeThumbnail("patagonia"));
    assertEquals(R.drawable.thumb_wallpaper_pure_dark, WallpaperGalleryView.themeThumbnail("pure_dark"));
    assertEquals(R.drawable.thumb_wallpaper_pure_light, WallpaperGalleryView.themeThumbnail("pure_light"));
  }
}
