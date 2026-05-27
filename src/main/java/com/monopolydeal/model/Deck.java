package com.monopolydeal.model;

import java.util.*;

/**
 * Deck class - manages the draw pile and discard pile in the game
 *
 * Responsible for card initialization, shuffling, drawing, discarding, and reshuffling the discard pile.
 *
 * The initial deck composition follows the official 106-card configuration:
 * - Money cards (20): 10M x1, 5M x2, 4M x3, 3M x3, 2M x5, 1M x6
 * - Standard property cards (28): Brown x2, Light Blue x3, Pink x3, Orange x3, Red x3, Yellow x3, Green x3, Dark Blue x2, Railroad x4, Utility x2
 * - Wild property cards (11): various dual/multi-color combinations
 * - Rent cards (13): dual-color x10 (1M each) + wild x3 (3M each)
 * - Action cards (34): all have face values, can be deposited into bank
 *
 * When the draw pile is empty, the discard pile is automatically reshuffled into the draw pile.
 */
public class Deck {
    /** Draw pile (stack structure, draw from top) */
    private final Stack<Card> drawPile;
    /** Discard pile (list structure, reshuffled into draw pile when needed) */
    private final List<Card> discardPile;
    /** Shared random number generator */
    private final Random random;

    /**
     * Constructor - initializes the deck and shuffles
     * Pre-creates all cards according to official rules
     */
    public Deck() {
        this.drawPile = new Stack<>();
        this.discardPile = new ArrayList<>();
        this.random = new Random();
        initializeDeck();  // Create all cards per rules
        shuffle();         // Initial shuffle
    }

    /**
     * Initialize the full 106-card official deck
     *
     * Card composition (strictly per official configuration):
     * - Money cards (20): 10M x1, 5M x2, 4M x3, 3M x3, 2M x5, 1M x6
     * - Standard property cards (28): Brown x2, Light Blue x3, Pink x3, Orange x3, Red x3, Yellow x3, Green x3, Dark Blue x2, Railroad x4, Utility x2
     * - Wild property cards (11): Dark Blue/Green x1, Brown/Light Blue x1, Pink/Orange x2, Red/Yellow x2, Railroad/Utility x1, Green/Railroad x1, Light Blue/Railroad x1, Multi-Color x2
     * - Rent cards (13): dual-color rent x10 (1M each), wild rent x3 (3M each)
     * - Action cards (34): Deal Breaker 5M x2, Just Say No 4M x3, Sly Deal 3M x3, Forced Deal 3M x3, Debt Collector 3M x3, Birthday 2M x3, Pass Go 1M x10, Double Rent 1M x2, House 3M x3, Hotel 4M x2
     */
    private void initializeDeck() {
        // ===== Money cards (20) =====
        addCards(CardType.MONEY, CardColor.NONE, 10, 1);
        addCards(CardType.MONEY, CardColor.NONE, 5, 2);
        addCards(CardType.MONEY, CardColor.NONE, 4, 3);
        addCards(CardType.MONEY, CardColor.NONE, 3, 3);
        addCards(CardType.MONEY, CardColor.NONE, 2, 5);
        addCards(CardType.MONEY, CardColor.NONE, 1, 6);

        // ===== Standard property cards (28) =====
        addCards(CardType.PROPERTY, CardColor.BROWN, 0, 2);
        addCards(CardType.PROPERTY, CardColor.LIGHT_BLUE, 0, 3);
        addCards(CardType.PROPERTY, CardColor.PINK, 0, 3);
        addCards(CardType.PROPERTY, CardColor.ORANGE, 0, 3);
        addCards(CardType.PROPERTY, CardColor.RED, 0, 3);
        addCards(CardType.PROPERTY, CardColor.YELLOW, 0, 3);
        addCards(CardType.PROPERTY, CardColor.GREEN, 0, 3);
        addCards(CardType.PROPERTY, CardColor.BLUE, 0, 2);
        addCards(CardType.PROPERTY, CardColor.BLACK, 0, 4, "Railroad Property");
        addCards(CardType.PROPERTY, CardColor.LIGHT_GREEN, 0, 2, "Utility Property");

        // ===== Wild property cards (11) =====
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Dark Blue/Green Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Brown/Light Blue Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Orange/Pink Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Red/Yellow Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Railroad/Utility Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Green/Railroad Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Light Blue/Railroad Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Multi-Color Wild");

        // ===== Rent cards (13, all with face values) =====
        addCards(CardType.RENT, CardColor.BROWN_LIGHT_BLUE, 1, 2, "Brown/Light Blue Rent");
        addCards(CardType.RENT, CardColor.PINK_ORANGE, 1, 2, "Pink/Orange Rent");
        addCards(CardType.RENT, CardColor.RED_YELLOW, 1, 2, "Red/Yellow Rent");
        addCards(CardType.RENT, CardColor.GREEN_BLUE, 1, 2, "Green/Blue Rent");
        addCards(CardType.RENT, CardColor.BLACK_LIGHT_GREEN, 1, 2, "Railroad/Utility Rent");
        addCards(CardType.RENT, CardColor.WILD, 3, 3, "Wild Rent");

        // ===== Action cards (34, all with face values) =====
        addCards(CardType.ACTION, CardColor.NONE, 5, 2, "Deal Breaker");
        addCards(CardType.ACTION, CardColor.NONE, 4, 3, "Just Say No");
        addCards(CardType.ACTION, CardColor.NONE, 3, 3, "Sly Deal");
        addCards(CardType.ACTION, CardColor.NONE, 3, 3, "Forced Deal");
        addCards(CardType.ACTION, CardColor.NONE, 3, 3, "Debt Collector");
        addCards(CardType.ACTION, CardColor.NONE, 2, 3, "Birthday");
        addCards(CardType.ACTION, CardColor.NONE, 1, 10, "Pass Go");
        addCards(CardType.ACTION, CardColor.NONE, 1, 2, "Double Rent");
        addCards(CardType.ACTION, CardColor.NONE, 3, 3, "House");
        addCards(CardType.ACTION, CardColor.NONE, 4, 2, "Hotel");

        // Validate total deck size = 106
        int total = drawPile.size();
        if (total != 106) {
            throw new IllegalStateException(
                "Deck initialization error: expected 106 cards, but generated " + total);
        }
    }

