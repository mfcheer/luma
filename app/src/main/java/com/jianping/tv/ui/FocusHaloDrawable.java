package com.jianping.tv.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Cached software-rendered halo: no per-frame blur or Android 12 RenderEffect dependency. */
public final class FocusHaloDrawable extends Drawable {
  private final float inset, radius;
  private Bitmap cache;
  private final int tint;

  public FocusHaloDrawable(float inset, float radius) {
    this(inset, radius, 0xFFAACEFF);
  }

  public FocusHaloDrawable(float inset, float radius, int tint) {
    this.inset = inset;
    this.radius = radius;
    this.tint = tint;
  }

  @Override
  protected void onBoundsChange(Rect bounds) {
    cache = null;
    if (bounds.width() <= 0 || bounds.height() <= 0) return;
    cache = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(cache);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    RectF r = new RectF(inset, inset, bounds.width() - inset, bounds.height() - inset);
    p.setColor(0xFF22354B);
    p.setShadowLayer(
        inset * .70f, 0, 0, Color.argb(155, Color.red(tint), Color.green(tint), Color.blue(tint)));
    c.drawRoundRect(r, radius, radius, p);
    p.setShadowLayer(
        inset * .25f,
        0,
        0,
        Color.argb(
            210,
            (Color.red(tint) + 255) / 2,
            (Color.green(tint) + 255) / 2,
            (Color.blue(tint) + 255) / 2));
    p.setColor(0xFF30475F);
    c.drawRoundRect(r, radius, radius, p);
    p.clearShadowLayer();
    p.setStyle(Paint.Style.STROKE);
    p.setStrokeWidth(Math.max(1, inset * .09f));
    p.setColor(0xFFF2F7FF);
    c.drawRoundRect(r, radius, radius, p);
  }

  @Override
  public void draw(Canvas canvas) {
    if (cache != null) canvas.drawBitmap(cache, getBounds().left, getBounds().top, null);
  }

  @Override
  public void setAlpha(int alpha) {}

  @Override
  public void setColorFilter(ColorFilter filter) {}

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
