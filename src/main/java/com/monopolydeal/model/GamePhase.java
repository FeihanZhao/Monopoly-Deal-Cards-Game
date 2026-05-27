package com.monopolydeal.model;

public enum GamePhase {
    INIT("Initialization"),
    DRAW("Draw Phase"),
    PLAY("Play Phase"),
    END("End Phase"),
    DISCARD("Discard Phase"),
    WAITING_FOR_PAYMENT("Waiting for Payment"),
    WAITING_FOR_REACTION("Waiting for Reaction"),
    GAME_OVER("Game Over");

    private final String displayName;

    GamePhase(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}