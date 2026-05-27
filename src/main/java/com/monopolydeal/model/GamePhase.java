package com.monopolydeal.model;

/**
 * Game phase enum class
 *
 * Defines the phases within a single turn of the Monopoly Deal card game:
 * 1. INIT (Initialization): game has not started, waiting for players to ready up
 * 2. DRAW (Draw Phase): automatically draw 3 cards at the start of a turn
 * 3. PLAY (Play Phase): player can play up to 3 cards
 * 4. END (End Phase): check if hand exceeds 7-card limit, discard if needed
 * 5. DISCARD (Discard Phase): player is forced to discard down to hand limit
 * 6. GAME_OVER (Game Over): a player has collected 3 complete property sets, game ends
 */
public enum GamePhase {
    INIT("Initialization"),  // Initialization phase - waiting for all players to ready up
    DRAW("Draw Phase"),      // Draw phase - auto-draw 3 cards per turn
    PLAY("Play Phase"),      // Play phase - player plays cards (up to 3)
    WAITING_FOR_PAYMENT("Waiting for Payment"),   // Waiting for debtor to select payment cards
    WAITING_FOR_REACTION("Waiting for Reaction"), // Waiting for opponent response (Just Say No etc.)
    END("End Phase"),        // End phase - turn wrap-up, auto-discard to 7-card limit
    DISCARD("Discard Phase"),// Discard phase - forced discard due to action card effect
    GAME_OVER("Game Over");  // Game over - a player has won

    /** Display name of the phase */
    private final String displayName;

    /**
     * Constructor
     * @param displayName display name of the phase
     */
    GamePhase(String displayName) {
        this.displayName = displayName;
    }

    /** Get the display name of the phase */
    public String getDisplayName() {
        return displayName;
    }

    /** Returns the display name string of the phase */
    @Override
    public String toString() {
        return displayName;
    }
}
