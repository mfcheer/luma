package com.jianping.tv.browser;

import android.graphics.*;
import com.jianping.tv.ui.UiPalette;
import java.net.URI;
import java.util.Locale;

/** Built-in high-definition vector logos for popular web and intranet services on TV. */
public final class WebPresetLogo {

  public enum Preset {
    BAIDU("百度", 0xFF2932E1),
    GITHUB("GitHub", 0xFF181717),
    BILIBILI("哔哩哔哩", 0xFFFB7299),
    YOUTUBE("YouTube", 0xFFFF0000),
    ALIST("Alist", 0xFF0070F3),
    SYNOLOGY("群晖 DSM", 0xFF1E293B),
    QNAP("威联通 QNAP", 0xFF0084C7),
    ROUTER("路由器后台", 0xFF0D9488),
    GOOGLE("Google", 0xFF4285F4),
    ZHIHU("知乎", 0xFF0066FF),
    DOUBAN("豆瓣", 0xFF007722),
    BING("Bing", 0xFF008373),
    STATUS_BOARD("编程状态屏", 0xFF6366F1);

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
    // Programming Status Board / 编程状态屏 (:8791, board, 词元之外)
    if (u.contains(":8791")
        || u.contains("/board")
        || host.contains("board")
        || n.contains("状态屏")
        || n.contains("词元之外")
        || n.contains("board")) {
      return Preset.STATUS_BOARD;
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
      case STATUS_BOARD:
        drawStatusBoard(canvas, cx, cy);
        break;
    }
  }

  private static void drawBaidu(Canvas canvas, float cx, float cy) {
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
    // Official GitHub dark silhouette
    Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
    dark.setColor(0xFF181717);
    dark.setStyle(Paint.Style.FILL);

    // Head
    canvas.drawCircle(cx, cy - 4, 23, dark);

    // Left ear
    Path leftEar = new Path();
    leftEar.moveTo(cx - 16, cy - 15);
    leftEar.lineTo(cx - 24, cy - 32);
    leftEar.lineTo(cx - 7, cy - 25);
    leftEar.close();
    canvas.drawPath(leftEar, dark);

    // Right ear
    Path rightEar = new Path();
    rightEar.moveTo(cx + 7, cy - 25);
    rightEar.lineTo(cx + 24, cy - 32);
    rightEar.lineTo(cx + 16, cy - 15);
    rightEar.close();
    canvas.drawPath(rightEar, dark);

    // Body
    Path body = new Path();
    body.moveTo(cx - 18, cy + 12);
    body.cubicTo(cx - 22, cy + 22, cx - 18, cy + 30, cx - 15, cy + 32);
    body.lineTo(cx + 15, cy + 32);
    body.cubicTo(cx + 18, cy + 30, cx + 22, cy + 22, cx + 18, cy + 12);
    body.close();
    canvas.drawPath(body, dark);
  }

  private static void drawBilibili(Canvas canvas, float cx, float cy) {
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
    // Alist Blue rounded app squircle
    Paint blue = new Paint(Paint.ANTI_ALIAS_FLAG);
    blue.setColor(0xFF0070F3);
    RectF squircle = new RectF(cx - 36, cy - 36, cx + 36, cy + 36);
    canvas.drawRoundRect(squircle, 14, 14, blue);

    // Cloud / Storage symbol in white
    Paint white = new Paint(Paint.ANTI_ALIAS_FLAG);
    white.setColor(Color.WHITE);

    // Storage server box
    RectF box = new RectF(cx - 18, cy + 3, cx + 18, cy + 19);
    canvas.drawRoundRect(box, 3, 3, white);

    Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
    dark.setColor(0xFF0070F3);
    canvas.drawCircle(cx - 11, cy + 11, 2f, dark);
    canvas.drawLine(cx - 4, cy + 11, cx + 13, cy + 11, dark);

    // Cloud curves
    canvas.drawCircle(cx, cy - 8, 12, white);
    canvas.drawCircle(cx - 11, cy - 3, 8.5f, white);
    canvas.drawCircle(cx + 11, cy - 3, 8.5f, white);
    canvas.drawRect(cx - 11, cy - 3, cx + 11, cy + 4, white);
  }

