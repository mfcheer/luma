import com.jianping.tv.data.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class SearchAliasesTest {
  private static int checks;
  private static AppAliases aliases;

  private static AppSearch.Item app(String id, String name, String pkg) {
    return new AppSearch.Item(id, name, pkg, aliases);
  }

  private static void expect(AppSearch search, String query, String... ids) {
    checks++;
    List<String> actual = search.find(query);
    if (!actual.equals(Arrays.asList(ids)))
      throw new AssertionError(query + " expected=" + Arrays.asList(ids) + " actual=" + actual);
  }

  private static void check(boolean ok, String message) {
    checks++;
    if (!ok) throw new AssertionError(message);
  }

  private static void bad(String text) throws Exception {
    checks++;
    try {
      AppAliases.read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
      throw new AssertionError("Malformed dictionary accepted");
    } catch (IOException expected) {
    }
  }

  public static void main(String[] args) throws Exception {
    aliases = AppAliases.read(new FileInputStream(args[0]));
    check(aliases.packageCount() == 61, "Reviewed package coverage");
    AppSearch.Item music = app("music", "网易云音乐", "com.netease.cloudmusic.tv");
    AppSearch.Item game = app("game", "网易云游戏", "com.netease.android.cloudgame.tv");
    AppSearch.Item youtube = app("youtube", "YouTube", "com.google.android.youtube.tv");
    AppSearch.Item smart = app("smart", "SmartTube Next", "com.teamsmart.videomanager.tv");
    AppSearch.Item bili = app("bili", "云视听小电视", "com.xiaodianshi.tv.yst");
    AppSearch.Item unknown = app("unknown", "云视听小电视", "example.unverified");
    AppSearch.Item qq = app("qq", "QQ音乐", "com.tencent.qqmusictv");
    AppSearch.Item education = app("education", "拼音乐园", "com.ihuman.pinyin.ott.vip");
    AppSearch.Item mango = app("mango", "芒果TV", "com.hunantv.license");
    AppSearch.Item migu = app("migu", "咪视界", "cn.miguvideo.migutv");
    AppSearch.Item tencent = app("tencent", "云视听极光", "com.ktcp.video");
    AppSearch.Item youku = app("youku", "CIBN酷喵", "com.cibn.tv");
    AppSearch.Item iqiyi = app("iqiyi", "银河奇异果", "com.gitvdemo.video");
    AppSearch.Item intl = app("intl", "iQIYI", "com.iqiyi.i18n.tv");
    Map<String, Double> usage = new HashMap<>();
    usage.put(game.packageName, 999999d);
    usage.put(migu.packageName, 999999d);
    List<AppSearch.Item> home =
        Arrays.asList(
            game, migu, unknown, smart, mango, youtube, music, bili, qq, education, tencent, youku,
            iqiyi, intl, bili);
    AppSearch search = new AppSearch(home, usage);
    expect(search, "网易云", "music", "game");
    expect(search, "wyy", "music", "game");
    expect(search, "wyyyy", "music");
    expect(search, "wang yi yun yin yue", "music");
    expect(search, "qqyy", "qq");
    expect(search, "qqyinyue", "qq");
    expect(search, "qqyinle");
    expect(search, "pinyinleyuan", "education");
    expect(search, "pinyinyueyuan");
    expect(search, "B站", "bili");
    expect(search, "bz", "bili");
    expect(search, "bzhan", "bili");
    expect(search, "blbl", "bili");
    expect(search, "ＢＩＬＩＢＩＬＩ", "bili");
    expect(search, "云视听小电视", "unknown", "bili");
    expect(search, "油管", "youtube");
    expect(search, "youguan", "youtube");
    expect(search, "ＹＴ", "youtube");
    expect(search, "stn", "smart");
    expect(search, "mgtv", "mango", "migu");
    expect(search, "腾讯视频", "tencent");
    expect(search, "txsp", "tencent");
    expect(search, "优酷", "youku");
    expect(search, "youku", "youku");
    expect(search, "yk", "youku");
    expect(search, "爱奇艺", "iqiyi", "intl");
    expect(search, "iqiyi", "intl", "iqiyi");
    expect(search, "iqiyi国际版", "intl");
    expect(search, "bilibili油管");
    expect(search, "YouTubebzhan");
    expect(search, "comgoogleandroidyoutubetv");
    expect(search, "", new String[0]);
    check(home.get(0) == game && home.size() == 15, "No mutation of home order or caller list");

    AppSearch.Item a = app("a", "音乐甲", "example.a"), b = app("b", "音乐乙", "example.b");
    Map<String, Double> weights = new HashMap<>();
    weights.put(b.packageName, 2d);
    AppSearch frozen = new AppSearch(Arrays.asList(a, b), weights);
    expect(frozen, "音乐", "b", "a");
    weights.put(a.packageName, 999d);
    expect(frozen, "音乐", "b", "a");
    expect(new AppSearch(Arrays.asList(a, b), weights), "音乐", "a", "b");
    expect(new AppSearch(Arrays.asList(a, b)), "音乐", "a", "b");
    weights.put(a.packageName, Double.NaN);
    weights.put(b.packageName, Double.POSITIVE_INFINITY);
    expect(new AppSearch(Arrays.asList(a, b), weights), "音乐", "a", "b");
    // A very popular alias still cannot outrank an exact original name.
    AppSearch.Item exact = app("exact", "网易云", "example.exact");
    weights.clear();
    weights.put(music.packageName, 999999d);
    expect(new AppSearch(Arrays.asList(music, exact), weights), "网易云", "exact", "music");
    expect(
        new AppSearch(Arrays.asList(app("new-label", "不熟悉的新名字", music.packageName))),
        "网易云",
        "new-label");
    expect(new AppSearch(Arrays.asList(app("case", "QQ音乐", "com.tencent.QQMUSICTV"))), "qqyy");
    expect(new AppSearch(Arrays.asList(app("unlisted", "新应用", "example.new"))), "xyy", "unlisted");
    expect(
        new AppSearch(
            Arrays.asList(app("same1", "重复名称", "example.a"), app("same2", "重复名称", "example.b"))),
        "重复名称",
        "same1",
        "same2");
    check(
        aliases.terms("com.google.android.youtube.tv.fake").isEmpty(), "No package-prefix lookup");
    check(
        aliases.terms("com.hpplay.happycast").isEmpty(),
        "Pending bindings must not leak into release asset");
    for (String pkg :
        Arrays.asList("org.smarttube.stable", "org.smarttube.beta", "app.smarttube.fdroid"))
      expect(new AppSearch(Arrays.asList(app(pkg, "SmartTube", pkg))), "stn", pkg);

    AppSearch.ItemCache cache = new AppSearch.ItemCache();
    AppSearch.Item first = cache.get("id", "网易云音乐", music.packageName, "v1:zh", aliases);
    check(
        cache.get("id", "网易云音乐", music.packageName, "v1:zh", aliases) == first,
        "Prepared terms reused");
    check(
        cache.get("id", "网易云音乐", music.packageName, "v2:zh", aliases) != first,
        "Version invalidates");
    AppSearch.Item changed = cache.get("id", "新名字", music.packageName, "v2:zh", aliases);
    check(changed.label.equals("新名字"), "Label changes invalidate");
    check(
        cache.get("id", "新名字", music.packageName, "v2:en", aliases) != changed,
        "Locale invalidates");
    AppSearch.Item without = cache.get("id", "网易云音乐", music.packageName, "v1:zh", AppAliases.EMPTY);
    check(
        cache.get("id", "网易云音乐", music.packageName, "v1:zh", aliases) != without,
        "Dictionary invalidates");
    first = cache.get("id", "网易云音乐", music.packageName, "v1:zh", aliases);
    cache.retain(Collections.emptyList());
    check(
        cache.get("id", "网易云音乐", music.packageName, "v1:zh", aliases) != first,
        "Removed apps evicted");
    bad("");
    bad("#still-search\t9\tx\n");
    bad("#still-search\t1\tx\nexample.app\t2\tpartial\talias\talias\talias\tpending\n");
    bad("#still-search\t1\tx\nexample.app\t9\tpartial\talias\talias\talias\treviewed\n");
    bad("#still-search\t1\tx\nexample.app\t2\tpartial\ta\ta\ta\treviewed\n");
    bad(
        "#still-search\t1\tx\n"
            + "example.app\t2\tpartial\talias\talias\talias\treviewed\n"
            + "example.app\t2\tpartial\talias\talias\talias\treviewed\n");
    expect(
        new AppSearch(Arrays.asList(new AppSearch.Item("fallback", "网易云音乐"))), "网易云", "fallback");

    // Exercise every published term through the real matcher, independent of its runtime label.
    Set<String> packages = new LinkedHashSet<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(new FileInputStream(args[0]), StandardCharsets.UTF_8))) {
      String row;
      while ((row = reader.readLine()) != null)
        if (!row.startsWith("#") && !row.isEmpty()) packages.add(row.split("\t")[0]);
    }
    for (String pkg : packages) {
      AppSearch isolated = new AppSearch(Arrays.asList(app(pkg, "原始陌生标签", pkg)));
      for (AppAliases.Term term : aliases.terms(pkg)) {
        expect(isolated, term.text, pkg);
        if (!term.exactOnly) {
          expect(isolated, term.pinyin, pkg);
          expect(isolated, term.initials, pkg);
        }
      }
    }

    long now = 1_700_000_000_000L, day = 24 * 60 * 60 * 1000L;
    check(Math.abs(UsageHistory.weight(10, now - 14 * day, now) - 5) < 1e-9, "14-day half-life");
    check(UsageHistory.weight(10, now + day, now) == 10, "Clock rollback never amplifies");
    check(
        UsageHistory.afterLaunch(10, now - 14 * day, now) == 6,
        "One accepted launch adds exactly one");
    check(UsageHistory.weight(Double.NaN, now, now) == 0, "NaN ignored");
    check(UsageHistory.weight(Double.POSITIVE_INFINITY, now, now) == 0, "Infinity ignored");
    check(UsageHistory.weight(-10, now, now) == 0, "Negative ignored");
    check(UsageHistory.weight(10, 0, now) == 0, "Missing timestamp ignored");
    check(UsageHistory.weight(10, 1, Long.MAX_VALUE) == 0, "Far-future clock safe");
    check(UsageHistory.weight(10, now, 0) == 0, "Invalid current clock safe");
    check(UsageHistory.afterLaunch(0, 0, now) == 1, "Cold history starts at one");
    System.out.println(
        "PASS: "
            + checks
            + " checks: reviewed asset, identity/alias boundaries, ranking/dedupe,"
            + " whole-name pinyin, frozen usage, cache invalidation, corrupt data and time decay");
  }
}
