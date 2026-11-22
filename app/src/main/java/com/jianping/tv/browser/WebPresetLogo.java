package com.jianping.tv.browser;

import android.graphics.*;
import com.jianping.tv.ui.UiPalette;
import java.net.URI;
import java.util.Locale;

/** Built-in high-definition vector logos for popular web and intranet services on TV. */
public final class WebPresetLogo {

  public enum Preset {
    BAIDU("百度", 0xFF2932E1),
    GITHUB("GitHub", 0xFF24292E),
    BILIBILI("哔哩哔哩", 0xFFFB7299),
    YOUTUBE("YouTube", 0xFFFF0000),
    ALIST("Alist", 0xFF0070F3),
    SYNOLOGY("群晖 DSM", 0xFF1E293B),
    QNAP("威联通 QNAP", 0xFF0084C7),
    ROUTER("路由器后台", 0xFF0D9488),
    GOOGLE("Google", 0xFF4285F4),
    ZHIHU("知乎", 0xFF0066FF),
    DOUBAN("豆瓣", 0xFF007722),
    BING("Bing", 0xFF008373);

    public final String label;
    public final int accent;

    Preset(String label, int accent) {
      this.label = label;
      this.accent = accent;
    }
  }

  private WebPresetLogo() {}

  public static Preset match(String url, String name) {
    String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
    String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
    String host = extractHostOnly(u);

    // Baidu
    if (host.contains("baidu.com") || n.contains("百度") || n.contains("baidu")) {
      return Preset.BAIDU;
    }
    // GitHub (only github.com, not personal pages on *.github.io)
    if ((host.equals("github.com") || host.endsWith(".github.com") || n.equals("github") || n.equals("github主页"))
        && !host.contains("github.io")) {
      return Preset.GITHUB;
    }
    // Bilibili
    if (host.contains("bilibili.com")
        || host.contains("b23.tv")
        || n.contains("哔哩")
        || n.contains("b站")
        || n.contains("bilibili")) {
      return Preset.BILIBILI;
    }
    // YouTube
    if (host.contains("youtube.com")
        || host.contains("youtu.be")
        || n.contains("youtube")
        || n.contains("油管")) {
      return Preset.YOUTUBE;
    }
    // Alist (:5244)
    if (u.contains(":5244") || host.contains("alist") || n.contains("alist")) {
      return Preset.ALIST;
    }
    // Synology / DSM (:5000 / :5001)
    if (u.contains(":5000")
        || u.contains(":5001")
        || n.contains("群晖")
        || n.contains("synology")
        || n.contains("dsm")) {
      return Preset.SYNOLOGY;
    }
    // QNAP / 威联通 (:8080 / :8081)
    if (u.contains(":8080")
        || u.contains(":8081")
        || n.contains("威联通")
        || n.contains("qnap")) {
      return Preset.QNAP;
    }
    // Router / OpenWrt
    if (host.equals("192.168.1.1")
        || host.equals("192.168.31.1")
        || host.equals("192.168.0.1")
        || host.equals("10.0.0.1")
        || host.contains("router")
        || n.contains("路由器")
        || n.contains("openwrt")
        || n.contains("网关")) {
      return Preset.ROUTER;
    }
    // Google
    if (host.contains("google.com")
        || host.contains("google.cn")
        || n.contains("google")
        || n.contains("谷歌")) {
      return Preset.GOOGLE;
    }
    // Zhihu
    if (host.contains("zhihu.com") || n.contains("知乎") || n.contains("zhihu")) {
      return Preset.ZHIHU;
    }
    // Douban
    if (host.contains("douban.com") || n.contains("豆瓣") || n.contains("douban")) {
      return Preset.DOUBAN;
    }
    // Bing
    if (host.contains("bing.com") || n.contains("必应") || n.contains("bing")) {
      return Preset.BING;
    }

    return null;
  }

