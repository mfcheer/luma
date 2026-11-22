package com.jianping.tv.browser;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.jianping.tv.ui.ArgbBlur;
import com.jianping.tv.ui.UiPalette;
import java.net.URI;

/** Generates high-resolution TV artwork and blurred preview for web shortcuts and the add card. */
public final class WebArtwork {
  private static final int WIDTH = 384;
  private static final int HEIGHT = 250;
  private static final float RADIUS = 39f;

  private WebArtwork() {}

  public static Drawable cardArtwork(
      Context context, Resources res, WebShortcut shortcut, UiPalette palette) {
    Bitmap bmp = createShortcutBitmap(context, shortcut, palette);
    return new BitmapDrawable(res, bmp);
  }

  public static Drawable previewArtwork(
      Context context, Resources res, WebShortcut shortcut, UiPalette palette) {
    Bitmap bmp = createShortcutBitmap(context, shortcut, palette);
    Bitmap blurred = blur(bmp);
    bmp.recycle();
    return new BitmapDrawable(res, blurred);
  }

  public static Drawable cardArtwork(Resources res, WebShortcut shortcut, UiPalette palette) {
    return cardArtwork(null, res, shortcut, palette);
  }

  public static Drawable previewArtwork(Resources res, WebShortcut shortcut, UiPalette palette) {
    return previewArtwork(null, res, shortcut, palette);
  }

  public static Drawable addCardArtwork(Resources res, UiPalette palette) {
    Bitmap bmp = createAddCardBitmap(palette);
    return new BitmapDrawable(res, bmp);
  }

  public static Drawable addPreviewArtwork(Resources res, UiPalette palette) {
    Bitmap bmp = createAddCardBitmap(palette);
    Bitmap blurred = blur(bmp);
    bmp.recycle();
    return new BitmapDrawable(res, blurred);
  }

