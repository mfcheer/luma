package com.jianping.tv.ui;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;
import static org.robolectric.util.ReflectionHelpers.ClassParameter.from;

import android.content.ComponentName;
import android.content.Intent;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import com.jianping.tv.data.AppEntry;
import com.jianping.tv.data.Preferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, qualifiers = "w960dp-h540dp-mdpi")
public class MainActivityScreensaverTest {
  private ActivityController<MainActivity> controller;
  private MainActivity activity;
  private AppCatalogCache catalog;
  private final List<AppEntry> apps = new ArrayList<>();

  @Before
  public void setUp() {
    for (int i = 0; i < 25; i++) {
      ComponentName component = new ComponentName("example.app" + i, "Main");
      apps.add(new AppEntry(component, "App " + i, "original"));
    }
    catalog = new AppCatalogCache(RuntimeEnvironment.getApplication());
    catalog.remember(snapshot(apps));
    controller = Robolectric.buildActivity(MainActivity.class).create();
    activity = controller.get();
    // Keep the test catalog deterministic while exercising the actual Activity and views.
    Future<?> task = field("loadTask");
    task.cancel(true);
    ReflectionHelpers.setField(activity, "loadGeneration", 10);

    shadowOf(activity.getSystemService(PowerManager.class)).setIsInteractive(true);
    controller.start().resume().visible().windowFocusChanged(true);
    idle(500);
  }

  @After
  public void tearDown() {
    controller.pause().stop().destroy();
    catalog.remember(null);
  }

  @Test
  public void restoresCardAndWallpaperAndConsumesEntireOkAndBackPresses() {
    GridWallView wall = field("homeWall");
    wall.focusKey(apps.get(12).id, true);
    idle(500);
    wall.scrollTo(0, wall.getScrollY() + 27);
    int originalOffset = wall.getScrollY();
    assertTrue(originalOffset > 0);
    View originalFocus = activity.getCurrentFocus();
    ImageView wallpaper = field("wallpaperView");
    // Robolectric's default bitmap decoder uses square placeholders for drawable resources.
    ShapeDrawable scenicImage = new ShapeDrawable();
    scenicImage.setIntrinsicWidth(1920);
    scenicImage.setIntrinsicHeight(1080);
    wallpaper.setImageDrawable(scenicImage);
    View blur = field("wallpaperBlurView");
    float originalTranslation = wallpaper.getTranslationY();
    float originalBlur = blur.getAlpha();
    assertTrue(originalBlur > 0f);

    activity.onWindowFocusChanged(true);
    idle(59_999);
    assertNull(field("screensaverOverlay"));
    idle(1);
    assertNotNull(field("screensaverOverlay"));
    idle(350);
    assertEquals(0f, wall.getAlpha(), 0f);
    assertEquals(0f, ((View) field("clock")).getAlpha(), 0f);
    assertEquals(0f, ((View) field("calendarDate")).getAlpha(), 0f);
    assertEquals(0f, wallpaper.getTranslationY(), 0f);
    assertEquals(0f, blur.getAlpha(), 0f);
    assertEquals(0f, ((View) field("ambient")).getAlpha(), 0f);
    assertEquals(0f, ((View) field("bottomGradient")).getAlpha(), 0f);
    assertEquals(0f, ((View) field("scrimView")).getAlpha(), 0f);
    RectF imageBounds = new RectF(
        0, 0, wallpaper.getDrawable().getIntrinsicWidth(), wallpaper.getDrawable().getIntrinsicHeight());
    wallpaper.getImageMatrix().mapRect(imageBounds);
    assertEquals(ImageView.ScaleType.MATRIX, wallpaper.getScaleType());
    assertEquals(0f, imageBounds.left, 1f);
    assertEquals(0f, imageBounds.top, 1f);
    assertEquals(960f, imageBounds.right, 1f);
    assertEquals(540f, imageBounds.bottom, 1f);

    for (int key : new int[] {KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK}) {
      long down = SystemClock.uptimeMillis();
      assertTrue(activity.dispatchKeyEvent(event(down, key, KeyEvent.ACTION_DOWN, 0, 7, 0)));
      idle(800);
      assertTrue(activity.dispatchKeyEvent(event(down, key, KeyEvent.ACTION_DOWN, 1, 7, 0)));
      assertTrue(activity.dispatchKeyEvent(event(down, key, KeyEvent.ACTION_UP, 0, 7, 0)));
      idle(350);
      assertSame(originalFocus, activity.getCurrentFocus());
      assertEquals(originalOffset, wall.getScrollY());
      assertEquals(originalTranslation, wallpaper.getTranslationY(), 0f);
      assertEquals(originalBlur, blur.getAlpha(), 0f);
      assertEquals(ImageView.ScaleType.CENTER_CROP, wallpaper.getScaleType());
      assertNull(shadowOf(activity).getNextStartedActivity());

      if (key == KeyEvent.KEYCODE_DPAD_CENTER) idle(60_000);
    }

    // A separate Back press is delivered normally and takes the wall back to the first row.
    long down = SystemClock.uptimeMillis();
    activity.dispatchKeyEvent(event(down, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_DOWN, 0, 7, 0));
    activity.dispatchKeyEvent(event(down, KeyEvent.KEYCODE_BACK, KeyEvent.ACTION_UP, 0, 7, 0));
    idle(500);
    assertEquals(apps.get(0).id, wall.currentKey());
  }

