package com.jianping.tv.airinstall;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import com.jianping.tv.platform.StillFileProvider;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

/**
 * Lightweight embedded LAN HTTP server for streaming APK uploads directly to TV cache storage.
 * Runs on a dynamic port, enforces 8KB buffer streaming without whole-file memory buffering,
 * checks storage limits, and automatically cleans up temporary files on cancellation.
 */
public final class AirInstallServer {

  public interface Listener {
    void onUploadStarted(String fileName, long totalBytes);

    void onUploadProgress(
        String fileName, long currentBytes, long totalBytes, int percent, long speedBytesPerSec);

    void onUploadCompleted(File apkFile, String fileName);

    void onUploadError(String error);
  }

  private final Context context;
  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final Listener listener;
  private ServerSocket serverSocket;
  private Thread workerThread;
  private volatile boolean running;
  private int port;
  private String localIp;
  private File activeTempFile;

  public AirInstallServer(Context context, Listener listener) {
    this.context = context.getApplicationContext();
    this.listener = listener;
  }

  public synchronized void start() throws IOException {
    if (running) return;
    cleanOldTempFiles();
    localIp = findLocalIpAddress();
    serverSocket = new ServerSocket(0);
    port = serverSocket.getLocalPort();
    running = true;
    workerThread =
        new Thread(
            () -> {
              while (running) {
                try {
                  Socket socket = serverSocket.accept();
                  handleClient(socket);
                } catch (IOException e) {
                  if (!running) break;
                }
              }
            },
            "Still-AirInstallServer");
    workerThread.start();
  }

  public synchronized void stop() {
    running = false;
    if (serverSocket != null) {
      try {
        serverSocket.close();
      } catch (IOException ignored) {
      }
      serverSocket = null;
    }
    if (workerThread != null) {
      workerThread.interrupt();
      workerThread = null;
    }
    cleanActiveTempFile();
  }

  public int getPort() {
    return port;
  }

  public String getLocalIp() {
    return localIp != null ? localIp : "127.0.0.1";
  }

  public String getUrl() {
    return "http://" + getLocalIp() + ":" + getPort() + "/";
  }

  private synchronized void cleanActiveTempFile() {
    if (activeTempFile != null && activeTempFile.exists()) {
      activeTempFile.delete();
      activeTempFile = null;
    }
  }

  public void cleanOldTempFiles() {
    try {
      File dir = StillFileProvider.getUpdateDir(context);
      File[] files = dir.listFiles();
      if (files != null) {
        for (File f : files) {
          if (f.getName().startsWith("air_upload_") && f.getName().endsWith(".tmp")) {
            f.delete();
          }
        }
      }
    } catch (Exception ignored) {
    }
  }

  private void handleClient(Socket socket) {
    new Thread(
            () -> {
              try {
                socket.setSoTimeout(15000);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                ByteArrayOutputStream headerBuffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                int headerEnd = -1;

                while (headerEnd < 0) {
                  int n = in.read(chunk);
                  if (n < 0) break;
                  headerBuffer.write(chunk, 0, n);
                  headerEnd = findHeaderEnd(headerBuffer.toByteArray());
                }

                if (headerEnd < 0) {
                  socket.close();
                  return;
                }

                byte[] raw = headerBuffer.toByteArray();
                String headerText = new String(raw, 0, headerEnd, StandardCharsets.UTF_8);
                String[] lines = headerText.split("\\r?\\n");
                if (lines.length == 0 || lines[0].isEmpty()) {
                  socket.close();
                  return;
                }

                String[] requestParts = lines[0].split(" ");
                if (requestParts.length < 2) {
                  socket.close();
                  return;
                }

                String method = requestParts[0];
                String path = requestParts[1];

                long contentLength = 0;
                String headerFileName = "";
                for (String line : lines) {
                  String lower = line.toLowerCase(Locale.ROOT);
                  if (lower.startsWith("content-length:")) {
                    try {
                      contentLength = Long.parseLong(line.substring(15).trim());
                    } catch (NumberFormatException ignored) {
                    }
                  } else if (lower.startsWith("x-file-name:")) {
                    try {
                      headerFileName = URLDecoder.decode(line.substring(12).trim(), "UTF-8");
                    } catch (Exception ignored) {
                      headerFileName = line.substring(12).trim();
                    }
                  }
                }

                if ("OPTIONS".equalsIgnoreCase(method)) {
                  sendResponse(out, 204, "No Content", "text/plain", new byte[0]);
                  socket.close();
                  return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                  if (path.startsWith("/api/status")) {
                    handleGetStatus(out);
                  } else {
                    byte[] html = buildHtmlPage().getBytes(StandardCharsets.UTF_8);
                    sendResponse(out, 200, "OK", "text/html; charset=UTF-8", html);
                  }
                } else if ("POST".equalsIgnoreCase(method) && path.startsWith("/api/upload")) {
                  String fileName = extractFileName(path, headerFileName);
                  int alreadyRead = raw.length - headerEnd;
                  handlePostUpload(in, out, fileName, contentLength, raw, headerEnd, alreadyRead);
                } else {
                  sendResponse(
                      out,
                      404,
                      "Not Found",
                      "text/plain",
                      "Not Found".getBytes(StandardCharsets.UTF_8));
                }
                socket.close();
              } catch (Exception e) {
                try {
                  socket.close();
                } catch (IOException ignored) {
                }
              }
            })
        .start();
  }

