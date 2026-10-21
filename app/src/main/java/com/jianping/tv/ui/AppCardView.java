package com.jianping.tv.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.*;

/** Shared remote-operated TV card: focus lift, cached light and press feedback. */
public final class AppCardView extends LinearLayout {
  private final float unit;
  private final FrameLayout body;
  private final TextView name;
  private final ImageView normalImage, softImage;
  private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF floorBounds = new RectF();
  private final SurfaceHighlight highlight;
  private final PathInterpolator settle = new PathInterpolator(.18f, .85f, .25f, 1);
  // One damped lift, including a small overshoot; exactly at rest when the animation ends.
  private final Interpolator lift =
      t -> t >= 1 ? 1 : (float) (1 - Math.exp(-6 * t) * (Math.cos(8 * t) + .75 * Math.sin(8 * t)));
  private final int accent;
  private final boolean brightSurface;
  private Bitmap glow, floorLight;
  private float focusAmount, pressAmount;
  private ValueAnimator focusTransition, pressTransition;
  private boolean activating, contextSelected, focusTarget;
  private Runnable action;

  private int px(float value) {
    return Math.round(value * unit);
  }

  public AppCardView(
      Context context, float unit, String label, Drawable art, Drawable soft, int accent) {
    super(context);
    this.unit = unit;
    this.accent = accent;
    if (art instanceof BitmapDrawable) {
      Bitmap b = ((BitmapDrawable) art).getBitmap();
      int color = b.getPixel(b.getWidth() / 2, Math.max(0, b.getHeight() / 12));
      brightSurface =
          Color.alpha(color) > 180
              && .2126f * Color.red(color)
                      + .7152f * Color.green(color)
                      + .0722f * Color.blue(color)
                  > 185;
    } else brightSurface = false;
    setOrientation(VERTICAL);
    setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
    setPadding(0, px(8), 0, 0);
    setClipChildren(false);
    setClipToPadding(false);
    setWillNotDraw(false);
    setFocusable(true);
    setClickable(true);
    setContentDescription(label);
    body = new FrameLayout(context);
    body.setAlpha(.88f);
    GradientDrawable base = new GradientDrawable();
    base.setColor(0xFF263244);
    base.setCornerRadius(px(12));
    body.setBackground(base);
    body.setClipToOutline(true);
    body.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    highlight = new SurfaceHighlight(brightSurface);
    body.setForeground(highlight);
    normalImage = new ImageView(context);
    normalImage.setImageDrawable(art);
    normalImage.setScaleType(ImageView.ScaleType.FIT_XY);
    body.addView(normalImage, new FrameLayout.LayoutParams(-1, -1));
    softImage = new ImageView(context);
    softImage.setImageDrawable(soft);
    softImage.setScaleType(ImageView.ScaleType.FIT_XY);
    softImage.setAlpha(0f);
    body.addView(softImage, new FrameLayout.LayoutParams(-1, -1));
    addView(body, new LayoutParams(px(120), px(78)));
    name = new TextView(context);
    name.setText(label);
    name.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
    name.setIncludeFontPadding(false);
    name.setGravity(Gravity.CENTER);
    name.setSingleLine(true);
    name.setEllipsize(TextUtils.TruncateAt.END);
    name.setTextColor(0xFFB9C3D2);
    LayoutParams np = new LayoutParams(-1, px(26));
    np.topMargin = px(7);
    addView(name, np);
    setOnFocusChangeListener((v, focused) -> updateFocus(focused));
    super.setOnClickListener(
        v -> {
          if (activating) return;
          activating = true;
          animatePress(0);
          postDelayed(
              () -> {
                activating = false;
                if (!isAttachedToWindow()) return;
                if (action != null) action.run();
              },
              85);
        });
  }

  public void action(Runnable action) {
    this.action = action;
  }

  public void showName(boolean visible) {
    // Keep the card geometry and accessible name stable in icon-only mode.
    name.setVisibility(visible ? VISIBLE : INVISIBLE);
  }

  public void contextSelected(boolean selected) {
    contextSelected = selected;
    animatePress(0);
    updateFocus(hasFocus());
  }