  @Test
  public void restartsIdlePeriodAfterSettingsAndKeepsHeaderFocusAcrossCatalogRefresh() {
    View settings = field("settingsButton");
    settings.performClick();
    idle(60_500);
    assertNull(field("screensaverOverlay"));

    activity.onBackPressed();
    idle(500);
    View search = field("searchButton");
    assertTrue(search.requestFocus());
    activity.onWindowFocusChanged(true);
    idle(60_350);
    View overlay = field("screensaverOverlay");
    assertNotNull(overlay);

    List<AppEntry> updated = new ArrayList<>(apps);
    updated.remove(0);
    acceptCatalog(updated);
    assertSame(overlay, field("screensaverOverlay"));
    FrameLayout root = field("root");
    assertSame(overlay, root.findFocus());

    activity.onBackPressed();
    idle(350);
    assertSame(search, root.findFocus());
    FrameLayout desktop = field("desktop");
    assertEquals(1f, desktop.getChildAt(desktop.getChildCount() - 1).getAlpha(), 0f);
    assertEquals(updated.get(0).id, ((GridWallView) field("homeWall")).currentKey());
  }

  @Test
  public void focusesNearestRemainingSlotWhenOriginalCardIsRemoved() {
    GridWallView wall = field("homeWall");
    AppEntry removed = apps.get(10);
    AppEntry next = apps.get(11);
    wall.focusKey(removed.id, true);
    idle(500);
    assertEquals(removed.id, wall.currentKey());

    idle(60_350);
    View overlay = field("screensaverOverlay");
    assertNotNull(overlay);
    List<AppEntry> updated = new ArrayList<>(apps);
    updated.remove(removed);
    acceptCatalog(updated);
    assertEquals(removed.id, wall.currentKey());

    long down = SystemClock.uptimeMillis();
    assertTrue(activity.dispatchKeyEvent(event(down, KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_DOWN, 0, 7, 0)));
    assertTrue(activity.dispatchKeyEvent(event(down, KeyEvent.KEYCODE_MENU, KeyEvent.ACTION_UP, 0, 7, 0)));
    idle(350);
    assertSame(wall, field("homeWall"));
    assertEquals(next.id, wall.currentKey());
    assertTrue(wall.findFocus() instanceof AppCardView);
  }

