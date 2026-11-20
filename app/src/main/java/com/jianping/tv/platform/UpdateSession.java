package com.jianping.tv.platform;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** One user-initiated update per process; no Activity retained by the download worker. */
public final class UpdateSession {
  public enum State { IDLE, CHECKING, AVAILABLE, DOWNLOADING, VERIFYING, READY, PERMISSION, INSTALLING, ERROR, CURRENT, INCOMPATIBLE }
  public interface Listener { void onUpdateChanged(); }
  private static UpdateSession instance;
  public static synchronized UpdateSession get(Context context) {
    if (instance == null) instance = new UpdateSession(context.getApplicationContext());
    return instance;
  }

  private final Context context;
  private final SharedPreferences prefs;
  interface DownloaderFactory { UpdateDownloader create(); }
  private final DownloaderFactory downloaderFactory;
  private UpdateDownloader downloader;
  private final List<Listener> listeners = new ArrayList<>();
  public State state = State.IDLE;
  public AppUpdate.UpdateInfo info;
  public String message = "";
  public int percent;
  public long bytes, total;
  private int generation;
  private boolean downloadError;

  private UpdateSession(Context context) {
    this(context, UpdateDownloader::new);
  }

  UpdateSession(Context context, DownloaderFactory factory) {
    this.context = context;
    this.downloaderFactory = factory;
    this.downloader = factory.create();
    prefs = context.getSharedPreferences("still_update_session", Context.MODE_PRIVATE);
    // A killed process cannot resume a partial stream. Remove only our own temporary files.
    File[] leftovers = StillFileProvider.getUpdateDir(context).listFiles();
    if (leftovers != null) for (File file : leftovers) {
      if (file.getName().matches("still_update_[0-9]+\\.tmp")) file.delete();
    }
    try {
      String manifest = prefs.getString("manifest", "");
      if (!manifest.isEmpty()) {
        info = AppUpdate.UpdateInfo.fromJson(manifest);
        state = State.READY; // Revalidate asynchronously before every install, including after restart.
      }
    } catch (RuntimeException e) {
      prefs.edit().clear().commit();
    }
  }

  public void addListener(Listener listener) { if (!listeners.contains(listener)) listeners.add(listener); }
  public void removeListener(Listener listener) { listeners.remove(listener); }
  private void changed() { for (Listener listener : new ArrayList<>(listeners)) listener.onUpdateChanged(); }
  public File apk() { return info == null ? null : new File(StillFileProvider.getUpdateDir(context), AppUpdate.getUpdateApkFileName(info.versionCode)); }
  public boolean busy() { return state == State.CHECKING || state == State.DOWNLOADING || state == State.VERIFYING; }

  public void check() {
    if (busy()) return;
    info = null;
    message = "";
    state = State.CHECKING;
    final int token = ++generation;
    downloader = downloaderFactory.create();
    changed();
    checkUrl(AppUpdate.DEFAULT_UPDATE_URL, false, token);
  }

  private void checkUrl(String url, boolean mirror, int token) {
    downloader.checkUpdate(url, new UpdateDownloader.CheckCallback() {
      public void onCheckSuccess(AppUpdate.UpdateInfo result) {
        if (token != generation) return;
        info = result;
        if (result.versionCode <= DeviceDiagnostics.appVersionCode(context)) {
          state = State.CURRENT;
        } else if (!AppUpdate.isSystemCompatible(result.minSdk)) {
          state = State.INCOMPATIBLE;
        } else if (apk().isFile()) {
          verifyCached(token);
          return;
        } else {
          state = State.AVAILABLE;
        }
        changed();
      }
      public void onCheckError(String error) {
        if (token != generation) return;
        if (!mirror) checkUrl(AppUpdate.DEFAULT_MIRROR_URL, true, token);
        else fail(error, false);
      }
    });
  }

  private void fail(String error, boolean download) {
    message = error;
    downloadError = download;
    state = State.ERROR;
    changed();
  }

  public void retry() {
    if (downloadError && info != null) download(); else check();
  }

  public void download() {
    if (busy() || info == null || !AppUpdate.isSystemCompatible(info.minSdk)
        || info.versionCode <= DeviceDiagnostics.appVersionCode(context)) return;
    final int token = ++generation;
    state = State.DOWNLOADING;
    percent = 0; bytes = 0; total = info.size; message = "";
    downloader = downloaderFactory.create();
    changed();
    downloader.downloadApk(context, info, StillFileProvider.getUpdateDir(context), new UpdateDownloader.DownloadCallback() {
      public void onProgress(int p, long b, long t) {
        if (token != generation) return;
        percent = Math.min(100, Math.max(0, p)); bytes = b; total = t; changed();
      }
      public void onVerifying() {
        if (token != generation) return;
        state = State.VERIFYING; changed();
      }
      public void onSuccess(File file) {
        if (token != generation) return;
        if (!save("ready")) { fail("无法保存更新进度，请重试。", true); return; }
        state = State.READY;
        changed();
      }
      public void onError(String error) { if (token == generation) fail(error, true); }
      public void onCancelled() {
        if (token != generation) return;
        state = State.AVAILABLE; message = "下载已取消，可重新下载。"; changed();
      }
    });
  }

