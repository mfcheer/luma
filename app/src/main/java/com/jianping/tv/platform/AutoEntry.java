package com.jianping.tv.platform;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.jianping.tv.data.Preferences;

/** One persisted switch controls boot entry and, on supported systems, standby entry. */
public final class AutoEntry {
  private AutoEntry() {}

  public static boolean supportsWake() {
    // Later versions require a separately verified background-launch strategy.
    return Build.VERSION.SDK_INT <= 28;
  }

  public static boolean sync(Context context) {
    Preferences prefs = new Preferences(context);
    if (!supportsWake()) return false;
    Intent service = new Intent(context, WakeEntryService.class);
    if (!prefs.bootEnabled()) {
      context.stopService(service);
      return true;
    }
    if (WakeEntryService.running()) return true;
    try {
      if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
      else context.startService(service);
      return true;
    } catch (RuntimeException e) {
      prefs.record("wakeMonitor", "监听未启动：" + e.getClass().getSimpleName());
      return false;
    }
  }
}
