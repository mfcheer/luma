package com.jianping.tv.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.*;
import java.util.*;

/** Reusable remote-only context panel with isolated focus and finite entrance/exit motion. */
public final class AppActionMenuView extends FrameLayout {
  public static final class Item {
    final String label;
    final int icon;
    final boolean destructive;
    final Runnable action;

    public Item(String label, int icon, boolean destructive, Runnable action) {
      this.label = label;
      this.icon = icon;
      this.destructive = destructive;
      this.action = action;
    }
  }

  public interface Selection {
    void selected(Runnable action);
  }

  private final float unit;
  private final View scrim;
  private final LinearLayout panel;
  private final PathInterpolator settle = new PathInterpolator(.18f, .85f, .25f, 1);
  private boolean closing, selected;

  private int px(float value) {
    return Math.round(value * unit);
  }

  public AppActionMenuView(
      Context c,
      float unit,
      String title,
      Drawable artwork,
      List<Item> items,
      Selection selection) {
    super(c);
    this.unit = unit;
    setClipChildren(false);
    setContentDescription("应用操作");
    scrim = new View(c);
    scrim.setBackgroundColor(0xAD040810);
    scrim.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    addView(scrim, new LayoutParams(-1, -1));
    panel = new LinearLayout(c);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(px(14), px(14), px(14), px(14));
    panel.setClipChildren(false);
    panel.setClipToPadding(false);
    GradientDrawable glass =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {0xFF293546, 0xFF192331});
    glass.setCornerRadius(px(24));
    glass.setStroke(Math.max(1, px(.5f)), 0x36CFDCEE);
    panel.setBackground(glass);
    panel.setElevation(px(24));
    LayoutParams pp = new LayoutParams(px(352), -2, Gravity.CENTER);
    addView(panel, pp);
    FrameLayout header = new FrameLayout(c);
    ImageView thumb = new ImageView(c);
    thumb.setImageDrawable(artwork);
    thumb.setScaleType(ImageView.ScaleType.FIT_XY);
    thumb.setBackground(round(0xFF28364A, 9));
    thumb.setClipToOutline(true);
    thumb.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    LayoutParams tp = new LayoutParams(px(76), px(50));
    tp.leftMargin = px(8);
    tp.topMargin = px(9);
    header.addView(thumb, tp);
    TextView heading = text(c, title, 20, 0xFFF3F6FA);
    heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    LayoutParams hp = new LayoutParams(px(214), px(30));
    hp.leftMargin = px(101);
    hp.topMargin = px(8);
    header.addView(heading, hp);
    TextView subtitle = text(c, "快捷操作", 12, 0xFF9FADC0);
    LayoutParams sp = new LayoutParams(px(214), px(20));
    sp.leftMargin = px(101);
    sp.topMargin = px(40);
    header.addView(subtitle, sp);
    panel.addView(header, new LinearLayout.LayoutParams(-1, px(76)));
    View separator = new View(c);
    separator.setBackgroundColor(0x1AF2F6FF);
    LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, Math.max(1, px(.5f)));
    dp.setMargins(px(8), 0, px(8), px(12));
    panel.addView(separator, dp);
    List<View> controls = new ArrayList<>();
    for (Item item : items) {
      LinearLayout row = new LinearLayout(c);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.setGravity(Gravity.CENTER_VERTICAL);
      row.setPadding(px(16), 0, px(16), 0);
      row.setFocusable(true);
      row.setClickable(true);
      row.setContentDescription(item.label);
      row.setId(View.generateViewId());
      ImageView icon = new ImageView(c);
      icon.setImageResource(item.icon);
      icon.setColorFilter(item.destructive ? 0xFFE6C2C2 : 0xFFBECBDD);
      icon.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      row.addView(icon, new LinearLayout.LayoutParams(px(22), px(22)));
      TextView label = text(c, item.label, 17, 0xFFE4EAF3);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
      lp.leftMargin = px(15);
      row.addView(label, lp);
      GradientDrawable focus =
          new GradientDrawable(
              GradientDrawable.Orientation.TL_BR, new int[] {0xFFF6F8FB, 0xFFDDE5EF});
      focus.setCornerRadius(px(13));
      focus.setAlpha(0);
      row.setBackground(focus);
      row.setOnFocusChangeListener(
          (v, focused) -> {
            row.animate().cancel();
            int start = focus.getAlpha(), end = focused ? 255 : 0;
            row.animate()
                .scaleX(focused ? 1.018f : 1)
                .scaleY(focused ? 1.018f : 1)
                .translationZ(focused ? px(3) : 0)
                .setDuration(focused ? 220 : 150)
                .setInterpolator(settle)
                .setUpdateListener(
                    a -> {
                      float t = a.getAnimatedFraction();
                      int alpha = Math.round(start + (end - start) * t);
                      focus.setAlpha(alpha);
                      float amount = alpha / 255f;
                      label.setTextColor(
                          blend(0xFFE4EAF3, item.destructive ? 0xFF713D40 : 0xFF172538, amount));
                      icon.setColorFilter(
                          blend(
                              item.destructive ? 0xFFE6C2C2 : 0xFFBECBDD,
                              item.destructive ? 0xFF875155 : 0xFF334760,
                              amount));
                    })
                .start();
          });
      row.setOnClickListener(
          v -> {
            if (selected || closing) return;
            selected = true;
            selection.selected(item.action);
          });
      LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, px(52));
      if (!controls.isEmpty()) rp.topMargin = px(4);
      panel.addView(row, rp);
      controls.add(row);
    }
    for (int i = 0; i < controls.size(); i++) {
      View row = controls.get(i);
      row.setNextFocusUpId(controls.get(Math.max(0, i - 1)).getId());
      row.setNextFocusDownId(controls.get(Math.min(controls.size() - 1, i + 1)).getId());
      row.setNextFocusLeftId(row.getId());
      row.setNextFocusRightId(row.getId());
    }
    scrim.setAlpha(0);
    panel.setAlpha(0);
    panel.setScaleX(.94f);
    panel.setScaleY(.94f);
    panel.setTranslationY(px(12));
    controls.get(0).requestFocus();
  }

  private TextView text(Context c, String value, float size, int color) {
    TextView t = new TextView(c);
    t.setText(value);
    t.setTextColor(color);
    t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
    t.setIncludeFontPadding(false);
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setSingleLine(true);
    t.setEllipsize(TextUtils.TruncateAt.END);
    t.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    return t;
  }

  private GradientDrawable round(int color, float radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(px(radius));
    return d;
  }

  private int blend(int from, int to, float amount) {
    return Color.rgb(
        Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * amount),
        Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * amount),
        Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * amount));
  }

  public void reveal() {
    scrim.animate().alpha(1).setDuration(220).start();
    panel
        .animate()
        .alpha(1)
        .scaleX(1)
        .scaleY(1)
        .translationY(0)
        .setDuration(320)
        .setInterpolator(settle)
        .start();
  }

  public void dismiss(Runnable done) {
    if (closing) return;
    closing = true;
    setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
    scrim.animate().cancel();
    panel.animate().cancel();
    scrim.animate().alpha(0).setDuration(180).start();
    panel
        .animate()
        .alpha(0)
        .scaleX(.97f)
        .scaleY(.97f)
        .translationY(px(8))
        .setDuration(180)
        .setInterpolator(settle)
        .withEndAction(done)
        .start();
  }

  @Override
  protected void onDetachedFromWindow() {
    scrim.animate().cancel();
    panel.animate().cancel();
    for (int i = 0; i < panel.getChildCount(); i++) panel.getChildAt(i).animate().cancel();
    super.onDetachedFromWindow();
  }
}
