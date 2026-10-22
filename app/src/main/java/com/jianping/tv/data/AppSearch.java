package com.jianping.tv.data;

import java.text.Normalizer;
import java.util.*;
import me.majiajie.tinypinyin.Pinyin;

/** Offline search index. Input order is the user's home order and breaks equal-score ties. */
public final class AppSearch {
  public static final class Item {
    public final String id, label;
    private final String name, pinyin, initials;

    public Item(String id, String label) {
      this.id = id;
      this.label = label;
      name = normalize(label);
      StringBuilder full = new StringBuilder(), shortName = new StringBuilder();
      for (char c : name.toCharArray()) {
        String syllable = Pinyin.toPinyin(c).toLowerCase(Locale.ROOT);
        full.append(syllable);
        shortName.append(Pinyin.isChinese(c) ? syllable.substring(0, 1) : String.valueOf(c));
      }
      pinyin = full.toString();
      initials = shortName.toString();
    }

    private int score(String query) {
      if (name.equals(query)) return 0;
      if (name.startsWith(query)) return 1;
      if (name.contains(query)) return 2;
      if (pinyin.startsWith(query)) return 3;
      if (initials.startsWith(query)) return 4;
      if (pinyin.contains(query)) return 5;
      return initials.contains(query) ? 6 : -1;
    }
  }

  private final List<Item> items;

  public AppSearch(List<Item> items) {
    this.items = new ArrayList<>(items);
  }

  public List<String> find(String query) {
    String normalized = normalize(query);
    List<Item> matches = new ArrayList<>();
    if (normalized.isEmpty()) return new ArrayList<>();
    for (Item item : items) if (item.score(normalized) >= 0) matches.add(item);
    Collections.sort(matches, (a, b) -> Integer.compare(a.score(normalized), b.score(normalized)));
    List<String> ids = new ArrayList<>();
    for (Item item : matches) ids.add(item.id);
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
