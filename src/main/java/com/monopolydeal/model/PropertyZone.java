package com.monopolydeal.model;

import java.util.*;

public class PropertyZone {
    private final Map<CardColor, List<Card>> propertyGroups;
    private final List<Card> allProperties;
    private final Map<CardColor, Integer> houseCount;
    private final Map<CardColor, Boolean> hasHotel;

    public PropertyZone() {
        this.propertyGroups = new LinkedHashMap<>();
        this.allProperties = new ArrayList<>();
        this.houseCount = new HashMap<>();
        this.hasHotel = new HashMap<>();
        for (CardColor color : CardColor.values()) {
            if (color.isPropertyColor()) {
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
        if (!propertyGroups.containsKey(effectiveColor)) {
            propertyGroups.put(effectiveColor, new ArrayList<>());
            houseCount.putIfAbsent(effectiveColor, 0);
            hasHotel.putIfAbsent(effectiveColor, false);
        }
        propertyGroups.get(effectiveColor).add(propertyCard);
        allProperties.add(propertyCard);
    }

    public boolean removeProperty(Card propertyCard) {
        CardColor effectiveColor = propertyCard.getEffectiveColor();
        List<Card> group = propertyGroups.get(effectiveColor);
        if (group != null && group.remove(propertyCard)) {
            allProperties.remove(propertyCard);
            return true;
        }
        return false;
    }

    public List<Card> getPropertiesByColor(CardColor color) {
        return Collections.unmodifiableList(propertyGroups.getOrDefault(color, Collections.emptyList()));
    }

    public int getPropertyCount(CardColor color) {
        List<Card> group = propertyGroups.get(color);
        return group != null ? group.size() : 0;
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
        // Official rule: max 1 House per completed property set
        return houseCount.getOrDefault(color, 0) == 0;
    }

    public void addHouse(CardColor color) {
        if (!canPlaceHouse(color)) {
            throw new IllegalStateException("Cannot place house on " + color.getName());
        }
        houseCount.merge(color, 1, Integer::sum);
    }

    public boolean canPlaceHotel(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;
        // Official rule: needs a completed set (House not required), max 1 Hotel per set
        return !hasHotel.getOrDefault(color, false);
    }

    public void addHotel(CardColor color) {
        if (!canPlaceHotel(color)) {
            throw new IllegalStateException("Cannot place hotel on " + color.getName());
        }
        // Official rule: Hotel stacks with House (+4M), House stays on the set (+3M)
        hasHotel.put(color, true);
    }

    public int getRentAmount(CardColor color) {
        int baseRent = color.getRentAmount(getPropertyCount(color));
        // Official rule: House = +3M, Hotel = +4M, they stack
        int houseBonus = houseCount.getOrDefault(color, 0) * 3;
        int hotelBonus = hasHotel.getOrDefault(color, false) ? 4 : 0;
        return baseRent + houseBonus + hotelBonus;
    }

    public void clear() {
        propertyGroups.values().forEach(List::clear);
        allProperties.clear();
        houseCount.replaceAll((k, v) -> 0);
        hasHotel.replaceAll((k, v) -> false);
    }

    public boolean changeWildCardColor(String cardId, CardColor newColor) {
        if (!newColor.isPropertyColor()) return false;
        for (Map.Entry<CardColor, List<Card>> entry : propertyGroups.entrySet()) {
            CardColor currentColor = entry.getKey();
            List<Card> group = entry.getValue();
            for (Card card : group) {
                if (card.getId().equals(cardId) && card.isWildProperty()) {
                    if (getCompleteSets().contains(currentColor)) return false;
                    if (hasHotel.getOrDefault(currentColor, false) ||
                            houseCount.getOrDefault(currentColor, 0) > 0) return false;
                    group.remove(card);
                    if (group.isEmpty()) {
                        propertyGroups.remove(currentColor);
                        houseCount.remove(currentColor);
                        hasHotel.remove(currentColor);
                    }
                    card.setWildColor(newColor);
                    addProperty(card);
                    return true;
                }
            }
        }
        return false;
    }

}