package com.jianping.tv.ui;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import com.jianping.tv.R;
import com.jianping.tv.data.*;
import com.jianping.tv.platform.DeviceDiagnostics;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

/** Native TV launcher; persistent data remains independent from its remote-driven presentation. */
public final class MainActivity extends Activity {
  private Preferences prefs;
  private UiPalette palette;
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
  private TextView clock, calendarDate, moveHint;
  private GridWallView homeWall;
  private View menuReturn;
  private String selectedSetting = "默认桌面", selectedId = "", page = "home";
  private AmbientBackgroundView ambient;
  private float scale = 1;
  private boolean loading = true, pendingRender, catalogError;
  private List<String> draftOrder;
  private String movingId;
  private int loadGeneration;
  private int homeOffset;
  private final Runnable ticker =
      new Runnable() {
        public void run() {
          updateClock();
          handler.postDelayed(this, 1000);
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
    palette = UiPalette.from(prefs.lightTheme());
    setTheme(palette.light ? R.style.AppThemeLight : R.style.AppTheme);
    super.onCreate(state);
    repository = new AppRepository(this);
    catalog = new AppCatalogCache(this);
    snapshot = catalog.memory();
    if (snapshot != null) {
      apps = snapshot.apps;
      loading = false;
    }
    if (state != null) {
      page = state.getString("page", "home");
      selectedId = state.getString("selected", "");
      homeOffset = state.getInt("homeOffset", 0);
    }
    recordEntry(getIntent());
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_PACKAGE_ADDED);
    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
    filter.addDataScheme("package");
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(packageChanges, filter, Context.RECEIVER_EXPORTED);
    else registerReceiver(packageChanges, filter);
    render();
    reload(true);
    handler.post(ticker);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    recordEntry(intent);
    boolean needsRender = !page.equals("home") || actionLayer != null || draftOrder != null;
    draftOrder = null;
    movingId = null;
    page = "home";
    if (needsRender) render();
  }

  private void recordEntry(Intent i) {
    String now = DateFormat.getDateTimeInstance().format(new Date());
    if (i.getBooleanExtra("boot", false)) prefs.record("bootVisible", now);
    if (i.hasCategory(Intent.CATEGORY_HOME)) prefs.record("homeVisible", now);
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    b.putString("page", page);
    b.putString("selected", selectedId);
    b.putInt("homeOffset", homeWall == null ? homeOffset : homeWall.getScrollY());
    super.onSaveInstanceState(b);
  }

  @Override
  protected void onResume() {
    super.onResume();
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    // Returning from another app keeps the existing wall and decoded artwork intact.
    if (page.equals("home") && homeWall != null && root.findFocus() == null)
      homeWall.restoreFocus(selectedId, homeWall.getScrollY());
    if (homeSetting != null && page.equals("settings"))
      homeSetting.detail(DeviceDiagnostics.homeLabel(this));
  }