  private static void drawSynology(Canvas canvas, float cx, float cy) {
    // NAS Chassis
    Paint chassis = new Paint(Paint.ANTI_ALIAS_FLAG);
    chassis.setColor(0xFF1E293B);
    canvas.drawRoundRect(new RectF(cx - 30, cy - 32, cx + 30, cy + 32), 6, 6, chassis);

    // Drive bays
    Paint bay = new Paint(Paint.ANTI_ALIAS_FLAG);
    bay.setColor(0xFF0F172A);
    canvas.drawRoundRect(new RectF(cx - 23, cy - 24, cx + 23, cy - 4), 3, 3, bay);
    canvas.drawRoundRect(new RectF(cx - 23, cy + 4, cx + 23, cy + 24), 3, 3, bay);

    // Status LEDs
    Paint greenLed = new Paint(Paint.ANTI_ALIAS_FLAG);
    greenLed.setColor(0xFF22C55E);
    canvas.drawCircle(cx + 15, cy - 14, 2.5f, greenLed);
    canvas.drawCircle(cx + 15, cy + 14, 2.5f, greenLed);

    Paint blueLed = new Paint(Paint.ANTI_ALIAS_FLAG);
    blueLed.setColor(0xFF38BDF8);
    canvas.drawCircle(cx - 17, cy - 28, 2f, blueLed);
  }

  private static void drawQnap(Canvas canvas, float cx, float cy) {
    Paint chassis = new Paint(Paint.ANTI_ALIAS_FLAG);
    chassis.setColor(0xFF0284C7);
    canvas.drawRoundRect(new RectF(cx - 32, cy - 30, cx + 32, cy + 30), 6, 6, chassis);

    Paint tray = new Paint(Paint.ANTI_ALIAS_FLAG);
    tray.setColor(0xFF0F172A);
    for (int i = 0; i < 4; i++) {
      float left = cx - 26 + i * 14;
      canvas.drawRoundRect(new RectF(left, cy - 20, left + 9, cy + 23), 2, 2, tray);
    }

    Paint led = new Paint(Paint.ANTI_ALIAS_FLAG);
    led.setColor(0xFF22C55E);
    canvas.drawCircle(cx + 23, cy - 24, 2f, led);
  }

  private static void drawRouter(Canvas canvas, float cx, float cy) {
    // Dual antennas
    Paint ant = new Paint(Paint.ANTI_ALIAS_FLAG);
    ant.setColor(0xFF0D9488);
    ant.setStrokeWidth(4f);
    ant.setStrokeCap(Paint.Cap.ROUND);
    canvas.drawLine(cx - 18, cy - 4, cx - 25, cy - 28, ant);
    canvas.drawLine(cx + 18, cy - 4, cx + 25, cy - 28, ant);

    // Router box
    Paint box = new Paint(Paint.ANTI_ALIAS_FLAG);
    box.setColor(0xFF0D9488);
    canvas.drawRoundRect(new RectF(cx - 34, cy - 5, cx + 34, cy + 22), 6, 6, box);

    // Wi-Fi signal arcs
    Paint wifi = new Paint(Paint.ANTI_ALIAS_FLAG);
    wifi.setStyle(Paint.Style.STROKE);
    wifi.setStrokeWidth(2.5f);
    wifi.setStrokeCap(Paint.Cap.ROUND);
    wifi.setColor(0xFF0D9488);
    canvas.drawArc(new RectF(cx - 11, cy - 22, cx + 11, cy), 200, 140, false, wifi);
    canvas.drawArc(new RectF(cx - 20, cy - 31, cx + 20, cy + 9), 205, 130, false, wifi);

    // LED indicators
    Paint led = new Paint(Paint.ANTI_ALIAS_FLAG);
    led.setColor(0xFF5EEAD4);
    for (int i = 0; i < 4; i++) {
      canvas.drawCircle(cx - 14 + i * 9, cy + 9, 2.5f, led);
    }
  }

  private static void drawGoogle(Canvas canvas, float cx, float cy) {
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
    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    bg.setColor(0xFF0066FF);
    RectF squircle = new RectF(cx - 36, cy - 36, cx + 36, cy + 36);
    canvas.drawRoundRect(squircle, 14, 14, bg);

    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    text.setTextAlign(Paint.Align.CENTER);
    text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    text.setTextSize(44f);
    text.setColor(Color.WHITE);
    Paint.FontMetrics fm = text.getFontMetrics();
    canvas.drawText("知", cx, cy - (fm.ascent + fm.descent) / 2f, text);
  }

  private static void drawDouban(Canvas canvas, float cx, float cy) {
    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    bg.setColor(0xFF007722);
    RectF squircle = new RectF(cx - 36, cy - 36, cx + 36, cy + 36);
    canvas.drawRoundRect(squircle, 14, 14, bg);

    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    text.setTextAlign(Paint.Align.CENTER);
    text.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    text.setTextSize(44f);
    text.setColor(Color.WHITE);
    Paint.FontMetrics fm = text.getFontMetrics();
    canvas.drawText("豆", cx, cy - (fm.ascent + fm.descent) / 2f, text);
  }

  private static void drawBing(Canvas canvas, float cx, float cy) {
    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    bg.setColor(0xFF008373);
    RectF squircle = new RectF(cx - 36, cy - 36, cx + 36, cy + 36);
    canvas.drawRoundRect(squircle, 14, 14, bg);

    Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    text.setTextAlign(Paint.Align.CENTER);
    text.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    text.setTextSize(44f);
    text.setColor(Color.WHITE);
    Paint.FontMetrics fm = text.getFontMetrics();
    canvas.drawText("b", cx, cy - (fm.ascent + fm.descent) / 2f, text);
  }