  public static void draw(Canvas canvas, Preset preset, float cx, float cy, UiPalette palette) {
    if (preset == null) return;
    switch (preset) {
      case BAIDU:
        drawBaidu(canvas, cx, cy);
        break;
      case GITHUB:
        drawGithub(canvas, cx, cy);
        break;
      case BILIBILI:
        drawBilibili(canvas, cx, cy);
        break;
      case YOUTUBE:
        drawYoutube(canvas, cx, cy);
        break;
      case ALIST:
        drawAlist(canvas, cx, cy);
        break;
      case SYNOLOGY:
        drawSynology(canvas, cx, cy);
        break;
      case QNAP:
        drawQnap(canvas, cx, cy);
        break;
      case ROUTER:
        drawRouter(canvas, cx, cy);
        break;
      case GOOGLE:
        drawGoogle(canvas, cx, cy);
        break;
      case ZHIHU:
        drawZhihu(canvas, cx, cy);
        break;
      case DOUBAN:
        drawDouban(canvas, cx, cy);
        break;
      case BING:
        drawBing(canvas, cx, cy);
        break;
    }
  }

  private static void drawBaidu(Canvas canvas, float cx, float cy) {
    // Pure white circle badge
    Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    circle.setColor(0xFFFFFFFF);
    canvas.drawCircle(cx, cy, 46, circle);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x20000000);
    canvas.drawCircle(cx, cy, 46, stroke);

    // Baidu Paw: Palm in Baidu Blue 0xFF2932E1, Signature Red Toe 0xFFE10602
    Paint blue = new Paint(Paint.ANTI_ALIAS_FLAG);
    blue.setColor(0xFF2932E1);
    blue.setStyle(Paint.Style.FILL);

    Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
    red.setColor(0xFFE10602);
    red.setStyle(Paint.Style.FILL);

    // Palm pad (bean/kidney shape)
    Path palm = new Path();
    palm.moveTo(cx - 20, cy + 6);
    palm.cubicTo(cx - 22, cy - 2, cx - 10, cy - 1, cx, cy + 4);
    palm.cubicTo(cx + 10, cy - 1, cx + 22, cy - 2, cx + 20, cy + 6);
    palm.cubicTo(cx + 18, cy + 22, cx + 8, cy + 24, cx, cy + 22);
    palm.cubicTo(cx - 8, cy + 24, cx - 18, cy + 22, cx - 20, cy + 6);
    palm.close();
    canvas.drawPath(palm, blue);

    // 4 Toe pads
    // Toe 1 (far left, blue)
    canvas.save();
    canvas.translate(cx - 22, cy - 10);
    canvas.rotate(-32);
    canvas.drawOval(new RectF(-5.5f, -8.5f, 5.5f, 8.5f), blue);
    canvas.restore();

    // Toe 2 (inner left, signature RED!)
    canvas.save();
    canvas.translate(cx - 8, cy - 20);
    canvas.rotate(-10);
    canvas.drawOval(new RectF(-6f, -10f, 6f, 10f), red);
    canvas.restore();

    // Toe 3 (inner right, blue)
    canvas.save();
    canvas.translate(cx + 8, cy - 20);
    canvas.rotate(10);
    canvas.drawOval(new RectF(-6f, -10f, 6f, 10f), blue);
    canvas.restore();

