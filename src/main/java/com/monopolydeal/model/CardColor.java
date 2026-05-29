package com.monopolydeal.model;

public enum CardColor {
    BROWN("Brown", 2),
    LIGHT_BLUE("Light Blue", 3),
    PINK("Pink", 3),
    ORANGE("Orange", 3),
    RED("Red", 3),
    YELLOW("Yellow", 3),
    GREEN("Green", 3),
    BLUE("Blue", 2),
    PURPLE("Purple", 3),
    BLACK("Black", 4),
    LIGHT_GREEN("Light Green", 3),

    BROWN_LIGHT_BLUE("Brown/Light Blue", 0),
    PINK_ORANGE("Pink/Orange", 0),
    RED_YELLOW("Red/Yellow", 0),
    GREEN_BLUE("Green/Blue", 0),
    PURPLE_ORANGE("Purple/Orange", 0),
    BLACK_LIGHT_GREEN("Black/Light Green", 0),

    WILD("Wild", 0),
    NONE("None", 0);

    private final String name;
    private final int setSize;

    CardColor(String name, int setSize) {
        this.name = name;
        this.setSize = setSize;
    }

    public String getName() { return name; }
    public int getSetSize() { return setSize; }

    public int getRentAmount(int propertiesInSet) {
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
        if (this == PURPLE) {
            if (propertiesInSet >= 3) return 4;
            if (propertiesInSet >= 2) return 2;
            return 1;
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

    public boolean isPropertyColor() {
        return setSize > 0 && this != WILD && this != NONE
                && this != BROWN_LIGHT_BLUE && this != PINK_ORANGE
                && this != RED_YELLOW && this != GREEN_BLUE
                && this != PURPLE_ORANGE && this != BLACK_LIGHT_GREEN;
    }

    public boolean isRentColor() {
        return this == BROWN_LIGHT_BLUE || this == PINK_ORANGE
                || this == RED_YELLOW || this == GREEN_BLUE
                || this == PURPLE_ORANGE || this == BLACK_LIGHT_GREEN
                || this == WILD;
    }

    public CardColor[] getComponentColors() {
        switch (this) {
            case BROWN_LIGHT_BLUE:
                return new CardColor[]{BROWN, LIGHT_BLUE};
            case PINK_ORANGE:
                return new CardColor[]{PINK, ORANGE};
            case RED_YELLOW:
                return new CardColor[]{RED, YELLOW};
            case GREEN_BLUE:
                return new CardColor[]{GREEN, BLUE};
            case PURPLE_ORANGE:
                return new CardColor[]{PURPLE, ORANGE};
            case BLACK_LIGHT_GREEN:
                return new CardColor[]{BLACK, LIGHT_GREEN};
            default:
                return new CardColor[]{this};
        }
    }
}