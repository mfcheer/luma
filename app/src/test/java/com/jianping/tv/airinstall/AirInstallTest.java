package com.jianping.tv.airinstall;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import com.jianping.tv.data.AppEntry;
import com.jianping.tv.data.Preferences;
import com.jianping.tv.platform.StillFileProvider;
import com.jianping.tv.ui.UiPalette;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
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
public class AirInstallTest {

  private Context context;
  private Preferences prefs;

  @Before
  public void setUp() {
    context = RuntimeEnvironment.getApplication();
    context.getSharedPreferences("launcher", Context.MODE_PRIVATE).edit().clear().commit();
    prefs = new Preferences(context);
  }

  @Test
  public void airInstallEntryProperties() {
    assertTrue(AirInstallEntry.isAirInstallEntry(AirInstallEntry.ENTRY));
    assertTrue(AirInstallEntry.isAirInstallId(AirInstallEntry.ENTRY.id));

    AppEntry normalApp =
        new AppEntry(
            new ComponentName("com.example.app", "MainActivity"),
            "Sample App",
            "com.example.app/MainActivity",
            100L);
    assertFalse(AirInstallEntry.isAirInstallEntry(normalApp));
    assertFalse(AirInstallEntry.isAirInstallId(normalApp.id));
    assertFalse(AirInstallEntry.isAirInstallEntry(null));
    assertFalse(AirInstallEntry.isAirInstallId(null));
  }

  @Test
  public void preferencesAirInstallCardToggle() {
    assertTrue("Default should be true", prefs.showAirInstallCard());
    prefs.showAirInstallCard(false);
    assertFalse(prefs.showAirInstallCard());
    prefs.showAirInstallCard(true);
    assertTrue(prefs.showAirInstallCard());
  }

  @Test
  public void airInstallArtworkPureWhiteAndDimensions() {
    BitmapDrawable lightCard =
        (BitmapDrawable) AirInstallArtwork.cardArtwork(context.getResources(), UiPalette.LIGHT);
    assertNotNull(lightCard);
    Bitmap bmp = lightCard.getBitmap();
    assertNotNull(bmp);
    assertEquals(384, bmp.getWidth());
    assertEquals(250, bmp.getHeight());

    BitmapDrawable preview =
        (BitmapDrawable) AirInstallArtwork.previewArtwork(context.getResources(), UiPalette.LIGHT);
    assertNotNull(preview);
    assertNotNull(preview.getBitmap());
    assertEquals(384, preview.getBitmap().getWidth());
    assertEquals(250, preview.getBitmap().getHeight());

    BitmapDrawable darkCard =
        (BitmapDrawable) AirInstallArtwork.cardArtwork(context.getResources(), UiPalette.DARK);
    assertNotNull(darkCard);
    assertNotNull(darkCard.getBitmap());
    assertEquals(384, darkCard.getBitmap().getWidth());
    assertEquals(250, darkCard.getBitmap().getHeight());

    BitmapDrawable darkPreview =
        (BitmapDrawable) AirInstallArtwork.previewArtwork(context.getResources(), UiPalette.DARK);
    assertNotNull(darkPreview);
    assertNotNull(darkPreview.getBitmap());
    assertEquals(384, darkPreview.getBitmap().getWidth());
    assertEquals(250, darkPreview.getBitmap().getHeight());
  }