  public void preview(float amount) {
    normalImage.setAlpha(1 - amount);
    softImage.setAlpha(amount);
    name.setAlpha(1 - amount);
  }

  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    super.onSizeChanged(w, h, ow, oh);
    if (w != ow || h != oh) {
      glow = null;
      floorLight = null;
    }
    if (hasFocus() || contextSelected) {
      prepareGlow();
      invalidate();
    }
  }

  private void updateFocus(boolean focused) {
    focused = focused || contextSelected;
    if (!focused) animatePress(0);
    name.setTypeface(
        Typeface.create(focused ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    if (focused) {
      preview(0);
      prepareGlow();
    }
    if (focusTarget == focused) return;
    focusTarget = focused;
    if (focusTransition != null) focusTransition.cancel();
    float target = focused ? 1 : 0;
    focusTransition = ValueAnimator.ofFloat(focusAmount, target);
    focusTransition.setDuration(
        Math.max(
            90,
            Math.round((focused ? 420 : 230) * Math.min(1.1f, Math.abs(target - focusAmount)))));
    focusTransition.setInterpolator(focused ? lift : settle);
    focusTransition.addUpdateListener(
        a -> {
          focusAmount = (float) a.getAnimatedValue();
          applyMotion();
        });
    focusTransition.start();
  }

  private void animatePress(float target) {
    if (pressTransition != null) pressTransition.cancel();
    pressTransition = ValueAnimator.ofFloat(pressAmount, target);
    pressTransition.setDuration(target > 0 ? 85 : 180);
    pressTransition.setInterpolator(settle);
    pressTransition.addUpdateListener(
        a -> {
          pressAmount = (float) a.getAnimatedValue();
          applyMotion();
        });
    pressTransition.start();
  }

  private void applyMotion() {
    float light = Math.max(0, Math.min(1, focusAmount));
    float size = 1 + .20f * focusAmount - .045f * pressAmount;
    body.setScaleX(size);
    body.setScaleY(size);
    body.setTranslationY(unit * (-10 * focusAmount + 3 * pressAmount));
    body.setTranslationZ(unit * 12 * light);
    body.setAlpha(.88f + .12f * light);
    name.setTranslationY(unit * 2 * light);
    int r = Math.round(185 + 70 * light);
    int g = Math.round(195 + 60 * light);
    int b = Math.round(210 + 45 * light);
    name.setTextColor(Color.rgb(r, g, b));
    highlight.setAlpha(Math.round(255 * light));
    invalidate();
  }

  private void prepareGlow() {
    if (glow != null || getWidth() == 0) return;
    int inset = px(24);
    glow =
        Bitmap.createBitmap(
            getWidth() + 2 * inset, getHeight() + 2 * inset, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(glow);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    float w = px(120), h = px(78);
    float x = inset + (getWidth() - w) / 2f;
    float y = inset + px(8);
    RectF bounds = new RectF(x, y, x + w, y + h);
    p.setColor(0xFF111824);
    p.setShadowLayer(px(14), 0, px(7), 0xC9000000);
    canvas.drawRoundRect(bounds, px(12), px(12), p);

    // A stationary soft pool below the lifted card provides a separate surface/depth cue.
    floorLight = Bitmap.createBitmap(160, 40, Bitmap.Config.ARGB_8888);
    Canvas floor = new Canvas(floorLight);
    Paint light = new Paint(Paint.ANTI_ALIAS_FLAG);
    light.setShader(
        new RadialGradient(
            80,
            20,
            74,
            new int[] {
              Color.argb(90, Color.red(accent), Color.green(accent), Color.blue(accent)),
              Color.argb(34, Color.red(accent), Color.green(accent), Color.blue(accent)),
              0
            },
            new float[] {0, .48f, 1},
            Shader.TileMode.CLAMP));
    floor.scale(1, .25f, 80, 20);
    floor.drawRect(0, -80, 160, 120, light);
    floorBounds.set(getWidth() / 2f - px(80), px(78), getWidth() / 2f + px(80), px(118));
    p.setShadowLayer(
        px(13), 0, 0, Color.argb(95, Color.red(accent), Color.green(accent), Color.blue(accent)));
    canvas.drawRoundRect(bounds, px(12), px(12), p);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (glow != null && focusAmount > 0) {
      glowPaint.setAlpha(Math.round(Math.min(1, focusAmount) * (1 - .25f * pressAmount) * 255));
      if (floorLight != null) canvas.drawBitmap(floorLight, null, floorBounds, glowPaint);
      int save = canvas.save();
      canvas.translate(0, body.getTranslationY());
      canvas.scale(body.getScaleX(), body.getScaleY(), getWidth() / 2f, px(8) + px(78) / 2f);
      canvas.drawBitmap(glow, -px(24), -px(24), glowPaint);
      canvas.restoreToCount(save);
    }
  }

  @Override
  public boolean onKeyDown(int code, KeyEvent event) {
    if ((code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER)
        && event.getRepeatCount() == 0) {
      animatePress(1);
    }
    return super.onKeyDown(code, event);
  }

  @Override
  public boolean onKeyUp(int code, KeyEvent event) {
    boolean handled = super.onKeyUp(code, event);
    if (!activating && (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER))
      animatePress(0);
    return handled;
  }

  @Override
  protected void onDetachedFromWindow() {
    if (focusTransition != null) focusTransition.cancel();
    if (pressTransition != null) pressTransition.cancel();
    super.onDetachedFromWindow();
  }

  /** Geometry and gradients are cached; only opacity changes during a focus transition. */
  private final class SurfaceHighlight extends Drawable {
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG),
        sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final boolean light;

    SurfaceHighlight(boolean light) {
      this.light = light;
      rim.setStyle(Paint.Style.STROKE);
      rim.setStrokeWidth(Math.max(1, unit * .8f));
      setAlpha(0);
    }

    @Override
    protected void onBoundsChange(Rect b) {
      bounds.set(b);
      bounds.inset(unit * .5f, unit * .5f);
      float bottom = Math.max(b.top + 1, b.bottom);
      rim.setShader(
          new LinearGradient(
              0,
              b.top,
              0,
              bottom,
              light ? 0xA36C7C92 : 0xCCFFFFFF,
              light ? 0x3D64748B : 0x26FFFFFF,
              Shader.TileMode.CLAMP));
      sheen.setShader(
          new LinearGradient(
              0,
              b.top,
              0,
              bottom,
              light ? 0x02FFFFFF : 0x13FFFFFF,
              0x00FFFFFF,
              Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
      c.drawRoundRect(bounds, px(12), px(12), sheen);
      c.drawRoundRect(bounds, px(12), px(12), rim);
    }

    @Override
    public void setAlpha(int alpha) {
      rim.setAlpha(alpha);
      sheen.setAlpha(alpha);
      invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
      rim.setColorFilter(filter);
      sheen.setColorFilter(filter);
      invalidateSelf();
    }

    @Override
    public int getOpacity() {
      return PixelFormat.TRANSLUCENT;
    }
  }
}
