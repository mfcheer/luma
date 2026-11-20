package com.jianping.tv.platform;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import java.io.File;
import java.nio.file.Files;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class AppUpdateVerificationTest {
  Context context;
  File apk;
  PackageInfo archive;
  AppUpdate.UpdateInfo info;
  @Before public void setup() throws Exception {
    context = RuntimeEnvironment.getApplication();
    apk = new File(StillFileProvider.getUpdateDir(context), "test.apk");
    Files.write(apk.toPath(), new byte[]{1,2,3,4});
    PackageInfo installed = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    installed.signatures = new Signature[]{new Signature("abcd")};
    Shadows.shadowOf(context.getPackageManager()).installPackage(installed);
    archive = new PackageInfo(); archive.packageName = context.getPackageName();
    archive.versionCode = installed.versionCode + 1;
    archive.signatures = new Signature[]{new Signature("abcd")};
    archive.applicationInfo = new ApplicationInfo(); archive.applicationInfo.minSdkVersion = 23;
    Shadows.shadowOf(context.getPackageManager()).setPackageArchiveInfo(apk.getAbsolutePath(), archive);
    info = new AppUpdate.UpdateInfo(archive.versionCode, "9.9.9", "https://example.com/a.apk", AppUpdate.calculateSha256(apk), 4, "", 23);
  }
  @Test public void acceptsMatchingIdentityAndRejectsTamperedBytes() throws Exception {
    assertTrue(AppUpdate.verifyApk(context, apk, info).success);
    Files.write(apk.toPath(), new byte[]{4,3,2,1});
    assertFalse(AppUpdate.verifyApk(context, apk, info).success);
  }
  @Test public void rejectsWrongPackageVersionSignatureAndMinimumSystem() {
    archive.packageName = "other.app";
    assertFalse(AppUpdate.verifyApk(context, apk, info).success);
    archive.packageName = context.getPackageName(); archive.versionCode++;
    assertFalse(AppUpdate.verifyApk(context, apk, info).success);
    archive.versionCode--; archive.signatures = new Signature[]{new Signature("dcba")};
    assertFalse(AppUpdate.verifyApk(context, apk, info).success);
    archive.signatures = new Signature[]{new Signature("abcd")}; archive.applicationInfo.minSdkVersion = 35;
    assertFalse(AppUpdate.verifyApk(context, apk, info).success);
  }
  @Test public void strictHttpsAndUnknownSizeSpaceBudget() {
    assertFalse(AppUpdate.isHttpsUrl("https://"));
    assertFalse(AppUpdate.isHttpsUrl("https://user:pass@example.com/a.apk"));
    assertFalse(AppUpdate.isHttpsUrl("https://example.com/a.apk#fragment"));
    assertTrue(AppUpdate.isHttpsUrl("https://example.com/a.apk"));
    assertEquals(AppUpdate.MAX_APK_SIZE + AppUpdate.SAFETY_MARGIN_BYTES, AppUpdate.requiredSpace(0));
  }
}
