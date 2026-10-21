package com.jianping.tv.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;
import com.jianping.tv.R;

/** Shared grouped settings row: navigation, action and switch keep the same visual rhythm. */
public final class SettingsRowView extends LinearLayout {
  private final float scale;
  private final TextView label, value;
  private final ImageView icon, chevron;
  private final Switch toggle;
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
      Context c, float scale, String title, int resource, String detail, boolean isToggle) {
    super(c);
    this.scale = scale;
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
    icon.setColorFilter(0xFFE0E6EF);
    addView(icon, new LayoutParams(px(23), px(23)));
    label = text(c, 17, 0xFFE7ECF4);
    label.setText(title);
    label.setSingleLine(true);
    LayoutParams lp = new LayoutParams(0, -1, 1);
    lp.leftMargin = px(20);
    addView(label, lp);
    value = text(c, 14, 0xFF9EADBF);
    value.setText(detail);
    addView(value, new LayoutParams(-2, -1));
    chevron = new ImageView(c);
    chevron.setImageResource(R.drawable.nav_forward);
    chevron.setColorFilter(0xFF9EADBF);
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
          bg.setColor(focus ? 0xFFE7ECF3 : Color.TRANSPARENT);
          setBackground(bg);
          label.setTextColor(focus ? 0xFF192333 : 0xFFE7ECF4);
          value.setTextColor(focus ? 0xFF596779 : 0xFF9EADBF);
          icon.setColorFilter(focus ? 0xFF253247 : 0xFFE0E6EF);
          chevron.setColorFilter(focus ? 0xFF657386 : 0xFF9EADBF);
          updateToggleColors(focus);
          animate()
              .scaleX(focus ? 1.015f : 1)
              .scaleY(focus ? 1.015f : 1)
              .translationZ(focus ? px(2) : 0)
              .setDuration(170)
              .start();
        });
  }

  public void checked(boolean checked) {
    toggle.setChecked(checked);
    updateToggleColors(hasFocus());
    setContentDescription(title + "，" + (checked ? "已开启" : "已关闭"));
  }

  private void updateToggleColors(boolean focused) {
    toggle.setThumbTintList(
        ColorStateList.valueOf(focused && !toggle.isChecked() ? 0xFF65758B : 0xFFE6ECF4));
    toggle.setTrackTintList(
        new ColorStateList(
            new int[][] {new int[] {android.R.attr.state_checked}, new int[] {}},
            new int[] {focused ? 0xFF215D47 : 0xFF739A8C, focused ? 0xFF8693A5 : 0xFF424F62}));
  }
}
