package com.jianping.tv.ui;

import android.content.*;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.*;
import android.util.AtomicFile;
import com.jianping.tv.data.AppEntry;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Process-wide prepared artwork plus a disposable, atomic disk snapshot for process death. */
public final class AppCatalogCache {
  private static final int FORMAT = 1;
  private static volatile Snapshot memory;
  private final File directory;
  private final Resources resources;

  public static final class Visual {
    public final String fingerprint;
    public final int accent;
    public final Drawable card, preview;
    private volatile boolean persisted;

    public Visual(String fingerprint, int accent, Drawable card, Drawable preview) {
      this.fingerprint = fingerprint;
      this.accent = accent;
      this.card = card;
      this.preview = preview;
    }
  }

  public static final class Snapshot {
    public final List<AppEntry> apps;
    public final Map<String, Visual> visuals;

    public Snapshot(List<AppEntry> apps, Map<String, Visual> visuals) {
      this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
      this.visuals = Collections.unmodifiableMap(new HashMap<>(visuals));
    }

    public boolean samePresentation(Snapshot other) {
      if (other == null || apps.size() != other.apps.size()) return false;
      for (int i = 0; i < apps.size(); i++) {
        AppEntry a = apps.get(i), b = other.apps.get(i);
        if (!a.id.equals(b.id)
            || !a.label.equals(b.label)
            || a.firstInstallTime != b.firstInstallTime
            || !a.fingerprint.equals(b.fingerprint)
            || visuals.get(a.id) != other.visuals.get(b.id)) return false;
      }
      return true;
    }
  }

  public AppCatalogCache(Context context) {
    directory = new File(context.getCacheDir(), "catalog-v" + FORMAT);
    resources = context.getResources();
  }

  public Snapshot memory() {
    return memory;
  }

  public void remember(Snapshot snapshot) {
    memory = snapshot;
  }

  public Snapshot read() {
    try {
      JSONObject index =
          new JSONObject(
              new String(
                  new AtomicFile(new File(directory, "catalog.json")).readFully(),
                  StandardCharsets.UTF_8));
      if (index.getInt("format") != FORMAT) return null;
      List<AppEntry> apps = new ArrayList<>();
      Map<String, Visual> visuals = new HashMap<>();
      JSONArray items = index.getJSONArray("apps");
      for (int i = 0; i < items.length(); i++) {
        if (Thread.currentThread().isInterrupted()) return null;
        JSONObject item = items.getJSONObject(i);
        ComponentName component = ComponentName.unflattenFromString(item.getString("id"));
        if (component == null) return null;
        AppEntry entry =
            new AppEntry(
                component,
                item.getString("label"),
                item.getString("fingerprint"),
                item.optLong("firstInstallTime", 0));
        Visual visual = readVisual(entry);
        if (visual == null)
          return null; // A missing/corrupt cache falls back to metadata reconciliation.
        apps.add(entry);
        visuals.put(entry.id, visual);
      }
      return new Snapshot(apps, visuals);
    } catch (IOException | RuntimeException | JSONException e) {
      return null;
    }
  }

  public Visual readVisual(AppEntry entry) {
    try {
      String key = key(entry);
      JSONObject info =
          new JSONObject(
              new String(
                  new AtomicFile(new File(directory, key + ".json")).readFully(),
                  StandardCharsets.UTF_8));
      if (!entry.fingerprint.equals(info.getString("fingerprint"))) return null;
      Bitmap card = bitmap(new File(directory, key + ".png"), 384, 250);
      Bitmap soft = bitmap(new File(directory, key + "-soft.png"), 128, 83);
      if (card == null || soft == null) return null;
      Visual visual =
          new Visual(
              entry.fingerprint,
              info.getInt("accent"),
              new BitmapDrawable(resources, card),
              new BitmapDrawable(resources, soft));
      visual.persisted = true;
      return visual;
    } catch (IOException | RuntimeException | JSONException e) {
      return null;
    }
  }

  private Bitmap bitmap(File file, int width, int height) {
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeFile(file.getPath(), options);
    if (options.outWidth != width || options.outHeight != height) return null;
    options.inJustDecodeBounds = false;
    options.inScaled = false;
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    Bitmap result = BitmapFactory.decodeFile(file.getPath(), options);
    if (result != null) result.setDensity(Bitmap.DENSITY_NONE);
    return result;
  }

  /** Write after publishing the live screen; interruption leaves the previous index intact. */
  public void write(Snapshot snapshot) {
    try {
      if (!directory.isDirectory() && !directory.mkdirs()) return;
      JSONArray apps = new JSONArray();
      Set<String> keep = new HashSet<>();
      keep.add("catalog.json");
      for (AppEntry entry : snapshot.apps) {
        if (Thread.currentThread().isInterrupted()) return;
        Visual visual = snapshot.visuals.get(entry.id);
        String key = key(entry);
        // Rewrite only when a changed visual was not already restored from this exact disk key.
        if (!visual.persisted
            || !new File(directory, key + ".png").isFile()
            || !new File(directory, key + "-soft.png").isFile()
            || !new File(directory, key + ".json").isFile()) {
          writeBitmap(new File(directory, key + ".png"), visual.card);
          writeBitmap(new File(directory, key + "-soft.png"), visual.preview);
          writeJson(
              new File(directory, key + ".json"),
              new JSONObject().put("fingerprint", entry.fingerprint).put("accent", visual.accent));
          visual.persisted = true;
        }
        keep.add(key + ".png");
        keep.add(key + "-soft.png");
        keep.add(key + ".json");
        apps.put(
            new JSONObject()
                .put("id", entry.id)
                .put("label", entry.label)
                .put("firstInstallTime", entry.firstInstallTime)
                .put("fingerprint", entry.fingerprint));
      }
      if (Thread.currentThread().isInterrupted()) return;
      writeJson(
          new File(directory, "catalog.json"),
          new JSONObject().put("format", FORMAT).put("apps", apps));
      File[] files = directory.listFiles();
      if (files != null) for (File file : files) if (!keep.contains(file.getName())) file.delete();
    } catch (IOException | RuntimeException | JSONException ignored) {
      /* Cache loss never changes preferences. */
    }
  }

  private void writeBitmap(File file, Drawable drawable) throws IOException {
    if (!(drawable instanceof BitmapDrawable)) throw new IOException("Unsupported cached artwork");
    AtomicFile atomic = new AtomicFile(file);
    FileOutputStream stream = null;
    try {
      stream = atomic.startWrite();
      if (!((BitmapDrawable) drawable).getBitmap().compress(Bitmap.CompressFormat.PNG, 100, stream))
        throw new IOException("Cannot encode artwork");
      atomic.finishWrite(stream);
    } catch (IOException | RuntimeException e) {
      atomic.failWrite(stream);
      throw e;
    }
  }

  private void writeJson(File file, JSONObject json) throws IOException {
    AtomicFile atomic = new AtomicFile(file);
    FileOutputStream stream = null;
    try {
      stream = atomic.startWrite();
      stream.write(json.toString().getBytes(StandardCharsets.UTF_8));
      atomic.finishWrite(stream);
    } catch (IOException | RuntimeException e) {
      atomic.failWrite(stream);
      throw e;
    }
  }

  private String key(AppEntry entry) {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(
                  (FORMAT + ":" + entry.id + ":" + entry.fingerprint)
                      .getBytes(StandardCharsets.UTF_8));
      StringBuilder key = new StringBuilder();
      for (byte b : digest) {
        key.append(Character.forDigit((b >> 4) & 15, 16));
        key.append(Character.forDigit(b & 15, 16));
      }
      return key.toString();
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
