package com.jianping.tv.ui;

import android.app.Activity;
import android.content.*;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.jianping.tv.R;
import com.jianping.tv.data.*;
import com.jianping.tv.platform.AppLaunch;
import com.jianping.tv.platform.AppUninstall;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** Local search owns focus/IME state and hands explicit order/location actions back to home. */
public final class SearchActivity extends Activity {
  static final int REQUEST = 51;
  static final String HOME_TARGET = "com.jianping.tv.search.HOME_TARGET";
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final Map<String, AppEntry> entries = new HashMap<>();
  private UiPalette palette;
  private AppNames names;
  private long namesRevision;
  private AppRenameDialog renameDialog;
  private String pendingUninstall = "";
  private AppCatalogCache.Snapshot snapshot;
  private AppRepository repository;
  private AppSearch index;
  private Map<String, Double> usageSnapshot;
  private Map<String, String> usageEpochs;
  private int loadGeneration;
  private boolean metadataDirty;
  private Future<?> loadTask;
  private final Runnable catalogReload = () -> load(true);
  private final BroadcastReceiver packageChanges =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
              && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
          handler.removeCallbacks(catalogReload);
          handler.postDelayed(catalogReload, 250);
        }
      };
  private FrameLayout root, content, inputBox;
  private AppActionMenuView actionLayer;
  private View menuReturn;
  private AmbientBackgroundView ambient;
  private SearchInput input;
  private GridWallView results;
  private TextView message, clock, date;
  private View back, clear;
  private float unit;
  private int screenHeight, resultOffset, imeRequest;
  private String selected = "", query = "";
  private String homeTarget = "";
  private boolean imeOpen, keyboardRequested, autoKeyboard, restoreResult, ready;
  private final Runnable clockTick =
      new Runnable() {
        @Override
        public void run() {
          updateClock();
          handler.postDelayed(this, 1000);
        }
      };

  private int px(float value) {
    return Math.round(value * unit);
  }

  @Override
  public void onCreate(Bundle state) {
    names = new AppNames(this);
    namesRevision = names.revision();
    palette = UiPalette.from(new Preferences(this).lightTheme());
    setTheme(palette.light ? R.style.AppThemeLight : R.style.AppTheme);
    super.onCreate(state);
    unit = getResources().getDisplayMetrics().widthPixels / 960f;
    screenHeight = getResources().getDisplayMetrics().heightPixels;
    query = state == null ? "" : state.getString("query", "");
    selected = state == null ? "" : state.getString("selected", "");
    resultOffset = state == null ? 0 : state.getInt("offset", 0);
    restoreResult = state != null && state.getBoolean("resultFocus", false);
    homeTarget = state == null ? "" : state.getString("homeTarget", "");
    pendingUninstall = state == null ? "" : state.getString("uninstall", "");
    metadataDirty = state != null && state.getBoolean("metadataDirty", false);
    if (!homeTarget.isEmpty()) publishHomeTarget();
    if (state != null && state.containsKey("usagePackages")) {
      ArrayList<String> packages = state.getStringArrayList("usagePackages");
      double[] scores = state.getDoubleArray("usageScores");
      ArrayList<String> epochs = state.getStringArrayList("usageEpochs");
      if (packages != null
          && scores != null
          && epochs != null
          && packages.size() == scores.length
          && epochs.size() == scores.length) {
        Map<String, Double> restoredUsage = new HashMap<>();
        Map<String, String> restoredEpochs = new HashMap<>();
        for (int i = 0; i < scores.length; i++) {
          restoredUsage.put(packages.get(i), scores[i]);
          restoredEpochs.put(packages.get(i), epochs.get(i));
        }
        usageSnapshot = Collections.unmodifiableMap(restoredUsage);
        usageEpochs = Collections.unmodifiableMap(restoredEpochs);
      }
    }
    autoKeyboard = state == null || state.getBoolean("keyboard", false);
    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    repository = new AppRepository(this);
    IntentFilter filter = new IntentFilter();
    filter.addAction(Intent.ACTION_PACKAGE_ADDED);
    filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
    filter.addDataScheme("package");
    if (Build.VERSION.SDK_INT >= 33)
      registerReceiver(packageChanges, filter, Context.RECEIVER_EXPORTED);
    else registerReceiver(packageChanges, filter);
    build();
    load(!pendingUninstall.isEmpty());
  }

  private void build() {
    root = new FrameLayout(this);
    root.setBackgroundColor(palette.background);
    root.setClipChildren(false);
    setContentView(root);
    content = new FrameLayout(this);
    root.addView(content, new FrameLayout.LayoutParams(-1, -1));
    ambient = new AmbientBackgroundView(this, palette);
    content.addView(ambient, new FrameLayout.LayoutParams(-1, -1));
    back = icon(R.drawable.nav_back, "返回首页", this::leave);
    place(back, 42, 18, 34, 42);
    place(text("搜索应用", 18, palette.text), 86, 18, 160, 42);
    clock = text("", 18, palette.secondary);
    clock.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    date = text("", 12, palette.secondary);
    date.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    place(clock, 698, 20, 176, 25);
    place(date, 698, 46, 176, 18);
    updateClock();
    inputBox = new FrameLayout(this);
    inputBox.setBackground(round(palette.light ? 0xFFE8EBEF : 0xFF202B3A, 10));
    place(inputBox, 78, 86, 804, 48);
    ImageView search = new ImageView(this);
    search.setImageResource(R.drawable.nav_search);
    search.setColorFilter(palette.secondary);
    search.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(px(24), px(24));
    sp.leftMargin = px(24);
    sp.gravity = Gravity.CENTER_VERTICAL;
    inputBox.addView(search, sp);
    input = new SearchInput();
    input.setId(View.generateViewId());
    input.setContentDescription("搜索应用名称或拼音首字母");
    input.setSingleLine(true);
    input.setSelectAllOnFocus(false);
    input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(23));
    input.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    input.setTextColor(palette.text);
    input.setHintTextColor(palette.muted);
    input.setHint("输入名称或拼音首字母");
    input.setPadding(0, 0, 0, 0);
    input.setBackgroundColor(Color.TRANSPARENT);
    input.setInputType(
        android.text.InputType.TYPE_CLASS_TEXT
            | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    input.setImeOptions(
        EditorInfo.IME_ACTION_SEARCH
            | EditorInfo.IME_FLAG_NO_EXTRACT_UI
            | EditorInfo.IME_FLAG_NO_FULLSCREEN);
    input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(80)});
    FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(-1, -1);
    ip.leftMargin = px(65);
    ip.rightMargin = px(52);
    inputBox.addView(input, ip);
    clear =
        icon(
            R.drawable.nav_close,
            "清空搜索",
            () -> {
              input.setText("");
              input.requestFocus();
              showKeyboard();
            });
    FrameLayout.LayoutParams cp =
        new FrameLayout.LayoutParams(px(42), px(42), Gravity.END | Gravity.CENTER_VERTICAL);
    cp.rightMargin = px(5);
    inputBox.addView(clear, cp);
    clear.setVisibility(View.GONE);
    message = text("正在准备搜索…", 14, palette.secondary);
    place(message, 78, 150, 804, 26);
    results =
        new GridWallView(
            this,
            unit * 1.2f,
            135,
            palette,
            new GridWallView.Listener() {
              public String label(AppEntry app) {
                return names.label(app);
              }

              public Drawable artwork(AppEntry app) {
                return image(app, false);
              }

              public Drawable preview(AppEntry app) {
                return image(app, true);
              }

              public int accent(AppEntry app) {
                return snapshot.visuals.get(app.id).accent;
              }

              public void focused(AppEntry app, String key) {
                selected = key;
                ambient.illuminate(snapshot.visuals.get(key).accent, true);
              }

              public void anchor(float x, float y, boolean animated) {
                ambient.follow(x, y, animated);
              }

              public void activate(AppEntry app) {
                resultOffset = results.getScrollY();
                restoreResult = true;
                hideKeyboard();
                try {
                  AppLaunch.open(SearchActivity.this, repository, app);
                } catch (RuntimeException e) {
                  Toast.makeText(SearchActivity.this, "无法打开此应用，请返回首页刷新应用列表", Toast.LENGTH_LONG)
                      .show();
                }
              }

              public void hold(AppEntry app, View source) {
                manage(app, source);
              }

              public boolean key(int code, KeyEvent event) {
                return false;
              }
            });
    results.showNames(new Preferences(this).showAppNames());
    results.focusBaselineLift(px(2.5f));
    place(results, 54, 188, 852, 322);
    results.upperFocus(input.getId());
    results.setVisibility(View.GONE);
    input.setNextFocusUpId(back.getId());
    input.setNextFocusRightId(clear.getId());
    clear.setNextFocusLeftId(input.getId());
    clear.setNextFocusUpId(back.getId());
    back.setNextFocusDownId(input.getId());
    back.setNextFocusLeftId(back.getId());
    back.setNextFocusRightId(input.getId());
    input.setText(query);
    input.setSelection(input.length());
    input.setOnClickListener(v -> showKeyboard());
    input.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_SEARCH
              || action == EditorInfo.IME_ACTION_DONE
              || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
            if (event == null || event.getAction() == KeyEvent.ACTION_UP) hideAndFocusResults();
            return true;
          }
          return false;
        });
    input.setOnKeyListener(
        (v, code, event) -> {
          if (code == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
            hideAndFocusResults();
            return true;
          }
          return false;
        });
    input.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            query = s.toString();
            clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
            updateResults(false);
          }

          public void afterTextChanged(Editable s) {}
        });
    clear.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
    input.requestFocus();
    root.getViewTreeObserver()
        .addOnGlobalLayoutListener(
            () -> {
              Rect visible = new Rect();
              root.getWindowVisibleDisplayFrame(visible);
              int[] position = new int[2];
              root.getLocationOnScreen(position);
              int available = Math.min(root.getHeight(), visible.bottom - position[1]);
              imeOpen = screenHeight - available > px(70);
              if (Build.VERSION.SDK_INT >= 30 && root.getRootWindowInsets() != null)
                imeOpen = root.getRootWindowInsets().isVisible(WindowInsets.Type.ime());
              boolean editing = (imeOpen || keyboardRequested) && !results.hasFocus();
              // Floating TV IMEs need not resize the window or report an occlusion inset.
              if (editing && available > screenHeight * .8f)
                available = Math.min(available, Math.round(screenHeight * .555f));
              boolean compact = editing && available < px(350);
              // The browsing viewport starts below the count, including lift/shadow headroom.
              int top = px(compact ? 142 : 188);
              int height = Math.max(0, available - top);
              int bottomPadding = editing ? 0 : px(14.4f);
              if (results.getPaddingBottom() != bottomPadding)
                results.setPadding(
                    results.getPaddingLeft(),
                    results.getPaddingTop(),
                    results.getPaddingRight(),
                    bottomPadding);
              results.edgeTreatment(!editing);
              FrameLayout.LayoutParams mp = (FrameLayout.LayoutParams) message.getLayoutParams();
              if (mp.topMargin != px(compact ? 132 : 150)) {
                mp.topMargin = px(compact ? 132 : 150);
                message.setLayoutParams(mp);
              }
              FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) results.getLayoutParams();
              if (p.height != height || p.topMargin != top) {
                p.height = height;
                p.topMargin = top;
                results.setLayoutParams(p);
              }
            });
  }

  private void load(boolean refreshMetadata) {
    // A name refresh/resume must not downgrade an unfinished package reconciliation.
    metadataDirty |= refreshMetadata;
    final boolean discover = metadataDirty;
    final int generation = ++loadGeneration;
    if (loadTask != null) loadTask.cancel(true);
    loadTask =
        worker.submit(
            () -> {
              try {
                AppCatalogCache cache = new AppCatalogCache(this);
                AppCatalogCache.Snapshot loaded = cache.memory();
                if (loaded == null) loaded = cache.read();
                // Discovery only for a package event or missing/corrupt cache, never on app return.
                if (discover || loaded == null) {
                  List<AppEntry> apps = repository.load();
                  if (Thread.currentThread().isInterrupted()) return;
                  Map<String, AppCatalogCache.Visual> visuals = new HashMap<>();
                  for (AppEntry app : apps) {
                    if (Thread.currentThread().isInterrupted()) return;
                    AppCatalogCache.Visual visual =
                        loaded == null ? null : loaded.visuals.get(app.id);
                    if (visual != null && !visual.fingerprint.equals(app.fingerprint))
                      visual = null;
                    if (visual == null) visual = cache.readVisual(app);
                    if (visual == null) {
                      AppRepository.Artwork raw = repository.artwork(app);
                      int color = IconPalette.accent(raw.icon);
                      Drawable art;
                      try {
                        art = IconArtwork.landscape(getResources(), raw.icon, raw.banner, color);
                      } catch (RuntimeException e) {
                        art = IconArtwork.fallback(getResources(), raw.icon);
                      }
                      visual =
                          new AppCatalogCache.Visual(
                              app.fingerprint,
                              color,
                              art,
                              IconArtwork.preview(getResources(), art));
                    }
                    visuals.put(app.id, visual);
                  }
                  loaded = new AppCatalogCache.Snapshot(apps, visuals);
                }
                Map<String, AppEntry> byId = new HashMap<>();
                List<String> available = new ArrayList<>();
                for (AppEntry app : loaded.apps) {
                  byId.put(app.id, app);
                  available.add(app.id);
                }
                List<AppEntry> ordered = new ArrayList<>();
                for (String id : AppOrder.reconcile(new Preferences(this).appOrder(), available))
                  ordered.add(byId.get(id));
                AppUsageStore.Snapshot currentUsage = new AppUsageStore(this).session();
                Map<String, Double> weights = new HashMap<>();
                Map<String, String> epochs = new HashMap<>();
                if (usageSnapshot == null) {
                  weights.putAll(currentUsage.weights);
                  epochs.putAll(currentUsage.epochs);
                } else {
                  // Keep the session frozen, except history erased by an uninstall/reinstall.
                  for (String pkg : usageSnapshot.keySet()) {
                    String epoch = usageEpochs.get(pkg);
                    if (epoch != null && epoch.equals(currentUsage.epochs.get(pkg))) {
                      weights.put(pkg, usageSnapshot.get(pkg));
                      epochs.put(pkg, epoch);
                    }
                  }
                }
                Map<String, Double> frozenWeights = Collections.unmodifiableMap(weights);
                Map<String, String> frozenEpochs = Collections.unmodifiableMap(epochs);
                AppCatalogCache.Snapshot restored = loaded;
                AppSearch prepared = SearchIndex.prepare(this, ordered, weights);
                runOnUiThread(
                    () -> {
                      if (isDestroyed() || isFinishing() || generation != loadGeneration) return;
                      if (discover) metadataDirty = false;
                      boolean keepFocus = results.hasFocus() || restoreResult;
                      restoreResult =
                          false; // Consume saved focus once; later package events keep editing
                      // focus.
                      int offset = ready ? results.getScrollY() : resultOffset;
                      boolean wasReady = ready;
                      List<String> previousMatches =
                          index == null ? Collections.emptyList() : index.find(query);
                      selected =
                          AppOrder.focusAfterRemoval(
                              previousMatches, prepared.find(query), selected);
                      snapshot = restored;
                      cache.remember(restored);
                      usageSnapshot = frozenWeights;
                      usageEpochs = frozenEpochs;
                      index = prepared;
                      namesRevision = names.revision();
                      entries.clear();
                      entries.putAll(byId);
                      if (!homeTarget.isEmpty() && !byId.containsKey(homeTarget)) {
                        homeTarget = "";
                        setResult(RESULT_CANCELED);
                      }
                      ready = true;
                      updateResults(keepFocus);
                      if (keepFocus && results.getVisibility() == View.VISIBLE)
                        results.post(() -> results.restoreFocus(selected, offset));
                      else if (wasReady) results.scrollTo(0, offset);
                      if (keepFocus && results.getVisibility() != View.VISIBLE)
                        input.requestFocus();
                    });
              } catch (RuntimeException e) {
                android.util.Log.w("StillSearch", "Unable to restore search catalog", e);
                runOnUiThread(
                    () -> {
                      if (!isDestroyed() && !isFinishing() && generation == loadGeneration)
                        message.setText("搜索暂不可用，请返回首页重试");
                    });
              }
            });
  }

  private void updateResults(boolean takeFocus) {
    if (!ready) return;
    List<AppEntry> matches = new ArrayList<>();
    for (String id : index.find(query)) matches.add(entries.get(id));
    message.setText(
        AppSearch.normalize(query).isEmpty()
            ? "输入名称或拼音首字母，例如 jkj"
            : (matches.isEmpty() ? "未找到应用，请换个关键词" : "找到 " + matches.size() + " 个应用"));
    results.setVisibility(matches.isEmpty() ? View.GONE : View.VISIBLE);
    results.setEntries(matches, selected, false, takeFocus);
    if (!takeFocus) results.scrollTo(0, 0);
    int down = matches.isEmpty() ? input.getId() : results.firstId();
    input.setNextFocusDownId(down);
    clear.setNextFocusDownId(down);
  }

  private Drawable image(AppEntry app, boolean soft) {
    AppCatalogCache.Visual visual = snapshot.visuals.get(app.id);
    Drawable drawable = soft ? visual.preview : visual.card;
    Drawable.ConstantState state = drawable.getConstantState();
    return state == null ? drawable : state.newDrawable(getResources()).mutate();
  }

  private void manage(AppEntry app, View source) {
    if (actionLayer != null) return;
    hideKeyboard();
    resultOffset = results.getScrollY();
    menuReturn = source;
    if (source instanceof AppCardView) ((AppCardView) source).contextSelected(true);
    content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    List<AppActionMenuView.Item> items = new ArrayList<>();
    items.add(
        new AppActionMenuView.Item(
            "移到最前", R.drawable.menu_move_first, false, () -> moveToFront(app)));
    items.add(
        new AppActionMenuView.Item(
            "移到最后", R.drawable.menu_move_last, false, () -> moveToEdge(app, false)));
    items.add(
        new AppActionMenuView.Item(
            "在首页定位",
            R.drawable.settings_home,
            false,
            () -> {
              homeTarget = app.id;
              publishHomeTarget();
              leave();
            }));
    items.add(new AppActionMenuView.Item("重命名", R.drawable.menu_rename, false, () -> rename(app)));
    items.add(
        new AppActionMenuView.Item(
            "应用信息",
            R.drawable.menu_info,
            false,
            () -> {
              resultOffset = results.getScrollY();
              restoreResult = true;
              try {
                startActivity(
                    new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + app.component.getPackageName())));
              } catch (RuntimeException error) {
                Toast.makeText(this, "当前系统不支持此入口", Toast.LENGTH_LONG).show();
              }
            }));
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
            unit,
            names.label(app),
            names.custom(app).isEmpty() ? "" : "原名称：" + app.label,
            image(app, false),
            items,
            action -> closeMenu(action),
            palette);
    root.addView(actionLayer, new FrameLayout.LayoutParams(-1, -1));
    actionLayer.reveal();
  }

  private void rename(AppEntry app) {
    autoKeyboard = false;
    renameDialog =
        new AppRenameDialog(
            this,
            app,
            names,
            palette,
            unit,
            () -> {
              resultOffset = results.getScrollY();
              restoreResult = true;
              load(false);
              Toast.makeText(this, "名称已保存", Toast.LENGTH_SHORT).show();
            });
    renameDialog.setOnDismissListener(d -> renameDialog = null);
    renameDialog.show();
  }

  private void uninstall(AppEntry app) {
    autoKeyboard = false;
    resultOffset = results.getScrollY();
    restoreResult = true;
    pendingUninstall = app.component.getPackageName();
    try {
      AppUninstall.request(this, app);
    } catch (RuntimeException e) {
      pendingUninstall = "";
      restoreResult = false;
      Toast.makeText(this, "当前系统无法卸载此应用，可在应用信息中查看", Toast.LENGTH_LONG).show();
    }
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (request != AppUninstall.REQUEST) return;
    if (!pendingUninstall.isEmpty() && !AppUninstall.installed(this, pendingUninstall)) {
      names.removed(pendingUninstall);
      new AppUsageStore(this).removed(pendingUninstall);
      load(true);
    }
    pendingUninstall = "";
  }

  private void closeMenu(Runnable action) {
    if (actionLayer == null) return;
    AppActionMenuView old = actionLayer;
    View target = menuReturn;
    old.dismiss(
        () -> {
          if (actionLayer != old || isDestroyed()) return;
          root.removeView(old);
          actionLayer = null;
          menuReturn = null;
          content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
          if (target != null && target.isAttachedToWindow()) target.requestFocus();
          else if (results.getVisibility() == View.VISIBLE) results.focusKey(selected, true);
          else input.requestFocus();
          if (target instanceof AppCardView) ((AppCardView) target).contextSelected(false);
          if (action != null) action.run();
        });
  }

  private void moveToFront(AppEntry app) {
    moveToEdge(app, true);
  }

  private void moveToEdge(AppEntry app, boolean first) {
    Preferences prefs = new Preferences(this);
    List<String> order = prefs.appOrder();
    if (!order.contains(app.id)) {
      Toast.makeText(this, "应用已变更，请返回首页刷新列表", Toast.LENGTH_LONG).show();
      return;
    }
    if (order.get(first ? 0 : order.size() - 1).equals(app.id)) {
      Toast.makeText(this, first ? "已在首页最前" : "已在首页最后", Toast.LENGTH_SHORT).show();
      return;
    }
    // Preserve entries installed while search was open, even if absent from its snapshot.
    prefs.saveAppOrder(
        first ? AppOrder.moveToFront(order, app.id) : AppOrder.moveToEnd(order, app.id));
    homeTarget = app.id;
    publishHomeTarget();
    List<String> available = new ArrayList<>();
    for (AppEntry entry : snapshot.apps) available.add(entry.id);
    List<AppEntry> ordered = new ArrayList<>();
    for (String id : AppOrder.reconcile(prefs.appOrder(), available)) ordered.add(entries.get(id));
    // Explicit home reordering reuses prepared terms and the same session's frozen usage.
    index = SearchIndex.prepare(this, ordered, usageSnapshot);
    updateResults(true);
    Toast.makeText(this, first ? "已移到首页最前" : "已移到首页最后", Toast.LENGTH_SHORT).show();
  }

  private void publishHomeTarget() {
    setResult(RESULT_OK, new Intent().putExtra(HOME_TARGET, homeTarget));
  }

  private void showKeyboard() {
    if (isFinishing() || !hasWindowFocus()) return;
    input.requestFocus();
    input.post(
        () -> {
          if (!isFinishing() && hasWindowFocus()) {
            final int request = ++imeRequest;
            keyboardRequested =
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                    .showSoftInput(
                        input,
                        InputMethodManager.SHOW_IMPLICIT,
                        new ResultReceiver(handler) {
                          @Override
                          protected void onReceiveResult(int code, Bundle data) {
                            if (request != imeRequest) return;
                            keyboardRequested =
                                code == InputMethodManager.RESULT_SHOWN
                                    || code == InputMethodManager.RESULT_UNCHANGED_SHOWN;
                            root.requestLayout();
                          }
                        });
            root.requestLayout();
          }
        });
  }

  private void hideKeyboard() {
    imeRequest++;
    keyboardRequested = false;
    root.requestLayout();
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(input.getWindowToken(), 0);
  }

  private void hideAndFocusResults() {
    autoKeyboard = false;
    hideKeyboard();
    if (results.getVisibility() == View.VISIBLE) results.focusKey(selected, true);
    else input.requestFocus();
  }

  private void leave() {
    hideKeyboard();
    finish();
  }

  @Override
  public void onBackPressed() {
    if (actionLayer != null) closeMenu(null);
    else if (imeOpen || keyboardRequested) hideAndFocusResults();
    else leave();
  }

  private final class SearchInput extends EditText {
    SearchInput() {
      super(SearchActivity.this);
    }

    @Override
    public boolean onKeyPreIme(int code, KeyEvent event) {
      if (code == KeyEvent.KEYCODE_BACK && (imeOpen || keyboardRequested)) {
        if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) hideAndFocusResults();
        return true;
      }
      return super.onKeyPreIme(code, event);
    }
  }

  @Override
  public void onWindowFocusChanged(boolean focus) {
    super.onWindowFocusChanged(focus);
    if (focus && autoKeyboard) {
      autoKeyboard = false;
      input.postDelayed(this::showKeyboard, 180);
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    if (usageSnapshot != null) {
      ArrayList<String> packages = new ArrayList<>(usageSnapshot.keySet());
      double[] scores = new double[packages.size()];
      ArrayList<String> epochs = new ArrayList<>();
      for (int i = 0; i < scores.length; i++) {
        scores[i] = usageSnapshot.get(packages.get(i));
        epochs.add(usageEpochs.get(packages.get(i)));
      }
      state.putStringArrayList("usageEpochs", epochs);
      state.putStringArrayList("usagePackages", packages);
      state.putDoubleArray("usageScores", scores);
    }
    state.putString("query", query);
    state.putString("selected", selected);
    state.putInt("offset", results.getScrollY());
    state.putBoolean("resultFocus", results.hasFocus() || restoreResult || actionLayer != null);
    state.putString("homeTarget", homeTarget);
    state.putString("uninstall", pendingUninstall);
    state.putBoolean("metadataDirty", metadataDirty);
    state.putBoolean("keyboard", imeOpen);
    super.onSaveInstanceState(state);
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
    handler.post(clockTick);
    if (ready && namesRevision != names.revision()) load(false);
    if (ready && restoreResult) {
      results.post(() -> results.restoreFocus(selected, resultOffset));
      restoreResult = false;
    }
  }

  @Override
  protected void onPause() {
    handler.removeCallbacks(clockTick);
    hideKeyboard();
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    if (renameDialog != null) {
      renameDialog.setOnDismissListener(null);
      renameDialog.dismiss();
      renameDialog = null;
    }
    handler.removeCallbacksAndMessages(null);
    unregisterReceiver(packageChanges);
    worker.shutdownNow();
    super.onDestroy();
  }

  private void updateClock() {
    Date now = new Date();
    String time = new SimpleDateFormat("HH:mm", Locale.CHINA).format(now);
    String day = new SimpleDateFormat("yyyy年M月d日  EEEE", Locale.CHINA).format(now);
    if (!time.contentEquals(clock.getText())) clock.setText(time);
    if (!day.contentEquals(date.getText())) date.setText(day);
  }

  private TextView text(String value, int size, int color) {
    TextView text = new TextView(this);
    text.setText(value);
    text.setTextColor(color);
    text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
    text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    text.setIncludeFontPadding(false);
    text.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    return text;
  }

  private View icon(int resource, String label, Runnable action) {
    FrameLayout button = new FrameLayout(this);
    button.setId(View.generateViewId());
    button.setFocusable(true);
    button.setClickable(true);
    button.setContentDescription(label);
    ImageView glyph = new ImageView(this);
    glyph.setImageResource(resource);
    glyph.setColorFilter(palette.icon);
    button.addView(glyph, new FrameLayout.LayoutParams(px(23), px(23), Gravity.CENTER));
    button.setOnFocusChangeListener(
        (v, focus) ->
            button.setBackground(round(focus ? palette.headerFocus : Color.TRANSPARENT, 18)));
    button.setOnClickListener(v -> action.run());
    return button;
  }

  private GradientDrawable round(int color, int radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(px(radius));
    return drawable;
  }

  private void place(View view, int x, int y, int w, int h) {
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(px(w), px(h));
    p.leftMargin = px(x);
    p.topMargin = px(y);
    content.addView(view, p);
  }
}
