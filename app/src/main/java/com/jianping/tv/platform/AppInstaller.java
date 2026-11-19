package com.jianping.tv.platform;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import java.io.File;

/**
 * Handles package installer intent dispatching and Android 8.0+ unknown sources
 * permission lifecycle requests with fallback handling for diverse TV ROMs.
 */
public final class AppInstaller {
  public static final int REQUEST_INSTALL_PERM = 63;

  private AppInstaller() {}

  public static boolean canRequestInstall(Context context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      try {
        return context.getPackageManager().canRequestPackageInstalls();
      } catch (Exception e) {
        return false;
      }
    }
    return true;
  }

  public static void requestInstallPermission(Activity activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      Intent intent =
          new Intent(
              Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
              Uri.parse("package:" + activity.getPackageName()));
      if (intent.resolveActivity(activity.getPackageManager()) != null) {
        activity.startActivityForResult(intent, REQUEST_INSTALL_PERM);
      } else {
        Intent fallback = new Intent(Settings.ACTION_SECURITY_SETTINGS);
        if (fallback.resolveActivity(activity.getPackageManager()) != null) {
          activity.startActivityForResult(fallback, REQUEST_INSTALL_PERM);
        } else {
          activity.startActivityForResult(new Intent(Settings.ACTION_SETTINGS), REQUEST_INSTALL_PERM);
        }
      }
    }
  }

  public static void installApk(Activity activity, File apkFile) {
    if (apkFile == null || !apkFile.exists()) {
      throw new IllegalArgumentException("APK file does not exist");
    }
    Intent intent = new Intent(Intent.ACTION_VIEW);
    Uri uri = StillFileProvider.getUriForFile(activity, apkFile);
    intent.setDataAndType(uri, "application/vnd.android.package-archive");
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    activity.startActivity(intent);
  }
}
