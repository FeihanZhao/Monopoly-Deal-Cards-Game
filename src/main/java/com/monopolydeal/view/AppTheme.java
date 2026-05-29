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
            Map.entry("LIGHT_BLUE",  new Color(0x5BC0EB)),
            Map.entry("PINK",        new Color(0xFF69B4)),
            Map.entry("ORANGE",      new Color(0xFF8C00)),
            Map.entry("RED",         new Color(0xDC143C)),
            Map.entry("YELLOW",      new Color(0xFFD700)),
            Map.entry("GREEN",       new Color(0x2E8B2E)),
            Map.entry("BLUE",        new Color(0x1E3A8A)),
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
            Map.entry("LIGHT_BLUE",  new Color(0x2E8BC0)),
            Map.entry("PINK",        new Color(0xC2185B)),
            Map.entry("ORANGE",      new Color(0xE65100)),
            Map.entry("RED",         new Color(0x8B0000)),
            Map.entry("YELLOW",      new Color(0xB8860B)),
            Map.entry("GREEN",       new Color(0x145214)),
            Map.entry("BLUE",        new Color(0x0F2460)),
            Map.entry("BLACK",       new Color(0x111111)),
            Map.entry("LIGHT_GREEN", new Color(0x4CAF50))
    );

    // ==================== Card type theme colors ====================

    /** Money card color */
    public static final Color MONEY_CARD = new Color(0x2E7D32);
    /** Money card gradient */
    public static final Color MONEY_CARD_GRADIENT = new Color(0x1B5E20);
    /** Action card color */
    public static final Color ACTION_CARD = new Color(0x7B1FA2);
    /** Action card gradient */
    public static final Color ACTION_CARD_GRADIENT = new Color(0x4A0072);
    /** Rent card color */
    public static final Color RENT_CARD = new Color(0xBF360C);
    /** Rent card gradient */
    public static final Color RENT_CARD_GRADIENT = new Color(0x870000);

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
    /** Gold glow (semi-transparent) */
    public static final Color GOLD_GLOW = new Color(255, 215, 0, 60);
    /** Gold dim */
    public static final Color GOLD_DIM = new Color(255, 215, 0, 40);

    // ==================== Main background colors ====================

    /** Main dark background — GamePanel main background */
    public static final Color BG_DARK = new Color(18, 22, 28);
    /** Slightly lighter panel background */
    public static final Color BG_PANEL = new Color(22, 26, 32);
    /** Darker background — sidebar, etc. */
    public static final Color BG_DARKER = new Color(14, 17, 22);
    /** Subtle panel border */
    public static final Color BG_BORDER = new Color(50, 55, 65);

    /** Green table color (light end) — player area gradient background */
    public static final Color TABLE_GREEN = new Color(25, 70, 40);
    /** Green table color (dark end) — player area gradient background */
    public static final Color TABLE_GREEN_DARK = new Color(15, 50, 28);
    /** Table felt highlight color */
    public static final Color TABLE_FELT_HIGHLIGHT = new Color(30, 85, 48);
    /** Table border wood color */
    public static final Color TABLE_BORDER = new Color(60, 35, 20);

    // ==================== Text colors ====================

    /** Primary text color — white content text */
    public static final Color TEXT_PRIMARY = new Color(220, 220, 220);
    /** Secondary text color */
    public static final Color TEXT_SECONDARY = new Color(200, 200, 200);
    /** Dimmed text color — secondary/disabled state */
    public static final Color TEXT_DIM = new Color(150, 150, 150);
    /** Muted text color */
    public static final Color TEXT_MUTED = new Color(100, 100, 110);

    // ==================== Semantic colors ====================

    /** Red danger/warning — countdown alert, error states */
    public static final Color RED_DANGER = new Color(220, 50, 50);
    /** Green success */
    public static final Color GREEN_SUCCESS = new Color(46, 204, 113);
    /** Blue info */
    public static final Color BLUE_INFO = new Color(52, 152, 219);

    // ==================== Card rendering constants ====================

    /** Card width */
    public static final int CARD_W = 90;
    /** Card height */
    public static final int CARD_H = 130;
}
