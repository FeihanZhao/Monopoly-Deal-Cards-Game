package com.monopolydeal.model;

import java.util.*;

/**
 * PropertyZone class — manages a player's property card display area.
 *
 * Each player has one PropertyZone for placing and managing played property cards.
 * Property cards are grouped by color; same-color properties are automatically placed together.
 *
 * Core concepts:
 * - Complete Set: when a color group's card count >= the setSize required for that color
 * - House: can be built on a complete set; +3M rent bonus each; max 1 per set (cannot be built on Black/LightGreen)
 * - Hotel: can be built on a complete set that already has a house; +4M rent bonus (cannot be built on Black/LightGreen)
 *
 * Win condition: own 3 complete property sets (3 full sets of different colors).
 */
public class PropertyZone {
    /** Property groups by color: key=color, value=list of property cards of that color */
    private final Map<CardColor, List<Card>> propertyGroups;
    /** Flat list of all property cards */
    private final List<Card> allProperties;
    /** House count per color: key=color, value=number of houses (0-1) */
    private final Map<CardColor, Integer> houseCount;
    /** Whether each color has a hotel: key=color, value=true=has hotel */
    private final Map<CardColor, Boolean> hasHotel;

    /**
     * Constructor — initializes an empty property zone.
     * Pre-creates empty groups and building status maps for each pure property color.
     */
    public PropertyZone() {
        this.propertyGroups = new LinkedHashMap<>();  // Preserve insertion order
        this.allProperties = new ArrayList<>();
        this.houseCount = new HashMap<>();
        this.hasHotel = new HashMap<>();
        for (CardColor color : CardColor.values()) {
            if (color.isPropertyColor()) {
                propertyGroups.put(color, new ArrayList<>());
                houseCount.put(color, 0);     // Initial houses = 0
                hasHotel.put(color, false);   // Initial hotel = false
            }
        }
    }

    /**
     * Add a property card to the property zone.
     * Automatically groups by the card's effective color (for wild properties, determined by player-chosen wildColor).
     *
     * @param propertyCard must be a PROPERTY type card
     * @throws IllegalArgumentException if the card is not a property card
     */
    public void addProperty(Card propertyCard) {
        if (!propertyCard.isPropertyCard()) {
            throw new IllegalArgumentException("Only property cards can be added to property zone");
        }
        CardColor effectiveColor = propertyCard.getEffectiveColor();
        // Dynamically create the group if it doesn't exist yet (e.g. wild property chose a new color)
        if (!propertyGroups.containsKey(effectiveColor)) {
            propertyGroups.put(effectiveColor, new ArrayList<>());
            houseCount.putIfAbsent(effectiveColor, 0);
            hasHotel.putIfAbsent(effectiveColor, false);
        }
        propertyGroups.get(effectiveColor).add(propertyCard);
        allProperties.add(propertyCard);
    }

    /**
     * Remove a property card from the property zone.
     * @param propertyCard the property card to remove
     * @return true=removed successfully, false=card not found
     */
    public boolean removeProperty(Card propertyCard) {
        CardColor effectiveColor = propertyCard.getEffectiveColor();
        List<Card> group = propertyGroups.get(effectiveColor);
        if (group != null && group.remove(propertyCard)) {
            allProperties.remove(propertyCard);
            // If the group is no longer a complete set after removal, demolish buildings
            if (getPropertyCount(effectiveColor) < effectiveColor.getSetSize()) {
                houseCount.put(effectiveColor, 0);
                hasHotel.put(effectiveColor, false);
            }
            return true;
        }
        return false;
    }

    /**
     * Get the list of property cards for a given color (read-only).
     * @param color property color
     * @return list of property cards of that color
     */
    public List<Card> getPropertiesByColor(CardColor color) {
        return Collections.unmodifiableList(propertyGroups.getOrDefault(color, Collections.emptyList()));
    }

    /**
     * Get the number of property cards of a given color.
     * @param color property color
     * @return card count for that color
     */
    public int getPropertyCount(CardColor color) {
        List<Card> group = propertyGroups.get(color);
        return group != null ? group.size() : 0;
    }

