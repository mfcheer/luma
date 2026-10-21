package com.jianping.tv.ui;

import android.app.*;
import android.app.role.RoleManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

/** Native remote-first UI. System access and persistent state live outside this Activity. */
public final class MainActivity extends Activity {
  private Preferences prefs;
  private AppRepository repository;
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Handler handler = new Handler(Looper.getMainLooper());
  private List<AppEntry> apps = new ArrayList<>();
  private FrameLayout root;
  private TextView clock;
  private ImageView heroIcon;
  private FrameLayout heroFrame;
  private AmbientBackgroundView ambient;
  private final Map<String, Integer> paletteCache = new HashMap<>();
  private float scale = 1;
  private String page = "home", selectedId = "";
  private boolean loading = true;
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
        public void onReceive(Context c, Intent i) {
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
    }
    recordEntry(getIntent());
    IntentFilter f = new IntentFilter();
    f.addAction(Intent.ACTION_PACKAGE_ADDED);
    f.addAction(Intent.ACTION_PACKAGE_REMOVED);
    f.addAction(Intent.ACTION_PACKAGE_CHANGED);
    f.addDataScheme("package");
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(packageChanges, f, Context.RECEIVER_EXPORTED);
    else registerReceiver(packageChanges, f);
    render();
    reload();
    handler.post(ticker);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    recordEntry(intent);
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
    worker.execute(
        () -> {
          List<AppEntry> result = repository.load();
          runOnUiThread(
              () -> {
                if (isDestroyed()) return;
                apps = result;
                loading = false;
                if (!prefs.initialized()) {
                  List<String> first = new ArrayList<>();
                  for (AppEntry a : apps) {
                    if (first.size() == 5) break;
                    first.add(a.id);
                  }
                  prefs.initialize(first);
                }
                render();
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
    t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    t.setIncludeFontPadding(false);
    t.setFontFeatureSettings("'kern' 1");
    return t;
  }

  private void put(View v, float x, float y, float w, float h) {
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(px(w), px(h));
    p.leftMargin = px(x);
    p.topMargin = px(y);
    root.addView(v, p);
  }

  private GradientDrawable surface(boolean focus) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(focus ? 0xDD30445D : 0x70303D50);
    d.setCornerRadius(px(16));
    if (focus) d.setStroke(px(2), 0xFFF1F7FF);
    return d;
  }

  private void focusable(View v) {
    v.setFocusable(true);
    v.setClickable(true);
    v.setBackground(surface(false));
    v.setOnFocusChangeListener(
        (view, focused) -> {
          view.setBackground(surface(focused));
          view.animate()
              .scaleX(focused && !(view instanceof TextView) ? 1.10f : 1f)
              .scaleY(focused && !(view instanceof TextView) ? 1.10f : 1f)
              .setDuration(160)
              .start();
          view.setElevation(focused ? px(8) : 0);
        });
  }

  private TextView button(String title, Runnable action) {
    TextView b = text(title, 17, Color.WHITE);
    b.setPadding(px(18), px(8), px(18), px(8));
    focusable(b);
    b.setOnClickListener(v -> action.run());
    return b;
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
          GradientDrawable bg = new GradientDrawable();
          bg.setColor(focus ? 0x20FFFFFF : Color.TRANSPARENT);
          bg.setCornerRadius(px(18));
          button.setBackground(bg);
          glyph.setColorFilter(focus ? Color.WHITE : 0xFFBBC7D9);
          button
              .animate()
              .scaleX(focus ? 1.12f : 1)
              .scaleY(focus ? 1.12f : 1)
              .translationY(focus ? -px(1) : 0)
              .setDuration(180)
              .start();
        });
    button.setOnClickListener(v -> action.run());
    return button;
  }

