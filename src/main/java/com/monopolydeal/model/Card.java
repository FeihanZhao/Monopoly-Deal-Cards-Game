package com.monopolydeal.model;

import java.util.Objects;

public class Card implements Cloneable {
    private final String id;
    private final CardType type;
    private final String name;
    private final int value; // Monetary value for money cards
    private final CardColor color;
    private final String description;
    private CardColor wildColor; // For wild property cards

    public Card(String id, CardType type, String name, int value,
                CardColor color, String description) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.value = value;
        this.color = color;
        this.description = description;
        this.wildColor = null;
    }

    // Copy constructor for testing and cloning
    public Card(Card other) {
        this.id = other.id;
        this.type = other.type;
        this.name = other.name;
        this.value = other.value;
        this.color = other.color;
        this.description = other.description;
        this.wildColor = other.wildColor;
    }

    @Override
    public Card clone() {
        return new Card(this);
    }

    public String getId() { return id; }
    public CardType getType() { return type; }
    public String getName() { return name; }
    public int getValue() { return value; }
    public CardColor getColor() { return color; }
    public String getDescription() { return description; }

    public CardColor getWildColor() { return wildColor; }
    public void setWildColor(CardColor wildColor) { this.wildColor = wildColor; }

    public CardColor getEffectiveColor() {
        return wildColor != null ? wildColor : color;
    }

    public boolean isMoneyCard() { return type == CardType.MONEY; }
    public boolean isPropertyCard() { return type == CardType.PROPERTY; }
    public boolean isActionCard() { return type == CardType.ACTION; }
    public boolean isRentCard() { return type == CardType.RENT; }
    public boolean isWildProperty() {
        return type == CardType.PROPERTY && color == CardColor.WILD;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return Objects.equals(id, card.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (type == CardType.MONEY) {
            sb.append(" (").append(value).append("M)");
        }
        if (isWildProperty() && wildColor != null) {
            sb.append(" [").append(wildColor.getName()).append("]");
        }
        return sb.toString();
    }
}