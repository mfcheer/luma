package com.jianping.tv.platform;

/** One return per observed sleep/wake cycle; restarting while awake must not steal focus. */
public final class WakeCycle {
  private boolean sleeping;

  public WakeCycle(boolean interactive) {
    sleeping = !interactive;
  }

  public void screenOff() {
    sleeping = true;
  }

  public boolean screenOn() {
    boolean returnHome = sleeping;
    sleeping = false;
    return returnHome;
  }
}
