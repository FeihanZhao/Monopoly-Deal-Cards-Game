package com.monopolydeal.model;

/**
 * Card color enum class
 *
 * Defines all card color types in the Monopoly Deal card game, divided into three categories:
 * 1. Pure property colors (BROWN ~ LIGHT_GREEN): used to form complete property sets
 * 2. Dual-color rent colors (BROWN_LIGHT_BLUE ~ BLACK_LIGHT_GREEN): used by rent cards
 * 3. Special colors (WILD, NONE): wild cards and no-color cards
 *
 * Each pure property color has a corresponding setSize (number of cards needed for a complete set),
 * and a corresponding rent calculation rule.
 */
public enum CardColor {
    // ==================== Pure Property Colors ====================
    // Format: color(display name, cards needed for a complete set)
    BROWN("Brown", 2),               // Brown - 2 cards for a complete set
    LIGHT_BLUE("Light Blue", 3),     // Light Blue - 3 cards for a complete set
    PINK("Pink", 3),                 // Pink - 3 cards for a complete set
    ORANGE("Orange", 3),             // Orange - 3 cards for a complete set
    RED("Red", 3),                   // Red - 3 cards for a complete set
    YELLOW("Yellow", 3),             // Yellow - 3 cards for a complete set
    GREEN("Green", 3),               // Green - 3 cards for a complete set
    BLUE("Blue", 2),                 // Blue - 2 cards for a complete set
    BLACK("Black", 4),               // Black - 4 cards for a complete set
    LIGHT_GREEN("Light Green", 2),   // Light Green - 2 cards for a complete set (Utilities)

    // ==================== Dual-Color Rent Colors ====================
    // Used by rent cards, indicates rent can be collected for properties of these two colors
    BROWN_LIGHT_BLUE("Brown/Light Blue", 0),
    PINK_ORANGE("Pink/Orange", 0),
    RED_YELLOW("Red/Yellow", 0),
    GREEN_BLUE("Green/Blue", 0),
    BLACK_LIGHT_GREEN("Black/Light Green", 0),

    // ==================== Special Colors ====================
    WILD("Wild", 0),    // Wild color - used by wild property and wild rent cards
    NONE("None", 0);    // No color - used by money and action cards

    /** Display name of the color */
    private final String name;
    /** Number of cards needed for a complete property set (pure property colors > 0, dual/special = 0) */
    private final int setSize;

    /**
     * Constructor
     * @param name display name
     * @param setSize number of cards needed for a complete set (0 for non-pure-property colors)
     */
    CardColor(String name, int setSize) {
        this.name = name;
        this.setSize = setSize;
    }

    /** Get the display name of this color */
    public String getName() { return name; }
    /** Get the number of cards needed for a complete property set */
    public int getSetSize() { return setSize; }

    /**
     * Calculate rent amount based on the number of properties of this color held
     *
     * Rent rules per color (from official Monopoly Deal rules):
     * - Brown/Light Blue: 1 card=1M, 2+ cards=2M
     * - Pink/Orange: 1 card=1M, 2+ cards=3M
     * - Red/Yellow: 1 card=2M, 2 cards=4M, 3+ cards=6M
     * - Green: 1 card=2M, 2 cards=4M, 3+ cards=7M
     * - Blue: 1 card=3M, 2+ cards=8M
     * - Black: 1 card=1M, 2 cards=2M, 3 cards=3M, 4+ cards=5M
     * - Light Green: 1 card=1M, 2 cards=2M, 3+ cards=4M
     *
     * @param propertiesInSet number of properties of this color currently held
     * @return rent amount to collect (in M/millions)
     */
    public int getRentAmount(int propertiesInSet) {
        if (propertiesInSet <= 0) return 0;
        if (this == BROWN || this == LIGHT_BLUE) {
            return propertiesInSet >= 2 ? 2 : 1;
        }
        if (this == PINK || this == ORANGE) {
            return propertiesInSet >= 2 ? 3 : 1;
        }
        if (this == RED || this == YELLOW) {
            if (propertiesInSet >= 3) return 6;
            if (propertiesInSet >= 2) return 4;
            return 2;
        }
        if (this == GREEN) {
            if (propertiesInSet >= 3) return 7;
            if (propertiesInSet >= 2) return 4;
            return 2;
        }
        if (this == BLUE) {
            return propertiesInSet >= 2 ? 8 : 3;
        }
        if (this == BLACK) {
            if (propertiesInSet >= 4) return 5;
            if (propertiesInSet >= 3) return 3;
            if (propertiesInSet >= 2) return 2;
            return 1;
        }
        if (this == LIGHT_GREEN) {
            if (propertiesInSet >= 3) return 4;
            if (propertiesInSet >= 2) return 2;
            return 1;
        }
        return 0;
    }

    /**
     * Check whether this color is a pure property color (can hold property cards)
     * Excludes dual-color rent, WILD, and NONE
     * @return true=can hold property cards, false=cannot
     */
    public boolean isPropertyColor() {
        return setSize > 0 && this != WILD && this != NONE
                && this != BROWN_LIGHT_BLUE && this != PINK_ORANGE
                && this != RED_YELLOW && this != GREEN_BLUE
                && this != BLACK_LIGHT_GREEN;
    }

    /**
     * Check whether this color is a rent card color (dual-color combination or wild)
     * Rent cards use dual-color combinations to indicate rent can be collected for those property colors
     * @return true=is a rent card color, false=is not
     */
    public boolean isRentColor() {
        return this == BROWN_LIGHT_BLUE || this == PINK_ORANGE
                || this == RED_YELLOW || this == GREEN_BLUE
                || this == BLACK_LIGHT_GREEN
                || this == WILD;
    }

    /**
     * Get the two pure property colors corresponding to a dual-color rent card
     * Only valid for dual-color rent colors, returns an array of length 2
     * @return the two component colors, empty array for non-dual-color rent colors
     */
    public CardColor[] getComponentColors() {
        switch (this) {
            case BROWN_LIGHT_BLUE:   return new CardColor[]{BROWN, LIGHT_BLUE};
            case PINK_ORANGE:        return new CardColor[]{PINK, ORANGE};
            case RED_YELLOW:         return new CardColor[]{RED, YELLOW};
            case GREEN_BLUE:         return new CardColor[]{GREEN, BLUE};
            case BLACK_LIGHT_GREEN:  return new CardColor[]{BLACK, LIGHT_GREEN};
            default:                 return new CardColor[0];
        }
    }
}