  @Test
  public void serverLifecycleAndEndpoints() throws Exception {
    AtomicBoolean started = new AtomicBoolean(false);
    AtomicBoolean completed = new AtomicBoolean(false);
    AtomicReference<File> receivedFile = new AtomicReference<>(null);
    AtomicLong progressBytes = new AtomicLong(0);

    AirInstallServer server =
        new AirInstallServer(
            context,
            new AirInstallServer.Listener() {
              @Override
              public void onUploadStarted(String fileName, long totalBytes) {
                started.set(true);
              }

              @Override
              public void onUploadProgress(
                  String fileName,
                  long currentBytes,
                  long totalBytes,
                  int percent,
                  long speedBytesPerSec) {
                progressBytes.set(currentBytes);
              }

              @Override
              public void onUploadCompleted(File apkFile, String fileName) {
                completed.set(true);
                receivedFile.set(apkFile);
              }

              @Override
              public void onUploadError(String error) {}
            });

    server.start();
    try {
      String baseUrl = server.getUrl();
      assertTrue(baseUrl.startsWith("http://"));
      assertTrue(server.getPort() > 0);

      // 1. Test GET / (HTML Page)
      HttpURLConnection getConn = (HttpURLConnection) new URL(baseUrl).openConnection();
      getConn.setRequestMethod("GET");
      getConn.setConnectTimeout(3000);
      getConn.setReadTimeout(3000);
      assertEquals(200, getConn.getResponseCode());
      String html = new String(getConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(html.contains("Still TV"));
      assertTrue(html.contains("隔空安装助手"));
      assertTrue(html.contains("apk"));
      assertFalse(html.contains("text-transform: uppercase"));
      getConn.disconnect();

      // 2. Test GET /api/status
      HttpURLConnection statusConn =
          (HttpURLConnection) new URL(baseUrl + "api/status").openConnection();
      statusConn.setRequestMethod("GET");
      statusConn.setConnectTimeout(3000);
      statusConn.setReadTimeout(3000);
      assertEquals(200, statusConn.getResponseCode());
      String statusJsonStr =
          new String(statusConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      JSONObject statusJson = new JSONObject(statusJsonStr);
      assertTrue(statusJson.getBoolean("success"));
      assertTrue(statusJson.has("freeBytes"));
      assertEquals("Still TV", statusJson.getString("deviceName"));
      statusConn.disconnect();

      // 3. Test POST /api/upload streaming
      byte[] fakeApkData = new byte[32768]; // 32KB
      for (int i = 0; i < fakeApkData.length; i++) {
        fakeApkData[i] = (byte) (i % 127);
      }

      HttpURLConnection uploadConn =
          (HttpURLConnection) new URL(baseUrl + "api/upload?name=test_media_player.apk").openConnection();
      uploadConn.setRequestMethod("POST");
      uploadConn.setDoOutput(true);
      uploadConn.setFixedLengthStreamingMode(fakeApkData.length);
      uploadConn.setRequestProperty("Content-Type", "application/octet-stream");
      uploadConn.setConnectTimeout(3000);
      uploadConn.setReadTimeout(3000);

      try (OutputStream os = uploadConn.getOutputStream()) {
        os.write(fakeApkData);
        os.flush();
      }

      assertEquals(200, uploadConn.getResponseCode());
      String uploadRespStr =
          new String(uploadConn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      JSONObject uploadResp = new JSONObject(uploadRespStr);
      assertTrue(uploadResp.getBoolean("success"));
      assertEquals("test_media_player.apk", uploadResp.getString("fileName"));
      assertEquals(fakeApkData.length, uploadResp.getLong("size"));
      uploadConn.disconnect();

      Shadows.shadowOf(Looper.getMainLooper()).idle();

      assertTrue(started.get());
      assertTrue(completed.get());
      assertNotNull(receivedFile.get());
      assertTrue(receivedFile.get().exists());
      assertEquals(fakeApkData.length, receivedFile.get().length());

      // Clean up file
      receivedFile.get().delete();
    } finally {
      server.stop();
    }
  }

  @Test
  public void serverCleanOldTempFiles() throws Exception {
    File updateDir = StillFileProvider.getUpdateDir(context);
    File leftover = new File(updateDir, "air_upload_123456.apk.tmp");
    leftover.createNewFile();
    assertTrue(leftover.exists());

    AirInstallServer server = new AirInstallServer(context, null);
    server.cleanOldTempFiles();
    assertFalse(leftover.exists());
  }
}