    /** Get a read-only map of all color-grouped property cards */
    public Map<CardColor, List<Card>> getAllPropertyGroups() {
        return Collections.unmodifiableMap(propertyGroups);
    }

    /**
     * Get the list of colors that have complete property sets.
     * A set is "complete" when the card count for that color >= the required setSize.
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
     * Check whether a house can be placed on the complete set of the given color.
     * Conditions:
     * 1. The color must be a complete set
     * 2. The color must not already have a hotel
     * 3. House count must be below the maximum (MAX_HOUSES_PER_SET)
     *
     * @param color target color
     * @return true=can build, false=cannot
     */
    public boolean canPlaceHouse(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;
        if (color == CardColor.BLACK || color == CardColor.LIGHT_GREEN) return false;
        if (hasHotel.getOrDefault(color, false)) return false;
        // Official rule: max 1 House per completed property set
        return houseCount.getOrDefault(color, 0) == 0;
    }

    /**
     * Build a house on the complete set of the given color.
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
     * Check whether a hotel can be placed on the complete set of the given color.
     * Conditions:
     * 1. The color must be a complete set
     * 2. The color must not already have a hotel
     * 3. The house count must be at the maximum (must first have a house before building a hotel)
     *
     * @param color target color
     * @return true=can build, false=cannot
     */
    public boolean canPlaceHotel(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;
        // Official rule: needs a completed set (House not required), max 1 Hotel per set
        return !hasHotel.getOrDefault(color, false);
    }

    /**
     * Build a hotel on the complete set of the given color.
     * @param color target color
     * @throws IllegalStateException if building conditions are not met
     */
    public void addHotel(CardColor color) {
        if (!canPlaceHotel(color)) {
            throw new IllegalStateException("Cannot build hotel on " + color.getName());
        }
        // Official rule: Hotel stacks with House (+4M), House stays on the set (+3M)
        hasHotel.put(color, true);
    }

    /**
     * Calculate the rent amount for a given color.
     * Rent = base rent + house bonus (+3M each) + hotel bonus (+4M)
     *
     * @param color property color
     * @return rent amount due (unit: M / millions)
     */
    public int getRentAmount(CardColor color) {
        int baseRent = color.getRentAmount(getPropertyCount(color));
        // Official rule: House = +3M, Hotel = +4M, they stack
        int houseBonus = houseCount.getOrDefault(color, 0) * 3;
        int hotelBonus = hasHotel.getOrDefault(color, false) ? 4 : 0;
        return baseRent + houseBonus + hotelBonus;
    }

    /**
     * Change the effective color of a wild property card (hardened safety version).
     *
     * Three-step safety validation:
     * 1. Target color must be a valid pure property color
     * 2. The wild card must allow switching to the target color (dual-color wilds can't masquerade as multi-color)
     * 3. If the old group has buildings, the group must still be complete after removing this card (prevents floating buildings)
     *
     * Defensive programming: locate first, then mutate — avoids ConcurrentModificationException risk.
     *
     * @param cardId unique ID of the wild property card
     * @param newColor new property color
     * @return true=switch successful, false=validation failed
     */
    public boolean changeWildCardColor(String cardId, CardColor newColor) {
        if (!newColor.isPropertyColor()) return false;

        // Phase 1: Safe lookup of the target card (read-only traversal, no mutation)
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

        // Phase 2: Validate dual-color legality
        if (!targetCard.isColorAllowed(newColor)) return false;

        // Phase 3: Validate no floating buildings — if old group has buildings, it must still be complete after removal
        if (hasBuildings(oldColor)) {
            int remaining = getPropertyCount(oldColor) - 1;
            if (remaining < oldColor.getSetSize()) {
                return false; // Reject: would leave house/hotel floating on an incomplete set
            }
        }

        // Phase 4: Safe execution — move the card to the new color group
        propertyGroups.get(oldColor).remove(targetCard);
        allProperties.remove(targetCard);
        targetCard.setWildColor(newColor);
        addProperty(targetCard);
        return true;
    }

    /** Check whether the given color group has houses or a hotel */
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
