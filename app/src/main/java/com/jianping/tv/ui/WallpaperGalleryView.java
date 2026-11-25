package com.jianping.tv.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.*;
import com.jianping.tv.R;
import java.util.ArrayList;
import java.util.List;

/**
 * TV Immersive Wallpaper Gallery with horizontal centered card stream,
 * 100ms debounced live try-on, 384x216 thumbnail memory boundary, and seamless OK/Back key lifecycle.
 */
public final class WallpaperGalleryView extends FrameLayout {

  public interface WallpaperGalleryListener {
    void onWallpaperPreview(String key);
    void onWallpaperConfirmed(String key);
    void onWallpaperCanceled(String originalKey);
  }

  private final float scale;
  private final String[] themeKeys;
  private final String[] themeNames;
  private final String initialKey;
  private final WallpaperGalleryListener listener;
  private final UiPalette palette;

  private final View scrim;
  private final LinearLayout contentPanel;
  private final HorizontalScrollView scrollView;
  private final TextView indicatorText;
  private final List<View> cardViews = new ArrayList<>();
  private final List<View> borderOverlays = new ArrayList<>();
  private final List<TextView> cardLabels = new ArrayList<>();

  private final Handler handler = new Handler(Looper.getMainLooper());
  private String currentPreviewKey;
  private int currentIndex = 0;
  private boolean isDismissing = false;
  private final PathInterpolator settle = new PathInterpolator(0.18f, 0.85f, 0.25f, 1f);