    // Toe 4 (far right, blue)
    canvas.save();
    canvas.translate(cx + 22, cy - 10);
    canvas.rotate(32);
    canvas.drawOval(new RectF(-5.5f, -8.5f, 5.5f, 8.5f), blue);
    canvas.restore();
  }

  private static void drawGithub(Canvas canvas, float cx, float cy) {
    // Dark GitHub disc
    Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    circle.setColor(0xFF161B22);
    canvas.drawCircle(cx, cy, 46, circle);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x33FFFFFF);
    canvas.drawCircle(cx, cy, 46, stroke);

    // Octocat silhouette in white
    Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    white.setColor(Color.WHITE);
    white.setStyle(Paint.Style.FILL);

    // Head
    canvas.drawCircle(cx, cy - 4, 23, white);

    // Left ear
    Path leftEar = new Path();
    leftEar.moveTo(cx - 16, cy - 15);
    leftEar.lineTo(cx - 24, cy - 32);
    leftEar.lineTo(cx - 7, cy - 25);
    leftEar.close();
    canvas.drawPath(leftEar, white);

    // Right ear
    Path rightEar = new Path();
    rightEar.moveTo(cx + 7, cy - 25);
    rightEar.lineTo(cx + 24, cy - 32);
    rightEar.lineTo(cx + 16, cy - 15);
    rightEar.close();
    canvas.drawPath(rightEar, white);

    // Body
    Path body = new Path();
    body.moveTo(cx - 18, cy + 12);
    body.cubicTo(cx - 22, cy + 22, cx - 18, cy + 30, cx - 15, cy + 32);
    body.lineTo(cx + 15, cy + 32);
    body.cubicTo(cx + 18, cy + 30, cx + 22, cy + 22, cx + 18, cy + 12);
    body.close();
    canvas.drawPath(body, white);
  }

  private static void drawBilibili(Canvas canvas, float cx, float cy) {
    // White circular badge
    Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    circle.setColor(Color.WHITE);
    canvas.drawCircle(cx, cy, 46, circle);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x20000000);
    canvas.drawCircle(cx, cy, 46, stroke);

    Paint pink = new Paint(Paint.ANTI_ALIAS_FLAG);
    pink.setColor(0xFFFB7299);
    pink.setStyle(Paint.Style.FILL);

    // Antennas
    Paint antPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    antPaint.setColor(0xFFFB7299);
    antPaint.setStrokeWidth(4.5f);
    antPaint.setStrokeCap(Paint.Cap.ROUND);
    canvas.drawLine(cx - 12, cy - 15, cx - 21, cy - 28, antPaint);
    canvas.drawLine(cx + 12, cy - 15, cx + 21, cy - 28, antPaint);

    // TV body
    RectF tvRect = new RectF(cx - 30, cy - 15, cx + 30, cy + 21);
    canvas.drawRoundRect(tvRect, 8, 8, pink);

    // Feet
    canvas.drawRoundRect(new RectF(cx - 18, cy + 19, cx - 10, cy + 25), 3, 3, pink);
    canvas.drawRoundRect(new RectF(cx + 10, cy + 19, cx + 18, cy + 25), 3, 3, pink);

    // Inner white screen
    Paint innerScreen = new Paint(Paint.ANTI_ALIAS_FLAG);
    innerScreen.setColor(Color.WHITE);
    canvas.drawRoundRect(new RectF(cx - 24, cy - 9, cx + 24, cy + 15), 5, 5, innerScreen);

    // Eyes
    canvas.drawRoundRect(new RectF(cx - 16, cy - 1, cx - 8, cy + 3), 2, 2, pink);
    canvas.drawRoundRect(new RectF(cx + 8, cy - 1, cx + 16, cy + 3), 2, 2, pink);

    // Smile
    Path mouth = new Path();
    mouth.moveTo(cx - 3, cy + 6);
    mouth.lineTo(cx, cy + 9);
    mouth.lineTo(cx + 3, cy + 6);
    Paint mouthPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    mouthPaint.setStyle(Paint.Style.STROKE);
    mouthPaint.setStrokeWidth(2f);
    mouthPaint.setStrokeCap(Paint.Cap.ROUND);
    mouthPaint.setColor(0xFFFB7299);
    canvas.drawPath(mouth, mouthPaint);
  }

  private static void drawYoutube(Canvas canvas, float cx, float cy) {
    // Red rounded pill
    Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
    red.setColor(0xFFFF0000);
    RectF pill = new RectF(cx - 38, cy - 26, cx + 38, cy + 26);
    canvas.drawRoundRect(pill, 16, 16, red);

    // White play triangle
    Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    white.setColor(Color.WHITE);
    white.setStyle(Paint.Style.FILL);
    Path tri = new Path();
    tri.moveTo(cx - 10, cy - 14);
    tri.lineTo(cx + 14, cy);
    tri.lineTo(cx - 10, cy + 14);
    tri.close();
    canvas.drawPath(tri, white);
  }

  private static void drawAlist(Canvas canvas, float cx, float cy) {
    // Alist Blue circle
    Paint blue = new Paint(Paint.ANTI_ALIAS_FLAG);
    blue.setColor(0xFF0070F3);
    canvas.drawCircle(cx, cy, 46, blue);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x44FFFFFF);
    canvas.drawCircle(cx, cy, 46, stroke);

    // Cloud / Storage symbol in white
    Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    white.setColor(Color.WHITE);

    // Storage server box / cloud
    RectF box = new RectF(cx - 24, cy + 2, cx + 24, cy + 22);
    canvas.drawRoundRect(box, 4, 4, white);

    Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
    dark.setColor(0xFF0070F3);
    canvas.drawCircle(cx - 14, cy + 12, 2.5f, dark);
    canvas.drawLine(cx - 5, cy + 12, cx + 18, cy + 12, dark);

    // Cloud curves
    canvas.drawCircle(cx, cy - 10, 16, white);
    canvas.drawCircle(cx - 14, cy - 4, 11, white);
    canvas.drawCircle(cx + 14, cy - 4, 11, white);
    canvas.drawRect(cx - 14, cy - 4, cx + 14, cy + 5, white);
  }

  private static void drawSynology(Canvas canvas, float cx, float cy) {
    // Navy Slate circle
    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    bg.setColor(0xFF1E293B);
    canvas.drawCircle(cx, cy, 46, bg);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x44FFFFFF);
    canvas.drawCircle(cx, cy, 46, stroke);

    // NAS Chassis
    Paint chassis = new Paint(Paint.ANTI_ALIAS_FLAG);
    chassis.setColor(0xFF334155);
    canvas.drawRoundRect(new RectF(cx - 26, cy - 28, cx + 26, cy + 28), 6, 6, chassis);

    // Drive bays
    Paint bay = new Paint(Paint.ANTI_ALIAS_FLAG);
    bay.setColor(0xFF0F172A);
    canvas.drawRoundRect(new RectF(cx - 20, cy - 21, cx + 20, cy - 3), 3, 3, bay);
    canvas.drawRoundRect(new RectF(cx - 20, cy + 3, cx + 20, cy + 21), 3, 3, bay);

    // Status LEDs
    Paint greenLed = new Paint(Paint.ANTI_ALIAS_FLAG);
    greenLed.setColor(0xFF22C55E);
    canvas.drawCircle(cx + 13, cy - 12, 2.5f, greenLed);
    canvas.drawCircle(cx + 13, cy + 12, 2.5f, greenLed);

    Paint blueLed = new Paint(Paint.ANTI_ALIAS_FLAG);
    blueLed.setColor(0xFF38BDF8);
    canvas.drawCircle(cx - 14, cy - 24, 2f, blueLed);
  }

  private static void drawQnap(Canvas canvas, float cx, float cy) {
    // QNAP Blue circle
    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    bg.setColor(0xFF0284C7);
    canvas.drawCircle(cx, cy, 46, bg);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x44FFFFFF);
    canvas.drawCircle(cx, cy, 46, stroke);

    // 4-bay NAS tower
    Paint chassis = new Paint(Paint.ANTI_ALIAS_FLAG);
    chassis.setColor(0xFF0F172A);
    canvas.drawRoundRect(new RectF(cx - 28, cy - 26, cx + 28, cy + 26), 5, 5, chassis);

    Paint tray = new Paint(Paint.ANTI_ALIAS_FLAG);
    tray.setColor(0xFF1E293B);
    for (int i = 0; i < 4; i++) {
      float left = cx - 23 + i * 12;
      canvas.drawRoundRect(new RectF(left, cy - 18, left + 8, cy + 20), 2, 2, tray);
    }

    Paint led = new Paint(Paint.ANTI_ALIAS_FLAG);
    led.setColor(0xFF22C55E);
    canvas.drawCircle(cx + 20, cy - 20, 2f, led);
  }

  private static void drawRouter(Canvas canvas, float cx, float cy) {
    // Teal circle
    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    bg.setColor(0xFF0D9488);
    canvas.drawCircle(cx, cy, 46, bg);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x44FFFFFF);
    canvas.drawCircle(cx, cy, 46, stroke);

    // Dual antennas
    Paint ant = new Paint(Paint.ANTI_ALIAS_FLAG);
    ant.setColor(0xFFF0FDFA);
    ant.setStrokeWidth(3.5f);
    ant.setStrokeCap(Paint.Cap.ROUND);
    canvas.drawLine(cx - 16, cy - 4, cx - 22, cy - 26, ant);
    canvas.drawLine(cx + 16, cy - 4, cx + 22, cy - 26, ant);

    // Router box
    Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    box.setColor(0xFF134E4A);
    canvas.drawRoundRect(new RectF(cx - 30, cy - 5, cx + 30, cy + 20), 5, 5, box);

    // Wi-Fi signal arcs
    Paint wifi = new Paint(Paint.ANTI_ALIAS_FLAG);
    wifi.setStyle(Paint.Style.STROKE);
    wifi.setStrokeWidth(2.5f);
    wifi.setStrokeCap(Paint.Cap.ROUND);
    wifi.setColor(0xFF5EEAD4);
    canvas.drawArc(new RectF(cx - 10, cy - 20, cx + 10, cy), 200, 140, false, wifi);
    canvas.drawArc(new RectF(cx - 18, cy - 28, cx + 18, cy + 8), 205, 130, false, wifi);

    // LED indicators
    Paint led = new Paint(Paint.ANTI_ALIAS_FLAG);
    led.setColor(0xFF34D399);
    for (int i = 0; i < 4; i++) {
      canvas.drawCircle(cx - 12 + i * 8, cy + 8, 2f, led);
    }
  }

  private static void drawGoogle(Canvas canvas, float cx, float cy) {
    Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    circle.setColor(Color.WHITE);
    canvas.drawCircle(cx, cy, 46, circle);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x20000000);
    canvas.drawCircle(cx, cy, 46, stroke);

    RectF oval = new RectF(cx - 24, cy - 24, cx + 24, cy + 24);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(9f);

    p.setColor(0xFF4285F4); // Blue
    canvas.drawArc(oval, -45, 60, false, p);
    p.setColor(0xFF34A853); // Green
    canvas.drawArc(oval, 15, 105, false, p);
    p.setColor(0xFFFBBC05); // Yellow
    canvas.drawArc(oval, 120, 75, false, p);
    p.setColor(0xFFEA4335); // Red
    canvas.drawArc(oval, 195, 120, false, p);

    // Blue crossbar
    p.setStyle(Paint.Style.FILL);
    p.setColor(0xFF4285F4);
    canvas.drawRect(cx, cy - 4.5f, cx + 22, cy + 4.5f, p);
  }

  private static void drawZhihu(Canvas canvas, float cx, float cy) {
    Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    circle.setColor(0xFF0066FF);
    canvas.drawCircle(cx, cy, 46, circle);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x44FFFFFF);
    canvas.drawCircle(cx, cy, 46, stroke);

    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    text.setTextAlign(Paint.Align.CENTER);
    text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    text.setTextSize(44f);
    text.setColor(Color.WHITE);
    Paint.FontMetrics fm = text.getFontMetrics();
    canvas.drawText("知", cx, cy - (fm.ascent + fm.descent) / 2f, text);
  }

  private static void drawDouban(Canvas canvas, float cx, float cy) {
    Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    circle.setColor(0xFF007722);
    canvas.drawCircle(cx, cy, 46, circle);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x44FFFFFF);
    canvas.drawCircle(cx, cy, 46, stroke);

    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    text.setTextAlign(Paint.Align.CENTER);
    text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    text.setTextSize(44f);
    text.setColor(Color.WHITE);
    Paint.FontMetrics fm = text.getFontMetrics();
    canvas.drawText("豆", cx, cy - (fm.ascent + fm.descent) / 2f, text);
  }

  private static void drawBing(Canvas canvas, float cx, float cy) {
    Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    circle.setColor(0xFF008373);
    canvas.drawCircle(cx, cy, 46, circle);

    Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    stroke.setStyle(Paint.Style.STROKE);
    stroke.setStrokeWidth(2f);
    stroke.setColor(0x44FFFFFF);
    canvas.drawCircle(cx, cy, 46, stroke);

    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    text.setTextAlign(Paint.Align.CENTER);
    text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    text.setTextSize(44f);
    text.setColor(Color.WHITE);
    Paint.FontMetrics fm = text.getFontMetrics();
    canvas.drawText("b", cx, cy - (fm.ascent + fm.descent) / 2f, text);
  }

  private static String extractHostOnly(String url) {
    if (url == null || url.isEmpty()) return "";
    try {
      URI uri = new URI(url);
      String host = uri.getHost();
      if (host != null && !host.isEmpty()) return host.toLowerCase(Locale.ROOT);
    } catch (Exception ignored) {
    }
    String stripped = url.replaceFirst("^https?://", "");
    int slash = stripped.indexOf('/');
    if (slash >= 0) stripped = stripped.substring(0, slash);
    int colon = stripped.indexOf(':');
    if (colon >= 0) stripped = stripped.substring(0, colon);
    return stripped.toLowerCase(Locale.ROOT);
  }
}
