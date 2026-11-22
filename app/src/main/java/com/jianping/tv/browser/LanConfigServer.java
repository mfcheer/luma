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
        + "  <title>Still TV · 网页快捷方式</title>\n"
        + "  <style>\n"
        + "    :root {\n"
        + "      --bg: #0C0D0F;\n"
        + "      --card-bg: #15171B;\n"
        + "      --card-border: rgba(255, 255, 255, 0.08);\n"
        + "      --input-bg: #1C1F25;\n"
        + "      --input-border: rgba(255, 255, 255, 0.12);\n"
        + "      --input-focus: #FFFFFF;\n"
        + "      --text-main: #FFFFFF;\n"
        + "      --text-sec: #8A8F9B;\n"
        + "      --text-sub: #5F646F;\n"
        + "      --chip-bg: rgba(255, 255, 255, 0.04);\n"
        + "      --chip-border: rgba(255, 255, 255, 0.08);\n"
        + "      --chip-hover: rgba(255, 255, 255, 0.12);\n"
        + "      --btn-bg: #FFFFFF;\n"
        + "      --btn-text: #0D0E11;\n"
        + "    }\n"
        + "    @media (prefers-color-scheme: light) {\n"
        + "      :root {\n"
        + "        --bg: #F4F5F7;\n"
        + "        --card-bg: #FFFFFF;\n"
        + "        --card-border: rgba(0, 0, 0, 0.08);\n"
        + "        --input-bg: #F8F9FA;\n"
        + "        --input-border: rgba(0, 0, 0, 0.12);\n"
        + "        --input-focus: #111827;\n"
        + "        --text-main: #111827;\n"
        + "        --text-sec: #6B7280;\n"
        + "        --text-sub: #9CA3AF;\n"
        + "        --chip-bg: rgba(0, 0, 0, 0.04);\n"
        + "        --chip-border: rgba(0, 0, 0, 0.08);\n"
        + "        --chip-hover: rgba(0, 0, 0, 0.08);\n"
        + "        --btn-bg: #111827;\n"
        + "        --btn-text: #FFFFFF;\n"
        + "      }\n"
        + "    }\n"
        + "    * { box-sizing: border-box; -webkit-tap-highlight-color: transparent; margin: 0; padding: 0; }\n"
        + "    body {\n"
        + "      background: var(--bg);\n"
        + "      color: var(--text-main);\n"
        + "      font-family: -apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"PingFang SC\", \"Hiragino Sans GB\", \"Segoe UI\", Roboto, sans-serif;\n"
        + "      min-height: 100vh;\n"
        + "      display: flex;\n"
        + "      flex-direction: column;\n"
        + "      align-items: center;\n"
        + "      justify-content: center;\n"
        + "      padding: 32px 16px;\n"
        + "      -webkit-font-smoothing: antialiased;\n"
        + "    }\n"
        + "    .container {\n"
        + "      width: 100%;\n"
        + "      max-width: 400px;\n"
        + "      background: var(--card-bg);\n"
        + "      border: 1px solid var(--card-border);\n"
        + "      border-radius: 20px;\n"
        + "      padding: 28px 24px 24px;\n"
        + "      box-shadow: 0 12px 32px -8px rgba(0, 0, 0, 0.25);\n"
        + "    }\n"
        + "    .header { margin-bottom: 24px; }\n"
        + "    .brand {\n"
        + "      font-size: 11px;\n"
        + "      font-weight: 600;\n"
        + "      letter-spacing: 0.08em;\n"
        + "      color: var(--text-sec);\n"
        + "      margin-bottom: 6px;\n"
        + "    }\n"
        + "    .title {\n"
        + "      font-size: 19px;\n"
        + "      font-weight: 600;\n"
        + "      letter-spacing: -0.02em;\n"
        + "      color: var(--text-main);\n"
        + "      line-height: 1.3;\n"
        + "    }\n"
        + "    .subtitle {\n"
        + "      font-size: 13px;\n"
        + "      color: var(--text-sec);\n"
        + "      margin-top: 4px;\n"
        + "      line-height: 1.4;\n"
        + "    }\n"
        + "    .field { margin-bottom: 16px; }\n"
        + "    .field-label {\n"
        + "      display: flex;\n"
        + "      justify-content: space-between;\n"
        + "      align-items: center;\n"
        + "      font-size: 12px;\n"
        + "      font-weight: 500;\n"
        + "      color: var(--text-sec);\n"
        + "      margin-bottom: 6px;\n"
        + "    }\n"
        + "    .field-label .opt {\n"
        + "      font-size: 11px;\n"
        + "      color: var(--text-sub);\n"
        + "      font-weight: 400;\n"
        + "    }\n"
        + "    .input {\n"
        + "      width: 100%;\n"
        + "      height: 42px;\n"
        + "      background: var(--input-bg);\n"
        + "      border: 1px solid var(--input-border);\n"
        + "      border-radius: 10px;\n"
        + "      color: var(--text-main);\n"
        + "      font-size: 14px;\n"
        + "      padding: 0 12px;\n"
        + "      outline: none;\n"
        + "      transition: border-color 0.15s ease, box-shadow 0.15s ease;\n"
        + "    }\n"
        + "    .input::placeholder { color: var(--text-sub); }\n"
        + "    .input:focus { border-color: var(--input-focus); }\n"
        + "    .presets-header {\n"
        + "      font-size: 12px;\n"
        + "      font-weight: 500;\n"
        + "      color: var(--text-sec);\n"
        + "      margin: 18px 0 8px;\n"
        + "    }\n"
        + "    .chips {\n"
        + "      display: flex;\n"
        + "      flex-wrap: wrap;\n"
        + "      gap: 6px;\n"
        + "      margin-bottom: 24px;\n"
        + "    }\n"
        + "    .chip {\n"
        + "      background: var(--chip-bg);\n"
        + "      border: 1px solid var(--chip-border);\n"
        + "      color: var(--text-sec);\n"
        + "      border-radius: 8px;\n"
        + "      padding: 6px 10px;\n"
        + "      font-size: 12px;\n"
        + "      font-weight: 400;\n"
        + "      cursor: pointer;\n"
        + "      transition: background 0.15s, color 0.15s, border-color 0.15s;\n"
        + "    }\n"
        + "    .chip:active {\n"
        + "      background: var(--chip-hover);\n"
        + "      color: var(--text-main);\n"
        + "      border-color: var(--input-focus);\n"
        + "    }\n"
        + "    .submit-btn {\n"
        + "      width: 100%;\n"
        + "      height: 44px;\n"
        + "      background: var(--btn-bg);\n"
        + "      color: var(--btn-text);\n"
        + "      border: none;\n"
        + "      border-radius: 11px;\n"
        + "      font-size: 14px;\n"
        + "      font-weight: 600;\n"
        + "      letter-spacing: -0.01em;\n"
        + "      cursor: pointer;\n"
        + "      transition: opacity 0.15s, transform 0.1s;\n"
        + "      display: flex;\n"
        + "      align-items: center;\n"
        + "      justify-content: center;\n"
        + "    }\n"
        + "    .submit-btn:active { opacity: 0.9; transform: scale(0.99); }\n"
        + "    .submit-btn:disabled { opacity: 0.4; cursor: not-allowed; }\n"
        + "    .status-msg {\n"
        + "      margin-top: 14px;\n"
        + "      font-size: 12.5px;\n"
        + "      line-height: 1.4;\n"
        + "      text-align: center;\n"
        + "      padding: 10px 12px;\n"
        + "      border-radius: 8px;\n"
        + "      display: none;\n"
        + "      animation: fadeIn 0.2s ease;\n"
        + "    }\n"
        + "    .status-msg.success {\n"
        + "      display: block;\n"
        + "      color: #34D399;\n"
        + "      background: rgba(52, 211, 153, 0.08);\n"
        + "      border: 1px solid rgba(52, 211, 153, 0.2);\n"
        + "    }\n"
        + "    .status-msg.error {\n"
        + "      display: block;\n"
        + "      color: #F87171;\n"
        + "      background: rgba(248, 113, 113, 0.08);\n"
        + "      border: 1px solid rgba(248, 113, 113, 0.2);\n"
        + "    }\n"
        + "    .status-msg.loading {\n"
        + "      display: block;\n"
        + "      color: var(--text-sec);\n"
        + "      background: var(--chip-bg);\n"
        + "    }\n"
        + "    @keyframes fadeIn {\n"
        + "      from { opacity: 0; transform: translateY(-4px); }\n"
        + "      to { opacity: 1; transform: translateY(0); }\n"
        + "    }\n"
        + "  </style>\n"
        + "</head>\n"
        + "<body>\n"
        + "  <div class=\"container\">\n"
        + "    <div class=\"header\">\n"
        + "      <div class=\"brand\">Still TV</div>\n"
        + "      <h1 class=\"title\">添加网页卡片</h1>\n"
        + "      <p class=\"subtitle\">在电视主桌面创建一个独立全屏网页快捷方式</p>\n"
        + "    </div>\n"
        + "    <div class=\"field\">\n"
        + "      <label class=\"field-label\" for=\"name\">\n"
        + "        <span>名称</span>\n"
        + "        <span class=\"opt\">可选</span>\n"
        + "      </label>\n"
        + "      <input class=\"input\" type=\"text\" id=\"name\" placeholder=\"例如：家庭网盘、路由器\" autocomplete=\"off\" autocorrect=\"off\" autocapitalize=\"off\" spellcheck=\"false\">\n"
        + "    </div>\n"
        + "    <div class=\"field\">\n"
        + "      <label class=\"field-label\" for=\"url\">\n"
        + "        <span>网址</span>\n"
        + "      </label>\n"
        + "      <input class=\"input\" type=\"text\" id=\"url\" placeholder=\"https:// 或局域网地址\" autocomplete=\"off\" autocorrect=\"off\" autocapitalize=\"off\" spellcheck=\"false\">\n"
        + "    </div>\n"
        + "    <div class=\"presets-header\">常用模板</div>\n"
        + "    <div class=\"chips\">\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('路由器后台', 'http://192.168.1.1')\">路由器后台</button>\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('Alist 网盘', 'http://" + subnet + "100:5244')\">Alist :5244</button>\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('群晖 DSM', 'http://" + subnet + "100:5000')\">群晖 :5000</button>\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('威联通 QNAP', 'http://" + subnet + "100:8080')\">威联通 :8080</button>\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('GitHub', 'https://github.com')\">GitHub</button>\n"
        + "      <button class=\"chip\" type=\"button\" onclick=\"setPreset('哔哩哔哩', 'https://www.bilibili.com')\">哔哩哔哩</button>\n"
        + "    </div>\n"
        + "    <button class=\"submit-btn\" id=\"sendBtn\" type=\"button\" onclick=\"sendToTv()\">添加至电视</button>\n"
        + "    <div class=\"status-msg\" id=\"statusBox\"></div>\n"
        + "  </div>\n"
        + "  <script>\n"
        + "    function setPreset(n, u) {\n"
        + "      document.getElementById('name').value = n;\n"
        + "      document.getElementById('url').value = u;\n"
        + "      document.getElementById('url').focus();\n"
        + "    }\n"
        + "    async function sendToTv() {\n"
        + "      const name = document.getElementById('name').value.trim();\n"
        + "      const url = document.getElementById('url').value.trim();\n"
        + "      const box = document.getElementById('statusBox');\n"
        + "      const btn = document.getElementById('sendBtn');\n"
        + "      if (!url) {\n"
        + "        box.className = 'status-msg error';\n"
        + "        box.innerText = '请填写网页网址';\n"
        + "        return;\n"
        + "      }\n"
        + "      btn.disabled = true;\n"
        + "      box.className = 'status-msg loading';\n"
        + "      box.innerText = '正在添加...';\n"
        + "      try {\n"
        + "        const resp = await fetch('/api/add', {\n"
        + "          method: 'POST',\n"
        + "          headers: { 'Content-Type': 'application/json' },\n"
        + "          body: JSON.stringify({ name: name || url, url: url })\n"
        + "        });\n"
        + "        const data = await resp.json();\n"
        + "        if (data.success) {\n"
        + "          box.className = 'status-msg success';\n"
        + "          box.innerText = '已成功添加至电视桌面';\n"
        + "        } else {\n"
        + "          box.className = 'status-msg error';\n"
        + "          box.innerText = '添加失败：' + (data.error || '未知错误');\n"
        + "        }\n"
        + "      } catch (err) {\n"
        + "        box.className = 'status-msg error';\n"
        + "        box.innerText = '网络连接失败，请确认手机与电视处于同一局域网';\n"
        + "      } finally {\n"
        + "        btn.disabled = false;\n"
        + "      }\n"
        + "    }\n"
        + "  </script>\n"
        + "</body>\n"
        + "</html>\n";
  }
}
