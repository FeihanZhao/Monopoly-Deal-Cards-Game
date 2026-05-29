package com.monopolydeal.view;

import java.awt.Color;
import java.awt.Font;
import java.util.Map;

/**
 * Application theme constants — the single source of truth for all shared colors and styles in the view layer.
 *
 * Design principles:
 * - Property colors match the hex values used in CardRenderer (shared across both locations)
 * - All view components reference static fields instead of defining their own color maps
 * - Utility class pattern: private constructor prevents instantiation
 */
public final class AppTheme {

    private AppTheme() {
        // Utility class, prevent instantiation
    }

    // ==================== Property color mapping (10 pure property colors → RGB) ====================

    /**
     * Pure property color mapping table.
     * key = CardColor enum name (e.g. "BROWN", "RED"), value = corresponding RGB color.
     * Used by: PlayerPanel property stack drawing, PropertySetPanel label backgrounds, CardRenderer card colors.
     */
    public static final Map<String, Color> PROPERTY_COLORS = Map.ofEntries(
            Map.entry("BROWN",       new Color(0x8B5E3C)),
            Map.entry("LIGHT_BLUE",  new Color(0x87CEEB)),
            Map.entry("PINK",        new Color(0xFF69B4)),
            Map.entry("ORANGE",      new Color(0xFF8C00)),
            Map.entry("RED",         new Color(0xDC143C)),
            Map.entry("YELLOW",      new Color(0xFFD700)),
            Map.entry("GREEN",       new Color(0x228B22)),
            Map.entry("BLUE",        new Color(0x00008B)),
            Map.entry("BLACK",       new Color(0x2B2B2B)),
            Map.entry("LIGHT_GREEN", new Color(0x90EE90))
    );

    /**
     * Property card gradient color mapping — the gradient endpoint color on the upper half of cards in CardRenderer.
     * key = CardColor enum name, value = darker gradient endpoint RGB.
     * These values come from original hand-tuned colors; must have a 1:1 correspondence with PROPERTY_COLORS keys.
     */
    public static final Map<String, Color> PROPERTY_GRADIENT_COLORS = Map.ofEntries(
            Map.entry("BROWN",       new Color(0x5D3A1A)),
            Map.entry("LIGHT_BLUE",  new Color(0x4A9EC4)),
            Map.entry("PINK",        new Color(0xC2185B)),
            Map.entry("ORANGE",      new Color(0xE65100)),
            Map.entry("RED",         new Color(0x8B0000)),
            Map.entry("YELLOW",      new Color(0xB8860B)),
            Map.entry("GREEN",       new Color(0x145214)),
            Map.entry("BLUE",        new Color(0x000055)),
            Map.entry("BLACK",       new Color(0x111111)),
            Map.entry("LIGHT_GREEN", new Color(0x4CAF50))
    );

    // ==================== Wild card configuration ====================

    /**
     * Wild property color selection mapping — key=wild card name, value=list of selectable property colors.
     * Used by showWildColorPicker() to provide limited color options based on card type.
     */
    public static final Map<String, String[]> WILD_COLOR_OPTIONS = Map.ofEntries(
            Map.entry("Multi-Color Wild",
                    new String[]{"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED",
                            "YELLOW", "GREEN", "BLUE", "BLACK", "LIGHT_GREEN"}),
            Map.entry("Dark Blue/Green Wild",
                    new String[]{"BLUE", "GREEN"}),
            Map.entry("Red/Yellow Wild",
                    new String[]{"RED", "YELLOW"}),
            Map.entry("Brown/Light Blue Wild",
                    new String[]{"BROWN", "LIGHT_BLUE"}),
            Map.entry("Orange/Pink Wild",
                    new String[]{"ORANGE", "PINK"}),
            Map.entry("Light Green/Black Wild",
                    new String[]{"LIGHT_GREEN", "BLACK"})
    );

    /**
     * Text contrast colors for property buttons/dialogs.
     * Returns a high-contrast text color for each property color background.
     */
    public static final Map<String, Color> TEXT_CONTRAST_COLORS = Map.ofEntries(
            Map.entry("BROWN",       Color.WHITE),
            Map.entry("LIGHT_BLUE",  new Color(0x1A1A1A)),
            Map.entry("PINK",        Color.WHITE),
            Map.entry("ORANGE",      Color.WHITE),
            Map.entry("RED",         Color.WHITE),
            Map.entry("YELLOW",      new Color(0x1A1A1A)),
            Map.entry("GREEN",       Color.WHITE),
            Map.entry("BLUE",        Color.WHITE),
            Map.entry("BLACK",       Color.WHITE),
            Map.entry("LIGHT_GREEN", new Color(0x1A1A1A))
    );