  private android.graphics.drawable.Drawable appIcon(AppEntry app) {
    android.graphics.drawable.Drawable icon =
        app == null ? getDrawable(R.drawable.apps_icon) : app.icon;
    android.graphics.drawable.Drawable.ConstantState state = icon.getConstantState();
    return state == null ? icon : state.newDrawable(getResources()).mutate();
  }

  private void render() {
    scale = getResources().getDisplayMetrics().widthPixels / 960f;
    root = new FrameLayout(this);
    root.setBackgroundColor(0xFF0C1523);
    setContentView(root);
    ambient = new AmbientBackgroundView(this);
    root.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
    TextView brand = text("Luma", 23, 0xFFE3E9F3);
    brand.setGravity(Gravity.CENTER_VERTICAL);
    brand.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    brand.setLetterSpacing(.07f);
    put(brand, 44, 25, 160, 38);
    clock = text(new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date()), 18, 0xFFAAB6C9);
    clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    clock.setFontFeatureSettings("'tnum' 1");
    clock.setLetterSpacing(.035f);
    put(clock, 786, 30, 74, 28);
    View settings =
        headerAction(
            page.equals("home"),
            () -> {
              page = page.equals("home") ? "settings" : "home";
              render();
            });
    put(settings, 884, 23, 42, 42);
    settings.setId(R.id.settings_button);
    settings.setNextFocusDownId(R.id.first_app);
    if (page.equals("home")) home(settings);
    else if (page.equals("apps")) allApps();
    else settingsPage();
  }

  private List<AppEntry> favorites() {
    List<AppEntry> r = new ArrayList<>();
    for (String id : prefs.favorites())
      for (AppEntry a : apps)
        if (a.id.equals(id)) {
          r.add(a);
          break;
        }
    return r;
  }

  private void showHero(AppEntry a, String title, String hint) {
    heroIcon.setContentDescription(title);
    heroIcon.animate().cancel();
    heroIcon.setAlpha(.4f);
    heroIcon.setImageDrawable(appIcon(a));
    String key = a == null ? "all-apps" : a.id;
    Integer color = paletteCache.get(key);
    if (color == null) {
      color = IconPalette.accent(appIcon(a));
      paletteCache.put(key, color);
    }
    ambient.illuminate(color, true);
    heroFrame.setBackground(new FocusHaloDrawable(px(26), px(28), color));
    heroIcon.animate().alpha(1).setDuration(180).start();
  }

  private void home(View settings) {
    FrameLayout hero = new FrameLayout(this);
    heroFrame = hero;
    hero.setBackground(new FocusHaloDrawable(px(26), px(28)));
    put(hero, 388, 60, 184, 184);
    heroIcon = new ImageView(this);
    heroIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
    heroIcon.setBackground(surface(false));
    heroIcon.setClipToOutline(true);
    hero.addView(heroIcon, new FrameLayout.LayoutParams(px(128), px(128), Gravity.CENTER));

    List<AppEntry> fav = favorites();
    ScrollView scroll = new FavoritesScrollView(this, px(120));
    scroll.setPadding(0, px(12), 0, px(12));
    scroll.setClipToPadding(true);
    scroll.setClipChildren(false);
    scroll.setVerticalScrollBarEnabled(false);
    scroll.setVerticalFadingEdgeEnabled(true);
    scroll.setFadingEdgeLength(px(16));
    put(scroll, 48, 244, 864, 264);
    LinearLayout grid = new LinearLayout(this);
    grid.setOrientation(LinearLayout.VERTICAL);
    grid.setPadding(px(8), 0, px(8), 0);
    grid.setClipChildren(false);
    grid.setClipToPadding(false);
    scroll.addView(grid);
    List<View> cells = new ArrayList<>();
    View initial = null;
    int count = fav.size() + 1;
    for (int start = 0; start < count; start += 6) {
      LinearLayout row = new LinearLayout(this);
      row.setClipChildren(false);
      row.setPadding(0, px(4), 0, px(4));
      grid.addView(row, new LinearLayout.LayoutParams(-1, px(120)));
      for (int j = start; j < Math.min(start + 6, count); j++) {
        AppEntry app = j < fav.size() ? fav.get(j) : null;
        View cell =
            tile(
                app,
                app == null ? "全部应用" : app.label,
                () -> {
                  if (app == null) {
                    page = "apps";
                    render();
                  } else launch(app);
                });
        cell.setId(j == 0 ? R.id.first_app : View.generateViewId());
        if (app != null)
          cell.setOnLongClickListener(
              v -> {
                manage(app, true);
                return true;
              });
        row.addView(cell, new LinearLayout.LayoutParams(0, px(112), 1));
        cells.add(cell);
        bindHero(cell, app, app == null ? "全部应用" : app.label, "");
        if (initial == null || (app != null && app.id.equals(selectedId))) initial = cell;
      }
      for (int j = Math.min(start + 6, count); j < start + 6; j++)
        row.addView(new View(this), new LinearLayout.LayoutParams(0, px(112), 1));
    }
    for (int i = 0; i < cells.size(); i++) {
      View cell = cells.get(i);
      int rowStart = i / 6 * 6, rowEnd = Math.min(rowStart + 5, cells.size() - 1);
      cell.setNextFocusLeftId(cells.get(Math.max(rowStart, i - 1)).getId());
      cell.setNextFocusRightId(cells.get(Math.min(rowEnd, i + 1)).getId());
      cell.setNextFocusUpId(i >= 6 ? cells.get(i - 6).getId() : R.id.settings_button);
      cell.setNextFocusDownId(
          rowStart + 6 < cells.size()
              ? cells.get(Math.min(i + 6, cells.size() - 1)).getId()
              : cell.getId());
    }
    settings.setNextFocusLeftId(settings.getId());
    settings.setNextFocusRightId(settings.getId());
    initial.requestFocus();
  }

  private void bindHero(View tile, AppEntry a, String title, String hint) {
    View.OnFocusChangeListener original = tile.getOnFocusChangeListener();
    tile.setOnFocusChangeListener(
        (v, f) -> {
          original.onFocusChange(v, f);
          if (f) {
            if (a != null) selectedId = a.id;
            showHero(a, title, hint);
          }
        });
  }

  private View tile(AppEntry a, String label, Runnable action) {
    LinearLayout tile = new LinearLayout(this);
    tile.setOrientation(LinearLayout.VERTICAL);
    tile.setGravity(Gravity.CENTER);
    tile.setClipChildren(false);
    FrameLayout iconFrame = new FrameLayout(this);
    iconFrame.setClipChildren(false);
    ImageView icon = new ImageView(this);
    icon.setImageDrawable(appIcon(a));
    icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
    icon.setBackground(surface(false));
    icon.setClipToOutline(true);
    iconFrame.addView(icon, new FrameLayout.LayoutParams(px(56), px(56), Gravity.CENTER));
    tile.addView(iconFrame, new LinearLayout.LayoutParams(px(88), px(88)));
    TextView name = text(label, 14, 0xFFB5C3D6);
    name.setSingleLine(true);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    tile.addView(name, new LinearLayout.LayoutParams(-1, px(24)));
    tile.setFocusable(true);
    tile.setClickable(true);
    tile.setOnFocusChangeListener(
        (view, focused) -> {
          iconFrame.setBackground(focused ? new FocusHaloDrawable(px(14), px(17)) : null);
          iconFrame
              .animate()
              .scaleX(focused ? 1.10f : 1f)
              .scaleY(focused ? 1.10f : 1f)
              .setDuration(160)
              .start();
          name.setTextColor(focused ? Color.WHITE : 0xFFB5C3D6);
        });
    tile.setOnClickListener(v -> action.run());
    tile.setContentDescription(label);
    return tile;
  }

  private void launch(AppEntry a) {
    try {
      startActivity(repository.launchIntent(a));
    } catch (RuntimeException e) {
      toast("无法打开此应用，请刷新应用列表");
      reload();
    }
  }

  private void allApps() {
    TextView title = text("全部应用", 28, Color.WHITE);
    title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    put(title, 48, 82, 600, 48);
    ScrollView sc = new ScrollView(this);
    sc.setClipChildren(false);
    sc.setClipToPadding(false);
    sc.setVerticalFadingEdgeEnabled(true);
    sc.setFadingEdgeLength(px(12));
    put(sc, 32, 144, 896, 344);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setClipChildren(false);
    list.setClipToPadding(false);
    list.setPadding(px(16), px(12), px(16), px(12));
    sc.addView(list);
    View initial = null;
    List<String> ids = prefs.favorites();
    List<View> tiles = new ArrayList<>();
    for (int i = 0; i < apps.size(); i += 6) {
      LinearLayout row = new LinearLayout(this);
      row.setClipChildren(false);
      row.setClipToPadding(false);
      list.addView(row, new LinearLayout.LayoutParams(-1, px(146)));
      for (int j = i; j < Math.min(i + 6, apps.size()); j++) {
        AppEntry a = apps.get(j);
        View t = tile(a, (ids.contains(a.id) ? "已收藏 · " : "") + a.label, () -> launch(a));
        t.setId(j == 0 ? R.id.first_app : View.generateViewId());
        tiles.add(t);
        t.setOnLongClickListener(
            v -> {
              manage(a, false);
              return true;
            });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, px(112), 1);
        p.setMargins(px(6), px(6), px(6), px(6));
        row.addView(t, p);
        if (initial == null || a.id.equals(selectedId)) initial = t;
      }
    }
    for (int i = 0; i < tiles.size(); i++) {
      View t = tiles.get(i);
      int rowStart = i / 6 * 6;
      int rowEnd = Math.min(rowStart + 5, tiles.size() - 1);
      t.setNextFocusLeftId(tiles.get(Math.max(rowStart, i - 1)).getId());
      t.setNextFocusRightId(tiles.get(Math.min(rowEnd, i + 1)).getId());
      t.setNextFocusUpId(i >= 6 ? tiles.get(i - 6).getId() : R.id.settings_button);
      t.setNextFocusDownId(i + 6 < tiles.size() ? tiles.get(i + 6).getId() : t.getId());
    }
    if (apps.isEmpty()) {
      TextView empty = text(loading ? "正在读取应用…" : "暂无可启动的应用", 20, Color.WHITE);
      list.addView(empty);
    }
    put(text("长按确定收藏应用   ·   返回键回首页", 14, 0xFF9FB1C8), 180, 504, 600, 26);
    if (initial != null) initial.requestFocus();
  }

  private void manage(AppEntry a, boolean home) {
    List<String> ids = prefs.favorites();
    boolean saved = ids.contains(a.id);
    String[] options =
        saved ? new String[] {"移到最前", "向左移动", "向右移动", "移出常用"} : new String[] {"添加到常用"};
    new AlertDialog.Builder(this)
        .setTitle(a.label)
        .setItems(
            options,
            (d, n) -> {
              int position = ids.indexOf(a.id);
              if (!saved) ids.add(a.id);
              else if (n == 3) ids.remove(a.id);
              else if (n == 0) {
                ids.remove(a.id);
                ids.add(0, a.id);
              } else if (n == 1 && position > 0) Collections.swap(ids, position, position - 1);
              else if (n == 2 && position < ids.size() - 1)
                Collections.swap(ids, position, position + 1);
              prefs.saveFavorites(ids);
              selectedId = a.id;
              render();
            })
        .setNegativeButton("取消", null)
        .show();
  }

  private void settingsPage() {
    ScrollView sc = new ScrollView(this);
    sc.setClipChildren(false);
    sc.setClipToPadding(false);
    sc.setVerticalFadingEdgeEnabled(true);
    sc.setFadingEdgeLength(px(12));
    put(sc, 164, 98, 632, 400);
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setClipChildren(false);
    list.setClipToPadding(false);
    list.setPadding(px(12), px(12), px(12), px(20));
    sc.addView(list);
    TextView heading = text("桌面与应用", 17, 0xFF9AAEC6);
    heading.setGravity(Gravity.START);
    list.addView(heading, new LinearLayout.LayoutParams(-1, px(48)));
    View first = settingButton(list, "设置默认桌面", this::requestHome);
    settingButton(
        list,
        "开机启动实验：" + (prefs.bootEnabled() ? "已开启" : "已关闭"),
        () -> {
          prefs.bootEnabled(!prefs.bootEnabled());
          render();
        });
    settingButton(list, "打开系统设置", () -> safeStart(new Intent(Settings.ACTION_SETTINGS)));
    settingButton(list, "刷新应用列表", this::reload);
    settingButton(
        list,
        "设备与启动信息",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("设备与启动信息")
                .setMessage(DeviceDiagnostics.report(this) + "\n\n开机实验可能被系统拦截；待机唤醒与重新开机需要分别验证。")
                .setPositiveButton(
                    "复制报告",
                    (dialog, which) -> {
                      android.content.ClipboardManager cb =
                          (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                      cb.setPrimaryClip(
                          ClipData.newPlainText("Luma 设备报告", DeviceDiagnostics.report(this)));
                      toast("已复制报告");
                    })
                .setNegativeButton("关闭", null)
                .show());
    first.requestFocus();
  }

  private View settingButton(LinearLayout parent, String label, Runnable action) {
    int iconRes = R.drawable.settings_description, tint = 0xFF596882;
    if (label.startsWith("设置默认")) {
      iconRes = R.drawable.settings_home;
      tint = 0xFF4F6D92;
    } else if (label.startsWith("开机")) {
      iconRes = R.drawable.settings_power_settings_new;
      tint = 0xFF527C75;
    } else if (label.startsWith("打开系统")) {
      iconRes = R.drawable.settings_settings;
      tint = 0xFF697B94;
    } else if (label.startsWith("刷新")) {
      iconRes = R.drawable.settings_refresh;
      tint = 0xFF85735F;
    }
    LinearLayout row = new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(px(12), 0, px(16), 0);
    FrameLayout iconBox = new FrameLayout(this);
    GradientDrawable badge = new GradientDrawable();
    badge.setColor(tint);
    badge.setCornerRadius(px(10));
    iconBox.setBackground(badge);
    ImageView icon = new ImageView(this);
    icon.setImageResource(iconRes);
    iconBox.addView(icon, new FrameLayout.LayoutParams(px(23), px(23), Gravity.CENTER));
    row.addView(iconBox, new LinearLayout.LayoutParams(px(38), px(38)));
    TextView name = text(label, 17, Color.WHITE);
    name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    name.setSingleLine(true);
    name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(0, -1, 1);
    np.leftMargin = px(14);
    row.addView(name, np);
    row.setFocusable(true);
    row.setClickable(true);
    row.setContentDescription(label);
    row.setBackground(surface(false));
    row.setOnFocusChangeListener(
        (v, focused) -> {
          GradientDrawable bg = new GradientDrawable();
          bg.setColor(focused ? 0xFFF0F4FA : 0x88313F53);
          bg.setCornerRadius(px(15));
          row.setBackground(bg);
          name.setTextColor(focused ? 0xFF152235 : 0xFFF0F3FA);
          row.animate()
              .scaleX(focused ? 1.025f : 1)
              .scaleY(focused ? 1.025f : 1)
              .setDuration(170)
              .start();
        });
    row.setOnClickListener(v -> action.run());
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(56));
    lp.setMargins(0, px(4), 0, px(4));
    parent.addView(row, lp);
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
    if (!page.equals("home")) {
      page = "home";
      render();
    } else toast("已在首页；按 Home 返回默认桌面");
  }
}
