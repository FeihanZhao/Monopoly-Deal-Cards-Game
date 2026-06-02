package com.monopolydeal;


import com.monopolydeal.model.Card;
import com.monopolydeal.model.Deck;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class DeckTest {
    private Deck deck;

    @BeforeEach
    void setUp() {
        deck = new Deck();
    }

    @Test
    void testInitialDeckSize() {
        assertEquals(106, deck.getDrawPileSize());
    }

    @Test
    void testDrawReducesDrawPile() {
        deck.draw();
        assertEquals(105, deck.getDrawPileSize());
    }

    @Test
    void testDrawMultiple() {
        List<Card> cards = deck.drawMultiple(5);
        assertEquals(5, cards.size());
        assertEquals(101, deck.getDrawPileSize());
    }

    @Test
    void testDiscardIncreasesDiscardPile() {
        Card card = deck.draw();
        deck.discard(card);
        assertEquals(1, deck.getDiscardPileSize());
    }

    @Test
    void testReshuffleWhenDrawPileEmpty() {
        // Draw all cards
        List<Card> all = deck.drawMultiple(106);
        assertEquals(0, deck.getDrawPileSize());
        // Discard them all
        deck.discardMultiple(all);
        assertEquals(106, deck.getDiscardPileSize());
        // Drawing again should trigger reshuffle
        Card card = deck.draw();
        assertNotNull(card);
        assertEquals(0, deck.getDiscardPileSize());
    }

    @Test
    void testIsNotEmptyAfterInit() {
        assertFalse(deck.isEmpty());
    }
}