  private final Runnable previewRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (listener != null && !isDismissing) {
            listener.onWallpaperPreview(currentPreviewKey);
          }
        }
      };

  public static int themeThumbnail(String key) {
    return WallpaperTheme.find(key).thumbRes;
  }

  private int px(float n) {
    return Math.round(n * scale);
  }

  private TextView text(Context c, float sizeSp, int color, boolean bold) {
    TextView t = new TextView(c);
    t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(sizeSp));
    t.setTextColor(color);
    t.setIncludeFontPadding(false);
    t.setGravity(Gravity.CENTER);
    t.setTypeface(
        bold
            ? Typeface.create("sans-serif-medium", Typeface.NORMAL)
            : Typeface.create("sans-serif", Typeface.NORMAL));
    return t;
  }

  public WallpaperGalleryView(
      Context context,
      float scale,
      String[] themeKeys,
      String[] themeNames,
      String currentKey,
      WallpaperGalleryListener listener,
      UiPalette palette) {
    super(context);
    this.scale = scale;
    this.themeKeys = themeKeys;
    this.themeNames = themeNames;
    this.initialKey = currentKey;
    this.currentPreviewKey = currentKey;
    this.listener = listener;
    this.palette = palette;

    setFocusable(true);
    setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
    setClipChildren(false);
    setClipToPadding(false);

    // 1. Multi-stop non-linear gradient scrim preserving 1080P wallpaper atmospheric depth while anchoring card contrast
    scrim = new View(context);
    GradientDrawable scrimGrad =
        new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] {0x4D000000, 0x1A000000, 0x33000000, 0x80000000, 0xD4000000});
    scrim.setBackground(scrimGrad);
    addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    // 2. Vertical content container
    contentPanel = new LinearLayout(context);
    contentPanel.setOrientation(LinearLayout.VERTICAL);
    contentPanel.setGravity(Gravity.CENTER_HORIZONTAL);
    contentPanel.setClipChildren(false);
    contentPanel.setClipToPadding(false);
    contentPanel.setPadding(0, px(38), 0, px(28));

    // Header: Title + Subtitle
    TextView title = text(context, 22, 0xFFFFFFFF, true);
    title.setText("外观与壁纸画廊");
    title.setShadowLayer(px(6), 0, px(2), 0xD9000000);
    contentPanel.addView(title, new LinearLayout.LayoutParams(-2, -2));

    TextView subtitle = text(context, 13, 0xB3FFFFFF, false);
    subtitle.setText("左右浏览实时试穿 · 按确定保存壁纸 · 按返回取消");
    subtitle.setShadowLayer(px(4), 0, px(1.5f), 0xB3000000);
    LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-2, -2);
    subLp.topMargin = px(6);
    subLp.bottomMargin = px(24);
    contentPanel.addView(subtitle, subLp);

    // Horizontal card stream
    scrollView = new HorizontalScrollView(context);
    scrollView.setHorizontalScrollBarEnabled(false);
    scrollView.setOverScrollMode(OVER_SCROLL_NEVER);
    scrollView.setClipChildren(false);
    scrollView.setClipToPadding(false);

    LinearLayout cardsRow = new LinearLayout(context);
    cardsRow.setOrientation(LinearLayout.HORIZONTAL);
    cardsRow.setGravity(Gravity.CENTER_VERTICAL);
    cardsRow.setClipChildren(false);
    cardsRow.setClipToPadding(false);

    // Padding left & right: (960 - 240) / 2 = 360 units to center any focused card
    int sidePad = px(360);
    cardsRow.setPadding(sidePad, px(18), sidePad, px(18));

    for (int i = 0; i < themeKeys.length; i++) {
      final int index = i;
      final String key = themeKeys[i];
      final String name = themeNames[i];

      LinearLayout card = new LinearLayout(context);
      card.setOrientation(LinearLayout.VERTICAL);
      card.setGravity(Gravity.CENTER_HORIZONTAL);
      card.setFocusable(true);
      card.setClickable(true);
      card.setClipChildren(false);
      card.setClipToPadding(false);
      card.setContentDescription(name + (key.equals(initialKey) ? "，当前正在使用" : "") + "，按确定应用此壁纸");

      // Card Body (240x135dp, 16:9)
      FrameLayout body = new FrameLayout(context);
      body.setClipChildren(false);
      body.setOutlineProvider(
          new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
              outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(12));
            }
          });
      body.setClipToOutline(true);

      GradientDrawable bodyBg = new GradientDrawable();
      bodyBg.setColor(0xFF1B1D22);
      bodyBg.setCornerRadius(px(12));
      body.setBackground(bodyBg);

      // WebP thumbnail (384x216, scaleType CENTER_CROP)
      ImageView thumb = new ImageView(context);
      thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
      thumb.setImageResource(themeThumbnail(key));
      body.addView(thumb, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

      // "当前应用" badge
      if (key.equals(initialKey)) {
        TextView curBadge = text(context, 11, 0xFFFFFFFF, true);
        curBadge.setText("当前应用");
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(0xCC000000);
        badgeBg.setCornerRadius(px(6));
        badgeBg.setStroke(px(1), 0x66FFFFFF);
        curBadge.setBackground(badgeBg);
        curBadge.setPadding(px(7), px(2), px(7), px(2));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(-2, -2);
        bp.gravity = Gravity.TOP | Gravity.END;
        bp.topMargin = px(8);
        bp.rightMargin = px(8);
        body.addView(curBadge, bp);
      }

      // 2dp Pearl white highlight stroke overlay (#FFFFFF)
      View highlightBorder = new View(context);
      highlightBorder.setBackground(createBorderDrawable(false));
      body.addView(highlightBorder, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
      borderOverlays.add(highlightBorder);

      card.addView(body, new LinearLayout.LayoutParams(px(240), px(135)));

      // Label below card: centered full theme name
      TextView label = text(context, 14, 0xFFFFFFFF, true);
      label.setText(name);
      label.setShadowLayer(px(5), 0, px(2), 0xCC000000);
      label.setAlpha(0.68f);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(240), px(28));
      lp.topMargin = px(10);
      card.addView(label, lp);
      cardLabels.add(label);

      // Card spacing: 20dp between cards
      LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(px(240), -2);
      if (i < themeKeys.length - 1) {
        cardLp.rightMargin = px(20);
      }

      card.setOnFocusChangeListener(
          (v, hasFocus) -> {
            updateCardFocus(index, hasFocus);
          });

      card.setOnClickListener(v -> confirmAndDismiss());

      card.setOnKeyListener(
          (v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
              if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                return true; // prevent stray focus jumps outside card stream
              }
            }
            return false;
          });

      cardsRow.addView(card, cardLp);
      cardViews.add(card);

      if (key.equals(initialKey)) {
        currentIndex = i;
      }
    }

    scrollView.addView(cardsRow, new LayoutParams(-2, -2));
    LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(205));
    contentPanel.addView(scrollView, scrollLp);

    // Indicator & Hints at bottom
    indicatorText = text(context, 14, 0xFFFFFFFF, true);
    indicatorText.setShadowLayer(px(4), 0, px(1.5f), 0xCC000000);
    LinearLayout.LayoutParams indLp = new LinearLayout.LayoutParams(-2, -2);
    indLp.topMargin = px(16);
    contentPanel.addView(indicatorText, indLp);

    TextView tip = text(context, 12, 0x99FFFFFF, false);
    tip.setText("按确定键保存所选壁纸  ·  按返回键退出恢复");
    tip.setShadowLayer(px(3), 0, px(1), 0x80000000);
    LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(-2, -2);
    tipLp.topMargin = px(6);
    contentPanel.addView(tip, tipLp);

    addView(contentPanel, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    updateIndicator(currentIndex);
  }

  private GradientDrawable createBorderDrawable(boolean focused) {
    GradientDrawable d = new GradientDrawable();
    d.setCornerRadius(px(12));
    if (focused) {
      // 2dp 珍珠白高光描边 (#FFFFFF)
      d.setStroke(px(2), 0xFFFFFFFF);
      d.setColor(0x00000000);
    } else {
      d.setStroke(Math.max(1, px(1)), 0x24FFFFFF);
      d.setColor(0x00000000);
    }
    return d;
  }

  private void updateIndicator(int index) {
    if (index >= 0 && index < themeKeys.length) {
      indicatorText.setText((index + 1) + " / " + themeKeys.length + "   " + themeNames[index]);
    }
  }

  private void updateCardFocus(int index, boolean focused) {
    if (index < 0 || index >= cardViews.size()) return;
    View card = cardViews.get(index);
    View border = borderOverlays.get(index);
    TextView label = cardLabels.get(index);

    border.setBackground(createBorderDrawable(focused));

    if (focused) {
      currentIndex = index;
      currentPreviewKey = themeKeys[index];
      updateIndicator(index);

      // Focus Zoom 1.08x + elevation lift
      card.animate()
          .scaleX(1.08f)
          .scaleY(1.08f)
          .translationZ(px(10))
          .setDuration(160)
          .setInterpolator(new DecelerateInterpolator())
          .start();
      label.animate().alpha(1.0f).setDuration(160).start();

      // Smooth center scroll: exactly aligns card center with 20dp margin
      int targetScroll = Math.round(index * px(240 + 20));
      scrollView.smoothScrollTo(targetScroll, 0);

      // Debounced live try-on (100ms)
      handler.removeCallbacks(previewRunnable);
      handler.postDelayed(previewRunnable, 100);
    } else {
      card.animate()
          .scaleX(1.0f)
          .scaleY(1.0f)
          .translationZ(0)
          .setDuration(160)
          .setInterpolator(new DecelerateInterpolator())
          .start();
      label.animate().alpha(0.68f).setDuration(160).start();
    }
  }

  public void reveal() {
    scrim.setAlpha(0f);
    scrim.animate().alpha(1f).setDuration(180).start();

    contentPanel.setAlpha(0f);
    contentPanel.setTranslationY(px(28));
    contentPanel.animate()
        .alpha(1f)
        .translationY(0)
        .setDuration(220)
        .setInterpolator(settle)
        .start();

    // Scroll to current wallpaper and request focus
    post(
        () -> {
          int initialScroll = Math.round(currentIndex * px(240 + 20));
          scrollView.scrollTo(initialScroll, 0);
          if (currentIndex >= 0 && currentIndex < cardViews.size()) {
            cardViews.get(currentIndex).requestFocus();
          }
        });
  }

  public void confirmAndDismiss() {
    if (isDismissing) return;
    isDismissing = true;
    handler.removeCallbacks(previewRunnable);

    if (listener != null) {
      listener.onWallpaperConfirmed(currentPreviewKey);
    }
    dismissAnimate();
  }

  public void cancelAndDismiss() {
    if (isDismissing) return;
    isDismissing = true;
    handler.removeCallbacks(previewRunnable);

    if (listener != null) {
      listener.onWallpaperCanceled(initialKey);
    }
    dismissAnimate();
  }

  private void dismissAnimate() {
    scrim.animate().alpha(0f).setDuration(140).start();
    contentPanel.animate()
        .alpha(0f)
        .translationY(px(20))
        .setDuration(140)
        .setListener(
            new AnimatorListenerAdapter() {
              @Override
              public void onAnimationEnd(Animator animation) {
                ViewGroup parent = (ViewGroup) getParent();
                if (parent != null) {
                  parent.removeView(WallpaperGalleryView.this);
                }
              }
            })
        .start();
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (event.getAction() == KeyEvent.ACTION_DOWN) {
      if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
        cancelAndDismiss();
        return true;
      } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
          || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
        confirmAndDismiss();
        return true;
      }
    }
    return super.dispatchKeyEvent(event);
  }
}
