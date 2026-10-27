package com.jianping.qa;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Bundle;
import com.jianping.tv.data.*;
import com.jianping.tv.platform.*;
import com.jianping.tv.ui.SearchActivity;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Dedicated emulator only. No actual uninstall; restores local names and usage afterwards. */
public final class AppManagementProbe extends Instrumentation {
  private int checks;

  private void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  private static final class RequestActivity extends Activity {
    final Context context;
    Intent intent;
    int request;

    RequestActivity(Context context) {
      this.context = context;
    }

    @Override
    public PackageManager getPackageManager() {
      return context.getPackageManager();
    }

    @Override
    public String getPackageName() {
      return context.getPackageName();
    }

    @Override
    public void startActivityForResult(Intent intent, int request) {
      this.intent = intent;
      this.request = request;
    }
  }

  private void test(Context c) throws Exception {
    AppNames names = new AppNames(c);
    String pkg = "com.jianping.fixture.app16";
    PackageInfo info = c.getPackageManager().getPackageInfo(pkg, 0);
    AppEntry app =
        new AppEntry(
            new ComponentName(pkg, "com.jianping.fixture.MainActivity"),
            "测试应用 16",
            "version-1",
            info.firstInstallTime);
    names.removed(pkg);
    check(names.label(app).equals(app.label), "No custom name uses original label");
    c.getSharedPreferences("app_names_v1", 0)
        .edit()
        .putInt("name." + pkg, 5)
        .putString("revision", "malformed")
        .commit();
    check(
        names.label(app).equals(app.label) && names.revision() == 0,
        "Malformed custom metadata falls back to original name");
    check(names.save(app, "　家庭影院　"), "Name persisted");
    check(
        new AppNames(c).label(app).equals("家庭影院"),
        "Unicode whitespace trimmed and another store reads the saved name");
    check(app.label.equals("测试应用 16"), "Original metadata remains unchanged");
    check(
        names
            .label(new AppEntry(app.component, "Updated original", "v2", info.firstInstallTime))
            .equals("家庭影院"),
        "APK update/label change retains custom name");
    check(
        names
            .label(new AppEntry(app.component, app.label, "reinstall", info.firstInstallTime + 1))
            .equals(app.label),
        "Reinstall generation cannot inherit old name");
    check(
        names.label(new AppEntry(app.component, app.label, "old-cache")).equals("家庭影院"),
        "Backward-compatible catalog cache validates installed generation");
    for (String bad : Arrays.asList("", "　\n\t ", String.join("", Collections.nCopies(31, "字")))) {
      try {
        names.save(app, bad);
        throw new AssertionError("Invalid name saved");
      } catch (IllegalArgumentException expected) {
        checks++;
      }
      check(names.label(app).equals("家庭影院"), "Validation cannot overwrite existing name");
    }
    String emojis = String.join("", Collections.nCopies(30, "\uD83D\uDE00"));
    check(
        names.save(app, emojis) && names.label(app).equals(emojis),
        "Length counts Unicode code points");
    check(AppNames.clean("\u00A0原\n名\u3000").equals("原名"), "Controls and Unicode padding cleaned");
    check(
        names.save(app, app.label) && names.custom(app).isEmpty(),
        "Restore original removes customization");
    names.save(app, "家庭影院");
    AppAliases dictionary = AppAliases.read(c.getAssets().open("search-aliases.tsv"));
    AppSearch.ItemCache cache = new AppSearch.ItemCache();
    AppSearch.Item item =
        cache.get(app.id, app.label, pkg, app.fingerprint, dictionary, names.custom(app));
    AppSearch search = new AppSearch(Collections.singletonList(item));
    for (String query : Arrays.asList("家庭影院", "jiatingyingyuan", "jtyy", "测试应用16", "cs", "家"))
      check(
          search.find(query).equals(Collections.singletonList(app.id)),
          "New/original name query: " + query);
    AppSearch.Item tencent =
        new AppSearch.Item("tencent", "云视听极光", "com.ktcp.video", dictionary, "家庭影院");
    AppSearch branded = new AppSearch(Collections.singletonList(tencent));
    for (String query : Arrays.asList("家庭影院", "腾讯视频", "txsp", "云视听极光"))
      check(
          branded.find(query).equals(Collections.singletonList("tencent")),
          "Brand aliases remain available: " + query);
    AppSearch.Item renamed = cache.get(app.id, app.label, pkg, app.fingerprint, dictionary, "儿童电影");
    check(
        renamed != item && new AppSearch(Collections.singletonList(renamed)).find("家庭影院").isEmpty(),
        "Rename invalidates only affected prepared terms");
    check(search.find("家庭影院").size() == 1, "Existing search snapshot remains immutable");
    AppSearch.Item restored = cache.get(app.id, app.label, pkg, app.fingerprint, dictionary, "");
    check(
        new AppSearch(Collections.singletonList(restored)).find("家庭影院").isEmpty(),
        "Restore original clears custom search terms");
    List<String> order = Arrays.asList("a", "b", "c", "d");
    check(
        AppOrder.focusAfterRemoval(order, Arrays.asList("a", "c", "d"), "b").equals("c"),
        "Middle removal selects next app");
    check(
        AppOrder.focusAfterRemoval(order, Arrays.asList("a", "b", "c"), "d").equals("c"),
        "Last removal selects previous app");
    check(
        AppOrder.focusAfterRemoval(order, Collections.emptyList(), "d").isEmpty(),
        "Empty results have no stale app selection");
    check(AppOrder.focusAfterRemoval(order, order, "c").equals("c"), "Cancel retains selection");
    check(AppUninstall.allowed(c, app), "User-installed fixture can request system uninstall");
    PackageInfo system = c.getPackageManager().getPackageInfo("com.android.tv.settings", 0);
    AppEntry systemApp =
        new AppEntry(
            new ComponentName(system.packageName, "example.Settings"),
            "Settings",
            "system",
            system.firstInstallTime);
    check(!AppUninstall.allowed(c, systemApp), "System app is protected");
    check(
        names.save(systemApp, "设备设置") && names.label(systemApp).equals("设备设置"),
        "System apps support local renaming even when uninstall is protected");
    RequestActivity activity = new RequestActivity(c);
    AppUninstall.request(activity, app);
    check(
        Intent.ACTION_UNINSTALL_PACKAGE.equals(activity.intent.getAction())
            && activity.intent.getData().equals(Uri.parse("package:" + pkg))
            && activity.intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)
            && activity.request == AppUninstall.REQUEST,
        "Requests system confirmation for the exact package");
    try {
      AppUninstall.request(activity, systemApp);
      throw new AssertionError("System app requested uninstall");
    } catch (IllegalStateException expected) {
      checks++;
    }
    check(AppUninstall.installed(c, pkg), "Request alone never uninstalls or assumes success");
    Intent removed = new Intent(Intent.ACTION_PACKAGE_FULLY_REMOVED, Uri.parse("package:" + pkg));
    new AppRemovedReceiver()
        .onReceive(c, new Intent(removed).putExtra(Intent.EXTRA_REPLACING, true));
    check(names.label(app).equals("家庭影院"), "APK replacement retains custom name");
    new AppRemovedReceiver().onReceive(c, new Intent("example.UNRELATED", removed.getData()));
    check(names.label(app).equals("家庭影院"), "Unrelated broadcast ignored");
    new AppRemovedReceiver().onReceive(c, removed);
    check(names.custom(app).isEmpty(), "Confirmed uninstall clears custom name");
    // Reproduce onActivityResult(load true) followed by onResume(load false) before work completes.
    SearchActivity pendingSearch = new SearchActivity();
    Field workerField = SearchActivity.class.getDeclaredField("worker");
    workerField.setAccessible(true);
    ExecutorService worker = (ExecutorService) workerField.get(pendingSearch);
    CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
    worker.submit(
        () -> {
          entered.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });
    try {
      check(
          entered.await(2, TimeUnit.SECONDS),
          "Search worker blocked for deterministic lifecycle race");
      Method load = SearchActivity.class.getDeclaredMethod("load", boolean.class);
      load.setAccessible(true);
      load.invoke(pendingSearch, true);
      load.invoke(pendingSearch, false);
      Field dirty = SearchActivity.class.getDeclaredField("metadataDirty");
      dirty.setAccessible(true);
      check(
          dirty.getBoolean(pendingSearch),
          "Resume/name refresh preserves pending package discovery");
    } finally {
      worker.shutdownNow();
      release.countDown();
    }
  }

  @Override
  public void onStart() {
    Context c = getTargetContext();
    Map<String, Map<String, ?>> originals = new HashMap<>();
    for (String name : Arrays.asList("app_names_v1", "app_usage_v1"))
      originals.put(name, c.getSharedPreferences(name, 0).getAll());
    Bundle result = new Bundle();
    try {
      runOnMainSync(
          () -> {
            try {
              test(c);
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          });
      result.putString(
          "result",
          "PASS: "
              + checks
              + " name/search/installation-generation/system-uninstall/focus integration checks");
    } catch (Throwable failure) {
      result.putString("failure", android.util.Log.getStackTraceString(failure));
    } finally {
      for (String name : originals.keySet()) {
        SharedPreferences.Editor edit = c.getSharedPreferences(name, 0).edit().clear();
        for (Map.Entry<String, ?> entry : originals.get(name).entrySet()) {
          Object value = entry.getValue();
          if (value instanceof String) edit.putString(entry.getKey(), (String) value);
          else if (value instanceof Long) edit.putLong(entry.getKey(), (Long) value);
          else if (value instanceof Boolean) edit.putBoolean(entry.getKey(), (Boolean) value);
          else if (value instanceof Integer) edit.putInt(entry.getKey(), (Integer) value);
          else if (value instanceof Float) edit.putFloat(entry.getKey(), (Float) value);
        }
        edit.commit();
      }
      setInTouchMode(false);
    }
    finish(result.containsKey("failure") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
  }
}
