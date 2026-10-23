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
  private boolean focusPending;
  private final Runnable settleFocus =
      () -> {
        focusPending = false;
        animateColor();
        animatePosition();
      };

  public AmbientBackgroundView(Context context, UiPalette palette) {
    super(context);
    this.palette = palette;
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    applyTint();
  }

  public void illuminate(int color, boolean animated) {
    removeCallbacks(settleFocus);
    if (colorTransition != null) colorTransition.cancel();
    if (positionTransition != null) positionTransition.cancel();
    // Saturated application artwork should leave a quiet tint, not color the whole page.
    float saturation = palette.light ? .38f : .65f;
    targetAccent =
        Color.rgb(
            mix(137, Color.red(color), saturation),
            mix(153, Color.green(color), saturation),
            mix(177, Color.blue(color), saturation));
    focusPending = animated;
    if (!animated) {
      accent = targetAccent;
      applyTint();
      invalidate();
      return;
    }
    // Skip applications crossed during repeat navigation. Position and tint settle together.
    postDelayed(settleFocus, 110);
  }

  private void animateColor() {
    if (accent == targetAccent) return;
    final int from = accent;
    final int color = targetAccent;
    colorTransition = ValueAnimator.ofFloat(0, 1);
    colorTransition.setDuration(520);
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
    float nextX = Math.max(.06f, Math.min(.94f, x / getWidth()));
    float nextY = Math.max(.08f, Math.min(.92f, y / getHeight()));
    if (Math.abs(targetX - nextX) < .0001f && Math.abs(targetY - nextY) < .0001f) return;
    targetX = nextX;
    targetY = nextY;
    if (focusPending) return;
    if (!animated) {
      // Scroll offsets update the existing destination instead of restarting an
      // animation every frame. At rest, follow the smooth scroll directly.
      if (positionTransition != null && positionTransition.isRunning()) return;
      centerX = nextX;
      centerY = nextY;
      invalidate();
      return;
    }
    animatePosition();
  }

  private void animatePosition() {
    if (positionTransition != null) positionTransition.cancel();
    if (Math.abs(centerX - targetX) < .0001f && Math.abs(centerY - targetY) < .0001f) return;
    final float fromX = centerX, fromY = centerY;
    positionTransition = ValueAnimator.ofFloat(0, 1);
    positionTransition.setDuration(460);
    positionTransition.setInterpolator(new DecelerateInterpolator());
    positionTransition.addUpdateListener(
        a -> {
          float t = (float) a.getAnimatedValue();
          centerX = fromX + (targetX - fromX) * t;
          centerY = fromY + (targetY - fromY) * t;
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
                ? new int[] {0x17FFFFFF, 0x07FFFFFF, 0x00FFFFFF}
                : new int[] {0x25FFFFFF, 0x0AFFFFFF, 0x00FFFFFF},
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
        x - getWidth() * .32f,
        y - getHeight() * .48f,
        x + getWidth() * .32f,
        y + getHeight() * .48f);
    if (lightMask != null) c.drawBitmap(lightMask, null, lightBounds, paint);
  }

  @Override
  protected void onDetachedFromWindow() {
    removeCallbacks(settleFocus);
    focusPending = false;
    if (colorTransition != null) colorTransition.cancel();
    if (positionTransition != null) positionTransition.cancel();
    super.onDetachedFromWindow();
  }
}
