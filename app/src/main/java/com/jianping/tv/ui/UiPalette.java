package com.jianping.tv.ui;

/** Semantic colors shared by native home, settings, menus and focus treatments. */
public final class UiPalette {
  public static final UiPalette LIGHT = new UiPalette(true), DARK = new UiPalette(false);
  public final boolean light;
  public final int background, text, secondary, muted, icon, headerFocus, headerFocusIcon;
  public final int surfaceTop,
      surfaceBottom,
      divider,
      outline,
      rowFocus,
      rowFocusText,
      rowFocusIcon;
  public final int menuTop, menuBottom, menuFocusTop, menuFocusBottom, scrim, cardFallback;
  public final int cardName, cardNameFocused, movingText;

  private UiPalette(boolean light) {
    this.light = light;
    background = light ? 0xFFF6F5F2 : 0xFF0B0F17;
    text = light ? 0xFF333D49 : 0xFFE7ECF4;
    secondary = light ? 0xFF5B697A : 0xFFB2BCCD;
    muted = light ? 0xFF5F6D7D : 0xFF94A1B5;
    icon = light ? 0xFF586575 : 0xFFBBC7D9;
    headerFocus = light ? 0x14000000 : 0x20FFFFFF;
    headerFocusIcon = light ? 0xFF293B50 : 0xFFFFFFFF;
    surfaceTop = light ? 0xEFFFFFFF : 0xC1242E3D;
    surfaceBottom = light ? 0xD9EEEEED : 0xB11C2532;
    divider = light ? 0x12000000 : 0x192E4058;
    outline = light ? 0x1A677384 : 0x26CFDCEE;
    rowFocus = light ? 0xFFDFE7F1 : 0xFFE7ECF3;
    rowFocusText = 0xFF243249;
    rowFocusIcon = 0xFF3A506A;
    menuTop = light ? 0xFFFEFEFC : 0xFF293546;
    menuBottom = light ? 0xFFF1F2F0 : 0xFF192331;
    menuFocusTop = light ? 0xFFE2EAF4 : 0xFFF6F8FB;
    menuFocusBottom = light ? 0xFFD5E0EE : 0xFFDDE5EF;
    scrim = light ? 0x2B667180 : 0xAD040810;
    cardFallback = light ? 0xFFE9ECEF : 0xFF263244;
    cardName = light ? 0xFF5F6D7D : 0xFFB9C3D2;
    cardNameFocused = light ? 0xFF303D4E : 0xFFFFFFFF;
    movingText = light ? 0xFF4E6076 : 0xFFEDF3FC;
  }

  /** Keep native elevation shadows quiet on white while retaining the same raised geometry. */
  public void styleShadow(android.view.View view) {
    if (light && android.os.Build.VERSION.SDK_INT >= 28) {
      view.setOutlineAmbientShadowColor(0x20000000);
      view.setOutlineSpotShadowColor(0x30000000);
    }
  }

  public static UiPalette from(boolean light) {
    return light ? LIGHT : DARK;
  }
}
