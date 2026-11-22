package com.jianping.tv.airinstall;

import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.jianping.tv.ui.ArgbBlur;
import com.jianping.tv.ui.UiPalette;

/**
 * Generates TV card artwork and ambient preview for Air Install (隔空安装).
 * Enforces pure white card background with standard rounded corners and crisp centered vector art.
 */
public final class AirInstallArtwork {
  private static final int WIDTH = 384;
  private static final int HEIGHT = 250;
  private static final float RADIUS = 39f;

  private AirInstallArtwork() {}

  public static Drawable cardArtwork(Resources res, UiPalette palette) {
    Bitmap bmp = createCardBitmap(palette);
    return new BitmapDrawable(res, bmp);
  }

  public static Drawable previewArtwork(Resources res, UiPalette palette) {
    Bitmap bmp = createCardBitmap(palette);
    Bitmap blurred = blur(bmp);
    bmp.recycle();
    return new BitmapDrawable(res, blurred);
  }

  private static Bitmap createCardBitmap(UiPalette palette) {
    Bitmap bmp = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    Path clip = new Path();
    clip.addRoundRect(new RectF(0, 0, WIDTH, HEIGHT), RADIUS, RADIUS, Path.Direction.CW);
    canvas.clipPath(clip);

    // Standard pure white card surface
    Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    bgPaint.setColor(0xFFFFFFFF);
    canvas.drawRect(0, 0, WIDTH, HEIGHT, bgPaint);

    float cx = WIDTH / 2f;
    float cy = HEIGHT / 2f;

    drawAirInstallVector(canvas, cx, cy);

    return bmp;
  }

  private static void drawAirInstallVector(Canvas canvas, float cx, float cy) {
    Paint strokeDark = new Paint(Paint.ANTI_ALIAS_FLAG);
    strokeDark.setStyle(Paint.Style.STROKE);
    strokeDark.setStrokeWidth(5.5f);
    strokeDark.setStrokeCap(Paint.Cap.ROUND);
    strokeDark.setStrokeJoin(Paint.Join.ROUND);
    strokeDark.setColor(0xFF1E293B); // Slate-800

    Paint fillCyan = new Paint(Paint.ANTI_ALIAS_FLAG);
    fillCyan.setStyle(Paint.Style.FILL);
    fillCyan.setColor(0xFF0EA5E9); // Sky-500 cyan accent

    Paint strokeCyan = new Paint(Paint.ANTI_ALIAS_FLAG);
    strokeCyan.setStyle(Paint.Style.STROKE);
    strokeCyan.setStrokeWidth(5f);
    strokeCyan.setStrokeCap(Paint.Cap.ROUND);
    strokeCyan.setColor(0xFF0EA5E9);

    // Outer wireless broadcast rings / concentric radar wave arcs
    // Left and right radiating arcs
    RectF wave1 = new RectF(cx - 72, cy - 72, cx + 72, cy + 72);
    canvas.drawArc(wave1, 215, 50, false, strokeCyan);
    canvas.drawArc(wave1, 275, 50, false, strokeCyan);

    RectF wave2 = new RectF(cx - 52, cy - 52, cx + 52, cy + 52);
    canvas.drawArc(wave2, 210, 60, false, strokeCyan);
    canvas.drawArc(wave2, 270, 60, false, strokeCyan);

    // Central device outline (Minimalist geometric screen / package)
    float phoneW = 56f;
    float phoneH = 82f;
    RectF phoneRect = new RectF(cx - phoneW / 2f, cy - phoneH / 2f + 10f, cx + phoneW / 2f, cy + phoneH / 2f + 10f);
    canvas.drawRoundRect(phoneRect, 14f, 14f, strokeDark);

    // Top speaker / notch pill
    strokeDark.setStrokeWidth(3.5f);
    canvas.drawLine(cx - 8f, phoneRect.top + 10f, cx + 8f, phoneRect.top + 10f, strokeDark);

    // Center download / receive arrow
    strokeDark.setStrokeWidth(4.5f);
    float arrowTop = phoneRect.top + 28f;
    float arrowBottom = phoneRect.bottom - 22f;
    canvas.drawLine(cx, arrowTop, cx, arrowBottom, strokeDark);
    canvas.drawLine(cx - 10f, arrowBottom - 10f, cx, arrowBottom, strokeDark);
    canvas.drawLine(cx + 10f, arrowBottom - 10f, cx, arrowBottom, strokeDark);

    // Base tray / receiving tray
    strokeDark.setStrokeWidth(4f);
    canvas.drawLine(cx - 14f, phoneRect.bottom - 12f, cx + 14f, phoneRect.bottom - 12f, strokeDark);

    // Glowing cyan origin beacon dot at top
    canvas.drawCircle(cx, cy - 64f, 4.5f, fillCyan);
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
}
