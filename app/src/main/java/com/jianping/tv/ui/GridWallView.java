package com.jianping.tv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import com.jianping.tv.data.AppEntry;
import java.util.*;

/** Five-column continuous wall, stable card identity and animated reorder. */
public final class GridWallView extends ScrollView {
  public static final int COLUMNS = 5;

  public interface Listener {
    Drawable artwork(AppEntry app);

    Drawable preview(AppEntry app);

    int accent(AppEntry app);

    void focused(AppEntry app, String key);

    void activate(AppEntry app);

    void hold(AppEntry app, View source);

    boolean key(int code, KeyEvent event);
  }

  private final float unit;
  private final int rowHeight;
  private final Listener listener;
  private final FrameLayout content;
  private final Map<String, AppCardView> cards = new HashMap<>();
  private final Map<String, AppEntry> entries = new HashMap<>();
  private List<String> order = new ArrayList<>();
  private String current = "";
  private int upId;
  private boolean showNames = true;

  private int px(float value) {
    return Math.round(value * unit);
  }

  public GridWallView(Context c, float unit, int rowPitch, Listener listener) {
    super(c);
    this.unit = unit;
    this.rowHeight = px(rowPitch);
    this.listener = listener;
    setPadding(px(6), px(12), px(6), px(12));
    // Focused cards use the top padding as lift headroom; clip only at the viewport itself.
    setClipToPadding(false);
    setClipChildren(false);
    setVerticalScrollBarEnabled(false);
    setVerticalFadingEdgeEnabled(true);
    setFadingEdgeLength(px(38));
    content = new FrameLayout(c);
    content.setClipChildren(false);
    content.setClipToPadding(false);
    addView(content, new ScrollView.LayoutParams(-1, -2));
  }

  public void setEntries(List<AppEntry> apps, String focusKey, boolean animate) {
    entries.clear();
    List<String> next = new ArrayList<>();
    for (AppEntry app : apps) {
      entries.put(app.id, app);
      next.add(app.id);
    }
    for (String key : new ArrayList<>(cards.keySet())) {
      if (!next.contains(key)) {
        content.removeView(cards.remove(key));
      }
    }
    order = next;
    int rows = Math.max(1, (order.size() + COLUMNS - 1) / COLUMNS);
    content.setLayoutParams(new ScrollView.LayoutParams(-1, rows * rowHeight));
    for (String key : order) {
      if (cards.containsKey(key)) continue;
      AppEntry app = entries.get(key);
      AppCardView card =
          new AppCardView(
              getContext(),
              unit,
              app.label,
              listener.artwork(app),
              listener.preview(app),
              listener.accent(app));
      card.showName(showNames);
      card.setId(View.generateViewId());
      card.action(() -> listener.activate(entries.get(key)));
      card.setOnLongClickListener(
          v -> {
            AppEntry entry = entries.get(key);
            if (entry == null) return false;
            listener.hold(entry, v);
            return true;
          });
      card.setOnKeyListener((v, code, event) -> listener.key(code, event));
      View.OnFocusChangeListener visual = card.getOnFocusChangeListener();
      card.setOnFocusChangeListener(
          (v, focus) -> {
            visual.onFocusChange(v, focus);
            if (focus) {
              current = key;
              notifyFocus(key);
            }
          });
      cards.put(key, card);
      content.addView(card);
    }
    layoutCards(animate);
    navigation();
    focusKey(focusKey, !animate);
  }

  private void notifyFocus(String key) {
    int i = order.indexOf(key);
    if (i >= 0) listener.focused(entries.get(key), key);
  }

  public void showNames(boolean visible) {
    showNames = visible;
    for (AppCardView card : cards.values()) card.showName(visible);
  }

