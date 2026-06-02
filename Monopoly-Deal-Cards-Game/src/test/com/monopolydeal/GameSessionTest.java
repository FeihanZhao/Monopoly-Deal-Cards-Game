package com.monopolydeal;


import com.monopolydeal.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

/**
 * Tests for GameSession-level logic using model classes directly.
 * Covers: payment transfer, win condition, player ready/room state.
 */
public class GameSessionTest {

    private Player player1;
    private Player player2;

    @BeforeEach
    void setUp() {
        player1 = new Player("p1", "Alice");
        player2 = new Player("p2", "Bob");
    }

    // ==================== Room / Ready state ====================

    @Test
    void testPlayerReadyToggle() {
        assertFalse(player1.isReady());
        player1.setReady(true);
        assertTrue(player1.isReady());
        player1.setReady(false);
        assertFalse(player1.isReady());
    }

    @Test
    void testAllPlayersReadyCheck() {
        player1.setReady(true);
        player2.setReady(false);
        List<Player> players = List.of(player1, player2);
        boolean allReady = players.stream().allMatch(Player::isReady);
        assertFalse(allReady);

        player2.setReady(true);
        allReady = players.stream().allMatch(Player::isReady);
        assertTrue(allReady);
    }

    @Test
    void testMinimumPlayersRequired() {
        List<Player> twoPlayers = List.of(player1, player2);
        assertTrue(twoPlayers.size() >= GameConstants.MIN_PLAYERS);

        List<Player> onePlayer = List.of(player1);
        assertFalse(onePlayer.size() >= GameConstants.MIN_PLAYERS);
    }

    // ==================== Deal cards (shuffleDeck / dealCards) ====================

    @Test
    void testDealInitialHand() {
        Deck deck = new Deck();
        int before = deck.getDrawPileSize();
        List<Card> hand = deck.drawMultiple(GameConstants.INITIAL_HAND_SIZE);
        hand.forEach(player1::addCardToHand);
        assertEquals(GameConstants.INITIAL_HAND_SIZE, player1.getHandCount());
        assertEquals(before - GameConstants.INITIAL_HAND_SIZE, deck.getDrawPileSize());
    }

    @Test
    void testDealToMultiplePlayers() {
        Deck deck = new Deck();
        List<Player> players = List.of(player1, player2);
        for (Player p : players) {
            deck.drawMultiple(GameConstants.INITIAL_HAND_SIZE).forEach(p::addCardToHand);
        }
        assertEquals(GameConstants.INITIAL_HAND_SIZE, player1.getHandCount());
        assertEquals(GameConstants.INITIAL_HAND_SIZE, player2.getHandCount());
        assertEquals(106 - GameConstants.INITIAL_HAND_SIZE * 2, deck.getDrawPileSize());
    }

    // ==================== drawCards ====================

    @Test
    void testDrawCardsPerTurn() {
        Deck deck = new Deck();
        List<Card> drawn = deck.drawMultiple(GameConstants.DRAW_COUNT);
        drawn.forEach(player1::addCardToHand);
        assertEquals(GameConstants.DRAW_COUNT, player1.getHandCount());
    }

    @Test
    void testDrawAllWhenHandEmpty() {
        Deck deck = new Deck();
        // Empty hand draws EMPTY_HAND_DRAW_COUNT
        List<Card> drawn = deck.drawMultiple(GameConstants.EMPTY_HAND_DRAW_COUNT);
        drawn.forEach(player1::addCardToHand);
        assertEquals(GameConstants.EMPTY_HAND_DRAW_COUNT, player1.getHandCount());
    }

    // ==================== moveCardToPropertySet ====================

    @Test
    void testMoveCardToPropertySet() {
        Card redProp = new Card("r1", CardType.PROPERTY, "Red Property",
                0, CardColor.RED, "Red");
        player1.addCardToHand(redProp);
        player1.removeCardFromHand(redProp);
        player1.getPropertyZone().addProperty(redProp);
        assertEquals(0, player1.getHandCount());
        assertEquals(1, player1.getPropertyZone().getPropertyCount(CardColor.RED));
    }

