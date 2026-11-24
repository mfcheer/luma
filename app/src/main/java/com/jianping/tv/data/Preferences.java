package com.jianping.tv.data;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;
import org.json.JSONArray;

public final class Preferences {
  private final SharedPreferences prefs;

  public Preferences(Context c) {
    prefs = c.getSharedPreferences("launcher", Context.MODE_PRIVATE);
  }

  public List<String> appOrder() {
    List<String> r = new ArrayList<>();
    try {
      // Existing favorites become the initial order prefix, never an inclusion filter.
      String key = prefs.contains("appOrder") ? "appOrder" : "favorites";
      JSONArray a = new JSONArray(prefs.getString(key, "[]"));
      for (int i = 0; i < a.length(); i++) r.add(a.getString(i));
    } catch (Exception ignored) {
    }
    return r;
  }

  public void saveAppOrder(List<String> ids) {
    prefs.edit().putString("appOrder", new JSONArray(ids).toString()).apply();
  }

  public boolean hasAppOrder() {
    return prefs.contains("appOrder");
  }

  public boolean lightTheme() {
    String wp = wallpaper();
    if ("pure_light".equals(wp)) {
      return true;
    }
    if ("pure_dark".equals(wp)
        || "sonoma".equals(wp)
        || "big_sur".equals(wp)
        || "catalina".equals(wp)
        || "mojave".equals(wp)
        || "sequoia".equals(wp)
        || "patagonia".equals(wp)
        || "yosemite".equals(wp)) {
      return false;
    }
    return "light".equals(prefs.getString("appearance", "dark"));
  }

  public void lightTheme(boolean light) {
    prefs.edit().putString("appearance", light ? "light" : "dark").apply();
  }

  public String wallpaper() {
    return prefs.getString("wallpaper", "sonoma");
  }

  public void wallpaper(String key) {
    prefs.edit().putString("wallpaper", key).apply();
  }

  public boolean bootEnabled() {
    return prefs.getBoolean("bootEnabled", false);
  }

  public void bootEnabled(boolean v) {
    prefs.edit().putBoolean("bootEnabled", v).apply();
  }

  public boolean showAppNames() {
    return prefs.getBoolean("showAppNames", false);
  }

  public void showAppNames(boolean visible) {
    prefs.edit().putBoolean("showAppNames", visible).apply();
  }

  public boolean showAirInstallCard() {
    return prefs.getBoolean("showAirInstallCard", true);
  }

  public void showAirInstallCard(boolean visible) {
    prefs.edit().putBoolean("showAirInstallCard", visible).apply();
  }

  public String otherHome() {
    return prefs.getString("otherHome", "");
  }

  public void otherHome(String component) {
    prefs.edit().putString("otherHome", component).apply();
  }

  public void record(String key, String value) {
    prefs.edit().putString(key, value).commit();
  }

  public String read(String key) {
    return prefs.getString(key, "尚无记录");
  }
}
