package com.monopolydeal.view;

import java.awt.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Advanced Visual Theme System for Monopoly Deal Card Game
 *
 * This class centrally manages all color resources for game UI and card rendering.
 * It extends basic color schemes with gradient, neon glow, hover, border, text contrast
 * and global UI theme colors, supporting rich visual effects and interactive styles.
 * All color keys are consistent with CardColor enum to ensure compatibility.
 */
public final class AppTheme {

    /**
     * Private constructor to prevent instantiation of utility class
     */
    private AppTheme() {}

    /** Standard base color for each property card */
    public static final Map<String, Color> PROPERTY_COLORS;

    /** Gradient end color for card vertical gradient rendering (top -> bottom) */
    public static final Map<String, Color> PROPERTY_GRADIENT_COLORS;

    /** Neon glow color for highlight, selection and special visual effects */
    public static final Map<String, Color> NEON_GLOW_COLORS;

    /** Contrast text color, ensures text readability on different colored backgrounds */
    public static final Map<String, Color> TEXT_CONTRAST_COLORS;

    /** Border stroke color for card outline decoration */
    public static final Map<String, Color> BORDER_COLORS;

    /** Highlight color when mouse hovers over cards */
    public static final Map<String, Color> HOVER_LIGHT_COLORS;

    /** Available color options for wild cards, used in color selection dialog */
    public static final Map<String, String[]> WILD_COLOR_OPTIONS;

    /**
     * Global UI Theme Container
     * Contains background, text, accent, shadow and dialog colors for overall game interface
     */
    public static final class UI {
        /** Main game background color (dark style) */
        public static final Color BACKGROUND_DARK  = new Color(0x121212);
        /** Sub-panel background color */
        public static final Color BACKGROUND_PANEL = new Color(0x1E1E1E);
        /** Primary accent color for buttons and key elements */
        public static final Color ACCENT_PRIMARY    = new Color(0xFF4081);
        /** Secondary accent color for decoration and hints */
        public static final Color ACCENT_SECONDARY  = new Color(0x00E5FF);
        /** Main text color */
        public static final Color TEXT_MAIN         = new Color(0xFFFFFF);
        /** Secondary / hint text color */
        public static final Color TEXT_SECONDARY    = new Color(0xB3B3B3);
        /** Card shadow color for stereo effect */
        public static final Color CARD_SHADOW       = new Color(0,0,0, 180);
        /** Popup dialog background color */
        public static final Color DIALOG_BG         = new Color(0x2A2A2A);
    }

