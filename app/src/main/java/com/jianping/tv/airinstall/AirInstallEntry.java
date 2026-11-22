package com.jianping.tv.airinstall;

import android.content.ComponentName;
import com.jianping.tv.data.AppEntry;

/**
 * System action entry for LAN Air Install (无线快传与隔空安装).
 */
public final class AirInstallEntry {
  public static final String ACTION_PKG = "still.action.air_install";
  public static final String ACTION_CLASS = "AirInstall";
  public static final ComponentName COMPONENT = new ComponentName(ACTION_PKG, ACTION_CLASS);

  public static final AppEntry ENTRY =
      new AppEntry(COMPONENT, "隔空安装", "still.action.air_install", Long.MAX_VALUE - 1);

  private AirInstallEntry() {}

  public static boolean isAirInstallEntry(AppEntry entry) {
    return entry != null
        && entry.component != null
        && ACTION_PKG.equals(entry.component.getPackageName());
  }

  public static boolean isAirInstallId(String id) {
    return id != null && id.startsWith(ACTION_PKG);
  }
}
