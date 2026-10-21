package com.jianping.tv.platform;

import android.content.*;
import com.jianping.tv.data.Preferences;
import com.jianping.tv.ui.MainActivity;
import java.text.DateFormat;
import java.util.Date;

public final class BootReceiver extends BroadcastReceiver {
  @Override
  public void onReceive(Context c, Intent intent) {
    if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
    Preferences p = new Preferences(c);
    p.record("bootReceived", DateFormat.getDateTimeInstance().format(new Date()));
    if (!p.bootEnabled()) return;
    p.record("bootAttempt", "已请求打开；是否成功请查看界面记录");
    try {
      c.startActivity(
          new Intent(c, MainActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              .putExtra("boot", true));
    } catch (RuntimeException e) {
      p.record("bootAttempt", "系统拒绝：" + e.getClass().getSimpleName());
    }
  }
}
