package com.monopolydeal.view;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Application theme class - centralized management of game UI color schemes
 *
 * Defines standard RGB values, gradient colors for all property colors,
 * and color selection options for wild cards in the Monopoly Deal card game.
 *
 * Color names are consistent with those in the CardColor enum (e.g. BROWN, LIGHT_BLUE, etc.).
 */
public final class AppTheme {

    private AppTheme() {}

    /** Property color mapping: key=color name (matching CardColor enum), value=standard display color */
    public static final Map<String, Color> PROPERTY_COLORS;

    /** Property gradient color mapping: key=color name, value=gradient end color (for top-to-bottom card gradient effect) */
    public static final Map<String, Color> PROPERTY_GRADIENT_COLORS;

    /**
     * Wild card color selection options mapping
     * key=wild card name, value=array of selectable color names
     * Used to display a color picker dialog when playing wild property/rent cards
     */
    public static final Map<String, String[]> WILD_COLOR_OPTIONS;

    static {
        // ===== Standard colors for the ten pure property colors =====
        Map<String, Color> colors = new HashMap<>();
        colors.put("BROWN",        new Color(0x8B5E3C));  // Brown
        colors.put("LIGHT_BLUE",   new Color(0x87CEEB));  // Light blue
        colors.put("PINK",         new Color(0xFF69B4));  // Pink
        colors.put("ORANGE",       new Color(0xFF8C00));  // Orange
        colors.put("RED",          new Color(0xDC143C));  // Red
        colors.put("YELLOW",       new Color(0xFFD700));  // Yellow
        colors.put("GREEN",        new Color(0x228B22));  // Green
        colors.put("BLUE",         new Color(0x0000CD));  // Blue
        colors.put("BLACK",        new Color(0x2B2B2B));  // Black
        colors.put("LIGHT_GREEN",  new Color(0x90EE90));  // Light green
        PROPERTY_COLORS = Collections.unmodifiableMap(colors);

        // ===== Property gradient colors (card top-to-bottom gradient from PROPERTY_COLORS to here) =====
        Map<String, Color> gradients = new HashMap<>();
        gradients.put("BROWN",        new Color(0x5C3A1E));  // Dark brown
        gradients.put("LIGHT_BLUE",   new Color(0x4A90B8));  // Dark blue
        gradients.put("PINK",         new Color(0xC44A8A));  // Dark pink
        gradients.put("ORANGE",       new Color(0xCC6600));  // Dark orange
        gradients.put("RED",          new Color(0x8B0000));  // Dark red
        gradients.put("YELLOW",       new Color(0xCC9900));  // Dark yellow
        gradients.put("GREEN",        new Color(0x145214));  // Dark green
        gradients.put("BLUE",         new Color(0x000080));  // Dark blue
        gradients.put("BLACK",        new Color(0x111111));  // Deeper black
        gradients.put("LIGHT_GREEN",  new Color(0x4CAF50));  // Dark light green
        PROPERTY_GRADIENT_COLORS = Collections.unmodifiableMap(gradients);

        // ===== Wild card color selection options =====
        // Multi-Color Wild can select any property color
        String[] allColors = {
                "BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED",
                "YELLOW", "GREEN", "BLUE", "BLACK", "LIGHT_GREEN"
        };

        // Two-color wild cards can only select from their two corresponding colors
        String[] brownLightBlue  = {"BROWN", "LIGHT_BLUE"};
        String[] pinkOrange      = {"PINK", "ORANGE"};
        String[] redYellow       = {"RED", "YELLOW"};
        String[] greenBlue       = {"GREEN", "BLUE"};
        String[] blackLightGreen = {"BLACK", "LIGHT_GREEN"};

        Map<String, String[]> wildOptions = new HashMap<>();
        wildOptions.put("Multi-Color Wild",     allColors);
        wildOptions.put("Wild Property",         allColors);
        wildOptions.put("Brown/Light Blue Wild", brownLightBlue);
        wildOptions.put("Pink/Orange Wild",      pinkOrange);
        wildOptions.put("Red/Yellow Wild",       redYellow);
        wildOptions.put("Green/Blue Wild",       greenBlue);
        wildOptions.put("Black/Light Green Wild", blackLightGreen);
        // Generic default
        wildOptions.put("Wild",                  allColors);
        WILD_COLOR_OPTIONS = Collections.unmodifiableMap(wildOptions);
    }
}
