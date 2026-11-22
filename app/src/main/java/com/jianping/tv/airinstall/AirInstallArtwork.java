package com.jianping.tv.airinstall;

import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.jianping.tv.ui.ArgbBlur;
import com.jianping.tv.ui.UiPalette;

/**
 * Generates TV card artwork and ambient preview for Air Install (隔空安装).
 * Enforces pure white card background with Apple AirDrop-inspired concentric waves
 * and precision aerodynamic transmission vector art.
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
    // Wave origin center slightly lower to balance overall visual gravity
    float waveCenterY = cy + 12f;

    // 1. Subtle Apple ambient aura glow
    Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    glowPaint.setShader(
        new RadialGradient(
            cx,
            waveCenterY - 10f,
            95f,
            new int[] {0x160EA5E9, 0x0838BDF8, 0x00FFFFFF},
            new float[] {0f, 0.6f, 1f},
            Shader.TileMode.CLAMP));
    canvas.drawCircle(cx, waveCenterY - 10f, 95f, glowPaint);

    // 2. Concentric Radiating Wave Arcs (Apple AirDrop style)
    // 3 rings: Outer (30%), Middle (60%), Inner (100%)
    float strokeW = 5.2f;
    float startAngle = 145f;
    float sweepAngle = 250f;

    // Outer wave
    float r3 = 72f;
    Paint pWave3 = new Paint(Paint.ANTI_ALIAS_FLAG);
    pWave3.setStyle(Paint.Style.STROKE);
    pWave3.setStrokeWidth(strokeW);
    pWave3.setStrokeCap(Paint.Cap.ROUND);
    pWave3.setShader(
        new LinearGradient(
            cx - r3, waveCenterY - r3, cx + r3, waveCenterY + r3 * 0.6f,
            0x4D0284C7, 0x4D38BDF8, Shader.TileMode.CLAMP));
    RectF rect3 = new RectF(cx - r3, waveCenterY - r3, cx + r3, waveCenterY + r3);
    canvas.drawArc(rect3, startAngle, sweepAngle, false, pWave3);

    // Middle wave
    float r2 = 50f;
    Paint pWave2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    pWave2.setStyle(Paint.Style.STROKE);
    pWave2.setStrokeWidth(strokeW);
    pWave2.setStrokeCap(Paint.Cap.ROUND);
    pWave2.setShader(
        new LinearGradient(
            cx - r2, waveCenterY - r2, cx + r2, waveCenterY + r2 * 0.6f,
            0x990284C7, 0x9938BDF8, Shader.TileMode.CLAMP));
    RectF rect2 = new RectF(cx - r2, waveCenterY - r2, cx + r2, waveCenterY + r2);
    canvas.drawArc(rect2, startAngle, sweepAngle, false, pWave2);

    // Inner wave
    float r1 = 28f;
    Paint pWave1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    pWave1.setStyle(Paint.Style.STROKE);
    pWave1.setStrokeWidth(strokeW);
    pWave1.setStrokeCap(Paint.Cap.ROUND);
    pWave1.setShader(
        new LinearGradient(
            cx - r1, waveCenterY - r1, cx + r1, waveCenterY + r1 * 0.6f,
            0xFF0284C7, 0xFF38BDF8, Shader.TileMode.CLAMP));
    RectF rect1 = new RectF(cx - r1, waveCenterY - r1, cx + r1, waveCenterY + r1);
    canvas.drawArc(rect1, startAngle, sweepAngle, false, pWave1);

    // 3. Central Apple Energy Beacon & Aero Dart
    // Emitter beacon at the base opening
    Paint beaconDot = new Paint(Paint.ANTI_ALIAS_FLAG);
    beaconDot.setStyle(Paint.Style.FILL);
    beaconDot.setColor(0xFF0EA5E9);
    canvas.drawCircle(cx, waveCenterY + 16f, 4.5f, beaconDot);

    Paint beaconHalo = new Paint(Paint.ANTI_ALIAS_FLAG);
    beaconHalo.setStyle(Paint.Style.STROKE);
    beaconHalo.setStrokeWidth(1.8f);
    beaconHalo.setColor(0x550EA5E9);
    canvas.drawCircle(cx, waveCenterY + 16f, 8.5f, beaconHalo);

    // Aerodynamic transmission dart pointing upwards into the sound waves
    Path dart = new Path();
    float tipY = waveCenterY - 18f;
    float wingY = waveCenterY + 4f;
    float notchY = waveCenterY - 2f;
    float wingX = 13f;

    dart.moveTo(cx, tipY);
    dart.lineTo(cx + wingX, wingY);
    dart.lineTo(cx, notchY);
    dart.lineTo(cx - wingX, wingY);
    dart.close();

    Paint dartPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    dartPaint.setStyle(Paint.Style.FILL);
    dartPaint.setPathEffect(new CornerPathEffect(2.5f));
    dartPaint.setShader(
        new LinearGradient(
            cx, tipY, cx, wingY,
            0xFF38BDF8, 0xFF0284C7, Shader.TileMode.CLAMP));
    canvas.drawPath(dart, dartPaint);
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