  @Test
  public void holdsOffWhileKeyIsDownAndDiscardsScreensaverOnWindowFocusLoss() {
    long down = SystemClock.uptimeMillis();
    activity.dispatchKeyEvent(event(down, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_DOWN, 0, 7, 0));
    idle(60_500);
    assertNull(field("screensaverOverlay"));
    activity.dispatchKeyEvent(event(down, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.ACTION_UP, 0, 7, 0));
    idle(59_999);
    assertNull(field("screensaverOverlay"));
    idle(1);
    assertNotNull(field("screensaverOverlay"));
    assertTrue(isKeepingScreenOn());

    controller.windowFocusChanged(false);
    assertNull(field("screensaverOverlay"));
    assertFalse(isKeepingScreenOn());
    idle(60_500);
    assertNull(field("screensaverOverlay"));
  }

  @Test
  public void cyclesPersistedDelaysWithoutOpeningScreensaverPreview() {
    View row = focusSetting("桌面屏保，1 分钟");
    FrameLayout root = field("root");
    assertTrue(row.getContentDescription().toString().contains("1 分钟"));
    press(KeyEvent.KEYCODE_DPAD_LEFT);
    assertTrue(row.getContentDescription().toString().contains("30 秒"));
    press(KeyEvent.KEYCODE_DPAD_LEFT);
    assertTrue(row.getContentDescription().toString().contains("关闭"));
    press(KeyEvent.KEYCODE_DPAD_LEFT);
    assertTrue(row.getContentDescription().toString().contains("2 分钟"));
    press(KeyEvent.KEYCODE_DPAD_RIGHT);
    assertEquals(0, new Preferences(activity).screensaverDelaySeconds());
    assertTrue(describedView(root, "桌面屏保，切换到下一项").performClick());
    assertEquals(30, new Preferences(activity).screensaverDelaySeconds());
    assertTrue(describedView(root, "桌面屏保，切换到上一项").performClick());
    assertEquals(0, new Preferences(activity).screensaverDelaySeconds());
    assertSame(row, root.findFocus());

    assertNoConfirmation(row);
    assertNull(field("screensaverOverlay"));
    assertFalse(isKeepingScreenOn());
    assertSame(row, root.findFocus());

    activity.onBackPressed();
    idle(120_500);
    assertNull(field("screensaverOverlay"));
    assertFalse(isKeepingScreenOn());
  }

  @Test
  public void cyclesWallpaperWithoutOpeningGallery() {
    View row = focusSetting("外观与壁纸，当前");
    FrameLayout root = field("root");
    press(KeyEvent.KEYCODE_DPAD_RIGHT);
    assertEquals("big_sur", new Preferences(activity).wallpaper());
    assertTrue(row.getContentDescription().toString().contains("Big Sur"));
    assertTrue(describedView(root, "外观与壁纸，切换到上一项").performClick());
    assertEquals("sonoma", new Preferences(activity).wallpaper());
    assertTrue(describedView(root, "外观与壁纸，切换到下一项").performClick());
    assertEquals("big_sur", new Preferences(activity).wallpaper());
    press(KeyEvent.KEYCODE_DPAD_LEFT);
    assertEquals("sonoma", new Preferences(activity).wallpaper());

    assertNoConfirmation(row);
    assertEquals(1, root.getChildCount());
    assertSame(row, root.findFocus());
    assertEquals("sonoma", new Preferences(activity).wallpaper());

    press(KeyEvent.KEYCODE_DPAD_LEFT);
    assertTrue(new Preferences(activity).lightTheme());
    View lightRow = describedView(field("root"), "外观与壁纸，当前");
    assertTrue(lightRow.isFocused());
    assertNoConfirmation(lightRow);
    press(KeyEvent.KEYCODE_DPAD_RIGHT);
    assertEquals("sonoma", new Preferences(activity).wallpaper());
    assertFalse(new Preferences(activity).lightTheme());
    assertTrue(describedView(field("root"), "外观与壁纸，当前").isFocused());
    assertEquals(1f, ((View) field("wallpaperBlurView")).getAlpha(), 0f);
  }

  private View focusSetting(String description) {
    ((View) field("settingsButton")).performClick();
    idle(350);
    View row = describedView(field("root"), description);
    assertTrue(row.requestFocus());
    idle(350);
    return row;
  }

