package com.jianping.tv.platform;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Resume the opted-in monitor after an in-place APK upgrade; never opens the UI. */
public final class PackageUpdatedReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) AutoEntry.sync(context);
  }
}
