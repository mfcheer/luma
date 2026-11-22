package com.jianping.tv.browser;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.jianping.tv.ui.UiPalette;

/** Dialog for editing an existing web shortcut's label or URL. */
public final class WebShortcutEditDialog extends Dialog {

  public interface Callback {
    void onUpdated(WebShortcut shortcut);
  }

  private final Activity activity;
  private final WebShortcut shortcut;
  private final WebShortcutStore store;
  private final UiPalette palette;
  private final float unit;
  private final boolean focusUrlFirst;
  private final Callback callback;

  private EditText inputName;
  private EditText inputUrl;
  private TextView errorText;

  public WebShortcutEditDialog(
      Activity activity,
      WebShortcut shortcut,
      WebShortcutStore store,
      UiPalette palette,
      float unit,
      boolean focusUrlFirst,
      Callback callback) {
    super(activity);
    this.activity = activity;
    this.shortcut = shortcut;
    this.store = store;
    this.palette = palette;
    this.unit = unit;
    this.focusUrlFirst = focusUrlFirst;
    this.callback = callback;
  }

  private int px(float value) {
    return Math.round(unit * value);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    super.onCreate(savedInstanceState);
    setCanceledOnTouchOutside(true);

    LinearLayout root = new LinearLayout(activity);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(px(24), px(20), px(24), px(20));

    GradientDrawable bg =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {palette.menuTop, palette.menuBottom});
    bg.setCornerRadius(px(20));
    bg.setStroke(Math.max(1, px(.5f)), palette.outline);
    root.setBackground(bg);
    palette.styleShadow(root);

    TextView title = new TextView(activity);
    title.setText(focusUrlFirst ? "修改网页网址" : "重命名网页快捷方式");
    title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(18));
    title.setTextColor(palette.text);
    title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    root.addView(title, new LinearLayout.LayoutParams(-1, -2));

    TextView subtitle = new TextView(activity);
    subtitle.setText("修改后电视桌面卡片将即时更新");
    subtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    subtitle.setTextColor(palette.secondary);
    LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
    subLp.topMargin = px(4);
    subLp.bottomMargin = px(14);
    root.addView(subtitle, subLp);

    TextView nameLabel = new TextView(activity);
    nameLabel.setText("名称");
    nameLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    nameLabel.setTextColor(palette.secondary);
    root.addView(nameLabel, new LinearLayout.LayoutParams(-1, -2));

    inputName = createEditText();
    inputName.setText(shortcut.name);
    root.addView(inputName, new LinearLayout.LayoutParams(-1, px(38)));

    TextView urlLabel = new TextView(activity);
    urlLabel.setText("网址");
    urlLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    urlLabel.setTextColor(palette.secondary);
    LinearLayout.LayoutParams uLabelLp = new LinearLayout.LayoutParams(-1, -2);
    uLabelLp.topMargin = px(10);
    root.addView(urlLabel, uLabelLp);

    inputUrl = createEditText();
    inputUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    inputUrl.setText(shortcut.url);
    root.addView(inputUrl, new LinearLayout.LayoutParams(-1, px(38)));

    errorText = new TextView(activity);
    errorText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    errorText.setTextColor(0xFFEF4444);
    errorText.setVisibility(View.GONE);
    root.addView(errorText, new LinearLayout.LayoutParams(-1, -2));

    LinearLayout btnRow = new LinearLayout(activity);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.END);
    LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(-1, -2);
    brLp.topMargin = px(16);
    root.addView(btnRow, brLp);

    Button cancelBtn = createButton("取消", false, this::dismiss);
    Button saveBtn = createButton("保存", true, this::save);

    btnRow.addView(cancelBtn, new LinearLayout.LayoutParams(px(80), px(38)));
    LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(px(96), px(38));
    sLp.leftMargin = px(12);
    btnRow.addView(saveBtn, sLp);

    setContentView(
        root, new ViewGroup.LayoutParams(px(460), ViewGroup.LayoutParams.WRAP_CONTENT));

    if (focusUrlFirst) {
      inputUrl.requestFocus();
      inputUrl.setSelection(inputUrl.getText().length());
    } else {
      inputName.requestFocus();
      inputName.setSelection(inputName.getText().length());
    }
  }

  private EditText createEditText() {
    EditText et = new EditText(activity);
    et.setSingleLine(true);
    et.setTextColor(palette.text);
    et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
    et.setPadding(px(10), 0, px(10), 0);
    GradientDrawable d = new GradientDrawable();
    d.setColor(palette.light ? 0xFFE2E8F0 : 0xFF17212F);
    d.setCornerRadius(px(8));
    et.setBackground(d);
    et.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    return et;
  }

  private Button createButton(String text, boolean primary, Runnable action) {
    Button btn = new Button(activity);
    btn.setText(text);
    btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
    btn.setFocusable(true);
    btn.setClickable(true);
    btn.setAllCaps(false);

    int normalBg = primary ? 0xFF2563EB : (palette.light ? 0xFFE2E8F0 : 0xFF263345);
    int normalText = primary ? Color.WHITE : palette.text;
    GradientDrawable d = new GradientDrawable();
    d.setColor(normalBg);
    d.setCornerRadius(px(10));
    btn.setBackground(d);
    btn.setTextColor(normalText);

    btn.setOnFocusChangeListener(
        (v, hasFocus) -> {
          GradientDrawable focusD = new GradientDrawable();
          focusD.setColor(
              primary
                  ? (hasFocus ? 0xFF3B82F6 : 0xFF2563EB)
                  : (hasFocus ? 0xFF475569 : (palette.light ? 0xFFE2E8F0 : 0xFF263345)));
          focusD.setCornerRadius(px(10));
          btn.setBackground(focusD);
          if (!primary) btn.setTextColor(hasFocus ? Color.WHITE : palette.text);
        });
    btn.setOnClickListener(v -> action.run());
    return btn;
  }

  private void save() {
    String name = inputName.getText().toString().trim();
    String url = inputUrl.getText().toString().trim();
    if (url.isEmpty()) {
      errorText.setText("网址不能为空");
      errorText.setVisibility(View.VISIBLE);
      inputUrl.requestFocus();
      return;
    }
    shortcut.name = name.isEmpty() ? WebShortcut.normalizeUrl(url) : name;
    shortcut.url = WebShortcut.normalizeUrl(url);
    shortcut.accent = WebShortcut.deriveAccent(shortcut.name);
    store.save(shortcut);
    if (callback != null) {
      callback.onUpdated(shortcut);
    }
    dismiss();
  }
}
