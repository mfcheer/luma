package com.jianping.tv.browser;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import com.jianping.tv.data.AppEntry;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local persistence for user-configured web shortcuts. */
public final class WebShortcutStore {
  private static final String PREF_NAME = "web_shortcuts";
  private static final String KEY_SHORTCUTS = "items";
  public static final String COMPONENT_PKG_PREFIX = "web.";
  public static final String COMPONENT_CLASS = "Shortcut";

  public static final String ADD_WEB_ACTION_PKG = "still.action.add_web";
  public static final String ADD_WEB_ACTION_CLASS = "Add";
  public static final ComponentName ADD_WEB_COMPONENT =
      new ComponentName(ADD_WEB_ACTION_PKG, ADD_WEB_ACTION_CLASS);
  public static final AppEntry ADD_WEB_ENTRY =
      new AppEntry(ADD_WEB_COMPONENT, "添加网页", "still.action.add_web", Long.MAX_VALUE);

  public interface Listener {
    void onShortcutsChanged();
  }

  private final SharedPreferences prefs;
  private final List<Listener> listeners = new ArrayList<>();

  public WebShortcutStore(Context context) {
    this.prefs =
        context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
  }

  public void addListener(Listener l) {
    if (l != null && !listeners.contains(l)) listeners.add(l);
  }

  public void removeListener(Listener l) {
    listeners.remove(l);
  }

  private void notifyChanged() {
    for (Listener l : new ArrayList<>(listeners)) {
      l.onShortcutsChanged();
    }
  }

  public synchronized List<WebShortcut> loadAll() {
    List<WebShortcut> list = new ArrayList<>();
    String raw = prefs.getString(KEY_SHORTCUTS, "[]");
    try {
      JSONArray array = new JSONArray(raw);
      for (int i = 0; i < array.length(); i++) {
        JSONObject obj = array.getJSONObject(i);
        WebShortcut s = WebShortcut.fromJson(obj);
        if (s != null) list.add(s);
      }
    } catch (Exception ignored) {
    }
    return list;
  }

  public synchronized WebShortcut get(String id) {
    if (id == null || id.isEmpty()) return null;
    for (WebShortcut s : loadAll()) {
      if (id.equals(s.id)) return s;
    }
    return null;
  }

  public synchronized void save(WebShortcut shortcut) {
    if (shortcut == null || shortcut.id.isEmpty()) return;
    List<WebShortcut> current = loadAll();
    boolean found = false;
    for (int i = 0; i < current.size(); i++) {
      if (current.get(i).id.equals(shortcut.id)) {
        current.set(i, shortcut);
        found = true;
        break;
      }
    }
    if (!found) {
      current.add(shortcut);
    }
    persist(current);
    notifyChanged();
  }

  public synchronized void delete(String id) {
    if (id == null || id.isEmpty()) return;
    List<WebShortcut> current = loadAll();
    boolean removed = current.removeIf(s -> id.equals(s.id));
    if (removed) {
      persist(current);
      notifyChanged();
    }
  }

  private void persist(List<WebShortcut> list) {
    JSONArray array = new JSONArray();
    for (WebShortcut s : list) {
      try {
        array.put(s.toJson());
      } catch (Exception ignored) {
      }
    }
    prefs.edit().putString(KEY_SHORTCUTS, array.toString()).apply();
  }

  public static boolean isWebShortcut(AppEntry entry) {
    return entry != null && isWebShortcutId(entry.id);
  }

  public static boolean isWebShortcutId(String id) {
    return id != null && id.startsWith(COMPONENT_PKG_PREFIX);
  }

  public static boolean isAddWebEntry(AppEntry entry) {
    return entry != null
        && entry.component != null
        && ADD_WEB_ACTION_PKG.equals(entry.component.getPackageName());
  }

  public static boolean isAddWebId(String id) {
    return id != null && id.startsWith(ADD_WEB_ACTION_PKG);
  }

  public static String extractId(String entryId) {
    if (entryId == null || !entryId.startsWith(COMPONENT_PKG_PREFIX)) return "";
    int slash = entryId.indexOf('/');
    if (slash > COMPONENT_PKG_PREFIX.length()) {
      return entryId.substring(COMPONENT_PKG_PREFIX.length(), slash);
    }
    return entryId.substring(COMPONENT_PKG_PREFIX.length());
  }

  public static AppEntry toAppEntry(WebShortcut s) {
    ComponentName cn = new ComponentName(COMPONENT_PKG_PREFIX + s.id, COMPONENT_CLASS);
    long iconVer = WebIconManager.getIconVersion(s.id);
    return new AppEntry(
        cn,
        s.name,
        s.url + ":" + s.name + ":" + s.pinned + ":" + s.accent + ":" + iconVer,
        s.createTime);
  }
}