    static {
        // Initialize base property colors
        Map<String, Color> colors = new HashMap<>();
        colors.put("BROWN",        new Color(0xAC7B4D));
        colors.put("LIGHT_BLUE",   new Color(0x64B5F6));
        colors.put("PINK",         new Color(0xEC407A));
        colors.put("ORANGE",       new Color(0xFF9800));
        colors.put("RED",          new Color(0xF44336));
        colors.put("YELLOW",       new Color(0xFFEB3B));
        colors.put("GREEN",        new Color(0x4CAF50));
        colors.put("BLUE",         new Color(0x2196F3));
        colors.put("BLACK",        new Color(0x424242));
        colors.put("LIGHT_GREEN",  new Color(0x81C784));
        PROPERTY_COLORS = Collections.unmodifiableMap(colors);

        // Initialize gradient end colors for cards
        Map<String, Color> gradients = new HashMap<>();
        gradients.put("BROWN",        new Color(0x79553D));
        gradients.put("LIGHT_BLUE",   new Color(0x2196F3));
        gradients.put("PINK",         new Color(0xD81B60));
        gradients.put("ORANGE",       new Color(0xE65100));
        gradients.put("RED",          new Color(0xC62828));
        gradients.put("YELLOW",       new Color(0xFBC02D));
        gradients.put("GREEN",        new Color(0x2E7D32));
        gradients.put("BLUE",         new Color(0x0D47A1));
        gradients.put("BLACK",        new Color(0x212121));
        gradients.put("LIGHT_GREEN",  new Color(0x4CAF50));
        PROPERTY_GRADIENT_COLORS = Collections.unmodifiableMap(gradients);

        // Initialize neon glow effect colors
        Map<String, Color> neon = new HashMap<>();
        neon.put("BROWN",        new Color(0xFFB74D));
        neon.put("LIGHT_BLUE",   new Color(0x80D8FF));
        neon.put("PINK",         new Color(0xFF80AB));
        neon.put("ORANGE",       new Color(0xFFAB40));
        neon.put("RED",          new Color(0xFF5252));
        neon.put("YELLOW",       new Color(0xFFFF00));
        neon.put("GREEN",        new Color(0x69F0AE));
        neon.put("BLUE",         new Color(0x40C4FF));
        neon.put("BLACK",        new Color(0xBDBDBD));
        neon.put("LIGHT_GREEN",  new Color(0xB9F6CA));
        NEON_GLOW_COLORS = Collections.unmodifiableMap(neon);

        // Initialize text contrast colors for readability
        Map<String, Color> text = new HashMap<>();
        text.put("BROWN",        Color.WHITE);
        text.put("LIGHT_BLUE",   Color.BLACK);
        text.put("PINK",         Color.WHITE);
        text.put("ORANGE",       Color.BLACK);
        text.put("RED",          Color.WHITE);
        text.put("YELLOW",       Color.BLACK);
        text.put("GREEN",        Color.WHITE);
        text.put("BLUE",         Color.WHITE);
        text.put("BLACK",        Color.WHITE);
        text.put("LIGHT_GREEN",  Color.BLACK);
        TEXT_CONTRAST_COLORS = Collections.unmodifiableMap(text);

        // Initialize card border colors
        Map<String, Color> border = new HashMap<>();
        border.put("BROWN",        new Color(0x5D4037));
        border.put("LIGHT_BLUE",   new Color(0x0277BD));
        border.put("PINK",         new Color(0x880E4F));
        border.put("ORANGE",       new Color(0xE65100));
        border.put("RED",          new Color(0xB71C1C));
        border.put("YELLOW",       new Color(0xF57F17));
        border.put("GREEN",        new Color(0x1B5E20));
        border.put("BLUE",         new Color(0x0D47A1));
        border.put("BLACK",        new Color(0x000000));
        border.put("LIGHT_GREEN",  new Color(0x2E7D32));
        BORDER_COLORS = Collections.unmodifiableMap(border);

        // Initialize mouse hover highlight colors
        Map<String, Color> hover = new HashMap<>();
        hover.put("BROWN",        new Color(0xD4A868));
        hover.put("LIGHT_BLUE",   new Color(0xA4CDF7));
        hover.put("PINK",         new Color(0xF48FB1));
        hover.put("ORANGE",       new Color(0xFFB74D));
        hover.put("RED",          new Color(0xE57373));
        hover.put("YELLOW",       new Color(0xFFF590));
        hover.put("GREEN",        new Color(0x81C784));
        hover.put("BLUE",         new Color(0x64B5F6));
        hover.put("BLACK",        new Color(0x757575));
        hover.put("LIGHT_GREEN",  new Color(0xA5D6A7));
        HOVER_LIGHT_COLORS = Collections.unmodifiableMap(hover);

        // Initialize color selection rules for all wild cards
        String[] allColors = {
            "BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED",
            "YELLOW", "GREEN", "BLUE", "BLACK", "LIGHT_GREEN"
        };
        String[] brownLightBlue  = {"BROWN", "LIGHT_BLUE"};
        String[] pinkOrange      = {"PINK", "ORANGE"};
        String[] redYellow       = {"RED", "YELLOW"};
        String[] greenBlue       = {"GREEN", "BLUE"};
        String[] blackLightGreen = {"BLACK", "LIGHT_GREEN"};

        Map<String, String[]> wildOptions = new HashMap<>();
        wildOptions.put("Multi-Color Wild",     allColors);
        wildOptions.put("Wild Property",        allColors);
        wildOptions.put("Brown/Light Blue Wild",brownLightBlue);
        wildOptions.put("Pink/Orange Wild",     pinkOrange);
        wildOptions.put("Red/Yellow Wild",      redYellow);
        wildOptions.put("Green/Blue Wild",      greenBlue);
        wildOptions.put("Black/Light Green Wild",blackLightGreen);
        wildOptions.put("Wild",                 allColors);
        WILD_COLOR_OPTIONS = Collections.unmodifiableMap(wildOptions);
    }
}
