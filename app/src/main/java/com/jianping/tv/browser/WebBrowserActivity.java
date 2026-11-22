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
  public static final String EXTRA_ID = "com.jianping.tv.browser.EXTRA_ID";

  private FrameLayout container;
  private String shortcutId;
  private WebView webView;
  private ProgressBar progressBar;
  private View customView;
  private WebChromeClient.CustomViewCallback customViewCallback;
  private PopupWindow menuPopup;
  private View errorOverlay;
  private boolean pageHasError;

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

    shortcutId = getIntent().getStringExtra(EXTRA_ID);
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

    // Optimize User-Agent: strip TV-specific tokens that cause desktop/web apps (e.g. GitHub) to reject or redirect
    String defaultUa = s.getUserAgentString();
    if (defaultUa != null && !defaultUa.isEmpty()) {
      String cleaned = defaultUa.replaceAll("(?i);?\\s*(Android TV|GoogleTV|Large Screen)", "");
      s.setUserAgentString(cleaned);
    } else {
      s.setUserAgentString(
          "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
    }

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
          public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            pageHasError = false;
            hideError();
          }

          @Override
          public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            if (!pageHasError) {
              hideError();
            }
            view.evaluateJavascript(
                "(function() {"
                    + "  setTimeout(function() {"
                    + "    var overlay = document.getElementById('homeLauncherOverlay');"
                    + "    if (overlay) {"
                    + "      overlay.style.transition = 'opacity 0.4s ease';"
                    + "      overlay.style.opacity = '0';"
                    + "      setTimeout(function() { if (overlay && overlay.parentNode) overlay.parentNode.removeChild(overlay); }, 400);"
                    + "    }"
                    + "  }, 600);"
                    + "})();",
                null);
          }

          @Override
          public void onReceivedSslError(
              WebView view, SslErrorHandler handler, SslError error) {
            // Allow self-signed certs for local NAS/router consoles
            handler.proceed();
          }

          @Override
          public void onReceivedError(
              WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
              if (request != null && request.isForMainFrame()) {
                pageHasError = true;
                String desc =
                    (error != null && error.getDescription() != null)
                        ? error.getDescription().toString()
                        : "网络连接失败";
                showError(desc, request.getUrl() != null ? request.getUrl().toString() : "");
              }
            }
          }

          @Override
          public void onReceivedError(
              WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            pageHasError = true;
            showError(description != null ? description : "网络连接失败", failingUrl);
          }

          @Override
          public void onReceivedHttpError(
              WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
              if (request != null && request.isForMainFrame()) {
                int code = errorResponse != null ? errorResponse.getStatusCode() : 0;
                if (code >= 400) {
                  pageHasError = true;
                  String reason =
                      (errorResponse != null && errorResponse.getReasonPhrase() != null)
                          ? errorResponse.getReasonPhrase()
                          : "HTTP " + code;
                  showError("服务器响应错误 (" + reason + ")", request.getUrl() != null ? request.getUrl().toString() : "");
                }
              }
            }
          }
        });

    wv.setWebChromeClient(
        new WebChromeClient() {
          @Override
          public void onReceivedIcon(WebView view, android.graphics.Bitmap icon) {
            super.onReceivedIcon(view, icon);
            if (icon != null && shortcutId != null && !shortcutId.isEmpty()) {
              WebIconManager.saveIcon(getApplicationContext(), shortcutId, icon);
            }
          }

          @Override
          public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed) {
            super.onReceivedTouchIconUrl(view, url, precomposed);
            if (url != null && shortcutId != null && !shortcutId.isEmpty()) {
              WebIconManager.fetchDirectIconAsync(getApplicationContext(), shortcutId, url);
            }
          }

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

  private void showError(String message, String url) {
    if (errorOverlay == null) {
      LinearLayout errorLayout = new LinearLayout(this);
      errorLayout.setOrientation(LinearLayout.VERTICAL);
      errorLayout.setGravity(Gravity.CENTER);
      errorLayout.setBackgroundColor(0xFF0F172A);
      errorLayout.setPadding(dp(32), dp(24), dp(32), dp(24));

      TextView titleView = new TextView(this);
      titleView.setText("无法访问此网页");
      titleView.setTextColor(Color.WHITE);
      titleView.setTextSize(20);
      titleView.setGravity(Gravity.CENTER);
      errorLayout.addView(titleView);

      TextView descView = new TextView(this);
      descView.setId(android.R.id.text1);
      descView.setTextColor(0xFF94A3B8);
      descView.setTextSize(13);
      descView.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(dp(480), -2);
      descLp.topMargin = dp(10);
      descLp.bottomMargin = dp(24);
      errorLayout.addView(descView, descLp);

      LinearLayout btnRow = new LinearLayout(this);
      btnRow.setOrientation(LinearLayout.HORIZONTAL);
      btnRow.setGravity(Gravity.CENTER);

      Button retryBtn =
          createMenuButton(
              "重新加载",
              () -> {
                hideError();
                if (webView != null) webView.reload();
              });
      LinearLayout.LayoutParams retryLp = new LinearLayout.LayoutParams(dp(140), dp(40));
      retryLp.rightMargin = dp(16);
      btnRow.addView(retryBtn, retryLp);

      Button exitBtn = createMenuButton("返回桌面", this::exitBrowser);
      btnRow.addView(exitBtn, new LinearLayout.LayoutParams(dp(140), dp(40)));

      errorLayout.addView(btnRow);
      errorOverlay = errorLayout;
      container.addView(errorOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    TextView desc = errorOverlay.findViewById(android.R.id.text1);
    if (desc != null) {
      StringBuilder sb = new StringBuilder();
      if (message != null && !message.isEmpty()) {
        sb.append(message).append("\n");
      }
      if (url != null && !url.isEmpty()) {
        sb.append(url);
      }
      desc.setText(sb.toString());
    }
    errorOverlay.setVisibility(View.VISIBLE);
    errorOverlay.bringToFront();
    try {
      ViewGroup btnRow = (ViewGroup) ((ViewGroup) errorOverlay).getChildAt(2);
      if (btnRow != null && btnRow.getChildCount() > 0) {
        btnRow.getChildAt(0).requestFocus();
      }
    } catch (Exception ignored) {
    }
  }

  private void hideError() {
    if (errorOverlay != null) {
      errorOverlay.setVisibility(View.GONE);
    }
  }

  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      event.startTracking();
      return true;
    }
    return super.onKeyDown(keyCode, event);
  }

  @Override
  public boolean onKeyLongPress(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      exitBrowser();
      return true;
    }
    return super.onKeyLongPress(keyCode, event);
  }

  @Override
  public boolean onKeyUp(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      handleBack();
      return true;
    } else if (keyCode == KeyEvent.KEYCODE_MENU) {
      showMenu();
      return true;
    }
    return super.onKeyUp(keyCode, event);
  }

  @Override
  public void onBackPressed() {
    handleBack();
  }

  private void handleBack() {
    if (menuPopup != null && menuPopup.isShowing()) {
      menuPopup.dismiss();
      return;
    }
    if (errorOverlay != null && errorOverlay.getVisibility() == View.VISIBLE) {
      exitBrowser();
      return;
    }
    if (customView != null) {
      // Hide custom view
      container.removeView(customView);
      customView = null;
      if (customViewCallback != null) {
        customViewCallback.onCustomViewHidden();
        customViewCallback = null;
      }
      if (webView != null) {
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
