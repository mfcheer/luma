import com.jianping.tv.platform.WakeCycle;

public final class WakeCycleTest {
  private static void check(boolean result) {
    if (!result) throw new AssertionError();
  }
  public static void main(String[] args) {
    WakeCycle awake = new WakeCycle(true);
    check(!awake.screenOn()); // Starting/restarting a monitor never hijacks an awake TV.
    awake.screenOff();
    check(awake.screenOn());
    check(!awake.screenOn()); // Duplicate SCREEN_ON cannot create a second request.
    awake.screenOff(); awake.screenOff();
    check(awake.screenOn());
    check(!awake.screenOn());
    WakeCycle asleep = new WakeCycle(false);
    check(asleep.screenOn()); // A sticky restart during sleep still handles the next wake.
    check(!asleep.screenOn());
    for (int i=0;i<1000;i++) {
      awake.screenOff(); check(awake.screenOn()); check(!awake.screenOn());
    }
    System.out.println("PASS: initial awake/asleep, duplicate events, 1000 sleep/wake cycles");
  }
}
