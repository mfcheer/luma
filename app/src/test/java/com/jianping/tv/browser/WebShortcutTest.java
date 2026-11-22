package com.jianping.tv.browser;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import com.jianping.tv.data.AppEntry;
import com.jianping.tv.data.AppOrder;
import com.jianping.tv.ui.UiPalette;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class WebShortcutTest {

  private Context context;
  private WebShortcutStore store;

  @Before
  public void setUp() {
    context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("web_shortcuts", Context.MODE_PRIVATE).edit().clear().commit();
    store = new WebShortcutStore(context);
  }

  @Test
  public void urlNormalization() {
    // LAN / local hosts default to http://
    assertEquals("http://192.168.1.1", WebShortcut.normalizeUrl("192.168.1.1"));
    assertEquals("http://192.168.1.100:5244", WebShortcut.normalizeUrl("http://192.168.1.100:5244"));
    assertEquals("http://router.lan", WebShortcut.normalizeUrl("router.lan"));
    assertEquals("http://nas.local:5000", WebShortcut.normalizeUrl("nas.local:5000"));
    assertEquals("http://10.0.1.2:8080", WebShortcut.normalizeUrl("10.0.1.2:8080"));
    assertEquals("http://172.20.1.1", WebShortcut.normalizeUrl("172.20.1.1"));
    assertEquals("http://localhost:3000", WebShortcut.normalizeUrl("localhost:3000"));

    // Public domains default to https://
    assertEquals("https://mfcheer.github.io", WebShortcut.normalizeUrl("mfcheer.github.io"));
    assertEquals("https://github.com", WebShortcut.normalizeUrl("github.com"));
    assertEquals("https://www.baidu.com", WebShortcut.normalizeUrl("www.baidu.com"));

    // Explicit scheme preserved
    assertEquals("https://www.baidu.com", WebShortcut.normalizeUrl("https://www.baidu.com"));
    assertEquals("https://bilibili.com/tv", WebShortcut.normalizeUrl("  https://bilibili.com/tv  "));
    assertEquals("http://example.com", WebShortcut.normalizeUrl("http://example.com"));

    // Empty / blank
    assertEquals("about:blank", WebShortcut.normalizeUrl(""));
    assertEquals("about:blank", WebShortcut.normalizeUrl(null));
  }

  @Test
  public void jsonSerializationAndParsing() throws Exception {
    WebShortcut original =
        new WebShortcut(
            "test_123",
            "家庭NAS",
            "192.168.1.88:5000",
            1732000000L,
            true,
            0xFF2563EB);

    JSONObject json = original.toJson();
    assertEquals("test_123", json.getString("id"));
    assertEquals("家庭NAS", json.getString("name"));
    assertEquals("http://192.168.1.88:5000", json.getString("url"));
    assertEquals(1732000000L, json.getLong("createTime"));
    assertTrue(json.getBoolean("pinned"));
    assertEquals(0xFF2563EB, json.getInt("accent"));

    WebShortcut parsed = WebShortcut.fromJson(json);
    assertNotNull(parsed);
    assertEquals("test_123", parsed.id);
    assertEquals("家庭NAS", parsed.name);
    assertEquals("http://192.168.1.88:5000", parsed.url);
    assertEquals(1732000000L, parsed.createTime);
    assertTrue(parsed.pinned);
    assertEquals(0xFF2563EB, parsed.accent);
  }

  @Test
  public void storeCrudAndChangeNotification() {
    AtomicBoolean changed = new AtomicBoolean(false);
    store.addListener(() -> changed.set(true));

    assertTrue(store.loadAll().isEmpty());

    WebShortcut s1 =
        new WebShortcut("s1", "Alist", "http://192.168.1.100:5244", 1000L, false, 0);
    store.save(s1);

    assertTrue(changed.get());
    assertEquals(1, store.loadAll().size());
    WebShortcut loaded1 = store.get("s1");
    assertNotNull(loaded1);
    assertEquals("Alist", loaded1.name);

    changed.set(false);
    loaded1.name = "Alist 网盘";
    store.save(loaded1);
    assertTrue(changed.get());
    assertEquals("Alist 网盘", store.get("s1").name);

    changed.set(false);
    store.delete("s1");
    assertTrue(changed.get());
    assertTrue(store.loadAll().isEmpty());
    assertNull(store.get("s1"));
  }

  @Test
  public void storeAppEntryMappingAndOrderReconciliation() {
    WebShortcut s1 = new WebShortcut("nas", "NAS", "http://nas.lan", 1L, false, 0);
    WebShortcut s2 = new WebShortcut("router", "路由器", "http://192.168.1.1", 2L, false, 0);

    AppEntry e1 = WebShortcutStore.toAppEntry(s1);
    AppEntry e2 = WebShortcutStore.toAppEntry(s2);

    assertTrue(WebShortcutStore.isWebShortcut(e1));
    assertTrue(WebShortcutStore.isWebShortcut(e2));
    assertEquals("nas", WebShortcutStore.extractId(e1.id));
    assertEquals("router", WebShortcutStore.extractId(e2.id));

    assertFalse(WebShortcutStore.isWebShortcut(WebShortcutStore.ADD_WEB_ENTRY));
    assertTrue(WebShortcutStore.isAddWebEntry(WebShortcutStore.ADD_WEB_ENTRY));

    // Ensure AppOrder.reconcile distinguishes between different web shortcuts and normal apps
    List<String> available =
        Arrays.asList(
            "com.app.first/MainActivity",
            e1.id,
            "com.app.second/MainActivity",
            e2.id);

    List<String> previous =
        Arrays.asList(
            e2.id,
            "com.app.first/MainActivity",
            e1.id);

    List<String> reconciled = AppOrder.reconcile(previous, available);
    assertEquals(4, reconciled.size());
    assertEquals(e2.id, reconciled.get(0));
    assertEquals("com.app.first/MainActivity", reconciled.get(1));
    assertEquals(e1.id, reconciled.get(2));
    assertEquals("com.app.second/MainActivity", reconciled.get(3));
  }

  @Test
  public void lanConfigServerGetAndPost() throws Exception {
    AtomicReference<String> receivedName = new AtomicReference<>("");
    AtomicReference<String> receivedUrl = new AtomicReference<>("");

    LanConfigServer server =
        new LanConfigServer(
            (name, url) -> {
              receivedName.set(name);
              receivedUrl.set(url);
            });

    server.start();
    try {
      String baseUrl = server.getUrl();
      assertTrue(baseUrl.startsWith("http://"));
      assertTrue(server.getPort() > 0);

      // Test GET /
      HttpURLConnection getConn = (HttpURLConnection) new URL(baseUrl).openConnection();
      getConn.setRequestMethod("GET");
      getConn.setConnectTimeout(3000);
      getConn.setReadTimeout(3000);
      assertEquals(200, getConn.getResponseCode());
      String html = new String(getConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(html.contains("Still TV"));
      assertTrue(html.contains("网页"));
      getConn.disconnect();

      // Test POST /api/add
      HttpURLConnection postConn =
          (HttpURLConnection) new URL(baseUrl + "api/add").openConnection();
      postConn.setRequestMethod("POST");
      postConn.setDoOutput(true);
      postConn.setRequestProperty("Content-Type", "application/json");
      postConn.setConnectTimeout(3000);
      postConn.setReadTimeout(3000);

      JSONObject payload = new JSONObject();
      payload.put("name", "家庭NAS");
      payload.put("url", "http://192.168.1.120:5000");

      try (OutputStream os = postConn.getOutputStream()) {
        os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
      }

      assertEquals(200, postConn.getResponseCode());
      String response = new String(postConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(response.contains("\"success\":true"));
      postConn.disconnect();

      Shadows.shadowOf(Looper.getMainLooper()).idle();

      assertEquals("家庭NAS", receivedName.get());
      assertEquals("http://192.168.1.120:5000", receivedUrl.get());
    } finally {
      server.stop();
    }
  }

  @Test
  public void webArtworkGeneratesDrawablesWithPureWhiteBackground() {
    WebShortcut shortcut =
        new WebShortcut("nas", "家庭NAS", "http://192.168.1.100:5000", 1L, false, 0xFF2563EB);
    UiPalette palette = UiPalette.LIGHT;

    BitmapDrawable card =
        (BitmapDrawable) WebArtwork.cardArtwork(context, context.getResources(), shortcut, palette);
    assertNotNull(card);
    assertNotNull(card.getBitmap());
    assertEquals(384, card.getBitmap().getWidth());
    assertEquals(250, card.getBitmap().getHeight());

    assertNotNull(WebArtwork.previewArtwork(context, context.getResources(), shortcut, palette));
    assertNotNull(WebArtwork.addCardArtwork(context.getResources(), palette));
    assertNotNull(WebArtwork.addPreviewArtwork(context.getResources(), palette));

    UiPalette darkPalette = UiPalette.DARK;
    BitmapDrawable darkCard =
        (BitmapDrawable) WebArtwork.cardArtwork(context, context.getResources(), shortcut, darkPalette);
    assertNotNull(darkCard);
    assertNotNull(darkCard.getBitmap());
    assertEquals(384, darkCard.getBitmap().getWidth());
    assertEquals(250, darkCard.getBitmap().getHeight());

    assertNotNull(WebArtwork.previewArtwork(context, context.getResources(), shortcut, darkPalette));
    assertNotNull(WebArtwork.addCardArtwork(context.getResources(), darkPalette));
    assertNotNull(WebArtwork.addPreviewArtwork(context.getResources(), darkPalette));
  }

  @Test
  public void presetMatchingRules() {
    // Baidu matches BAIDU
    assertEquals(WebPresetLogo.Preset.BAIDU, WebPresetLogo.match("https://www.baidu.com", "百度"));
    assertEquals(WebPresetLogo.Preset.BAIDU, WebPresetLogo.match("http://baidu.com", "Baidu"));

    // GitHub only matches github.com, NOT personal pages on *.github.io
    assertEquals(WebPresetLogo.Preset.GITHUB, WebPresetLogo.match("https://github.com", "GitHub"));
    assertEquals(WebPresetLogo.Preset.GITHUB, WebPresetLogo.match("https://github.com/mfcheer/luma", "GitHub"));
    assertNull(WebPresetLogo.match("https://mfcheer.github.io", "我的个人主页"));
    assertNull(WebPresetLogo.match("mfcheer.github.io", "mfcheer"));

    // Bilibili
    assertEquals(WebPresetLogo.Preset.BILIBILI, WebPresetLogo.match("https://www.bilibili.com", "哔哩哔哩"));
    assertEquals(WebPresetLogo.Preset.BILIBILI, WebPresetLogo.match("https://b23.tv/xyz", "B站"));

    // Alist
    assertEquals(WebPresetLogo.Preset.ALIST, WebPresetLogo.match("http://192.168.1.100:5244", "Alist"));

    // Synology / DSM
    assertEquals(WebPresetLogo.Preset.SYNOLOGY, WebPresetLogo.match("http://192.168.1.100:5000", "群晖"));

    // Router
    assertEquals(WebPresetLogo.Preset.ROUTER, WebPresetLogo.match("http://192.168.1.1", "路由器后台"));
    assertEquals(WebPresetLogo.Preset.ROUTER, WebPresetLogo.match("http://192.168.31.1", "网关"));

    // Programming Status Board
    assertEquals(
        WebPresetLogo.Preset.STATUS_BOARD,
        WebPresetLogo.match("http://192.168.31.187:8791/board", ""));
    assertEquals(
        WebPresetLogo.Preset.STATUS_BOARD,
        WebPresetLogo.match("http://192.168.31.187:8791/board", "192.168.31.187:8791/board"));
    assertEquals(
        WebPresetLogo.Preset.STATUS_BOARD,
        WebPresetLogo.match("http://localhost:8791/", "编程状态屏"));
    assertEquals(
        WebPresetLogo.Preset.STATUS_BOARD,
        WebPresetLogo.match("https://dashboard.local/board", "词元之外"));
  }

  @Test
  public void mobilePageNoUppercaseStyle() throws Exception {
    LanConfigServer server = new LanConfigServer(null);
    server.start();
    try {
      HttpURLConnection getConn = (HttpURLConnection) new URL(server.getUrl()).openConnection();
      assertEquals(200, getConn.getResponseCode());
      String html = new String(getConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertFalse(html.contains("text-transform: uppercase"));
      assertTrue(html.contains("Still TV"));
      getConn.disconnect();
    } finally {
      server.stop();
    }
  }

  @Test
  public void webIconManagerLifecycleAndListener() {
    String id = "test_icon_shortcut";
    assertFalse(WebIconManager.hasIcon(context, id));
    assertNull(WebIconManager.getIcon(context, id));

    AtomicReference<String> updatedId = new AtomicReference<>("");
    WebIconManager.Listener listener = updatedId::set;
    WebIconManager.addListener(listener);

    try {
      android.graphics.Bitmap testBmp =
          android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888);
      WebIconManager.saveIcon(context, id, testBmp);

      Shadows.shadowOf(Looper.getMainLooper()).idle();

      assertEquals(id, updatedId.get());
      assertTrue(WebIconManager.hasIcon(context, id));
      assertNotNull(WebIconManager.getIcon(context, id));
      assertTrue(WebIconManager.getIconVersion(id) > 0L);

      WebShortcut shortcut =
          new WebShortcut(id, "个人主页", "https://mfcheer.github.io", 1L, false, 0xFF10B981);
      BitmapDrawable card =
          (BitmapDrawable) WebArtwork.cardArtwork(context, context.getResources(), shortcut, UiPalette.LIGHT);
      assertNotNull(card);
      assertNotNull(card.getBitmap());
      assertEquals(384, card.getBitmap().getWidth());
      assertEquals(250, card.getBitmap().getHeight());

      WebIconManager.deleteIcon(context, id);
      assertFalse(WebIconManager.hasIcon(context, id));
      assertNull(WebIconManager.getIcon(context, id));
    } finally {
      WebIconManager.removeListener(listener);
    }
  }

  @Test
  public void urlNameDetection() {
    assertTrue(WebShortcut.isUrlName(null, "http://192.168.31.187:8791/board"));
    assertTrue(WebShortcut.isUrlName("", "http://192.168.31.187:8791/board"));
    assertTrue(WebShortcut.isUrlName("   ", "http://192.168.31.187:8791/board"));
    assertTrue(
        WebShortcut.isUrlName(
            "http://192.168.31.187:8791/board", "http://192.168.31.187:8791/board"));
    assertTrue(
        WebShortcut.isUrlName(
            "192.168.31.187:8791/board", "http://192.168.31.187:8791/board"));
    assertTrue(
        WebShortcut.isUrlName(
            "192.168.31.187:8791", "http://192.168.31.187:8791/board"));
    assertTrue(
        WebShortcut.isUrlName(
            "https://mfcheer.github.io", "https://mfcheer.github.io"));

    assertFalse(
        WebShortcut.isUrlName("词元之外 · 编程状态屏", "http://192.168.31.187:8791/board"));
    assertFalse(WebShortcut.isUrlName("个人主页", "https://mfcheer.github.io"));
  }

  @Test
  public void htmlTitleExtractionAndEntityCleaning() {
    String html =
        "<!doctype html><html><head><title>词元之外 &middot; 编程状态屏 &amp; TV</title></head></html>";
    String title = WebIconManager.extractTitleFromHtml(html);
    assertEquals("词元之外 &middot; 编程状态屏 & TV", title);

    assertEquals("Hello & World <Test>", WebIconManager.cleanHtmlTitle("Hello &amp; World &lt;Test&gt;"));
    assertNull(WebIconManager.cleanHtmlTitle(""));
    assertNull(WebIconManager.cleanHtmlTitle(null));
  }

  @Test
  public void statusBoardArtworkGeneratesCrispVectorOnPureWhite() {
    WebShortcut boardShortcut =
        new WebShortcut(
            "board_1",
            "词元之外 · 编程状态屏",
            "http://192.168.31.187:8791/board",
            1L,
            false,
            0);
    BitmapDrawable card =
        (BitmapDrawable)
            WebArtwork.cardArtwork(context, context.getResources(), boardShortcut, UiPalette.LIGHT);
    assertNotNull(card);
    assertNotNull(card.getBitmap());
    assertEquals(384, card.getBitmap().getWidth());
    assertEquals(250, card.getBitmap().getHeight());

    assertNotNull(WebArtwork.previewArtwork(context, context.getResources(), boardShortcut, UiPalette.LIGHT));
  }
}
