package com.jianping.tv.ui;

/** Exact separable radius-two box blur; running sums avoid ten clamped lookups per pixel. */
public final class ArgbBlur {
  private ArgbBlur() {}

  public static void apply(int[] pixels, int width, int height) {
    if (width <= 0 || height <= 0 || (long) width * height != pixels.length)
      throw new IllegalArgumentException("Invalid image size");
    int[] temp = new int[pixels.length];
    pass(pixels, temp, width, height, true);
    pass(temp, pixels, width, height, false);
  }

  private static void pass(int[] from, int[] to, int width, int height, boolean horizontal) {
    int length = horizontal ? width : height, lines = horizontal ? height : width;
    int step = horizontal ? 1 : width;
    for (int line = 0; line < lines; line++) {
      int offset = horizontal ? line * width : line;
      int a = 0, r = 0, g = 0, b = 0;
      for (int d = -2; d <= 2; d++) {
        int color = from[offset + Math.max(0, Math.min(length - 1, d)) * step];
        a += color >>> 24;
        r += (color >>> 16) & 255;
        g += (color >>> 8) & 255;
        b += color & 255;
      }
      for (int x = 0; x < length; x++) {
        to[offset + x * step] = ((a / 5) << 24) | ((r / 5) << 16) | ((g / 5) << 8) | (b / 5);
        if (x + 1 == length) continue;
        int leaving = from[offset + Math.max(0, x - 2) * step];
        int entering = from[offset + Math.min(length - 1, x + 3) * step];
        a += (entering >>> 24) - (leaving >>> 24);
        r += ((entering >>> 16) & 255) - ((leaving >>> 16) & 255);
        g += ((entering >>> 8) & 255) - ((leaving >>> 8) & 255);
        b += (entering & 255) - (leaving & 255);
      }
    }
  }
}
