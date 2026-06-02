package com.monopolydeal.model;

/**
 * Card type enumeration.
 *
 * Defines the four card types in Monopoly Deal:
 * - MONEY: banked as funds, used to pay rent etc.
 * - PROPERTY: placed in property zone to form color sets; completing sets wins the game
 * - RENT: charges rent to other players
 * - ACTION: cards with special effects (e.g. Forced Deal, Debt Collector)
 */
public enum CardType {
    MONEY("Money"),        // Money card — denominations: 1M/2M/3M/4M/5M/10M
    PROPERTY("Property"),  // Property card — 10 pure property colors + wild properties
    RENT("Rent"),          // Rent card — charges rent based on a specific property color
    ACTION("Action");      // Action card — various special effects

    /** Display name of the type */
    private final String displayName;

    /**
     * Constructor.
     * @param displayName display name of the type
     */
    CardType(String displayName) {
        this.displayName = displayName;
    }

    /** Get the display name of the type */
    public String getDisplayName() {
        return displayName;
    }
}