  @Override
  protected void onPause() {
    waitingForHomeSettings = false;
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
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
                  AppCatalogCache.Visual visual =
                      cached == null ? null : cached.visuals.get(app.id);
                  boolean dirty = invalidated.contains(app.component.getPackageName());
                  if (dirty || (visual != null && !app.fingerprint.equals(visual.fingerprint)))
                    visual = null;
                  if (visual == null && !dirty) visual = catalog.readVisual(app);
                  if (visual == null) {
                    AppRepository.Artwork raw = repository.artwork(app);
                    int color = IconPalette.accent(raw.icon);
                    Drawable art;
                    try {
                      art = IconArtwork.landscape(getResources(), raw.icon, raw.banner, color);
                    } catch (RuntimeException e) {
                      art = IconArtwork.fallback(getResources(), raw.icon);
                    }
                    Drawable soft = IconArtwork.preview(getResources(), art);
                    visual = new AppCatalogCache.Visual(app.fingerprint, color, art, soft);
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
    snapshot = next;
    catalog.remember(next);
    apps = next.apps;
    loading = false;
    catalogError = false;
    if (fresh) {
      List<String> order = AppOrder.reconcile(prefs.appOrder(), installedIds());
      if (!prefs.hasAppOrder() || !order.equals(prefs.appOrder())) prefs.saveAppOrder(order);
    }
    if (!changed) return;
    if (draftOrder != null || actionLayer != null) pendingRender = true;
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

  private View headerAction(boolean home, Runnable action) {
    return headerAction(
        home ? R.drawable.nav_settings : R.drawable.nav_back, home ? "设置" : "返回", action);
  }

  private View headerAction(int icon, String label, Runnable action) {
    FrameLayout button = new FrameLayout(this);
    ImageView glyph = new ImageView(this);
    glyph.setImageResource(icon);
    glyph.setColorFilter(palette.icon);
    int glyphSize = px(icon == R.drawable.nav_back ? 24 : 20);
    // Align the magnifier's circular body optically with the centre of the gear.
    if (icon == R.drawable.nav_search) glyph.setTranslationY(px(2));
    button.addView(glyph, new FrameLayout.LayoutParams(glyphSize, glyphSize, Gravity.CENTER));
    button.setFocusable(true);
    button.setClickable(true);
    button.setContentDescription(label);
    button.setOnFocusChangeListener(
        (v, focus) -> {
          button.setBackground(rounded(focus ? palette.headerFocus : Color.TRANSPARENT, 18));
          glyph.setColorFilter(focus ? palette.headerFocusIcon : palette.icon);
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
    AppCatalogCache.Visual visual = snapshot == null ? null : snapshot.visuals.get(app.id);
    return visual == null ? 0xFF7092CC : visual.accent;
  }

  private Drawable artwork(AppEntry app, boolean soft) {
    AppCatalogCache.Visual visual = snapshot.visuals.get(app.id);
    if (visual == null) return getDrawable(android.R.drawable.sym_def_app_icon);
    Drawable image = soft ? visual.preview : visual.card;
    Drawable.ConstantState state = image.getConstantState();
    return state == null ? image : state.newDrawable(getResources()).mutate();
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

  private void render() {
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
    pendingRender = false;
    ambient = new AmbientBackgroundView(this, palette);
    desktop.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
    clock = text("", 18, palette.secondary);
    clock.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    clock.setLetterSpacing(.015f);
    clock.setFontFeatureSettings("'tnum' 1");
    clock.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    put(clock, page.equals("home") ? 648 : 698, 20, 176, 25);
    calendarDate = text("", 12, palette.muted);
    calendarDate.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    calendarDate.setFontFeatureSettings("'tnum' 1");
    put(calendarDate, page.equals("home") ? 648 : 698, 46, 176, 18);
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
    put(settings, 884, 23, 42, 42);
    if (page.equals("home")) {
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
                startActivity(new Intent(this, SearchActivity.class));
              });
      search.setId(View.generateViewId());
      put(search, 834, 23, 42, 42);
      home(search);
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
      search.setNextFocusRightId(settings.getId());
      search.setNextFocusLeftId(search.getId());
      settings.setNextFocusLeftId(search.getId());
      settings.setNextFocusRightId(settings.getId());
      settings.setNextFocusDownId(apps.isEmpty() ? search.getId() : homeWall.firstId());
    } else {
      settings.setNextFocusDownId(R.id.first_app);
      settingsPage();
    }
  }

  private List<String> installedIds() {
    List<String> ids = new ArrayList<>();
    for (AppEntry app : apps) ids.add(app.id);
    return ids;
  }

  private List<AppEntry> orderedApps() {
    Map<String, AppEntry> byId = new HashMap<>();
    for (AppEntry app : apps) byId.put(app.id, app);
    List<AppEntry> result = new ArrayList<>();
    for (String id :
        AppOrder.reconcile(draftOrder == null ? prefs.appOrder() : draftOrder, installedIds()))
      result.add(byId.get(id));
    return result;
  }

  private GridWallView.Listener wallListener() {
    return new GridWallView.Listener() {
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
        else launch(app);
      }

      public void hold(AppEntry app, View source) {
        if (draftOrder == null) manage(app, source);
      }

      public boolean key(int code, KeyEvent event) {
        if (draftOrder == null || event.getAction() != KeyEvent.ACTION_DOWN) return false;
        return moveKey(code);
      }
    };
  }

  private void home(View settings) {
    moveHint = text("", 14, palette.secondary);
    put(moveHint, 210, 36, 480, 30);
    homeWall = new GridWallView(this, scale, 156, palette, wallListener());
    homeWall.showNames(prefs.showAppNames());
    put(homeWall, 42, 82, 876, 424);
    String previous = selectedId;
    homeWall.setEntries(orderedApps(), selectedId, false);
    if (previous.equals(selectedId)) homeWall.restoreFocus(selectedId, homeOffset);
    homeWall.upperFocus(settings.getId());
    if (apps.isEmpty()) {
      TextView empty =
          text(
              loading ? "正在读取应用…" : (catalogError ? "读取失败，请在设置中重试" : "暂无可启动的应用"),
              19,
              palette.secondary);
      put(empty, 200, 225, 560, 45);
      settings.requestFocus();
      // An empty loading wall must not overwrite the saved scroll position on cache restore.
      homeWall = null;
    }
    settings.setNextFocusDownId(apps.isEmpty() ? settings.getId() : homeWall.firstId());
    settings.setNextFocusLeftId(settings.getId());
    settings.setNextFocusRightId(settings.getId());
  }

  private void launch(AppEntry app) {
    try {
      startActivity(repository.launchIntent(app));
    } catch (RuntimeException e) {
      toast("无法打开此应用，请刷新应用列表");
      reload(false);
    }
  }

  private void manage(AppEntry app, View source) {
    if (actionLayer != null) return;
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
            "应用信息",
            R.drawable.menu_info,
            false,
            () ->
                safeStart(
                    new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + app.component.getPackageName())))));
    actionLayer =
        new AppActionMenuView(
            this,
            scale,
            app.label,
            artwork(app, false),
            items,
            action -> closeMenu(false, action),
            palette);
    root.addView(actionLayer, new FrameLayout.LayoutParams(-1, -1));
    actionLayer.reveal();
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
        });
  }

  private void moveToFront(AppEntry app) {
    List<String> order = AppOrder.reconcile(prefs.appOrder(), installedIds());
    if (!order.remove(app.id)) {
      toast("应用已变更，请重试");
      return;
    }
    order.add(0, app.id);
    prefs.saveAppOrder(order);
    selectedId = app.id;
    homeOffset = 0;
    homeWall.setEntries(orderedApps(), selectedId, false);
    toast("已移到最前");
  }

  private void beginMove(AppEntry app) {
    draftOrder = AppOrder.reconcile(prefs.appOrder(), installedIds());
    if (!draftOrder.contains(app.id)) {
      draftOrder = null;
      render();
      return;
    }
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
    if (pendingRender) render();
    else homeWall.setEntries(orderedApps(), selectedId, true);
    if (save) toast("顺序已保存");
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
    String home = DeviceDiagnostics.homeLabel(this);
    SettingsRowView first =
        settingsRow(desktop, rows, "默认桌面", R.drawable.settings_home, home, false);
    homeSetting = first;
    first.setId(R.id.first_app);
    first.setOnClickListener(v -> requestHome());
    SettingsRowView boot =
        settingsRow(desktop, rows, "开机启动", R.drawable.settings_power_settings_new, "", true);
    boot.checked(prefs.bootEnabled());
    boot.setOnClickListener(
        v -> {
          prefs.bootEnabled(!prefs.bootEnabled());
          boot.checked(prefs.bootEnabled());
        });
    SettingsRowView names =
        settingsRow(desktop, rows, "显示应用名称", R.drawable.settings_description, "", true);
    names.checked(prefs.showAppNames());
    names.setOnClickListener(
        v -> {
          prefs.showAppNames(!prefs.showAppNames());
          names.checked(prefs.showAppNames());
        });
    SettingsRowView appearance =
        settingsRow(
            desktop, rows, "外观主题", R.drawable.settings_theme, palette.light ? "浅色" : "深色", false);
    appearance.setOnClickListener(v -> showThemePicker());
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
                .setMessage(DeviceDiagnostics.report(this) + "\n\n开机实验可能被系统拦截；待机唤醒与重新开机需要分别验证。")
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
    group.addView(row, new LinearLayout.LayoutParams(-1, px(42)));
    rows.put(title, row);
    return row;
  }

  private void showThemePicker() {
    new AlertDialog.Builder(this)
        .setTitle("外观主题")
        .setSingleChoiceItems(
            new String[] {"浅色", "深色"},
            palette.light ? 0 : 1,
            (dialog, which) -> {
              dialog.dismiss();
              boolean light = which == 0;
              if (light == palette.light) return;
              prefs.lightTheme(light);
              palette = UiPalette.from(light);
              setTheme(light ? R.style.AppThemeLight : R.style.AppTheme);
              render();
            })
        .setNegativeButton("取消", null)
        .show();
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
    List<android.content.pm.ResolveInfo> result = new ArrayList<>();
    for (android.content.pm.ResolveInfo info : DeviceDiagnostics.homeCandidates(this))
      if (!getPackageName().equals(info.activityInfo.packageName)
          && DeviceDiagnostics.usableHome(info)) result.add(info);
    return result;
  }

  private void openOtherHome() {
    List<android.content.pm.ResolveInfo> candidates = otherHomes();
    if (candidates.isEmpty()) {
      toast("未发现其他可打开的桌面");
      return;
    }
    String[] labels = new String[candidates.size()];
    for (int i = 0; i < labels.length; i++)
      labels[i] = candidates.get(i).loadLabel(getPackageManager()).toString();
    new AlertDialog.Builder(this)
        .setTitle("打开其他桌面")
        .setItems(
            labels,
            (d, which) -> {
              android.content.pm.ActivityInfo activity = candidates.get(which).activityInfo;
              safeStart(
                  DeviceDiagnostics.homeIntent()
                      .setComponent(new ComponentName(activity.packageName, activity.name))
                      .addFlags(
                          Intent.FLAG_ACTIVITY_NEW_TASK
                              | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            })
        .setNegativeButton("取消", null)
        .show();
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
    if (actionLayer != null) closeMenu(true);
    else if (draftOrder != null) finishMove(false);
    else if (!page.equals("home")) {
      page = "home";
      render();
    } else if (homeWall != null) homeWall.returnToFirstRow();
  }
}