  private static Bitmap createShortcutBitmap(
      Context context, WebShortcut shortcut, UiPalette palette) {
    Bitmap bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    Path clip = new Path();
    clip.addRoundRect(new RectF(0, 0, WIDTH, HEIGHT), RADIUS, RADIUS, Path.Direction.CW);
    canvas.clipPath(clip);

    WebPresetLogo.Preset preset = WebPresetLogo.match(shortcut.url, shortcut.name);
    int accent = preset != null ? preset.accent : shortcut.accent;

    int top = palette.light ? blend(accent, 0xFFFAFBFD, 0.12f) : blend(accent, 0xFF2A3649, 0.35f);
    int bottom =
        palette.light ? blend(accent, 0xFFE2E8F0, 0.25f) : blend(accent, 0xFF141C26, 0.20f);

    Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    bgPaint.setShader(
        new LinearGradient(0, 0, 0, HEIGHT, top, bottom, Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, WIDTH, HEIGHT, bgPaint);

    // Subtle decorative glow behind badge
    Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    glow.setColor(accent);
    glow.setAlpha(palette.light ? 25 : 55);
    canvas.drawCircle(WIDTH / 2f, 105, 70, glow);

    float cx = WIDTH / 2f;
    float cy = 105f;

    Bitmap customIcon = WebIconManager.getIcon(context, shortcut.id);
    if (customIcon != null && !customIcon.isRecycled()) {
      // 1. Custom downloaded or captured Favicon / Touch Icon
      drawCustomIconBadge(canvas, customIcon, cx, cy, palette);
    } else if (preset != null) {
      // 2. Built-in high-definition vector logo
      WebPresetLogo.draw(canvas, preset, cx, cy, palette);
    } else {
      // 3. Elegant initial glyph fallback
      drawGlyphBadge(canvas, shortcut, cx, cy, accent, palette);
    }

    // Top-right "Web" badge (clean title case)
    Paint webBadge = new Paint(Paint.ANTI_ALIAS_FLAG);
    webBadge.setColor(palette.light ? 0x18000000 : 0x30FFFFFF);
    RectF badgeRect = new RectF(WIDTH - 76, 20, WIDTH - 24, 42);
    canvas.drawRoundRect(badgeRect, 9, 9, webBadge);

    Paint webText = new Paint(Paint.ANTI_ALIAS_FLAG);
    webText.setTextAlign(Paint.Align.CENTER);
    webText.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    webText.setTextSize(11f);
    webText.setColor(palette.light ? palette.secondary : 0xFFCBD5E1);
    Paint.FontMetrics wfm = webText.getFontMetrics();
    canvas.drawText(
        "Web", badgeRect.centerX(), badgeRect.centerY() - (wfm.ascent + wfm.descent) / 2f, webText);

    // Bottom domain / host summary
    String host = extractHost(shortcut.url);
    Paint hostPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    hostPaint.setTextAlign(Paint.Align.CENTER);
    hostPaint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    hostPaint.setTextSize(17f);
    hostPaint.setColor(palette.light ? palette.secondary : 0xFF94A3B8);
    canvas.drawText(host, WIDTH / 2f, 202, hostPaint);

    return bmp;
  }

  private static void drawCustomIconBadge(
      Canvas canvas, Bitmap icon, float cx, float cy, UiPalette palette) {
    // Center circular / rounded rect container
    Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    circlePaint.setColor(palette.light ? 0xFFFFFFFF : 0x2EFFFFFF);
    canvas.drawCircle(cx, cy, 46, circlePaint);

    Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    strokePaint.setStyle(Paint.Style.STROKE);
    strokePaint.setStrokeWidth(2f);
    strokePaint.setColor(palette.light ? 0x20000000 : 0x44FFFFFF);
    canvas.drawCircle(cx, cy, 46, strokePaint);

    // Scale and center the icon inside 56x56 area
    float targetSize = 56f;
    float w = icon.getWidth();
    float h = icon.getHeight();
    float scale = Math.min(targetSize / w, targetSize / h);
    float drawW = w * scale;
    float drawH = h * scale;
    RectF dst =
        new RectF(
            cx - drawW / 2f,
            cy - drawH / 2f,
            cx + drawW / 2f,
            cy + drawH / 2f);

    Paint filterPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    canvas.save();
    Path clip = new Path();
    clip.addRoundRect(dst, 10f, 10f, Path.Direction.CW);
    canvas.clipPath(clip);
    canvas.drawBitmap(icon, null, dst, filterPaint);
    canvas.restore();
  }

  private static void drawGlyphBadge(
      Canvas canvas, WebShortcut shortcut, float cx, float cy, int accent, UiPalette palette) {
    Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    circlePaint.setColor(palette.light ? 0xFFFFFFFF : 0x2EFFFFFF);
    canvas.drawCircle(cx, cy, 46, circlePaint);

    Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    strokePaint.setStyle(Paint.Style.STROKE);
    strokePaint.setStrokeWidth(2f);
    strokePaint.setColor(palette.light ? 0x20000000 : 0x44FFFFFF);
    canvas.drawCircle(cx, cy, 46, strokePaint);

    String glyph = getGlyph(shortcut.name);
    Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setTextAlign(Paint.Align.CENTER);
    textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    textPaint.setTextSize(48f);
    textPaint.setColor(palette.light ? accent : 0xFFFFFFFF);
    Paint.FontMetrics fm = textPaint.getFontMetrics();
    float textY = cy - (fm.ascent + fm.descent) / 2f;
    canvas.drawText(glyph, cx, textY, textPaint);
  }

  private static Bitmap createAddCardBitmap(UiPalette palette) {
    Bitmap bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    Path clip = new Path();
    clip.addRoundRect(new RectF(0, 0, WIDTH, HEIGHT), RADIUS, RADIUS, Path.Direction.CW);
    canvas.clipPath(clip);

    // Translucent background
    Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    bg.setColor(palette.light ? 0x0A000000 : 0x12FFFFFF);
    canvas.drawRect(0, 0, WIDTH, HEIGHT, bg);

    // Dashed border inside clip
    Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    border.setStyle(Paint.Style.STROKE);
    border.setStrokeWidth(3.5f);
    border.setPathEffect(new DashPathEffect(new float[] {16, 12}, 0));
    border.setColor(palette.light ? 0x35000000 : 0x40FFFFFF);
    canvas.drawRoundRect(new RectF(2, 2, WIDTH - 2, HEIGHT - 2), RADIUS - 2, RADIUS - 2, border);

    // Plus circle icon
    Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
    circle.setColor(palette.light ? 0x14000000 : 0x24FFFFFF);
    canvas.drawCircle(WIDTH / 2f, 95, 38, circle);

    Paint plus = new Paint(Paint.ANTI_ALIAS_FLAG);
    plus.setColor(palette.light ? palette.text : 0xFFF1F5F9);
    plus.setStrokeWidth(5f);
    plus.setStrokeCap(Paint.Cap.ROUND);
    canvas.drawLine(WIDTH / 2f - 18, 95, WIDTH / 2f + 18, 95, plus);
    canvas.drawLine(WIDTH / 2f, 95 - 18, WIDTH / 2f, 95 + 18, plus);

    // Label
    Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    label.setTextAlign(Paint.Align.CENTER);
    label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    label.setTextSize(21f);
    label.setColor(palette.secondary);
    canvas.drawText("添加网页", WIDTH / 2f, 178, label);

    return bmp;
  }

  private static Bitmap blur(Bitmap source) {
    int w = source.getWidth(), h = source.getHeight();
    int[] pixels = new int[w * h];
    source.getPixels(pixels, 0, w, 0, 0, w, h);
    ArgbBlur.apply(pixels, w, h);
    ArgbBlur.apply(pixels, w, h);
    Bitmap blurred = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    blurred.setPixels(pixels, 0, w, 0, 0, w, h);
    return blurred;
  }

  private static String getGlyph(String name) {
    if (name == null || name.isEmpty()) return "W";
    int cp = name.codePointAt(0);
    return new String(Character.toChars(cp)).toUpperCase();
  }

  private static String extractHost(String url) {
    if (url == null || url.isEmpty()) return "";
    try {
      URI uri = new URI(url);
      String host = uri.getHost();
      if (host != null && !host.isEmpty()) {
        int port = uri.getPort();
        return port > 0 ? host + ":" + port : host;
      }
    } catch (Exception ignored) {
    }
    String stripped = url.replaceFirst("^https?://", "");
    int slash = stripped.indexOf('/');
    if (slash >= 0) stripped = stripped.substring(0, slash);
    return stripped;
  }

  private static int blend(int fg, int bg, float ratio) {
    float inv = 1f - ratio;
    int r = Math.round(Color.red(fg) * ratio + Color.red(bg) * inv);
    int g = Math.round(Color.green(fg) * ratio + Color.green(bg) * inv);
    int b = Math.round(Color.blue(fg) * ratio + Color.blue(bg) * inv);
    return Color.rgb(r, g, b);
  }
}
