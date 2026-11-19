package com.jianping.tv.platform;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/**
 * Handles HTTPS manifest fetching and APK streaming download, enforces size and storage
 * boundaries, reports real-time progress, and executes post-download verification.
 */
public final class UpdateDownloader {
  public interface CheckCallback {
    void onCheckSuccess(AppUpdate.UpdateInfo info);

    void onCheckError(String message);
  }

  public interface DownloadCallback {
    void onProgress(int percent, long currentBytes, long totalBytes);

    void onSuccess(File apkFile);

    void onError(String message);

    void onCancelled();
  }

  private static final ExecutorService EXECUTOR =
      Executors.newSingleThreadExecutor(
          r -> {
            Thread t = new Thread(r, "StillUpdateThread");
            t.setDaemon(true);
            return t;
          });

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private volatile boolean cancelled = false;
  private volatile HttpURLConnection activeConnection = null;

  public void cancel() {
    cancelled = true;
    HttpURLConnection conn = activeConnection;
    if (conn != null) {
      new Thread(conn::disconnect).start();
    }
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void checkUpdate(String manifestUrl, CheckCallback callback) {
    cancelled = false;
    EXECUTOR.execute(
        () -> {
          if (!AppUpdate.isHttpsUrl(manifestUrl)) {
            post(() -> callback.onCheckError("更新地址非安全 HTTPS 协议，已拒绝连接"));
            return;
          }
          HttpURLConnection conn = null;
          try {
            URL url = new URL(manifestUrl);
            conn = (HttpURLConnection) url.openConnection();
            activeConnection = conn;
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(AppUpdate.CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(AppUpdate.READ_TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
              post(() -> callback.onCheckError("更新服务器响应异常 (HTTP " + code + ")"));
              return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
              byte[] buffer = new byte[4096];
              int len;
              int total = 0;
              while ((len = in.read(buffer)) != -1) {
                if (cancelled) {
                  post(() -> callback.onCheckError("检查更新已取消"));
                  return;
                }
                total += len;
                if (total > 64 * 1024) {
                  post(() -> callback.onCheckError("更新配置信息超出大小限制"));
                  return;
                }
                baos.write(buffer, 0, len);
              }
            }
            String json = new String(baos.toByteArray(), StandardCharsets.UTF_8);
            AppUpdate.UpdateInfo info = AppUpdate.UpdateInfo.fromJson(json);
            post(() -> callback.onCheckSuccess(info));
          } catch (Exception e) {
            if (cancelled) {
              post(() -> callback.onCheckError("检查更新已取消"));
            } else {
              post(() -> callback.onCheckError("无法获取更新信息: " + safeErrorMessage(e)));
            }
          } finally {
            activeConnection = null;
            if (conn != null) conn.disconnect();
          }
        });
  }

  public void downloadApk(
      Context context,
      AppUpdate.UpdateInfo info,
      File targetDir,
      DownloadCallback callback) {
    cancelled = false;
    EXECUTOR.execute(
        () -> {
          if (!AppUpdate.isHttpsUrl(info.downloadUrl)) {
            post(() -> callback.onError("安装包下载地址非 HTTPS，已拒绝下载"));
            return;
          }

          if (!AppUpdate.hasEnoughSpace(targetDir, info.size)) {
            post(() -> callback.onError("存储空间不足，无法下载新版本"));
            return;
          }

          File tempFile = new File(targetDir, AppUpdate.getUpdateTmpFileName(info.versionCode));
          File finalApk = new File(targetDir, AppUpdate.getUpdateApkFileName(info.versionCode));

          if (tempFile.exists()) {
            tempFile.delete();
          }

          HttpURLConnection conn = null;
          FileOutputStream fos = null;
          InputStream in = null;
          try {
            URL url = new URL(info.downloadUrl);
            conn = (HttpURLConnection) url.openConnection();
            activeConnection = conn;
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(AppUpdate.CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(AppUpdate.READ_TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept-Encoding", "identity");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
              post(() -> callback.onError("下载服务器响应异常 (HTTP " + code + ")"));
              return;
            }

            long totalBytes;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
              totalBytes = conn.getContentLengthLong();
            } else {
              String header = conn.getHeaderField("Content-Length");
              try {
                totalBytes = header != null ? Long.parseLong(header) : conn.getContentLength();
              } catch (NumberFormatException e) {
                totalBytes = conn.getContentLength();
              }
            }
            if (totalBytes <= 0 && info.size > 0) {
              totalBytes = info.size;
            }
            if (totalBytes > AppUpdate.MAX_APK_SIZE) {
              post(() -> callback.onError("安装包文件体积超出安全限制 (50MB)"));
              return;
            }

            in = conn.getInputStream();
            fos = new FileOutputStream(tempFile);
            byte[] buffer = new byte[8192];
            long currentBytes = 0;
            long lastNotifyTime = 0;
            int lastPercent = -1;
            int read;

            while ((read = in.read(buffer)) != -1) {
              if (cancelled) {
                cleanup(tempFile);
                post(callback::onCancelled);
                return;
              }
              fos.write(buffer, 0, read);
              currentBytes += read;

              if (currentBytes > AppUpdate.MAX_APK_SIZE) {
                cleanup(tempFile);
                post(() -> callback.onError("已下载数据超出最大限制，已终止"));
                return;
              }

              int percent = totalBytes > 0 ? (int) (currentBytes * 100 / totalBytes) : 0;
              long now = System.currentTimeMillis();
              if (percent != lastPercent && (now - lastNotifyTime >= 100 || percent == 100)) {
                lastPercent = percent;
                lastNotifyTime = now;
                long finalCurrent = currentBytes;
                long finalTotal = totalBytes;
                post(() -> callback.onProgress(percent, finalCurrent, finalTotal));
              }
            }
            fos.flush();
            fos.close();
            fos = null;

            if (cancelled) {
              cleanup(tempFile);
              post(callback::onCancelled);
              return;
            }

            // 1. Verify SHA-256 before renaming
            String actualSha256 = AppUpdate.calculateSha256(tempFile);
            if (!actualSha256.equalsIgnoreCase(info.sha256)) {
              cleanup(tempFile);
              post(() -> callback.onError("安装包哈希校验失败 (SHA-256 不匹配)，已放弃损坏文件"));
              return;
            }

            // 2. Atomic rename to .apk
            if (finalApk.exists()) {
              finalApk.delete();
            }
            if (!tempFile.renameTo(finalApk)) {
              cleanup(tempFile);
              post(() -> callback.onError("保存安装包文件失败"));
              return;
            }

            // 3. Verify APK package archive and signatures
            AppUpdate.VerificationResult verifyResult =
                AppUpdate.verifyApk(context, finalApk, info);
            if (!verifyResult.success) {
              cleanup(finalApk);
              post(() -> callback.onError("安装包安全核验失败: " + verifyResult.message));
              return;
            }

            // All checks passed!
            post(() -> callback.onSuccess(finalApk));
          } catch (Exception e) {
            cleanup(tempFile);
            if (cancelled) {
              post(callback::onCancelled);
            } else {
              post(() -> callback.onError("下载安装包失败: " + safeErrorMessage(e)));
            }
          } finally {
            activeConnection = null;
            if (in != null) {
              try {
                in.close();
              } catch (IOException ignored) {
              }
            }
            if (fos != null) {
              try {
                fos.close();
              } catch (IOException ignored) {
              }
            }
            if (conn != null) {
              conn.disconnect();
            }
          }
        });
  }

  private void cleanup(File file) {
    if (file != null && file.exists()) {
      try {
        file.delete();
      } catch (Exception ignored) {
      }
    }
  }

  private void post(Runnable r) {
    mainHandler.post(r);
  }

  private static String safeErrorMessage(Exception e) {
    String msg = e.getMessage();
    if (msg == null || msg.trim().isEmpty()) {
      return e.getClass().getSimpleName();
    }
    return msg;
  }
}
