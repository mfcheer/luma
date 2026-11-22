package com.jianping.tv.browser;

import static org.junit.Assert.*;

import android.content.Context;
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
    assertEquals("http://192.168.1.1", WebShortcut.normalizeUrl("192.168.1.1"));
    assertEquals("http://192.168.1.100:5244", WebShortcut.normalizeUrl("http://192.168.1.100:5244"));
    assertEquals("https://www.baidu.com", WebShortcut.normalizeUrl("https://www.baidu.com"));
    assertEquals("https://bilibili.com/tv", WebShortcut.normalizeUrl("  https://bilibili.com/tv  "));
    assertEquals("http://router.lan", WebShortcut.normalizeUrl("router.lan"));
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
  public void webArtworkGeneratesDrawables() {
    WebShortcut shortcut =
        new WebShortcut("nas", "家庭NAS", "http://192.168.1.100:5000", 1L, false, 0xFF2563EB);
    UiPalette palette = UiPalette.LIGHT;

    assertNotNull(WebArtwork.cardArtwork(context.getResources(), shortcut, palette));
    assertNotNull(WebArtwork.previewArtwork(context.getResources(), shortcut, palette));
    assertNotNull(WebArtwork.addCardArtwork(context.getResources(), palette));
    assertNotNull(WebArtwork.addPreviewArtwork(context.getResources(), palette));

    UiPalette darkPalette = UiPalette.DARK;
    assertNotNull(WebArtwork.cardArtwork(context.getResources(), shortcut, darkPalette));
    assertNotNull(WebArtwork.previewArtwork(context.getResources(), shortcut, darkPalette));
    assertNotNull(WebArtwork.addCardArtwork(context.getResources(), darkPalette));
    assertNotNull(WebArtwork.addPreviewArtwork(context.getResources(), darkPalette));
  }
}
