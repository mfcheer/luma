package com.jianping.tv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Cached, finite light transitions follow the card without an idle rendering loop. */
public final class AmbientBackgroundView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF lightBounds = new RectF();
  private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final UiPalette palette;
  private int accent = Color.rgb(103, 139, 200), targetAccent = accent;
  private float centerX = .5f, centerY = .32f, targetX = centerX, targetY = centerY;
  private ValueAnimator colorTransition, positionTransition;
  private Bitmap lightMask;

  public AmbientBackgroundView(Context context, UiPalette palette) {
    super(context);
    this.palette = palette;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    applyTint();
  }

  public void illuminate(int color, boolean animated) {
    if (animated && targetAccent == color) return;
    if (colorTransition != null) colorTransition.cancel();
    targetAccent = color;
    if (!animated) {
      accent = color;
      applyTint();
      invalidate();
      return;
    }
    final int from = accent;
    colorTransition = ValueAnimator.ofFloat(0, 1);
    colorTransition.setDuration(460);
    colorTransition.setInterpolator(new DecelerateInterpolator());
    colorTransition.addUpdateListener(
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
    colorTransition.start();
  }

  /** Coordinates are in the desktop, and already include the wall's current scroll offset. */
  public void follow(float x, float y, boolean animated) {
    if (getWidth() == 0 || getHeight() == 0) return;
    float nextX = .08f + .84f * Math.max(0, Math.min(1, x / getWidth()));
    float nextY = .10f + .76f * Math.max(0, Math.min(1, y / getHeight()));
    if (Math.abs(targetX - nextX) < .0001f && Math.abs(targetY - nextY) < .0001f) return;
    targetX = nextX;
    targetY = nextY;
    if (positionTransition != null) positionTransition.cancel();
    if (!animated) {
      centerX = nextX;
      centerY = nextY;
      invalidate();
      return;
    }
    final float fromX = centerX, fromY = centerY;
    positionTransition = ValueAnimator.ofFloat(0, 1);
    positionTransition.setDuration(380);
    positionTransition.setInterpolator(new DecelerateInterpolator());
    positionTransition.addUpdateListener(
        a -> {
          float t = (float) a.getAnimatedValue();
          centerX = fromX + (nextX - fromX) * t;
          centerY = fromY + (nextY - fromY) * t;
          invalidate();
        });
    positionTransition.start();
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
    if (palette.light)
      surfacePaint.setShader(
          new LinearGradient(0, 0, w, h, 0xFFFAF9F7, 0xFFF2F2F0, Shader.TileMode.CLAMP));
    if (lightMask != null) lightMask.recycle();
    lightMask = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(lightMask);
    Paint mask = new Paint(Paint.ANTI_ALIAS_FLAG);
    mask.setDither(true);
    mask.setShader(
        new RadialGradient(
            128,
            128,
            128,
            palette.light
                ? new int[] {0x10FFFFFF, 0x04FFFFFF, 0x00FFFFFF}
                : new int[] {0x27FFFFFF, 0x0BFFFFFF, 0x00FFFFFF},
            new float[] {0, .52f, 1},
            Shader.TileMode.CLAMP));
    c.drawRect(0, 0, 256, 256, mask);
  }

  @Override
  protected void onDraw(Canvas c) {
    super.onDraw(c);
    c.drawColor(palette.background);
    if (palette.light) c.drawRect(0, 0, getWidth(), getHeight(), surfacePaint);
    float x = centerX * getWidth(), y = centerY * getHeight();
    lightBounds.set(
        x - getWidth() * .58f,
        y - getHeight() * .76f,
        x + getWidth() * .58f,
        y + getHeight() * .76f);
    if (lightMask != null) c.drawBitmap(lightMask, null, lightBounds, paint);
  }

  @Override
  protected void onDetachedFromWindow() {
    if (colorTransition != null) colorTransition.cancel();
    if (positionTransition != null) positionTransition.cancel();
    super.onDetachedFromWindow();
  }
}
