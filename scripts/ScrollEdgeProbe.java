package com.jianping.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Emulator-only instrumentation. Never packaged in Still or installed on a user's TV. */
public final class ScrollEdgeProbe extends Instrumentation {
  private String label;
  private ScrollView wall;

  @Override
  public void onCreate(Bundle args) {
    label = args.getString("label", "probe");
    super.onCreate(args);
    start();
  }

  @Override
  public void onStart() {
    Bundle result = new Bundle();
    try {
      Activity activity = startActivitySync(new Intent().setClassName(
          "com.jianping.tv", "com.jianping.tv.ui.MainActivity")
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
      Field home = activity.getClass().getDeclaredField("homeWall");
      home.setAccessible(true);
      for (int i = 0; i < 40; i++) {
        runOnMainSync(() -> {
          try { wall = (ScrollView) home.get(activity); }
          catch (Exception e) { throw new RuntimeException(e); }
        });
        if (wall != null && ((ViewGroup) wall.getChildAt(0)).getChildCount() >= 20) break;
        SystemClock.sleep(100);
      }
      if (wall == null) throw new AssertionError("No application wall");
      ViewGroup cards = (ViewGroup) wall.getChildAt(0);
      if (cards.getChildCount() < 20) throw new AssertionError("Requires emulator fixture set");
      Field pitchField = wall.getClass().getDeclaredField("rowHeight");
      pitchField.setAccessible(true);
      int pitch = pitchField.getInt(wall);
      Method strength = wall.getClass().getDeclaredMethod("getTopFadingEdgeStrength");
      strength.setAccessible(true);
      File out = new File(getTargetContext().getExternalFilesDir(null), "scroll-probe/" + label);
      out.mkdirs();
      StringBuilder values = new StringBuilder("offset,strength\n");
      for (int row = 1; row <= 2; row++) {
        final View selected = cards.getChildAt((row + 1) * 5);
        runOnMainSync(selected::requestFocus);
        SystemClock.sleep(800);
        for (int delta : new int[] {-2, -1, 0, 1, 2}) {
          int y = row * pitch + delta;
          runOnMainSync(() -> wall.scrollTo(0, y));
          SystemClock.sleep(120);
          waitForIdleSync();
          values.append(y).append(',').append(strength.invoke(wall)).append('\n');
          Bitmap screenshot = getUiAutomation().takeScreenshot();
          try (FileOutputStream stream = new FileOutputStream(new File(out, y + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream);
          }
          screenshot.recycle();
        }
      }
      try (FileOutputStream stream = new FileOutputStream(new File(out, "samples.csv"))) {
        stream.write(values.toString().getBytes("UTF-8"));
      }
      result.putString("result", "PASS: captured exact row-boundary pixels and fading strengths");
      result.putString("directory", out.toString());
      finish(Activity.RESULT_OK, result);
    } catch (Throwable failure) {
      result.putString("failure", android.util.Log.getStackTraceString(failure));
      finish(Activity.RESULT_CANCELED, result);
    }
  }
}
