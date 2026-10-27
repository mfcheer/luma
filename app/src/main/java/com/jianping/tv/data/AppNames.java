package com.jianping.tv.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import java.util.*;

/** Local display names, independent of original labels, ordering and disposable artwork. */
public final class AppNames {
  public static final int MAX_LENGTH = 30;
  private final Context context;
  private final SharedPreferences prefs;

  public AppNames(Context context) {
    this.context = context.getApplicationContext();
    prefs = this.context.getSharedPreferences("app_names_v1", Context.MODE_PRIVATE);
  }

  public String label(AppEntry app) {
    String name = custom(app);
    return name.isEmpty() ? app.label : name;
  }

  public String custom(AppEntry app) {
    String pkg = app.component.getPackageName();
    Map<String, ?> values = prefs.getAll();
    Object value = values.get("name." + pkg);
    String name = value instanceof String ? (String) value : "";
    if (name.isEmpty()) return "";
    long installed = app.firstInstallTime;
    if (installed == 0) {
      try {
        installed = context.getPackageManager().getPackageInfo(pkg, 0).firstInstallTime;
      } catch (PackageManager.NameNotFoundException e) {
        return "";
      }
    }
    Object stamp = values.get("installed." + pkg);
    return stamp instanceof Long && installed == (Long) stamp ? name : "";
  }

  public long revision() {
    Object value = prefs.getAll().get("revision");
    return value instanceof Long ? (Long) value : 0;
  }

  public boolean save(AppEntry app, String value) throws PackageManager.NameNotFoundException {
    String name = clean(value);
    boolean original = name.equals(clean(app.label));
    if (name.isEmpty() || (!original && name.codePointCount(0, name.length()) > MAX_LENGTH))
      throw new IllegalArgumentException("名称不能为空，最多30个字符");
    String pkg = app.component.getPackageName();
    long installed = context.getPackageManager().getPackageInfo(pkg, 0).firstInstallTime;
    if (app.firstInstallTime != 0 && app.firstInstallTime != installed)
      throw new PackageManager.NameNotFoundException(pkg);
    SharedPreferences.Editor edit = prefs.edit().putLong("revision", revision() + 1);
    if (original) edit.remove("name." + pkg).remove("installed." + pkg);
    else edit.putString("name." + pkg, name).putLong("installed." + pkg, installed);
    return edit.commit();
  }

  public void removed(String pkg) {
    if (!prefs.contains("name." + pkg)) return;
    prefs
        .edit()
        .remove("name." + pkg)
        .remove("installed." + pkg)
        .putLong("revision", revision() + 1)
        .apply();
  }

  public static String clean(String value) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < value.length(); ) {
      int cp = value.codePointAt(i);
      if (!Character.isISOControl(cp)) result.appendCodePoint(cp);
      i += Character.charCount(cp);
    }
    int start = 0, end = result.length();
    while (start < end && space(result.codePointAt(start)))
      start += Character.charCount(result.codePointAt(start));
    while (end > start && space(result.codePointBefore(end)))
      end -= Character.charCount(result.codePointBefore(end));
    return result.substring(start, end);
  }

  private static boolean space(int cp) {
    return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
  }
}
