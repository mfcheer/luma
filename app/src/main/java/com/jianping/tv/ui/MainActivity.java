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
  private AppRepository repository;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private List<AppEntry> apps = new ArrayList<>();
  private FrameLayout root, desktop;
  private AppActionMenuView actionLayer;
  private TextView clock, moveHint;
  private GridWallView homeWall;
  private View menuReturn;
  private String selectedSetting = "默认桌面", selectedId = "", page = "home";
  private AmbientBackgroundView ambient;
  private final Map<String, Integer> paletteCache = new HashMap<>();
  private final Map<String, Drawable> artworkCache = new HashMap<>(),
      previewCache = new HashMap<>();
  private float scale = 1;
  private boolean loading = true, pendingRender;
  private List<String> draftOrder;
  private String movingId;
  private int loadGeneration;
  private int homeOffset;
  private final Runnable ticker =
      new Runnable() {
        public void run() {
          if (clock != null)
            clock.setText(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()));
          handler.postDelayed(this, 1000);
        }
      };
  private final BroadcastReceiver packageChanges =
      new BroadcastReceiver() {
        public void onReceive(Context c, Intent intent) {
          // An APK replacement briefly removes its old entry; retain its slot until added.
          if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
              && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
          reload();
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    prefs = new Preferences(this);
    repository = new AppRepository(this);
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
    reload();
    handler.post(ticker);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    recordEntry(intent);
    draftOrder = null;
    movingId = null;
    page = "home";
    render();
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
    if (!loading) reload();
  }

  @Override
  protected void onDestroy() {
    handler.removeCallbacksAndMessages(null);
    unregisterReceiver(packageChanges);
    worker.shutdownNow();
    super.onDestroy();
  }

  private void reload() {
    final int generation = ++loadGeneration;
    worker.execute(
        () -> {
          List<AppEntry> result = repository.load();
          Map<String, Drawable> art = new HashMap<>(), soft = new HashMap<>();
          Map<String, Integer> colors = new HashMap<>();
          for (AppEntry app : result) {
            if (Thread.currentThread().isInterrupted()) return;
            try {
              int color = IconPalette.accent(app.icon);
              Drawable card = IconArtwork.landscape(getResources(), app.icon, app.banner, color);
              colors.put(app.id, color);
              art.put(app.id, card);
              soft.put(app.id, IconArtwork.preview(getResources(), card));
            } catch (RuntimeException e) {
              colors.put(app.id, 0xFF7092CC);
              art.put(app.id, app.icon);
              soft.put(app.id, app.icon);
            }
          }
          runOnUiThread(
              () -> {
                if (isDestroyed() || generation != loadGeneration) return;
                apps = result;
                artworkCache.clear();
                artworkCache.putAll(art);
                previewCache.clear();
                previewCache.putAll(soft);
                paletteCache.clear();
                paletteCache.putAll(colors);
                loading = false;
                List<String> order = AppOrder.reconcile(prefs.appOrder(), installedIds());
                if (!prefs.hasAppOrder() || !order.equals(prefs.appOrder()))
                  prefs.saveAppOrder(order);
                if (draftOrder != null || actionLayer != null) pendingRender = true;
                else render();
              });
        });
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
    FrameLayout button = new FrameLayout(this);
    ImageView glyph = new ImageView(this);
    glyph.setImageResource(home ? R.drawable.settings_settings : R.drawable.nav_back);
    glyph.setColorFilter(0xFFBBC7D9);
    button.addView(
        glyph,
        new FrameLayout.LayoutParams(px(home ? 19 : 25), px(home ? 19 : 25), Gravity.CENTER));
    button.setFocusable(true);
    button.setClickable(true);
    button.setContentDescription(home ? "设置" : "返回");
    button.setOnFocusChangeListener(
        (v, focus) -> {
          button.setBackground(rounded(focus ? 0x20FFFFFF : Color.TRANSPARENT, 18));
          glyph.setColorFilter(focus ? Color.WHITE : 0xFFBBC7D9);
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
    Integer color = paletteCache.get(app.id);
    return color == null ? 0xFF7092CC : color;
  }

  private Drawable artwork(AppEntry app, boolean soft) {
    String key = app.id;
    if (!artworkCache.containsKey(key)) {
      Drawable source = app.icon;
      int color = IconPalette.accent(source);
      paletteCache.put(key, color);
      Drawable art = IconArtwork.landscape(getResources(), source, app.banner, color);
      artworkCache.put(key, art);
      previewCache.put(key, IconArtwork.preview(getResources(), art));
    }
    Drawable image = (soft ? previewCache : artworkCache).get(key);
    Drawable.ConstantState state = image.getConstantState();
    return state == null ? image : state.newDrawable(getResources()).mutate();
  }

  private void render() {
    if (homeWall != null && homeWall.isAttachedToWindow()) homeOffset = homeWall.getScrollY();
    homeWall = null;
    menuReturn = null;
    scale = getResources().getDisplayMetrics().widthPixels / 960f;
    root = new FrameLayout(this);
    root.setBackgroundColor(0xFF0B1019);
    setContentView(root);
    desktop = new FrameLayout(this);
    root.addView(desktop, new FrameLayout.LayoutParams(-1, -1));
    actionLayer = null;
    pendingRender = false;
    ambient = new AmbientBackgroundView(this);
    desktop.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
    clock = text(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()), 18, 0xFFB2BCCD);
    clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    clock.setFontFeatureSettings("'tnum' 1");
    put(clock, 800, 30, 74, 28);
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
      home(settings);
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
    moveHint = text("", 14, 0xFFD5DFEC);
    put(moveHint, 210, 36, 540, 30);
    homeWall = new GridWallView(this, scale, 156, wallListener());
    homeWall.showNames(prefs.showAppNames());
    put(homeWall, 42, 86, 876, 420);
    String previous = selectedId;
    homeWall.setEntries(orderedApps(), selectedId, false);
    if (previous.equals(selectedId)) homeWall.restoreFocus(selectedId, homeOffset);
    homeWall.upperFocus(settings.getId());
    if (apps.isEmpty()) {
      TextView empty = text(loading ? "正在读取应用…" : "暂无可启动的应用", 19, 0xFFC6D1E1);
      put(empty, 200, 225, 560, 45);
      settings.requestFocus();
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
      reload();
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
            this, scale, app.label, artwork(app, false), items, action -> closeMenu(false, action));
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
          else if (restore && target != null && target.isAttachedToWindow()) target.requestFocus();
          if (target instanceof AppCardView) ((AppCardView) target).contextSelected(false);
          if (action != null) action.run();
        });
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
    TextView heading = text("设置", 22, 0xFFECF0F6);
    heading.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    list.addView(heading, new LinearLayout.LayoutParams(-1, px(34)));
    Map<String, SettingsRowView> rows = new LinkedHashMap<>();
    LinearLayout desktop = settingsGroup(list, "桌面", false);
    String home = DeviceDiagnostics.homePackage(this).equals(getPackageName()) ? "Still" : "系统桌面";
    SettingsRowView first =
        settingsRow(desktop, rows, "默认桌面", R.drawable.settings_home, home, false);
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
    LinearLayout application = settingsGroup(list, "应用", true);
    SettingsRowView system =
        settingsRow(application, rows, "系统设置", R.drawable.settings_settings, "", false);
    system.setOnClickListener(v -> safeStart(new Intent(Settings.ACTION_SETTINGS)));
    SettingsRowView refresh =
        settingsRow(application, rows, "刷新应用列表", R.drawable.settings_refresh, "", false);
    refresh.setOnClickListener(
        v -> {
          reload();
          toast("应用列表已刷新");
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
    TextView caption = text(title, 14, 0xFF8F9EB3);
    caption.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, px(22));
    cp.topMargin = gap ? px(10) : 0;
    caption.setPadding(px(10), 0, 0, 0);
    list.addView(caption, cp);
    LinearLayout group = new LinearLayout(this);
    group.setOrientation(LinearLayout.VERTICAL);
    group.setClipChildren(false);
    group.setClipToPadding(false);
    GradientDrawable background =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {0xC1242E3D, 0xB11C2532});
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
      divider.setBackgroundColor(0x192E4058);
      LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, px(1));
      dp.leftMargin = px(68);
      dp.rightMargin = px(20);
      group.addView(divider, dp);
    }
    SettingsRowView row = new SettingsRowView(this, scale, title, icon, detail, toggle);
    row.setId(View.generateViewId());
    View.OnFocusChangeListener visual = row.getOnFocusChangeListener();
    row.setOnFocusChangeListener(
        (v, f) -> {
          visual.onFocusChange(v, f);
          if (f) selectedSetting = title;
        });
    group.addView(row, new LinearLayout.LayoutParams(-1, px(46)));
    rows.put(title, row);
    return row;
  }

  private void requestHome() {
    try {
      if (Build.VERSION.SDK_INT >= 29) {
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles != null
            && roles.isRoleAvailable(RoleManager.ROLE_HOME)
            && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
          startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 50);
          return;
        }
      }
      startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
    } catch (RuntimeException e) {
      toast("系统未开放桌面选择，请查看安装说明中的 ADB 检查步骤");
    }
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
    } else toast("已在首页；按 Home 返回默认桌面");
  }
}
