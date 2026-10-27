package com.jianping.tv.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;
import com.jianping.tv.data.AppEntry;
import java.util.*;

/** Five-column continuous wall, stable card identity and animated reorder. */
public final class GridWallView extends ScrollView {
  public static final int COLUMNS = 5;

  public interface Listener {
    default String label(AppEntry app) {
      return app.label;
    }

    Drawable artwork(AppEntry app);

    Drawable preview(AppEntry app);

    int accent(AppEntry app);

    void focused(AppEntry app, String key);

    void anchor(float x, float y, boolean animated);

    void activate(AppEntry app);

    void hold(AppEntry app, View source);

    boolean key(int code, KeyEvent event);
  }

  private final float unit;
  private final UiPalette palette;
  private final int rowHeight;
  private final Listener listener;
  private final FrameLayout content;
  private final Map<String, AppCardView> cards = new HashMap<>();
  private final Map<String, AppEntry> entries = new HashMap<>();
  private List<String> order = new ArrayList<>();
  private String current = "", movingKey = "";
  private long lastFocusAt;
  private int upId;
  private boolean showNames;
  private float baselineLift;
  private boolean edgesEnabled = true;
  private final Paint edgeMask = new Paint(Paint.ANTI_ALIAS_FLAG);
  private LinearGradient topMask, bottomMask;
  private final android.graphics.Matrix maskTransform = new android.graphics.Matrix();

  private int px(float value) {
    return Math.round(value * unit);
  }

  public GridWallView(Context c, float unit, int rowPitch, UiPalette palette, Listener listener) {
    super(c);
    this.unit = unit;
    this.palette = palette;
    this.rowHeight = px(rowPitch);
    this.listener = listener;
    setPadding(px(6), px(16), px(6), px(12));
    // Focused cards use the top padding as lift headroom; clip only at the viewport itself.
    setClipToPadding(false);
    setClipChildren(false);
    setVerticalScrollBarEnabled(false);
    // One fixed viewport mask for both edges. Native fading-edge padding changes
    // during row transitions used to expose a bright strip above the artwork.
    setVerticalFadingEdgeEnabled(false);
    edgeMask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    topMask = new LinearGradient(0, 0, 0, 1, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP);
    bottomMask =
        new LinearGradient(0, 0, 0, 1, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP);
    content = new FrameLayout(c);
    content.setClipChildren(false);
    content.setClipToPadding(false);
    addView(content, new ScrollView.LayoutParams(-1, -2));
  }

  public void setEntries(List<AppEntry> apps, String focusKey, boolean animate) {
    setEntries(apps, focusKey, animate, true);
  }

