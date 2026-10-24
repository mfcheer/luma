package com.jianping.tv.ui;

import android.content.Context;
import android.util.Log;
import com.jianping.tv.data.*;
import java.io.IOException;
import java.util.*;

/** Prepared on the existing search worker, never from a text-change or resume callback. */
final class SearchIndex {
  private static final AppSearch.ItemCache ITEMS = new AppSearch.ItemCache();
  private static AppAliases aliases;

  private SearchIndex() {}

  private static synchronized AppAliases aliases(Context context) {
    if (aliases == null) {
      try {
        aliases = AppAliases.read(context.getAssets().open("search-aliases.tsv"));
      } catch (IOException | RuntimeException error) {
        Log.w("StillSearch", "Dictionary unavailable; retaining label/pinyin search", error);
        aliases = AppAliases.EMPTY;
      }
    }
    return aliases;
  }

  static AppSearch prepare(Context context, List<AppEntry> apps, Map<String, Double> usage) {
    long began = android.os.SystemClock.elapsedRealtime();
    AppAliases dictionary = aliases(context);
    List<AppSearch.Item> items = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    for (AppEntry app : apps) {
      ids.add(app.id);
      items.add(
          ITEMS.get(
              app.id, app.label, app.component.getPackageName(), app.fingerprint, dictionary));
    }
    ITEMS.retain(ids);
    Log.i(
        "StillSearch",
        "indexReady apps="
            + items.size()
            + " dictionary="
            + dictionary.version
            + " elapsedMs="
            + (android.os.SystemClock.elapsedRealtime() - began));
    return new AppSearch(items, usage);
  }
}
