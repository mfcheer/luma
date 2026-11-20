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
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;

/**
 * Handles HTTPS manifest fetching and APK streaming download, enforces size and storage
 * boundaries, reports real-time progress, and executes post-download verification.
 */
public class UpdateDownloader {
  public interface CheckCallback {
    void onCheckSuccess(AppUpdate.UpdateInfo info);

    void onCheckError(String message);
  }

  public interface DownloadCallback {
    void onProgress(int percent, long currentBytes, long totalBytes);

    default void onVerifying() {}

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

  HttpURLConnection openConnection(URL url) throws IOException {
    return (HttpURLConnection) url.openConnection();
  }

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

  public interface VerifyCallback {
    void onVerified(AppUpdate.VerificationResult result);
  }

  public void verifyCached(Context context, File file, AppUpdate.UpdateInfo info, VerifyCallback callback) {
    EXECUTOR.execute(() -> {
      AppUpdate.VerificationResult result;
      try {
        result = AppUpdate.verifyApk(context, file, info);
      } catch (RuntimeException e) {
        result = AppUpdate.VerificationResult.failure("无法读取安装包，请重新下载。");
      }
      final AppUpdate.VerificationResult checked = result;
      post(() -> callback.onVerified(checked));
    });
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
            conn = openConnection(url);
            activeConnection = conn;
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(AppUpdate.CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(AppUpdate.READ_TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
              post(() -> callback.onCheckError(serverError(code)));
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
              post(() -> callback.onCheckError(networkError(e)));
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
            post(() -> callback.onError("存储空间不足，请至少留出 " + AppUpdate.formatSize(AppUpdate.requiredSpace(info.size)) + " 后重试。"));
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
            conn = openConnection(url);
            activeConnection = conn;
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(AppUpdate.CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(AppUpdate.READ_TIMEOUT_MS);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Accept-Encoding", "identity");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
              post(() -> callback.onError(serverError(code)));
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

              int percent = totalBytes > 0 ? (int) Math.min(100, currentBytes * 100 / totalBytes) : 0;
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

            post(callback::onVerifying);
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

            if (cancelled) {
              cleanup(finalApk);
              post(callback::onCancelled);
              return;
            }
            post(() -> callback.onSuccess(finalApk));
          } catch (Exception e) {
            cleanup(tempFile);
            if (cancelled) {
              post(callback::onCancelled);
            } else {
              post(() -> callback.onError(networkError(e)));
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

  static String serverError(int code) {
    return "更新服务暂不可用（HTTP " + code + "），请稍后重试，或从官网下载。";
  }

  static String networkError(Exception e) {
    if (e instanceof UnknownHostException || e instanceof ConnectException)
      return "无法连接更新服务，请检查网络后重试。";
    if (e instanceof SocketTimeoutException)
      return "连接或下载超时，请检查网络后重试。";
    if (e instanceof IllegalArgumentException)
      return "更新信息格式异常，请稍后重试，或从官网下载。";
    if (e instanceof javax.net.ssl.SSLException)
      return "无法建立安全连接，请检查系统时间或从官网下载。";
    return "更新未完成，请检查网络和存储空间后重试。";
  }
}
