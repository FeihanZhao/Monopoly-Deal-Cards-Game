package com.monopolydeal.view;

import java.awt.Color;
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
}
