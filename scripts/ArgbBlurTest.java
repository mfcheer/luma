import com.jianping.tv.ui.ArgbBlur;
import java.util.*;

public final class ArgbBlurTest {
  private static int[] reference(int[] source, int w, int h) {
    int[] temp = new int[source.length], result = new int[source.length];
    for (int direction = 0; direction < 2; direction++) {
      int[] from = direction == 0 ? source : temp, to = direction == 0 ? temp : result;
      for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
        int a = 0, r = 0, g = 0, b = 0;
        for (int d = -2; d <= 2; d++) {
          int xx = direction == 0 ? Math.max(0, Math.min(w - 1, x + d)) : x;
          int yy = direction == 0 ? y : Math.max(0, Math.min(h - 1, y + d));
          int color = from[yy * w + xx];
          a += color >>> 24; r += (color >>> 16) & 255; g += (color >>> 8) & 255; b += color & 255;
        }
        to[y * w + x] = ((a / 5) << 24) | ((r / 5) << 16) | ((g / 5) << 8) | (b / 5);
      }
    }
    return result;
  }
  public static void main(String[] args) {
    Random random = new Random(813);
    for (int[] size : new int[][] {{1,1},{1,19},{31,1},{2,2},{3,7},{128,83}}) {
      int w=size[0], h=size[1];
      for (int i=0;i<12;i++) {
        int[] image = new int[w*h];
        for (int p=0;p<image.length;p++) image[p]=random.nextInt();
        int[] expected=reference(image,w,h);
        ArgbBlur.apply(image,w,h);
        if (!Arrays.equals(image,expected)) throw new AssertionError(w+"x"+h);
      }
    }
    try { ArgbBlur.apply(new int[2],1,1); throw new AssertionError("Expected dimensions check"); }
    catch (IllegalArgumentException expected) { }
    System.out.println("PASS: running-sum blur exactly matches radius-two reference for random ARGB, transparent edges, 1px axes and 128x83 previews; invalid dimensions rejected");
  }
}
