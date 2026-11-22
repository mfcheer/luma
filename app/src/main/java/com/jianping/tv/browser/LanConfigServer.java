package com.jianping.tv.browser;

import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.JSONObject;

/** Lightweight embedded LAN HTTP server for pairing and receiving web shortcuts from mobile devices. */
public final class LanConfigServer {

  public interface Listener {
    void onConfigReceived(String name, String url);
  }

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final Listener listener;
  private ServerSocket serverSocket;
  private Thread workerThread;
  private volatile boolean running;
  private int port;
  private String localIp;

  public LanConfigServer(Listener listener) {
    this.listener = listener;
  }

  public synchronized void start() throws IOException {
    if (running) return;
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
            "Still-LanConfigServer");
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

  private void handleClient(Socket socket) {
    new Thread(
            () -> {
              try {
                socket.setSoTimeout(5000);
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[1024];
                int headerEnd = -1;

                while (headerEnd < 0) {
                  int n = in.read(chunk);
                  if (n < 0) break;
                  buffer.write(chunk, 0, n);
                  headerEnd = findHeaderEnd(buffer.toByteArray());
                }

                if (headerEnd < 0) {
                  socket.close();
                  return;
                }

                byte[] raw = buffer.toByteArray();
                String headerText = new String(raw, 0, headerEnd, StandardCharsets.UTF_8);
                String[] lines = headerText.split("\r?\n");
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

                int contentLength = 0;
                for (String line : lines) {
                  if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
                    try {
                      contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (NumberFormatException ignored) {
                    }
                  }
                }

                if ("OPTIONS".equalsIgnoreCase(method)) {
                  sendResponse(out, 204, "No Content", "text/plain", new byte[0]);
                  socket.close();
                  return;
                }

                if ("GET".equalsIgnoreCase(method)) {
                  byte[] html = buildHtmlPage().getBytes(StandardCharsets.UTF_8);
                  sendResponse(out, 200, "OK", "text/html; charset=UTF-8", html);
                } else if ("POST".equalsIgnoreCase(method) && path.startsWith("/api/add")) {
                  ByteArrayOutputStream bodyBuffer = new ByteArrayOutputStream();
                  int alreadyRead = raw.length - headerEnd;
                  if (alreadyRead > 0) {
                    bodyBuffer.write(raw, headerEnd, alreadyRead);
                  }
                  while (bodyBuffer.size() < contentLength) {
                    int remaining = contentLength - bodyBuffer.size();
                    int n = in.read(chunk, 0, Math.min(chunk.length, remaining));
                    if (n < 0) break;
                    bodyBuffer.write(chunk, 0, n);
                  }
                  String body = new String(bodyBuffer.toByteArray(), StandardCharsets.UTF_8);
                  handlePostAdd(body, out);
                } else {
                  sendResponse(
                      out,
                      404,
                      "Not Found",
                      "text/plain",
                      "Not Found".getBytes(StandardCharsets.UTF_8));
                }
                socket.close();
              } catch (Exception ignored) {
                try {
                  socket.close();
                } catch (IOException ignored2) {
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

  private void handlePostAdd(String body, OutputStream out) throws IOException {
    String name = "";
    String url = "";
    try {
      if (body.startsWith("{")) {
        JSONObject json = new JSONObject(body);
        name = json.optString("name", "");
        url = json.optString("url", "");
      } else {
        // Form url encoded
        String[] pairs = body.split("&");
        for (String pair : pairs) {
          int eq = pair.indexOf('=');
          if (eq > 0) {
            String k = URLDecoder.decode(pair.substring(0, eq), "UTF-8");
            String v = URLDecoder.decode(pair.substring(eq + 1), "UTF-8");
            if ("name".equalsIgnoreCase(k)) name = v;
            else if ("url".equalsIgnoreCase(k)) url = v;
          }
        }
      }
    } catch (Exception ignored) {
    }

    final String finalName = name;
    final String finalUrl = url;

    if (!finalUrl.trim().isEmpty()) {
      mainHandler.post(() -> {
        if (listener != null) {
          listener.onConfigReceived(finalName, finalUrl);
        }
      });
      byte[] resp = "{\"success\":true}".getBytes(StandardCharsets.UTF_8);
      sendResponse(out, 200, "OK", "application/json; charset=UTF-8", resp);
    } else {
      byte[] resp = "{\"success\":false,\"error\":\"URL cannot be empty\"}".getBytes(StandardCharsets.UTF_8);
      sendResponse(out, 400, "Bad Request", "application/json; charset=UTF-8", resp);
    }
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
            + "Access-Control-Allow-Headers: Content-Type\r\n"
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
    String subnet = "192.168.1.";
    if (localIp != null && localIp.contains(".")) {
      int lastDot = localIp.lastIndexOf('.');
      if (lastDot > 0) subnet = localIp.substring(0, lastDot + 1);
    }
    return "<!DOCTYPE html>\n"
        + "<html lang=\"zh-CN\">\n"
        + "<head>\n"
        + "  <meta charset=\"UTF-8\">\n"
        + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no\">\n"
        + "  <title>Still TV · 添加网页快捷方式</title>\n"
        + "  <style>\n"
        + "    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; }\n"
        + "    body { margin: 0; padding: 24px 16px; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #0F172A; color: #F1F5F9; display: flex; justify-content: center; min-height: 100vh; }\n"
        + "    .card { width: 100%; max-width: 440px; background: #1E293B; border-radius: 20px; padding: 24px 20px; box-shadow: 0 10px 25px -5px rgba(0,0,0,0.5); border: 1px solid rgba(255,255,255,0.08); }\n"
        + "    h1 { font-size: 20px; margin: 0 0 4px 0; font-weight: 600; display: flex; align-items: center; gap: 8px; }\n"
        + "    p.desc { font-size: 13px; color: #94A3B8; margin: 0 0 20px 0; }\n"
        + "    label { display: block; font-size: 13px; font-weight: 500; color: #CBD5E1; margin-bottom: 6px; }\n"
        + "    input { width: 100%; padding: 12px 14px; background: #0F172A; border: 1px solid #334155; border-radius: 12px; color: #FFF; font-size: 15px; outline: none; margin-bottom: 16px; transition: border-color 0.2s; }\n"
        + "    input:focus { border-color: #3B82F6; }\n"
        + "    .chips { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 22px; }\n"
        + "    .chip { background: #334155; color: #CBD5E1; border: none; border-radius: 18px; padding: 6px 12px; font-size: 12px; cursor: pointer; transition: background 0.2s; }\n"
        + "    .chip:active { background: #3B82F6; color: #FFF; }\n"
        + "    .btn { width: 100%; padding: 14px; background: #2563EB; border: none; border-radius: 14px; color: #FFF; font-size: 16px; font-weight: 600; cursor: pointer; transition: transform 0.1s, background 0.2s; }\n"
        + "    .btn:active { transform: scale(0.98); background: #1D4ED8; }\n"
        + "    .status { margin-top: 16px; padding: 12px; border-radius: 10px; font-size: 13px; text-align: center; display: none; }\n"
        + "    .status.success { display: block; background: rgba(34,197,94,0.15); color: #4ADE80; border: 1px solid rgba(34,197,94,0.3); }\n"
        + "    .status.error { display: block; background: rgba(239,68,68,0.15); color: #F87171; border: 1px solid rgba(239,68,68,0.3); }\n"
        + "  </style>\n"
        + "</head>\n"
        + "<body>\n"
        + "  <div class=\"card\">\n"
        + "    <h1>📺 Still TV 网页卡片</h1>\n"
        + "    <p class=\"desc\">手机输入并一键推送到电视桌面</p>\n"
        + "    <label for=\"name\">卡片名称</label>\n"
        + "    <input type=\"text\" id=\"name\" placeholder=\"例如：家庭NAS / Alist / 路由器\">\n"
        + "    <label for=\"url\">网页网址</label>\n"
        + "    <input type=\"text\" id=\"url\" placeholder=\"例如：http://" + subnet + "100:5244\">\n"
        + "    <label>快捷模板</label>\n"
        + "    <div class=\"chips\">\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('路由器后台', 'http://192.168.1.1')\">路由器 (192.168.1.1)</button>\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('Alist 网盘', 'http://" + subnet + "100:5244')\">Alist (:5244)</button>\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('群晖 DSM', 'http://" + subnet + "100:5000')\">群晖 (:5000)</button>\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('威联通 QNAP', 'http://" + subnet + "100:8080')\">威联通 (:8080)</button>\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('百度', 'https://www.baidu.com')\">百度</button>\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('哔哩哔哩', 'https://www.bilibili.com')\">哔哩哔哩</button>\n"
        + "    </div>\n"
        + "    <button class=\"btn\" id=\"sendBtn\" type=\"button\" onclick=\"sendToTv()\">🚀 发送到电视</button>\n"
        + "    <div class=\"status\" id=\"statusBox\"></div>\n"
        + "  </div>\n"
        + "  <script>\n"
        + "    function setPreset(n, u) {\n"
        + "      document.getElementById('name').value = n;\n"
        + "      document.getElementById('url').value = u;\n"
        + "    }\n"
        + "    async function sendToTv() {\n"
        + "      const name = document.getElementById('name').value.trim();\n"
        + "      const url = document.getElementById('url').value.trim();\n"
        + "      const box = document.getElementById('statusBox');\n"
        + "      if (!url) {\n"
        + "        box.className = 'status error';\n"
        + "        box.innerText = '请填写网页网址';\n"
        + "        return;\n"
        + "      }\n"
        + "      box.className = 'status';\n"
        + "      box.style.display = 'block';\n"
        + "      box.innerText = '正在发送...';\n"
        + "      try {\n"
        + "        const resp = await fetch('/api/add', {\n"
        + "          method: 'POST',\n"
        + "          headers: { 'Content-Type': 'application/json' },\n"
        + "          body: JSON.stringify({ name: name || url, url: url })\n"
        + "        });\n"
        + "        const data = await resp.json();\n"
        + "        if (data.success) {\n"
        + "          box.className = 'status success';\n"
        + "          box.innerText = '✅ 已成功发送到电视！可在电视桌面查看';\n"
        + "        } else {\n"
        + "          box.className = 'status error';\n"
        + "          box.innerText = '发送失败：' + (data.error || '未知错误');\n"
        + "        }\n"
        + "      } catch (err) {\n"
        + "        box.className = 'status error';\n"
        + "        box.innerText = '网络连接失败，请确保手机与电视在同一 Wi-Fi';\n"
        + "      }\n"
        + "    }\n"
        + "  </script>\n"
        + "</body>\n"
        + "</html>\n";
  }
}