  public void setEntries(List<AppEntry> apps, String focusKey, boolean animate, boolean takeFocus) {
    Map<String, AppEntry> previous = new HashMap<>(entries);
    entries.clear();
    List<String> next = new ArrayList<>();
    for (AppEntry app : apps) {
      entries.put(app.id, app);
      next.add(app.id);
    }
    for (String key : new ArrayList<>(cards.keySet())) {
      AppEntry before = previous.get(key), after = entries.get(key);
      boolean changed =
          before != null
              && after != null
              && (!before.label.equals(after.label)
                  || !before.fingerprint.equals(after.fingerprint));
      if (after == null || changed) content.removeView(cards.remove(key));
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
              listener.label(app),
              listener.artwork(app),
              listener.preview(app),
              listener.accent(app),
              palette);
      card.showName(showNames);
      card.focusBaselineLift(baselineLift);
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
            if (focus) {
              long now = android.os.SystemClock.uptimeMillis();
              card.rapidFocus(lastFocusAt != 0 && now - lastFocusAt < 200);
              lastFocusAt = now;
            }
            visual.onFocusChange(v, focus);
            if (focus) {
              current = key;
              notifyFocus(key);
            }
          });
      cards.put(key, card);
      content.addView(card);
    }
    refreshLabels();
    layoutCards(animate);
    movingKey(movingKey);
    navigation();
    if (takeFocus) focusKey(focusKey, !animate);
    else {
      current = cards.containsKey(focusKey) ? focusKey : (order.isEmpty() ? "" : order.get(0));
      notifyFocus(current);
    }
  }

  private void notifyFocus(String key) {
    int i = order.indexOf(key);
    if (i >= 0) {
      listener.focused(entries.get(key), key);
      post(() -> notifyAnchor(true));
    }
  }

  public void refreshLabels() {
    for (String id : order) cards.get(id).label(listener.label(entries.get(id)));
  }

  public void movingKey(String key) {
    movingKey = key == null ? "" : key;
    for (String id : cards.keySet()) cards.get(id).moving(id.equals(movingKey));
  }

  private void notifyAnchor(boolean animated) {
    AppCardView card = cards.get(current);
    if (card == null || !card.isLaidOut()) return;
    listener.anchor(
        getX() + getPaddingLeft() + card.getX() + card.getWidth() / 2f,
        getY() + getPaddingTop() + card.getY() - getScrollY() + px(47),
        animated);
  }

  public void showNames(boolean visible) {
    showNames = visible;
    for (AppCardView card : cards.values()) card.showName(visible);
  }

  public void focusBaselineLift(float pixels) {
    baselineLift = pixels;
    for (AppCardView card : cards.values()) card.focusBaselineLift(pixels);
  }

  public void edgeTreatment(boolean enabled) {
    if (edgesEnabled == enabled) return;
    edgesEnabled = enabled;
    updatePreviews();
    invalidate();
  }

  private void layoutCards(boolean animate) {
    int width = getWidth();
    if (width <= 0 && getLayoutParams() != null) width = getLayoutParams().width;
    int usable = width - getPaddingLeft() - getPaddingRight();
    if (usable <= 0) usable = px(864);
    for (int i = 0; i < order.size(); i++) {
      String key = order.get(i);
      AppCardView card = cards.get(key);
      int left = i % COLUMNS * usable / COLUMNS,
          right = (i % COLUMNS + 1) * usable / COLUMNS,
          top = i / COLUMNS * rowHeight;
      float oldX = card.getX(), oldY = card.getY();
      boolean laidOut = card.isLaidOut();
      card.animate().cancel();
      card.animate().setUpdateListener(null);
      FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(right - left, rowHeight);
      p.leftMargin = left;
      p.topMargin = top;
      card.setLayoutParams(p);
      if (animate && laidOut) {
        card.setTranslationX(oldX - left);
        card.setTranslationY(oldY - top);
        card.animate()
            .translationX(0)
            .translationY(0)
            .setDuration(180)
            .setUpdateListener(
                a -> {
                  if (key.equals(current)) notifyAnchor(false);
                })
            .start();
      } else {
        card.setTranslationX(0);
        card.setTranslationY(0);
      }
    }
  }

  @Override
  protected void dispatchDraw(Canvas canvas) {
    int save = canvas.save();
    int left = getScrollX(), top = getScrollY(), right = left + getWidth();
    int bottom = top + getHeight() - getPaddingBottom();
    canvas.clipRect(left, top, right, bottom);
    // Fade only artwork actually leaving through the upper edge. At an aligned
    // row the first card starts below the headroom and must stay fully opaque.
    // The outgoing artwork (including shadow) is gone before the next row arrives,
    // so this reaches zero without changing opacity across a row boundary.
    int phase = Math.max(0, top) % rowHeight;
    float clipped = Math.max(0, phase - getPaddingTop() - px(8));
    float remaining = Math.max(0, getPaddingTop() + px(102) - phase);
    float topFade = Math.min(px(48), remaining) * Math.min(1, clipped / px(24));
    float bottomFade =
        Math.min(px(48), Math.max(0, content.getHeight() + getPaddingTop() - bottom));
    if (!edgesEnabled) topFade = bottomFade = 0;
    AppCardView focused = cards.get(current);
    if (focused != null && (focused.focusHighlighted() || !movingKey.isEmpty())) {
      // Protect the complete lifted card and its label, including at restored offsets.
      float cardTop = getPaddingTop() + focused.getY();
      topFade = Math.min(topFade, Math.max(0, cardTop - top - px(16)));
      bottomFade = Math.min(bottomFade, Math.max(0, bottom - cardTop - px(128)));
    }
    boolean masked = topFade > 0 || bottomFade > 0;
    if (masked) canvas.saveLayer(left, top, right, bottom, null);
    super.dispatchDraw(canvas);
    if (topFade > 0) drawEdge(canvas, topMask, left, right, top, topFade);
    if (bottomFade > 0) drawEdge(canvas, bottomMask, left, right, bottom - bottomFade, bottomFade);
    if (masked) canvas.restore();
    canvas.restoreToCount(save);
  }

  private void drawEdge(
      Canvas canvas, LinearGradient gradient, int left, int right, float y, float height) {
    maskTransform.setScale(1, height);
    maskTransform.postTranslate(0, y);
    gradient.setLocalMatrix(maskTransform);
    edgeMask.setShader(gradient);
    canvas.drawRect(left, y, right, y + height, edgeMask);
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

  public void returnToFirstRow() {
    if (order.isEmpty()) return;
    int index = order.indexOf(current);
    // Keep the selection when already on the first row; otherwise start at the first app.
    focusKey(index >= 0 && index < COLUMNS ? current : order.get(0), false);
  }

  public void restoreFocus(String key, int offset) {
    focusKey(key, true);
    post(() -> scrollTo(0, scrollForRow(Math.max(0, order.indexOf(current)) / COLUMNS, offset)));
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
          int row = index / COLUMNS;
          int y = scrollForRow(row, Math.max(0, row - 1) * rowHeight);
          if (immediate) scrollTo(0, y);
          else smoothScrollTo(0, y);
        });
  }

  @Override
  protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
    int row = Math.max(0, rect.top / rowHeight);
    return scrollForRow(row, getScrollY()) - getScrollY();
  }

  private int scrollForRow(int row, int preferred) {
    int target = preferred;
    if (row * rowHeight < target) target = row * rowHeight;
    // The search wall has larger cards in a shorter viewport: two complete row
    // pitches do not always fit. Scroll the extra few pixels needed for the name
    // instead of snapping to a row offset that still leaves its caption clipped.
    int visibleHeight = getHeight() - getPaddingBottom();
    int visualBottom = getPaddingTop() + row * rowHeight + px(124);
    if (visualBottom > target + visibleHeight)
      target = Math.max(Math.max(0, row - 1) * rowHeight, visualBottom - visibleHeight);
    int available = getHeight() - getPaddingTop() - getPaddingBottom();
    int max = Math.max(0, content.getHeight() - available);
    return Math.max(0, Math.min(target, max));
  }

  @Override
  protected void onScrollChanged(int l, int t, int oldl, int oldt) {
    super.onScrollChanged(l, t, oldl, oldt);
    updatePreviews();
    notifyAnchor(false);
  }

  private void updatePreviews() {
    int top = getScrollY(), bottom = top + getHeight() - getPaddingTop() - getPaddingBottom();
    for (String key : order) {
      AppCardView card = cards.get(key);
      int rowTop = card.getTop();
      float hidden = Math.max(Math.max(0, top - rowTop), Math.max(0, rowTop + rowHeight - bottom));
      card.preview(
          !edgesEnabled || card.focusHighlighted() || key.equals(movingKey)
              ? 0
              : Math.min(1, hidden / (rowHeight * .45f)));
    }
  }

  @Override
  protected void onLayout(boolean changed, int l, int t, int r, int b) {
    super.onLayout(changed, l, t, r, b);
    updatePreviews();
    notifyAnchor(false);
  }
}
