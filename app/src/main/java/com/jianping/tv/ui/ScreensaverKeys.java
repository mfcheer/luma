package com.jianping.tv.ui;

import android.view.KeyEvent;
import java.util.HashSet;
import java.util.Set;

/** Keeps the complete wake-up press away from the desktop, even after it regains focus. */
final class ScreensaverKeys {
  private final Set<Long> held = new HashSet<>();
  private final Set<Long> wakePresses = new HashSet<>();

  boolean hasHeldKeys() {
    return !held.isEmpty();
  }

  void clearHeldKeys() {
    held.clear();
    // The wake press can still end after a window focus change.
  }

  boolean handle(KeyEvent event, boolean screensaverVisible) {
    int code = event.getKeyCode();
    long key = ((long) event.getDeviceId() << 32) | (code & 0xffffffffL);
    boolean navigationKey = isNavigationKey(code);
    if (!navigationKey && event.isSystem()) return false;
    if (event.getAction() == KeyEvent.ACTION_UP) {
      held.remove(key);
      return wakePresses.remove(key);
    }
    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;

    if (navigationKey || screensaverVisible) held.add(key);
    if (event.getRepeatCount() == 0) wakePresses.remove(key);
    if (screensaverVisible) wakePresses.add(key);
    return wakePresses.contains(key);
  }

  private static boolean isNavigationKey(int code) {
    switch (code) {
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_DPAD_DOWN:
      case KeyEvent.KEYCODE_DPAD_LEFT:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
      case KeyEvent.KEYCODE_DPAD_CENTER:
      case KeyEvent.KEYCODE_ENTER:
      case KeyEvent.KEYCODE_NUMPAD_ENTER:
      case KeyEvent.KEYCODE_BACK:
      case KeyEvent.KEYCODE_MENU:
        return true;
      default:
        return false;
    }
  }
}
