package com.jianping.tv.ui;

import android.content.Context;
import android.graphics.Rect;
import android.widget.ScrollView;

/** Scrolls by complete two-row pages, so no half icon remains at a resting edge. */
public final class FavoritesScrollView extends ScrollView {
  private final int rowHeight;

  public FavoritesScrollView(Context context, int rowHeight) {
    super(context);
    this.rowHeight = rowHeight;
  }

  @Override
  protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
    if (getChildCount() == 0) return 0;
    int row = Math.max(0, (rect.top - getChildAt(0).getPaddingTop()) / rowHeight);
    int target = row / 2 * rowHeight * 2;
    int max =
        Math.max(0, getChildAt(0).getHeight() + getPaddingTop() + getPaddingBottom() - getHeight());
    return Math.min(target, max) - getScrollY();
  }

  private boolean atRestingEdge() {
    int max =
        getChildCount() == 0
            ? 0
            : Math.max(
                0, getChildAt(0).getHeight() + getPaddingTop() + getPaddingBottom() - getHeight());
    return getScrollY() % (rowHeight * 2) == 0 || getScrollY() == max;
  }

  @Override
  protected float getTopFadingEdgeStrength() {
    return atRestingEdge() ? 0 : super.getTopFadingEdgeStrength();
  }

  @Override
  protected float getBottomFadingEdgeStrength() {
    return atRestingEdge() ? 0 : super.getBottomFadingEdgeStrength();
  }
}
