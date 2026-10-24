package com.jianping.qa;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import com.jianping.tv.data.*;
import com.jianping.tv.platform.*;
import java.util.*;

/** Emulator-only integration probe, not included in Still. */
public final class SearchUsageProbe extends Instrumentation {
  private int checks;

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    start();
  }

  private void check(boolean value, String message) {
    checks++;
    if (!value) throw new AssertionError(message);
  }

  public static class FakeActivity extends Activity {
    private final Context context;
    private final boolean reject;

    FakeActivity(Context context, boolean reject) {
      this.context = context;
      this.reject = reject;
    }

    @Override
    public Context getApplicationContext() {
      return context.getApplicationContext();
    }

    @Override
    public void startActivity(Intent intent) {
      if (reject) throw new ActivityNotFoundException("QA deliberately rejected launch");
    }
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    Context context = getTargetContext();
    SharedPreferences prefs = context.getSharedPreferences("app_usage_v1", Context.MODE_PRIVATE);
    Map<String, ?> original = prefs.getAll();
    try {
      runOnMainSync(
          () -> {
            prefs.edit().clear().commit();
            AppUsageStore store = new AppUsageStore(context);
            AppEntry app =
                new AppEntry(
                    new ComponentName("com.jianping.fixture.app01", "example.Main"), "QA", "v1");
            AppRepository repo = new AppRepository(context);
            try {
              AppLaunch.open(new FakeActivity(context, true), repo, app);
              throw new AssertionError("Rejected launch did not throw");
            } catch (ActivityNotFoundException expected) {
            }
            check(store.snapshot().isEmpty(), "Rejected launch must not count");
            AppLaunch.open(new FakeActivity(context, false), repo, app);
            double first = store.snapshot().get(app.component.getPackageName());
            check(first > .99 && first <= 1, "One launch exactly one contribution");
            Map<String, Double> frozen = store.snapshot();
            AppLaunch.open(new FakeActivity(context, false), repo, app);
            check(
                store.snapshot().get(app.component.getPackageName()) > 1.99, "Second launch once");
            check(
                frozen.get(app.component.getPackageName()) <= 1,
                "Snapshot independent from later launches");
            check(
                new AppUsageStore(context).snapshot().get(app.component.getPackageName()) > 1.99,
                "Another store reads same aggregate");
            Intent removed =
                new Intent(
                    Intent.ACTION_PACKAGE_FULLY_REMOVED,
                    android.net.Uri.parse("package:" + app.component.getPackageName()));
            new AppRemovedReceiver()
                .onReceive(context, new Intent(removed).putExtra(Intent.EXTRA_REPLACING, true));
            check(!store.snapshot().isEmpty(), "APK replacement preserves aggregate");
            new AppRemovedReceiver()
                .onReceive(context, new Intent("example.UNRELATED", removed.getData()));
            check(!store.snapshot().isEmpty(), "Unrelated broadcast ignored");
            String previousEpoch = store.session().epochs.get(app.component.getPackageName());
            new AppRemovedReceiver().onReceive(context, removed);
            check(store.snapshot().isEmpty(), "True uninstall clears aggregate");
            store.opened(app.component.getPackageName());
            check(
                !previousEpoch.equals(store.session().epochs.get(app.component.getPackageName())),
                "Reinstalled history has a new generation, including across saved search state");
            store.removed(app.component.getPackageName());
            prefs
                .edit()
                .putString("score.example.bad", "wrong-type")
                .putLong("time.example.bad", System.currentTimeMillis())
                .commit();
            check(store.snapshot().isEmpty(), "Malformed preference values ignored");
            store.opened("example.bad");
            check(
                store.snapshot().get("example.bad") > .99,
                "Malformed entry self-heals on a launch");
            // Aggregate storage errors cannot turn a successful launch into an error shown by the
            // UI.
            AppLaunch.open(
                new FakeActivity(context, false) {
                  @Override
                  public Context getApplicationContext() {
                    throw new IllegalStateException("QA storage unavailable");
                  }
                },
                repo,
                app);
            check(true, "Launch survives unavailable usage storage");
            result.putString(
                "result",
                "PASS: "
                    + checks
                    + " Android integration checks: accepted/rejected launch, single counting,"
                    + " shared persistence, frozen snapshot, replace/uninstall and corrupt"
                    + " storage");
          });
    } catch (Throwable failure) {
      result.putString("failure", android.util.Log.getStackTraceString(failure));
    } finally {
      SharedPreferences.Editor edit = prefs.edit().clear();
      for (Map.Entry<String, ?> e : original.entrySet()) {
        Object v = e.getValue();
        if (v instanceof Long) edit.putLong(e.getKey(), (Long) v);
        else if (v instanceof String) edit.putString(e.getKey(), (String) v);
        else if (v instanceof Boolean) edit.putBoolean(e.getKey(), (Boolean) v);
        else if (v instanceof Integer) edit.putInt(e.getKey(), (Integer) v);
        else if (v instanceof Float) edit.putFloat(e.getKey(), (Float) v);
      }
      edit.commit();
      setInTouchMode(false);
    }
    finish(result.containsKey("failure") ? Activity.RESULT_CANCELED : Activity.RESULT_OK, result);
  }
}
