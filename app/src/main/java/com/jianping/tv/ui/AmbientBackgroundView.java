package com.jianping.tv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Cached light mask, tinted during finite focus transitions; no idle rendering loop. */
public final class AmbientBackgroundView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF viewport = new RectF();
  private int accent = Color.rgb(103, 139, 200);
  private ValueAnimator transition;
  private Bitmap lightMask;

  public AmbientBackgroundView(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    applyTint();
  }

  public void illuminate(int color, boolean animated) {
    if (transition != null) transition.cancel();
    if (!animated || accent == color) {
      accent = color;
      applyTint();
      invalidate();
      return;
    }
    final int from = accent;
    transition = ValueAnimator.ofFloat(0, 1);
    transition.setDuration(620);
    transition.setInterpolator(new DecelerateInterpolator());
    transition.addUpdateListener(
        a -> {
          float t = (float) a.getAnimatedValue();
          accent =
              Color.rgb(
                  mix(Color.red(from), Color.red(color), t),
                  mix(Color.green(from), Color.green(color), t),
                  mix(Color.blue(from), Color.blue(color), t));
          applyTint();
          invalidate();
        });
    transition.start();
  }

  private void applyTint() {
    paint.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
  }

  private int mix(int a, int b, float t) {
    return Math.round(a + (b - a) * t);
  }

  @Override
  protected void onSizeChanged(int w, int h, int oldw, int oldh) {
    super.onSizeChanged(w, h, oldw, oldh);
    viewport.set(0, 0, w, h);
    if (lightMask != null) lightMask.recycle();
    // Soft light needs no full-resolution texture. Build geometry once, never inside onDraw.
    lightMask = Bitmap.createBitmap(480, 270, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(lightMask);
    Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    mask.setDither(true);
    float x = 240, y = 70;
    mask.setShader(
        new RadialGradient(
            x,
            y,
            250,
            new int[] {0x1EFFFFFF, 0x0BFFFFFF, 0x00FFFFFF},
            new float[] {0, .5f, 1},
            Shader.TileMode.CLAMP));
    c.drawRect(0, 0, 480, 270, mask);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.drawColor(0xFF0B0F17);
    if (lightMask != null) c.drawBitmap(lightMask, null, viewport, paint);
  }

  @Override
  protected void onDetachedFromWindow() {
    if (transition != null) transition.cancel();
    super.onDetachedFromWindow();
  }
}
