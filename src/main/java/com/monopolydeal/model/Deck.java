package com.monopolydeal.model;

import java.util.*;

public class Deck {
    private final Stack<Card> drawPile;
    private final List<Card> discardPile;
    /** Shared random number generator */
    private final Random random;

    public Deck() {
        this.drawPile = new Stack<>();
        this.discardPile = new ArrayList<>();
        this.random = new Random();
        initializeDeck();
        shuffle();
    }

    private void initializeDeck() {
        // Money Cards
        addCards(CardType.MONEY, CardColor.NONE, 1, 6);  // 6x 1M
        addCards(CardType.MONEY, CardColor.NONE, 2, 5);  // 5x 2M
        addCards(CardType.MONEY, CardColor.NONE, 3, 3);  // 3x 3M
        addCards(CardType.MONEY, CardColor.NONE, 4, 3);  // 3x 4M
        addCards(CardType.MONEY, CardColor.NONE, 5, 2);  // 2x 5M
        addCards(CardType.MONEY, CardColor.NONE, 10, 1); // 1x 10M

        // Property Cards (including Wild Properties)
        addCards(CardType.PROPERTY, CardColor.BROWN, 0, 2);
        addCards(CardType.PROPERTY, CardColor.LIGHT_BLUE, 0, 3);
        addCards(CardType.PROPERTY, CardColor.PINK, 0, 3);
        addCards(CardType.PROPERTY, CardColor.ORANGE, 0, 3);
        addCards(CardType.PROPERTY, CardColor.RED, 0, 3);
        addCards(CardType.PROPERTY, CardColor.YELLOW, 0, 3);
        addCards(CardType.PROPERTY, CardColor.GREEN, 0, 3);
        addCards(CardType.PROPERTY, CardColor.BLUE, 0, 2);
        addCards(CardType.PROPERTY, CardColor.PURPLE, 0, 3);
        addCards(CardType.PROPERTY, CardColor.BLACK, 0, 4);
        addCards(CardType.PROPERTY, CardColor.LIGHT_GREEN, 0, 2);

        // Wild Properties
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Multi-Color Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Dark Blue/Green Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Red/Yellow Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Brown/Light Blue Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Orange/Pink Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Railroad/Utility Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Green/Railroad Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Light Blue/Railroad Wild");

        // Rent Cards
        addCards(CardType.RENT, CardColor.BROWN_LIGHT_BLUE, 0, 2, "Brown/Light Blue Rent");
        addCards(CardType.RENT, CardColor.PINK_ORANGE, 0, 2, "Pink/Orange Rent");
        addCards(CardType.RENT, CardColor.RED_YELLOW, 0, 2, "Red/Yellow Rent");
        addCards(CardType.RENT, CardColor.GREEN_BLUE, 0, 2, "Green/Blue Rent");
        addCards(CardType.RENT, CardColor.PURPLE_ORANGE, 0, 2, "Purple/Orange Rent");
        addCards(CardType.RENT, CardColor.BLACK_LIGHT_GREEN, 0, 2, "Black/Light Green Rent");
        addCards(CardType.RENT, CardColor.WILD, 0, 3, "Wild Rent");

        // Action Cards
        addCards(CardType.ACTION, CardColor.NONE, 0, 2, "Deal Breaker");
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "Just Say No");
        addCards(CardType.ACTION, CardColor.NONE, 0, 10, "Pass Go");
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "Forced Deal");
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "Sly Deal");
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "Debt Collector");
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "Birthday");
        addCards(CardType.ACTION, CardColor.NONE, 0, 2, "Double Rent");
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "House");
        addCards(CardType.ACTION, CardColor.NONE, 0, 2, "Hotel");
    }

    private void addCards(CardType type, CardColor color, int value, int count) {
        addCards(type, color, value, count, null);
    }

    private void addCards(CardType type, CardColor color, int value, int count, String customName) {
        for (int i = 0; i < count; i++) {
            String id = UUID.randomUUID().toString().substring(0, 8);
            String name = customName != null ? customName :
                    (type == CardType.MONEY ? value + "M" : color.getName() + " " + type.getDisplayName());
            drawPile.push(new Card(id, type, name, value, color, name));
        }
    }

    public void shuffle() {
        Collections.shuffle(drawPile, random);
    }

    public Card draw() {
        if (drawPile.isEmpty()) {
            reshuffleDiscardPile();
        }
        return drawPile.isEmpty() ? null : drawPile.pop();
    }

    public List<Card> drawMultiple(int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Card card = draw();
            if (card != null) {
                cards.add(card);
            }
        }
        return cards;
    }

    public void discard(Card card) {
        discardPile.add(card);
    }

    public void discardMultiple(List<Card> cards) {
        discardPile.addAll(cards);
    }

    private void reshuffleDiscardPile() {
        if (!discardPile.isEmpty()) {
            Collections.shuffle(discardPile, random);
            drawPile.addAll(discardPile);
            discardPile.clear();
        }
    }

    public int getDrawPileSize() {
        return drawPile.size();
    }

    public int getDiscardPileSize() {
        return discardPile.size();
    }

    public boolean isEmpty() {
        return drawPile.isEmpty() && discardPile.isEmpty();
    }
}
