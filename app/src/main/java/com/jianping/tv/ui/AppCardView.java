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
  private final UiPalette palette;
  private static Bitmap restingShadow;
  private final RectF restingBounds = new RectF();
  private final FrameLayout body;
  private final TextView name, moveBadge;
  private final ImageView normalImage, softImage;
  private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF floorBounds = new RectF();
  private final SurfaceHighlight highlight;
  private final PathInterpolator settle = new PathInterpolator(.18f, .85f, .25f, 1);
  // A restrained landing for isolated presses; rapid navigation uses a plain ease-out.
  private final Interpolator lift =
      t -> t >= 1 ? 1 : (float) (1 - Math.exp(-9 * t) * (Math.cos(6 * t) + 1.5 * Math.sin(6 * t)));
  private final int accent;
  private final boolean brightSurface;
  private Bitmap glow, floorLight;
  private float focusAmount, pressAmount, previewAmount, moveAmount, nameAmount;
  private ValueAnimator focusTransition, pressTransition, moveTransition, nameTransition;
  private boolean activating, contextSelected, focusTarget, rapid, moving, namesVisible;
  private float baselineLift;
  private final Runnable revealName = () -> animateName(true);
  private Runnable action;

  private int px(float value) {
    return Math.round(value * unit);
  }

  public AppCardView(
      Context context,
      float unit,
      String label,
      Drawable art,
      Drawable soft,
      int accent,
      UiPalette palette) {
    super(context);
    this.unit = unit;
    this.palette = palette;
    if (palette.light) prepareRestingShadow();
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
    body.setAlpha(palette.light ? 1f : .90f);
    GradientDrawable base = new GradientDrawable();
    base.setColor(palette.cardFallback);
    base.setCornerRadius(px(12));
    body.setBackground(base);
    body.setClipToOutline(true);
    body.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
    palette.styleShadow(body);
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
    name.setTextColor(palette.cardName);
    name.setAlpha(0);
    name.setVisibility(INVISIBLE);
    LayoutParams np = new LayoutParams(-1, px(26));
    np.topMargin = px(7);
    FrameLayout labelSlot = new FrameLayout(context);
    labelSlot.addView(name, new FrameLayout.LayoutParams(-1, -1));
    moveBadge = new TextView(context);
    moveBadge.setText("移动中");
    moveBadge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    moveBadge.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    moveBadge.setTextColor(palette.movingText);
    moveBadge.setIncludeFontPadding(false);
    moveBadge.setGravity(Gravity.CENTER);
    moveBadge.setVisibility(INVISIBLE);
    moveBadge.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    labelSlot.addView(moveBadge, new FrameLayout.LayoutParams(px(68), px(22), Gravity.CENTER));
    addView(labelSlot, np);
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
    // False means focus-only. The label slot and accessible card name never change size.
    namesVisible = visible;
    updateName(false);
  }

  private void updateName(boolean delayed) {
    removeCallbacks(revealName);
    boolean visible = !moving && (namesVisible || hasFocus() || contextSelected);
    if (visible && delayed && !namesVisible && !contextSelected && nameAmount == 0) {
      postDelayed(revealName, rapid ? 140 : 90);
    } else animateName(visible);
  }

  private void animateName(boolean visible) {
    // A delayed reveal must never resurrect a label after its focus has moved away.
    if (visible && (moving || !(namesVisible || hasFocus() || contextSelected))) return;
    if (nameTransition != null) nameTransition.cancel();
    float target = visible ? 1 : 0;
    if (target == nameAmount) return;
    name.setVisibility(VISIBLE);
    nameTransition = ValueAnimator.ofFloat(nameAmount, target);
    nameTransition.setDuration(visible ? 120 : 80);
    nameTransition.setInterpolator(settle);
    nameTransition.addUpdateListener(
        a -> {
          nameAmount = (float) a.getAnimatedValue();
          name.setAlpha(nameAmount * (1 - previewAmount));
          name.setVisibility(nameAmount > 0 ? VISIBLE : INVISIBLE);
        });
    nameTransition.start();
  }

  public void rapidFocus(boolean rapid) {
    this.rapid = rapid;
  }

  public boolean focusHighlighted() {
    return hasFocus() || contextSelected;
  }

  public void focusBaselineLift(float pixels) {
    baselineLift = pixels;
    applyMotion();
  }

  public void moving(boolean value) {
    if (moving == value) return;
    moving = value;
    updateName(false);
    moveBadge.setVisibility(moving ? VISIBLE : INVISIBLE);
    if (android.os.Build.VERSION.SDK_INT >= 30) setStateDescription(moving ? "移动中" : null);
    if (moveTransition != null) moveTransition.cancel();
    if (!isAttachedToWindow()) {
      moveAmount = moving ? 1 : 0;
      applyMotion();
      return;
    }
    moveTransition = ValueAnimator.ofFloat(moveAmount, moving ? 1 : 0);
    moveTransition.setDuration(180);
    moveTransition.setInterpolator(settle);
    moveTransition.addUpdateListener(
        a -> {
          moveAmount = (float) a.getAnimatedValue();
          applyMotion();
        });
    moveTransition.start();
  }

  public void contextSelected(boolean selected) {
    contextSelected = selected;
    animatePress(0);
    updateFocus(hasFocus());
  }

  public void preview(float amount) {
    previewAmount = Math.max(0, Math.min(1, amount));
    float blur = .62f * previewAmount * previewAmount * (3 - 2 * previewAmount);
    normalImage.setAlpha(1 - blur);
    softImage.setAlpha(blur);
    name.setAlpha(nameAmount * (1 - previewAmount));
    surfaceAlpha();
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
    updateName(focused);
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
            Math.round(
                (focused ? (rapid ? 120 : 260) : 120)
                    * Math.min(1.1f, Math.abs(target - focusAmount)))));
    focusTransition.setInterpolator(focused && !rapid ? lift : settle);
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
    float size = 1 + .14f * focusAmount + .012f * moveAmount - .035f * pressAmount;
    body.setScaleX(size);
    body.setScaleY(size);
    // Compensate enlargement first, then lift the bottom by the same screen-space
    // distance on Home and search, regardless of their different card sizes.
    float liftY = (-px(78) * .5f * .14f - baselineLift) * focusAmount;
    body.setTranslationY(liftY + unit * (-3 * moveAmount + 3 * pressAmount));
    body.setTranslationZ(
        unit * ((palette.light ? 4 : 8) * light + (palette.light ? 2 : 4) * moveAmount));
    surfaceAlpha();
    // Let the caption accompany the lift without opening a large gap under the card.
    name.setTranslationY(unit * -4 * light);
    int from = palette.cardName, to = palette.cardNameFocused;
    int r = Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * light);
    int g = Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * light);
    int b = Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * light);
    name.setTextColor(Color.rgb(r, g, b));
    highlight.moving(moveAmount);
    highlight.setAlpha(Math.round(255 * light));
    invalidate();
  }

  private void surfaceAlpha() {
    body.setAlpha(
        (palette.light ? 1f : (.90f + .10f * Math.max(0, Math.min(1, focusAmount))))
            * (1 - .16f * previewAmount));
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
    p.setColor(palette.light ? 0xFFF7F7F5 : 0xFF111824);
    p.setShadowLayer(px(12), 0, px(9), palette.light ? 0x38000000 : 0xA0000000);
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
              Color.argb(
                  palette.light ? 5 : 26,
                  Color.red(accent),
                  Color.green(accent),
                  Color.blue(accent)),
              Color.argb(
                  palette.light ? 2 : 9,
                  Color.red(accent),
                  Color.green(accent),
                  Color.blue(accent)),
              0
            },
            new float[] {0, .48f, 1},
            Shader.TileMode.CLAMP));
    floor.scale(1, .25f, 80, 20);
    floor.drawRect(0, -80, 160, 120, light);
    floorBounds.set(getWidth() / 2f - px(80), px(78), getWidth() / 2f + px(80), px(118));
  }

  /** One shared shadow texture for every resting light card, independent of screen density. */
  private static void prepareRestingShadow() {
    if (restingShadow != null) return;
    restingShadow = Bitmap.createBitmap(336, 252, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(restingShadow);
    c.scale(2, 2);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(0xFFF6F6F4);
    p.setShadowLayer(7, 0, 4, 0x1C000000);
    c.drawRoundRect(new RectF(24, 24, 144, 102), 12, 12, p);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (palette.light && restingShadow != null) {
      glowPaint.setAlpha(Math.round((1 - .65f * previewAmount) * 255));
      int save = canvas.save();
      canvas.translate(0, body.getTranslationY());
      canvas.scale(body.getScaleX(), body.getScaleY(), getWidth() / 2f, px(47));
      restingBounds.set(getWidth() / 2f - px(84), -px(16), getWidth() / 2f + px(84), px(110));
      canvas.drawBitmap(restingShadow, null, restingBounds, glowPaint);
      canvas.restoreToCount(save);
    }
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
    removeCallbacks(revealName);
    if (nameTransition != null) nameTransition.cancel();
    if (focusTransition != null) focusTransition.cancel();
    if (pressTransition != null) pressTransition.cancel();
    if (moveTransition != null) moveTransition.cancel();
    super.onDetachedFromWindow();
  }

  /** Geometry and gradients are cached; only opacity changes during a focus transition. */
  private final class SurfaceHighlight extends Drawable {
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG),
        sheen = new Paint(Paint.ANTI_ALIAS_FLAG),
        editRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final boolean light;
    private float editAmount;
    private int opacity;

    SurfaceHighlight(boolean light) {
      this.light = light;
      rim.setStyle(Paint.Style.STROKE);
      rim.setStrokeWidth(Math.max(1, unit * .65f));
      editRim.setStyle(Paint.Style.STROKE);
      editRim.setStrokeWidth(Math.max(1, unit * 1.05f));
      editRim.setColor(palette.light ? 0xFF617B9C : 0xFFD5E6FF);
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
              light ? 0x286C7C92 : 0x40FFFFFF,
              light ? 0x1064748B : 0x0CFFFFFF,
              Shader.TileMode.CLAMP));
      sheen.setShader(
          new LinearGradient(
              0,
              b.top,
              0,
              bottom,
              light ? 0x01FFFFFF : 0x09FFFFFF,
              0x00FFFFFF,
              Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
      c.drawRoundRect(bounds, px(12), px(12), sheen);
      c.drawRoundRect(bounds, px(12), px(12), rim);
      if (editAmount > 0) {
        editRim.setAlpha(Math.round(opacity * .72f * editAmount));
        c.drawRoundRect(bounds, px(12), px(12), editRim);
      }
    }

    public void moving(float amount) {
      editAmount = amount;
    }

    @Override
    public void setAlpha(int alpha) {
      opacity = alpha;
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
