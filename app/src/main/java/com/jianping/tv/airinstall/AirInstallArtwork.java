package com.jianping.tv.airinstall;

import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.jianping.tv.ui.ArgbBlur;
import com.jianping.tv.ui.UiPalette;

/**
 * Generates TV card artwork and ambient preview for Air Install (隔空安装).
 * Directly reuses the authentic Apple iOS AirDrop concentric wave symbol.
 */
public final class AirInstallArtwork {
  private static final int WIDTH = 384;
  private static final int HEIGHT = 250;
  private static final float RADIUS = 39f;

  private AirInstallArtwork() {}

  public static Drawable cardArtwork(Resources res, UiPalette palette) {
    Bitmap bmp = createCardBitmap(res, palette);
    return new BitmapDrawable(res, bmp);
  }

  public static Drawable previewArtwork(Resources res, UiPalette palette) {
    Bitmap bmp = createCardBitmap(res, palette);
    Bitmap blurred = blur(bmp);
    bmp.recycle();
    return new BitmapDrawable(res, blurred);
  }

  private static Bitmap createCardBitmap(Resources res, UiPalette palette) {
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

    // Subtle Apple ambient aura glow behind the AirDrop waves
    Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    glowPaint.setShader(
        new RadialGradient(
            cx,
            cy,
            90f,
            new int[] {0x18007AFF, 0x08007AFF, 0x00FFFFFF},
            new float[] {0f, 0.6f, 1f},
            Shader.TileMode.CLAMP));
    canvas.drawCircle(cx, cy, 90f, glowPaint);

    Bitmap mark = null;
    if (res != null) {
      try {
        mark = BitmapFactory.decodeResource(res, com.jianping.tv.R.drawable.ic_airdrop_mark);
      } catch (Throwable ignored) {
      }
    }

    if (mark != null) {
      // Draw authentic Apple AirDrop asset scaled and centered
      float targetW = 144f;
      float targetH = targetW * mark.getHeight() / mark.getWidth();
      RectF dst = new RectF(cx - targetW / 2f, cy - targetH / 2f, cx + targetW / 2f, cy + targetH / 2f);
      Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
      canvas.drawBitmap(mark, null, dst, p);
    } else {
      drawAirDropVectorFallback(canvas, cx, cy);
    }

    return bmp;
  }

  private static void drawAirDropVectorFallback(Canvas canvas, float cx, float cy) {
    int airdropBlue = 0xFF007AFF;

    // 1. Center solid dot
    Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    dotPaint.setStyle(Paint.Style.FILL);
    dotPaint.setColor(airdropBlue);
    canvas.drawCircle(cx, cy, 6.5f, dotPaint);

    // 2. Concentric circular open wave arcs (pure Apple AirDrop)
    Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    wavePaint.setStyle(Paint.Style.STROKE);
    wavePaint.setStrokeWidth(5.5f);
    wavePaint.setStrokeCap(Paint.Cap.ROUND);
    wavePaint.setColor(airdropBlue);

    float[] radii = new float[] {24f, 44f, 64f, 84f};
    for (float r : radii) {
      RectF oval = new RectF(cx - r, cy - r, cx + r, cy + r);
      canvas.drawArc(oval, 130f, 280f, false, wavePaint);
    }
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
