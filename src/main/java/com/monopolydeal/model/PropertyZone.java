package com.monopolydeal.model;

import java.util.*;

/**
 * Property zone class - manages a player's property card display area
 *
 * Each player owns a PropertyZone for placing and managing played property cards.
 * Property cards are grouped by color; cards of the same color are automatically grouped together.
 *
 * Core concepts:
 * - Complete Set: when the number of cards in a color group >= the setSize required for that color
 * - House: can be built on a complete set, +3M rent bonus per house, max 1 (cannot be built on Black/LightGreen)
 * - Hotel: can only be built on a complete set that already has a house, +4M rent bonus (cannot be built on Black/LightGreen)
 *
 * Win condition: own 3 complete property sets (collect 3 full sets of different colors).
 */
public class PropertyZone {
    /** Property groups by color: key=color, value=list of property cards with that color */
    private final Map<CardColor, List<Card>> propertyGroups;
    /** Flat list of all property cards */
    private final List<Card> allProperties;
    /** House count per color: key=color, value=number of houses (0-1) */
    private final Map<CardColor, Integer> houseCount;
    /** Whether each color has a hotel: key=color, value=true=has hotel */
    private final Map<CardColor, Boolean> hasHotel;

    /**
     * Constructor - initialize an empty property zone
     * Pre-creates empty groups and house/hotel state maps for each pure property color
     */
    public PropertyZone() {
        this.propertyGroups = new LinkedHashMap<>();  // Preserve insertion order
        this.allProperties = new ArrayList<>();
        this.houseCount = new HashMap<>();
        this.hasHotel = new HashMap<>();
        for (CardColor color : CardColor.values()) {
            if (color.isPropertyColor()) {
                propertyGroups.put(color, new ArrayList<>());
                houseCount.put(color, 0);     // Initial house count = 0
                hasHotel.put(color, false);   // Initially no hotel
            }
        }
    }

    /**
     * Add a property card to the property zone
     * Groups automatically by the card's effective color (for wild property cards, determined by the player's selected wildColor)
     *
     * @param propertyCard must be a property card type
     * @throws IllegalArgumentException if the card is not a property card
     */
    public void addProperty(Card propertyCard) {
        if (!propertyCard.isPropertyCard()) {
            throw new IllegalArgumentException("Only property cards can be added to the property zone");
        }
        CardColor effectiveColor = propertyCard.getEffectiveColor();
        // Dynamically create the color group if it doesn't exist yet (wild property card chose a new color)
        if (!propertyGroups.containsKey(effectiveColor)) {
            propertyGroups.put(effectiveColor, new ArrayList<>());
            houseCount.putIfAbsent(effectiveColor, 0);
            hasHotel.putIfAbsent(effectiveColor, false);
        }
        propertyGroups.get(effectiveColor).add(propertyCard);
        allProperties.add(propertyCard);
    }

    /**
     * Remove a property card from the property zone
     * @param propertyCard the property card to remove
     * @return true=removed successfully, false=card not found
     */
    public boolean removeProperty(Card propertyCard) {
        CardColor effectiveColor = propertyCard.getEffectiveColor();
        List<Card> group = propertyGroups.get(effectiveColor);
        if (group != null && group.remove(propertyCard)) {
            allProperties.remove(propertyCard);
            // If the set is no longer complete after removal, auto-demolish buildings
            if (getPropertyCount(effectiveColor) < effectiveColor.getSetSize()) {
                houseCount.put(effectiveColor, 0);
                hasHotel.put(effectiveColor, false);
            }
            return true;
        }
        return false;
    }

    /**
     * Get the list of property cards of a specified color (read-only)
     * @param color property color
     * @return list of property cards with that color
     */
    public List<Card> getPropertiesByColor(CardColor color) {
        return Collections.unmodifiableList(propertyGroups.getOrDefault(color, Collections.emptyList()));
    }

    /**
     * Get the number of property cards of a specified color
     * @param color property color
     * @return number of cards with that color
     */
    public int getPropertyCount(CardColor color) {
        List<Card> group = propertyGroups.get(color);
        return group != null ? group.size() : 0;
    }

    /** Get a read-only map of all property color groups */
    public Map<CardColor, List<Card>> getAllPropertyGroups() {
        return Collections.unmodifiableMap(propertyGroups);
    }

    /**
     * Get the list of colors with completed property sets
     * A set is "complete" when the card count for a color >= the required setSize for that color
     *
     * @return list of colors with complete sets
     */
    public List<CardColor> getCompleteSets() {
        List<CardColor> completeSets = new ArrayList<>();
        for (Map.Entry<CardColor, List<Card>> entry : propertyGroups.entrySet()) {
            CardColor color = entry.getKey();
            int count = entry.getValue().size();
            int required = color.getSetSize();
            if (required > 0 && count >= required) {
                completeSets.add(color);
            }
        }
        return completeSets;
    }

    /** Get the number of complete property sets */
    public int getCompleteSetsCount() {
        return getCompleteSets().size();
    }

