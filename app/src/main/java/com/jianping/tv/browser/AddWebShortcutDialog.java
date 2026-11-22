package com.jianping.tv.browser;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.jianping.tv.ui.UiPalette;
import java.util.*;

/** Dialog presenting LAN QR code pairing alongside remote-driven TV input. */
public final class AddWebShortcutDialog extends Dialog {

  public interface Callback {
    void onShortcutAdded(WebShortcut shortcut);
  }

  private final Activity activity;
  private final UiPalette palette;
  private final float unit;
  private final WebShortcutStore store;
  private final Callback callback;

  private LanConfigServer server;
  private EditText inputName;
  private EditText inputUrl;
  private TextView errorText;
  private ImageView qrImageView;
  private TextView urlHint;

  public AddWebShortcutDialog(
      Activity activity,
      UiPalette palette,
      float unit,
      WebShortcutStore store,
      Callback callback) {
    super(activity);
    this.activity = activity;
    this.palette = palette;
    this.unit = unit;
    this.store = store;
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
    bg.setCornerRadius(px(22));
    bg.setStroke(Math.max(1, px(.5f)), palette.outline);
    root.setBackground(bg);
    palette.styleShadow(root);

    // Title header
    TextView title = new TextView(activity);
    title.setText("添加网页快捷方式");
    title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(20));
    title.setTextColor(palette.text);
    title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    root.addView(title, new LinearLayout.LayoutParams(-1, -2));

    TextView subtitle = new TextView(activity);
    subtitle.setText("手机扫码直接输入，或使用遥控器手动添加");
    subtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    subtitle.setTextColor(palette.secondary);
    LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
    subLp.topMargin = px(4);
    subLp.bottomMargin = px(16);
    root.addView(subtitle, subLp);

    // Two-column layout
    LinearLayout body = new LinearLayout(activity);
    body.setOrientation(LinearLayout.HORIZONTAL);
    body.setGravity(Gravity.CENTER_VERTICAL);
    root.addView(body, new LinearLayout.LayoutParams(-1, -2));

    // Left Column: QR Code
    LinearLayout qrBox = new LinearLayout(activity);
    qrBox.setOrientation(LinearLayout.VERTICAL);
    qrBox.setGravity(Gravity.CENTER_HORIZONTAL);
    qrBox.setPadding(px(14), px(14), px(14), px(14));
    GradientDrawable qrBg = new GradientDrawable();
    qrBg.setColor(palette.light ? 0x0A000000 : 0x16FFFFFF);
    qrBg.setCornerRadius(px(16));
    qrBox.setBackground(qrBg);

    qrImageView = new ImageView(activity);
    int qrSize = px(150);
    qrBox.addView(qrImageView, new LinearLayout.LayoutParams(qrSize, qrSize));

    TextView qrLabel = new TextView(activity);
    qrLabel.setText("手机扫码快速添加");
    qrLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    qrLabel.setTextColor(palette.text);
    qrLabel.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams qlLp = new LinearLayout.LayoutParams(-2, -2);
    qlLp.topMargin = px(8);
    qrBox.addView(qrLabel, qlLp);