  public void cancel() {
    if (state != State.CHECKING && state != State.DOWNLOADING) return;
    downloader.cancel();
    ++generation;
    state = info == null ? State.IDLE : State.AVAILABLE;
    message = "已取消，未完成的下载将清理。";
    changed();
  }

  private boolean save(String stage) {
    return prefs.edit().putString("manifest", info.toJson()).putString("stage", stage).commit();
  }

  private void verifyCached(int token) {
    state = State.VERIFYING;
    changed();
    downloader.verifyCached(context, apk(), info, result -> {
      if (token != generation) return;
      if (result.success) {
        if (!save("ready")) { fail("无法保存更新进度，请重试。", true); return; }
        state = State.READY;
      } else {
        apk().delete();
        prefs.edit().clear().commit();
        state = State.AVAILABLE;
        message = "已保存的安装包不可用，请重新下载。";
      }
      changed();
    });
  }

  /** Returns a one-time success message only after the installed version reaches the recorded target. */
  public String reconcileInstalledVersion() {
    if (prefs.getString("manifest", "").isEmpty()) return reconcileLegacyUpgrade();
    if (info == null || DeviceDiagnostics.appVersionCode(context) < info.versionCode) return null;
    // Persist both acknowledgements before notifying: returning Home must not repeat success.
    if (!context.getSharedPreferences("still_update_history", Context.MODE_PRIVATE).edit()
        .putInt("seen_version", DeviceDiagnostics.appVersionCode(context)).commit()) return null;
    if (!prefs.edit().clear().commit()) return null;
    ++generation;
    downloader.cancel();
    File dir = StillFileProvider.getUpdateDir(context);
    File[] files = dir.listFiles();
    if (files != null) for (File file : files) {
      if (file.getName().matches("still_update_[0-9]+\\.(apk|tmp)")) file.delete();
    }
    info = null; state = State.IDLE;
    return "已更新至 Still " + DeviceDiagnostics.appVersion(context);
  }

  /** Migration for 0.9.8/manual installers that did not record an upgrade target. */
  private String reconcileLegacyUpgrade() {
    int current = DeviceDiagnostics.appVersionCode(context);
    if (current <= 0) return null;
    SharedPreferences history = context.getSharedPreferences("still_update_history", Context.MODE_PRIVATE);
    int seen = history.getInt("seen_version", 0);
    if (seen == current) return null;
    boolean upgraded = seen > 0 && current > seen;
    if (seen == 0) {
      try {
        android.content.pm.PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        upgraded = installed.firstInstallTime > 0 && installed.lastUpdateTime > installed.firstInstallTime;
      } catch (android.content.pm.PackageManager.NameNotFoundException ignored) { }
    }
    if (!history.edit().putInt("seen_version", current).commit()) return null;
    return upgraded ? "已更新至 Still " + DeviceDiagnostics.appVersion(context) : null;
  }

  /** Called after returning from settings/installer, including process recreation. */
  public boolean returnedFromSystem() {
    String stage = prefs.getString("stage", "");
    if (!"permission".equals(stage) && !"installing".equals(stage)) return false;
    if (!prefs.edit().putString("stage", "ready").commit()) return false;
    state = State.READY;
    message = "permission".equals(stage)
        ? (AppInstaller.canRequestInstall(context) ? "安装权限已开启，可继续安装。" : "尚未允许安装应用，可重新授权后继续。")
        : "尚未确认升级完成，可继续安装；安装包已保留。";
    changed();
    return true;
  }

  public void install(Activity activity) {
    if (busy() || info == null) return;
    final int token = ++generation;
    state = State.VERIFYING;
    changed();
    downloader.verifyCached(context, apk(), info, result -> {
      if (token != generation) return;
      state = State.READY;
      if (!result.success) {
        apk().delete(); prefs.edit().clear().commit();
        fail("安装包校验失败，请重新下载。" + result.message, true);
        return;
      }
      if (activity.isFinishing() || activity.isDestroyed()) { changed(); return; }
      boolean permitted = AppInstaller.canRequestInstall(activity);
      if (!save(permitted ? "installing" : "permission")) {
        message = "无法保存安装进度，请重试。"; changed(); return;
      }
      try {
        if (permitted) {
          state = State.INSTALLING;
          AppInstaller.installApk(activity, apk());
        } else {
          state = State.PERMISSION;
          AppInstaller.requestInstallPermission(activity);
        }
      } catch (RuntimeException e) {
        save("ready"); state = State.READY;
        message = permitted ? "无法打开系统安装程序，可重试或从官网下载。" : "无法打开安装权限设置，请在系统安全设置中允许 Still 安装应用。";
      }
      changed();
    });
  }
}