    /**
     * Hover highlight colors for property buttons/dialogs.
     * A lighter version of each property color used on hover state.
     */
    public static final Map<String, Color> HOVER_LIGHT_COLORS = Map.ofEntries(
            Map.entry("BROWN",       new Color(0xA07050)),
            Map.entry("LIGHT_BLUE",  new Color(0xA3DBF2)),
            Map.entry("PINK",        new Color(0xFF8FC8)),
            Map.entry("ORANGE",      new Color(0xFFA640)),
            Map.entry("RED",         new Color(0xE05060)),
            Map.entry("YELLOW",      new Color(0xFFE44D)),
            Map.entry("GREEN",       new Color(0x3AA03A)),
            Map.entry("BLUE",        new Color(0x3030A0)),
            Map.entry("BLACK",       new Color(0x505050)),
            Map.entry("LIGHT_GREEN", new Color(0xA8F0A8))
    );

    // ==================== Brand colors ====================

    /** Gold — used for titles, highlighted borders, turn indicators, etc. */
    public static final Color GOLD = new Color(255, 215, 0);

    // ==================== Main background colors ====================

    /** Main dark background — GamePanel main background */
    public static final Color BG_DARK = new Color(18, 22, 28);

    /** Darker background — sidebar, etc. */
    public static final Color BG_DARKER = new Color(14, 17, 22);

    /** Green table color (light end) — player area gradient background */
    public static final Color TABLE_GREEN = new Color(25, 70, 40);

    /** Green table color (dark end) — player area gradient background */
    public static final Color TABLE_GREEN_DARK = new Color(15, 50, 28);

    // ==================== Text colors ====================

    /** Primary text color — white content text */
    public static final Color TEXT_PRIMARY = new Color(220, 220, 220);

    /** Dimmed text color — secondary/disabled state */
    public static final Color TEXT_DIM = new Color(150, 150, 150);

    // ==================== Semantic colors ====================

    /** Red danger/warning — countdown alert, error states */
    public static final Color RED_DANGER = new Color(220, 50, 50);

    /** Red glow — bright accent for hover/active states */
    public static final Color RED_GLOW = new Color(255, 100, 100);

    /** Red dark — pressed/disabled state */
    public static final Color RED_DARK = new Color(140, 20, 20);

    /** Green glow — success/complete states */
    public static final Color GREEN_GLOW = new Color(40, 180, 100);

    /** Purple accent — used for special borders and buttons */
    public static final Color PURPLE_ACCENT = new Color(130, 50, 210);

    /** Purple glow — lighter purple for glowing borders */
    public static final Color PURPLE_GLOW = new Color(160, 80, 255);

    /** Purple dark — darker shade for backgrounds */
    public static final Color PURPLE_DARK = new Color(70, 20, 110);

    // ==================== Shadow colors ====================

    /** Standard shadow color (semi-transparent black) */
    public static final Color SHADOW = new Color(0, 0, 0, 80);

    /** Strong shadow for selected/raised elements */
    public static final Color SHADOW_STRONG = new Color(0, 0, 0, 120);

    // ==================== Font constants ====================

    /** Primary UI font family name */
    public static final String FONT_MAIN = "Segoe UI";

    /** Monospace font family name */
    public static final String FONT_MONO = "Consolas";

    /** Emoji font family name (for card type icons) */
    public static final String FONT_EMOJI = "Segoe UI Emoji";

    /** Fallback font name when Segoe UI is unavailable */
    public static final String FONT_FALLBACK = "SansSerif";

    // ==================== Animation constants ====================

    /** Frame interval in ms for 60fps animations */
    public static final int ANIM_MS = 16;

    /** Ease factor for animation interpolation (0.0=linear, 1.0=instant) */
    public static final float ANIM_EASE = 0.25f;

    /** Hover animation speed (0.0=slow, 1.0=fast) */
    public static final float ANIM_HOVER_SPEED = 0.12f;

    // ==================== Panel transparency ====================

    /** Glass morphism background opacity (for floating panels) */
    public static final float ALPHA_GLASS = 0.85f;
}
