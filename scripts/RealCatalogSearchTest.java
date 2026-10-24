import com.jianping.tv.data.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline replay of a captured device catalog. Does not connect to or modify a device. */
public final class RealCatalogSearchTest {
  public static void main(String[] args) throws Exception {
    AppAliases aliases = AppAliases.read(new FileInputStream(args[0]));
    List<AppSearch.Item> items = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(args[1]), StandardCharsets.UTF_8))) {
      String row;
      while ((row = reader.readLine()) != null) {
        String[] fields = row.split("\t", -1);
        items.add(new AppSearch.Item(fields[0], fields[2], fields[1], aliases));
      }
    }
    AppSearch search = new AppSearch(items);
    String[][] cases = {
      {"B站", "com.xiaodianshi.tv.yst"}, {"bzhan", "com.xiaodianshi.tv.yst"},
      {"油管", "com.google.android.youtube.tv"}, {"yt", "com.google.android.youtube.tv"},
      {"优酷", "com.cibn.tv"}, {"youku", "com.cibn.tv"},
      {"腾讯视频", "com.ktcp.video"}, {"txsp", "com.ktcp.video"},
      {"爱奇艺", "com.gitvdemo.video"}, {"aqy", "com.gitvdemo.video"},
      {"网易云", "com.netease.cloudmusic.tv"}, {"wyy", "com.netease.cloudmusic.tv"},
      {"qqyy", "com.tencent.qqmusictv"}, {"qqyinyue", "com.tencent.qqmusictv"},
      {"mgtv", "com.hunantv.license"}, {"央视频", "com.newtv.cboxtv"},
      {"小爱", "com.xiaomi.voicecontrol"}, {"pinyinleyuan", "com.ihuman.pinyin.ott.vip"},
      {"stn", "com.teamsmart.videomanager.tv"}, {"jkj", "com.zentraedi.zspace.tv"}
    };
    for (String[] sample : cases) {
      List<String> found = search.find(sample[0]);
      if (found.isEmpty() || !found.get(0).startsWith(sample[1] + "/"))
        throw new AssertionError("Wrong first result for " + sample[0]);
      if (new HashSet<>(found).size() != found.size()) throw new AssertionError("Duplicate result");
    }
    if (!search.find("Netflix").isEmpty())
      throw new AssertionError("Uninstalled dictionary entries leaked");
    System.out.println(
        "PASS: "
            + cases.length
            + " top-result checks against "
            + items.size()
            + " actual TV launch entries; deduplicated, no uninstalled app suggestions. Offline JVM"
            + " replay only.");
  }
}
