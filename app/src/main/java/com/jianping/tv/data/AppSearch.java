package com.jianping.tv.data;

import java.text.Normalizer;
import java.util.*;
import me.majiajie.tinypinyin.Pinyin;

/** One result per launch entry: best match, frozen usage, then the user's home order. */
public final class AppSearch {
  private static final int NONE = Integer.MAX_VALUE;

  private static final class Term {
    final String text, pinyin, initials;
    final int source;
    final boolean exactOnly;

    Term(String text, String pinyin, String initials, int source, boolean exactOnly) {
      this.text = text;
      this.pinyin = pinyin;
      this.initials = initials;
      this.source = source;
      this.exactOnly = exactOnly;
    }

    int score(String query) {
      // One-character queries retain original-label behavior without expanding all aliases.
      if (source != 0 && query.length() < 2) return NONE;
      if (text.equals(query)) return source * 10;
      if (exactOnly) return NONE;
      if (pinyin.equals(query)) return 100 + source * 10 + 1;
      if (initials.equals(query)) return 100 + source * 10 + 2;
      if (text.startsWith(query)) return 200 + source * 10;
      if (text.contains(query)) return 300 + source * 10;
      if (pinyin.startsWith(query)) return 400 + source * 10 + 1;
      if (initials.startsWith(query)) return 400 + source * 10 + 2;
      if (source == 0 && pinyin.contains(query)) return 501;
      return source == 0 && initials.contains(query) ? 502 : NONE;
    }
  }

  public static final class Item {
    public final String id, label, packageName, customName;
    private final List<Term> terms = new ArrayList<>();

    public Item(String id, String label) {
      this(id, label, "", AppAliases.EMPTY);
    }

    public Item(String id, String label, String packageName, AppAliases dictionary) {
      this(id, label, packageName, dictionary, "");
    }

    public Item(
        String id, String label, String packageName, AppAliases dictionary, String customName) {
      this.id = id;
      this.label = label;
      this.packageName = packageName;
      this.customName = customName;
      String name = normalize(label);
      List<AppAliases.Term> aliases = dictionary.terms(packageName);
      terms.add(nameTerm(name, aliases));
      String custom = normalize(customName);
      if (!custom.isEmpty() && !custom.equals(name)) terms.add(nameTerm(custom, aliases));
      for (AppAliases.Term term : aliases)
        if (!term.text.equals(name))
          terms.add(new Term(term.text, term.pinyin, term.initials, term.source, term.exactOnly));
    }

    private static Term nameTerm(String name, List<AppAliases.Term> aliases) {
      AppAliases.Term reviewed = null;
      for (AppAliases.Term term : aliases) if (term.text.equals(name)) reviewed = term;
      // Whole-name corrections only: never turn 拼音/乐园 into 拼/音乐/园.
      if (reviewed != null) return new Term(name, reviewed.pinyin, reviewed.initials, 0, false);
      else {
        StringBuilder full = new StringBuilder(), shortName = new StringBuilder();
        for (char c : name.toCharArray()) {
          String syllable = Pinyin.toPinyin(c).toLowerCase(Locale.ROOT);
          full.append(syllable);
          shortName.append(Pinyin.isChinese(c) ? syllable.substring(0, 1) : String.valueOf(c));
        }
        return new Term(name, full.toString(), shortName.toString(), 0, false);
      }
    }

    private int score(String query) {
      int best = NONE;
      for (Term term : terms) best = Math.min(best, term.score(query));
      return best;
    }
  }

  /**
   * Reuse phonetic tokens across search sessions; fingerprint/language/dictionary changes rebuild.
   */
  public static final class ItemCache {
    private static final class Cached {
      final String fingerprint;
      final AppAliases dictionary;
      final Item item;

      Cached(String fingerprint, AppAliases dictionary, Item item) {
        this.fingerprint = fingerprint;
        this.dictionary = dictionary;
        this.item = item;
      }
    }

    private final Map<String, Cached> entries = new HashMap<>();

    public synchronized Item get(
        String id, String label, String packageName, String fingerprint, AppAliases dictionary) {
      return get(id, label, packageName, fingerprint, dictionary, "");
    }

    public synchronized Item get(
        String id,
        String label,
        String packageName,
        String fingerprint,
        AppAliases dictionary,
        String customName) {
      Cached cached = entries.get(id);
      if (cached != null
          && cached.dictionary == dictionary
          && cached.fingerprint.equals(fingerprint)
          && cached.item.label.equals(label)
          && cached.item.customName.equals(customName)
          && cached.item.packageName.equals(packageName)) return cached.item;
      Item item = new Item(id, label, packageName, dictionary, customName);
      entries.put(id, new Cached(fingerprint, dictionary, item));
      return item;
    }

    public synchronized void retain(Collection<String> ids) {
      entries.keySet().retainAll(ids);
    }
  }

  private static final class Match {
    final Item item;
    final int score, homePosition;
    final double usage;

    Match(Item item, int score, int homePosition, double usage) {
      this.item = item;
      this.score = score;
      this.homePosition = homePosition;
      this.usage = Double.isNaN(usage) || Double.isInfinite(usage) || usage < 0 ? 0 : usage;
    }
  }

  private final List<Item> items;
  private final Map<String, Double> usage;

  public AppSearch(List<Item> items) {
    this(items, Collections.emptyMap());
  }

  public AppSearch(List<Item> items, Map<String, Double> usage) {
    Map<String, Item> unique = new LinkedHashMap<>();
    for (Item item : items) if (!unique.containsKey(item.id)) unique.put(item.id, item);
    this.items = new ArrayList<>(unique.values());
    this.usage = new HashMap<>(usage);
  }

  public List<String> find(String query) {
    String normalized = normalize(query);
    List<Match> matches = new ArrayList<>();
    if (normalized.isEmpty()) return new ArrayList<>();
    for (int i = 0; i < items.size(); i++) {
      Item item = items.get(i);
      int score = item.score(normalized);
      if (score != NONE) {
        Double weight = usage.get(item.packageName);
        matches.add(new Match(item, score, i, weight == null ? 0 : weight));
      }
    }
    Collections.sort(
        matches,
        (a, b) -> {
          int result = Integer.compare(a.score, b.score);
          if (result == 0) result = Double.compare(b.usage, a.usage);
          if (result == 0) result = Integer.compare(a.homePosition, b.homePosition);
          return result == 0 ? a.item.id.compareTo(b.item.id) : result;
        });
    List<String> ids = new ArrayList<>();
    for (Match match : matches) ids.add(match.item.id);
    return ids;
  }

  public static String normalize(String text) {
    String value = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    StringBuilder result = new StringBuilder();
    for (int offset = 0; offset < value.length(); ) {
      int codePoint = value.codePointAt(offset);
      if (Character.isLetterOrDigit(codePoint)) result.appendCodePoint(codePoint);
      offset += Character.charCount(codePoint);
    }
    return result.toString();
  }
}
