package com.monopolydeal.model;

import java.util.*;

/**
 * Deck class — manages the game's draw pile and discard pile.
 *
 * Responsible for card initialization, shuffling, drawing, discarding,
 * and recycling the discard pile back into the draw pile.
 *
 * The initial deck follows the official 106-card configuration:
 * - Money cards 20: 10M×1, 5M×2, 4M×3, 3M×3, 2M×5, 1M×6
 * - Standard properties 28: Brown×2, Light Blue×3, Pink×3, Orange×3, Red×3, Yellow×3,
 *   Green×3, Dark Blue×2, Railroad×4, Utility×2
 * - Wild properties 11: various dual/multi-color combinations
 * - Rent cards 13: dual-color×10 (1M each) + wild×3 (3M each)
 * - Action cards 34: all have monetary value and can be banked
 *
 * When the draw pile is empty, the discard pile is automatically shuffled
 * into the draw pile so the game never runs out of cards.
 */
public class Deck {
    /** Draw pile (stack structure; cards are drawn from the top) */
    private final Stack<Card> drawPile;
    /** Discard pile (list structure; recycled into draw pile when needed) */
    private final List<Card> discardPile;
    /** Shared random number generator */
    private final Random random;

    /**
     * Constructor — initializes the deck and shuffles it.
     * Pre-configures all card types and counts per official rules.
     */
    public Deck() {
        this.drawPile = new Stack<>();
        this.discardPile = new ArrayList<>();
        this.random = new Random();
        initializeDeck();  // Create all cards per rules
        shuffle();         // Initial shuffle
    }

    /**
     * Initialize the complete 106-card official deck.
     *
     * Card composition (strictly per official configuration):
     * - Money cards 20: 10M×1, 5M×2, 4M×3, 3M×3, 2M×5, 1M×6
     * - Standard properties 28: Brown×2, Light Blue×3, Pink×3, Orange×3, Red×3, Yellow×3,
     *   Green×3, Dark Blue×2, Railroad×4, Utility×2
     * - Wild properties 11: Dark Blue/Green×1, Brown/Light Blue×1, Pink/Orange×2,
     *   Red/Yellow×2, Railroad/Utility×1, Green/Railroad×1, Light Blue/Railroad×1,
     *   Multi-Color×2
     * - Rent cards 13: dual-color rent×10 (1M each), wild rent×3 (3M each)
     * - Action cards 34: Deal Breaker 5M×2, Just Say No 4M×3, Sly Deal 3M×3,
     *   Forced Deal 3M×3, Debt Collector 3M×3, Birthday 2M×3, Pass Go 1M×10,
     *   Double Rent 1M×2, House 3M×3, Hotel 4M×2
     */
    private void initializeDeck() {
        // ===== Money Cards (20) =====
        addCards(CardType.MONEY, CardColor.NONE, 10, 1);
        addCards(CardType.MONEY, CardColor.NONE, 5, 2);
        addCards(CardType.MONEY, CardColor.NONE, 4, 3);
        addCards(CardType.MONEY, CardColor.NONE, 3, 3);
        addCards(CardType.MONEY, CardColor.NONE, 2, 5);
        addCards(CardType.MONEY, CardColor.NONE, 1, 6);

        // ===== Standard Property Cards (28) =====
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

        // ===== Wild Property Cards (11) =====
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Dark Blue/Green Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Brown/Light Blue Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Orange/Pink Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Red/Yellow Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Railroad/Utility Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Green/Railroad Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Light Blue/Railroad Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Multi-Color Wild");

        // ===== Rent Cards (13, all with monetary value) =====
        addCards(CardType.RENT, CardColor.BROWN_LIGHT_BLUE, 1, 2, "Brown/Light Blue Rent");
        addCards(CardType.RENT, CardColor.PINK_ORANGE, 1, 2, "Pink/Orange Rent");
        addCards(CardType.RENT, CardColor.RED_YELLOW, 1, 2, "Red/Yellow Rent");
        addCards(CardType.RENT, CardColor.GREEN_BLUE, 1, 2, "Green/Blue Rent");
        addCards(CardType.RENT, CardColor.BLACK_LIGHT_GREEN, 1, 2, "Railroad/Utility Rent");
        addCards(CardType.RENT, CardColor.WILD, 3, 3, "Wild Rent");

        // ===== Action Cards (34, all with monetary value) =====
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

        // Verify total = 106
        int total = drawPile.size();
        if (total != 106) {
            throw new IllegalStateException(
                "Deck init error: expected 106, generated " + total);
        }
    }

    /**
     * Batch-add cards with the same attributes to the draw pile.
     * @param type card type
     * @param color card color
     * @param value monetary value
     * @param count number of copies
     */
    private void addCards(CardType type, CardColor color, int value, int count) {
        addCards(type, color, value, count, null);
    }

    /**
     * Batch-add cards with the same attributes (supports custom name).
     * Each card uses the first 8 chars of a UUID as its unique ID.
     *
     * @param type card type
     * @param color card color
     * @param value monetary value
     * @param count number of copies
     * @param customName custom name (auto-generated if null)
     */
    private void addCards(CardType type, CardColor color, int value, int count, String customName) {
        for (int i = 0; i < count; i++) {
            String id = UUID.randomUUID().toString().substring(0, 8);
            String name = customName != null ? customName :
                    (type == CardType.MONEY ? value + "M" : color.getName() + " " + type.getDisplayName());
            drawPile.push(new Card(id, type, name, value, color, name));
        }
    }

    /** Shuffle — randomly reorder the draw pile */
    public void shuffle() {
        Collections.shuffle(drawPile, random);
    }

    /**
     * Draw a single card from the top of the draw pile.
     * If the draw pile is empty, the discard pile is shuffled and moved into the draw pile first.
     * @return the drawn card (null if both piles are completely empty)
     */
    public Card draw() {
        if (drawPile.isEmpty()) {
            reshuffleDiscardPile();  // Recycle discard pile
        }
        return drawPile.isEmpty() ? null : drawPile.pop();
    }

    /**
     * Draw multiple cards from the top of the draw pile.
     * @param count number of cards to draw
     * @return list of drawn cards (actual count may be less if the deck runs low)
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

    /** Place multiple cards into the discard pile */
    public void discardMultiple(List<Card> cards) {
        discardPile.addAll(cards);
    }

    /**
     * Discard pile recycling — triggered when the draw pile is empty.
     * Shuffles the discard pile and moves all cards to the draw pile, then clears the discard pile.
     * This ensures the game never runs out of cards to draw.
     */
    private void reshuffleDiscardPile() {
        if (!discardPile.isEmpty()) {
            Collections.shuffle(discardPile, random);
            drawPile.addAll(discardPile);
            discardPile.clear();
        }
    }

    // ==================== Query Methods ====================

    /** Get the current draw pile size */
    public int getDrawPileSize() {
        return drawPile.size();
    }

    /** Get the current discard pile size */
    public int getDiscardPileSize() {
        return discardPile.size();
    }

    /** Check whether the deck is completely empty (both draw and discard piles) */
    public boolean isEmpty() {
        return drawPile.isEmpty() && discardPile.isEmpty();
    }
}
