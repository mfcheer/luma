package com.jianping.tv.platform;

import static org.junit.Assert.*;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class UpdateSessionTest {
  Context context;
  SharedPreferences prefs;
  FakeDownloader transport;
  UpdateSession session;
  AppUpdate.UpdateInfo next;
  @Before public void setup() {
    context = RuntimeEnvironment.getApplication();
    prefs = context.getSharedPreferences("still_update_session", Context.MODE_PRIVATE);
    prefs.edit().clear().commit();
    context.getSharedPreferences("still_update_history", Context.MODE_PRIVATE).edit().clear().commit();
    File[] files = StillFileProvider.getUpdateDir(context).listFiles();
    if (files != null) for (File file : files) file.delete();
    next = new AppUpdate.UpdateInfo(DeviceDiagnostics.appVersionCode(context) + 1, "9.9.9",
        "https://example.com/update.apk", "a".repeat(64), 1024, "测试更新", 23);
    transport = new FakeDownloader();
    session = new UpdateSession(context, () -> transport);
  }
  private void available() {
    session.check(); transport.checks.get(0).onCheckSuccess(next);
    assertEquals(UpdateSession.State.AVAILABLE, session.state);
  }
  private void ready() {
    available(); session.download(); transport.download.onSuccess(session.apk());
    assertEquals(UpdateSession.State.READY, session.state);
  }
  @Test public void checksUseMirrorAndRetryManifestAfterBothFail() {
    session.check(); session.check();
    assertEquals(1, transport.checks.size());
    transport.checks.get(0).onCheckError("404");
    assertEquals(AppUpdate.DEFAULT_MIRROR_URL, transport.urls.get(1));
    transport.checks.get(1).onCheckError("服务暂不可用");
    assertEquals(UpdateSession.State.ERROR, session.state);
    session.retry();
    assertEquals(3, transport.checks.size());
  }
  @Test public void cancellationIgnoresStaleResultsAndAllowsRetry() {
    session.check(); UpdateDownloader.CheckCallback old = transport.checks.get(0);
    session.cancel(); session.check(); old.onCheckSuccess(next);
    assertNull(session.info);
    transport.checks.get(1).onCheckSuccess(next);
    session.download(); UpdateDownloader.DownloadCallback stale = transport.download;
    session.cancel(); session.download(); stale.onSuccess(session.apk());
    assertEquals(UpdateSession.State.DOWNLOADING, session.state);
    assertEquals("", prefs.getString("manifest", ""));
    transport.download.onSuccess(session.apk());
    assertEquals(UpdateSession.State.READY, session.state);
  }
  @Test public void downloadSurvivesDetachedUiAndPreventsDuplicates() {
    available(); final int[] calls = {0};
    UpdateSession.Listener listener = () -> calls[0]++;
    session.addListener(listener); session.download(); session.download();
    assertEquals(1, transport.downloadCalls);
    session.removeListener(listener); int before = calls[0];
    transport.download.onProgress(120, 1024, 1024);
    assertEquals(100, session.percent); assertEquals(before, calls[0]);
    transport.download.onSuccess(session.apk());
    assertEquals(UpdateSession.State.READY, session.state);
    UpdateSession restored = new UpdateSession(context, () -> transport);
    assertEquals(next.versionCode, restored.info.versionCode);
    assertEquals(UpdateSession.State.READY, restored.state);
  }
  @Test public void failedDownloadRetriesDownloadRatherThanCheck() {
    available(); session.download(); transport.download.onError("断网");
    session.retry(); assertEquals(2, transport.downloadCalls);
    assertEquals(1, transport.checks.size());
  }
  @Test public void permissionAndInstallerReturnKeepPackageWithoutClaimingSuccess() {
    ready();
    for (String stage : new String[]{"permission", "installing"}) {
      prefs.edit().putString("stage", stage).commit();
      UpdateSession restored = new UpdateSession(context, () -> transport);
      assertNull(restored.reconcileInstalledVersion());
      assertTrue(restored.returnedFromSystem());
      assertFalse(restored.returnedFromSystem());
      assertEquals(UpdateSession.State.READY, restored.state);
      assertFalse(prefs.getString("manifest", "").isEmpty());
    }
  }
  @Test public void onlyActualUpgradeProducesSuccessOnceAndCleansFiles() throws Exception {
    ready();
    File apk = session.apk(); assertTrue(apk.createNewFile());
    File tmp = new File(apk.getParentFile(), "still_update_777.tmp"); assertTrue(tmp.createNewFile());
    AppUpdate.UpdateInfo installed = new AppUpdate.UpdateInfo(DeviceDiagnostics.appVersionCode(context),
        "0.9.9", next.downloadUrl, next.sha256, next.size, "", 23);
    prefs.edit().putString("manifest", installed.toJson()).commit();
    UpdateSession upgraded = new UpdateSession(context, () -> transport);
    assertNotNull(upgraded.reconcileInstalledVersion());
    assertNull(upgraded.reconcileInstalledVersion());
    assertFalse(apk.exists()); assertFalse(tmp.exists());
    assertEquals("", prefs.getString("manifest", ""));
  }
  @Test public void ordinaryLatestCheckDoesNotClaimUpgradeOnResume() {
    session.check();
    transport.checks.get(0).onCheckSuccess(new AppUpdate.UpdateInfo(DeviceDiagnostics.appVersionCode(context),
        "0.9.9", next.downloadUrl, next.sha256, 0, "", 23));
    assertEquals(UpdateSession.State.CURRENT, session.state);
    assertNull(session.reconcileInstalledVersion());
  }
  @Test public void installAlwaysReverifiesAndRejectsTampering() throws Exception {
    ready(); File apk = session.apk(); assertTrue(apk.createNewFile());
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    session.install(activity); assertEquals(UpdateSession.State.VERIFYING, session.state);
    transport.verify.onVerified(AppUpdate.VerificationResult.failure("签名不符"));
    assertEquals(UpdateSession.State.ERROR, session.state);
    assertFalse(apk.exists());
    assertEquals("", prefs.getString("manifest", ""));
    session.retry(); assertEquals(UpdateSession.State.DOWNLOADING, session.state);
    activity.finish();
  }
  @Test public void cachedApkValidationRunsOffUiAndCorruptionAllowsRedownload() throws Exception {
    File apk = new File(StillFileProvider.getUpdateDir(context), AppUpdate.getUpdateApkFileName(next.versionCode));
    assertTrue(apk.createNewFile()); session.check(); transport.checks.get(0).onCheckSuccess(next);
    assertEquals(UpdateSession.State.VERIFYING, session.state);
    transport.verify.onVerified(AppUpdate.VerificationResult.failure("损坏"));
    assertEquals(UpdateSession.State.AVAILABLE, session.state); assertFalse(apk.exists());
  }
  @Test public void incompatibleDevicesCannotDownloadFromCheckResult() {
    session.check(); transport.checks.get(0).onCheckSuccess(new AppUpdate.UpdateInfo(next.versionCode,
        next.versionName, next.downloadUrl, next.sha256, 1024, "", 35));
    assertEquals(UpdateSession.State.INCOMPATIBLE, session.state);
  }
  @Test public void installerDispatchFailureKeepsRetryState() {
    ready();
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    org.robolectric.Shadows.shadowOf(context.getPackageManager()).setCanRequestPackageInstalls(true);
    session.install(activity);
    transport.verify.onVerified(AppUpdate.VerificationResult.success());
    // This test uses a recorded download, not a real file. Dispatch must fail safely and keep state.
    assertEquals(UpdateSession.State.READY, session.state);
    assertFalse(prefs.getString("manifest", "").isEmpty());
    assertNull(session.reconcileInstalledVersion());
    activity.finish();
  }

  @Test public void dialogDismissKeepsDownloadAndProgressKeepsFocusedButton() throws Exception {
    available();
    java.lang.reflect.Field field = UpdateSession.class.getDeclaredField("instance");
    field.setAccessible(true); field.set(null, session);
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    final boolean[] returnedHome = {false};
    com.jianping.tv.ui.AppUpdateDialog dialog = new com.jianping.tv.ui.AppUpdateDialog(activity,
        com.jianping.tv.ui.UiPalette.DARK, 1f, () -> returnedHome[0] = true);
    dialog.show(); session.download();
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    android.view.View before = dialog.getWindow().getDecorView().findFocus();
    transport.download.onProgress(25, 256, 1024);
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertNotNull(before);
    assertSame(before, dialog.getWindow().getDecorView().findFocus());
    assertEquals("返回桌面", ((android.widget.TextView) before).getText().toString());
    before.performClick();
    assertTrue(returnedHome[0]);
    assertFalse(dialog.isShowing());
    assertEquals(UpdateSession.State.DOWNLOADING, session.state);
    transport.download.onSuccess(session.apk());
    com.jianping.tv.ui.AppUpdateDialog reopened = new com.jianping.tv.ui.AppUpdateDialog(activity,
        com.jianping.tv.ui.UiPalette.LIGHT, 1f);
    reopened.show();
    assertEquals(UpdateSession.State.READY, session.state);
    reopened.dismiss(); activity.finish(); field.set(null, null);
  }

  @Test public void successfulDispatchOnlyRecordsInstallingUntilVersionActuallyChanges() throws Exception {
    ready(); assertTrue(session.apk().createNewFile());
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    org.robolectric.Shadows.shadowOf(context.getPackageManager()).setCanRequestPackageInstalls(true);
    session.install(activity);
    transport.verify.onVerified(AppUpdate.VerificationResult.success());
    assertEquals(UpdateSession.State.INSTALLING, session.state);
    assertEquals("installing", prefs.getString("stage", ""));
    assertNotNull(org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity());
    assertNull(session.reconcileInstalledVersion());
    assertTrue(session.returnedFromSystem());
    assertEquals(UpdateSession.State.READY, session.state);
    assertTrue(session.apk().exists());
    activity.finish();
  }

  @Test public void missingPermissionIsRecordedBeforeOpeningSettings() throws Exception {
    ready(); assertTrue(session.apk().createNewFile());
    Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
    org.robolectric.Shadows.shadowOf(context.getPackageManager()).setCanRequestPackageInstalls(false);
    session.install(activity);
    transport.verify.onVerified(AppUpdate.VerificationResult.success());
    assertEquals(UpdateSession.State.PERMISSION, session.state);
    assertEquals("permission", prefs.getString("stage", ""));
    assertTrue(session.returnedFromSystem());
    assertTrue(session.message.contains("尚未允许"));
    assertTrue(session.apk().exists());
    activity.finish();
  }

  @Test public void firstUpgradeFromLegacyClientShowsSuccessOnceButFreshInstallDoesNot() throws Exception {
    android.content.pm.PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    installed.firstInstallTime = 100; installed.lastUpdateTime = 100;
    org.robolectric.Shadows.shadowOf(context.getPackageManager()).installPackage(installed);
    assertNull(session.reconcileInstalledVersion());
    context.getSharedPreferences("still_update_history", Context.MODE_PRIVATE).edit().clear().commit();
    installed.lastUpdateTime = 200;
    org.robolectric.Shadows.shadowOf(context.getPackageManager()).installPackage(installed);
    assertNotNull(session.reconcileInstalledVersion());
    assertNull(session.reconcileInstalledVersion());
  }

  static class FakeDownloader extends UpdateDownloader {
    List<CheckCallback> checks = new ArrayList<>(); List<String> urls = new ArrayList<>();
    DownloadCallback download; VerifyCallback verify; int downloadCalls;
    @Override public void checkUpdate(String url, CheckCallback cb) { urls.add(url); checks.add(cb); }
    @Override public void downloadApk(Context c, AppUpdate.UpdateInfo i, File d, DownloadCallback cb) { download = cb; downloadCalls++; }
    @Override public void verifyCached(Context c, File f, AppUpdate.UpdateInfo i, VerifyCallback cb) { verify = cb; }
    @Override public void cancel() {}
  }
}
