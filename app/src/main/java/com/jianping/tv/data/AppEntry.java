package com.jianping.tv.data;

import android.content.ComponentName;

/** Lightweight launch metadata; decoded resources belong to the presentation cache. */
public final class AppEntry {
  public final String id, label, fingerprint;
  public final ComponentName component;

  public AppEntry(ComponentName component, String label, String fingerprint) {
    this.component = component;
    this.id = component.flattenToString();
    this.label = label;
    this.fingerprint = fingerprint;
  }
}
