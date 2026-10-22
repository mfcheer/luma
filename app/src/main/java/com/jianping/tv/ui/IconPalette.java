package com.jianping.tv.ui;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small one-time icon sample; excludes transparent and near-neutral pixels. */
public final class IconPalette {
  private IconPalette() {}

  public static int accent(Drawable drawable) {
    Bitmap image = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
    Rect old = new Rect(drawable.getBounds());
    try {
      drawable.setBounds(0, 0, 32, 32);
      drawable.draw(new Canvas(image));
      int[] pixels = new int[32 * 32];
      image.getPixels(pixels, 0, 32, 0, 0, 32, 32);
      float[] hsv = new float[3];
      float[] weights = new float[12], hs = new float[12], ss = new float[12];
      for (int y = 0; y < 32; y++)
        for (int x = 0; x < 32; x++) {
          int pixel = pixels[y * 32 + x];
          if (Color.alpha(pixel) < 150) continue;
          Color.colorToHSV(pixel, hsv);
          if (hsv[1] < .28f || hsv[2] < .25f) continue;
          int bucket = Math.min(11, (int) (hsv[0] / 30));
          float weight = hsv[1] * hsv[1] * hsv[2];
          weights[bucket] += weight;
          hs[bucket] += hsv[0] * weight;
          ss[bucket] += hsv[1] * weight;
        }
      int best = 0;
      for (int i = 1; i < 12; i++) if (weights[i] > weights[best]) best = i;
      if (weights[best] < 1) return Color.rgb(112, 146, 204);
      return Color.HSVToColor(
          new float[] {
            hs[best] / weights[best], Math.min(.65f, ss[best] / weights[best] * .8f), .90f
          });
    } finally {
      drawable.setBounds(old);
      image.recycle();
    }
  }
}
