package com.jianping.tv.browser;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.jianping.tv.ui.ArgbBlur;
import com.jianping.tv.ui.UiPalette;

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

    // Pure white background: clean, minimal, no extra artificial shapes or colorings
    Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    bgPaint.setColor(0xFFFFFFFF);
    canvas.drawRect(0, 0, WIDTH, HEIGHT, bgPaint);

    float cx = WIDTH / 2f;
    float cy = HEIGHT / 2f;

    Bitmap customIcon = WebIconManager.getIcon(context, shortcut.id);
    WebPresetLogo.Preset preset = WebPresetLogo.match(shortcut.url, shortcut.name);

    if (customIcon != null && !customIcon.isRecycled()) {
      // 1. Custom downloaded or captured Favicon / Touch Icon
      drawCustomIcon(canvas, customIcon, cx, cy);
    } else if (preset != null) {
      // 2. Built-in high-definition vector logo
      drawPresetLogo(canvas, preset, cx, cy, palette);
    } else {
      // 3. Clean initial glyph fallback
      drawGlyph(canvas, shortcut, cx, cy);
    }

    return bmp;
  }

  private static void drawCustomIcon(Canvas canvas, Bitmap icon, float cx, float cy) {
    float targetSize = 130f;
    float w = icon.getWidth();
    float h = icon.getHeight();
    if (w <= 0 || h <= 0) return;
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
    float radius = Math.min(drawW, drawH) * 0.18f;
    canvas.save();
    Path clip = new Path();
    clip.addRoundRect(dst, radius, radius, Path.Direction.CW);
    canvas.clipPath(clip);
    canvas.drawBitmap(icon, null, dst, filterPaint);
    canvas.restore();
  }

  private static void drawPresetLogo(
      Canvas canvas, WebPresetLogo.Preset preset, float cx, float cy, UiPalette palette) {
    canvas.save();
    float scale = getPresetScale(preset);
    canvas.scale(scale, scale, cx, cy);
    WebPresetLogo.draw(canvas, preset, cx, cy, palette);
    canvas.restore();
  }

  private static float getPresetScale(WebPresetLogo.Preset preset) {
    if (preset == null) return 1.4f;
    switch (preset) {
      case BAIDU:
        return 2.2f;
      case GITHUB:
        return 1.7f;
      case BILIBILI:
        return 1.8f;
      case YOUTUBE:
        return 1.6f;
      case GOOGLE:
        return 2.1f;
      case ALIST:
      case ZHIHU:
      case DOUBAN:
      case BING:
      case SYNOLOGY:
      case QNAP:
      case ROUTER:
      default:
        return 1.45f;
    }
  }

  private static void drawGlyph(
      Canvas canvas, WebShortcut shortcut, float cx, float cy) {
    String glyph = getGlyph(shortcut.name);
    Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    textPaint.setTextAlign(Paint.Align.CENTER);
    textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    textPaint.setTextSize(72f);
    textPaint.setColor(0xFF1E293B);
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
}
