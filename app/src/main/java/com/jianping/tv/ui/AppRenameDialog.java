package com.jianping.tv.ui;

import android.app.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.*;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import com.jianping.tv.R;
import com.jianping.tv.data.*;
import java.util.*;

/** Shared TV editor: local names only, explicit save, and Back dismisses the IME first. */
public final class AppRenameDialog extends Dialog {
  private final Activity activity;
  private final AppEntry app;
  private final AppNames names;
  private final UiPalette palette;
  private final float unit;
  private final Runnable saved;
  private EditText input;
  private TextView error;
  private boolean keyboardRequested;
  private final Runnable keyboardTask = this::showKeyboard;

  public AppRenameDialog(
      Activity activity,
      AppEntry app,
      AppNames names,
      UiPalette palette,
      float unit,
      Runnable saved) {
    super(activity);
    this.activity = activity;
    this.app = app;
    this.names = names;
    this.palette = palette;
    this.unit = unit;
    this.saved = saved;
  }

  private int px(float value) {
    return Math.round(unit * value);
  }

  @Override
  protected void onCreate(Bundle state) {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    super.onCreate(state);
    setCanceledOnTouchOutside(false);
    LinearLayout panel = new LinearLayout(activity);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(px(22), px(16), px(22), px(14));
    GradientDrawable surface =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {palette.menuTop, palette.menuBottom});
    surface.setCornerRadius(px(20));
    surface.setStroke(Math.max(1, px(.5f)), palette.outline);
    panel.setBackground(surface);
    panel.addView(text("重命名", 21, palette.text), new LinearLayout.LayoutParams(-1, px(30)));
    panel.addView(
        text("仅修改 Still 中显示的名称", 12, palette.secondary), new LinearLayout.LayoutParams(-1, px(24)));
    input =
        new EditText(activity) {
          @Override
          public boolean onKeyPreIme(int code, KeyEvent event) {
            if (code == KeyEvent.KEYCODE_BACK && keyboardRequested) {
              if (event.getAction() == KeyEvent.ACTION_UP && !event.isCanceled()) hideKeyboard();
              return true;
            }
            return super.onKeyPreIme(code, event);
          }
        };
    input.setId(View.generateViewId());
    input.setContentDescription("应用显示名称");
    input.setSingleLine(true);
    input.setTextColor(palette.text);
    input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(18));
    input.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    input.setPadding(px(12), 0, px(12), 0);
    input.setBackground(round(palette.light ? 0xFFE8EBEF : 0xFF17212F, 10));
    input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    input.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    input.setText(names.label(app));
    input.setFilters(
        new InputFilter[] {
          (source, start, end, dest, dstart, dend) -> {
            int available =
                AppNames.MAX_LENGTH
                    - Character.codePointCount(dest, 0, dstart)
                    - Character.codePointCount(dest, dend, dest.length());
            int count = Character.codePointCount(source, start, end);
            if (count <= available) return null;
            if (available <= 0) return "";
            return source.subSequence(
                start, Character.offsetByCodePoints(source, start, available));
          }
        });
    input.selectAll();
    LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, px(46));
    ip.topMargin = px(8);
    panel.addView(input, ip);
    TextView original = text("原名称：" + app.label, 11, palette.secondary);
    original.setSingleLine(true);
    original.setEllipsize(TextUtils.TruncateAt.END);
    panel.addView(original, new LinearLayout.LayoutParams(-1, px(24)));
    error = text("", 11, palette.light ? 0xFF9C4D4F : 0xFFE0A3A3);
    panel.addView(error, new LinearLayout.LayoutParams(-1, px(20)));
    LinearLayout actions = new LinearLayout(activity);
    actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    List<View> buttons = new ArrayList<>();
    if (!names.custom(app).isEmpty()) buttons.add(button("恢复原名", () -> save(app.label)));
    buttons.add(button("取消", this::cancel));
    TextView confirm = button("保存", () -> save(input.getText().toString()));
    buttons.add(confirm);
    for (int i = 0; i < buttons.size(); i++) {
      View button = buttons.get(i);
      LinearLayout.LayoutParams bp =
          new LinearLayout.LayoutParams(px(i == 0 && buttons.size() == 3 ? 94 : 72), px(38));
      if (i > 0) bp.leftMargin = px(8);
      actions.addView(button, bp);
      button.setNextFocusUpId(input.getId());
      button.setNextFocusDownId(button.getId());
      button.setNextFocusLeftId(buttons.get(Math.max(0, i - 1)).getId());
      button.setNextFocusRightId(buttons.get(Math.min(buttons.size() - 1, i + 1)).getId());
    }
    input.setNextFocusDownId(confirm.getId());
    input.setOnClickListener(v -> showKeyboard());
    input.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_DONE) {
            hideKeyboard();
            confirm.requestFocus();
            return true;
          }
          return false;
        });
    panel.addView(actions, new LinearLayout.LayoutParams(-1, px(42)));
    setContentView(panel);
    Window window = getWindow();
    window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    WindowManager.LayoutParams params = window.getAttributes();
    params.dimAmount = palette.light ? .12f : .45f;
    params.width =
        Math.min(px(440), activity.getResources().getDisplayMetrics().widthPixels - px(48));
    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
    params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
    params.y = px(24);
    window.setAttributes(params);
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    window
        .getDecorView()
        .setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    input.requestFocus();
    input.postDelayed(keyboardTask, 180);
  }

  private void showKeyboard() {
    if (!isShowing() || input == null) return;
    keyboardRequested = true;
    input.requestFocus();
    ((InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
        .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
  }

  private void hideKeyboard() {
    keyboardRequested = false;
    if (input != null)
      ((InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(input.getWindowToken(), 0);
  }

  private void save(String value) {
    try {
      if (!names.save(app, value)) {
        error.setText("未能保存，请重试");
        return;
      }
      hideKeyboard();
      dismiss();
      saved.run();
    } catch (IllegalArgumentException e) {
      error.setText(activity.getString(R.string.rename_invalid_name, AppNames.MAX_LENGTH));
    } catch (PackageManager.NameNotFoundException e) {
      error.setText("应用已变更，请取消后重试");
    }
  }

  @Override
  public void onBackPressed() {
    if (keyboardRequested) hideKeyboard();
    else super.onBackPressed();
  }

  @Override
  public void dismiss() {
    hideKeyboard();
    if (input != null) input.removeCallbacks(keyboardTask);
    super.dismiss();
  }

  private TextView text(String value, int size, int color) {
    TextView view = new TextView(activity);
    view.setText(value);
    view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
    view.setTextColor(color);
    view.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
    view.setIncludeFontPadding(false);
    view.setGravity(Gravity.CENTER_VERTICAL);
    return view;
  }

  private TextView button(String label, Runnable action) {
    TextView view = text(label, 15, palette.text);
    view.setGravity(Gravity.CENTER);
    view.setId(View.generateViewId());
    view.setContentDescription(label);
    view.setFocusable(true);
    view.setClickable(true);
    view.setBackground(round(palette.headerFocus, 10));
    view.setOnFocusChangeListener(
        (v, focused) -> {
          view.setBackground(round(focused ? palette.rowFocus : palette.headerFocus, 10));
          view.setTextColor(focused ? palette.rowFocusText : palette.text);
        });
    view.setOnClickListener(v -> action.run());
    return view;
  }

  private GradientDrawable round(int color, int radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(px(radius));
    return drawable;
  }
}
