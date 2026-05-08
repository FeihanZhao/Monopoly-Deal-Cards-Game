package com.monopolydeal.model;

import java.util.*;

public class PropertyZone {
    private final Map<CardColor, List<Card>> propertyGroups;
    private final List<Card> allProperties;

    // House/Hotel placements
    private final Map<CardColor, Integer> houseCount;
    private final Map<CardColor, Boolean> hasHotel;

    public PropertyZone() {
        this.propertyGroups = new LinkedHashMap<>();
        this.allProperties = new ArrayList<>();
        this.houseCount = new HashMap<>();
        this.hasHotel = new HashMap<>();

        // Initialize all colors except NONE and WILD
        for (CardColor color : CardColor.values()) {
            if (color != CardColor.NONE && color != CardColor.WILD) {
                propertyGroups.put(color, new ArrayList<>());
                houseCount.put(color, 0);
                hasHotel.put(color, false);
            }
        }
    }

    public void addProperty(Card propertyCard) {
        if (!propertyCard.isPropertyCard()) {
            throw new IllegalArgumentException("Only property cards can be added to property zone");
        }

        CardColor effectiveColor = propertyCard.getEffectiveColor();
        propertyGroups.get(effectiveColor).add(propertyCard);
        allProperties.add(propertyCard);
    }

    public boolean removeProperty(Card propertyCard) {
        CardColor effectiveColor = propertyCard.getEffectiveColor();
        boolean removed = propertyGroups.get(effectiveColor).remove(propertyCard);
        if (removed) {
            allProperties.remove(propertyCard);
        }
        return removed;
    }

    public List<Card> getPropertiesByColor(CardColor color) {
        return Collections.unmodifiableList(
                propertyGroups.getOrDefault(color, Collections.emptyList()));
    }

    public int getPropertyCount(CardColor color) {
        return propertyGroups.getOrDefault(color, Collections.emptyList()).size();
    }

    public Map<CardColor, List<Card>> getAllPropertyGroups() {
        return Collections.unmodifiableMap(propertyGroups);
    }

    public List<CardColor> getCompleteSets() {
        List<CardColor> completeSets = new ArrayList<>();
        for (Map.Entry<CardColor, List<Card>> entry : propertyGroups.entrySet()) {
            CardColor color = entry.getKey();
            int count = entry.getValue().size();
            int required = color.getSetSize();

            // Count wild properties assigned to this color
            if (required > 0 && count >= required) {
                completeSets.add(color);
            }
        }
        return completeSets;
    }

    public int getCompleteSetsCount() {
        return getCompleteSets().size();
    }

    public boolean canPlaceHouse(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;
        if (hasHotel.getOrDefault(color, false)) return false;
        return houseCount.getOrDefault(color, 0) < 4; // Max 4 houses
    }

    public void addHouse(CardColor color) {
        if (!canPlaceHouse(color)) {
            throw new IllegalStateException("Cannot place house on " + color.getName());
        }
        houseCount.merge(color, 1, Integer::sum);
    }

    public boolean canPlaceHotel(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;
        if (hasHotel.getOrDefault(color, false)) return false;
        return houseCount.getOrDefault(color, 0) >= 4;
    }

    public void addHotel(CardColor color) {
        if (!canPlaceHotel(color)) {
            throw new IllegalStateException("Cannot place hotel on " + color.getName());
        }
        hasHotel.put(color, true);
    }

    public int getRentAmount(CardColor color) {
        int baseRent = color.getRentAmount(getPropertyCount(color));
        int houseBonus = houseCount.getOrDefault(color, 0) * 1; // +1 per house
        int hotelBonus = hasHotel.getOrDefault(color, false) ? 3 : 0;
        return baseRent + houseBonus + hotelBonus;
    }

    public void clear() {
        propertyGroups.values().forEach(List::clear);
        allProperties.clear();
        houseCount.replaceAll((k, v) -> 0);
        hasHotel.replaceAll((k, v) -> false);
    }
}