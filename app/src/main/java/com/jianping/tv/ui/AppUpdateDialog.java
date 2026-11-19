package com.jianping.tv.ui;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.jianping.tv.platform.AppUpdate;
import com.jianping.tv.platform.DeviceDiagnostics;
import com.jianping.tv.platform.StillFileProvider;
import com.jianping.tv.platform.UpdateDownloader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * TV D-pad friendly update dialog supporting check, release notes inspection,
 * progress tracking, cancellation/retry, and installation launch.
 */
public final class AppUpdateDialog extends Dialog {
  public interface OnInstallRequested {
    void onInstall(File apkFile);
  }

  private final Activity activity;
  private final UiPalette palette;
  private final float unit;
  private final OnInstallRequested installCallback;
  private final UpdateDownloader downloader = new UpdateDownloader();

  private TextView titleView;
  private TextView subtitleView;
  private ScrollView notesScroll;
  private TextView notesView;
  private LinearLayout progressArea;
  private TextView progressLabel;
  private FrameLayout progressTrack;
  private View progressFill;
  private TextView errorView;
  private LinearLayout buttonBar;

  private AppUpdate.UpdateInfo currentUpdateInfo;
  private File verifiedApk;
  private int currentProgressPercent = 0;

  public AppUpdateDialog(
      Activity activity,
      UiPalette palette,
      float unit,
      OnInstallRequested installCallback) {
    super(activity);
    this.activity = activity;
    this.palette = palette;
    this.unit = unit;
    this.installCallback = installCallback;
  }

  private int px(float value) {
    return Math.round(unit * value);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    super.onCreate(savedInstanceState);
    setCanceledOnTouchOutside(false);

    LinearLayout panel = new LinearLayout(activity);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(px(24), px(20), px(24), px(18));

    GradientDrawable surface =
        new GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            new int[] {palette.menuTop, palette.menuBottom});
    surface.setCornerRadius(px(20));
    surface.setStroke(Math.max(1, px(.5f)), palette.outline);
    panel.setBackground(surface);

    // Title
    titleView = text("检查更新", 21, palette.text, true);
    panel.addView(titleView, new LinearLayout.LayoutParams(-1, px(32)));

    // Subtitle / Version Info
    subtitleView = text("正在连接更新服务器...", 13, palette.secondary, false);
    panel.addView(subtitleView, new LinearLayout.LayoutParams(-1, px(26)));

    // Release notes scrollable container with explicit view ID and D-pad focus
    notesScroll = new ScrollView(activity);
    notesScroll.setId(View.generateViewId());
    notesScroll.setFocusable(true);
    notesScroll.setFocusableInTouchMode(true);
    notesScroll.setVerticalScrollBarEnabled(true);
    notesScroll.setVisibility(View.GONE);
    GradientDrawable notesBg = new GradientDrawable();
    notesBg.setColor(palette.light ? 0xFFE9EDF2 : 0xFF182230);
    notesBg.setCornerRadius(px(12));
    notesScroll.setBackground(notesBg);
    notesScroll.setPadding(px(14), px(10), px(14), px(10));
    notesScroll.setOnFocusChangeListener(
        (v, focused) -> {
          GradientDrawable bg = new GradientDrawable();
          bg.setColor(focused ? palette.rowFocus : (palette.light ? 0xFFE9EDF2 : 0xFF182230));
          bg.setCornerRadius(px(12));
          if (focused) {
            bg.setStroke(Math.max(1, px(1f)), palette.rowFocusIcon);
          }
          notesScroll.setBackground(bg);
        });

    notesView = text("", 12, palette.text, false);
    notesView.setLineSpacing(px(3), 1.0f);
    notesScroll.addView(notesView);

    LinearLayout.LayoutParams nsp = new LinearLayout.LayoutParams(-1, px(130));
    nsp.topMargin = px(8);
    panel.addView(notesScroll, nsp);

    // Progress Area
    progressArea = new LinearLayout(activity);
    progressArea.setOrientation(LinearLayout.VERTICAL);
    progressArea.setVisibility(View.GONE);