    urlHint = new TextView(activity);
    urlHint.setText("正在启动服务…");
    urlHint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(10));
    urlHint.setTextColor(palette.muted);
    urlHint.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams uhLp = new LinearLayout.LayoutParams(-2, -2);
    uhLp.topMargin = px(2);
    qrBox.addView(urlHint, uhLp);

    body.addView(qrBox, new LinearLayout.LayoutParams(px(210), -2));

    // Right Column: TV Manual Input & Presets
    LinearLayout inputCol = new LinearLayout(activity);
    inputCol.setOrientation(LinearLayout.VERTICAL);
    inputCol.setPadding(px(20), 0, 0, 0);

    inputCol.addView(sectionLabel("网页名称"));
    inputName = createEditText("例如：家庭 NAS / Alist / 路由器");
    inputCol.addView(inputName, new LinearLayout.LayoutParams(-1, px(38)));

    LinearLayout.LayoutParams urlLabelLp = new LinearLayout.LayoutParams(-1, -2);
    urlLabelLp.topMargin = px(8);
    inputCol.addView(sectionLabel("网页网址"), urlLabelLp);
    inputUrl = createEditText("例如：http://192.168.1.100:5244");
    inputCol.addView(inputUrl, new LinearLayout.LayoutParams(-1, px(38)));

    // Preset chips
    TextView presetLabel = sectionLabel("常用模板");
    LinearLayout.LayoutParams pLabelLp = new LinearLayout.LayoutParams(-1, -2);
    pLabelLp.topMargin = px(8);
    inputCol.addView(presetLabel, pLabelLp);

    HorizontalScrollView chipsScroll = new HorizontalScrollView(activity);
    chipsScroll.setHorizontalScrollBarEnabled(false);
    chipsScroll.setClipChildren(false);
    chipsScroll.setClipToPadding(false);

    LinearLayout chipsRow = new LinearLayout(activity);
    chipsRow.setOrientation(LinearLayout.HORIZONTAL);
    chipsRow.setClipChildren(false);
    chipsRow.setClipToPadding(false);

    chipsRow.addView(createChip("路由器 (192.168.1.1)", "路由器后台", "http://192.168.1.1"));
    chipsRow.addView(createChip("Alist (:5244)", "Alist 网盘", "http://192.168.1.100:5244"));
    chipsRow.addView(createChip("群晖 (:5000)", "群晖 DSM", "http://192.168.1.100:5000"));
    chipsRow.addView(createChip("百度", "百度", "https://www.baidu.com"));
    chipsRow.addView(createChip("哔哩哔哩", "哔哩哔哩", "https://www.bilibili.com"));

    chipsScroll.addView(chipsRow, new ViewGroup.LayoutParams(-2, -2));
    inputCol.addView(chipsScroll, new LinearLayout.LayoutParams(-1, px(36)));

    // Error text
    errorText = new TextView(activity);
    errorText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    errorText.setTextColor(0xFFEF4444);
    errorText.setVisibility(View.GONE);
    inputCol.addView(errorText, new LinearLayout.LayoutParams(-1, -2));

    // Buttons
    LinearLayout btnRow = new LinearLayout(activity);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.END);
    LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(-1, -2);
    brLp.topMargin = px(12);
    inputCol.addView(btnRow, brLp);

    Button cancelBtn = createButton("取消", false, this::dismiss);
    Button confirmBtn = createButton("确定添加", true, this::submitManual);
    confirmBtn.setId(View.generateViewId());

    btnRow.addView(cancelBtn, new LinearLayout.LayoutParams(px(88), px(38)));
    LinearLayout.LayoutParams cfLp = new LinearLayout.LayoutParams(px(108), px(38));
    cfLp.leftMargin = px(12);
    btnRow.addView(confirmBtn, cfLp);

    body.addView(inputCol, new LinearLayout.LayoutParams(px(430), -2));

    setContentView(
        root, new ViewGroup.LayoutParams(px(700), ViewGroup.LayoutParams.WRAP_CONTENT));

    startLanServer();
  }

  private TextView sectionLabel(String text) {
    TextView v = new TextView(activity);
    v.setText(text);
    v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    v.setTextColor(palette.secondary);
    return v;
  }

  private EditText createEditText(String hint) {
    EditText et = new EditText(activity);
    et.setSingleLine(true);
    et.setHint(hint);
    et.setHintTextColor(palette.muted);
    et.setTextColor(palette.text);
    et.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
    et.setPadding(px(10), 0, px(10), 0);
    et.setBackground(roundedBackground(palette.light ? 0xFFE2E8F0 : 0xFF17212F, 8));
    et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    et.setImeOptions(EditorInfo.IME_ACTION_NEXT | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
    return et;
  }

  private View createChip(String label, String name, String url) {
    TextView chip = new TextView(activity);
    chip.setText(label);
    chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(11));
    chip.setTextColor(palette.text);
    chip.setPadding(px(10), px(5), px(10), px(5));
    chip.setFocusable(true);
    chip.setClickable(true);
    chip.setBackground(roundedBackground(palette.light ? 0xFFE2E8F0 : 0xFF2A374A, 14));
    chip.setOnFocusChangeListener(
        (v, hasFocus) -> {
          chip.setBackground(
              roundedBackground(
                  hasFocus ? 0xFF3B82F6 : (palette.light ? 0xFFE2E8F0 : 0xFF2A374A), 14));
          chip.setTextColor(hasFocus ? Color.WHITE : palette.text);
        });
    chip.setOnClickListener(
        v -> {
          inputName.setText(name);
          inputUrl.setText(url);
          errorText.setVisibility(View.GONE);
        });
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
    lp.rightMargin = px(8);
    chip.setLayoutParams(lp);
    return chip;
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
    btn.setBackground(roundedBackground(normalBg, 10));
    btn.setTextColor(normalText);

    btn.setOnFocusChangeListener(
        (v, hasFocus) -> {
          if (primary) {
            btn.setBackground(roundedBackground(hasFocus ? 0xFF3B82F6 : 0xFF2563EB, 10));
            btn.setScaleX(hasFocus ? 1.05f : 1f);
            btn.setScaleY(hasFocus ? 1.05f : 1f);
          } else {
            btn.setBackground(
                roundedBackground(
                    hasFocus ? 0xFF475569 : (palette.light ? 0xFFE2E8F0 : 0xFF263345), 10));
            btn.setTextColor(hasFocus ? Color.WHITE : palette.text);
          }
        });
    btn.setOnClickListener(v -> action.run());
    return btn;
  }

  private GradientDrawable roundedBackground(int color, float radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(px(radius));
    return d;
  }

  private void startLanServer() {
    server =
        new LanConfigServer(
            (name, url) -> {
              activity.runOnUiThread(
                  () -> {
                    addShortcutAndDismiss(name, url);
                  });
            });
    try {
      server.start();
      String url = server.getUrl();
      urlHint.setText(url);
      Bitmap qr = generateQrBitmap(url, px(150));
      if (qr != null) {
        qrImageView.setImageDrawable(new BitmapDrawable(activity.getResources(), qr));
      }
    } catch (Exception e) {
      urlHint.setText("局域网服务启动失败");
    }
  }

  private Bitmap generateQrBitmap(String content, int size) {
    try {
      Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
      hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
      hints.put(EncodeHintType.MARGIN, 1);
      hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
      com.google.zxing.common.BitMatrix matrix =
          new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints);
      int w = matrix.getWidth(), h = matrix.getHeight();
      int[] pixels = new int[w * h];
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          pixels[y * w + x] = matrix.get(x, y) ? 0xFF0F172A : 0xFFFFFFFF;
        }
      }
      Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
      bmp.setPixels(pixels, 0, w, 0, 0, w, h);
      return bmp;
    } catch (Exception ignored) {
      return null;
    }
  }

  private void submitManual() {
    String name = inputName.getText().toString().trim();
    String url = inputUrl.getText().toString().trim();
    if (url.isEmpty()) {
      errorText.setText("请填写网页网址");
      errorText.setVisibility(View.VISIBLE);
      inputUrl.requestFocus();
      return;
    }
    addShortcutAndDismiss(name, url);
  }

  private void addShortcutAndDismiss(String name, String url) {
    String finalName = name.isEmpty() ? WebShortcut.normalizeUrl(url) : name;
    String id = "web_" + System.currentTimeMillis();
    WebShortcut shortcut =
        new WebShortcut(
            id,
            finalName,
            url,
            System.currentTimeMillis(),
            false,
            WebShortcut.deriveAccent(finalName));
    store.save(shortcut);
    if (callback != null) {
      callback.onShortcutAdded(shortcut);
    }
    dismiss();
  }

  @Override
  public void dismiss() {
    if (server != null) {
      server.stop();
      server = null;
    }
    super.dismiss();
  }
}
