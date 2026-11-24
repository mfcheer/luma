package com.jianping.tv.airinstall;

import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.jianping.tv.platform.AppInstaller;
import com.jianping.tv.ui.UiPalette;
import java.io.File;
import java.util.*;

/**
 * TV D-pad friendly dialog for LAN Air Install with a 3-phase state machine:
 * 1. Waiting (High-contrast QR code + URL + pairing guidance)
 * 2. Transferring (In-place progress bar, percentage, speed and volume stats)
 * 3. Ready to Install (Native APK package parsing for real icon, app name, version, and one-click install)
 */
public final class AirInstallDialog extends Dialog {

  public enum Phase {
    WAITING,
    TRANSFERRING,
    READY_TO_INSTALL
  }

  private final Activity activity;
  private final UiPalette palette;
  private final float unit;
  private AirInstallServer server;
  private Phase currentPhase = Phase.WAITING;

  private LinearLayout rootLayout;
  private LinearLayout waitingContainer;
  private LinearLayout transferContainer;
  private LinearLayout readyContainer;

  // Phase 1 (Waiting) Views
  private ImageView qrImageView;
  private TextView urlHint;
  private TextView waitingStatusText;
  private Button waitingCloseBtn;

  // Phase 2 (Transferring) Views
  private TextView transferFileName;
  private View progressFill;
  private TextView progressPctText;
  private TextView progressSpeedText;
  private TextView progressSizeText;
  private Button cancelTransferBtn;

  // Phase 3 (Ready to Install) Views
  private ImageView apkIconView;
  private TextView apkNameText;
  private TextView apkMetaText;
  private Button installBtn;
  private Button readyCloseBtn;

  private File currentApkFile;
  private boolean installTriggered;

  public AirInstallDialog(Activity activity, UiPalette palette, float unit) {
    super(activity);
    this.activity = activity;
    this.palette = palette;
    this.unit = unit;
  }

  private int px(float value) {
    return Math.round(unit * value);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    super.onCreate(savedInstanceState);
    setCanceledOnTouchOutside(false);

    rootLayout = new LinearLayout(activity);
    rootLayout.setOrientation(LinearLayout.VERTICAL);
    rootLayout.setPadding(px(26), px(22), px(26), px(22));
    rootLayout.setClipChildren(false);
    rootLayout.setClipToPadding(false);

    GradientDrawable bg =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR, new int[] {palette.menuTop, palette.menuBottom});
    bg.setCornerRadius(px(22));
    bg.setStroke(Math.max(1, px(.5f)), palette.outline);
    rootLayout.setBackground(bg);
    palette.styleShadow(rootLayout);

    buildWaitingView();
    buildTransferView();
    buildReadyView();

    rootLayout.addView(waitingContainer, new LinearLayout.LayoutParams(-1, -2));
    rootLayout.addView(transferContainer, new LinearLayout.LayoutParams(-1, -2));
    rootLayout.addView(readyContainer, new LinearLayout.LayoutParams(-1, -2));

    setContentView(
        rootLayout, new ViewGroup.LayoutParams(px(720), ViewGroup.LayoutParams.WRAP_CONTENT));

    Window window = getWindow();
    if (window != null) {
      window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      WindowManager.LayoutParams params = window.getAttributes();
      params.dimAmount = palette.light ? .15f : .50f;
      params.width =
          Math.min(px(720), activity.getResources().getDisplayMetrics().widthPixels - px(48));
      params.height = WindowManager.LayoutParams.WRAP_CONTENT;
      params.gravity = Gravity.CENTER;
      window.setAttributes(params);
      if (window.getDecorView() instanceof ViewGroup) {
        ((ViewGroup) window.getDecorView()).setClipChildren(false);
        ((ViewGroup) window.getDecorView()).setClipToPadding(false);
      }
    }

