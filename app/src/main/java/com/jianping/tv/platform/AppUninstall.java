package com.jianping.tv.platform;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import com.jianping.tv.data.AppEntry;

/** Requests the system confirmation UI; never deletes packages or guesses the result. */
public final class AppUninstall {
  public static final int REQUEST = 62;

  private AppUninstall() {}

  public static boolean allowed(Context context, AppEntry app) {
    return blockedReason(context, app).isEmpty();
  }

  public static String blockedReason(Context context, AppEntry app) {
    try {
      PackageInfo info =
          context.getPackageManager().getPackageInfo(app.component.getPackageName(), 0);
      if (app.component.getPackageName().equals(context.getPackageName())) return "当前桌面";
      if ((info.applicationInfo.flags
              & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
          != 0) return "系统应用";
      if (app.firstInstallTime != 0 && app.firstInstallTime != info.firstInstallTime)
        return "应用已变更";
      return "";
    } catch (PackageManager.NameNotFoundException e) {
      return "应用已移除";
    }
  }

  public static boolean installed(Context context, String pkg) {
    try {
      context.getPackageManager().getApplicationInfo(pkg, 0);
      return true;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }

  @SuppressWarnings("deprecation")
  public static void request(Activity activity, AppEntry app) {
    if (!allowed(activity, app))
      throw new IllegalStateException("Application cannot be uninstalled");
    activity.startActivityForResult(
        new Intent(
                Intent.ACTION_UNINSTALL_PACKAGE,
                Uri.parse("package:" + app.component.getPackageName()))
            .putExtra(Intent.EXTRA_RETURN_RESULT, true),
        REQUEST);
  }
}
