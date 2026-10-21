package com.jianping.tv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** A finite, focus-driven light transition. No continuous rendering while the desktop is idle. */
public final class AmbientBackgroundView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int accent = Color.rgb(103, 139, 200);
  private ValueAnimator transition;

  public AmbientBackgroundView(Context context) {
    super(context);
    paint.setDither(true);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  public void illuminate(int color, boolean animated) {
    if (transition != null) transition.cancel();
    if (!animated || accent == color) {
      accent = color;
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
          invalidate();
        });
    transition.start();
  }

  private int mix(int a, int b, float t) {
    return Math.round(a + (b - a) * t);
  }

  private int alpha(int value) {
    return Color.argb(value, Color.red(accent), Color.green(accent), Color.blue(accent));
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    float w = getWidth(), h = getHeight();
    c.drawColor(0xFF080D17);
    // Main ambient source is aligned to the selected hero icon, not a corner wallpaper.
    paint.setShader(
        new RadialGradient(
            w * .5f,
            h * .282f,
            w * .38f,
            new int[] {alpha(104), alpha(37), alpha(0)},
            new float[] {0, .44f, 1},
            Shader.TileMode.CLAMP));
    c.drawRect(0, 0, w, h, paint);
    paint.setShader(
        new RadialGradient(
            w * .5f,
            h * .37f,
            w * .19f,
            new int[] {alpha(44), alpha(0)},
            null,
            Shader.TileMode.CLAMP));
    c.drawRect(0, 0, w, h, paint);
    paint.setShader(
        new LinearGradient(
            0, h * .46f, 0, h, new int[] {0x00000000, 0xA0080D17}, null, Shader.TileMode.CLAMP));
    c.drawRect(0, 0, w, h, paint);
    paint.setShader(null);
  }

  @Override
  protected void onDetachedFromWindow() {
    if (transition != null) transition.cancel();
    super.onDetachedFromWindow();
  }
}
