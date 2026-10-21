package com.jianping.tv.data;

import android.content.*;
import android.content.pm.*;
import java.text.Collator;
import java.util.*;

public final class AppRepository {
  private final Context context;

  public AppRepository(Context c) {
    context = c.getApplicationContext();
  }

  public List<AppEntry> load() {
    PackageManager pm = context.getPackageManager();
    Map<String, AppEntry> entries = new LinkedHashMap<>();
    for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
      for (ResolveInfo info :
          pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
        ActivityInfo a = info.activityInfo;
        if (a == null || !a.exported || a.packageName.equals(context.getPackageName())) continue;
        // Prefer TV launch activity when an app exposes both phone and television entry points.
        if (!entries.containsKey(a.packageName))
          try {
            entries.put(
                a.packageName,
                new AppEntry(
                    new ComponentName(a.packageName, a.name),
                    info.loadLabel(pm).toString(),
                    loadBestIcon(pm, info)));
          } catch (RuntimeException ignored) {
          }
      }
    }
    List<AppEntry> result = new ArrayList<>(entries.values());
    Collator collator = Collator.getInstance(Locale.CHINA);
    Collections.sort(result, (a, b) -> collator.compare(a.label, b.label));
    return result;
  }

  private android.graphics.drawable.Drawable loadBestIcon(PackageManager pm, ResolveInfo info) {
    try {
      int id = info.getIconResource();
      if (id != 0)
        return pm.getResourcesForApplication(info.activityInfo.applicationInfo)
            .getDrawableForDensity(id, android.util.DisplayMetrics.DENSITY_XXXHIGH, null);
    } catch (Exception ignored) {
      // Some vendor packages expose nonstandard resources. Keep the system fallback.
    }
    return info.loadIcon(pm);
  }

  public Intent launchIntent(AppEntry e) {
    return new Intent(Intent.ACTION_MAIN)
        .setComponent(e.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
  }
}
