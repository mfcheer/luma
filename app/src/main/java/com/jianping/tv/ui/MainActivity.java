package com.jianping.tv.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.animation.PathInterpolator;
import android.widget.*;
import com.jianping.tv.R;
import com.jianping.tv.airinstall.*;
import com.jianping.tv.browser.*;
import com.jianping.tv.data.*;
import com.jianping.tv.platform.AppInstaller;
import com.jianping.tv.platform.AppLaunch;
import com.jianping.tv.platform.AppUninstall;
import com.jianping.tv.platform.AutoEntry;
import com.jianping.tv.platform.DeviceDiagnostics;
import com.jianping.tv.platform.NetworkMonitor;
import com.jianping.tv.platform.OtherHome;
import com.jianping.tv.platform.UpdateSession;
import com.jianping.tv.platform.WakeEntryService;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

/** Native TV launcher; persistent data remains independent from its remote-driven presentation. */
public final class MainActivity extends Activity {
  private Preferences prefs;
  private AppNames names;
  private AppRenameDialog renameDialog;
  private AppUpdateDialog updateDialog;
  private AirInstallDialog airInstallDialog;
  private WebShortcutStore shortcutStore;
  private AddWebShortcutDialog addWebDialog;
  private WebShortcutEditDialog editWebDialog;
  private WebIconManager.Listener iconListener;
  private String pendingUninstall = "";
  private UiPalette palette;
  private NetworkMonitor networkMonitor;
  private NetworkMonitor.State networkState = NetworkMonitor.State.DISCONNECTED;
  private ImageView networkGlyph;
  private View networkButton, searchButton;
  private AppRepository repository;
  private AppCatalogCache catalog;
  private AppCatalogCache.Snapshot snapshot;
  private Future<?> loadTask;
  private final Set<String> invalidatedPackages = new HashSet<>();
  private SettingsRowView homeSetting;
  private boolean waitingForHomeSettings;
  private final Runnable packageReload = () -> reload(false);
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private List<AppEntry> apps = new ArrayList<>();
  private FrameLayout root, desktop;
  private AppActionMenuView actionLayer;
  private View settingsButton;
  private TextView clock, calendarDate, moveHint;
  private GridWallView homeWall;
  private View menuReturn;
  private String selectedSetting = "打开电视原生桌面", selectedId = "", page = "home";
  private String pendingSearchTarget = "";
  private AmbientBackgroundView ambient;
  private ImageView wallpaperView, wallpaperBlurView;
  private View scrimView, bottomGradient;
  private static final String[] THEME_KEYS = {
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
  private static final String[] THEME_NAMES = {
    "Sonoma（晨曦）",
    "Big Sur（海岸）",
    "Catalina（夜湾）",
    "Mojave（沙丘）",
    "Sequoia（红杉）",
    "Patagonia（雪峰）",
    "Yosemite（苍谷）",
    "经典深色",
    "经典浅色"
  };
  private float scale = 1;
  private boolean loading = true, pendingRender, catalogError;
  private List<String> draftOrder;
  private String movingId;
  private int loadGeneration;
  private int homeOffset;
  private long pendingWakeEntry;
  private static final long SCREENSAVER_KEEP_SCREEN_ON_MS = 15 * 60 * 1000L;
  private static final int[] SCREENSAVER_DELAYS = {30, 60, 120, 0};
  private static final String[] SCREENSAVER_DELAY_LABELS = {"30 秒", "1 分钟", "2 分钟", "关闭"};
  private final ScreensaverKeys screensaverKeys = new ScreensaverKeys();
  private final Matrix screensaverWallpaperMatrix = new Matrix();
  private final Runnable showScreensaver = this::showScreensaver;
  private final Runnable releaseScreensaverKeepScreenOn = this::releaseScreensaverKeepScreenOn;
  private View screensaverOverlay, screensaverFocus;
  private String screensaverCardId;
  private ValueAnimator screensaverTransition;
  private float screensaverProgress;
  private int screensaverOffset, desktopFocusability, desktopAccessibility;
  private boolean resumed, screensaverTimerArmed, desktopRefreshAfterScreensaver;
  private boolean touchHeld, wakeTouch;
  private final Runnable ticker =
      new Runnable() {
        public void run() {
          updateClock();
          handler.postDelayed(this, 1000);
        }
      };
  private final BroadcastReceiver screenChanges =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) stopScreensaver();
          else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) restartScreensaverTimer();
        }
      };
  private final BroadcastReceiver packageChanges =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent intent) {
          // An APK replacement briefly removes its old entry; retain its slot until added.
          if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
              && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
          String changed =
              intent.getData() == null ? null : intent.getData().getSchemeSpecificPart();
          if (changed != null) invalidatedPackages.add(changed);
          handler.removeCallbacks(packageReload);
          handler.postDelayed(packageReload, 180);
        }
      };

  @Override
  public void onCreate(Bundle state) {
    prefs = new Preferences(this);
    names = new AppNames(this);
    shortcutStore = new WebShortcutStore(this);
    shortcutStore.addListener(
        () ->
            runOnUiThread(
                () -> {
                  if (isDestroyed() || loading || !page.equals("home")) return;
                  if (screensaverOverlay != null) desktopRefreshAfterScreensaver = true;
                  else render();
                }));
    iconListener =
        shortcutId ->
            runOnUiThread(
                () -> {
                  if (isDestroyed()) return;
                  if (screensaverOverlay != null) {
                    desktopRefreshAfterScreensaver = true;
                  } else if (homeWall != null && !loading && "home".equals(page)) {
                    if (!homeWall.sameEntries(desktopApps())) {
                      homeWall.setEntries(desktopApps(), selectedId, false);
                    } else {
                      homeWall.refreshLabels();
                    }
                  }
                });
    WebIconManager.addListener(iconListener);
    fetchMissingWebIcons();
    palette = UiPalette.from(prefs.lightTheme());
    setTheme(palette.light ? R.style.AppThemeLight : R.style.AppTheme);
    super.onCreate(state);
    repository = new AppRepository(this);
    catalog = new AppCatalogCache(this);
    snapshot = catalog.memory();
    if (snapshot == null) {
      snapshot = catalog.read();
    }
    if (snapshot != null) {
      apps = snapshot.apps;
      loading = false;
    }
    if (state != null) {
      page = state.getString("page", "home");
      selectedId = state.getString("selected", "");
      homeOffset = state.getInt("homeOffset", 0);
      pendingSearchTarget = state.getString("searchTarget", "");
      pendingUninstall = state.getString("uninstall", "");
    }
    if (selectedId.isEmpty() && !apps.isEmpty()) {
      List<String> order = AppOrder.reconcile(prefs.appOrder(), installedIds());
      if (!order.isEmpty()) selectedId = order.get(0);
    }
    recordEntry(getIntent());
    AutoEntry.sync(this);
    networkMonitor = new NetworkMonitor(this);
    networkState = NetworkMonitor.current(this);
    networkMonitor.start(
        state1 -> {
          networkState = state1;
          updateNetworkView();
        });
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_PACKAGE_ADDED);
    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
    filter.addDataScheme("package");
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(packageChanges, filter, Context.RECEIVER_EXPORTED);
    else registerReceiver(packageChanges, filter);
    IntentFilter screenFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    screenFilter.addAction(Intent.ACTION_SCREEN_ON);
    registerReceiver(screenChanges, screenFilter);
    render();
    reload(true);
    handler.post(ticker);
    handleActionIntent(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    stopScreensaver();
    setIntent(intent);
    recordEntry(intent);
    boolean needsRender = !page.equals("home") || actionLayer != null || draftOrder != null;
    if (renameDialog != null) renameDialog.cancel();
    if (updateDialog != null) updateDialog.cancel();
    if (airInstallDialog != null) airInstallDialog.cancel();
    if (addWebDialog != null) addWebDialog.cancel();
    if (editWebDialog != null) editWebDialog.cancel();
    draftOrder = null;
    movingId = null;
    page = "home";
    if (needsRender) render();
    handleActionIntent(intent);
    restartScreensaverTimer();
  }

  private void handleActionIntent(Intent intent) {
    if (intent != null && "com.jianping.tv.action.AIR_INSTALL".equals(intent.getAction())) {
      handler.post(this::showAirInstallDialog);
    }
  }

  private void recordEntry(Intent i) {
    pendingWakeEntry = i.getLongExtra("wakeId", 0);
    i.removeExtra("wakeId");
    if (pendingWakeEntry != 0) page = "home";
    String now = DateFormat.getDateTimeInstance().format(new Date());
    if (i.getBooleanExtra("boot", false)) prefs.record("bootVisible", now);
    if (i.hasCategory(Intent.CATEGORY_HOME)) prefs.record("homeVisible", now);
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    b.putString("page", page);
    b.putString("selected", selectedId);
    b.putInt("homeOffset", homeWall == null ? homeOffset : homeWall.getScrollY());
    b.putString("searchTarget", pendingSearchTarget);
    b.putString("uninstall", pendingUninstall);
    super.onSaveInstanceState(b);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request == AppInstaller.REQUEST_INSTALL_PERM) {
      // onResume restores the durable update state and presents the next action.
      return;
    }
    if (request == AppUninstall.REQUEST) {
      if (!pendingUninstall.isEmpty() && !AppUninstall.installed(this, pendingUninstall)) {
        names.removed(pendingUninstall);
        new AppUsageStore(this).removed(pendingUninstall);
        reload(false);
      }
      pendingUninstall = "";
      return;
    }
    if (request != SearchActivity.REQUEST || result != RESULT_OK || data == null) return;
    String target = data.getStringExtra(SearchActivity.HOME_TARGET);
    if (target == null || target.isEmpty()) return;
    selectedId = target;
    pendingSearchTarget = target;
    homeOffset = 0;
    page = "home";
    if (homeWall != null && !loading) {
      homeWall.setEntries(desktopApps(), target, false);
      homeWall.focusKey(target, true);
      pendingSearchTarget = "";
    } else if (!loading) render();
  }

  @Override
  protected void onResume() {
    super.onResume();
    resumed = true;
    UpdateSession updates = UpdateSession.get(this);
    String updated = updates.reconcileInstalledVersion();
    if (updated != null) toast(updated);
    else if (updates.returnedFromSystem()) showUpdateDialog();
    if (pendingWakeEntry != 0) {
      WakeEntryService.recordVisible(this, pendingWakeEntry);
      pendingWakeEntry = 0;
    }
    AutoEntry.sync(this);
    networkState = NetworkMonitor.current(this);
    WebIconManager.syncFromDisk(this);
    fetchMissingWebIcons();
    if (homeWall != null && !loading && "home".equals(page)) {
      if (!homeWall.sameEntries(desktopApps())) {
        homeWall.setEntries(desktopApps(), selectedId, false);
      }
    }
    updateNetworkView();
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    // Returning from another app keeps the existing wall and decoded artwork intact.
    if (homeWall != null) homeWall.refreshLabels();
    if (page.equals("home") && homeWall != null && root.findFocus() == null)
      homeWall.restoreFocus(selectedId, homeWall.getScrollY());
    if (homeSetting != null && page.equals("settings"))
      homeSetting.detail(DeviceDiagnostics.homeLabel(this));
    restartScreensaverTimer();
  }

  @Override
  protected void onPause() {
    resumed = false;
    stopScreensaver();
    waitingForHomeSettings = false;
    super.onPause();
  }

  @Override
  protected void onStop() {
    stopScreensaver();
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    resumed = false;
    stopScreensaver();
    if (renameDialog != null) {
      renameDialog.setOnDismissListener(null);
      renameDialog.dismiss();
      renameDialog = null;
    }
    if (updateDialog != null) {
      updateDialog.setOnDismissListener(null);
      updateDialog.dismiss();
      updateDialog = null;
    }
    if (airInstallDialog != null) {
      airInstallDialog.setOnDismissListener(null);
      airInstallDialog.dismiss();
      airInstallDialog = null;
    }
    if (addWebDialog != null) {
      addWebDialog.setOnDismissListener(null);
      addWebDialog.dismiss();
      addWebDialog = null;
    }
    if (editWebDialog != null) {
      editWebDialog.setOnDismissListener(null);
      editWebDialog.dismiss();
      editWebDialog = null;
    }
    if (networkMonitor != null) {
      networkMonitor.stop();
      networkMonitor = null;
    }
    handler.removeCallbacksAndMessages(null);
    if (wallpaperView != null) {
      wallpaperView.setImageDrawable(null);
      wallpaperView = null;
    }
    if (wallpaperBlurView != null) {
      wallpaperBlurView.setImageDrawable(null);
      wallpaperBlurView = null;
    }
    WebIconManager.removeListener(iconListener);
    unregisterReceiver(screenChanges);
    unregisterReceiver(packageChanges);
    worker.shutdownNow();
    super.onDestroy();
  }

  private void reload(boolean allowDisk) {
    final int generation = ++loadGeneration;
    if (loadTask != null) loadTask.cancel(true);
    final Set<String> invalidated = new HashSet<>(invalidatedPackages);
    final AppCatalogCache.Snapshot previous = snapshot;
    loadTask =
        worker.submit(
            () -> {
              try {
                long began = SystemClock.elapsedRealtime();
                AppCatalogCache.Snapshot cached = previous;
                if (cached == null && allowDisk) {
                  cached = catalog.read();
                  if (cached != null) {
                    final AppCatalogCache.Snapshot disk = cached;
                    runOnUiThread(() -> acceptCatalog(disk, generation, false));
                    android.util.Log.i(
                        "StillCatalog",
                        "diskReady apps="
                            + disk.apps.size()
                            + " elapsedMs="
                            + (SystemClock.elapsedRealtime() - began));
                  }
                }
                if (Thread.currentThread().isInterrupted()) return;
                List<AppEntry> result = repository.load();
                if (Thread.currentThread().isInterrupted()) return;
                long queried = SystemClock.elapsedRealtime();
                Map<String, AppCatalogCache.Visual> visuals = new HashMap<>();
                int prepared = 0, reused = 0;
                for (AppEntry app : result) {
                  if (Thread.currentThread().isInterrupted()) return;
                  AppCatalogCache.Visual visual = cached == null ? null : cached.visuals.get(app.id);
                  if (visual == null
                      || invalidated.contains(app.component.getPackageName())
                      || !visual.fingerprint.equals(app.fingerprint)) {
                    AppRepository.Artwork raw = repository.artwork(app);
                    int accent = IconPalette.accent(raw.icon);
                    Drawable card =
                        IconArtwork.landscape(getResources(), raw.icon, raw.banner, accent);
                    visual =
                        new AppCatalogCache.Visual(
                            app.fingerprint, accent, card, IconArtwork.preview(getResources(), card));
                    prepared++;
                  } else reused++;
                  visuals.put(app.id, visual);
                }
                if (Thread.currentThread().isInterrupted()) return;
                AppCatalogCache.Snapshot next = new AppCatalogCache.Snapshot(result, visuals);
                boolean changed = !next.samePresentation(cached);
                runOnUiThread(
                    () -> {
                      if (isDestroyed() || generation != loadGeneration) return;
                      invalidatedPackages.removeAll(invalidated);
                      acceptCatalog(next, generation, true);
                    });
                android.util.Log.i(
                    "StillCatalog",
                    "refresh apps="
                        + result.size()
                        + " prepared="
                        + prepared
                        + " reused="
                        + reused
                        + " queryMs="
                        + (queried - began)
                        + " artworkMs="
                        + (SystemClock.elapsedRealtime() - queried));
                if (changed && !Thread.currentThread().isInterrupted()) catalog.write(next);
              } catch (RuntimeException error) {
                android.util.Log.w("StillCatalog", "Unable to refresh application catalog", error);
                runOnUiThread(
                    () -> {
                      if (isDestroyed() || generation != loadGeneration) return;
                      loading = false;
                      catalogError = true;
                      if (apps.isEmpty()) render();
                      toast("读取应用失败，可在设置中重试");
                    });
              }
            });
  }

  private void acceptCatalog(AppCatalogCache.Snapshot next, int generation, boolean fresh) {
    if (isDestroyed() || generation != loadGeneration) return;
    boolean changed = !next.samePresentation(snapshot);
    List<String> previousOrder = new ArrayList<>();
    for (AppEntry app : orderedApps()) previousOrder.add(app.id);
    snapshot = next;
    catalog.remember(next);
    apps = next.apps;
    loading = false;
    catalogError = false;
    List<String> order = AppOrder.reconcile(prefs.appOrder(), installedIds());
    if (fresh) {
      selectedId = AppOrder.focusAfterRemoval(previousOrder, order, selectedId);
      if (!order.contains(pendingSearchTarget)) pendingSearchTarget = "";
      if (!prefs.hasAppOrder() || !order.equals(prefs.appOrder())) prefs.saveAppOrder(order);
    } else {
      if (selectedId.isEmpty() || !order.contains(selectedId)) {
        selectedId = AppOrder.focusAfterRemoval(previousOrder, order, selectedId);
      }
    }
    if ((selectedId.isEmpty() || !order.contains(selectedId)) && !order.isEmpty()) {
      selectedId = order.get(0);
    }
    if (!changed) return;
    if (screensaverOverlay != null) {
      if ("home".equals(page)) desktopRefreshAfterScreensaver = true;
      return;
    }
    if (draftOrder != null
        || actionLayer != null
        || renameDialog != null
        || updateDialog != null
        || airInstallDialog != null
        || addWebDialog != null
        || editWebDialog != null) pendingRender = true;
    else render();
  }

  private int px(float n) {
    return Math.round(n * scale);
  }

  private TextView text(String value, float size, int color) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
    t.setTextColor(color);
    t.setGravity(Gravity.CENTER);
    t.setIncludeFontPadding(false);
    t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    t.setFontFeatureSettings("'kern' 1");
    return t;
  }

  private void place(FrameLayout parent, View view, float x, float y, float w, float h) {
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(px(w), px(h));
    p.leftMargin = px(x);
    p.topMargin = px(y);
    parent.addView(view, p);
  }

  private void put(View v, float x, float y, float w, float h) {
    place(desktop, v, x, y, w, h);
  }

  private GradientDrawable rounded(int color, float radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(px(radius));
    return d;
  }

  private View networkAction(Runnable action) {
    FrameLayout button = new FrameLayout(this);
    ImageView glyph = new ImageView(this);
    networkGlyph = glyph;
    int glyphSize = px(20);
    // Align the network icon optically with the search magnifier and settings gear.
    glyph.setTranslationY(px(1.5f));
    button.addView(glyph, new FrameLayout.LayoutParams(glyphSize, glyphSize, Gravity.CENTER));
    button.setFocusable(true);
    button.setClickable(true);
    button.setBackground(rounded(palette.light ? Color.TRANSPARENT : 0x2E000000, 18));
    updateNetworkView(button, glyph);
    button.setOnFocusChangeListener(
        (v, focus) -> {
          button.setBackground(
              rounded(focus ? palette.headerFocus : (palette.light ? Color.TRANSPARENT : 0x2E000000), 18));
          int unfocusedColor =
              palette.light
                  ? (networkState.connected ? palette.icon : palette.muted)
                  : (networkState.connected ? 0xEEFFFFFF : 0x99FFFFFF);
          glyph.setColorFilter(focus ? palette.headerFocusIcon : unfocusedColor);
          glyph.setAlpha((!focus && !networkState.connected) ? 0.6f : 1f);
          button
              .animate()
              .scaleX(focus ? 1.12f : 1)
              .scaleY(focus ? 1.12f : 1)
              .setDuration(180)
              .start();
          if (focus) toast(networkState.label + (networkState.connected ? "" : " · 按确定打开网络设置"));
        });
    button.setOnClickListener(v -> action.run());
    return button;
  }

  private void updateNetworkView() {
    if (networkButton != null && networkGlyph != null) {
      updateNetworkView(networkButton, networkGlyph);
    }
  }

  private void updateNetworkView(View button, ImageView glyph) {
    glyph.setImageResource(networkState.iconRes);
    button.setContentDescription(networkState.label);
    boolean focused = button.hasFocus();
    button.setBackground(
        rounded(focused ? palette.headerFocus : (palette.light ? Color.TRANSPARENT : 0x2E000000), 18));
    int unfocusedColor =
        palette.light
            ? (networkState.connected ? palette.icon : palette.muted)
            : (networkState.connected ? 0xEEFFFFFF : 0x99FFFFFF);
    glyph.setColorFilter(focused ? palette.headerFocusIcon : unfocusedColor);
    glyph.setAlpha((!focused && !networkState.connected) ? 0.6f : 1f);
  }

  private View headerAction(boolean home, Runnable action) {
    return headerAction(
        home ? R.drawable.nav_settings : R.drawable.nav_back, home ? "设置" : "返回", action);
  }

  private View headerAction(int icon, String label, Runnable action) {
    FrameLayout button = new FrameLayout(this);
    ImageView glyph = new ImageView(this);
    glyph.setImageResource(icon);
    button.setBackground(rounded(palette.light ? Color.TRANSPARENT : 0x2E000000, 18));
    glyph.setColorFilter(palette.light ? palette.icon : 0xEEFFFFFF);
    int glyphSize = px(icon == R.drawable.nav_back ? 24 : 20);
    // Align the magnifier's circular body optically with the centre of the gear.
    if (icon == R.drawable.nav_search) glyph.setTranslationY(px(2));
    button.addView(glyph, new FrameLayout.LayoutParams(glyphSize, glyphSize, Gravity.CENTER));
    button.setFocusable(true);
    button.setClickable(true);
    button.setContentDescription(label);
    button.setOnFocusChangeListener(
        (v, focus) -> {
          button.setBackground(
              rounded(focus ? palette.headerFocus : (palette.light ? Color.TRANSPARENT : 0x2E000000), 18));
          glyph.setColorFilter(
              focus ? palette.headerFocusIcon : (palette.light ? palette.icon : 0xEEFFFFFF));
          button
              .animate()
              .scaleX(focus ? 1.12f : 1)
              .scaleY(focus ? 1.12f : 1)
              .setDuration(180)
              .start();
        });
    button.setOnClickListener(v -> action.run());
    return button;
  }

  private int appAccent(AppEntry app) {
    if (AirInstallEntry.isAirInstallEntry(app)) {
      return 0xFF0EA5E9;
    }
    if (WebShortcutStore.isAddWebEntry(app)) {
      return 0xFF3B82F6;
    }
    if (WebShortcutStore.isWebShortcut(app)) {
      WebShortcut shortcut = shortcutStore.get(WebShortcutStore.extractId(app.id));
      return shortcut != null ? shortcut.accent : 0xFF2563EB;
    }
    AppCatalogCache.Visual visual = snapshot == null ? null : snapshot.visuals.get(app.id);
    return visual == null ? 0xFF7092CC : visual.accent;
  }

  private Drawable artwork(AppEntry app, boolean soft) {
    if (AirInstallEntry.isAirInstallEntry(app)) {
      return soft
          ? AirInstallArtwork.previewArtwork(getResources(), palette)
          : AirInstallArtwork.cardArtwork(getResources(), palette);
    }
    if (WebShortcutStore.isAddWebEntry(app)) {
      return soft
          ? WebArtwork.addPreviewArtwork(getResources(), palette)
          : WebArtwork.addCardArtwork(getResources(), palette);
    }
    if (WebShortcutStore.isWebShortcut(app)) {
      WebShortcut shortcut = shortcutStore.get(WebShortcutStore.extractId(app.id));
      if (shortcut != null) {
        return soft
            ? WebArtwork.previewArtwork(this, getResources(), shortcut, palette)
            : WebArtwork.cardArtwork(this, getResources(), shortcut, palette);
      }
    }
    AppCatalogCache.Visual visual = snapshot == null ? null : snapshot.visuals.get(app.id);
    if (visual == null) return getDrawable(android.R.drawable.sym_def_app_icon);
    Drawable image = soft ? visual.preview : visual.card;
    Drawable.ConstantState state = image.getConstantState();
    return state == null ? image : state.newDrawable(getResources()).mutate();
  }

  private void fetchMissingWebIcons() {
    for (WebShortcut s : shortcutStore.loadAll()) {
      boolean missingIcon = (WebPresetLogo.match(s.url, s.name) == null && !WebIconManager.hasIcon(this, s.id));
      boolean missingTitle = WebShortcut.isUrlName(s.name, s.url);
      if (missingIcon || missingTitle) {
        WebIconManager.fetchFaviconAsync(this, s, null);
      }
    }
  }

  private void updateClock() {
    if (clock == null || calendarDate == null) return;
    // Use the same instant and the device's current timezone for both lines, including midnight.
    Date now = new Date();
    String time = new SimpleDateFormat("HH:mm", Locale.CHINA).format(now);
    String date = new SimpleDateFormat("yyyy年M月d日  EEEE", Locale.CHINA).format(now);
    // Keep the one-second time-change response without relaying out unchanged text every tick.
    if (!time.contentEquals(clock.getText())) clock.setText(time);
    if (!date.contentEquals(calendarDate.getText())) calendarDate.setText(date);
  }

  private boolean isPureTheme(String key) {
    return "pure_dark".equals(key) || "pure_light".equals(key) || "none".equals(key);
  }

  private boolean isLightTheme(String key) {
    return "pure_light".equals(key);
  }

  private String themeDisplayName(String key) {
    for (int i = 0; i < THEME_KEYS.length; i++) {
      if (THEME_KEYS[i].equals(key)) return THEME_NAMES[i];
    }
    return THEME_NAMES[0];
  }

  private int themeIndex(String key) {
    for (int i = 0; i < THEME_KEYS.length; i++) {
      if (THEME_KEYS[i].equals(key)) return i;
    }
    return 0;
  }

  private void previewInlineTheme(
      boolean forward, SettingsRowView appearance, TextView desktopHint) {
    String current = prefs.wallpaper();
    int idx = themeIndex(current);
    int next =
        forward
            ? (idx + 1) % THEME_KEYS.length
            : (idx - 1 + THEME_KEYS.length) % THEME_KEYS.length;
    String newKey = THEME_KEYS[next];
    boolean light = isLightTheme(newKey);
    boolean wasLight = isLightTheme(current);
    prefs.wallpaper(newKey);
    prefs.lightTheme(light);
    if (light != wasLight) {
      palette = UiPalette.from(light);
      setTheme(light ? R.style.AppThemeLight : R.style.AppTheme);
      selectedSetting = "外观与壁纸";
      render();
      return;
    }

    updateAppearanceSetting(appearance, newKey);
    applyWallpaper(newKey);
    desktopHint.setText("已切换至 " + themeDisplayName(newKey));
  }

  private void updateAppearanceSetting(SettingsRowView row, String key) {
    String name = themeDisplayName(key);
    row.previewThumb(WallpaperGalleryView.themeThumbnail(key));
    row.detail(name);
    row.setContentDescription("外观与壁纸，当前" + name + "，左右键切换");
  }

  private void applyWallpaper(String key) {
    boolean pure = isPureTheme(key);
    if (pure) {
      if (wallpaperView != null) wallpaperView.setVisibility(View.GONE);
      if (wallpaperBlurView != null) wallpaperBlurView.setVisibility(View.GONE);
      if (bottomGradient != null) bottomGradient.setVisibility(View.GONE);
      if (scrimView != null) scrimView.setVisibility(View.GONE);
      if (ambient != null) ambient.drawBackground(true);
      return;
    }
    int resId;
    int blurResId;
    switch (key) {
      case "big_sur":
        resId = R.drawable.wallpaper_big_sur;
        blurResId = R.drawable.wallpaper_big_sur_blur;
        break;
      case "catalina":
        resId = R.drawable.wallpaper_catalina;
        blurResId = R.drawable.wallpaper_catalina_blur;
        break;
      case "mojave":
        resId = R.drawable.wallpaper_mojave;
        blurResId = R.drawable.wallpaper_mojave_blur;
        break;
      case "sequoia":
        resId = R.drawable.wallpaper_sequoia;
        blurResId = R.drawable.wallpaper_sequoia_blur;
        break;
      case "patagonia":
        resId = R.drawable.wallpaper_patagonia;
        blurResId = R.drawable.wallpaper_patagonia_blur;
        break;
      case "yosemite":
        resId = R.drawable.wallpaper_yosemite;
        blurResId = R.drawable.wallpaper_yosemite_blur;
        break;
      case "sonoma":
      default:
        resId = R.drawable.wallpaper_sonoma;
        blurResId = R.drawable.wallpaper_sonoma_blur;
        break;
    }
    if (wallpaperView != null) {
      wallpaperView.setVisibility(View.VISIBLE);
      wallpaperView.setImageResource(resId);
    }
    if (wallpaperBlurView != null) {
      wallpaperBlurView.setVisibility(View.VISIBLE);
      wallpaperBlurView.setImageResource(blurResId);
    }
    if (bottomGradient != null) {
      bottomGradient.setVisibility(page.equals("settings") ? View.GONE : View.VISIBLE);
    }
    if (scrimView != null) {
      scrimView.setVisibility(View.VISIBLE);
    }
    if (ambient != null) {
      ambient.drawBackground(false);
    }
    if (page.equals("settings")) {
      updateWallpaperScroll(0);
    }
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) restartScreensaverTimer();
    else stopScreensaver();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    boolean consumed = screensaverKeys.handle(event, screensaverOverlay != null);
    if (consumed && event.getAction() == KeyEvent.ACTION_DOWN) hideScreensaver(true);
    boolean handled = consumed || super.dispatchKeyEvent(event);
    restartScreensaverTimer();
    return handled;
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent event) {
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        touchHeld = true;
        wakeTouch = screensaverOverlay != null;
        if (wakeTouch) hideScreensaver(true);
        break;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        touchHeld = false;
        break;
    }
    boolean handled = wakeTouch || super.dispatchTouchEvent(event);
    if (!touchHeld) wakeTouch = false;
    restartScreensaverTimer();
    return handled;
  }

  @Override
  public boolean dispatchGenericMotionEvent(MotionEvent event) {
    int action = event.getActionMasked();
    if (action != MotionEvent.ACTION_HOVER_MOVE
        && action != MotionEvent.ACTION_SCROLL
        && action != MotionEvent.ACTION_MOVE) return super.dispatchGenericMotionEvent(event);
    boolean waking = screensaverOverlay != null;
    if (waking) hideScreensaver(true);
    boolean handled = waking || super.dispatchGenericMotionEvent(event);
    restartScreensaverTimer();
    return handled;
  }

  private boolean canStartScreensaver() {
    return canShowScreensaver()
        && "home".equals(page)
        && !loading
        && snapshot != null
        && draftOrder == null
        && movingId == null
        && actionLayer == null
        && !isShowing(renameDialog)
        && !isShowing(updateDialog)
        && !isShowing(airInstallDialog)
        && !isShowing(addWebDialog)
        && !isShowing(editWebDialog)
        && !touchHeld
        && !screensaverKeys.hasHeldKeys();
  }

  private boolean canShowScreensaver() {
    return resumed
        && root != null
        && screensaverOverlay == null
        && hasWindowFocus()
        && !isFinishing()
        && getSystemService(PowerManager.class).isInteractive();
  }

  private static boolean isShowing(Dialog dialog) {
    return dialog != null && dialog.isShowing();
  }

  private void cancelScreensaverTimer() {
    if (!screensaverTimerArmed) return;
    handler.removeCallbacks(showScreensaver);
    screensaverTimerArmed = false;
  }

  private void scheduleScreensaver() {
    int seconds = prefs.screensaverDelaySeconds();
    if (seconds == 0 || !canStartScreensaver()) {
      cancelScreensaverTimer();
    } else if (!screensaverTimerArmed) {
      screensaverTimerArmed = true;
      handler.postDelayed(showScreensaver, seconds * 1000L);
    }
  }

  private void restartScreensaverTimer() {
    cancelScreensaverTimer();
    scheduleScreensaver();
  }

  private void stopScreensaver() {
    cancelScreensaverTimer();
    screensaverKeys.clearHeldKeys();
    touchHeld = false;
    hideScreensaver(false);
  }

  private void showScreensaver() {
    screensaverTimerArmed = false;
    if (prefs.screensaverDelaySeconds() == 0 || !canStartScreensaver()) return;
    screensaverFocus = root.findFocus();
    screensaverCardId =
        screensaverFocus instanceof AppCardView && homeWall != null
            ? homeWall.currentKey()
            : null;
    screensaverOffset = homeWall == null ? homeOffset : homeWall.getScrollY();
    desktopFocusability = desktop.getDescendantFocusability();
    desktopAccessibility = desktop.getImportantForAccessibility();
    desktop.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    desktop.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);

    screensaverOverlay = new View(this);
    screensaverOverlay.setFocusableInTouchMode(true);
    screensaverOverlay.setClickable(true);
    screensaverOverlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    root.addView(screensaverOverlay, new FrameLayout.LayoutParams(-1, -1));
    screensaverOverlay.requestFocus();
    handler.removeCallbacks(ticker);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    handler.postDelayed(releaseScreensaverKeepScreenOn, SCREENSAVER_KEEP_SCREEN_ON_MS);
    animateScreensaver(1f, 300);
  }

  private void releaseScreensaverKeepScreenOn() {
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  private void hideScreensaver(boolean animate) {
    if (screensaverOverlay == null) {
      if (!animate && screensaverTransition != null) {
        screensaverTransition.cancel();
        screensaverTransition = null;
        applyScreensaverProgress(0f);
      }
      return;
    }

    handler.removeCallbacks(releaseScreensaverKeepScreenOn);
    releaseScreensaverKeepScreenOn();
    View overlay = screensaverOverlay;
    View focus = screensaverFocus;
    String cardId = screensaverCardId;
    boolean refresh = desktopRefreshAfterScreensaver;
    screensaverOverlay = null;
    screensaverFocus = null;
    screensaverCardId = null;
    desktopRefreshAfterScreensaver = false;
    desktop.setDescendantFocusability(desktopFocusability);
    desktop.setImportantForAccessibility(desktopAccessibility);
    if (refresh && homeWall == null) {
      boolean hadNetworkFocus = focus == networkButton;
      boolean hadSearchFocus = focus == searchButton;
      if (screensaverTransition != null) screensaverTransition.cancel();
      screensaverProgress = 0f;
      render();
      (hadNetworkFocus ? networkButton : hadSearchFocus ? searchButton : settingsButton)
          .requestFocusFromTouch();
    } else {
      if (homeWall != null) {
        if (refresh) {
          homeWall.refreshAfterScreensaver(desktopApps(), cardId, screensaverOffset);
          int first = homeWall.firstId();
          networkButton.setNextFocusDownId(first);
          searchButton.setNextFocusDownId(first);
          settingsButton.setNextFocusDownId(first);
        } else if (cardId != null) {
          homeWall.restoreExactFocus(cardId, screensaverOffset);
        } else {
          homeWall.scrollTo(0, screensaverOffset);
        }
      }
      if (cardId == null && focus != null && focus.isAttachedToWindow()) {
        focus.requestFocusFromTouch();
      }
      root.removeView(overlay);
      if (animate) animateScreensaver(0f, 180);
      else {
        if (screensaverTransition != null) screensaverTransition.cancel();
        screensaverTransition = null;
        applyScreensaverProgress(0f);
      }
    }
    updateClock();
    handler.postDelayed(ticker, 1000);
  }

  private void animateScreensaver(float target, int duration) {
    if (screensaverTransition != null) screensaverTransition.cancel();
    screensaverTransition = ValueAnimator.ofFloat(screensaverProgress, target);
    screensaverTransition.setDuration(duration);
    screensaverTransition.setInterpolator(new PathInterpolator(.18f, .85f, .25f, 1f));
    screensaverTransition.addUpdateListener(
        animator -> applyScreensaverProgress((float) animator.getAnimatedValue()));
    screensaverTransition.addListener(
        new AnimatorListenerAdapter() {
          @Override
          public void onAnimationEnd(Animator animation) {
            if (screensaverTransition == animation) screensaverTransition = null;
          }
        });
    screensaverTransition.start();
  }

  private void applyScreensaverProgress(float progress) {
    screensaverProgress = progress;
    // The background occupies the children through ambient. Everything above it is home UI.
    int start = desktop.indexOfChild(ambient) + 1;
    for (int i = start; i < desktop.getChildCount(); i++) {
      desktop.getChildAt(i).setAlpha(1f - progress);
    }
    bottomGradient.setAlpha(1f - progress);
    if (wallpaperView.getVisibility() == View.VISIBLE) {
      ambient.setAlpha(1f - progress);
      frameScreensaverWallpaper(progress);
    }
    int offset =
        screensaverOverlay != null || homeWall == null ? screensaverOffset : homeWall.getScrollY();
    updateWallpaperScroll(offset);
  }

  private void frameScreensaverWallpaper(float progress) {
    if (progress == 0f) {
      wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
      return;
    }
    Drawable image = wallpaperView.getDrawable();
    if (image == null
        || image.getIntrinsicWidth() <= 0
        || image.getIntrinsicHeight() <= 0
        || wallpaperView.getWidth() <= 0
        || desktop.getHeight() <= 0) return;

    // The normal wallpaper view is taller than the screen to leave room for parallax.
    // Frame the same drawable against the viewport as the desktop fades away.
    int width = wallpaperView.getWidth();
    int wallpaperHeight = wallpaperView.getHeight();
    float height = wallpaperHeight + (desktop.getHeight() - wallpaperHeight) * progress;
    float zoom =
        Math.max(width / (float) image.getIntrinsicWidth(), height / image.getIntrinsicHeight());
    screensaverWallpaperMatrix.setScale(zoom, zoom);
    screensaverWallpaperMatrix.postTranslate(
        Math.round((width - image.getIntrinsicWidth() * zoom) / 2f),
        Math.round((height - image.getIntrinsicHeight() * zoom) / 2f));
    wallpaperView.setScaleType(ImageView.ScaleType.MATRIX);
    wallpaperView.setImageMatrix(screensaverWallpaperMatrix);
  }

  private void updateWallpaperScroll(int scrollY) {
    if (wallpaperView == null || wallpaperBlurView == null) return;
    if (isPureTheme(prefs.wallpaper())) return;
    int headerSpace = px(268);
    float progress =
        "settings".equals(page)
            ? 1f
            : headerSpace > 0 ? Math.min(1f, Math.max(0f, (float) scrollY / headerSpace)) : 0f;
    int maxShift = px(60);
    progress *= 1f - screensaverProgress;
    float shift = -progress * maxShift;
    wallpaperView.setTranslationY(shift);
    wallpaperBlurView.setTranslationY(shift);
    wallpaperBlurView.setAlpha(progress);
    if (scrimView != null) {
      scrimView.setAlpha(0.38f * progress);
    }
  }

  private void render() {
    if (screensaverOverlay != null || screensaverTransition != null) hideScreensaver(false);
    if (homeWall != null && homeWall.isAttachedToWindow()) homeOffset = homeWall.getScrollY();
    homeWall = null;
    menuReturn = null;
    scale = getResources().getDisplayMetrics().widthPixels / 960f;
    root = new FrameLayout(this);
    root.setBackgroundColor(palette.background);
    getWindow()
        .setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(palette.background));
    setContentView(root);
    desktop = new FrameLayout(this);
    root.addView(desktop, new FrameLayout.LayoutParams(-1, -1));
    actionLayer = null;
    settingsButton = null;
    searchButton = null;
    pendingRender = false;

    boolean pure = isPureTheme(prefs.wallpaper());
    wallpaperView = new ImageView(this);
    wallpaperView.setScaleType(ImageView.ScaleType.CENTER_CROP);
    wallpaperBlurView = new ImageView(this);
    wallpaperBlurView.setScaleType(ImageView.ScaleType.CENTER_CROP);

    int extraHeight = px(100);
    FrameLayout.LayoutParams wpParams =
        new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(540) + extraHeight);
    desktop.addView(wallpaperView, wpParams);
    desktop.addView(wallpaperBlurView, wpParams);

    bottomGradient = new View(this);
    GradientDrawable bottomGrad =
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0x00000000, 0x00000000, 0x1A000000, 0x66000000, 0xB8000000});
    bottomGradient.setBackground(bottomGrad);
    bottomGradient.setVisibility(page.equals("settings") || pure ? View.GONE : View.VISIBLE);
    desktop.addView(bottomGradient, new FrameLayout.LayoutParams(-1, -1));

    scrimView = new View(this);
    scrimView.setBackgroundColor(0xFF000000);
    scrimView.setAlpha(page.equals("settings") ? 0.38f : 0f);
    desktop.addView(scrimView, new FrameLayout.LayoutParams(-1, -1));

    applyWallpaper(prefs.wallpaper());

    ambient = new AmbientBackgroundView(this, palette);
    ambient.drawBackground(pure);
    desktop.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
    clock = text("", 18, pure ? palette.secondary : 0xFFFFFFFF);
    clock.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    clock.setLetterSpacing(.015f);
    clock.setFontFeatureSettings("'tnum' 1");
    clock.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    if (!pure) clock.setShadowLayer(px(6), 0, px(2), 0xD9000000);
    put(clock, page.equals("home") ? 598 : 648, 20, 176, 25);
    calendarDate = text("", 12, pure ? palette.muted : 0xF2FFFFFF);
    calendarDate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    calendarDate.setFontFeatureSettings("'tnum' 1");
    if (!pure) calendarDate.setShadowLayer(px(4), 0, px(1.5f), 0xCC000000);
    put(calendarDate, page.equals("home") ? 598 : 648, 46, 176, 18);
    updateClock();
    View settings =
        headerAction(
            page.equals("home"),
            () -> {
              if (draftOrder != null) {
                finishMove(false);
                return;
              }
              page = page.equals("home") ? "settings" : "home";
              render();
            });
    settings.setId(R.id.settings_button);
    settingsButton = settings;
    put(settings, 884, 23, 42, 42);
    if (page.equals("home")) {
      View network =
          networkAction(
              () -> {
                if (draftOrder != null) {
                  finishMove(false);
                  return;
                }
                NetworkMonitor.openSettings(this);
              });
      network.setId(View.generateViewId());
      networkButton = network;
      put(network, 784, 23, 42, 42);

      View search =
          headerAction(
              R.drawable.nav_search,
              "搜索",
              () -> {
                if (draftOrder != null) {
                  finishMove(false);
                  return;
                }
                if (loading || snapshot == null) {
                  toast("应用列表准备中，请稍候");
                  return;
                }
                if (homeWall != null) homeWall.restoreFocus(selectedId, homeWall.getScrollY());
                startActivityForResult(
                    new Intent(this, SearchActivity.class), SearchActivity.REQUEST);
              });
      search.setId(View.generateViewId());
      searchButton = search;
      put(search, 834, 23, 42, 42);
      home(search);
      network.setOnKeyListener(
          (v, code, event) -> {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN
                && event.getAction() == KeyEvent.ACTION_DOWN
                && homeWall != null) {
              homeWall.focusKey(selectedId, false);
              return true;
            }
            return false;
          });
      search.setOnKeyListener(
          (v, code, event) -> {
            if (code == KeyEvent.KEYCODE_DPAD_DOWN
                && event.getAction() == KeyEvent.ACTION_DOWN
                && homeWall != null) {
              homeWall.focusKey(selectedId, false);
              return true;
            }
            return false;
          });
      network.setNextFocusRightId(search.getId());
      network.setNextFocusLeftId(network.getId());
      network.setNextFocusDownId(homeWall == null ? network.getId() : homeWall.firstId());
      search.setNextFocusRightId(settings.getId());
      search.setNextFocusLeftId(network.getId());
      search.setNextFocusDownId(homeWall == null ? search.getId() : homeWall.firstId());
      settings.setNextFocusLeftId(search.getId());
      settings.setNextFocusRightId(settings.getId());
      settings.setNextFocusDownId(homeWall == null ? settings.getId() : homeWall.firstId());
    } else {
      View network = networkAction(() -> NetworkMonitor.openSettings(this));
      network.setId(View.generateViewId());
      networkButton = network;
      put(network, 834, 23, 42, 42);
      network.setNextFocusRightId(settings.getId());
      network.setNextFocusLeftId(network.getId());
      network.setNextFocusDownId(R.id.first_app);
      settings.setNextFocusLeftId(network.getId());
      settings.setNextFocusRightId(settings.getId());
      settings.setNextFocusDownId(R.id.first_app);
      settingsPage();
    }
    scheduleScreensaver();
  }

  private List<String> installedIds() {
    List<String> ids = new ArrayList<>();
    for (AppEntry app : apps) ids.add(app.id);
    for (WebShortcut s : shortcutStore.loadAll()) {
      ids.add(WebShortcutStore.toAppEntry(s).id);
    }
    return ids;
  }

  private List<AppEntry> orderedApps() {
    Map<String, AppEntry> byId = new HashMap<>();
    for (AppEntry app : apps) byId.put(app.id, app);
    for (WebShortcut s : shortcutStore.loadAll()) {
      AppEntry e = WebShortcutStore.toAppEntry(s);
      byId.put(e.id, e);
    }
    List<AppEntry> result = new ArrayList<>();
    for (String id :
        AppOrder.reconcile(draftOrder == null ? prefs.appOrder() : draftOrder, installedIds())) {
      AppEntry entry = byId.get(id);
      if (entry != null) result.add(entry);
    }
    return result;
  }

  private List<AppEntry> desktopApps() {
    if (loading && apps.isEmpty()) {
      return Collections.emptyList();
    }
    List<AppEntry> wallList = new ArrayList<>(orderedApps());
    if (draftOrder == null) {
      wallList.add(AirInstallEntry.ENTRY);
      wallList.add(WebShortcutStore.ADD_WEB_ENTRY);
    }
    return wallList;
  }

  private GridWallView.Listener wallListener() {
    return new GridWallView.Listener() {
      public String label(AppEntry app) {
        if (AirInstallEntry.isAirInstallEntry(app)) {
          return "隔空安装";
        }
        if (WebShortcutStore.isAddWebEntry(app)) {
          return "添加网页";
        }
        if (WebShortcutStore.isWebShortcut(app)) {
          WebShortcut s = shortcutStore.get(WebShortcutStore.extractId(app.id));
          return s != null ? s.name : app.label;
        }
        return names.label(app);
      }

      public Drawable artwork(AppEntry app) {
        return MainActivity.this.artwork(app, false);
      }

      public Drawable preview(AppEntry app) {
        return MainActivity.this.artwork(app, true);
      }

      public int accent(AppEntry app) {
        return appAccent(app);
      }

      public void focused(AppEntry app, String key) {
        if (!loading) selectedId = key;
        ambient.illuminate(appAccent(app), true);
      }

      public void anchor(float x, float y, boolean animated) {
        ambient.follow(x, y, animated);
      }

      public void activate(AppEntry app) {
        if (draftOrder != null) finishMove(true);
        else if (AirInstallEntry.isAirInstallEntry(app)) showAirInstallDialog();
        else if (WebShortcutStore.isAddWebEntry(app)) showAddWebShortcutDialog();
        else if (WebShortcutStore.isWebShortcut(app)) {
          WebShortcut s = shortcutStore.get(WebShortcutStore.extractId(app.id));
          if (s != null) launchWebShortcut(s);
        } else launch(app);
      }

      public void hold(AppEntry app, View source) {
        if (draftOrder == null) {
          if (AirInstallEntry.isAirInstallEntry(app)) return;
          if (WebShortcutStore.isAddWebEntry(app)) return;
          if (WebShortcutStore.isWebShortcut(app)) {
            manageWebShortcut(app, source);
            return;
          }
          manage(app, source);
        }
      }

      public boolean key(int code, KeyEvent event) {
        if (draftOrder == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        return moveKey(code);
      }

      public void onScroll(int scrollY) {
        if (screensaverOverlay == null) updateWallpaperScroll(scrollY);
      }
    };
  }

  private void home(View settings) {
    boolean pure = isPureTheme(prefs.wallpaper());
    moveHint = text("", 14, pure ? palette.secondary : 0xFFFFFFFF);
    if (!pure) moveHint.setShadowLayer(px(4), 0, px(1.5f), 0xB3000000);
    put(moveHint, 180, 36, 400, 30);
    if (loading && apps.isEmpty()) {
      TextView empty =
          text(
              catalogError ? "读取失败，请在设置中重试" : "正在读取应用…",
              19,
              palette.secondary);
      put(empty, 200, 225, 560, 45);
      settings.setNextFocusDownId(settings.getId());
      homeWall = null;
      updateWallpaperScroll(0);
      return;
    }
    int headerSpace = pure ? 0 : px(268);
    homeWall = new GridWallView(this, scale, 156, headerSpace, palette, wallListener());
    homeWall.focusBaselineLift(px(2.5f));
    homeWall.showNames(prefs.showAppNames());
    put(homeWall, 42, 82, 876, pure ? 424 : 458);
    String previous = selectedId;
    homeWall.setEntries(desktopApps(), selectedId, false, false);
    if (!pendingSearchTarget.isEmpty() && !apps.isEmpty()) {
      homeWall.focusKey(pendingSearchTarget, true);
      pendingSearchTarget = "";
    } else if (previous.equals(selectedId) && !selectedId.isEmpty()) homeWall.restoreFocus(selectedId, homeOffset);
    else homeWall.focusKey(selectedId, true);
    homeWall.upperFocus(settings.getId());
    if (apps.isEmpty() && shortcutStore.loadAll().isEmpty()) {
      TextView empty =
          text(catalogError ? "读取失败，请在设置中重试" : "暂无可启动的应用", 19, palette.secondary);
      put(empty, 200, 225, 560, 45);
      settings.requestFocus();
      // An empty loading wall must not overwrite the saved scroll position on cache restore.
      homeWall = null;
    }
    settings.setNextFocusDownId(homeWall == null ? settings.getId() : homeWall.firstId());
    updateWallpaperScroll(homeOffset);
  }

  private void launch(AppEntry app) {
    try {
      AppLaunch.open(this, repository, app);
    } catch (RuntimeException e) {
      toast("无法打开此应用，请刷新应用列表");
      reload(false);
    }
  }

  private void launchWebShortcut(WebShortcut shortcut) {
    Intent intent = new Intent(this, WebBrowserActivity.class);
    intent.putExtra(WebBrowserActivity.EXTRA_URL, shortcut.url);
    intent.putExtra(WebBrowserActivity.EXTRA_TITLE, shortcut.name);
    intent.putExtra(WebBrowserActivity.EXTRA_ID, shortcut.id);
    startActivity(intent);
  }

  private void manageWebShortcut(AppEntry app, View source) {
    if (actionLayer != null) return;
    String shortcutId = WebShortcutStore.extractId(app.id);
    WebShortcut shortcut = shortcutStore.get(shortcutId);
    if (shortcut == null) return;

    menuReturn = source;
    if (source instanceof AppCardView) ((AppCardView) source).contextSelected(true);
    desktop.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);

    List<AppActionMenuView.Item> items = new ArrayList<>();
    items.add(
        new AppActionMenuView.Item(
            "打开网页", R.drawable.menu_info, false, () -> launchWebShortcut(shortcut)));
    items.add(
        new AppActionMenuView.Item("移动位置", R.drawable.menu_move, false, () -> beginMove(app)));
    items.add(
        new AppActionMenuView.Item(
            shortcut.pinned ? "取消常驻收藏" : "固定到常驻收藏",
            R.drawable.menu_move_first,
            false,
            () -> togglePinShortcut(shortcut, app)));
    items.add(
        new AppActionMenuView.Item(
            "移到最后", R.drawable.menu_move_last, false, () -> moveToEdge(app, false)));
    items.add(
        new AppActionMenuView.Item(
            "重命名", R.drawable.menu_rename, false, () -> editWebShortcut(shortcut, false)));
    items.add(
        new AppActionMenuView.Item(
            "修改网址", R.drawable.settings_settings, false, () -> editWebShortcut(shortcut, true)));
    items.add(
        new AppActionMenuView.Item(
            "删除卡片", R.drawable.menu_uninstall, true, () -> deleteWebShortcut(shortcut, app)));

    actionLayer =
        new AppActionMenuView(
            this,
            scale,
            shortcut.name,
            shortcut.url,
            artwork(app, false),
            items,
            action -> closeMenu(false, action),
            palette);
    root.addView(actionLayer, new FrameLayout.LayoutParams(-1, -1));
    actionLayer.reveal();
  }

  private void togglePinShortcut(WebShortcut shortcut, AppEntry app) {
    shortcut.pinned = !shortcut.pinned;
    shortcutStore.save(shortcut);
    if (shortcut.pinned) {
      moveToEdge(app, true);
    } else {
      render();
      toast("已取消常驻收藏");
    }
  }

  private void deleteWebShortcut(WebShortcut shortcut, AppEntry app) {
    WebIconManager.deleteIcon(this, shortcut.id);
    shortcutStore.delete(shortcut.id);
    List<String> order = AppOrder.reconcile(prefs.appOrder(), installedIds());
    prefs.saveAppOrder(order);
    selectedId = "";
    render();
    toast("已删除网页快捷卡片");
  }

  private void editWebShortcut(WebShortcut shortcut, boolean focusUrl) {
    editWebDialog =
        new WebShortcutEditDialog(
            this,
            shortcut,
            shortcutStore,
            palette,
            scale,
            focusUrl,
            updated -> {
              render();
              toast(focusUrl ? "网址已更新" : "名称已更新");
            });
    editWebDialog.setOnDismissListener(
        d -> {
          editWebDialog = null;
          if (pendingRender) render();
        });
    editWebDialog.show();
  }

  private void showAddWebShortcutDialog() {
    addWebDialog =
        new AddWebShortcutDialog(
            this,
            palette,
            scale,
            shortcutStore,
            added -> {
              selectedId = WebShortcutStore.toAppEntry(added).id;
              render();
              toast("已添加网页快捷卡片");
              if (!WebIconManager.hasIcon(this, added.id) || WebShortcut.isUrlName(added.name, added.url)) {
                WebIconManager.fetchFaviconAsync(this, added, null);
              }
            });
    addWebDialog.setOnDismissListener(
        d -> {
          addWebDialog = null;
          if (pendingRender) render();
        });
    addWebDialog.show();
  }

  private void showAirInstallDialog() {
    if (isFinishing() || isDestroyed()) return;
    if (airInstallDialog != null && airInstallDialog.isShowing()) return;
    if (airInstallDialog != null) {
      airInstallDialog.dismiss();
      airInstallDialog = null;
    }
    hideScreensaver(false);
    cancelScreensaverTimer();
    airInstallDialog = new AirInstallDialog(this, palette, scale);
    airInstallDialog.setOnDismissListener(
        d -> {
          if (airInstallDialog == d) airInstallDialog = null;
          if (pendingRender) render();
          restartScreensaverTimer();
        });
    airInstallDialog.show();
  }

  private void manage(AppEntry app, View source) {
    if (actionLayer != null) return;
    cancelScreensaverTimer();
    menuReturn = source;
    if (source instanceof AppCardView) ((AppCardView) source).contextSelected(true);
    desktop.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    List<AppActionMenuView.Item> items = new ArrayList<>();
    items.add(
        new AppActionMenuView.Item("移动位置", R.drawable.menu_move, false, () -> beginMove(app)));
    items.add(
        new AppActionMenuView.Item(
            "移到最前", R.drawable.menu_move_first, false, () -> moveToFront(app)));
    items.add(
        new AppActionMenuView.Item(
            "移到最后", R.drawable.menu_move_last, false, () -> moveToEdge(app, false)));
    items.add(new AppActionMenuView.Item("重命名", R.drawable.menu_rename, false, () -> rename(app)));
    items.add(
        new AppActionMenuView.Item(
            "应用信息",
            R.drawable.menu_info,
            false,
            () ->
                safeStart(
                    new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + app.component.getPackageName())))));
    String blockedReason = AppUninstall.blockedReason(this, app);
    boolean removable = blockedReason.isEmpty();
    items.add(
        new AppActionMenuView.Item(
            "卸载应用",
            R.drawable.menu_uninstall,
            true,
            removable ? () -> uninstall(app) : null,
            blockedReason));
    actionLayer =
        new AppActionMenuView(
            this,
            scale,
            names.label(app),
            names.custom(app).isEmpty() ? "" : "原名称：" + app.label,
            artwork(app, false),
            items,
            action -> closeMenu(false, action),
            palette);
    root.addView(actionLayer, new FrameLayout.LayoutParams(-1, -1));
    actionLayer.reveal();
  }

  private void rename(AppEntry app) {
    renameDialog =
        new AppRenameDialog(
            this,
            app,
            names,
            palette,
            scale,
            () -> {
              if (homeWall != null) homeWall.refreshLabels();
              toast("名称已保存");
            });
    renameDialog.setOnDismissListener(
        d -> {
          renameDialog = null;
          if (pendingRender) render();
        });
    renameDialog.show();
  }

  private void uninstall(AppEntry app) {
    pendingUninstall = app.component.getPackageName();
    try {
      AppUninstall.request(this, app);
    } catch (RuntimeException e) {
      pendingUninstall = "";
      toast("当前系统无法卸载此应用，可在应用信息中查看");
    }
  }

  private void closeMenu(boolean restore) {
    closeMenu(restore, null);
  }

  private void closeMenu(boolean restore, Runnable action) {
    if (actionLayer == null) return;
    AppActionMenuView old = actionLayer;
    View target = menuReturn;
    old.dismiss(
        () -> {
          // Home or a new intent may have replaced the entire root during dismissal.
          if (actionLayer != old) return;
          root.removeView(old);
          actionLayer = null;
          menuReturn = null;
          desktop.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
          if (pendingRender) render();
          else if ((restore || action != null) && target != null && target.isAttachedToWindow())
            target.requestFocus();
          if (target instanceof AppCardView) ((AppCardView) target).contextSelected(false);
          if (action != null) action.run();
          restartScreensaverTimer();
        });
  }

  private void moveToFront(AppEntry app) {
    moveToEdge(app, true);
  }

  private void moveToEdge(AppEntry app, boolean first) {
    List<String> order = AppOrder.reconcile(prefs.appOrder(), installedIds());
    if (!order.contains(app.id)) {
      toast("应用已变更，请重试");
      return;
    }
    prefs.saveAppOrder(
        first ? AppOrder.moveToFront(order, app.id) : AppOrder.moveToEnd(order, app.id));
    selectedId = app.id;
    homeOffset = 0;
    homeWall.setEntries(desktopApps(), selectedId, false);
    toast(first ? "已移到最前" : "已移到最后");
  }

  private void beginMove(AppEntry app) {
    draftOrder = AppOrder.reconcile(prefs.appOrder(), installedIds());
    if (!draftOrder.contains(app.id)) {
      draftOrder = null;
      render();
      return;
    }
    cancelScreensaverTimer();
    movingId = app.id;
    selectedId = app.id;
    moveHint.setText("方向键移动位置 · 确定保存 · 返回取消");
    homeWall.setEntries(orderedApps(), movingId, false);
    homeWall.movingKey(movingId);
  }

  private boolean moveKey(int code) {
    int delta;
    switch (code) {
      case KeyEvent.KEYCODE_DPAD_LEFT:
        delta = -1;
        break;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        delta = 1;
        break;
      case KeyEvent.KEYCODE_DPAD_UP:
        delta = -GridWallView.COLUMNS;
        break;
      case KeyEvent.KEYCODE_DPAD_DOWN:
        delta = GridWallView.COLUMNS;
        break;
      default:
        return false;
    }
    draftOrder = AppOrder.reconcile(draftOrder, installedIds());
    List<AppEntry> visible = orderedApps();
    int from = -1;
    for (int i = 0; i < visible.size(); i++) if (visible.get(i).id.equals(movingId)) from = i;
    if (from < 0) {
      finishMove(false);
      return true;
    }
    int target = Math.max(0, Math.min(visible.size() - 1, from + delta));
    if (from == target) return true;
    int to = draftOrder.indexOf(visible.get(target).id);
    draftOrder.remove(movingId);
    draftOrder.add(to, movingId);
    homeWall.setEntries(orderedApps(), movingId, true);
    return true;
  }

  private void finishMove(boolean save) {
    if (draftOrder == null) return;
    if (save) prefs.saveAppOrder(AppOrder.reconcile(draftOrder, installedIds()));
    draftOrder = null;
    movingId = null;
    homeWall.movingKey(null);
    moveHint.setText("");
    List<AppEntry> wallList = new ArrayList<>(orderedApps());
    wallList.add(AirInstallEntry.ENTRY);
    wallList.add(WebShortcutStore.ADD_WEB_ENTRY);
    if (pendingRender) render();
    else homeWall.setEntries(wallList, selectedId, true);
    if (save) toast("顺序已保存");
    restartScreensaverTimer();
  }

  private void settingsPage() {
    ScrollView sc = new ScrollView(this);
    sc.setClipChildren(false);
    sc.setClipToPadding(false);
    sc.setVerticalScrollBarEnabled(false);
    put(sc, 194, 78, 572, 424);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setClipChildren(false);
    list.setClipToPadding(false);
    list.setPadding(px(8), px(8), px(8), px(8));
    sc.addView(list);
    TextView heading = text("设置", 22, palette.text);
    heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    list.addView(heading, new LinearLayout.LayoutParams(-1, px(30)));
    Map<String, SettingsRowView> rows = new LinkedHashMap<>();
    LinearLayout desktop = settingsGroup(list, "桌面", false);
    TextView desktopHint = text("", 11, palette.muted);
    desktopHint.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    put(desktopHint, 212, 509, 552, 20);
    SettingsRowView first =
        settingsRow(desktop, rows, "打开电视原生桌面", R.drawable.settings_open_desktop, "", false);
    first.setId(R.id.first_app);
    first.setOnClickListener(v -> openOtherHome());
    View.OnFocusChangeListener desktopFocus = first.getOnFocusChangeListener();
    first.setOnFocusChangeListener(
        (v, focused) -> {
          desktopFocus.onFocusChange(v, focused);
          desktopHint.setText(
              focused
                  ? (getPackageName().equals(DeviceDiagnostics.homePackage(this))
                      ? "临时打开，按 Home 返回 Still"
                      : "临时打开，不更改默认桌面")
                  : "");
        });
    String home = DeviceDiagnostics.homeLabel(this);
    SettingsRowView defaultHome =
        settingsRow(desktop, rows, "默认桌面", R.drawable.settings_home, home, false);
    homeSetting = defaultHome;
    defaultHome.setOnClickListener(v -> requestHome());
    SettingsRowView boot =
        settingsRow(
            desktop,
            rows,
            "自动进入 Still",
            R.drawable.settings_power_settings_new,
            AutoEntry.supportsWake() ? "" : "仅开机",
            true);
    boot.checked(prefs.bootEnabled());
    boot.setOnClickListener(
        v -> {
          prefs.bootEnabled(!prefs.bootEnabled());
          boot.checked(prefs.bootEnabled());
          boolean ready = AutoEntry.sync(this);
          if (prefs.bootEnabled())
            toast(
                AutoEntry.supportsWake()
                    ? (ready ? "已开启：开机和待机唤醒后返回 Still" : "开机启动已开启，唤醒监听未能启动，请查看设备信息")
                    : "此系统暂仅尝试开机进入，待机唤醒尚未适配");
          else toast("已关闭自动进入 Still");
        });
    SettingsRowView names =
        settingsRow(desktop, rows, "始终显示应用名称", R.drawable.settings_description, "", true);
    names.checked(prefs.showAppNames());
    names.setOnClickListener(
        v -> {
          prefs.showAppNames(!prefs.showAppNames());
          names.checked(prefs.showAppNames());
        });
    SettingsRowView appearance =
        settingsRow(desktop, rows, "外观与壁纸", R.drawable.settings_theme, "", false);
    updateAppearanceSetting(appearance, prefs.wallpaper());
    View.OnFocusChangeListener appearanceFocus = appearance.getOnFocusChangeListener();
    appearance.setOnFocusChangeListener(
        (v, focused) -> {
          appearanceFocus.onFocusChange(v, focused);
          desktopHint.setText(focused ? "左右方向键切换外观与壁纸" : "");
        });
    appearance.stepper(
        () -> previewInlineTheme(false, appearance, desktopHint),
        () -> previewInlineTheme(true, appearance, desktopHint));

    SettingsRowView screensaver =
        settingsRow(desktop, rows, "桌面屏保", R.drawable.settings_theme, "", false);
    updateScreensaverSetting(screensaver, screensaverDelayIndex());
    View.OnFocusChangeListener screensaverFocus = screensaver.getOnFocusChangeListener();
    screensaver.setOnFocusChangeListener(
        (v, focused) -> {
          screensaverFocus.onFocusChange(v, focused);
          desktopHint.setText(focused ? "左右键调整等待时间" : "");
        });
    screensaver.stepper(
        () -> stepScreensaverSetting(screensaver, -1),
        () -> stepScreensaverSetting(screensaver, 1));
    LinearLayout application = settingsGroup(list, "应用", true);
    SettingsRowView system =
        settingsRow(application, rows, "系统设置", R.drawable.settings_settings, "", false);
    system.setOnClickListener(v -> safeStart(new Intent(Settings.ACTION_SETTINGS)));
    SettingsRowView refresh =
        settingsRow(application, rows, "刷新应用列表", R.drawable.settings_refresh, "", false);
    refresh.setOnClickListener(
        v -> {
          reload(false);
          toast("正在后台刷新应用列表");
        });
    LinearLayout device = settingsGroup(list, "设备", true);
    SettingsRowView info =
        settingsRow(device, rows, "设备与启动信息", R.drawable.settings_description, "", false);
    info.setOnClickListener(
        v ->
            new AlertDialog.Builder(this)
                .setTitle("设备与启动信息")
                .setMessage(
                    DeviceDiagnostics.report(this) + "\n\n自动进入由同一个开关控制；系统重启、待机唤醒和长时间待机需分别验证。")
                .setPositiveButton(
                    "复制报告",
                    (d, w) -> {
                      android.content.ClipboardManager cb =
                          (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                      cb.setPrimaryClip(
                          ClipData.newPlainText("Still 设备报告", DeviceDiagnostics.report(this)));
                      toast("已复制报告");
                    })
                .setNegativeButton("关闭", null)
                .show());
    SettingsRowView checkUpdate =
        settingsRow(
            device,
            rows,
            "检查更新",
            R.drawable.settings_update,
            "v" + DeviceDiagnostics.appVersion(this),
            false);
    checkUpdate.setOnClickListener(v -> showUpdateDialog());
    List<SettingsRowView> order = new ArrayList<>(rows.values());
    for (int i = 0; i < order.size(); i++) {
      SettingsRowView row = order.get(i);
      row.setNextFocusUpId(i == 0 ? R.id.settings_button : order.get(i - 1).getId());
      row.setNextFocusDownId(order.get(Math.min(i + 1, order.size() - 1)).getId());
      row.setNextFocusLeftId(row.getId());
      row.setNextFocusRightId(row.getId());
    }
    (rows.containsKey(selectedSetting) ? rows.get(selectedSetting) : first).requestFocus();
  }

  private int screensaverDelayIndex() {
    int seconds = prefs.screensaverDelaySeconds();
    for (int i = 0; i < SCREENSAVER_DELAYS.length; i++) {
      if (SCREENSAVER_DELAYS[i] == seconds) return i;
    }
    return 1;
  }

  private void stepScreensaverSetting(SettingsRowView row, int direction) {
    int count = SCREENSAVER_DELAYS.length;
    int index = (screensaverDelayIndex() + direction + count) % count;
    prefs.screensaverDelaySeconds(SCREENSAVER_DELAYS[index]);
    updateScreensaverSetting(row, index);
  }

  private void updateScreensaverSetting(SettingsRowView row, int index) {
    String label = SCREENSAVER_DELAY_LABELS[index];
    row.detail(label);
    row.setContentDescription("桌面屏保，" + label + "，左右键调整等待时间");
  }

  private void showUpdateDialog() {
    if (isFinishing() || isDestroyed()) return;
    if (updateDialog != null && updateDialog.isShowing()) return;
    if (updateDialog != null) {
      updateDialog.dismiss();
      updateDialog = null;
    }
    hideScreensaver(false);
    cancelScreensaverTimer();
    updateDialog = new AppUpdateDialog(this, palette, scale, () -> {
      page = "home";
      render();
    });
    updateDialog.setOnDismissListener(
        d -> {
          if (updateDialog == d) updateDialog = null;
          restartScreensaverTimer();
        });
    updateDialog.show();
  }

  private LinearLayout settingsGroup(LinearLayout list, String title, boolean gap) {
    TextView caption = text(title, 14, palette.muted);
    caption.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, px(20));
    cp.topMargin = gap ? px(8) : 0;
    caption.setPadding(px(10), 0, 0, 0);
    list.addView(caption, cp);
    LinearLayout group = new LinearLayout(this);
    group.setOrientation(LinearLayout.VERTICAL);
    group.setClipChildren(false);
    group.setClipToPadding(false);
    GradientDrawable background =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {palette.surfaceTop, palette.surfaceBottom});
    background.setCornerRadius(px(12));
    group.setBackground(background);
    list.addView(group, new LinearLayout.LayoutParams(-1, -2));
    return group;
  }

  private SettingsRowView settingsRow(
      LinearLayout group,
      Map<String, SettingsRowView> rows,
      String title,
      int icon,
      String detail,
      boolean toggle) {
    if (group.getChildCount() > 0) {
      View divider = new View(this);
      divider.setBackgroundColor(palette.divider);
      LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, px(1));
      dp.leftMargin = px(68);
      dp.rightMargin = px(20);
      group.addView(divider, dp);
    }
    SettingsRowView row = new SettingsRowView(this, scale, title, icon, detail, toggle, palette);
    row.setId(View.generateViewId());
    View.OnFocusChangeListener visual = row.getOnFocusChangeListener();
    row.setOnFocusChangeListener(
        (v, f) -> {
          visual.onFocusChange(v, f);
          if (f) selectedSetting = title;
        });
    group.addView(row, new LinearLayout.LayoutParams(-1, px(48)));
    rows.put(title, row);
    return row;
  }

  private void requestHome() {
    AlertDialog.Builder dialog =
        new AlertDialog.Builder(this)
            .setTitle("默认桌面")
            .setMessage(
                "当前检测："
                    + DeviceDiagnostics.homeLabel(this)
                    + "\n\n请以从其他应用按 Home 后实际进入的界面为准。可打开系统设置选择默认桌面，或临时打开其他桌面。")
            .setPositiveButton("系统桌面设置", (d, w) -> openHomeSettings())
            .setNegativeButton("关闭", null);
    if (!otherHomes().isEmpty()) dialog.setNeutralButton("打开其他桌面", (d, w) -> openOtherHome());
    dialog.show();
  }

  private List<android.content.pm.ResolveInfo> otherHomes() {
    return new OtherHome(this).candidates();
  }

  private void openOtherHome() {
    OtherHome launcher = new OtherHome(this);
    List<android.content.pm.ResolveInfo> candidates = launcher.candidates();
    ComponentName preferred = launcher.preferred(candidates);
    if (candidates.isEmpty()) {
      toast("暂时无法打开电视原生桌面");
      return;
    }
    if (preferred != null) {
      if (!launcher.open(preferred)) toast("暂时无法打开电视原生桌面");
      return;
    }
    String[] labels = new String[candidates.size()];
    for (int i = 0; i < labels.length; i++)
      labels[i] = candidates.get(i).loadLabel(getPackageManager()).toString();
    AlertDialog picker =
        new AlertDialog.Builder(this)
            .setTitle("选择电视桌面")
            .setItems(
                labels,
                (d, which) -> {
                  if (!launcher.open(OtherHome.component(candidates.get(which))))
                    toast("暂时无法打开电视原生桌面");
                })
            .setNegativeButton("取消", null)
            .create();
    picker.setOnShowListener(
        d -> {
          picker.getListView().requestFocus();
          picker.getListView().setSelection(0);
        });
    picker.show();
  }

  private void openHomeSettings() {
    waitingForHomeSettings = true;
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles != null
            && roles.isRoleAvailable(RoleManager.ROLE_HOME)
            && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
          startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 50);
          watchHomeSettings();
          return;
        }
      }
    } catch (RuntimeException ignored) {
    }
    List<String> actions = new ArrayList<>();
    actions.add(Settings.ACTION_HOME_SETTINGS);
    if (Build.VERSION.SDK_INT >= 24) actions.add(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
    for (String action : actions) {
      try {
        Intent intent = new Intent(action);
        if (intent.resolveActivity(getPackageManager()) != null) {
          startActivity(intent);
          watchHomeSettings();
          return;
        }
      } catch (RuntimeException ignored) {
      }
    }
    waitingForHomeSettings = false;
    homeSettingsUnavailable();
  }

  private void watchHomeSettings() {
    handler.postDelayed(
        () -> {
          if (waitingForHomeSettings && hasWindowFocus() && !isFinishing()) {
            waitingForHomeSettings = false;
            homeSettingsUnavailable();
          }
        },
        1200);
  }

  private void homeSettingsUnavailable() {
    new AlertDialog.Builder(this)
        .setTitle("系统未显示桌面选择页")
        .setMessage("可以尝试在电视系统设置中查找“默认应用”或“桌面”。若从其他应用按 Home 已经回到 Still，可继续使用；开机自动进入需要另外验证。")
        .setPositiveButton("打开系统设置", (d, w) -> safeStart(new Intent(Settings.ACTION_SETTINGS)))
        .setNegativeButton("关闭", null)
        .show();
  }

  private void safeStart(Intent i) {
    try {
      startActivity(i);
    } catch (RuntimeException e) {
      toast("当前系统不支持此入口");
    }
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_LONG).show();
  }

  @Override
  public void onBackPressed() {
    if (screensaverOverlay != null) {
      hideScreensaver(true);
      restartScreensaverTimer();
      return;
    }
    if (actionLayer != null) closeMenu(true);
    else if (draftOrder != null) finishMove(false);
    else if (!page.equals("home")) {
      page = "home";
      render();
    } else if (homeWall != null) {
      homeWall.returnToFirstRow();
      restartScreensaverTimer();
    }
  }
}