    /**
     * Batch add cards with the same attributes to the draw pile
     * @param type card type
     * @param color card color
     * @param value money face value
     * @param count number of cards to add
     */
    private void addCards(CardType type, CardColor color, int value, int count) {
        addCards(type, color, value, count, null);
    }

    /**
     * Batch add cards with the same attributes (supports custom names)
     * Each card uses the first 8 chars of a UUID as its unique ID
     *
     * @param type card type
     * @param color card color
     * @param value money face value
     * @param count number of cards to add
     * @param customName custom name (null for auto-generated)
     */
    private void addCards(CardType type, CardColor color, int value, int count, String customName) {
        for (int i = 0; i < count; i++) {
            String id = UUID.randomUUID().toString().substring(0, 8);
            String name = customName != null ? customName :
                    (type == CardType.MONEY ? value + "M" : color.getName() + " " + type.getDisplayName());
            drawPile.push(new Card(id, type, name, value, color, name));
        }
    }

    /** Shuffle - randomly reorders the draw pile using a random shuffle algorithm */
    public void shuffle() {
        Collections.shuffle(drawPile, random);
    }

    /**
     * Draw a single card from the top of the draw pile
     * If the draw pile is empty, automatically reshuffles the discard pile first
     * @return the drawn card (returns null if both piles are completely empty)
     */
    public Card draw() {
        if (drawPile.isEmpty()) {
            reshuffleDiscardPile();  // Recycle discard pile
        }
        return drawPile.isEmpty() ? null : drawPile.pop();
    }

    /**
     * Batch draw multiple cards from the top of the draw pile
     * @param count number of cards to draw
     * @return list of drawn cards (actual count may be less than count depending on remaining cards)
     */
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

    /** Place a single card into the discard pile */
    public void discard(Card card) {
        discardPile.add(card);
    }

    /** Batch place cards into the discard pile */
    public void discardMultiple(List<Card> cards) {
        discardPile.addAll(cards);
    }

    /**
     * Reshuffle discard pile - triggered when the draw pile is empty
     * Shuffles the discard pile and moves all cards into the draw pile, then clears the discard pile.
     * This ensures there are always cards available to draw during the game.
     */
    private void reshuffleDiscardPile() {
        if (!discardPile.isEmpty()) {
            Collections.shuffle(discardPile, random);
            drawPile.addAll(discardPile);
            discardPile.clear();
        }
    }

    // ==================== Query Methods ====================

    /** Get the current number of cards in the draw pile */
    public int getDrawPileSize() {
        return drawPile.size();
    }

    /** Get the current number of cards in the discard pile */
    public int getDiscardPileSize() {
        return discardPile.size();
    }

    /** Check whether the deck is completely empty (both draw and discard piles are empty) */
    public boolean isEmpty() {
        return drawPile.isEmpty() && discardPile.isEmpty();
    }
}
