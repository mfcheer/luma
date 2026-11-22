package com.jianping.tv.browser;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.webkit.*;
import android.widget.*;

/** Isolated-process full-screen web viewer for TV remote control. */
public final class WebBrowserActivity extends Activity {
  public static final String EXTRA_URL = "com.jianping.tv.browser.EXTRA_URL";
  public static final String EXTRA_TITLE = "com.jianping.tv.browser.EXTRA_TITLE";

  private FrameLayout container;
  private WebView webView;
  private ProgressBar progressBar;
  private View customView;
  private WebChromeClient.CustomViewCallback customViewCallback;
  private PopupWindow menuPopup;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    super.onCreate(savedInstanceState);

    setImmersive();

    container = new FrameLayout(this);
    container.setBackgroundColor(Color.BLACK);

    webView = new WebView(this);
    setupWebView(webView);
    container.addView(webView, new FrameLayout.LayoutParams(-1, -1));

    progressBar =
        new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
    progressBar.setMax(100);
    progressBar.setProgress(0);
    progressBar.setVisibility(View.GONE);
    FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(-1, dp(3));
    pbLp.gravity = Gravity.TOP;
    container.addView(progressBar, pbLp);

    setContentView(container);

    String targetUrl = getIntent().getStringExtra(EXTRA_URL);
    if (targetUrl == null || targetUrl.isEmpty()) {
      targetUrl = "about:blank";
    }
    webView.loadUrl(WebShortcut.normalizeUrl(targetUrl));
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private void setImmersive() {
    View decor = getWindow().getDecorView();
    decor.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      setImmersive();
    }
  }

  private void setupWebView(WebView wv) {
    WebSettings s = wv.getSettings();
    s.setJavaScriptEnabled(true);
    s.setDomStorageEnabled(true);
    s.setDatabaseEnabled(true);
    s.setUseWideViewPort(true);
    s.setLoadWithOverviewMode(true);
    s.setSupportZoom(true);
    s.setBuiltInZoomControls(true);
    s.setDisplayZoomControls(false);
    s.setMediaPlaybackRequiresUserGesture(false);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
      CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
    }
    CookieManager.getInstance().setAcceptCookie(true);

    wv.setFocusable(true);
    wv.setFocusableInTouchMode(true);
    wv.requestFocus();

    wv.setWebViewClient(
        new WebViewClient() {
          @Override
          public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
          }

          @Override
          public void onReceivedSslError(
              WebView view, SslErrorHandler handler, SslError error) {
            // Allow self-signed certs for local NAS/router consoles
            handler.proceed();
          }

          @Override
          public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
          }
        });

    wv.setWebChromeClient(
        new WebChromeClient() {
          @Override
          public void onProgressChanged(WebView view, int newProgress) {
            if (newProgress < 100) {
              progressBar.setVisibility(View.VISIBLE);
              progressBar.setProgress(newProgress);
            } else {
              progressBar.setVisibility(View.GONE);
            }
          }

          @Override
          public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
              callback.onCustomViewHidden();
              return;
            }
            customView = view;
            customViewCallback = callback;
            webView.setVisibility(View.GONE);
            container.addView(customView, new FrameLayout.LayoutParams(-1, -1));
            setImmersive();
          }

          @Override
          public void onHideCustomView() {
            if (customView == null) return;
            container.removeView(customView);
            customView = null;
            if (customViewCallback != null) {
              customViewCallback.onCustomViewHidden();
              customViewCallback = null;
            }
            webView.setVisibility(View.VISIBLE);
            setImmersive();
          }
        });
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    int code = event.getKeyCode();
    if (code == KeyEvent.KEYCODE_BACK) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (event.isLongPress()) {
          exitBrowser();
          return true;
        }
        event.startTracking();
        return true;
      } else if (event.getAction() == KeyEvent.ACTION_UP) {
        if (event.isTracking() && !event.isCanceled()) {
          handleBack();
          return true;
        }
      }
    } else if (code == KeyEvent.KEYCODE_MENU) {
      if (event.getAction() == KeyEvent.ACTION_UP) {
        showMenu();
        return true;
      }
    }
    return super.dispatchKeyEvent(event);
  }

  private void handleBack() {
    if (menuPopup != null && menuPopup.isShowing()) {
      menuPopup.dismiss();
      return;
    }
    if (customView != null) {
      if (webView != null && webView.getWebChromeClient() != null) {
        // Hide custom view
        container.removeView(customView);
        customView = null;
        if (customViewCallback != null) {
          customViewCallback.onCustomViewHidden();
          customViewCallback = null;
        }
        webView.setVisibility(View.VISIBLE);
      }
      return;
    }
    if (webView != null && webView.canGoBack()) {
      webView.goBack();
    } else {
      exitBrowser();
    }
  }

  private void showMenu() {
    if (menuPopup != null && menuPopup.isShowing()) {
      menuPopup.dismiss();
      return;
    }

    LinearLayout panel = new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(20), dp(16), dp(20), dp(16));
    GradientDrawable gd = new GradientDrawable();
    gd.setColor(0xE61E293B);
    gd.setCornerRadius(dp(16));
    gd.setStroke(dp(1), 0x33FFFFFF);
    panel.setBackground(gd);

    TextView title = new TextView(this);
    title.setText(webView != null && webView.getTitle() != null ? webView.getTitle() : "网页");
    title.setTextColor(Color.WHITE);
    title.setTextSize(16);
    title.setSingleLine(true);
    panel.addView(title, new LinearLayout.LayoutParams(dp(280), -2));

    TextView url = new TextView(this);
    url.setText(webView != null && webView.getUrl() != null ? webView.getUrl() : "");
    url.setTextColor(0xFF94A3B8);
    url.setTextSize(11);
    url.setSingleLine(true);
    LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(dp(280), -2);
    urlLp.bottomMargin = dp(14);
    panel.addView(url, urlLp);

    Button refreshBtn = createMenuButton("刷新网页", () -> {
      if (webView != null) webView.reload();
      if (menuPopup != null) menuPopup.dismiss();
    });
    panel.addView(refreshBtn, new LinearLayout.LayoutParams(-1, dp(38)));

    Button exitBtn = createMenuButton("返回桌面", this::exitBrowser);
    LinearLayout.LayoutParams exitLp = new LinearLayout.LayoutParams(-1, dp(38));
    exitLp.topMargin = dp(8);
    panel.addView(exitBtn, exitLp);

    menuPopup = new PopupWindow(panel, dp(320), ViewGroup.LayoutParams.WRAP_CONTENT, true);
    menuPopup.setBackgroundDrawable(null);
    menuPopup.showAtLocation(container, Gravity.CENTER, 0, 0);
    refreshBtn.requestFocus();
  }

  private Button createMenuButton(String label, Runnable action) {
    Button btn = new Button(this);
    btn.setText(label);
    btn.setTextColor(Color.WHITE);
    btn.setTextSize(14);
    btn.setFocusable(true);
    btn.setClickable(true);
    btn.setAllCaps(false);

    GradientDrawable normal = new GradientDrawable();
    normal.setColor(0x22FFFFFF);
    normal.setCornerRadius(dp(10));
    btn.setBackground(normal);

    btn.setOnFocusChangeListener((v, hasFocus) -> {
      GradientDrawable d = new GradientDrawable();
      d.setColor(hasFocus ? 0xFF3B82F6 : 0x22FFFFFF);
      d.setCornerRadius(dp(10));
      btn.setBackground(d);
    });
    btn.setOnClickListener(v -> action.run());
    return btn;
  }

  private void exitBrowser() {
    if (menuPopup != null && menuPopup.isShowing()) {
      menuPopup.dismiss();
    }
    finish();
  }

  @Override
  protected void onDestroy() {
    if (menuPopup != null) {
      menuPopup.dismiss();
      menuPopup = null;
    }
    if (webView != null) {
      ViewParent parent = webView.getParent();
      if (parent instanceof ViewGroup) {
        ((ViewGroup) parent).removeView(webView);
      }
      webView.stopLoading();
      webView.clearHistory();
      webView.destroy();
      webView = null;
    }
    super.onDestroy();
    // Reclaim memory immediately in the isolated :browser process
    android.os.Process.killProcess(android.os.Process.myPid());
  }
}