    /**
     * Check whether a house can be built on a complete set of the specified color
     * Conditions:
     * 1. The color must be a complete set
     * 2. No hotel has been built on this color yet
     * 3. House count has not reached the limit (MAX_HOUSES_PER_SET)
     *
     * @param color target color
     * @return true=can build, false=cannot build
     */
    public boolean canPlaceHouse(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;
        if (color == CardColor.BLACK || color == CardColor.LIGHT_GREEN) return false;
        if (hasHotel.getOrDefault(color, false)) return false;
        return houseCount.getOrDefault(color, 0) < GameConstants.MAX_HOUSES_PER_SET;
    }

    /**
     * Build a house on a complete set of the specified color
     * @param color target color
     * @throws IllegalStateException if building conditions are not met
     */
    public void addHouse(CardColor color) {
        if (!canPlaceHouse(color)) {
            throw new IllegalStateException("Cannot build house on " + color.getName());
        }
        houseCount.merge(color, 1, Integer::sum);
    }

    /**
     * Check whether a hotel can be built on a complete set of the specified color
     * Conditions:
     * 1. The color must be a complete set
     * 2. No hotel has been built on this color yet
     * 3. House count has reached the limit (a house must exist before a hotel can be built)
     *
     * @param color target color
     * @return true=can build, false=cannot build
     */
    public boolean canPlaceHotel(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;
        if (color == CardColor.BLACK || color == CardColor.LIGHT_GREEN) return false;
        if (hasHotel.getOrDefault(color, false)) return false;
        return houseCount.getOrDefault(color, 0) >= GameConstants.MAX_HOUSES_PER_SET;
    }

    /**
     * Build a hotel on a complete set of the specified color
     * @param color target color
     * @throws IllegalStateException if building conditions are not met
     */
    public void addHotel(CardColor color) {
        if (!canPlaceHotel(color)) {
            throw new IllegalStateException("Cannot build hotel on " + color.getName());
        }
        houseCount.put(color, 0);  // Official rule: Hotel replaces House, House is discarded
        hasHotel.put(color, true);
    }

    /**
     * Calculate rent income for the specified color
     * Rent = base rent + house bonus (+3M each) + hotel bonus (+4M)
     *
     * @param color property color
     * @return rent amount to collect (in M/millions)
     */
    public int getRentAmount(CardColor color) {
        int baseRent = color.getRentAmount(getPropertyCount(color));  // Base rent (based on count held)
        int houseBonus = houseCount.getOrDefault(color, 0) * 3;       // +3M per house
        int hotelBonus = hasHotel.getOrDefault(color, false) ? 4 : 0; // +4M for hotel
        return baseRent + houseBonus + hotelBonus;
    }

    /**
     * Change the effective color of a wild property card (hardened version)
     *
     * Three-step safety checks:
     * 1. Target color must be a valid pure property color
     * 2. The wild card must be allowed to switch to the target color (dual-color cards cannot impersonate multi-color)
     * 3. If the original color group has buildings, removing this card must still leave the set complete (prevents dangling buildings)
     *
     * Defensive programming: no direct remove during iteration; locate first, then operate to avoid CME risk.
     *
     * @param cardId unique ID of the wild property card
     * @param newColor new property color
     * @return true=color changed successfully, false=validation failed
     */
    public boolean changeWildCardColor(String cardId, CardColor newColor) {
        if (!newColor.isPropertyColor()) return false;

        // Phase 1: safely locate the target card (read-only iteration, no modifications)
        Card targetCard = null;
        CardColor oldColor = null;

        for (Map.Entry<CardColor, List<Card>> entry : propertyGroups.entrySet()) {
            for (Card card : entry.getValue()) {
                if (card.getId().equals(cardId) && card.isWildProperty()) {
                    targetCard = card;
                    oldColor = entry.getKey();
                    break;
                }
            }
            if (targetCard != null) break;
        }

        if (targetCard == null) return false;

        // Phase 2: validate dual-color legality
        if (!targetCard.isColorAllowed(newColor)) return false;

        // Phase 3: validate dangling building risk - if old group has buildings, set must remain complete after removal
        if (hasBuildings(oldColor)) {
            int remaining = getPropertyCount(oldColor) - 1;
            if (remaining < oldColor.getSetSize()) {
                return false; // Rejected: would leave house/hotel dangling on an incomplete set
            }
        }

        // Phase 4: safely execute the color transfer
        propertyGroups.get(oldColor).remove(targetCard);
        allProperties.remove(targetCard);
        targetCard.setWildColor(newColor);
        addProperty(targetCard);
        return true;
    }

    /** Check whether the specified color group has any houses or hotels */
    private boolean hasBuildings(CardColor color) {
        return houseCount.getOrDefault(color, 0) > 0
                || hasHotel.getOrDefault(color, false);
    }

    /** Clear the property zone (remove all property cards, houses, and hotels) */
    public void clear() {
        propertyGroups.values().forEach(List::clear);
        allProperties.clear();
        houseCount.replaceAll((k, v) -> 0);
        hasHotel.replaceAll((k, v) -> false);
    }
}
