package com.monopolydeal.model;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

/**
 * Card class — represents a single card in the game.
 *
 * Each card has an immutable identity (id, type, name, value, color, description)
 * and a mutable wildColor (set by the player only for wild property cards).
 *
 * Card type determines how it is used:
 * - MONEY: deposited into the bank, denominations 1M–10M
 * - PROPERTY: placed in the property zone, grouped by color
 * - RENT: charges rent to other players
 * - ACTION: executes a special effect
 *
 * Design note: Card is Cloneable; copy constructor supports testing and deep copies.
 */
public class Card implements Cloneable {
    /** Unique card identifier (first 8 chars of UUID) */
    private final String id;
    /** Card type (money / property / rent / action) */
    private final CardType type;
    /** Card name (e.g. "Deal Breaker", "5M", "Red Property") */
    private final String name;
    /** Monetary value (only valid for money cards, 0 for others) — unit: M (millions) */
    private final int value;
    /** Card color (property color / dual rent color / WILD / NONE) */
    private final CardColor color;
    /** Card description text */
    private final String description;
    /** Actual chosen color for wild property cards (non-null only for wilds, set by player when played) */
    private CardColor wildColor;

    /**
     * Primary constructor — creates a new card.
     *
     * @param id unique identifier
     * @param type card type
     * @param name card name
     * @param value monetary value (pass 0 for non-money cards)
     * @param color card color
     * @param description description text
     */
    public Card(String id, CardType type, String name, int value,
                CardColor color, String description) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.value = value;
        this.color = color;
        this.description = description;
        this.wildColor = null;  // No wild color chosen by default; set by player when placing
    }

    /**
     * Copy constructor — creates a deep copy of a card (preserving the original ID).
     * Used for testing and card cloning during GameState serialization.
     * @param other card to copy
     */
    public Card(Card other) {
        this.id = other.id;
        this.type = other.type;
        this.name = other.name;
        this.value = other.value;
        this.color = other.color;
        this.description = other.description;
        this.wildColor = other.wildColor;
    }

    /**
     * Transfer copy constructor — creates a copy with same content but a different ID.
     * Used when transferring cards between players to avoid ID collisions.
     */
    private Card(Card template, String newId) {
        this.id = newId;
        this.type = template.type;
        this.name = template.name;
        this.value = template.value;
        this.color = template.color;
        this.description = template.description;
        this.wildColor = template.wildColor;
    }

    /** Clone this card (deep copy, preserves original ID) */
    @Override
    public Card clone() {
        return new Card(this);
    }

    /** Create a transfer copy with a new ID (for cross-player transfers like payments/swaps) */
    public Card transferCopy() {
        return new Card(this, UUID.randomUUID().toString().substring(0, 8));
    }

    // ==================== Getters ====================

    public String getId() { return id; }
    public CardType getType() { return type; }
    public String getName() { return name; }
    public int getValue() { return value; }
    public CardColor getColor() { return color; }
    public String getDescription() { return description; }

    /** Get the chosen color of a wild property card (null for non-wilds) */
    public CardColor getWildColor() { return wildColor; }
    /** Set the actual color of a wild property card */
    public void setWildColor(CardColor wildColor) { this.wildColor = wildColor; }

    /**
     * Get the card's effective color.
     * Returns the chosen color for wild properties, otherwise the card's own color.
     * @return effective color
     */
    public CardColor getEffectiveColor() {
        return wildColor != null ? wildColor : color;
    }

    // ==================== Type Checks ====================

    /** Whether this is a money card (MONEY type only) */
    public boolean isMoneyCard() { return type == CardType.MONEY; }
    /** Whether it can be banked as currency (value > 0, includes action/rent cards) */
    public boolean canBeUsedAsMoney() { return value > 0; }
    /** Whether this is a property card */
    public boolean isPropertyCard() { return type == CardType.PROPERTY; }
    /** Whether this is an action card */
    public boolean isActionCard() { return type == CardType.ACTION; }
    /** Whether this is a rent card */
    public boolean isRentCard() { return type == CardType.RENT; }
    /** Whether this is a wild property card (property + WILD color) */
    public boolean isWildProperty() {
        return type == CardType.PROPERTY && color == CardColor.WILD;
    }

    // ==================== Wild Card Color Rules ====================

    /** Wild property card name → allowed color list */
    private static final Map<String, List<CardColor>> WILD_COLOR_RULES = new HashMap<>();
    static {
        WILD_COLOR_RULES.put("Multi-Color Wild", Arrays.asList(
                CardColor.BROWN, CardColor.LIGHT_BLUE, CardColor.PINK, CardColor.ORANGE,
                CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE,
                CardColor.BLACK, CardColor.LIGHT_GREEN));
        WILD_COLOR_RULES.put("Dark Blue/Green Wild",
                Arrays.asList(CardColor.BLUE, CardColor.GREEN));
        WILD_COLOR_RULES.put("Brown/Light Blue Wild",
                Arrays.asList(CardColor.BROWN, CardColor.LIGHT_BLUE));
        WILD_COLOR_RULES.put("Orange/Pink Wild",
                Arrays.asList(CardColor.ORANGE, CardColor.PINK));
        WILD_COLOR_RULES.put("Red/Yellow Wild",
                Arrays.asList(CardColor.RED, CardColor.YELLOW));
        WILD_COLOR_RULES.put("Railroad/Utility Wild",
                Arrays.asList(CardColor.BLACK, CardColor.LIGHT_GREEN));
        WILD_COLOR_RULES.put("Green/Railroad Wild",
                Arrays.asList(CardColor.GREEN, CardColor.BLACK));
        WILD_COLOR_RULES.put("Light Blue/Railroad Wild",
                Arrays.asList(CardColor.LIGHT_BLUE, CardColor.BLACK));
    }

    /**
     * Check whether this wild property card can switch to the target color.
     * Dual-color wilds can only switch between their two colors; multi-color wilds can use any property color.
     * @param targetColor target color
     * @return true=allowed, false=not in allowed range
     */
    public boolean isColorAllowed(CardColor targetColor) {
        if (!isWildProperty()) return false;
        List<CardColor> allowed = WILD_COLOR_RULES.get(name);
        if (allowed == null) {
            // Unknown wild card type: reject defensively to prevent bypass via unknown names
            return false;
        }
        return allowed.contains(targetColor);
    }

    /**
     * Get the allowed color list for this wild property card (read-only).
     * @return allowed color list, empty list for non-wilds
     */
    public List<CardColor> getAllowedColors() {
        if (!isWildProperty()) return Collections.emptyList();
        List<CardColor> allowed = WILD_COLOR_RULES.get(name);
        return allowed != null ? Collections.unmodifiableList(allowed) : Collections.emptyList();
    }

    // ==================== equals / hashCode / toString ====================

    /** Equality based on card ID */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return Objects.equals(id, card.id);
    }

    /** Hash code based on card ID */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** Generate a human-readable card description string */
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
