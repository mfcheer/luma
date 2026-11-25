package com.jianping.tv.ui;

import com.jianping.tv.R;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Metadata and resource bindings for launcher appearance presets and wallpapers.
 */
public final class WallpaperTheme {
  public final String key;
  public final String name;
  public final int thumbRes;
  public final int wallpaperRes;
  public final int blurRes;
  public final boolean pure;
  public final boolean light;

  public WallpaperTheme(
      String key,
      String name,
      int thumbRes,
      int wallpaperRes,
      int blurRes,
      boolean pure,
      boolean light) {
    this.key = key;
    this.name = name;
    this.thumbRes = thumbRes;
    this.wallpaperRes = wallpaperRes;
    this.blurRes = blurRes;
    this.pure = pure;
    this.light = light;
  }

  public static final List<WallpaperTheme> ALL;

  static {
    List<WallpaperTheme> list = new ArrayList<>();
    list.add(
        new WallpaperTheme(
            "sonoma",
            "Sonoma（晨曦）",
            R.drawable.thumb_wallpaper_sonoma,
            R.drawable.wallpaper_sonoma,
            R.drawable.wallpaper_sonoma_blur,
            false,
            false));
    list.add(
        new WallpaperTheme(
            "big_sur",
            "Big Sur（海岸）",
            R.drawable.thumb_wallpaper_big_sur,
            R.drawable.wallpaper_big_sur,
            R.drawable.wallpaper_big_sur_blur,
            false,
            false));
    list.add(
        new WallpaperTheme(
            "catalina",
            "Catalina（夜湾）",
            R.drawable.thumb_wallpaper_catalina,
            R.drawable.wallpaper_catalina,
            R.drawable.wallpaper_catalina_blur,
            false,
            false));
    list.add(
        new WallpaperTheme(
            "mojave",
            "Mojave（沙丘）",
            R.drawable.thumb_wallpaper_mojave,
            R.drawable.wallpaper_mojave,
            R.drawable.wallpaper_mojave_blur,
            false,
            false));
    list.add(
        new WallpaperTheme(
            "sequoia",
            "Sequoia（红杉）",
            R.drawable.thumb_wallpaper_sequoia,
            R.drawable.wallpaper_sequoia,
            R.drawable.wallpaper_sequoia_blur,
            false,
            false));
    list.add(
        new WallpaperTheme(
            "patagonia",
            "Patagonia（雪峰）",
            R.drawable.thumb_wallpaper_patagonia,
            R.drawable.wallpaper_patagonia,
            R.drawable.wallpaper_patagonia_blur,
            false,
            false));
    list.add(
        new WallpaperTheme(
            "yosemite",
            "Yosemite（苍谷）",
            R.drawable.thumb_wallpaper_yosemite,
            R.drawable.wallpaper_yosemite,
            R.drawable.wallpaper_yosemite_blur,
            false,
            false));
    list.add(
        new WallpaperTheme(
            "pure_dark",
            "经典深色",
            R.drawable.thumb_wallpaper_pure_dark,
            0,
            0,
            true,
            false));
    list.add(
        new WallpaperTheme(
            "pure_light",
            "经典浅色",
            R.drawable.thumb_wallpaper_pure_light,
            0,
            0,
            true,
            true));
    ALL = Collections.unmodifiableList(list);
  }

  public static WallpaperTheme find(String key) {
    if (key != null) {
      for (WallpaperTheme item : ALL) {
        if (item.key.equals(key)) return item;
      }
    }
    return ALL.get(0);
  }

  public static int indexOf(String key) {
    if (key != null) {
      for (int i = 0; i < ALL.size(); i++) {
        if (ALL.get(i).key.equals(key)) return i;
      }
    }
    return 0;
  }
}