  private int findHeaderEnd(byte[] b) {
    for (int i = 0; i <= b.length - 4; i++) {
      if (b[i] == '\r' && b[i + 1] == '\n' && b[i + 2] == '\r' && b[i + 3] == '\n') {
        return i + 4;
      }
    }
    for (int i = 0; i <= b.length - 2; i++) {
      if (b[i] == '\n' && b[i + 1] == '\n') {
        return i + 2;
      }
    }
    return -1;
  }

  private String extractFileName(String path, String headerFileName) {
    String name = headerFileName;
    int q = path.indexOf("name=");
    if (q > 0) {
      try {
        String sub = path.substring(q + 5);
        int am = sub.indexOf('&');
        if (am > 0) sub = sub.substring(0, am);
        name = URLDecoder.decode(sub, "UTF-8");
      } catch (Exception ignored) {
      }
    }
    if (name == null || name.trim().isEmpty()) {
      name = "app.apk";
    }
    name = new File(name).getName();
    if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
      name += ".apk";
    }
    return name;
  }

  private void handleGetStatus(OutputStream out) throws IOException {
    File updateDir = StillFileProvider.getUpdateDir(context);
    long freeBytes = 0;
    try {
      StatFs stat = new StatFs(updateDir.getAbsolutePath());
      freeBytes = stat.getAvailableBytes();
    } catch (Exception ignored) {
    }

    JSONObject res = new JSONObject();
    try {
      res.put("success", true);
      res.put("freeBytes", freeBytes);
      res.put("freeMb", Math.round(freeBytes / (1024f * 1024f)));
      res.put("deviceName", "Still TV");
    } catch (Exception ignored) {
    }

    byte[] json = res.toString().getBytes(StandardCharsets.UTF_8);
    sendResponse(out, 200, "OK", "application/json; charset=UTF-8", json);
  }

  private void handlePostUpload(
      InputStream in,
      OutputStream out,
      String fileName,
      long contentLength,
      byte[] initialBuffer,
      int headerEnd,
      int initialBodyLength)
      throws IOException {

    File updateDir = StillFileProvider.getUpdateDir(context);
    try {
      StatFs stat = new StatFs(updateDir.getAbsolutePath());
      long freeBytes = stat.getAvailableBytes();
      if (contentLength > 0 && freeBytes > 0 && freeBytes < contentLength * 2L) {
        String err = "{\"success\":false,\"error\":\"电视剩余存储空间不足\"}";
        sendResponse(
            out,
            507,
            "Insufficient Storage",
            "application/json; charset=UTF-8",
            err.getBytes(StandardCharsets.UTF_8));
        return;
      }
    } catch (Exception ignored) {
    }

    File tempFile = new File(updateDir, "air_upload_" + System.currentTimeMillis() + ".apk.tmp");
    synchronized (this) {
      activeTempFile = tempFile;
    }

    notifyStarted(fileName, contentLength);

    FileOutputStream fos = null;
    boolean success = false;
    try {
      fos = new FileOutputStream(tempFile);
      if (initialBodyLength > 0) {
        fos.write(initialBuffer, headerEnd, initialBodyLength);
      }

      long totalWritten = initialBodyLength;
      byte[] chunk = new byte[8192];
      long lastReportTime = System.currentTimeMillis();
      long lastReportBytes = totalWritten;

      while (contentLength <= 0 || totalWritten < contentLength) {
        int readMax = (contentLength > 0)
            ? (int) Math.min(chunk.length, contentLength - totalWritten)
            : chunk.length;
        int n = in.read(chunk, 0, readMax);
        if (n < 0) break;
        fos.write(chunk, 0, n);
        totalWritten += n;

        long now = System.currentTimeMillis();
        if (now - lastReportTime >= 150) {
          long deltaMs = Math.max(1, now - lastReportTime);
          long deltaBytes = totalWritten - lastReportBytes;
          long speed = (deltaBytes * 1000L) / deltaMs;
          int percent = (contentLength > 0)
              ? (int) Math.min(100, Math.round((totalWritten * 100.0) / contentLength))
              : 0;
          notifyProgress(fileName, totalWritten, contentLength, percent, speed);
          lastReportTime = now;
          lastReportBytes = totalWritten;
        }
      }

      fos.flush();
      fos.close();
      fos = null;

      if (contentLength > 0 && totalWritten < contentLength) {
        throw new IOException("上传中断，数据传输不完整");
      }

      File finalApk = new File(updateDir, "air_install_" + System.currentTimeMillis() + ".apk");
      if (tempFile.renameTo(finalApk)) {
        success = true;
        synchronized (this) {
          activeTempFile = null;
        }
        notifyCompleted(finalApk, fileName);
        JSONObject res = new JSONObject();
        try {
          res.put("success", true);
          res.put("fileName", fileName);
          res.put("size", totalWritten);
        } catch (Exception ignored) {
        }
        sendResponse(
            out,
            200,
            "OK",
            "application/json; charset=UTF-8",
            res.toString().getBytes(StandardCharsets.UTF_8));
      } else {
        throw new IOException("重命名临时安装包失败");
      }
    } catch (Exception e) {
      if (fos != null) {
        try {
          fos.close();
        } catch (IOException ignored) {
        }
      }
      if (tempFile.exists()) {
        tempFile.delete();
      }
      synchronized (this) {
        activeTempFile = null;
      }
      notifyError(e.getMessage() != null ? e.getMessage() : "传输失败");
      String err = "{\"success\":false,\"error\":\"" + (e.getMessage() != null ? e.getMessage() : "上传失败") + "\"}";
      sendResponse(
          out,
          500,
          "Internal Server Error",
          "application/json; charset=UTF-8",
          err.getBytes(StandardCharsets.UTF_8));
    }
  }

  private void notifyStarted(String fileName, long totalBytes) {
    mainHandler.post(() -> {
      if (listener != null) listener.onUploadStarted(fileName, totalBytes);
    });
  }

  private void notifyProgress(
      String fileName, long currentBytes, long totalBytes, int percent, long speed) {
    mainHandler.post(() -> {
      if (listener != null) {
        listener.onUploadProgress(fileName, currentBytes, totalBytes, percent, speed);
      }
    });
  }

  private void notifyCompleted(File apkFile, String fileName) {
    mainHandler.post(() -> {
      if (listener != null) listener.onUploadCompleted(apkFile, fileName);
    });
  }

  private void notifyError(String error) {
    mainHandler.post(() -> {
      if (listener != null) listener.onUploadError(error);
    });
  }

  private void sendResponse(
      OutputStream out, int status, String msg, String contentType, byte[] data)
      throws IOException {
    String headers =
        "HTTP/1.1 "
            + status
            + " "
            + msg
            + "\r\n"
            + "Content-Type: "
            + contentType
            + "\r\n"
            + "Content-Length: "
            + data.length
            + "\r\n"
            + "Access-Control-Allow-Origin: *\r\n"
            + "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n"
            + "Access-Control-Allow-Headers: Content-Type, X-File-Name\r\n"
            + "Connection: close\r\n\r\n";
    out.write(headers.getBytes(StandardCharsets.UTF_8));
    if (data.length > 0) {
      out.write(data);
    }
    out.flush();
  }

  private String findLocalIpAddress() {
    try {
      List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
      for (NetworkInterface intf : interfaces) {
        if (!intf.isUp() || intf.isLoopback()) continue;
        for (InetAddress addr : Collections.list(intf.getInetAddresses())) {
          if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
            String ip = addr.getHostAddress();
            if (ip != null && !ip.startsWith("127.")) {
              return ip;
            }
          }
        }
      }
    } catch (Exception ignored) {
    }
    return "127.0.0.1";
  }

  private String buildHtmlPage() {
    return "<!DOCTYPE html>\n"
        + "<html lang=\"zh-CN\">\n"
        + "<head>\n"
        + "  <meta charset=\"UTF-8\">\n"
        + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n"
        + "  <title>Still TV · 隔空安装助手</title>\n"
        + "  <style>\n"
        + "    :root {\n"
        + "      --bg: #0C0D0F;\n"
        + "      --card-bg: #15171B;\n"
        + "      --card-border: rgba(255, 255, 255, 0.08);\n"
        + "      --drop-bg: rgba(255, 255, 255, 0.02);\n"
        + "      --drop-border: rgba(255, 255, 255, 0.15);\n"
        + "      --text-main: #FFFFFF;\n"
        + "      --text-sec: #8A8F9B;\n"
        + "      --text-sub: #5F646F;\n"
        + "      --accent: #0EA5E9;\n"
        + "      --accent-glow: rgba(14, 165, 233, 0.15);\n"
        + "      --btn-bg: #FFFFFF;\n"
        + "      --btn-text: #0D0E11;\n"
        + "      --progress-bg: rgba(255, 255, 255, 0.08);\n"
        + "    }\n"
        + "    @media (prefers-color-scheme: light) {\n"
        + "      :root {\n"
        + "        --bg: #F4F5F7;\n"
        + "        --card-bg: #FFFFFF;\n"
        + "        --card-border: rgba(0, 0, 0, 0.08);\n"
        + "        --drop-bg: rgba(0, 0, 0, 0.015);\n"
        + "        --drop-border: rgba(0, 0, 0, 0.15);\n"
        + "        --text-main: #111827;\n"
        + "        --text-sec: #6B7280;\n"
        + "        --text-sub: #9CA3AF;\n"
        + "        --accent: #0284C7;\n"
        + "        --accent-glow: rgba(2, 132, 199, 0.12);\n"
        + "        --btn-bg: #111827;\n"
        + "        --btn-text: #FFFFFF;\n"
        + "        --progress-bg: rgba(0, 0, 0, 0.06);\n"
        + "      }\n"
        + "    }\n"
        + "    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; margin: 0; padding: 0; }\n"
        + "    body {\n"
        + "      background: var(--bg);\n"
        + "      color: var(--text-main);\n"
        + "      font-family: -apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"PingFang SC\", \"Hiragino Sans GB\", sans-serif;\n"
        + "      min-height: 100vh;\n"
        + "      display: flex;\n"
        + "      flex-direction: column;\n"
        + "      align-items: center;\n"
        + "      justify-content: center;\n"
        + "      padding: 24px 16px;\n"
        + "      -webkit-font-smoothing: antialiased;\n"
        + "    }\n"
        + "    .container {\n"
        + "      width: 100%;\n"
        + "      max-width: 440px;\n"
        + "      background: var(--card-bg);\n"
        + "      border: 1px solid var(--card-border);\n"
        + "      border-radius: 22px;\n"
        + "      padding: 30px 24px 26px;\n"
        + "      box-shadow: 0 16px 40px -10px rgba(0, 0, 0, 0.3);\n"
        + "    }\n"
        + "    .header { margin-bottom: 22px; }\n"
        + "    .brand-row {\n"
        + "      display: flex;\n"
        + "      align-items: center;\n"
        + "      justify-content: space-between;\n"
        + "      margin-bottom: 8px;\n"
        + "    }\n"
        + "    .brand {\n"
        + "      font-size: 11.5px;\n"
        + "      font-weight: 600;\n"
        + "      letter-spacing: 0.06em;\n"
        + "      color: var(--text-sec);\n"
        + "    }\n"
        + "    .conn-pill {\n"
        + "      font-size: 11px;\n"
        + "      font-weight: 500;\n"
        + "      color: #10B981;\n"
        + "      background: rgba(16, 185, 129, 0.1);\n"
        + "      border: 1px solid rgba(16, 185, 129, 0.25);\n"
        + "      padding: 3px 8px;\n"
        + "      border-radius: 12px;\n"
        + "      display: flex;\n"
        + "      align-items: center;\n"
        + "      gap: 4px;\n"
        + "    }\n"
        + "    .conn-dot {\n"
        + "      width: 6px;\n"
        + "      height: 6px;\n"
        + "      background: #10B981;\n"
        + "      border-radius: 50%;\n"
        + "    }\n"
        + "    .title {\n"
        + "      font-size: 21px;\n"
        + "      font-weight: 600;\n"
        + "      letter-spacing: -0.02em;\n"
        + "      color: var(--text-main);\n"
        + "      line-height: 1.3;\n"
        + "    }\n"
        + "    .subtitle {\n"
        + "      font-size: 13px;\n"
        + "      color: var(--text-sec);\n"
        + "      margin-top: 5px;\n"
        + "      line-height: 1.4;\n"
        + "    }\n"
        + "    .dropzone {\n"
        + "      border: 2px dashed var(--drop-border);\n"
        + "      background: var(--drop-bg);\n"
        + "      border-radius: 16px;\n"
        + "      padding: 36px 20px;\n"
        + "      text-align: center;\n"
        + "      cursor: pointer;\n"
        + "      transition: border-color 0.15s, background-color 0.15s, transform 0.1s;\n"
        + "      margin-bottom: 18px;\n"
        + "    }\n"
        + "    .dropzone.dragover {\n"
        + "      border-color: var(--accent);\n"
        + "      background: var(--accent-glow);\n"
        + "      transform: scale(1.01);\n"
        + "    }\n"
        + "    .drop-icon {\n"
        + "      width: 48px;\n"
        + "      height: 48px;\n"
        + "      margin: 0 auto 12px;\n"
        + "      color: var(--accent);\n"
        + "    }\n"
        + "    .drop-primary {\n"
        + "      font-size: 15px;\n"
        + "      font-weight: 600;\n"
        + "      color: var(--text-main);\n"
        + "      margin-bottom: 4px;\n"
        + "    }\n"
        + "    .drop-sub {\n"
        + "      font-size: 12px;\n"
        + "      color: var(--text-sub);\n"
        + "    }\n"
        + "    .progress-card {\n"
        + "      display: none;\n"
        + "      background: var(--drop-bg);\n"
        + "      border: 1px solid var(--card-border);\n"
        + "      border-radius: 14px;\n"
        + "      padding: 18px 16px;\n"
        + "      margin-bottom: 18px;\n"
        + "    }\n"
        + "    .progress-header {\n"
        + "      display: flex;\n"
        + "      justify-content: space-between;\n"
        + "      align-items: center;\n"
        + "      font-size: 13px;\n"
        + "      font-weight: 500;\n"
        + "      margin-bottom: 10px;\n"
        + "    }\n"
        + "    .file-name-label {\n"
        + "      max-width: 220px;\n"
        + "      white-space: nowrap;\n"
        + "      overflow: hidden;\n"
        + "      text-overflow: ellipsis;\n"
        + "      color: var(--text-main);\n"
        + "    }\n"
        + "    .progress-pct {\n"
        + "      color: var(--accent);\n"
        + "      font-weight: 600;\n"
        + "    }\n"
        + "    .progress-bar-track {\n"
        + "      width: 100%;\n"
        + "      height: 8px;\n"
        + "      background: var(--progress-bg);\n"
        + "      border-radius: 4px;\n"
        + "      overflow: hidden;\n"
        + "      margin-bottom: 8px;\n"
        + "    }\n"
        + "    .progress-bar-fill {\n"
        + "      height: 100%;\n"
        + "      width: 0%;\n"
        + "      background: var(--accent);\n"
        + "      border-radius: 4px;\n"
        + "      transition: width 0.15s ease-out;\n"
        + "    }\n"
        + "    .progress-meta {\n"
        + "      display: flex;\n"
        + "      justify-content: space-between;\n"
        + "      font-size: 11.5px;\n"
        + "      color: var(--text-sub);\n"
        + "    }\n"
        + "    .success-card {\n"
        + "      display: none;\n"
        + "      background: rgba(16, 185, 129, 0.08);\n"
        + "      border: 1px solid rgba(16, 185, 129, 0.25);\n"
        + "      border-radius: 14px;\n"
        + "      padding: 22px 18px;\n"
        + "      text-align: center;\n"
        + "      margin-bottom: 18px;\n"
        + "    }\n"
        + "    .success-icon {\n"
        + "      width: 42px;\n"
        + "      height: 42px;\n"
        + "      color: #10B981;\n"
        + "      margin: 0 auto 10px;\n"
        + "    }\n"
        + "    .success-title {\n"
        + "      font-size: 16px;\n"
        + "      font-weight: 600;\n"
        + "      color: #10B981;\n"
        + "      margin-bottom: 6px;\n"
        + "    }\n"
        + "    .success-tip {\n"
        + "      font-size: 13px;\n"
        + "      color: var(--text-main);\n"
        + "      line-height: 1.4;\n"
        + "    }\n"
        + "    .notice-msg {\n"
        + "      display: none;\n"
        + "      font-size: 12.5px;\n"
        + "      padding: 10px 12px;\n"
        + "      border-radius: 8px;\n"
        + "      margin-bottom: 14px;\n"
        + "      line-height: 1.4;\n"
        + "    }\n"
        + "    .notice-msg.error {\n"
        + "      display: block;\n"
        + "      color: #EF4444;\n"
        + "      background: rgba(239, 68, 68, 0.08);\n"
        + "      border: 1px solid rgba(239, 68, 68, 0.2);\n"
        + "    }\n"
        + "    .btn-row {\n"
        + "      display: flex;\n"
        + "      gap: 10px;\n"
        + "    }\n"
        + "    .btn {\n"
        + "      flex: 1;\n"
        + "      height: 44px;\n"
        + "      background: var(--btn-bg);\n"
        + "      color: var(--btn-text);\n"
        + "      border: none;\n"
        + "      border-radius: 11px;\n"
        + "      font-size: 14px;\n"
        + "      font-weight: 600;\n"
        + "      cursor: pointer;\n"
        + "      display: flex;\n"
        + "      align-items: center;\n"
        + "      justify-content: center;\n"
        + "      transition: opacity 0.15s, transform 0.1s;\n"
        + "    }\n"
        + "    .btn:active { opacity: 0.9; transform: scale(0.99); }\n"
        + "    .btn-secondary {\n"
        + "      background: var(--drop-bg);\n"
        + "      color: var(--text-main);\n"
        + "      border: 1px solid var(--card-border);\n"
        + "    }\n"
        + "    .footer-note {\n"
        + "      font-size: 11.5px;\n"
        + "      color: var(--text-sub);\n"
        + "      text-align: center;\n"
        + "      margin-top: 14px;\n"
        + "    }\n"
        + "  </style>\n"
        + "</head>\n"
        + "<body>\n"
        + "  <div class=\"container\">\n"
        + "    <div class=\"header\">\n"
        + "      <div class=\"brand-row\">\n"
        + "        <div class=\"brand\">Still TV</div>\n"
        + "        <div class=\"conn-pill\"><span class=\"conn-dot\"></span>已连接电视</div>\n"
        + "      </div>\n"
        + "      <h1 class=\"title\">隔空安装助手</h1>\n"
        + "      <p class=\"subtitle\">免插 U 盘，将 Android 安装包无线传输到电视并安装</p>\n"
        + "    </div>\n"
        + "    <div id=\"noticeBox\" class=\"notice-msg\"></div>\n"
        + "    <div id=\"dropzone\" class=\"dropzone\" onclick=\"document.getElementById('fileInput').click()\">\n"
        + "      <svg class=\"drop-icon\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.8\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n"
        + "        <path d=\"M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4\"></path>\n"
        + "        <polyline points=\"17 8 12 3 7 8\"></polyline>\n"
        + "        <line x1=\"12\" y1=\"3\" x2=\"12\" y2=\"15\"></line>\n"
        + "      </svg>\n"
        + "      <div class=\"drop-primary\">选择或拖拽 APK 安装包</div>\n"
        + "      <div class=\"drop-sub\">手机点击直接选择 · 电脑直接拖拽文件</div>\n"
        + "      <input type=\"file\" id=\"fileInput\" accept=\".apk,application/vnd.android.package-archive\" style=\"display:none\">\n"
        + "    </div>\n"
        + "    <div id=\"progressCard\" class=\"progress-card\">\n"
        + "      <div class=\"progress-header\">\n"
        + "        <span id=\"progFileName\" class=\"file-name-label\">应用包.apk</span>\n"
        + "        <span id=\"progPct\" class=\"progress-pct\">0%</span>\n"
        + "      </div>\n"
        + "      <div class=\"progress-bar-track\">\n"
        + "        <div id=\"progFill\" class=\"progress-bar-fill\"></div>\n"
        + "      </div>\n"
        + "      <div class=\"progress-meta\">\n"
        + "        <span id=\"progSpeed\">0 MB/s</span>\n"
        + "        <span id=\"progTransferred\">0 MB / 0 MB</span>\n"
        + "      </div>\n"
        + "    </div>\n"
        + "    <div id=\"successCard\" class=\"success-card\">\n"
        + "      <svg class=\"success-icon\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\">\n"
        + "        <path d=\"M22 11.08V12a10 10 0 1 1-5.93-9.14\"></path>\n"
        + "        <polyline points=\"22 4 12 14.01 9 11.01\"></polyline>\n"
        + "      </svg>\n"
        + "      <div class=\"success-title\">安装包已送达电视！</div>\n"
        + "      <div class=\"success-tip\">请拿起电视遥控器，在电视屏幕上点击「立即安装」完成操作。</div>\n"
        + "    </div>\n"
        + "    <div id=\"actionButtons\" class=\"btn-row\" style=\"display:none;\">\n"
        + "      <button type=\"button\" class=\"btn btn-secondary\" onclick=\"resetUpload()\">发送另一个安装包</button>\n"
        + "    </div>\n"
        + "    <div class=\"footer-note\">传输全程流式直存 · 电视端用完即毁零后台</div>\n"
        + "  </div>\n"
        + "  <script>\n"
        + "    const dropzone = document.getElementById('dropzone');\n"
        + "    const fileInput = document.getElementById('fileInput');\n"
        + "    const noticeBox = document.getElementById('noticeBox');\n"
        + "    const progressCard = document.getElementById('progressCard');\n"
        + "    const successCard = document.getElementById('successCard');\n"
        + "    const actionButtons = document.getElementById('actionButtons');\n"
        + "    let activeXhr = null;\n"
        + "    dropzone.addEventListener('dragover', (e) => {\n"
        + "      e.preventDefault();\n"
        + "      dropzone.classList.add('dragover');\n"
        + "    });\n"
        + "    dropzone.addEventListener('dragleave', () => {\n"
        + "      dropzone.classList.remove('dragover');\n"
        + "    });\n"
        + "    dropzone.addEventListener('drop', (e) => {\n"
        + "      e.preventDefault();\n"
        + "      dropzone.classList.remove('dragover');\n"
        + "      if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {\n"
        + "        handleFile(e.dataTransfer.files[0]);\n"
        + "      }\n"
        + "    });\n"
        + "    fileInput.addEventListener('change', () => {\n"
        + "      if (fileInput.files && fileInput.files.length > 0) {\n"
        + "        handleFile(fileInput.files[0]);\n"
        + "      }\n"
        + "    });\n"
        + "    function showError(msg) {\n"
        + "      noticeBox.className = 'notice-msg error';\n"
        + "      noticeBox.innerText = msg;\n"
        + "    }\n"
        + "    function hideError() {\n"
        + "      noticeBox.className = 'notice-msg';\n"
        + "      noticeBox.innerText = '';\n"
        + "    }\n"
        + "    function handleFile(file) {\n"
        + "      if (!file) return;\n"
        + "      if (!file.name.toLowerCase().endsWith('.apk')) {\n"
        + "        showError('请选择 Android 安装包文件 (.apk)');\n"
        + "        return;\n"
        + "      }\n"
        + "      hideError();\n"
        + "      startUpload(file);\n"
        + "    }\n"
        + "    function startUpload(file) {\n"
        + "      dropzone.style.display = 'none';\n"
        + "      progressCard.style.display = 'block';\n"
        + "      successCard.style.display = 'none';\n"
        + "      actionButtons.style.display = 'none';\n"
        + "      document.getElementById('progFileName').innerText = file.name;\n"
        + "      const totalMb = (file.size / (1024 * 1024)).toFixed(1);\n"
        + "      let lastTime = Date.now();\n"
        + "      let lastLoaded = 0;\n"
        + "      activeXhr = new XMLHttpRequest();\n"
        + "      activeXhr.open('POST', '/api/upload?name=' + encodeURIComponent(file.name), true);\n"
        + "      activeXhr.setRequestHeader('X-File-Name', encodeURIComponent(file.name));\n"
        + "      activeXhr.upload.onprogress = (e) => {\n"
        + "        if (e.lengthComputable) {\n"
        + "          const pct = Math.round((e.loaded / e.total) * 100);\n"
        + "          document.getElementById('progFill').style.width = pct + '%';\n"
        + "          document.getElementById('progPct').innerText = pct + '%';\n"
        + "          const loadedMb = (e.loaded / (1024 * 1024)).toFixed(1);\n"
        + "          document.getElementById('progTransferred').innerText = loadedMb + ' MB / ' + totalMb + ' MB';\n"
        + "          const now = Date.now();\n"
        + "          const dt = (now - lastTime) / 1000;\n"
        + "          if (dt >= 0.3) {\n"
        + "            const speedMb = ((e.loaded - lastLoaded) / (1024 * 1024) / dt).toFixed(1);\n"
        + "            document.getElementById('progSpeed').innerText = speedMb + ' MB/s';\n"
        + "            lastTime = now;\n"
        + "            lastLoaded = e.loaded;\n"
        + "          }\n"
        + "        }\n"
        + "      };\n"
        + "      activeXhr.onload = () => {\n"
        + "        if (activeXhr.status >= 200 && activeXhr.status < 300) {\n"
        + "          progressCard.style.display = 'none';\n"
        + "          successCard.style.display = 'block';\n"
        + "          actionButtons.style.display = 'flex';\n"
        + "        } else {\n"
        + "          let errMsg = '上传失败';\n"
        + "          try {\n"
        + "            const res = JSON.parse(activeXhr.responseText);\n"
        + "            if (res.error) errMsg = res.error;\n"
        + "          } catch (_) {}\n"
        + "          showError('上传失败：' + errMsg);\n"
        + "          resetUpload();\n"
        + "        }\n"
        + "      };\n"
        + "      activeXhr.onerror = () => {\n"
        + "        showError('网络连接异常，请确认手机与电视处于同一局域网');\n"
        + "        resetUpload();\n"
        + "      };\n"
        + "      activeXhr.send(file);\n"
        + "    }\n"
        + "    function resetUpload() {\n"
        + "      if (activeXhr) {\n"
        + "        try { activeXhr.abort(); } catch (_) {}\n"
        + "        activeXhr = null;\n"
        + "      }\n"
        + "      fileInput.value = '';\n"
        + "      dropzone.style.display = 'block';\n"
        + "      progressCard.style.display = 'none';\n"
        + "      successCard.style.display = 'none';\n"
        + "      actionButtons.style.display = 'none';\n"
        + "    }\n"
        + "  </script>\n"
        + "</body>\n"
        + "</html>\n";
  }
}
