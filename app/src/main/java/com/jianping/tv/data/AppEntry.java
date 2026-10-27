package com.jianping.tv.data;

import android.content.ComponentName;

/** Lightweight launch metadata; decoded resources belong to the presentation cache. */
public final class AppEntry {
  public final String id, label, fingerprint;
  public final ComponentName component;
  public final long firstInstallTime;

  public AppEntry(ComponentName component, String label, String fingerprint) {
    this(component, label, fingerprint, 0);
  }

  public AppEntry(
      ComponentName component, String label, String fingerprint, long firstInstallTime) {
    this.component = component;
    this.id = component.flattenToString();
    this.label = label;
    this.fingerprint = fingerprint;
    this.firstInstallTime = firstInstallTime;
  }
}
