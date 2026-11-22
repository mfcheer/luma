package com.jianping.tv.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Manages downloading, local caching, and lifecycle of website icons and favicons. */
public final class WebIconManager {
  private static final String ICONS_DIR = "web_icons";
  private static final int MAX_CACHE_SIZE = 30;
  private static final LruCache<String, Bitmap> MEMORY_CACHE =
      new LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
          return 1;
        }
      };

  public interface Listener {
    void onIconUpdated(String shortcutId);
  }

  private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();
  private static final Map<String, Long> ICON_VERSIONS = new ConcurrentHashMap<>();

  private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
  private static Handler getMainHandler() {
    return new Handler(Looper.getMainLooper());
  }

  private WebIconManager() {}

  public static void addListener(Listener listener) {
    if (listener != null && !LISTENERS.contains(listener)) {
      LISTENERS.add(listener);
    }
  }

  public static void removeListener(Listener listener) {
    LISTENERS.remove(listener);
  }

  public static long getIconVersion(String shortcutId) {
    if (shortcutId == null) return 0L;
    Long v = ICON_VERSIONS.get(shortcutId);
    return v != null ? v : 0L;
  }

  public static synchronized Bitmap getIcon(Context context, String shortcutId) {
    if (shortcutId == null || shortcutId.isEmpty()) return null;
    Bitmap cached = MEMORY_CACHE.get(shortcutId);
    if (cached != null && !cached.isRecycled()) {
      return cached;
    }
    if (context == null) return null;
    File file = getIconFile(context, shortcutId);
    if (file.exists() && file.length() > 0) {
      try {
        Bitmap decoded = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (decoded != null) {
          MEMORY_CACHE.put(shortcutId, decoded);
          if (!ICON_VERSIONS.containsKey(shortcutId)) {
            ICON_VERSIONS.put(shortcutId, file.lastModified());
          }
          return decoded;
        }
      } catch (Throwable ignored) {
      }
    }
    return null;
  }

  public static synchronized boolean hasIcon(Context context, String shortcutId) {
    if (shortcutId == null || shortcutId.isEmpty()) return false;
    if (MEMORY_CACHE.get(shortcutId) != null) return true;
    if (context == null) return false;
    File file = getIconFile(context, shortcutId);
    return file.exists() && file.length() > 0;
  }

  public static synchronized void saveIcon(Context context, String shortcutId, Bitmap bitmap) {
    if (context == null || shortcutId == null || shortcutId.isEmpty() || bitmap == null) return;
    Bitmap scaled = scaleIfNeeded(bitmap, 256);
    MEMORY_CACHE.put(shortcutId, scaled);
    long now = System.currentTimeMillis();
    ICON_VERSIONS.put(shortcutId, now);

    EXECUTOR.execute(
        () -> {
          File file = getIconFile(context, shortcutId);
          File parent = file.getParentFile();
          if (parent != null && !parent.exists()) {
            parent.mkdirs();
          }
          try (FileOutputStream fos = new FileOutputStream(file)) {
            scaled.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
          } catch (IOException ignored) {
          }
        });

    getMainHandler().post(
        () -> {
          for (Listener l : LISTENERS) {
            try {
              l.onIconUpdated(shortcutId);
            } catch (Throwable ignored) {
            }
          }
        });
  }

  public static synchronized void deleteIcon(Context context, String shortcutId) {
    if (shortcutId == null || shortcutId.isEmpty()) return;
    MEMORY_CACHE.remove(shortcutId);
    ICON_VERSIONS.remove(shortcutId);
    if (context == null) return;
    File file = getIconFile(context, shortcutId);
    if (file.exists()) {
      file.delete();
    }
  }

  public static void clearMemoryCache() {
    MEMORY_CACHE.evictAll();
  }

  public static void syncFromDisk(Context context) {
    if (context == null) return;
    File dir = new File(context.getFilesDir(), ICONS_DIR);
    if (dir.exists() && dir.isDirectory()) {
      File[] files = dir.listFiles((d, name) -> name.endsWith(".png"));
      if (files != null) {
        boolean changed = false;
        for (File f : files) {
          String name = f.getName();
          String id = name.substring(0, name.length() - 4);
          long lastMod = f.lastModified();
          Long current = ICON_VERSIONS.get(id);
          if (current == null || current < lastMod) {
            ICON_VERSIONS.put(id, lastMod);
            changed = true;
          }
        }
        if (changed) {
          clearMemoryCache();
        }
      }
    }
  }

  public static File getIconFile(Context context, String shortcutId) {
    File dir = new File(context.getFilesDir(), ICONS_DIR);
    return new File(dir, shortcutId + ".png");
  }

  public static void fetchDirectIconAsync(Context context, String shortcutId, String iconUrl) {
    if (context == null || shortcutId == null || iconUrl == null || iconUrl.isEmpty()) return;
    EXECUTOR.execute(
        () -> {
          try {
            Bitmap bmp = downloadAndDecode(iconUrl);
            if (bmp != null) {
              saveIcon(context, shortcutId, bmp);
            }
          } catch (Throwable ignored) {
          }
        });
  }

  public static void fetchFaviconAsync(
      Context context, WebShortcut shortcut, Runnable onDone) {
    if (context == null || shortcut == null || shortcut.url == null) return;
    String targetUrl = shortcut.url.trim();
    if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) return;

    EXECUTOR.execute(
        () -> {
          Bitmap fetched = null;
          try {
            fetched = discoverAndFetch(targetUrl);
          } catch (Throwable ignored) {
          }

          if (fetched != null) {
            final Bitmap finalBmp = fetched;
            saveIcon(context, shortcut.id, finalBmp);
            if (onDone != null) {
              getMainHandler().post(onDone);
            }
          }
        });
  }

  private static Bitmap discoverAndFetch(String siteUrl) {
    // 1. Try extracting explicit touch icon or favicon from HTML
    String foundIconUrl = findIconUrlFromHtml(siteUrl);
    if (foundIconUrl != null) {
      Bitmap bmp = downloadAndDecode(foundIconUrl);
      if (bmp != null) return bmp;
    }

    try {
      URI uri = new URI(siteUrl);
      String scheme = uri.getScheme() != null ? uri.getScheme() : "http";
      String host = uri.getHost();
      int port = uri.getPort();
      if (host != null && !host.isEmpty()) {
        String base = scheme + "://" + host + (port > 0 ? ":" + port : "");
        // 2. Try Apple touch icon direct path
        Bitmap appleTouch = downloadAndDecode(base + "/apple-touch-icon.png");
        if (appleTouch != null) return appleTouch;

        // 3. Try favicon.png
        Bitmap favPng = downloadAndDecode(base + "/favicon.png");
        if (favPng != null) return favPng;

        // 4. Try favicon.ico
        Bitmap favIco = downloadAndDecode(base + "/favicon.ico");
        if (favIco != null) return favIco;
      }
    } catch (Throwable ignored) {
    }
    return null;
  }

  private static HttpURLConnection openConnectionWithRedirects(String urlStr) throws IOException {
    String currentUrl = urlStr;
    for (int i = 0; i < 5; i++) {
      HttpURLConnection conn = (HttpURLConnection) new URL(currentUrl).openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(4000);
      conn.setReadTimeout(4000);
      conn.setInstanceFollowRedirects(false);
      conn.setRequestProperty(
          "User-Agent",
          "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
      int code = conn.getResponseCode();
      if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
        String location = conn.getHeaderField("Location");
        conn.disconnect();
        if (location != null && !location.isEmpty()) {
          currentUrl = resolveUrl(currentUrl, location);
          continue;
        }
      }
      return conn;
    }
    return (HttpURLConnection) new URL(currentUrl).openConnection();
  }

  private static String findIconUrlFromHtml(String siteUrl) {
    try {
      HttpURLConnection conn = openConnectionWithRedirects(siteUrl);
      String effectiveUrl = conn.getURL().toString();
      int code = conn.getResponseCode();
      if (code < 200 || code >= 400) {
        conn.disconnect();
        return null;
      }

      InputStream in = conn.getInputStream();
      byte[] buf = new byte[65536];
      int read = in.read(buf);
      conn.disconnect();
      if (read <= 0) return null;

      String html = new String(buf, 0, read, StandardCharsets.UTF_8);

      // Priority 1: High-res Apple Touch Icon
      Pattern pApple =
          Pattern.compile(
              "<link[^>]+rel=[\\\"']?(?:apple-touch-icon|apple-touch-icon-precomposed)[\\\"']?[^>]*>",
              Pattern.CASE_INSENSITIVE);
      Matcher mApple = pApple.matcher(html);
      if (mApple.find()) {
        String href = extractHref(mApple.group());
        if (href != null && !href.isEmpty()) {
          return resolveUrl(effectiveUrl, href);
        }
      }

      // Priority 2: Standard Favicon / Icon
      Pattern pIcon =
          Pattern.compile(
              "<link[^>]+rel=[\\\"']?(?:icon|shortcut icon)[\\\"']?[^>]*>",
              Pattern.CASE_INSENSITIVE);
      Matcher mIcon = pIcon.matcher(html);
      while (mIcon.find()) {
        String href = extractHref(mIcon.group());
        if (href != null && !href.isEmpty()) {
          return resolveUrl(effectiveUrl, href);
        }
      }
    } catch (Throwable ignored) {
    }
    return null;
  }

  private static String extractHref(String tag) {
    Pattern hrefPattern =
        Pattern.compile("href=[\\\"']?([^\\\"' >]+)[\\\"']?", Pattern.CASE_INSENSITIVE);
    Matcher hrefMatcher = hrefPattern.matcher(tag);
    if (hrefMatcher.find()) {
      return hrefMatcher.group(1);
    }
    return null;
  }

  private static String resolveUrl(String baseUrl, String href) {
    if (href.startsWith("http://") || href.startsWith("https://")) {
      return href;
    }
    if (href.startsWith("//")) {
      return (baseUrl.startsWith("https:") ? "https:" : "http:") + href;
    }
    try {
      URI baseUri = new URI(baseUrl);
      URI resolved = baseUri.resolve(href);
      return resolved.toString();
    } catch (Exception e) {
      return href;
    }
  }

  private static Bitmap downloadAndDecode(String iconUrl) {
    try {
      HttpURLConnection conn = openConnectionWithRedirects(iconUrl);
      if (conn.getResponseCode() != 200) {
        conn.disconnect();
        return null;
      }
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      try (InputStream in = conn.getInputStream()) {
        byte[] buffer = new byte[4096];
        int n;
        while ((n = in.read(buffer)) > 0) {
          baos.write(buffer, 0, n);
          if (baos.size() > 2 * 1024 * 1024) break; // Max 2MB icon
        }
      }
      conn.disconnect();
      byte[] data = baos.toByteArray();
      if (data.length == 0) return null;

      // Try standard decode (PNG, JPEG, WEBP)
      Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
      if (bmp != null) return bmp;

      // Try parsing ICO container if it wraps a PNG
      return decodeIcoPng(data);
    } catch (Throwable ignored) {
      return null;
    }
  }

  private static Bitmap decodeIcoPng(byte[] data) {
    if (data.length < 6) return null;
    byte[] pngMagic = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    for (int i = 0; i <= data.length - 8; i++) {
      boolean match = true;
      for (int j = 0; j < 8; j++) {
        if (data[i + j] != pngMagic[j]) {
          match = false;
          break;
        }
      }
      if (match) {
        try {
          return BitmapFactory.decodeByteArray(data, i, data.length - i);
        } catch (Throwable ignored) {
        }
      }
    }
    return null;
  }

  private static Bitmap scaleIfNeeded(Bitmap src, int maxDim) {
    int w = src.getWidth();
    int h = src.getHeight();
    if (w <= maxDim && h <= maxDim) return src;
    float ratio = Math.min((float) maxDim / w, (float) maxDim / h);
    int targetW = Math.round(w * ratio);
    int targetH = Math.round(h * ratio);
    return Bitmap.createScaledBitmap(src, targetW, targetH, true);
  }
}
