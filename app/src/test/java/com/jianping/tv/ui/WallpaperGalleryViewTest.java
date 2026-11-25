package com.jianping.tv.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import com.jianping.tv.R;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WallpaperGalleryViewTest {

  private Activity activity;
  private UiPalette palette;
  private static final String[] THEME_KEYS = {
    "sonoma", "big_sur", "catalina", "mojave", "sequoia", "patagonia", "yosemite", "pure_dark", "pure_light"
  };
  private static final String[] THEME_NAMES = {
    "Sonoma（晨曦）", "Big Sur（海岸）", "Catalina（夜湾）", "Mojave（沙丘）",
    "Sequoia（红杉）", "Patagonia（雪峰）", "Yosemite（苍谷）", "经典深色", "经典浅色"
  };

  @Before
  public void setUp() {
    activity = Robolectric.buildActivity(Activity.class).setup().get();
    palette = UiPalette.from(false);
  }

  @Test
  public void testSettingsRowViewThumbnail() {
    SettingsRowView row = new SettingsRowView(activity, 2.0f, "外观与壁纸", R.drawable.settings_theme, "Sonoma", false, palette);
    assertEquals(View.GONE, row.thumbContainer().getVisibility());

    row.preview(R.drawable.thumb_wallpaper_sonoma);
    assertEquals(View.VISIBLE, row.thumbContainer().getVisibility());
    assertNotNull(row.previewThumb().getDrawable());

    row.preview(0);
    assertEquals(View.GONE, row.thumbContainer().getVisibility());
  }

  @Test
  public void testGalleryViewCreationAndKeyLifecycle() {
    AtomicReference<String> previewed = new AtomicReference<>();
    AtomicReference<String> confirmed = new AtomicReference<>();
    AtomicBoolean canceled = new AtomicBoolean(false);

    WallpaperGalleryView gallery =
        new WallpaperGalleryView(
            activity,
            2.0f,
            THEME_KEYS,
            THEME_NAMES,
            "sonoma",
            new WallpaperGalleryView.WallpaperGalleryListener() {
              @Override
              public void onWallpaperPreview(String key) {
                previewed.set(key);
              }

              @Override
              public void onWallpaperConfirmed(String key) {
                confirmed.set(key);
              }

              @Override
              public void onWallpaperCanceled(String originalKey) {
                canceled.set(true);
              }
            },
            palette);

    assertNotNull(gallery);
    assertEquals(ViewGroup.FOCUS_AFTER_DESCENDANTS, gallery.getDescendantFocusability());

    // Back key dispatches cancel
    KeyEvent backKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK);
    boolean handledBack = gallery.dispatchKeyEvent(backKey);
    assertTrue(handledBack);
    assertTrue(canceled.get());
  }

  @Test
  public void testGalleryConfirmDispatchesSelection() {
    AtomicReference<String> confirmed = new AtomicReference<>();

    WallpaperGalleryView gallery =
        new WallpaperGalleryView(
            activity,
            2.0f,
            THEME_KEYS,
            THEME_NAMES,
            "patagonia",
            new WallpaperGalleryView.WallpaperGalleryListener() {
              @Override
              public void onWallpaperPreview(String key) {}

              @Override
              public void onWallpaperConfirmed(String key) {
                confirmed.set(key);
              }

              @Override
              public void onWallpaperCanceled(String originalKey) {}
            },
            palette);

    // Confirm dispatches confirmed key
    KeyEvent enterKey = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER);
    boolean handledEnter = gallery.dispatchKeyEvent(enterKey);
    assertTrue(handledEnter);
    assertEquals("patagonia", confirmed.get());
  }
}