  private void layoutCards(boolean animate) {
    int usable = getWidth() - getPaddingLeft() - getPaddingRight();
    if (usable <= 0) usable = px(864);
    for (int i = 0; i < order.size(); i++) {
      AppCardView card = cards.get(order.get(i));
      int left = i % COLUMNS * usable / COLUMNS,
          right = (i % COLUMNS + 1) * usable / COLUMNS,
          top = i / COLUMNS * rowHeight;
      float oldX = card.getX(), oldY = card.getY();
      boolean laidOut = card.isLaidOut();
      card.animate().cancel();
      FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(right - left, rowHeight);
      p.leftMargin = left;
      p.topMargin = top;
      card.setLayoutParams(p);
      if (animate && laidOut) {
        card.setTranslationX(oldX - left);
        card.setTranslationY(oldY - top);
        card.animate().translationX(0).translationY(0).setDuration(180).start();
      } else {
        card.setTranslationX(0);
        card.setTranslationY(0);
      }
    }
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    int save = canvas.save();
    int top = getScrollY() + (getScrollY() % rowHeight == 0 ? 0 : getPaddingTop());
    canvas.clipRect(
        getScrollX(),
        top,
        getScrollX() + getWidth(),
        getScrollY() + getHeight() - getPaddingBottom());
    super.dispatchDraw(canvas);
    canvas.restoreToCount(save);
  }

  @Override
  protected void onSizeChanged(int w, int h, int ow, int oh) {
    super.onSizeChanged(w, h, ow, oh);
    layoutCards(false);
  }

  private void navigation() {
    for (int i = 0; i < order.size(); i++) {
      AppCardView card = cards.get(order.get(i));
      int start = i / COLUMNS * COLUMNS, end = Math.min(start + COLUMNS - 1, order.size() - 1);
      card.setAccessibilityTraversalAfter(i > 0 ? cards.get(order.get(i - 1)).getId() : View.NO_ID);
      card.setAccessibilityTraversalBefore(
          i + 1 < order.size() ? cards.get(order.get(i + 1)).getId() : View.NO_ID);
      card.setNextFocusLeftId(cards.get(order.get(Math.max(start, i - 1))).getId());
      card.setNextFocusRightId(cards.get(order.get(Math.min(end, i + 1))).getId());
      card.setNextFocusUpId(i >= COLUMNS ? cards.get(order.get(i - COLUMNS)).getId() : upId);
      card.setNextFocusDownId(
          start + COLUMNS < order.size()
              ? cards.get(order.get(Math.min(i + COLUMNS, order.size() - 1))).getId()
              : card.getId());
    }
  }

  public void upperFocus(int id) {
    upId = id;
    navigation();
  }

  public int firstId() {
    return order.isEmpty() ? getId() : cards.get(order.get(0)).getId();
  }

  public String currentKey() {
    return current;
  }

  public void restoreFocus(String key, int offset) {
    focusKey(key, true);
    post(() -> scrollTo(0, offset));
  }

  public void focusKey(String key, boolean immediate) {
    if (order.isEmpty()) return;
    String target = cards.containsKey(key) ? key : order.get(0);
    cards.get(target).requestFocus();
    current = target;
    notifyFocus(target);
    post(
        () -> {
          int index = order.indexOf(target);
          if (index < 0) return;
          int y = Math.max(0, index / COLUMNS - 1) * rowHeight;
          if (immediate) scrollTo(0, y);
          else smoothScrollTo(0, y);
        });
  }

  @Override
  protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
    int row = Math.max(0, rect.top / rowHeight);
    int available = getHeight() - getPaddingTop() - getPaddingBottom();
    int target = getScrollY();
    if (row * rowHeight < target) target = row * rowHeight;
    else if (row * rowHeight + px(122) > target + available)
      target = Math.max(0, row - 1) * rowHeight;
    int max = Math.max(0, content.getHeight() - available);
    return Math.min(target, max) - getScrollY();
  }

  @Override
  protected float getTopFadingEdgeStrength() {
    return getScrollY() % rowHeight == 0 ? 0 : super.getTopFadingEdgeStrength();
  }

  @Override
  protected void onScrollChanged(int l, int t, int oldl, int oldt) {
    super.onScrollChanged(l, t, oldl, oldt);
    updatePreviews();
  }

  private void updatePreviews() {
    int top = getScrollY(), bottom = top + getHeight() - getPaddingTop() - getPaddingBottom();
    for (String key : order) {
      AppCardView card = cards.get(key);
      int rowTop = card.getTop();
      float hidden = Math.max(Math.max(0, top - rowTop), Math.max(0, rowTop + rowHeight - bottom));
      card.preview(card.hasFocus() ? 0 : Math.min(1, hidden / (rowHeight * .45f)));
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    updatePreviews();
  }
}
