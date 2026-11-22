package com.jianping.tv.browser;

import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/** Durable model for a user-added web shortcut on the TV desktop. */
public final class WebShortcut {
  public final String id;
  public String name;
  public String url;
  public long createTime;
  public boolean pinned;
  public int accent;

  public WebShortcut(
      String id, String name, String url, long createTime, boolean pinned, int accent) {
    this.id = id == null ? "" : id;
    this.name = name == null ? "" : name.trim();
    this.url = normalizeUrl(url);
    this.createTime = createTime;
    this.pinned = pinned;
    this.accent = accent != 0 ? accent : deriveAccent(this.name);
  }

  public static String normalizeUrl(String input) {
    if (input == null) return "about:blank";
    String trimmed = input.trim();
    if (trimmed.isEmpty()) return "about:blank";
    if (!trimmed.startsWith("http://")
        && !trimmed.startsWith("https://")
        && !trimmed.startsWith("file://")
        && !trimmed.startsWith("about:")) {
      if (isLanOrLocal(trimmed)) {
        return "http://" + trimmed;
      } else {
        return "https://" + trimmed;
      }
    }
    return trimmed;
  }

  public static boolean isLanOrLocal(String input) {
    if (input == null || input.isEmpty()) return false;
    String host = input.trim();
    int slashIndex = host.indexOf('/');
    if (slashIndex >= 0) {
      host = host.substring(0, slashIndex);
    }
    int colonIndex = host.indexOf(':');
    if (colonIndex >= 0) {
      host = host.substring(0, colonIndex);
    }
    host = host.trim().toLowerCase(Locale.ROOT);

    if (host.equals("localhost") || host.equals("127.0.0.1")) {
      return true;
    }
    if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("127.")) {
      return true;
    }
    if (host.endsWith(".local")
        || host.endsWith(".lan")
        || host.endsWith(".home")
        || host.endsWith(".internal")) {
      return true;
    }
    if (host.startsWith("172.")) {
      String[] parts = host.split("\\.");
      if (parts.length >= 2) {
        try {
          int second = Integer.parseInt(parts[1]);
          if (second >= 16 && second <= 31) {
            return true;
          }
        } catch (NumberFormatException ignored) {
        }
      }
    }
    return false;
  }

  public static int deriveAccent(String name) {
    int[] palette =
        new int[] {
          0xFF2563EB, // Royal Blue
          0xFF0D9488, // Teal
          0xFF7C3AED, // Violet
          0xFFEA580C, // Orange
          0xFF0284C7, // Sky Blue
          0xFF16A34A, // Green
          0xFFD97706, // Amber
          0xFFDB2777 // Pink
        };
    int hash = name == null ? 0 : Math.abs(name.hashCode());
    return palette[hash % palette.length];
  }

  public JSONObject toJson() throws JSONException {
    JSONObject json = new JSONObject();
    json.put("id", id);
    json.put("name", name);
    json.put("url", url);
    json.put("createTime", createTime);
    json.put("pinned", pinned);
    json.put("accent", accent);
    return json;
  }

  public static WebShortcut fromJson(JSONObject json) {
    if (json == null) return null;
    String id = json.optString("id", "");
    if (id.isEmpty()) return null;
    String name = json.optString("name", "网页");
    String url = json.optString("url", "about:blank");
    long createTime = json.optLong("createTime", System.currentTimeMillis());
    boolean pinned = json.optBoolean("pinned", false);
    int accent = json.optInt("accent", 0);
    return new WebShortcut(id, name, url, createTime, pinned, accent);
  }
}
