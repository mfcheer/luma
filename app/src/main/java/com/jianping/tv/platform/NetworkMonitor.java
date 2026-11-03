package com.jianping.tv.platform;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import com.jianping.tv.R;

/**
 * Monitors TV network connectivity state (Ethernet, Wi-Fi, Disconnected)
 * without requiring location permissions or internet access.
 */
public final class NetworkMonitor {

  public enum Type {
    DISCONNECTED,
    WIFI,
    ETHERNET,
    OTHER
  }

  public static final class State {
    public final Type type;
    public final int iconRes;
    public final String label;
    public final boolean connected;

    public State(Type type, int iconRes, String label, boolean connected) {
      this.type = type;
      this.iconRes = iconRes;
      this.label = label;
      this.connected = connected;
    }

    public static final State DISCONNECTED =
        new State(
            Type.DISCONNECTED,
            R.drawable.nav_network_disconnected,
            "未连接网络",
            false);

    public static final State ETHERNET =
        new State(
            Type.ETHERNET,
            R.drawable.nav_network_ethernet,
            "有线网络已连接",
            true);

    public static State wifi(String ssid) {
      String name = (ssid == null || ssid.isEmpty()) ? "Wi-Fi 已连接" : "Wi-Fi: " + ssid;
      return new State(Type.WIFI, R.drawable.nav_network_wifi, name, true);
    }

    public static final State OTHER =
        new State(
            Type.OTHER,
            R.drawable.nav_network_wifi,
            "网络已连接",
            true);
  }

  public interface Listener {
    void onNetworkChanged(State state);
  }

  private final Context context;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private ConnectivityManager.NetworkCallback callback;
  private Listener listener;
  private boolean registered;

  public NetworkMonitor(Context context) {
    this.context = context.getApplicationContext();
  }

  public static State current(Context context) {
    try {
      ConnectivityManager cm =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (cm == null) return State.DISCONNECTED;

      if (Build.VERSION.SDK_INT >= 23) {
        Network active = cm.getActiveNetwork();
        if (active == null) return State.DISCONNECTED;
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        if (caps == null) return State.DISCONNECTED;

        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
          return State.ETHERNET;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
          String ssid = detectWifiSsid(context, caps);
          return State.wifi(ssid);
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
          return State.OTHER;
        }
        return State.OTHER;
      } else {
        @SuppressWarnings("deprecation")
        android.net.NetworkInfo info = cm.getActiveNetworkInfo();
        if (info == null || !info.isConnected()) return State.DISCONNECTED;
        if (info.getType() == ConnectivityManager.TYPE_ETHERNET) return State.ETHERNET;
        if (info.getType() == ConnectivityManager.TYPE_WIFI) {
          return State.wifi(detectWifiSsid(context, null));
        }
        return State.OTHER;
      }
    } catch (Exception ignored) {
      return State.DISCONNECTED;
    }
  }

  private static String detectWifiSsid(Context context, NetworkCapabilities caps) {
    try {
      if (Build.VERSION.SDK_INT >= 31 && caps != null) {
        Object transport = caps.getTransportInfo();
        if (transport instanceof WifiInfo) {
          return cleanSsid(((WifiInfo) transport).getSSID());
        }
      }
      WifiManager wm =
          (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      if (wm != null) {
        @SuppressWarnings("deprecation")
        WifiInfo info = wm.getConnectionInfo();
        if (info != null) {
          return cleanSsid(info.getSSID());
        }
      }
    } catch (Exception ignored) {
    }
    return "";
  }

  private static String cleanSsid(String raw) {
    if (raw == null || raw.isEmpty() || "<unknown ssid>".equals(raw) || "0x".equals(raw)) {
      return "";
    }
    if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() > 2) {
      return raw.substring(1, raw.length() - 1);
    }
    return raw;
  }

  public synchronized void start(Listener listener) {
    this.listener = listener;
    if (registered) return;
    try {
      ConnectivityManager cm =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (cm == null) return;

      callback =
          new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
              notifyChange();
            }

            @Override
            public void onLost(Network network) {
              notifyChange();
            }

            @Override
            public void onCapabilitiesChanged(Network network, NetworkCapabilities capabilities) {
              notifyChange();
            }
          };

      if (Build.VERSION.SDK_INT >= 24) {
        cm.registerDefaultNetworkCallback(callback);
      } else {
        NetworkRequest request = new NetworkRequest.Builder().build();
        cm.registerNetworkCallback(request, callback);
      }
      registered = true;
    } catch (Exception ignored) {
    }
  }

  public synchronized void stop() {
    listener = null;
    if (!registered || callback == null) return;
    try {
      ConnectivityManager cm =
          (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      if (cm != null) cm.unregisterNetworkCallback(callback);
    } catch (Exception ignored) {
    }
    callback = null;
    registered = false;
  }

  private void notifyChange() {
    handler.post(
        () -> {
          Listener l;
          synchronized (NetworkMonitor.this) {
            l = listener;
          }
          if (l != null) l.onNetworkChanged(current(context));
        });
  }

  public static void openSettings(Context context) {
    String[] actions =
        new String[] {
          Settings.ACTION_WIFI_SETTINGS,
          Settings.ACTION_WIRELESS_SETTINGS,
          Settings.ACTION_SETTINGS
        };
    for (String action : actions) {
      try {
        Intent intent = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) != null) {
          context.startActivity(intent);
          return;
        }
      } catch (Exception ignored) {
      }
    }
  }
}
