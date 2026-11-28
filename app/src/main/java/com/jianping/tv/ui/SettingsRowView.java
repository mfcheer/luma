package com.jianping.tv.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.*;
import com.jianping.tv.R;

/** Shared grouped settings row: navigation, action and switch keep the same visual rhythm. */
public final class SettingsRowView extends LinearLayout {
  private final float scale;
  private final UiPalette palette;
  private final TextView label, value;
  private final ImageView icon, chevron;
  private ImageView previous, next;
  private final Switch toggle;
  private final FrameLayout thumbContainer;
  private final ImageView previewThumb;
  private final String title;

  private int px(float n) {
    return Math.round(n * scale);
  }

  private TextView text(Context c, float size, int color) {
    TextView t = new TextView(c);
    t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
    t.setTextColor(color);
    t.setIncludeFontPadding(false);
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setTypeface(android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL));
    return t;
  }

  public SettingsRowView(
      Context c,
      float scale,
      String title,
      int resource,
      String detail,
      boolean isToggle,
      UiPalette palette) {
    super(c);
    this.scale = scale;
    this.palette = palette;
    palette.styleShadow(this);
    this.title = title;
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    setPadding(px(22), 0, px(20), 0);
    setClipChildren(false);
    setFocusable(true);
    setClickable(true);
    setContentDescription(title);
    icon = new ImageView(c);
    icon.setImageResource(resource);
    icon.setColorFilter(palette.icon);
    addView(icon, new LayoutParams(px(23), px(23)));
    label = text(c, 17, palette.text);
    label.setText(title);
    label.setSingleLine(true);
    LayoutParams lp = new LayoutParams(0, -1, 1);
    lp.leftMargin = px(20);
    addView(label, lp);

    thumbContainer = new FrameLayout(c);
    thumbContainer.setVisibility(GONE);
    previewThumb = new ImageView(c);
    previewThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
    previewThumb.setOutlineProvider(
        new ViewOutlineProvider() {
          @Override
          public void getOutline(View view, Outline outline) {
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(6));
          }
        });
    previewThumb.setClipToOutline(true);
    GradientDrawable thumbStroke = new GradientDrawable();
    thumbStroke.setCornerRadius(px(6));
    thumbStroke.setStroke(px(1), palette.light ? 0x26000000 : 0x33FFFFFF);
    previewThumb.setForeground(thumbStroke);
    thumbContainer.addView(previewThumb, new FrameLayout.LayoutParams(-1, -1));
    LayoutParams tp = new LayoutParams(px(64), px(36));
    tp.rightMargin = px(12);
    addView(thumbContainer, tp);

    value = text(c, 14, palette.secondary);
    value.setText(detail);
    addView(value, new LayoutParams(-2, -1));
    chevron = new ImageView(c);
    chevron.setImageResource(R.drawable.nav_forward);
    chevron.setColorFilter(palette.secondary);
    LayoutParams cp = new LayoutParams(px(18), px(18));
    cp.leftMargin = px(12);
    if (!isToggle) addView(chevron, cp);
    toggle = new Switch(c);
    if (isToggle) {
      toggle.setFocusable(false);
      toggle.setClickable(false);
      toggle.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
      updateToggleColors(false);
      addView(toggle, new LayoutParams(px(49), px(32)));
    }
    setOnFocusChangeListener(
        (v, focus) -> {
          GradientDrawable bg = new GradientDrawable();
          bg.setCornerRadius(px(12));
          bg.setColor(focus ? palette.rowFocus : Color.TRANSPARENT);
          setBackground(bg);
          label.setTextColor(focus ? palette.rowFocusText : palette.text);
          value.setTextColor(focus ? 0xFF596779 : palette.secondary);
          icon.setColorFilter(focus ? palette.rowFocusIcon : palette.icon);
          int arrowColor = focus ? 0xFF657386 : palette.secondary;
          chevron.setColorFilter(arrowColor);
          if (previous != null) {
            previous.setColorFilter(arrowColor);
            next.setColorFilter(arrowColor);
          }
          GradientDrawable thumbBorder = new GradientDrawable();
          thumbBorder.setCornerRadius(px(6));
          thumbBorder.setStroke(
              px(focus ? 1.5f : 1),
              focus ? 0xFFFFFFFF : (palette.light ? 0x26000000 : 0x33FFFFFF));
          previewThumb.setForeground(thumbBorder);
          updateToggleColors(focus);
          animate()
              .scaleX(focus ? 1.015f : 1)
              .scaleY(focus ? 1.015f : 1)
              .translationZ(focus ? px(2) : 0)
              .setDuration(170)
              .start();
        });
  }

  public void stepper(Runnable onPrevious, Runnable onNext) {
    chevron.setVisibility(GONE);
    setClickable(false);
    setSoundEffectsEnabled(false);
    previous = stepButton(R.drawable.nav_back, title + "，切换到上一项", onPrevious);
    next = stepButton(R.drawable.nav_forward, title + "，切换到下一项", onNext);
    addView(previous, indexOfChild(value), new LayoutParams(px(36), -1));
    addView(next, indexOfChild(value) + 1, new LayoutParams(px(36), -1));
    setOnKeyListener(
        (view, key, event) -> {
          if (key != KeyEvent.KEYCODE_DPAD_LEFT && key != KeyEvent.KEYCODE_DPAD_RIGHT) {
            return false;
          }
          if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            (key == KeyEvent.KEYCODE_DPAD_LEFT ? onPrevious : onNext).run();
          }
          return true;
        });
  }

  private ImageView stepButton(int resource, String description, Runnable action) {
    ImageView button = new ImageView(getContext());
    button.setImageResource(resource);
    button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
    button.setColorFilter(isFocused() ? 0xFF657386 : palette.secondary);
    button.setContentDescription(description);
    button.setOnClickListener(view -> action.run());
    button.setFocusable(false);
    return button;
  }

  public void previewThumb(int resId) {
    if (resId != 0) {
      previewThumb.setImageResource(resId);
      thumbContainer.setVisibility(VISIBLE);
    } else {
      thumbContainer.setVisibility(GONE);
    }
  }

  public void preview(int resId) {
    previewThumb(resId);
  }

  public FrameLayout thumbContainer() {
    return thumbContainer;
  }

  public ImageView previewThumb() {
    return previewThumb;
  }

  public void detail(String detail) {
    value.setText(detail);
  }

  public void checked(boolean checked) {
    toggle.setChecked(checked);
    updateToggleColors(hasFocus());
    setContentDescription(title + "，" + (checked ? "已开启" : "已关闭"));
  }

  private void updateToggleColors(boolean focused) {
    toggle.setThumbTintList(
        ColorStateList.valueOf(
            !toggle.isChecked() && (focused || palette.light) ? 0xFF718095 : 0xFFF6F8FA));
    toggle.setTrackTintList(
        new ColorStateList(
            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
            new int[] {
              focused ? 0xFF215D47 : 0xFF739A8C,
              palette.light ? 0xFFB9C3CE : (focused ? 0xFF8693A5 : 0xFF424F62)
            }));
  }
}
