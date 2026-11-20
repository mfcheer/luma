package com.jianping.tv.platform;

import static org.junit.Assert.*;
import android.content.Context;
import android.os.Looper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateDownloaderTest {
  private void await(CountDownLatch done) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (done.getCount() != 0 && System.nanoTime() < deadline) {
      Shadows.shadowOf(Looper.getMainLooper()).idle();
      done.await(10, TimeUnit.MILLISECONDS);
    }
    assertEquals("worker must finish", 0, done.getCount());
  }
  @Test public void serverAndNetworkErrorsHaveRecoveryInstructions() {
    assertTrue(UpdateDownloader.serverError(404).contains("官网"));
    assertTrue(UpdateDownloader.serverError(503).contains("稍后重试"));
    assertTrue(UpdateDownloader.networkError(new UnknownHostException()).contains("检查网络"));
    assertTrue(UpdateDownloader.networkError(new SocketTimeoutException()).contains("超时"));
    assertTrue(UpdateDownloader.networkError(new javax.net.ssl.SSLException("test")).contains("系统时间"));
    assertTrue(UpdateDownloader.networkError(new IllegalArgumentException()).contains("格式异常"));
  }
  @Test public void rejectsMalformedAndOversizedManifests() throws Exception {
    for (byte[] body : new byte[][] {"{}".getBytes(StandardCharsets.UTF_8), new byte[65537]}) {
      StubConnection conn = new StubConnection(200, body);
      UpdateDownloader downloader = new StubDownloader(conn);
      CountDownLatch done = new CountDownLatch(1); final String[] error = {null};
      downloader.checkUpdate("https://example.com/update.json", new UpdateDownloader.CheckCallback() {
        public void onCheckSuccess(AppUpdate.UpdateInfo info) { error[0] = "unexpected success"; done.countDown(); }
        public void onCheckError(String message) { error[0] = message; done.countDown(); }
      });
      await(done); assertNotNull(error[0]); assertNotEquals("unexpected success", error[0]);
      assertFalse(conn.getInstanceFollowRedirects());
    }
  }
  @Test public void downloadRejectsHttpFailureOversizeAndCorruptionAndCleansTemp() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    AppUpdate.UpdateInfo info = new AppUpdate.UpdateInfo(100, "1.0", "https://example.com/a.apk", "a".repeat(64), 1, "", 23);
    for (StubConnection conn : new StubConnection[]{new StubConnection(404, new byte[0]),
        new StubConnection(200, new byte[]{1}), new StubConnection(200, new byte[]{2})}) {
      if (conn.body.length > 0 && conn.body[0] == 1) conn.length = AppUpdate.MAX_APK_SIZE + 1;
      CountDownLatch done = new CountDownLatch(1);
      final String[] error = {null};
      File dir = StillFileProvider.getUpdateDir(context);
      UpdateDownloader downloader = new StubDownloader(conn);
      downloader.downloadApk(context, info, dir, new UpdateDownloader.DownloadCallback() {
        public void onProgress(int p, long b, long t) {}
        public void onSuccess(File file) { error[0] = "unexpected success"; done.countDown(); }
        public void onError(String message) { error[0] = message; done.countDown(); }
        public void onCancelled() { error[0] = "unexpected cancel"; done.countDown(); }
      });
      await(done);
      assertNotNull(error[0]); assertNotEquals("unexpected success", error[0]);
      assertFalse(new File(dir, AppUpdate.getUpdateTmpFileName(info.versionCode)).exists());
      assertFalse(new File(dir, AppUpdate.getUpdateApkFileName(info.versionCode)).exists());
    }
  }
  @Test public void cancellationDisconnectsAndRemovesPartialDownload() throws Exception {
    Context context = RuntimeEnvironment.getApplication();
    CountDownLatch released = new CountDownLatch(1);
    StubConnection conn = new StubConnection(200, new byte[]{1}) {
      @Override public InputStream getInputStream() {
        return new InputStream() {
          boolean sent;
          @Override public int read() throws IOException { return -1; }
          @Override public int read(byte[] buffer) throws IOException {
            if (!sent) { sent = true; buffer[0] = 1; return 1; }
            try { released.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new IOException(e); }
            return -1;
          }
        };
      }
      @Override public void disconnect() { released.countDown(); }
    };
    conn.length = 4;
    UpdateDownloader downloader = new StubDownloader(conn);
    AppUpdate.UpdateInfo info = new AppUpdate.UpdateInfo(101, "1.1", "https://example.com/a.apk", "a".repeat(64), 4, "", 23);
    File dir = StillFileProvider.getUpdateDir(context);
    CountDownLatch done = new CountDownLatch(1); final String[] result = {null};
    downloader.downloadApk(context, info, dir, new UpdateDownloader.DownloadCallback() {
      public void onProgress(int p, long b, long t) { downloader.cancel(); }
      public void onSuccess(File file) { result[0] = "success"; done.countDown(); }
      public void onError(String message) { result[0] = message; done.countDown(); }
      public void onCancelled() { result[0] = "cancelled"; done.countDown(); }
    });
    await(done); assertEquals("cancelled", result[0]);
    assertFalse(new File(dir, AppUpdate.getUpdateTmpFileName(info.versionCode)).exists());
    assertFalse(new File(dir, AppUpdate.getUpdateApkFileName(info.versionCode)).exists());
  }

  static class StubDownloader extends UpdateDownloader {
    final StubConnection conn;
    StubDownloader(StubConnection conn) { this.conn = conn; }
    @Override HttpURLConnection openConnection(URL url) { return conn; }
  }
  static class StubConnection extends HttpURLConnection {
    final int status; final byte[] body; long length;
    StubConnection(int status, byte[] body) throws Exception {
      super(new URL("https://example.com/test")); this.status = status; this.body = body; length = body.length;
    }
    public int getResponseCode() { return status; }
    public InputStream getInputStream() { return new ByteArrayInputStream(body); }
    public long getContentLengthLong() { return length; }
    public int getContentLength() { return (int)length; }
    public void connect() {} public void disconnect() {} public boolean usingProxy() { return false; }
  }
}
