package com.jianping.tv.platform;

import android.content.*;
import android.content.pm.ResolveInfo;
import android.os.Build;
import com.jianping.tv.data.Preferences;

public final class DeviceDiagnostics {
  public static String homePackage(Context c) {
    ResolveInfo r =
        c.getPackageManager()
            .resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0);
    return r == null || r.activityInfo == null ? "未解析" : r.activityInfo.packageName;
  }

  public static String report(Context c) {
    Preferences p = new Preferences(c);
    return "设备  "
        + Build.MANUFACTURER
        + " / "
        + Build.MODEL
        + "\nAndroid  "
        + Build.VERSION.RELEASE
        + "  ·  API "
        + Build.VERSION.SDK_INT
        + "\n架构  "
        + android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS)
        + "\n系统  "
        + Build.DISPLAY
        + "\n当前默认桌面  "
        + homePackage(c)
        + "\n开机通知  "
        + p.read("bootReceived")
        + "\n启动请求  "
        + p.read("bootAttempt")
        + "\n开机进入界面  "
        + p.read("bootVisible")
        + "\n上次 Home 进入  "
        + p.read("homeVisible");
  }
}
