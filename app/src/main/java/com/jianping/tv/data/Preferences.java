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

  public boolean bootEnabled() {
    return prefs.getBoolean("bootEnabled", false);
  }

  public void bootEnabled(boolean v) {
    prefs.edit().putBoolean("bootEnabled", v).apply();
  }

  public boolean showAppNames() {
    return prefs.getBoolean("showAppNames", true);
  }

  public void showAppNames(boolean visible) {
    prefs.edit().putBoolean("showAppNames", visible).apply();
  }

  public void record(String key, String value) {
    prefs.edit().putString(key, value).commit();
  }

  public String read(String key) {
    return prefs.getString(key, "尚无记录");
  }
}