    switchPhase(Phase.WAITING);
    startServer();
  }

  private void buildWaitingView() {
    waitingContainer = new LinearLayout(activity);
    waitingContainer.setOrientation(LinearLayout.VERTICAL);
    waitingContainer.setClipChildren(false);
    waitingContainer.setClipToPadding(false);

    // Title & subtitle
    TextView title = new TextView(activity);
    title.setText("隔空安装助手");
    title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(20));
    title.setTextColor(palette.text);
    title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    waitingContainer.addView(title, new LinearLayout.LayoutParams(-1, -2));

    TextView subtitle = new TextView(activity);
    subtitle.setText("免插 U 盘，手机或电脑扫码直接发送安装包到电视");
    subtitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12.5f));
    subtitle.setTextColor(palette.secondary);
    LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
    subLp.topMargin = px(4);
    subLp.bottomMargin = px(16);
    waitingContainer.addView(subtitle, subLp);

    // 2-column layout
    LinearLayout body = new LinearLayout(activity);
    body.setOrientation(LinearLayout.HORIZONTAL);
    body.setGravity(Gravity.CENTER_VERTICAL);
    body.setClipChildren(false);
    body.setClipToPadding(false);
    waitingContainer.addView(body, new LinearLayout.LayoutParams(-1, -2));

    // Left column: QR code box
    LinearLayout qrBox = new LinearLayout(activity);
    qrBox.setOrientation(LinearLayout.VERTICAL);
    qrBox.setGravity(Gravity.CENTER_HORIZONTAL);
    qrBox.setPadding(px(16), px(16), px(16), px(16));
    GradientDrawable qrBg = new GradientDrawable();
    qrBg.setColor(palette.light ? 0x0A000000 : 0x16FFFFFF);
    qrBg.setCornerRadius(px(18));
    qrBox.setBackground(qrBg);

    qrImageView = new ImageView(activity);
    int qrSize = px(160);
    qrBox.addView(qrImageView, new LinearLayout.LayoutParams(qrSize, qrSize));

    urlHint = new TextView(activity);
    urlHint.setText("正在启动快传服务…");
    urlHint.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    urlHint.setTextColor(palette.text);
    urlHint.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
    urlHint.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams uhLp = new LinearLayout.LayoutParams(-2, -2);
    uhLp.topMargin = px(10);
    qrBox.addView(urlHint, uhLp);

    body.addView(qrBox, new LinearLayout.LayoutParams(px(220), -2));

    // Right column: instructions & actions
    LinearLayout rightCol = new LinearLayout(activity);
    rightCol.setOrientation(LinearLayout.VERTICAL);
    rightCol.setPadding(px(24), 0, 0, 0);
    rightCol.setClipChildren(false);
    rightCol.setClipToPadding(false);

    TextView stepTitle = new TextView(activity);
    stepTitle.setText("操作指引");
    stepTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
    stepTitle.setTextColor(palette.text);
    stepTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    rightCol.addView(stepTitle, new LinearLayout.LayoutParams(-1, -2));

    TextView steps = new TextView(activity);
    steps.setText(
        "1. 确认手机或电脑与电视处于同一局域网 Wi-Fi\n"
            + "2. 手机相机扫码打开网页，或电脑浏览器输入左侧网址\n"
            + "3. 选择本地 APK 安装包，无线流式直传至电视");
    steps.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    steps.setTextColor(palette.secondary);
    steps.setLineSpacing(px(4), 1f);
    LinearLayout.LayoutParams stepLp = new LinearLayout.LayoutParams(-1, -2);
    stepLp.topMargin = px(8);
    stepLp.bottomMargin = px(18);
    rightCol.addView(steps, stepLp);

    // Waiting connection badge
    waitingStatusText = new TextView(activity);
    waitingStatusText.setText("● 等待设备连接中…");
    waitingStatusText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    waitingStatusText.setTextColor(0xFF0EA5E9);
    rightCol.addView(waitingStatusText, new LinearLayout.LayoutParams(-1, -2));

    // Buttons
    LinearLayout btnRow = new LinearLayout(activity);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.END);
    btnRow.setClipChildren(false);
    btnRow.setClipToPadding(false);
    LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(-1, -2);
    brLp.topMargin = px(24);
    brLp.rightMargin = px(8);
    rightCol.addView(btnRow, brLp);

    waitingCloseBtn = createButton("关闭", false, this::dismiss);
    btnRow.addView(waitingCloseBtn, new LinearLayout.LayoutParams(px(104), px(42)));

    body.addView(rightCol, new LinearLayout.LayoutParams(0, -2, 1f));
  }

  private void buildTransferView() {
    transferContainer = new LinearLayout(activity);
    transferContainer.setOrientation(LinearLayout.VERTICAL);
    transferContainer.setClipChildren(false);
    transferContainer.setClipToPadding(false);

    TextView title = new TextView(activity);
    title.setText("正在接收安装包");
    title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(20));
    title.setTextColor(palette.text);
    title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    transferContainer.addView(title, new LinearLayout.LayoutParams(-1, -2));

    transferFileName = new TextView(activity);
    transferFileName.setText("app.apk");
    transferFileName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(14));
    transferFileName.setTextColor(palette.text);
    transferFileName.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    transferFileName.setSingleLine(true);
    LinearLayout.LayoutParams tfLp = new LinearLayout.LayoutParams(-1, -2);
    tfLp.topMargin = px(14);
    tfLp.bottomMargin = px(10);
    transferContainer.addView(transferFileName, tfLp);

    // Progress bar container
    FrameLayout track = new FrameLayout(activity);
    GradientDrawable trackBg = new GradientDrawable();
    trackBg.setColor(palette.light ? 0xFFE2E8F0 : 0xFF1E293B);
    trackBg.setCornerRadius(px(6));
    track.setBackground(trackBg);

    progressFill = new View(activity);
    GradientDrawable fillBg = new GradientDrawable();
    fillBg.setColor(0xFF0EA5E9);
    fillBg.setCornerRadius(px(6));
    progressFill.setBackground(fillBg);
    track.addView(progressFill, new FrameLayout.LayoutParams(0, -1));

    transferContainer.addView(track, new LinearLayout.LayoutParams(-1, px(12)));

    // Meta stats row (percent, speed, size)
    LinearLayout metaRow = new LinearLayout(activity);
    metaRow.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout.LayoutParams mrLp = new LinearLayout.LayoutParams(-1, -2);
    mrLp.topMargin = px(10);
    mrLp.bottomMargin = px(16);
    transferContainer.addView(metaRow, mrLp);

    progressPctText = new TextView(activity);
    progressPctText.setText("0%");
    progressPctText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
    progressPctText.setTextColor(0xFF0EA5E9);
    progressPctText.setTypeface(Typeface.DEFAULT_BOLD);
    metaRow.addView(progressPctText, new LinearLayout.LayoutParams(-2, -2));

    progressSpeedText = new TextView(activity);
    progressSpeedText.setText(" · 0 MB/s");
    progressSpeedText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12.5f));
    progressSpeedText.setTextColor(palette.secondary);
    metaRow.addView(progressSpeedText, new LinearLayout.LayoutParams(-2, -2));

    View spacer = new View(activity);
    metaRow.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1f));

    progressSizeText = new TextView(activity);
    progressSizeText.setText("0 MB / 0 MB");
    progressSizeText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12.5f));
    progressSizeText.setTextColor(palette.secondary);
    metaRow.addView(progressSizeText, new LinearLayout.LayoutParams(-2, -2));

    TextView tip = new TextView(activity);
    tip.setText("正在通过局域网流式写入电视存储，请保持网络畅通…");
    tip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    tip.setTextColor(palette.muted);
    transferContainer.addView(tip, new LinearLayout.LayoutParams(-1, -2));

    LinearLayout btnRow = new LinearLayout(activity);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.END);
    btnRow.setClipChildren(false);
    btnRow.setClipToPadding(false);
    LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(-1, -2);
    brLp.topMargin = px(16);
    brLp.rightMargin = px(8);
    transferContainer.addView(btnRow, brLp);

    cancelTransferBtn =
        createButton(
            "取消",
            false,
            () -> {
              if (server != null) server.cleanOldTempFiles();
              switchPhase(Phase.WAITING);
            });
    btnRow.addView(cancelTransferBtn, new LinearLayout.LayoutParams(px(104), px(42)));
  }

  private void buildReadyView() {
    readyContainer = new LinearLayout(activity);
    readyContainer.setOrientation(LinearLayout.VERTICAL);
    readyContainer.setClipChildren(false);
    readyContainer.setClipToPadding(false);

    TextView title = new TextView(activity);
    title.setText("安装包已就绪");
    title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(20));
    title.setTextColor(palette.text);
    title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    readyContainer.addView(title, new LinearLayout.LayoutParams(-1, -2));

    // App info card
    LinearLayout appCard = new LinearLayout(activity);
    appCard.setOrientation(LinearLayout.HORIZONTAL);
    appCard.setGravity(Gravity.CENTER_VERTICAL);
    appCard.setPadding(px(16), px(16), px(16), px(16));
    GradientDrawable acBg = new GradientDrawable();
    acBg.setColor(palette.light ? 0x0A000000 : 0x16FFFFFF);
    acBg.setCornerRadius(px(16));
    appCard.setBackground(acBg);
    LinearLayout.LayoutParams acLp = new LinearLayout.LayoutParams(-1, -2);
    acLp.topMargin = px(14);
    acLp.bottomMargin = px(16);
    readyContainer.addView(appCard, acLp);

    apkIconView = new ImageView(activity);
    apkIconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    appCard.addView(apkIconView, new LinearLayout.LayoutParams(px(56), px(56)));

    LinearLayout appInfoCol = new LinearLayout(activity);
    appInfoCol.setOrientation(LinearLayout.VERTICAL);
    appInfoCol.setPadding(px(16), 0, 0, 0);

    apkNameText = new TextView(activity);
    apkNameText.setText("应用名称");
    apkNameText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(16));
    apkNameText.setTextColor(palette.text);
    apkNameText.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    appInfoCol.addView(apkNameText, new LinearLayout.LayoutParams(-1, -2));

    apkMetaText = new TextView(activity);
    apkMetaText.setText("v1.0.0 · com.example.app · 0 MB");
    apkMetaText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    apkMetaText.setTextColor(palette.secondary);
    LinearLayout.LayoutParams amLp = new LinearLayout.LayoutParams(-1, -2);
    amLp.topMargin = px(4);
    appInfoCol.addView(apkMetaText, amLp);

    appCard.addView(appInfoCol, new LinearLayout.LayoutParams(0, -2, 1f));

    TextView tip = new TextView(activity);
    tip.setText("点击「立即安装」将拉起系统安装器，使用遥控器确认即可。");
    tip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(12));
    tip.setTextColor(palette.muted);
    readyContainer.addView(tip, new LinearLayout.LayoutParams(-1, -2));

    LinearLayout btnRow = new LinearLayout(activity);
    btnRow.setOrientation(LinearLayout.HORIZONTAL);
    btnRow.setGravity(Gravity.END);
    btnRow.setClipChildren(false);
    btnRow.setClipToPadding(false);
    LinearLayout.LayoutParams brLp = new LinearLayout.LayoutParams(-1, -2);
    brLp.topMargin = px(18);
    brLp.rightMargin = px(8);
    readyContainer.addView(btnRow, brLp);

    readyCloseBtn = createButton("重新发送", false, () -> switchPhase(Phase.WAITING));
    installBtn = createButton("立即安装", true, this::triggerInstall);
    installBtn.setId(View.generateViewId());

    btnRow.addView(readyCloseBtn, new LinearLayout.LayoutParams(px(120), px(42)));
    LinearLayout.LayoutParams ibLp = new LinearLayout.LayoutParams(px(120), px(42));
    ibLp.leftMargin = px(14);
    btnRow.addView(installBtn, ibLp);
  }

  private void switchPhase(Phase phase) {
    this.currentPhase = phase;
    waitingContainer.setVisibility(phase == Phase.WAITING ? View.VISIBLE : View.GONE);
    transferContainer.setVisibility(phase == Phase.TRANSFERRING ? View.VISIBLE : View.GONE);
    readyContainer.setVisibility(phase == Phase.READY_TO_INSTALL ? View.VISIBLE : View.GONE);

    if (phase == Phase.WAITING) {
      if (waitingCloseBtn != null) waitingCloseBtn.requestFocus();
    } else if (phase == Phase.TRANSFERRING) {
      if (cancelTransferBtn != null) cancelTransferBtn.requestFocus();
    } else if (phase == Phase.READY_TO_INSTALL) {
      if (installBtn != null) installBtn.requestFocus();
    }
  }

  private void startServer() {
    server =
        new AirInstallServer(
            activity,
            new AirInstallServer.Listener() {
              @Override
              public void onUploadStarted(String fileName, long totalBytes) {
                switchPhase(Phase.TRANSFERRING);
                transferFileName.setText(fileName);
                progressPctText.setText("0%");
                progressSpeedText.setText(" · 0 MB/s");
                String totalMb = String.format(Locale.CHINA, "%.1f MB", totalBytes / (1024f * 1024f));
                progressSizeText.setText("0 MB / " + totalMb);
                setTrackFillPercent(0);
              }

              @Override
              public void onUploadProgress(
                  String fileName,
                  long currentBytes,
                  long totalBytes,
                  int percent,
                  long speedBytesPerSec) {
                if (currentPhase != Phase.TRANSFERRING) {
                  switchPhase(Phase.TRANSFERRING);
                }
                setTrackFillPercent(percent);
                progressPctText.setText(percent + "%");
                String speedMb =
                    String.format(Locale.CHINA, "%.1f MB/s", speedBytesPerSec / (1024f * 1024f));
                progressSpeedText.setText(" · " + speedMb);
                String curMb =
                    String.format(Locale.CHINA, "%.1f MB", currentBytes / (1024f * 1024f));
                String totMb =
                    String.format(Locale.CHINA, "%.1f MB", totalBytes / (1024f * 1024f));
                progressSizeText.setText(curMb + " / " + totMb);
              }

              @Override
              public void onUploadCompleted(File apkFile, String fileName) {
                currentApkFile = apkFile;
                inspectAndShowReady(apkFile, fileName);
              }

              @Override
              public void onUploadError(String error) {
                waitingStatusText.setText("● 上传异常：" + error);
                switchPhase(Phase.WAITING);
              }
            });

    try {
      server.start();
      String url = server.getUrl();
      urlHint.setText(url);
      Bitmap qr = generateQrBitmap(url, px(160));
      if (qr != null) {
        qrImageView.setImageDrawable(new BitmapDrawable(activity.getResources(), qr));
      }
    } catch (Exception e) {
      urlHint.setText("服务启动失败");
      waitingStatusText.setText("● 启动快传端口失败，请检查网络权限");
    }
  }

  private void setTrackFillPercent(int percent) {
    if (progressFill == null || progressFill.getParent() == null) return;
    View parent = (View) progressFill.getParent();
    int parentWidth = parent.getWidth();
    if (parentWidth <= 0) {
      parent.post(() -> setTrackFillPercent(percent));
      return;
    }
    int fillWidth = Math.round((parentWidth * percent) / 100f);
    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
    lp.width = fillWidth;
    progressFill.setLayoutParams(lp);
  }

  private void inspectAndShowReady(File apkFile, String fallbackName) {
    PackageManager pm = activity.getPackageManager();
    PackageInfo pi = null;
    try {
      pi = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
    } catch (Exception ignored) {
    }

    String label = fallbackName;
    String version = "未知版本";
    String pkgName = "";
    Drawable icon = null;

    if (pi != null) {
      try {
        pi.applicationInfo.sourceDir = apkFile.getAbsolutePath();
        pi.applicationInfo.publicSourceDir = apkFile.getAbsolutePath();
        CharSequence l = pi.applicationInfo.loadLabel(pm);
        if (l != null && l.length() > 0) label = l.toString();
        icon = pi.applicationInfo.loadIcon(pm);
        version = pi.versionName != null ? pi.versionName : "v" + pi.versionCode;
        pkgName = pi.packageName;
      } catch (Exception ignored) {
      }
    }

    if (icon == null) {
      icon = activity.getDrawable(android.R.drawable.sym_def_app_icon);
    }
    apkIconView.setImageDrawable(icon);
    apkNameText.setText(label);

    long size = apkFile.length();
    String sizeStr = String.format(Locale.CHINA, "%.1f MB", size / (1024f * 1024f));
    String meta = version + (pkgName.isEmpty() ? "" : " · " + pkgName) + " · " + sizeStr;
    apkMetaText.setText(meta);

    switchPhase(Phase.READY_TO_INSTALL);
  }

  private void triggerInstall() {
    if (currentApkFile == null || !currentApkFile.exists()) {
      switchPhase(Phase.WAITING);
      return;
    }

    if (!AppInstaller.canRequestInstall(activity)) {
      Toast.makeText(activity, "请开启允许安装未知来源应用权限", Toast.LENGTH_LONG).show();
      AppInstaller.requestInstallPermission(activity);
      return;
    }

    try {
      installTriggered = true;
      AppInstaller.installApk(activity, currentApkFile);
      dismiss();
    } catch (Exception e) {
      Toast.makeText(activity, "拉起安装器失败：" + e.getMessage(), Toast.LENGTH_LONG).show();
    }
  }

  private Button createButton(String text, boolean primary, Runnable action) {
    Button btn = new Button(activity);
    btn.setText(text);
    btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(13));
    btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
    btn.setFocusable(true);
    btn.setClickable(true);
    btn.setAllCaps(false);
    btn.setIncludeFontPadding(false);
    btn.setGravity(Gravity.CENTER);
    if (android.os.Build.VERSION.SDK_INT >= 21) {
      btn.setStateListAnimator(null);
    }

    updateButtonAppearance(btn, primary, false);

    btn.setOnFocusChangeListener(
        (v, hasFocus) -> {
          updateButtonAppearance(btn, primary, hasFocus);
          btn.setScaleX(hasFocus ? 1.05f : 1f);
          btn.setScaleY(hasFocus ? 1.05f : 1f);
        });
    btn.setOnClickListener(v -> action.run());
    return btn;
  }

  private void updateButtonAppearance(Button btn, boolean primary, boolean hasFocus) {
    GradientDrawable d = new GradientDrawable();
    d.setCornerRadius(px(21)); // Pill shape (42dp height / 2)

    if (primary) {
      d.setOrientation(GradientDrawable.Orientation.TL_BR);
      if (hasFocus) {
        d.setColors(new int[] {0xFF0EA5E9, 0xFF38BDF8});
      } else {
        d.setColors(new int[] {0xFF0284C7, 0xFF0EA5E9});
      }
      btn.setTextColor(Color.WHITE);
    } else {
      // Secondary: tvOS pearl focus vs quiet translucent glass
      if (hasFocus) {
        d.setColor(palette.rowFocus);
        btn.setTextColor(palette.rowFocusText);
      } else {
        d.setColor(palette.light ? 0x12000000 : 0x18FFFFFF);
        btn.setTextColor(palette.light ? 0xFF334155 : 0xEEFFFFFF);
      }
    }
    btn.setBackground(d);
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

  @Override
  public void dismiss() {
    if (server != null) {
      server.stop();
      server = null;
    }
    // Clean up received APK if user cancelled without installing
    if (!installTriggered && currentApkFile != null && currentApkFile.exists()) {
      try {
        currentApkFile.delete();
      } catch (Exception ignored) {
      }
    }
    super.dismiss();
  }
}