    progressLabel = text("0%", 12, palette.secondary, false);
    progressArea.addView(progressLabel, new LinearLayout.LayoutParams(-1, px(22)));

    progressTrack = new FrameLayout(activity);
    GradientDrawable trackBg = new GradientDrawable();
    trackBg.setColor(palette.light ? 0xFFD8DFE8 : 0xFF243346);
    trackBg.setCornerRadius(px(4));
    progressTrack.setBackground(trackBg);

    progressFill = new View(activity);
    GradientDrawable fillBg = new GradientDrawable();
    fillBg.setColor(palette.light ? 0xFF2F6690 : 0xFF5D9CEC);
    fillBg.setCornerRadius(px(4));
    progressFill.setBackground(fillBg);
    progressTrack.addView(progressFill, new FrameLayout.LayoutParams(0, -1));

    LinearLayout.LayoutParams ptp = new LinearLayout.LayoutParams(-1, px(8));
    ptp.topMargin = px(4);
    progressArea.addView(progressTrack, ptp);

    LinearLayout.LayoutParams pap = new LinearLayout.LayoutParams(-1, -2);
    pap.topMargin = px(8);
    panel.addView(progressArea, pap);

    // Error Message
    errorView = text("", 12, palette.light ? 0xFFB33A3A : 0xFFE57373, false);
    errorView.setVisibility(View.GONE);
    LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(-1, -2);
    ep.topMargin = px(8);
    panel.addView(errorView, ep);

