package com.monopolydeal.model;

/**
 * Game phase enumeration.
 *
 * Defines the phases within a turn of Monopoly Deal:
 * 1. INIT: game not yet started, waiting for players to ready up
 * 2. DRAW: auto-draw phase at the start of each turn
 * 3. PLAY: player may play up to 3 cards
 * 4. WAITING_FOR_PAYMENT: waiting for debtor to select payment cards
 * 5. WAITING_FOR_REACTION: waiting for opponent response (Just Say No, etc.)
 * 6. END: turn wrap-up, auto-discard down to 7-card hand limit
 * 7. DISCARD: forced discard due to action card effects
 * 8. GAME_OVER: a player has won the game
 */
public enum GamePhase {
    INIT("Initialization"),             // Initialization phase — waiting for all players to ready up
    DRAW("Draw Phase"),                 // Draw phase — auto-draw each turn
    PLAY("Play Phase"),                 // Play phase — player plays cards (up to 3 non-action plays)
    WAITING_FOR_PAYMENT("Waiting for Payment"),   // Waiting for debtor to select payment cards
    WAITING_FOR_REACTION("Waiting for Reaction"), // Waiting for opponent response (Just Say No, etc.)
    END("End Phase"),                   // End phase — turn wrap-up, auto-discard to 7-card limit
    DISCARD("Discard Phase"),           // Discard phase — forced discard due to action card effects
    GAME_OVER("Game Over");             // Game over — a player has won

    /** Display name of the phase */
    private final String displayName;

    /**
     * Constructor.
     * @param displayName display name of the phase
     */
    GamePhase(String displayName) {
        this.displayName = displayName;
    }

    /** Get the display name of the phase */
    public String getDisplayName() {
        return displayName;
    }

    /** Return the display name string */
    @Override
    public String toString() {
        return displayName;
    }
}
