import com.jianping.tv.data.AppOrder;
import java.util.*;

/** Standalone domain checks; no Android runtime or additional dependencies required. */
public final class AppOrderTest {
  private static void expect(List<String> previous, List<String> installed, String... expected) {
    List<String> result = AppOrder.reconcile(previous, installed);
    if (!result.equals(Arrays.asList(expected))) throw new AssertionError(result);
    if (!AppOrder.reconcile(result, installed).equals(result))
      throw new AssertionError("Repeated refresh must be stable");
  }

  public static void main(String[] args) {
    List<String> installed = Arrays.asList("a/.Tv", "b/.Tv", "c/.Tv");
    expect(Collections.emptyList(), installed, "a/.Tv", "b/.Tv", "c/.Tv");
    expect(Arrays.asList("c/.Tv", "a/.Tv"), installed, "c/.Tv", "a/.Tv", "b/.Tv");
    expect(Arrays.asList("gone/.Tv", "b/.Tv", "b/.Tv"), installed, "b/.Tv", "a/.Tv", "c/.Tv");
    expect(Arrays.asList("b/.Old", "a/.Tv"), installed, "b/.Tv", "a/.Tv", "c/.Tv");
    expect(Arrays.asList("b/.Old"), Arrays.asList("a/.Tv", "b/.Tv", "b/.Phone"), "b/.Tv", "a/.Tv");
    expect(Arrays.asList(null, "", "c/.Tv"), installed, "c/.Tv", "a/.Tv", "b/.Tv");
    expect(installed, Collections.emptyList());
    List<String> original = Arrays.asList("a/.Tv", "b/.Tv", "new/.Tv");
    List<String> promoted = AppOrder.moveToFront(original, "b/.Tv");
    if (!promoted.equals(Arrays.asList("b/.Tv", "a/.Tv", "new/.Tv"))
        || !original.equals(Arrays.asList("a/.Tv", "b/.Tv", "new/.Tv"))
        || !AppOrder.moveToFront(promoted, "b/.Tv").equals(promoted)
        || !AppOrder.moveToFront(original, "gone/.Tv").equals(original))
      throw new AssertionError("Promotion must preserve other entries/input and be stable");
    System.out.println(
        "PASS: fresh install, legacy prefix, append/remove/deduplicate, activity rename, TV entry"
            + " preference, invalid IDs, empty list, refresh stability and immutable/stable"
            + " promotion");
  }
}
