package com.jianping.tv.platform;

import android.content.*;
import com.jianping.tv.data.AppUsageStore;

/** FULLY_REMOVED also arrives when Still's UI is absent; APK replacement keeps its weight. */
public final class AppRemovedReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context context, Intent intent) {
    if (!Intent.ACTION_PACKAGE_FULLY_REMOVED.equals(intent.getAction())
        || intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
        || intent.getData() == null) return;
    String pkg = intent.getData().getSchemeSpecificPart();
    if (pkg != null && !pkg.isEmpty()) new AppUsageStore(context).removed(pkg);
  }
}
