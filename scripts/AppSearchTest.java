import com.jianping.tv.data.AppSearch;
import java.util.*;

public final class AppSearchTest {
  private static void expect(AppSearch search, String query, String... ids) {
    List<String> result = search.find(query);
    if (!result.equals(Arrays.asList(ids))) throw new AssertionError(query + ": " + result);
  }

  public static void main(String[] args) {
    List<AppSearch.Item> items =
        Arrays.asList(
            new AppSearch.Item("j", "极空间"), new AppSearch.Item("m", "芒果TV"),
            new AppSearch.Item("d2", "当贝市场"), new AppSearch.Item("d1", "当贝播放器"),
            new AppSearch.Item("y", "YouTube"), new AppSearch.Item("x", "小米视频"),
            new AppSearch.Item("d0", "当贝"), new AppSearch.Item("other", "我的当贝"));
    AppSearch search = new AppSearch(items);
    expect(search, "jkj", "j");
    expect(search, "极空", "j");
    expect(search, "JIKONGJIAN", "j");
    expect(search, "mgtv", "m");
    expect(search, "tv", "m");
    expect(search, "Tube", "y");
    expect(search, " ＹｏｕＴｕｂｅ ", "y");
    expect(search, "当贝", "d0", "d2", "d1", "other");
    // Full initials now outrank initial-prefix matches; equal prefix matches retain home order.
    expect(search, "db", "d0", "d2", "d1", "other");
    expect(search, "xiao mi", "x");
    expect(search, "播放器", "d1");
    expect(search, "");
    expect(search, "  ");
    expect(search, "。?!");
    expect(search, "not-installed");
    expect(search, "😀");
    // Full initials now outrank initial-prefix matches; equal prefix matches retain home order.
    expect(search, "db", "d0", "d2", "d1", "other");
    if (!items.get(0).id.equals("j") || !items.get(2).id.equals("d2"))
      throw new AssertionError("Searching must never mutate home order");
    System.out.println(
        "PASS: Chinese/English/pinyin/initials, mixed TV names, NFKC/case/whitespace,"
            + " ranking/stable ties, empty/no match, repeat and immutable home order");
  }
}
