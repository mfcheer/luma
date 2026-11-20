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
import com.jianping.tv.platform.UpdateSession;
import android.content.Intent;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

/**
 * TV D-pad friendly update dialog supporting check, release notes inspection,
 * progress tracking, cancellation/retry, and installation launch.
 */
public final class AppUpdateDialog extends Dialog {
  private final Activity activity;
  private final UiPalette palette;
  private final float unit;
  private final UpdateSession session;
  private final Runnable returnToDesktop;
  private final UpdateSession.Listener listener = this::renderState;

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

  private int currentProgressPercent;
  private UpdateSession.State renderedState;

  public AppUpdateDialog(Activity activity, UiPalette palette, float unit) {
    this(activity, palette, unit, null);
  }

  public AppUpdateDialog(Activity activity, UiPalette palette, float unit, Runnable returnToDesktop) {
    super(activity);
    this.activity = activity;
    this.palette = palette;
    this.unit = unit;
    this.session = UpdateSession.get(activity);
    this.returnToDesktop = returnToDesktop;
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
    panel.addView(subtitleView, new LinearLayout.LayoutParams(-1, -2));

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

    renderState();
  }

  @Override
  protected void onStart() {
    super.onStart();
    session.addListener(listener);
    if (session.state == UpdateSession.State.IDLE) session.check();
    else renderState();
  }

  @Override
  protected void onStop() {
    session.removeListener(listener);
    super.onStop();
  }

  private void renderState() {
    if (titleView == null) return;
    if (renderedState == UpdateSession.State.DOWNLOADING && session.state == renderedState) {
      updateProgress(session.percent, session.bytes, session.total);
      return;
    }
    renderedState = session.state;
    notesScroll.setVisibility(View.GONE);
    progressArea.setVisibility(View.GONE);
    errorView.setVisibility(View.GONE);
    String current = DeviceDiagnostics.appVersion(activity);
    subtitleView.setText("当前版本 Still " + current);
    switch (session.state) {
      case IDLE:
      case CHECKING:
        titleView.setText("检查更新");
        subtitleView.setText("当前版本 Still " + current + " · 正在连接更新服务器…");
        setButtons(new ButtonSpec("取消", () -> { session.cancel(); dismiss(); }, true));
        break;
      case CURRENT:
        titleView.setText("当前已是最新版");
        setButtons(new ButtonSpec("确定", this::dismiss, true));
        break;
      case INCOMPATIBLE:
        titleView.setText("设备系统暂不支持");
        subtitleView.setText("新版要求 API " + session.info.minSdk + "，当前为 API " + Build.VERSION.SDK_INT + "。");
        setButtons(new ButtonSpec("关闭", this::dismiss, true));
        break;
      case AVAILABLE:
        titleView.setText("发现新版本 Still " + session.info.versionName);
        subtitleView.setText("当前 " + current + " · 安装包 " + AppUpdate.formatSize(session.info.size));
        notesView.setText("更新内容：\n" + (TextUtils.isEmpty(session.info.releaseNotes) ? "本次未提供更新说明。" : session.info.releaseNotes));
        notesScroll.setVisibility(View.VISIBLE);
        setButtons(new ButtonSpec("稍后", this::dismiss, false),
            new ButtonSpec("下载并更新", session::download, true));
        break;
      case DOWNLOADING:
        titleView.setText("正在下载 Still " + session.info.versionName);
        subtitleView.setText("可返回桌面继续使用，再次检查更新可查看进度。");
        progressArea.setVisibility(View.VISIBLE);
        updateProgress(session.percent, session.bytes, session.total);
        setButtons(new ButtonSpec("取消下载", session::cancel, false),
            new ButtonSpec("返回桌面", this::returnHome, true));
        break;
      case VERIFYING:
        titleView.setText("正在校验安装包");
        subtitleView.setText("正在核对完整性、应用身份和签名，请稍候…");
        setButtons(new ButtonSpec("返回桌面", this::returnHome, true));
        break;
      case READY:
        titleView.setText("可继续安装 Still " + session.info.versionName);
        subtitleView.setText(com.jianping.tv.platform.AppInstaller.canRequestInstall(activity)
            ? "安装前会重新校验；请在系统界面确认安装，原有设置将保留。"
            : "需要允许 Still 安装应用，才能更新。返回后可继续安装，无需重新下载。");
        setButtons(new ButtonSpec("稍后安装", this::dismiss, false),
            new ButtonSpec(com.jianping.tv.platform.AppInstaller.canRequestInstall(activity) ? "继续安装" : "前往授权", () -> session.install(activity), true));
        break;
      case PERMISSION:
      case INSTALLING:
        dismiss();
        return;
      case ERROR:
        titleView.setText("更新未完成");
        setButtons(new ButtonSpec("关闭", this::dismiss, false),
            new ButtonSpec(session.message.startsWith("存储空间不足") ? "清理空间" : "官网",
                session.message.startsWith("存储空间不足") ? this::openStorageSettings : this::openWebsite, false),
            new ButtonSpec("重试", session::retry, true));
        break;
    }
    if (!TextUtils.isEmpty(session.message)) {
      errorView.setTextColor(session.state == UpdateSession.State.ERROR
          ? (palette.light ? 0xFFB33A3A : 0xFFE57373) : palette.secondary);
      errorView.setText(session.message);
      errorView.setVisibility(View.VISIBLE);
    }
  }

  private void returnHome() {
    dismiss();
    if (returnToDesktop != null) returnToDesktop.run();
  }

  private void openStorageSettings() {
    try {
      activity.startActivity(new Intent(android.provider.Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
    } catch (RuntimeException e) {
      session.message = "请在电视系统设置中清理存储空间，再返回重试。";
      renderState();
    }
  }

  private void openWebsite() {
    try {
      activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://mfcheer.github.io/luma/#download")));
    } catch (RuntimeException e) {
      session.message = "请用手机或电脑访问 mfcheer.github.io/luma 下载。";
      renderState();
    }
  }

  private void updateProgress(int percent, long currentBytes, long totalBytes) {
    currentProgressPercent = Math.max(0, Math.min(100, percent));
    progressLabel.setText(totalBytes <= 0 ? "已下载 " + AppUpdate.formatSize(currentBytes) :
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

  @Override
  public void dismiss() {
    session.removeListener(listener);
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
