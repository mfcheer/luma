package com.jianping.tv.ui;

import android.content.res.Resources;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Adapts real installed artwork without stretching, cropping its mark or substituting a logo. */
public final class IconArtwork {
  private static final int SIZE = 256;

  private IconArtwork() {}

  /** Prefer authentic TV banners. Transparent fallback marks share one wide surface. */
  public static Drawable landscape(
      Resources resources, Drawable source, Drawable banner, int accent) {
    final int width = 384, height = 250;
    Drawable input = banner != null ? banner : source;
    float ratio =
        input.getIntrinsicWidth() > 0 && input.getIntrinsicHeight() > 0
            ? (float) input.getIntrinsicWidth() / input.getIntrinsicHeight()
            : 1;
    int bh = 256, bw = banner != null ? Math.max(1, Math.round(bh * Math.min(3, ratio))) : 256;
    Bitmap image = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
    Rect old = new Rect(input.getBounds());
    int iw = banner != null ? bw : Math.round(256 * Math.min(1, ratio));
    int ih = banner != null ? bh : Math.round(256 * Math.min(1, 1 / ratio));
    try {
      input.setBounds((bw - iw) / 2, (bh - ih) / 2, (bw + iw) / 2, (bh + ih) / 2);
      input.draw(new Canvas(image));
    } finally {
      input.setBounds(old);
    }
    Rect visible = new Rect(bw, bh, 0, 0);
    int solid = 0, colorful = 0, sampled = 0;
    float luminance = 0;
    int[] pixels = new int[bw * bh];
    image.getPixels(pixels, 0, bw, 0, 0, bw, bh);
    float[] hsv = new float[3];
    for (int y = 0; y < bh; y++)
      for (int x = 0; x < bw; x++) {
        int color = pixels[y * bw + x];
        if (Color.alpha(color) < 48) continue;
        visible.left = Math.min(visible.left, x);
        visible.right = Math.max(visible.right, x + 1);
        visible.top = Math.min(visible.top, y);
        visible.bottom = Math.max(visible.bottom, y + 1);
        solid++;
        // Bounds retain full pixel precision; surface classification only needs sparse samples.
        if ((x & 3) == 0 && (y & 3) == 0) {
          sampled++;
          Color.colorToHSV(color, hsv);
          if (hsv[1] > .45f) colorful++;
          luminance +=
              (.2126f * Color.red(color) + .7152f * Color.green(color) + .0722f * Color.blue(color))
                  / 255;
        }
      }
    if (visible.isEmpty()) {
      image.recycle();
      return source;
    }
    float aspect = (float) visible.width() / visible.height();
    boolean full =
        banner != null
            || (aspect > .88f
                && aspect < 1.14f
                && (float) solid / (visible.width() * visible.height()) > .84f);
    boolean light = sampled > 0 && colorful > sampled * .4f && luminance / sampled < .62f;
    int top = light ? tint(accent, .025f, 0xFFFAFCFF) : tint(accent, .22f, 0xFF273343);
    int bottom = light ? tint(accent, .055f, 0xFFE6EAF1) : tint(accent, .10f, 0xFF171E2B);
    if (full) {
      int t = image.getPixel(bw / 2, Math.min(bh - 1, visible.top + 8));
      int b = image.getPixel(bw / 2, Math.max(0, visible.bottom - 9));
      if (Color.alpha(t) > 150) top = t;
      if (Color.alpha(b) > 150) bottom = b;
    }
    Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(result);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    Path clip = new Path();
    clip.addRoundRect(new RectF(0, 0, width, height), 39, 39, Path.Direction.CW);
    canvas.clipPath(clip);
    paint.setShader(
        new LinearGradient(0, 0, 0, height, new int[] {top, bottom}, null, Shader.TileMode.CLAMP));
    canvas.drawRect(0, 0, width, height, paint);
    paint.setShader(null);
    float fit =
        full
            ? Math.min((float) width / visible.width(), (float) height / visible.height())
            : Math.min(width * .72f / visible.width(), height * .72f / visible.height());
    float w = visible.width() * fit, h = visible.height() * fit;
    canvas.drawBitmap(
        image,
        visible,
        new RectF((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2),
        paint);
    image.recycle();
    return new BitmapDrawable(resources, result);
  }

  public static Drawable create(Resources resources, Drawable source, int accent) {
    Bitmap sample = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
    Canvas scan = new Canvas(sample);
    Rect original = new Rect(source.getBounds());
    float ratio =
        source.getIntrinsicWidth() > 0 && source.getIntrinsicHeight() > 0
            ? (float) source.getIntrinsicWidth() / source.getIntrinsicHeight()
            : 1;
    int width = Math.round(SIZE * Math.min(1, ratio));
    int height = Math.round(SIZE * Math.min(1, 1 / ratio));
    try {
      source.setBounds(
          (SIZE - width) / 2, (SIZE - height) / 2, (SIZE + width) / 2, (SIZE + height) / 2);
      source.draw(scan);
    } finally {
      source.setBounds(original);
    }
    Rect bounds = new Rect(SIZE, SIZE, 0, 0);
    int solid = 0;
    int colorful = 0;
    float luminance = 0;
    float[] hsv = new float[3];
    int[] pixels = new int[SIZE * SIZE];
    sample.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        if (Color.alpha(pixels[y * SIZE + x]) < 48) continue;
        bounds.left = Math.min(bounds.left, x);
        bounds.top = Math.min(bounds.top, y);
        bounds.right = Math.max(bounds.right, x + 1);
        bounds.bottom = Math.max(bounds.bottom, y + 1);
        solid++;
        int pixel = pixels[y * SIZE + x];
        Color.colorToHSV(pixel, hsv);
        if (hsv[1] > .45f) colorful++;
        luminance +=
            (.2126f * Color.red(pixel) + .7152f * Color.green(pixel) + .0722f * Color.blue(pixel))
                / 255;
      }
    }
    if (bounds.isEmpty()) {
      sample.recycle();
      return source;
    }
    float aspect = (float) bounds.width() / bounds.height();
    float coverage = (float) solid / (bounds.width() * bounds.height());
    boolean fullTile = aspect > .88f && aspect < 1.14f && coverage > .84f;
    boolean lightSurface = colorful > solid * .4f && luminance / solid < .62f;
    Bitmap result = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(result);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    Path outline = new Path();
    outline.addRoundRect(
        new RectF(0, 0, SIZE, SIZE), SIZE * .225f, SIZE * .225f, Path.Direction.CW);
    canvas.clipPath(outline);
    if (!fullTile) {
      // Transparent and landscape marks get a quiet, brand-tinted surface, not an empty outline.
      paint.setShader(
          new LinearGradient(
              0,
              0,
              SIZE,
              SIZE,
              new int[] {
                lightSurface ? tint(accent, .025f, 0xFFFAFCFF) : tint(accent, .22f, 0xFF273343),
                lightSurface ? tint(accent, .055f, 0xFFE6EAF1) : tint(accent, .10f, 0xFF171E2B)
              },
              null,
              Shader.TileMode.CLAMP));
      canvas.drawRect(0, 0, SIZE, SIZE, paint);
      paint.setShader(null);
    }
    RectF target = new RectF(0, 0, SIZE, SIZE);
    if (!fullTile) {
      float fit = Math.min(SIZE * .78f / bounds.width(), SIZE * .68f / bounds.height());
      float w = bounds.width() * fit, h = bounds.height() * fit;
      target.set((SIZE - w) / 2, (SIZE - h) / 2, (SIZE + w) / 2, (SIZE + h) / 2);
    }
    canvas.drawBitmap(sample, bounds, target, paint);
    sample.recycle();
    return new BitmapDrawable(resources, result);
  }

  /** Low-resolution separable box blur, prepared once off the UI thread for edge previews. */
  public static Drawable preview(Resources resources, Drawable art) {
    int w = 128, h = 83;
    Bitmap image = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Rect old = new Rect(art.getBounds());
    try {
      art.setBounds(0, 0, w, h);
      art.draw(new Canvas(image));
    } finally {
      art.setBounds(old);
    }
    int[] pixels = new int[w * h];
    image.getPixels(pixels, 0, w, 0, 0, w, h);
    ArgbBlur.apply(pixels, w, h);
    image.setPixels(pixels, 0, w, 0, 0, w, h);
    return new BitmapDrawable(resources, image);
  }

  /** Cheap normalized fallback retains the real mark without expensive surface analysis. */
  public static Drawable fallback(Resources resources, Drawable source) {
    Bitmap image = Bitmap.createBitmap(384, 250, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(image);
    canvas.drawColor(0xFF263244);
    int width = Math.max(1, source.getIntrinsicWidth()),
        height = Math.max(1, source.getIntrinsicHeight());
    float scale = Math.min(276f / width, 180f / height);
    int w = Math.round(width * scale), h = Math.round(height * scale);
    Rect old = new Rect(source.getBounds());
    try {
      source.setBounds((384 - w) / 2, (250 - h) / 2, (384 + w) / 2, (250 + h) / 2);
      source.draw(canvas);
    } finally {
      source.setBounds(old);
    }
    return new BitmapDrawable(resources, image);
  }

  private static int tint(int accent, float amount, int base) {
    return Color.rgb(
        Math.round(Color.red(base) * (1 - amount) + Color.red(accent) * amount),
        Math.round(Color.green(base) * (1 - amount) + Color.green(accent) * amount),
        Math.round(Color.blue(base) * (1 - amount) + Color.blue(accent) * amount));
  }
}