  private static void drawStatusBoard(Canvas canvas, float cx, float cy) {
    // 4 AI tracks: Coral Red, Amber Gold, Royal Blue, Mint Green
    Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
    red.setColor(0xFFF43F5E);
    red.setStyle(Paint.Style.STROKE);
    red.setStrokeWidth(9f);
    red.setStrokeCap(Paint.Cap.ROUND);

    Paint amber = new Paint(Paint.ANTI_ALIAS_FLAG);
    amber.setColor(0xFFF59E0B);
    amber.setStyle(Paint.Style.STROKE);
    amber.setStrokeWidth(9f);
    amber.setStrokeCap(Paint.Cap.ROUND);

    Paint blue = new Paint(Paint.ANTI_ALIAS_FLAG);
    blue.setColor(0xFF3B82F6);
    blue.setStyle(Paint.Style.STROKE);
    blue.setStrokeWidth(9f);
    blue.setStrokeCap(Paint.Cap.ROUND);

    Paint green = new Paint(Paint.ANTI_ALIAS_FLAG);
    green.setColor(0xFF10B981);
    green.setStyle(Paint.Style.STROKE);
    green.setStrokeWidth(9f);
    green.setStrokeCap(Paint.Cap.ROUND);

    // Left Infinity Loop (cx - 18, cy - 2)
    // Arc 1: Top-left curve (Red) from center crossing (cx, cy - 2) to (cx - 36, cy - 2)
    Path arcTopLeft = new Path();
    arcTopLeft.moveTo(cx, cy - 2);
    arcTopLeft.cubicTo(cx - 10, cy - 22, cx - 28, cy - 22, cx - 36, cy - 2);
    canvas.drawPath(arcTopLeft, red);

    // Arc 2: Bottom-left curve (Amber) from (cx - 36, cy - 2) to center crossing (cx, cy - 2)
    Path arcBottomLeft = new Path();
    arcBottomLeft.moveTo(cx - 36, cy - 2);
    arcBottomLeft.cubicTo(cx - 28, cy + 18, cx - 10, cy + 18, cx, cy - 2);
    canvas.drawPath(arcBottomLeft, amber);

    // Right Infinity Loop (cx + 18, cy - 2)
    // Arc 3: Top-right curve (Blue) from center crossing (cx, cy - 2) to (cx + 36, cy - 2)
    Path arcTopRight = new Path();
    arcTopRight.moveTo(cx, cy - 2);
    arcTopRight.cubicTo(cx + 10, cy - 22, cx + 28, cy - 22, cx + 36, cy - 2);
    canvas.drawPath(arcTopRight, blue);

    // Arc 4: Bottom-right curve (Green) from (cx + 36, cy - 2) to center crossing (cx, cy - 2)
    Path arcBottomRight = new Path();
    arcBottomRight.moveTo(cx + 36, cy - 2);
    arcBottomRight.cubicTo(cx + 28, cy + 18, cx + 10, cy + 18, cx, cy - 2);
    canvas.drawPath(arcBottomRight, green);

    // Telemetry status line and 4 active agent indicators below
    Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    trackPaint.setColor(0xFFE2E8F0);
    trackPaint.setStyle(Paint.Style.FILL);
    canvas.drawRoundRect(new RectF(cx - 32, cy + 24, cx + 32, cy + 27.5f), 2f, 2f, trackPaint);

    Paint fillRed = new Paint(Paint.ANTI_ALIAS_FLAG);
    fillRed.setColor(0xFFF43F5E);
    fillRed.setStyle(Paint.Style.FILL);

    Paint fillAmber = new Paint(Paint.ANTI_ALIAS_FLAG);
    fillAmber.setColor(0xFFF59E0B);
    fillAmber.setStyle(Paint.Style.FILL);

    Paint fillBlue = new Paint(Paint.ANTI_ALIAS_FLAG);
    fillBlue.setColor(0xFF3B82F6);
    fillBlue.setStyle(Paint.Style.FILL);

    Paint fillGreen = new Paint(Paint.ANTI_ALIAS_FLAG);
    fillGreen.setColor(0xFF10B981);
    fillGreen.setStyle(Paint.Style.FILL);

    // 4 glowing agent telemetry dots
    canvas.drawCircle(cx - 22, cy + 25.75f, 3.5f, fillRed);
    canvas.drawCircle(cx - 7.5f, cy + 25.75f, 3.5f, fillAmber);
    canvas.drawCircle(cx + 7.5f, cy + 25.75f, 3.5f, fillGreen);
    canvas.drawCircle(cx + 22, cy + 25.75f, 3.5f, fillBlue);
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
