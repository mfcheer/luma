package com.jianping.tv.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class AppOrderTest {

  @Test
  public void focusAfterRemoval_whenSelectedInAfter_preservesSelection() {
    List<String> before = Arrays.asList("app.a", "app.b", "app.c");
    List<String> after = Arrays.asList("app.a", "app.b", "app.c");
    assertEquals("app.b", AppOrder.focusAfterRemoval(before, after, "app.b"));
  }

  @Test
  public void focusAfterRemoval_whenSelectedNotInAfter_fallsBackToValidSlot() {
    List<String> before = Arrays.asList("app.a", "app.b", "app.c");
    List<String> after = Arrays.asList("app.a", "app.c");
    assertEquals("app.c", AppOrder.focusAfterRemoval(before, after, "app.b"));
  }

  @Test
  public void focusAfterRemoval_whenEmptyInitialSelection_picksFirstAvailableApp() {
    List<String> before = Collections.emptyList();
    List<String> after = Arrays.asList("app.first", "app.second");
    assertEquals("app.first", AppOrder.focusAfterRemoval(before, after, ""));
  }

  @Test
  public void focusAfterRemoval_whenSelectedWasAirInstall_revertsToFirstApp() {
    List<String> before = Collections.emptyList();
    List<String> after = Arrays.asList("com.bilibili.tv", "com.gitv.tv");
    assertEquals("com.bilibili.tv", AppOrder.focusAfterRemoval(before, after, "air_install"));
  }

  @Test
  public void reconcile_preservesSavedOrderAndAppendsNew() {
    List<String> saved = Arrays.asList("app.b", "app.a");
    List<String> available = Arrays.asList("app.a", "app.b", "app.c");
    List<String> result = AppOrder.reconcile(saved, available);
    assertEquals(Arrays.asList("app.b", "app.a", "app.c"), result);
  }
}
