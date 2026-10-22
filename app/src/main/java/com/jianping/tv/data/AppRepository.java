package com.jianping.tv.data;

import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.DisplayMetrics;
import java.text.Collator;
import java.util.*;

/** Discovery stays cheap: icon/banner resources are decoded only for a changed cache entry. */
public final class AppRepository {
  private final Context context;

  public static final class Artwork {
    public final Drawable icon, banner;

    Artwork(Drawable icon, Drawable banner) {
      this.icon = icon;
      this.banner = banner;
    }
  }

  public AppRepository(Context c) {
    context = c.getApplicationContext();
  }

  public List<AppEntry> load() {
    PackageManager pm = context.getPackageManager();
    Map<String, AppEntry> entries = new LinkedHashMap<>();
    String locale =
        Build.VERSION.SDK_INT >= 24
            ? context.getResources().getConfiguration().getLocales().toLanguageTags()
            : context.getResources().getConfiguration().locale.toLanguageTag();
    for (String cat : new String[] {Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
      for (ResolveInfo info :
          pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
        if (Thread.currentThread().isInterrupted()) return Collections.emptyList();
        ActivityInfo a = info.activityInfo;
        if (a == null
            || !a.exported
            || a.packageName.equals(context.getPackageName())
            || entries.containsKey(a.packageName)) continue;
        try {
          PackageInfo pkg = pm.getPackageInfo(a.packageName, 0);
          long version = Build.VERSION.SDK_INT >= 28 ? pkg.getLongVersionCode() : pkg.versionCode;
          entries.put(
              a.packageName,
              new AppEntry(
                  new ComponentName(a.packageName, a.name),
                  info.loadLabel(pm).toString(),
                  pkg.lastUpdateTime + ":" + version + ":" + locale));
        } catch (RuntimeException | PackageManager.NameNotFoundException ignored) {
        }
      }
    }
    List<AppEntry> result = new ArrayList<>(entries.values());
    Collator collator = Collator.getInstance(Locale.CHINA);
    Collections.sort(result, (a, b) -> collator.compare(a.label, b.label));
    return result;
  }

  public Artwork artwork(AppEntry entry) {
    PackageManager pm = context.getPackageManager();
    try {
      ActivityInfo a = pm.getActivityInfo(entry.component, 0);
      Drawable icon;
      try {
        icon = resource(pm, a, a.getIconResource());
      } catch (RuntimeException | PackageManager.NameNotFoundException e) {
        icon = a.loadIcon(pm);
      }
      Drawable banner = null;
      try {
        banner = resource(pm, a, a.banner != 0 ? a.banner : a.applicationInfo.banner);
      } catch (RuntimeException | PackageManager.NameNotFoundException ignored) {
      }
      return new Artwork(icon == null ? pm.getDefaultActivityIcon() : icon, banner);
    } catch (RuntimeException | PackageManager.NameNotFoundException e) {
      return new Artwork(pm.getDefaultActivityIcon(), null);
    }
  }

  private Drawable resource(PackageManager pm, ActivityInfo info, int id)
      throws PackageManager.NameNotFoundException {
    // Decode only cache misses, retaining the original high-resolution artwork for TV screens.
    return id == 0
        ? null
        : pm.getResourcesForApplication(info.applicationInfo)
            .getDrawableForDensity(id, DisplayMetrics.DENSITY_XXXHIGH, null);
  }

  public Intent launchIntent(AppEntry e) {
    return new Intent(Intent.ACTION_MAIN)
        .setComponent(e.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
  }
}
