package com.jianping.tv.data;

/** Local aggregate launch weight, with lazy 14-day half-life and no event/query history. */
public final class UsageHistory {
  private static final double HALF_LIFE_MS = 14d * 24 * 60 * 60 * 1000;
  private static final double MAX_SCORE = 1_000_000;

  private UsageHistory() {}

  public static double weight(double score, long updatedAt, long now) {
    if (Double.isNaN(score) || Double.isInfinite(score) || score < 0 || updatedAt <= 0 || now <= 0)
      return 0;
    double elapsed = Math.max(0d, (double) now - updatedAt);
    double result = Math.min(MAX_SCORE, score) * Math.pow(0.5, elapsed / HALF_LIFE_MS);
    return result < 0.000001 ? 0 : result;
  }

  public static double afterLaunch(double score, long updatedAt, long now) {
    return Math.min(MAX_SCORE, weight(score, updatedAt, now) + 1);
  }
}
