package com.monopolydeal.model;

/**
 * Card color enumeration.
 *
 * Defines all card color types in Monopoly Deal, grouped into three categories:
 * 1. Pure property colors (BROWN ~ LIGHT_GREEN): colors that can form complete property sets
 * 2. Dual-color rent colors (BROWN_LIGHT_BLUE ~ BLACK_LIGHT_GREEN): dual-color combos used by rent cards
 * 3. Special colors (WILD, NONE): wild cards and colorless cards
 *
 * Each pure property color has a corresponding setSize (number of cards needed for a complete set)
 * and rent calculation rules.
 */
public enum CardColor {
    // ==================== Pure Property Colors ====================
    // Format: COLOR(displayName, cardsNeededForFullSet)
    BROWN("Brown", 2),               // Brown — 2 cards to complete a set
    LIGHT_BLUE("Light Blue", 3),     // Light Blue — 3 cards to complete a set
    PINK("Pink", 3),                 // Pink — 3 cards to complete a set
    ORANGE("Orange", 3),             // Orange — 3 cards to complete a set
    RED("Red", 3),                   // Red — 3 cards to complete a set
    YELLOW("Yellow", 3),             // Yellow — 3 cards to complete a set
    GREEN("Green", 3),               // Green — 3 cards to complete a set
    BLUE("Blue", 2),                 // Blue — 2 cards to complete a set
    BLACK("Black", 4),               // Black — 4 cards to complete a set
    LIGHT_GREEN("Light Green", 2),   // Light Green — 2 cards to complete a set (utilities)

    // ==================== Dual-Color Rent Colors ====================
    // Used by rent cards; indicates the card can charge rent on properties of either color
    BROWN_LIGHT_BLUE("Brown/Light Blue", 0),
    PINK_ORANGE("Pink/Orange", 0),
    RED_YELLOW("Red/Yellow", 0),
    GREEN_BLUE("Green/Blue", 0),
    BLACK_LIGHT_GREEN("Black/Light Green", 0),

    // ==================== Special Colors ====================
    WILD("Wild", 0),    // Wild — used by wild property cards and wild rent cards
    NONE("None", 0);    // None — used by money cards and action cards

    /** Display name of the color */
    private final String name;
    /** Number of cards needed to complete a set (positive for pure property colors, 0 for dual/special) */
    private final int setSize;

    /**
     * Constructor.
     * @param name display name
     * @param setSize cards needed for a complete set (0 for non-pure-property colors)
     */
    CardColor(String name, int setSize) {
        this.name = name;
        this.setSize = setSize;
    }

    /** Get the display name of the color */
    public String getName() { return name; }
    /** Get the number of cards needed to complete a set */
    public int getSetSize() { return setSize; }

    /**
     * Calculate rent amount based on the number of properties owned in this color.
     *
     * Rent rules are per the official Monopoly Deal rules:
     * - Brown / Light Blue: 1 card=1M, 2+=2M
     * - Pink / Orange:      1 card=1M, 2+=3M
     * - Red / Yellow:       1 card=2M, 2=4M, 3+=6M
     * - Green:              1 card=2M, 2=4M, 3+=7M
     * - Blue:               1 card=3M, 2+=8M
     * - Black:              1 card=1M, 2=2M, 3=3M, 4+=5M
     * - Light Green:        1 card=1M, 2=2M, 3+=4M
     *
     * @param propertiesInSet number of properties owned in this color
     * @return rent amount due (unit: M / millions)
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
     * Whether this is a pure property color (can hold property cards).
     * Excludes dual-color rent colors, WILD, and NONE.
     * @return true=can hold properties, false=cannot
     */
    public boolean isPropertyColor() {
        return setSize > 0 && this != WILD && this != NONE
                && this != BROWN_LIGHT_BLUE && this != PINK_ORANGE
                && this != RED_YELLOW && this != GREEN_BLUE
                && this != BLACK_LIGHT_GREEN;
    }

    /**
     * Whether this is a rent card color (dual-color combo or wild).
     * Rent cards use dual-color combos to charge rent on either of the two property colors.
     * @return true=is a rent color, false=not
     */
    public boolean isRentColor() {
        return this == BROWN_LIGHT_BLUE || this == PINK_ORANGE
                || this == RED_YELLOW || this == GREEN_BLUE
                || this == BLACK_LIGHT_GREEN
                || this == WILD;
    }

    /**
     * Get the two pure property colors corresponding to this dual-color rent card.
     * Only valid for dual-color rent colors; returns a 2-element array.
     * @return two component colors; empty array for non-dual-color values
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
