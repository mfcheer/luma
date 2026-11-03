import com.jianping.tv.platform.NetworkMonitor;

public final class NetworkMonitorTest {
  private static void check(boolean condition) {
    if (!condition) throw new AssertionError("Test assertion failed");
  }

  public static void main(String[] args) {
    // 1. Verify disconnected state
    NetworkMonitor.State disconnected = NetworkMonitor.State.DISCONNECTED;
    check(!disconnected.connected);
    check(disconnected.type == NetworkMonitor.Type.DISCONNECTED);
    check(disconnected.label.equals("未连接网络"));

    // 2. Verify ethernet state
    NetworkMonitor.State ethernet = NetworkMonitor.State.ETHERNET;
    check(ethernet.connected);
    check(ethernet.type == NetworkMonitor.Type.ETHERNET);
    check(ethernet.label.equals("有线网络已连接"));

    // 3. Verify wifi state with/without SSID
    NetworkMonitor.State wifiNoSsid = NetworkMonitor.State.wifi("");
    check(wifiNoSsid.connected);
    check(wifiNoSsid.type == NetworkMonitor.Type.WIFI);
    check(wifiNoSsid.label.equals("Wi-Fi 已连接"));

    NetworkMonitor.State wifiSsid = NetworkMonitor.State.wifi("Home_5G");
    check(wifiSsid.connected);
    check(wifiSsid.type == NetworkMonitor.Type.WIFI);
    check(wifiSsid.label.equals("Wi-Fi: Home_5G"));

    System.out.println("PASS: NetworkMonitor state transitions, labels, and types verified.");
  }
}
