package com.monopolydeal.model;

public enum CardType {
    MONEY("Money"),
    PROPERTY("Property"),
    RENT("Rent"),
    ACTION("Action");

    private final String displayName;

    CardType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}