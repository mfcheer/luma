package com.jianping.tv.data;

import android.content.ComponentName;
import android.graphics.drawable.Drawable;

public final class AppEntry {
  public final String id, label;
  public final ComponentName component;
  public final Drawable icon;

  public AppEntry(ComponentName component, String label, Drawable icon) {
    this.component = component;
    this.id = component.flattenToString();
    this.label = label;
    this.icon = icon;
  }
}
