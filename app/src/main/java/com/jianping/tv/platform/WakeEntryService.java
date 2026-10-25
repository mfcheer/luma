package com.jianping.tv.platform;

import android.app.*;
import android.content.*;
import android.os.*;
import android.util.Log;
import com.jianping.tv.R;
import com.jianping.tv.data.Preferences;
import com.jianping.tv.ui.MainActivity;
import java.text.DateFormat;
import java.util.Date;

/** Opt-in, event-driven Android 6–9 wake monitor. No polling, wake lock or wake command. */
public final class WakeEntryService extends Service {
  private static final String CHANNEL = "still_auto_entry";
  private static final int NOTIFICATION = 21;
  private static boolean active;
  private static WakeEntryService instance;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Preferences prefs;
  private PowerManager power;
  private WakeCycle cycle;
  private boolean registered;
  private long pendingId;

  public static boolean running() {
    return active;
  }

  /** A deliberate desktop switch wins over a return already queued for this wake cycle. */
  public static void cancelPendingReturn() {
    if (instance == null) return;
    instance.handler.removeCallbacks(instance.returnHome);
    instance.handler.removeCallbacks(instance.confirmEntry);
    instance.pendingId = 0;
  }

  private final Runnable confirmEntry =
      () -> {
        if (pendingId == 0) return;
        boolean visible = Long.toString(pendingId).equals(prefs.read("wakeVisibleId"));
        prefs.record("wakeAttempt", now() + (visible ? " · 已进入 Still" : " · 请求后未确认进入"));
        Log.i("StillWake", "confirmed=" + visible + " id=" + pendingId);
        pendingId = 0;
      };

  private final Runnable returnHome =
      () -> {
        if (pendingId == 0 || !prefs.bootEnabled() || !power.isInteractive()) return;
        long id = pendingId;
        prefs.record("wakeAttempt", now() + " · 已请求返回 Still");
        Log.i("StillWake", "request id=" + id);
        try {
          startActivity(
              new Intent(this, MainActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                  .putExtra("wakeId", id));
          handler.postDelayed(confirmEntry, 3000);
        } catch (RuntimeException e) {
          prefs.record("wakeAttempt", now() + " · 系统拒绝：" + e.getClass().getSimpleName());
          pendingId = 0;
        }
      };

  private final BroadcastReceiver screen =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
            cycle.screenOff();
            handler.removeCallbacks(returnHome);
            handler.removeCallbacks(confirmEntry);
            pendingId = 0;
            Log.i("StillWake", "screenOff");
          } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction()) && cycle.screenOn()) {
            if (!prefs.bootEnabled()) return;
            pendingId = System.currentTimeMillis();
            prefs.record("wakeReceived", now());
            Log.i("StillWake", "screenOn id=" + pendingId);
            // Let the TV finish its own wake transition; make only one entry request.
            handler.postDelayed(returnHome, 1000);
          }
        }
      };

  // Both the resource-enabled component and AutoEntry guard exclude API 29+ entirely.
  @android.annotation.SuppressLint("ForegroundServiceType")
  @Override
  public void onCreate() {
    super.onCreate();
    prefs = new Preferences(this);
    power = (PowerManager) getSystemService(POWER_SERVICE);
    if (!AutoEntry.supportsWake() || !prefs.bootEnabled()) {
      stopSelf();
      return;
    }
    try {
      NotificationManager notifications =
          (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      if (Build.VERSION.SDK_INT >= 26) {
        NotificationChannel channel =
            new NotificationChannel(CHANNEL, "自动进入 Still", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("开机及待机唤醒后返回 Still");
        channel.setSound(null, null);
        notifications.createNotificationChannel(channel);
      }
      Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      PendingIntent content =
          PendingIntent.getActivity(
              this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      Notification.Builder notification =
          Build.VERSION.SDK_INT >= 26
              ? new Notification.Builder(this, CHANNEL)
              : new Notification.Builder(this);
      startForeground(
          NOTIFICATION,
          notification
              .setSmallIcon(R.drawable.settings_home)
              .setContentTitle("自动进入 Still 已开启")
              .setContentText("待机唤醒后返回首页，可在 Still 设置中关闭")
              .setContentIntent(content)
              .setCategory(Notification.CATEGORY_SERVICE)
              .setPriority(Notification.PRIORITY_LOW)
              .setOngoing(true)
              .setShowWhen(false)
              .build());
      cycle = new WakeCycle(power.isInteractive());
      IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
      filter.addAction(Intent.ACTION_SCREEN_ON);
      registerReceiver(screen, filter);
      registered = true;
      active = true;
      instance = this;
      prefs.record("wakeMonitor", now() + " · 正在监听待机唤醒");
      Log.i("StillWake", "monitorStarted interactive=" + power.isInteractive());
    } catch (RuntimeException e) {
      prefs.record("wakeMonitor", "监听未启动：" + e.getClass().getSimpleName());
      stopSelf();
    }
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (!active || !prefs.bootEnabled()) {
      stopSelf();
      return START_NOT_STICKY;
    }
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    active = false;
    if (instance == this) instance = null;
    handler.removeCallbacksAndMessages(null);
    if (registered) unregisterReceiver(screen);
    stopForeground(true);
    if (prefs != null) prefs.record("wakeMonitor", now() + " · 监听已停止");
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  public static void recordVisible(Context context, long id) {
    Preferences prefs = new Preferences(context);
    prefs.record("wakeVisibleId", Long.toString(id));
    prefs.record("wakeVisible", now());
    Log.i("StillWake", "resumed id=" + id);
  }

  private static String now() {
    return DateFormat.getDateTimeInstance().format(new Date());
  }
}
