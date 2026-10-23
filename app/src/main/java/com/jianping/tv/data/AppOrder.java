package com.jianping.tv.data;

import java.util.*;

/** Reconcile a user order with launchable apps: retain positions, drop missing, append new. */
public final class AppOrder {
  private AppOrder() {}

  /** Promote one existing entry without mutating the caller's order or other positions. */
  public static List<String> moveToFront(List<String> order, String id) {
    List<String> result = new ArrayList<>(order);
    if (result.remove(id)) result.add(0, id);
    return result;
  }

  public static List<String> reconcile(List<String> previous, List<String> available) {
    Map<String, String> remaining = new LinkedHashMap<>();
    for (String id : available) {
      if (id == null || id.isEmpty()) continue;
      String key = packageKey(id);
      if (!remaining.containsKey(key)) remaining.put(key, id);
    }
    List<String> result = new ArrayList<>();
    for (String id : previous) {
      if (id == null || id.isEmpty()) continue;
      String current = remaining.remove(packageKey(id));
      if (current != null) result.add(current);
    }
    result.addAll(remaining.values());
    return result;
  }

  private static String packageKey(String id) {
    int slash = id.indexOf('/');
    return slash < 0 ? id : id.substring(0, slash);
  }
}
