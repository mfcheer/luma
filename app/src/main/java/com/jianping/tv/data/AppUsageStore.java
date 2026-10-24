package com.jianping.tv.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Per-package aggregates, separate from presentation preferences and icon caches. */
public final class AppUsageStore {
  private static final Object LOCK = new Object();
  private static final String SCORE = "score.", TIME = "time.", EPOCH = "epoch.";
  private final SharedPreferences prefs;

  public AppUsageStore(Context context) {
    prefs =
        context.getApplicationContext().getSharedPreferences("app_usage_v1", Context.MODE_PRIVATE);
  }

  public static final class Snapshot {
    public final Map<String, Double> weights;
    public final Map<String, String> epochs;

    private Snapshot(Map<String, Double> weights, Map<String, String> epochs) {
      this.weights = Collections.unmodifiableMap(weights);
      this.epochs = Collections.unmodifiableMap(epochs);
    }
  }

  public void opened(String packageName) {
    synchronized (LOCK) {
      long now = System.currentTimeMillis();
      Map<String, ?> values = prefs.getAll();
      long updated = longValue(values.get(TIME + packageName));
      double old = Double.longBitsToDouble(longValue(values.get(SCORE + packageName)));
      double next = UsageHistory.afterLaunch(old, updated, now);
      // Clock rollback must not age backwards or magnify the previous score.
      Object epoch = values.get(EPOCH + packageName);
      prefs
          .edit()
          .putLong(SCORE + packageName, Double.doubleToRawLongBits(next))
          .putLong(TIME + packageName, Math.max(now, updated))
          .putString(
              EPOCH + packageName,
              epoch instanceof String ? (String) epoch : UUID.randomUUID().toString())
          .apply();
    }
  }

  public Map<String, Double> snapshot() {
    return session().weights;
  }

  public Snapshot session() {
    synchronized (LOCK) {
      long now = System.currentTimeMillis();
      Map<String, ?> values = prefs.getAll();
      Map<String, Double> result = new HashMap<>();
      Map<String, String> epochs = new HashMap<>();
      for (String key : values.keySet()) {
        if (!key.startsWith(SCORE)) continue;
        String pkg = key.substring(SCORE.length());
        double weight =
            UsageHistory.weight(
                Double.longBitsToDouble(longValue(values.get(key))),
                longValue(values.get(TIME + pkg)),
                now);
        Object epoch = values.get(EPOCH + pkg);
        if (weight > 0 && epoch instanceof String) {
          result.put(pkg, weight);
          epochs.put(pkg, (String) epoch);
        }
      }
      return new Snapshot(result, epochs);
    }
  }

  public void removed(String packageName) {
    synchronized (LOCK) {
      prefs
          .edit()
          .remove(SCORE + packageName)
          .remove(TIME + packageName)
          .remove(EPOCH + packageName)
          .apply();
    }
  }

  private static long longValue(Object value) {
    return value instanceof Long ? (Long) value : 0;
  }
}
