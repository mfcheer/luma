package com.jianping.tv.platform;

import android.content.*;
import android.content.pm.*;
import android.os.Build;
import com.jianping.tv.data.Preferences;
import java.util.*;

public final class DeviceDiagnostics {
  public static Intent homeIntent() {
    return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
  }

  public static List<ResolveInfo> homeCandidates(Context c) {
    List<ResolveInfo> result = new ArrayList<>();
    for (ResolveInfo info :
        c.getPackageManager()
            .queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)) {
      ActivityInfo a = info.activityInfo;
      if (a != null
          && a.enabled
          && a.exported
          && (a.permission == null
              || c.checkSelfPermission(a.permission) == PackageManager.PERMISSION_GRANTED))
        result.add(info);
    }
    return result;
  }

  public static ComponentName resolvedHome(Context c) {
    ResolveInfo info =
        c.getPackageManager().resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY);
    return info == null || info.activityInfo == null
        ? null
        : new ComponentName(info.activityInfo.packageName, info.activityInfo.name);
  }

  public static boolean usableHome(ResolveInfo info) {
    String name = info.activityInfo.name;
    return !"android".equals(info.activityInfo.packageName)
        && !name.endsWith(".FallbackHome")
        && !name.endsWith(".ResolverActivity")
        && !name.endsWith(".ChooserActivity");
  }

  public static String homePackage(Context c) {
    ComponentName resolved = resolvedHome(c);
    if (resolved != null)
      for (ResolveInfo candidate : homeCandidates(c)) {
        ActivityInfo a = candidate.activityInfo;
        if (usableHome(candidate) && resolved.equals(new ComponentName(a.packageName, a.name)))
          return a.packageName;
      }
    // A system resolver is an entry point for choosing, not a confirmed preferred launcher.
    return "未确认";
  }

  public static String homeLabel(Context c) {
    String pkg = homePackage(c);
    if (pkg.equals(c.getPackageName())) return "Still";
    if (pkg.equals("未确认")) return "由系统决定";
    try {
      return c.getPackageManager()
          .getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg, 0))
          .toString();
    } catch (PackageManager.NameNotFoundException | RuntimeException e) {
      return pkg;
    }
  }

  public static String report(Context c) {
    Preferences p = new Preferences(c);
    ComponentName resolved = resolvedHome(c);
    StringBuilder candidates = new StringBuilder();
    for (ResolveInfo info : homeCandidates(c)) {
      if (candidates.length() > 0) candidates.append("\n  ");
      candidates.append(info.activityInfo.packageName).append("/").append(info.activityInfo.name);
    }
    return "设备  "
        + Build.MANUFACTURER
        + " / "
        + Build.MODEL
        + "\nAndroid  "
        + Build.VERSION.RELEASE
        + "  ·  API "
        + Build.VERSION.SDK_INT
        + "\n架构  "
        + android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS)
        + "\n系统  "
        + Build.DISPLAY
        + "\n当前默认桌面  "
        + homeLabel(c)
        + "（"
        + homePackage(c)
        + "）"
        + "\nHome 解析组件  "
        + (resolved == null ? "未解析" : resolved.flattenToShortString())
        + "\n开机通知  "
        + p.read("bootReceived")
        + "\n启动请求  "
        + p.read("bootAttempt")
        + "\n开机进入界面  "
        + p.read("bootVisible")
        + "\n上次 Home 进入  "
        + p.read("homeVisible")
        + "\n可用桌面入口\n  "
        + (candidates.length() == 0 ? "未发现" : candidates);
  }
}