    @Test
    void testMoveMoneyCardToBank() {
        Card money = new Card("m1", CardType.MONEY, "5M", 5, CardColor.NONE, "5M");
        player1.addCardToHand(money);
        player1.removeCardFromHand(money);
        player1.getBank().deposit(money);
        assertEquals(0, player1.getHandCount());
        assertEquals(5, player1.getBank().getTotal());
    }

    // ==================== checkWinCondition ====================

    @Test
    void testWinConditionNotMetWithTwoSets() {
        addProperties(player1, CardColor.BLUE, 2);
        addProperties(player1, CardColor.BROWN, 2);
        assertEquals(2, player1.getCompleteSetsCount());
        assertFalse(player1.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS);
    }

    @Test
    void testWinConditionMetWithThreeSets() {
        addProperties(player1, CardColor.BLUE, 2);
        addProperties(player1, CardColor.BROWN, 2);
        addProperties(player1, CardColor.GREEN, 3);
        assertEquals(3, player1.getCompleteSetsCount());
        assertTrue(player1.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS);
    }

    @Test
    void testWinConditionCheckedAfterPropertyAdded() {
        addProperties(player1, CardColor.BLUE, 2);
        addProperties(player1, CardColor.BROWN, 2);
        assertFalse(player1.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS);
        // Add the third set
        addProperties(player1, CardColor.RED, 3);
        assertTrue(player1.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS);
    }

    // ==================== resolveAction / executeEffect ====================

    @Test
    void testPaymentTransferFromBankToBank() throws Bank.InsufficientFundsException {
        // Simulate rent payment: player2 pays player1
        Card money5 = new Card("m1", CardType.MONEY, "5M", 5, CardColor.NONE, "5M");
        Card money3 = new Card("m2", CardType.MONEY, "3M", 3, CardColor.NONE, "3M");
        player2.getBank().deposit(money5);
        player2.getBank().deposit(money3);

        int rentAmount = 5;
        List<Card> payment = player2.getBank().removeCardsFallback(rentAmount);
        for (Card c : payment) {
            player1.getBank().deposit(c.transferCopy());
        }

        assertTrue(player1.getBank().getTotal() >= rentAmount);
        assertTrue(player2.getBank().getTotal() < 8);
    }

    @Test
    void testPaymentWhenBankEmpty() {
        // Player with 0 balance cannot pay
        assertFalse(player2.getBank().canPay(1));
        assertEquals(0, player2.getBank().getTotal());
    }

    @Test
    void testPartialPaymentWhenInsufficientFunds() throws Bank.InsufficientFundsException {
        Card money2 = new Card("m1", CardType.MONEY, "2M", 2, CardColor.NONE, "2M");
        player2.getBank().deposit(money2);
        // Debt is 5M but only 2M available
        int actualAmount = Math.min(5, player2.getBank().getTotal());
        assertEquals(2, actualAmount);
        List<Card> payment = player2.getBank().removeCardsFallback(actualAmount);
        assertEquals(2, payment.stream().mapToInt(Card::getValue).sum());
        assertEquals(0, player2.getBank().getTotal());
    }

    @Test
    void testSlyDealTransfer() {
        Card redProp = new Card("r1", CardType.PROPERTY, "Red Property",
                0, CardColor.RED, "Red");
        player2.getPropertyZone().addProperty(redProp);
        assertEquals(1, player2.getPropertyZone().getPropertyCount(CardColor.RED));

        // Steal: remove from victim, add to thief
        player2.getPropertyZone().removeProperty(redProp);
        player1.getPropertyZone().addProperty(redProp);

        assertEquals(0, player2.getPropertyZone().getPropertyCount(CardColor.RED));
        assertEquals(1, player1.getPropertyZone().getPropertyCount(CardColor.RED));
    }

    @Test
    void testDealBreakerTransfer() {
        // Give player2 a complete Blue set
        addProperties(player2, CardColor.BLUE, 2);
        assertEquals(1, player2.getCompleteSetsCount());

        // Steal entire set
        List<Card> stolenSet = new ArrayList<>(
                player2.getPropertyZone().getPropertiesByColor(CardColor.BLUE));
        for (Card c : stolenSet) {
            player2.getPropertyZone().removeProperty(c);
            player1.getPropertyZone().addProperty(c);
        }

        assertEquals(0, player2.getPropertyZone().getPropertyCount(CardColor.BLUE));
        assertEquals(1, player1.getCompleteSetsCount());
    }

