package com.jianping.tv.platform;

import android.app.Activity;
import com.jianping.tv.data.*;

/** The only user-app launch path; a rejected launch never contributes usage. */
public final class AppLaunch {
  private AppLaunch() {}

  public static void open(Activity activity, AppRepository repository, AppEntry app) {
    activity.startActivity(repository.launchIntent(app));
    try {
      new AppUsageStore(activity).opened(app.component.getPackageName());
    } catch (RuntimeException error) {
      android.util.Log.w("StillSearch", "Usage unavailable; app launch is unaffected", error);
    }
  }
}
