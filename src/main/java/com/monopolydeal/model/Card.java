package com.monopolydeal.model;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;

/**
 * Game card with immutable core properties and a mutable wild color.
 * Card types: MONEY, PROPERTY, RENT, ACTION.
 * Wild property cards can be assigned a color when played.
 */
public class Card implements Cloneable {
/** Unique card ID (first 8 chars of UUID) */
    private final String id;
    /** Card type: MONEY, PROPERTY, RENT, ACTION */
    private final CardType type;
    /** Card display name */
    private final String name;
    /** Monetary value (0 for non-money cards) */
    private final int value;
    /** Base card color */
    private final CardColor color;
    /** Card effect description */
    private final String description;
    /** Assigned color for wild property cards */
    private CardColor wildColor;

   /**
     * Create a new card
     * @param id Unique ID
     * @param type Card type
     * @param name Display name
     * @param value Money value (0 if not money)
     * @param color Base color
     * @param description Effect text
     */
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

     /**
     * Copy constructor (deep copy)
     * @param other Card to copy
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

    /** Create and return a deep copy of this card */
    @Override
    public Card clone() {
        return new Card(this);
    }

    // ==================== Getters ====================

    public String getId() { return id; }
    public CardType getType() { return type; }
    public String getName() { return name; }
    public int getValue() { return value; }
    public CardColor getColor() { return color; }
    public String getDescription() { return description; }

    /** Get assigned wild color (null if not wild) */
    public CardColor getWildColor() { return wildColor; }
    /** Set color for wild property card */
    public void setWildColor(CardColor wildColor) { this.wildColor = wildColor; }

     /**
     * Get effective color (wild color if set, else base color)
     * @return Active color for gameplay
     */
    public CardColor getEffectiveColor() {
        return wildColor != null ? wildColor : color;
    }

    // ==================== Type Checks ====================

    /** Check if this is a dedicated money card */
    public boolean isMoneyCard() { return type == CardType.MONEY; }
    /** Check if card can be used as currency (value > 0) */
    public boolean canBeUsedAsMoney() { return value > 0; }
    /** Check if this is a property card */
    public boolean isPropertyCard() { return type == CardType.PROPERTY; }
    /** Check if this is an action card */
    public boolean isActionCard() { return type == CardType.ACTION; }
    /** Check if this is a rent card */
    public boolean isRentCard() { return type == CardType.RENT; }
    /** Check if this is a wild property card */
    public boolean isWildProperty() {
        return type == CardType.PROPERTY && color == CardColor.WILD;
    }

    // ==================== Wild Color Rules ====================

    /** Wild card name → allowed color list */
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
     * Validate if target color is allowed for this wild card
     * @param targetColor Color to check
     * @return true if allowed
     */
    public boolean isColorAllowed(CardColor targetColor) {
        if (!isWildProperty()) return false;
        List<CardColor> allowed = WILD_COLOR_RULES.get(name);
        if (allowed == null) {
            return false;
        }
        return allowed.contains(targetColor);
    }

   /**
     * Get unmodifiable list of allowed colors for wild card
     * @return Allowed colors (empty if not wild)
     */
    public List<CardColor> getAllowedColors() {
        if (!isWildProperty()) return Collections.emptyList();
        List<CardColor> allowed = WILD_COLOR_RULES.get(name);
        return allowed != null ? Collections.unmodifiableList(allowed) : Collections.emptyList();
    }

    // ==================== equals / hashCode / toString ====================

    /** Equals based on unique card ID */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return Objects.equals(id, card.id);
    }

    /** Hash code based on unique card ID */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** Human-readable card string */
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