    // ==================== validateHandSize / discardCards ====================

    @Test
    void testValidateHandSizeNoDiscard() {
        Deck deck = new Deck();
        deck.drawMultiple(7).forEach(player1::addCardToHand);
        assertFalse(player1.needsToDiscard());
    }

    @Test
    void testValidateHandSizeNeedsDiscard() {
        Deck deck = new Deck();
        deck.drawMultiple(8).forEach(player1::addCardToHand);
        assertTrue(player1.needsToDiscard());
    }

    @Test
    void testDiscardReducesHandToLimit() {
        Deck deck = new Deck();
        List<Card> cards = deck.drawMultiple(9);
        cards.forEach(player1::addCardToHand);
        assertEquals(9, player1.getHandCount());

        // Discard until at limit
        while (player1.needsToDiscard()) {
            player1.removeCardFromHand(0);
        }
        assertEquals(GameConstants.MAX_HAND_SIZE, player1.getHandCount());
        assertFalse(player1.needsToDiscard());
    }

    // ==================== nextTurn ====================

    @Test
    void testNextTurnResetsActivePlayer() {
        player1.setActivePlayer(true);
        player1.incrementPlaysUsed();
        player1.setDoubleRentActive(true);

        // End turn
        player1.setActivePlayer(false);
        player1.resetTurnState();

        // Next player starts
        player2.setActivePlayer(true);
        player2.resetTurnState();

        assertFalse(player1.isActivePlayer());
        assertFalse(player1.isDoubleRentActive());
        assertEquals(0, player1.getPlaysUsed());
        assertTrue(player2.isActivePlayer());
        assertEquals(0, player2.getPlaysUsed());
    }

    @Test
    void testNextTurnRotation() {
        List<Player> players = Arrays.asList(player1, player2,
                new Player("p3", "Charlie"));
        int currentIndex = 0;
        players.get(currentIndex).setActivePlayer(true);

        // Advance to next
        players.get(currentIndex).setActivePlayer(false);
        currentIndex = (currentIndex + 1) % players.size();
        players.get(currentIndex).setActivePlayer(true);

        assertFalse(players.get(0).isActivePlayer());
        assertTrue(players.get(1).isActivePlayer());
        assertFalse(players.get(2).isActivePlayer());
    }

    @Test
    void testNextTurnSkipsDisconnectedPlayer() {
        Player player3 = new Player("p3", "Charlie");
        player2.setConnected(false);
        List<Player> players = Arrays.asList(player1, player2, player3);

        int currentIndex = 0;
        players.get(currentIndex).setActivePlayer(false);
        int nextIndex = (currentIndex + 1) % players.size();

        // Skip disconnected
        while (!players.get(nextIndex).isConnected()) {
            nextIndex = (nextIndex + 1) % players.size();
        }
        players.get(nextIndex).setActivePlayer(true);

        assertTrue(players.get(2).isActivePlayer()); // Charlie, not Bob
        assertFalse(players.get(1).isActivePlayer());
    }

    // ==================== cancelAction (Just Say No) ====================

    @Test
    void testJustSayNoCardDetection() {
        Card jsn = new Card("jsn1", CardType.ACTION, "Just Say No", 4,
                CardColor.NONE, "Just Say No");
        player1.addCardToHand(jsn);
        boolean hasJSN = player1.getHand().stream()
                .anyMatch(c -> c.getName().contains("Just Say No"));
        assertTrue(hasJSN);
    }

    @Test
    void testJustSayNoCardRemovedOnPlay() {
        Card jsn = new Card("jsn1", CardType.ACTION, "Just Say No", 4,
                CardColor.NONE, "Just Say No");
        player1.addCardToHand(jsn);
        player1.removeCardFromHand(jsn);
        boolean hasJSN = player1.getHand().stream()
                .anyMatch(c -> c.getName().contains("Just Say No"));
        assertFalse(hasJSN);
    }

    // ==================== Helper ====================

    private void addProperties(Player player, CardColor color, int count) {
        for (int i = 0; i < count; i++) {
            player.getPropertyZone().addProperty(
                    new Card(UUID.randomUUID().toString().substring(0, 8),
                            CardType.PROPERTY, color.getName() + " Property",
                            0, color, ""));
        }
    }
}