    // Button bar
    buttonBar = new LinearLayout(activity);
    buttonBar.setOrientation(LinearLayout.HORIZONTAL);
    buttonBar.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
    LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, px(42));
    bp.topMargin = px(14);
    panel.addView(buttonBar, bp);

    setContentView(panel);

    Window window = getWindow();
    if (window != null) {
      window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
      window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
      WindowManager.LayoutParams params = window.getAttributes();
      params.dimAmount = palette.light ? .15f : .50f;
      params.width =
          Math.min(px(460), activity.getResources().getDisplayMetrics().widthPixels - px(48));
      params.height = WindowManager.LayoutParams.WRAP_CONTENT;
      params.gravity = Gravity.CENTER;
      window.setAttributes(params);
      window
          .getDecorView()
          .setSystemUiVisibility(
              View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    startUpdateCheck();
  }

  private void startUpdateCheck() {
    titleView.setText("检查更新");
    subtitleView.setText("正在连接更新服务器...");
    notesScroll.setVisibility(View.GONE);
    progressArea.setVisibility(View.GONE);
    errorView.setVisibility(View.GONE);

    setButtons(
        new ButtonSpec("取消", this::cancelAndDismiss, true));

    downloader.checkUpdate(
        AppUpdate.DEFAULT_UPDATE_URL,
        new UpdateDownloader.CheckCallback() {
          @Override
          public void onCheckSuccess(AppUpdate.UpdateInfo info) {
            if (!isShowing()) return;
            handleCheckResult(info);
          }

          @Override
          public void onCheckError(String message) {
            if (!isShowing()) return;
            showError("检查更新失败: " + message, true);
          }
        });
  }

  private void handleCheckResult(AppUpdate.UpdateInfo info) {
    this.currentUpdateInfo = info;
    int currentCode = DeviceDiagnostics.appVersionCode(activity);
    String currentName = DeviceDiagnostics.appVersion(activity);

    if (info.versionCode > currentCode) {
      // Check system compatibility
      if (!AppUpdate.isSystemCompatible(info.minSdk)) {
        showIncompatible(info);
        return;
      }

      // Check if already downloaded and verified
      File cacheDir = StillFileProvider.getUpdateDir(activity);
      File potentialApk = new File(cacheDir, AppUpdate.getUpdateApkFileName(info.versionCode));
      if (potentialApk.exists()) {
        AppUpdate.VerificationResult vr =
            AppUpdate.verifyApk(activity, potentialApk, info);
        if (vr.success) {
          verifiedApk = potentialApk;
          showReadyToInstall(info);
          return;
        } else {
          potentialApk.delete();
        }
      }
      showUpdateAvailable(info, currentName, currentCode);
    } else {
      showUpToDate(currentName);
    }
  }

  private void showIncompatible(AppUpdate.UpdateInfo info) {
    titleView.setText("设备系统暂不支持");
    subtitleView.setText(
        "Still "
            + info.versionName
            + " 要求最低系统版本 API "
            + info.minSdk
            + "，当前设备系统为 API "
            + Build.VERSION.SDK_INT
            + "，无法在此设备上更新。");
    notesScroll.setVisibility(View.GONE);
    progressArea.setVisibility(View.GONE);
    errorView.setVisibility(View.GONE);

    setButtons(new ButtonSpec("我知道了", this::dismiss, true));
  }

  private void showUpToDate(String currentName) {
    titleView.setText("当前已是最新版");
    subtitleView.setText("当前版本 Still " + currentName + " 已是最新，暂无新版发布。");
    notesScroll.setVisibility(View.GONE);
    progressArea.setVisibility(View.GONE);
    errorView.setVisibility(View.GONE);

    setButtons(new ButtonSpec("确定", this::dismiss, true));
  }

  private void showUpdateAvailable(
      AppUpdate.UpdateInfo info, String currentName, int currentCode) {
    titleView.setText("发现新版本 Still " + info.versionName);
    subtitleView.setText(
        "当前版本: "
            + currentName
            + " (代号 "
            + currentCode
            + ")  ·  大小: "
            + AppUpdate.formatSize(info.size));

    if (!TextUtils.isEmpty(info.releaseNotes)) {
      notesView.setText("更新内容：\n" + info.releaseNotes);
      notesScroll.setVisibility(View.VISIBLE);
    } else {
      notesScroll.setVisibility(View.GONE);
    }

    progressArea.setVisibility(View.GONE);
    errorView.setVisibility(View.GONE);

    setButtons(
        new ButtonSpec("取消", this::dismiss, false),
        new ButtonSpec("下载并更新", () -> startDownload(info), true));
  }

  private void startDownload(AppUpdate.UpdateInfo info) {
    titleView.setText("正在下载 Still " + info.versionName);
    subtitleView.setText("正在下载安装包，请稍候...");
    notesScroll.setVisibility(View.GONE);
    progressArea.setVisibility(View.VISIBLE);
    errorView.setVisibility(View.GONE);
    updateProgress(0, 0, info.size);

    setButtons(new ButtonSpec("取消下载", this::cancelDownload, true));

    File targetDir = StillFileProvider.getUpdateDir(activity);
    downloader.downloadApk(
        activity,
        info,
        targetDir,
        new UpdateDownloader.DownloadCallback() {
          @Override
          public void onProgress(int percent, long currentBytes, long totalBytes) {
            if (!isShowing()) return;
            updateProgress(percent, currentBytes, totalBytes);
          }

          @Override
          public void onSuccess(File apkFile) {
            if (!isShowing()) return;
            verifiedApk = apkFile;
            showReadyToInstall(info);
          }

          @Override
          public void onError(String message) {
            if (!isShowing()) return;
            showError("下载失败: " + message, true);
          }

          @Override
          public void onCancelled() {
            if (!isShowing()) return;
            titleView.setText("下载已取消");
            subtitleView.setText("已取消下载并清理未完成的文件。");
            progressArea.setVisibility(View.GONE);
            setButtons(
                new ButtonSpec("重新下载", () -> startDownload(info), true),
                new ButtonSpec("关闭", AppUpdateDialog.this::dismiss, false));
          }
        });
  }

  private void updateProgress(int percent, long currentBytes, long totalBytes) {
    currentProgressPercent = Math.max(0, Math.min(100, percent));
    progressLabel.setText(
        currentProgressPercent
            + "% ("
            + AppUpdate.formatSize(currentBytes)
            + " / "
            + AppUpdate.formatSize(totalBytes)
            + ")");

    progressTrack.post(
        () -> {
          int trackWidth = progressTrack.getWidth();
          if (trackWidth > 0) {
            FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) progressFill.getLayoutParams();
            lp.width = Math.round(trackWidth * currentProgressPercent / 100f);
            progressFill.setLayoutParams(lp);
          }
        });
  }

  private void showReadyToInstall(AppUpdate.UpdateInfo info) {
    titleView.setText("下载完成");
    subtitleView.setText("Still " + info.versionName + " 校验通过，可立即开始安装。");
    notesScroll.setVisibility(View.GONE);
    progressArea.setVisibility(View.GONE);
    errorView.setVisibility(View.GONE);

    setButtons(
        new ButtonSpec("稍后安装", this::dismiss, false),
        new ButtonSpec(
            "立即安装",
            () -> {
              if (verifiedApk != null && verifiedApk.exists()) {
                dismiss();
                if (installCallback != null) {
                  installCallback.onInstall(verifiedApk);
                }
              }
            },
            true));
  }

  private void showError(String message, boolean canRetry) {
    errorView.setText(message);
    errorView.setVisibility(View.VISIBLE);
    progressArea.setVisibility(View.GONE);

    if (canRetry) {
      setButtons(
          new ButtonSpec("取消", this::dismiss, false),
          new ButtonSpec(
              "重试",
              () -> {
                if (currentUpdateInfo != null) {
                  startDownload(currentUpdateInfo);
                } else {
                  startUpdateCheck();
                }
              },
              true));
    } else {
      setButtons(new ButtonSpec("关闭", this::dismiss, true));
    }
  }

  private void cancelDownload() {
    downloader.cancel();
  }

  private void cancelAndDismiss() {
    downloader.cancel();
    dismiss();
  }

  @Override
  public void dismiss() {
    downloader.cancel();
    super.dismiss();
  }

  private static final class ButtonSpec {
    final String label;
    final Runnable action;
    final boolean defaultFocus;

    ButtonSpec(String label, Runnable action, boolean defaultFocus) {
      this.label = label;
      this.action = action;
      this.defaultFocus = defaultFocus;
    }
  }

  private void setButtons(ButtonSpec... specs) {
    buttonBar.removeAllViews();
    List<TextView> buttons = new ArrayList<>();
    TextView focusTarget = null;

    for (int i = 0; i < specs.length; i++) {
      ButtonSpec spec = specs[i];
      TextView btn = createButton(spec.label, spec.action);
      buttons.add(btn);
      if (spec.defaultFocus || focusTarget == null) {
        focusTarget = btn;
      }
      LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(px(96), px(38));
      if (i > 0) bp.leftMargin = px(10);
      buttonBar.addView(btn, bp);
    }

    for (int i = 0; i < buttons.size(); i++) {
      TextView b = buttons.get(i);
      b.setNextFocusLeftId(buttons.get(Math.max(0, i - 1)).getId());
      b.setNextFocusRightId(buttons.get(Math.min(buttons.size() - 1, i + 1)).getId());
      b.setNextFocusUpId(notesScroll.getVisibility() == View.VISIBLE ? notesScroll.getId() : b.getId());
      b.setNextFocusDownId(b.getId());
    }

    if (focusTarget != null) {
      notesScroll.setNextFocusDownId(focusTarget.getId());
      TextView target = focusTarget;
      target.post(target::requestFocus);
    }
  }

  private TextView createButton(String label, Runnable action) {
    TextView view = text(label, 14, palette.text, false);
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

  private TextView text(String value, int size, int color, boolean bold) {
    TextView view = new TextView(activity);
    view.setText(value);
    view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, px(size));
    view.setTextColor(color);
    view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
    view.setIncludeFontPadding(false);
    view.setGravity(Gravity.CENTER_VERTICAL);
    return view;
  }

  private GradientDrawable round(int color, int radius) {
    GradientDrawable drawable = new GradientDrawable();
    drawable.setColor(color);
    drawable.setCornerRadius(px(radius));
    return drawable;
  }
}
