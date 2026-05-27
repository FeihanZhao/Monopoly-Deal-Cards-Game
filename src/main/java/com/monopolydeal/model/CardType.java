package com.monopolydeal.model;

/**
 * Card type enum class
 *
 * Defines the four card types in the Monopoly Deal card game:
 * - MONEY: deposited into the bank as funds, used to pay rent etc.
 * - PROPERTY: placed in the property zone to form color sets, collecting full sets wins the game
 * - RENT: collect rent from other players
 * - ACTION: execute special effects (e.g. Deal Breaker, Debt Collector, etc.)
 */
public enum CardType {
    MONEY("Money"),        // Money card - denominations: 1M/2M/3M/4M/5M/10M
    PROPERTY("Property"),  // Property card - 11 pure property colors + wild property
    RENT("Rent"),          // Rent card - collect rent from other players for specific property colors
    ACTION("Action");      // Action card - includes various special effects

    /** Display name of the type */
    private final String displayName;

    /**
     * Constructor
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
