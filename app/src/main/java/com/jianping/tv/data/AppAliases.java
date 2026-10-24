package com.jianping.tv.data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Versioned, offline search terms. Package keys are exact and retain Android's case. */
public final class AppAliases {
  public static final AppAliases EMPTY = new AppAliases("empty", Collections.emptyMap());

  public static final class Term {
    public final String text, pinyin, initials;
    public final int source;
    public final boolean exactOnly;

    private Term(String text, String pinyin, String initials, int source, boolean exactOnly) {
      this.text = text;
      this.pinyin = pinyin;
      this.initials = initials;
      this.source = source;
      this.exactOnly = exactOnly;
    }
  }

  public final String version;
  private final Map<String, List<Term>> packages;

  private AppAliases(String version, Map<String, List<Term>> packages) {
    this.version = version;
    this.packages = Collections.unmodifiableMap(packages);
  }

  public List<Term> terms(String packageName) {
    List<Term> result = packages.get(packageName);
    return result == null ? Collections.emptyList() : result;
  }

  public int packageCount() {
    return packages.size();
  }

  /** Reject a malformed dictionary as a whole; callers retain ordinary label/pinyin search. */
  public static AppAliases read(InputStream stream) throws IOException {
    Map<String, List<Term>> packages = new LinkedHashMap<>();
    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
      String header = reader.readLine();
      if (header == null || !header.startsWith("#still-search\t1\t"))
        throw new IOException("Unsupported search dictionary");
      String version = header.substring("#still-search\t1\t".length());
      if (version.isEmpty()) throw new IOException("Missing search dictionary version");
      String line;
      int number = 1;
      while ((line = reader.readLine()) != null) {
        number++;
        if (line.isEmpty()) continue;
        String[] cells = line.split("\t", -1);
        if (cells.length != 7) throw new IOException("Invalid search term at " + number);
        String pkg = cells[0], text = cells[3], pinyin = cells[4], initials = cells[5];
        if (!pkg.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+"))
          throw new IOException("Invalid package at " + number);
        if (text.length() < 2
            || !text.equals(AppSearch.normalize(text))
            || !pinyin.matches("[a-z0-9]+")
            || !initials.matches("[a-z0-9]+")
            || !cells[6].equals("reviewed"))
          throw new IOException("Invalid reviewed term at " + number);
        int source;
        try {
          source = Integer.parseInt(cells[1]);
        } catch (NumberFormatException error) {
          throw new IOException("Invalid search source at " + number, error);
        }
        if (source < 1 || source > 3 || !(cells[2].equals("exact") || cells[2].equals("partial")))
          throw new IOException("Invalid search rule at " + number);
        List<Term> terms = packages.get(pkg);
        if (terms == null) {
          terms = new ArrayList<>();
          packages.put(pkg, terms);
        }
        for (Term existing : terms)
          if (existing.text.equals(text))
            throw new IOException("Duplicate search term at " + number);
        terms.add(new Term(text, pinyin, initials, source, cells[2].equals("exact")));
      }
      for (Map.Entry<String, List<Term>> entry : packages.entrySet())
        entry.setValue(Collections.unmodifiableList(entry.getValue()));
      return new AppAliases(version, packages);
    }
  }
}