  private static View describedView(View root, String description) {
    ArrayList<View> matches = new ArrayList<>();
    root.findViewsWithText(matches, description, View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
    assertEquals(1, matches.size());
    return matches.get(0);
  }

  private void assertNoConfirmation(View row) {
    assertFalse(row.isClickable());
    assertFalse(row.hasOnClickListeners());
    assertFalse(row.isSoundEffectsEnabled());
    assertFalse(row.getContentDescription().toString().contains("按确定"));
    press(KeyEvent.KEYCODE_DPAD_CENTER);
    press(KeyEvent.KEYCODE_ENTER);
    assertFalse(row.performClick());
    idle(350);
  }

  @Test
  public void capsKeepScreenOnAndClearsItOnScreenOff() {
    new Preferences(activity).screensaverDelaySeconds(30);
    activity.onWindowFocusChanged(true);
    idle(29_999);
    assertNull(field("screensaverOverlay"));
    idle(1);
    assertTrue(isKeepingScreenOn());
    idle(899_000);
    assertTrue(isKeepingScreenOn());
    idle(1_000);
    assertFalse(isKeepingScreenOn());
    assertNotNull(field("screensaverOverlay"));

    press(KeyEvent.KEYCODE_DPAD_CENTER);
    idle(30_000);
    assertTrue(isKeepingScreenOn());
    shadowOf(activity.getSystemService(PowerManager.class)).setIsInteractive(false);
    activity.sendBroadcast(new Intent(Intent.ACTION_SCREEN_OFF));
    idle(0);
    assertFalse(isKeepingScreenOn());
    assertNull(field("screensaverOverlay"));
  }

  @Test
  public void leavesOtherDevicesAndSystemKeysAloneAndConsumesCanceledWakeRelease() {
    ScreensaverKeys keys = new ScreensaverKeys();
    long down = SystemClock.uptimeMillis();
    assertFalse(keys.handle(event(down, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.ACTION_DOWN, 0, 1, 0), true));
    assertFalse(keys.hasHeldKeys());
    assertTrue(keys.handle(event(down, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN, 0, 1, 0), true));
    assertTrue(keys.hasHeldKeys());
    assertFalse(keys.handle(event(down, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_UP, 0, 2, 0), false));
    assertTrue(keys.hasHeldKeys());
    keys.clearHeldKeys();
    assertTrue(keys.handle(
        event(down, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_UP, 0, 1, KeyEvent.FLAG_CANCELED), false));
    assertFalse(keys.handle(event(down, KeyEvent.KEYCODE_ENTER, KeyEvent.ACTION_DOWN, 0, 1, 0), false));
  }

  private void acceptCatalog(List<AppEntry> entries) {
    ReflectionHelpers.callInstanceMethod(
        activity,
        "acceptCatalog",
        from(AppCatalogCache.Snapshot.class, snapshot(entries)),
        from(int.class, 10),
        from(boolean.class, true));
  }

  private AppCatalogCache.Snapshot snapshot(List<AppEntry> entries) {
    return new AppCatalogCache.Snapshot(entries, Collections.emptyMap());
  }

  private <T> T field(String name) {
    return ReflectionHelpers.getField(activity, name);
  }

  private void idle(long milliseconds) {
    shadowOf(Looper.getMainLooper()).idleFor(milliseconds, TimeUnit.MILLISECONDS);
  }

  private boolean isKeepingScreenOn() {
    return (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        != 0;
  }

  private void press(int code) {
    long down = SystemClock.uptimeMillis();
    activity.dispatchKeyEvent(event(down, code, KeyEvent.ACTION_DOWN, 0, 7, 0));
    activity.dispatchKeyEvent(event(down, code, KeyEvent.ACTION_UP, 0, 7, 0));
    idle(0);
  }

  private static KeyEvent event(long downTime, int key, int action, int repeat, int device, int flags) {
    return new KeyEvent(downTime, SystemClock.uptimeMillis(), action, key, repeat, 0, device, 0, flags);
  }
}
