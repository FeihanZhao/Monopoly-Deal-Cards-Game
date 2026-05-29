package com.monopolydeal;

import com.monopolydeal.model.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

public class GameTest {

    private Deck deck;
    private Player player1;
    private Player player2;
    private Bank bank;

    @BeforeEach
    void setUp() {
        deck = new Deck();
        player1 = new Player("p1", "Alice");
        player2 = new Player("p2", "Bob");
        bank = new Bank();
    }

    @Test
    void testDeckInitialization() {
        assertNotNull(deck);
        assertTrue(deck.getDrawPileSize() > 0);
        assertTrue(deck.getDrawPileSize() <= 110);
    }

    @Test
    void testDrawCard() {
        int initialSize = deck.getDrawPileSize();
        Card card = deck.draw();
        assertNotNull(card);
        assertEquals(initialSize - 1, deck.getDrawPileSize());
    }

    @Test
    void testDrawMultiple() {
        List<Card> cards = deck.drawMultiple(5);
        assertEquals(5, cards.size());
        for (Card card : cards) {
            assertNotNull(card);
            assertNotNull(card.getId());
            assertNotNull(card.getType());
        }
    }

    @Test
    void testDiscardAndReshuffle() {
        Card card = deck.draw();
        deck.discard(card);
        assertEquals(1, deck.getDiscardPileSize());
        int drawPileSize = deck.getDrawPileSize();
        for (int i = 0; i < drawPileSize; i++) {
            deck.draw();
        }
        Card drawn = deck.draw();
        assertNotNull(drawn);
        assertTrue(deck.getDiscardPileSize() == 0 || deck.getDrawPileSize() >= 0);
    }

    @Test
    void testPlayerInitialState() {
        assertEquals(0, player1.getHandCount());
        assertEquals(0, player1.getBank().getTotal());
        assertEquals(0, player1.getCompleteSetsCount());
        assertEquals(0, player1.getPlaysUsed());
        assertFalse(player1.isActivePlayer());
        assertEquals(3, player1.getRemainingPlays());
    }

    @Test
    void testPlayerDrawCards() {
        List<Card> cards = deck.drawMultiple(5);
        cards.forEach(player1::addCardToHand);
        assertEquals(5, player1.getHandCount());
    }

    @Test
    void testPlayerPlayCard() {
        List<Card> cards = deck.drawMultiple(3);
        cards.forEach(player1::addCardToHand);
        Card card = player1.getHand().get(0);
        player1.removeCardFromHand(card);
        assertEquals(2, player1.getHandCount());
        player1.incrementPlaysUsed();
        assertEquals(1, player1.getPlaysUsed());
        assertEquals(2, player1.getRemainingPlays());
    }

    @Test
    void testPlayerNeedsToDiscard() {
        List<Card> cards = deck.drawMultiple(8);
        cards.forEach(player1::addCardToHand);
        assertTrue(player1.needsToDiscard());
        player1.removeCardFromHand(0);
        assertFalse(player1.needsToDiscard());
    }

    @Test
    void testPlayerTurnReset() {
        player1.setActivePlayer(true);
        player1.incrementPlaysUsed();
        player1.incrementPlaysUsed();
        player1.setDoubleRentActive(true);
        player1.resetTurnState();
        assertEquals(0, player1.getPlaysUsed());
        assertFalse(player1.isDoubleRentActive());
    }

    @Test
    void testBankDeposit() {
        Card moneyCard = new Card("m1", CardType.MONEY, "5M", 5, CardColor.NONE, "5 Million");
        bank.deposit(moneyCard);
        assertEquals(5, bank.getTotal());
        assertEquals(1, bank.getCount(5));
    }

    @Test
    void testBankMultipleDeposits() {
        bank.deposit(new Card("m1", CardType.MONEY, "1M", 1, CardColor.NONE, ""));
        bank.deposit(new Card("m2", CardType.MONEY, "2M", 2, CardColor.NONE, ""));
        bank.deposit(new Card("m3", CardType.MONEY, "5M", 5, CardColor.NONE, ""));
        assertEquals(8, bank.getTotal());
        assertEquals(1, bank.getCount(1));
        assertEquals(1, bank.getCount(2));
        assertEquals(1, bank.getCount(5));
    }

    @Test
    void testBankRemoveCards() throws Bank.InsufficientFundsException {
        bank.deposit(new Card("m1", CardType.MONEY, "5M", 5, CardColor.NONE, ""));
        bank.deposit(new Card("m2", CardType.MONEY, "3M", 3, CardColor.NONE, ""));
        bank.deposit(new Card("m3", CardType.MONEY, "2M", 2, CardColor.NONE, ""));
        List<Card> payment = bank.removeCardsFallback(4);
        assertFalse(payment.isEmpty());
        int paid = payment.stream().mapToInt(Card::getValue).sum();
        assertTrue(paid >= 4);
        assertTrue(bank.getTotal() <= 6);
    }

    @Test
    void testBankInsufficientFunds() {
        bank.deposit(new Card("m1", CardType.MONEY, "3M", 3, CardColor.NONE, ""));
        assertThrows(Bank.InsufficientFundsException.class, () -> bank.removeCardsFallback(10));
    }

    @Test
    void testBankRemoveCardsByIds() {
        bank.deposit(new Card("m1", CardType.MONEY, "5M", 5, CardColor.NONE, ""));
        bank.deposit(new Card("m2", CardType.MONEY, "3M", 3, CardColor.NONE, ""));
        bank.deposit(new Card("m3", CardType.MONEY, "2M", 2, CardColor.NONE, ""));
        List<Card> payment = bank.removeCardsByIds(Arrays.asList("m1", "m3"), 5);
        assertEquals(2, payment.size());
        int paid = payment.stream().mapToInt(Card::getValue).sum();
        assertEquals(7, paid);
        assertEquals(3, bank.getTotal());
    }

    @Test
    void testBankRemoveCardsByIdsInsufficient() {
        bank.deposit(new Card("m1", CardType.MONEY, "2M", 2, CardColor.NONE, ""));
        assertThrows(IllegalArgumentException.class, () -> bank.removeCardsByIds(Arrays.asList("m1"), 5));
    }

    @Test
    void testBankRemoveCardsByIdsCardNotFound() {
        bank.deposit(new Card("m1", CardType.MONEY, "5M", 5, CardColor.NONE, ""));
        assertThrows(IllegalArgumentException.class, () -> bank.removeCardsByIds(Arrays.asList("m1", "ghost"), 5));
    }

    @Test
    void testBankCanPay() {
        bank.deposit(new Card("m1", CardType.MONEY, "5M", 5, CardColor.NONE, ""));
        assertTrue(bank.canPay(3));
        assertTrue(bank.canPay(5));
        assertFalse(bank.canPay(6));
    }

    @Test
    void testPropertyZoneAddProperty() {
        Card property = new Card("p1", CardType.PROPERTY, "Blue Property", 0, CardColor.BLUE, "");
        player1.getPropertyZone().addProperty(property);
        assertEquals(1, player1.getPropertyZone().getPropertyCount(CardColor.BLUE));
    }

    @Test
    void testPropertyZoneCompleteSet() {
        player1.getPropertyZone().addProperty(new Card("p1", CardType.PROPERTY, "Blue 1", 0, CardColor.BLUE, ""));
        player1.getPropertyZone().addProperty(new Card("p2", CardType.PROPERTY, "Blue 2", 0, CardColor.BLUE, ""));
        assertEquals(1, player1.getCompleteSetsCount());
        assertTrue(player1.getPropertyZone().getCompleteSets().contains(CardColor.BLUE));
    }

    @Test
    void testPropertyZoneRemoveProperty() {
        Card property = new Card("p1", CardType.PROPERTY, "Blue Property", 0, CardColor.BLUE, "");
        player1.getPropertyZone().addProperty(property);
        assertTrue(player1.getPropertyZone().removeProperty(property));
        assertEquals(0, player1.getPropertyZone().getPropertyCount(CardColor.BLUE));
    }

    @Test
    void testWildPropertyColorChange() {
        Card wild = new Card("w1", CardType.PROPERTY, "Multi-Color Wild", 0, CardColor.WILD, "");
        wild.setWildColor(CardColor.RED);
        assertEquals(CardColor.RED, wild.getEffectiveColor());
        player1.getPropertyZone().addProperty(wild);
        assertEquals(1, player1.getPropertyZone().getPropertyCount(CardColor.RED));
    }

    @Test
    void testThreeCompleteSetsWin() {
        addProperties(player1, CardColor.BLUE, 2);
        addProperties(player1, CardColor.BROWN, 2);
        addProperties(player1, CardColor.GREEN, 3);
        assertEquals(3, player1.getCompleteSetsCount());
        assertTrue(player1.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS);
    }

    @Test
    void testHousePlacement() {
        addProperties(player1, CardColor.BLUE, 2);
        assertTrue(player1.getPropertyZone().canPlaceHouse(CardColor.BLUE));
        player1.getPropertyZone().addHouse(CardColor.BLUE);
        assertFalse(player1.getPropertyZone().canPlaceHotel(CardColor.BLUE));
        for (int i = 0; i < 3; i++) {
            player1.getPropertyZone().addHouse(CardColor.BLUE);
        }
        assertTrue(player1.getPropertyZone().canPlaceHotel(CardColor.BLUE));
        player1.getPropertyZone().addHotel(CardColor.BLUE);
        assertFalse(player1.getPropertyZone().canPlaceHouse(CardColor.BLUE));
    }

    @Test
    void testRentCalculation() {
        addProperties(player1, CardColor.BLUE, 2);
        int rent = player1.getPropertyZone().getRentAmount(CardColor.BLUE);
        assertEquals(8, rent);
        player1.getPropertyZone().addHouse(CardColor.BLUE);
        int rentWithHouse = player1.getPropertyZone().getRentAmount(CardColor.BLUE);
        assertEquals(9, rentWithHouse);
    }

    @Test
    void testCardTypes() {
        Card moneyCard = new Card("m1", CardType.MONEY, "5M", 5, CardColor.NONE, "");
        Card propertyCard = new Card("p1", CardType.PROPERTY, "Red Property", 0, CardColor.RED, "");
        Card actionCard = new Card("a1", CardType.ACTION, "Deal Breaker", 0, CardColor.NONE, "");
        Card rentCard = new Card("r1", CardType.RENT, "Wild Rent", 0, CardColor.WILD, "");

        assertTrue(moneyCard.isMoneyCard());
        assertTrue(propertyCard.isPropertyCard());
        assertTrue(actionCard.isActionCard());
        assertTrue(rentCard.isRentCard());
        assertTrue(moneyCard.canBeUsedAsMoney());
        assertFalse(propertyCard.canBeUsedAsMoney());
    }

    @Test
    void testCardEquality() {
        Card card1 = new Card("abc123", CardType.MONEY, "1M", 1, CardColor.NONE, "");
        Card card2 = new Card("abc123", CardType.MONEY, "1M", 1, CardColor.NONE, "");
        Card card3 = new Card("xyz789", CardType.MONEY, "1M", 1, CardColor.NONE, "");
        assertEquals(card1, card2);
        assertNotEquals(card1, card3);
        assertEquals(card1.hashCode(), card2.hashCode());
    }

    @Test
    void testCardClone() {
        Card original = new Card("c1", CardType.PROPERTY, "Wild", 0, CardColor.WILD, "");
        original.setWildColor(CardColor.RED);
        Card cloned = original.clone();
        assertEquals(original.getId(), cloned.getId());
        assertEquals(original.getWildColor(), cloned.getWildColor());
    }

    @Test
    void testAllCardsInDeck() {
        Set<String> cardTypes = new HashSet<>();
        int totalCards = deck.getDrawPileSize();
        for (int i = 0; i < totalCards; i++) {
            Card card = deck.draw();
            if (card != null) {
                cardTypes.add(card.getType().name());
            }
        }
        assertTrue(cardTypes.contains("MONEY"));
        assertTrue(cardTypes.contains("PROPERTY"));
        assertTrue(cardTypes.contains("ACTION"));
        assertTrue(cardTypes.contains("RENT"));
    }

    @Test
    void testInitialHandDeal() {
        for (int i = 0; i < GameConstants.INITIAL_HAND_SIZE; i++) {
            player1.addCardToHand(deck.draw());
        }
        assertEquals(GameConstants.INITIAL_HAND_SIZE, player1.getHandCount());
    }

    @Test
    void testDoubleRentFlag() {
        assertFalse(player1.isDoubleRentActive());
        player1.setDoubleRentActive(true);
        assertTrue(player1.isDoubleRentActive());
        player1.resetTurnState();
        assertFalse(player1.isDoubleRentActive());
    }

    @Test
    void testPlayerConnectedStatus() {
        assertTrue(player1.isConnected());
        player1.setConnected(false);
        assertFalse(player1.isConnected());
    }

    @Test
    void testPlayerReadyStatus() {
        assertFalse(player1.isReady());
        player1.setReady(true);
        assertTrue(player1.isReady());
    }

    @Test
    void testOptimalPaymentCalculation() {
        bank.deposit(new Card("m1", CardType.MONEY, "10M", 10, CardColor.NONE, ""));
        bank.deposit(new Card("m2", CardType.MONEY, "5M", 5, CardColor.NONE, ""));
        bank.deposit(new Card("m3", CardType.MONEY, "1M", 1, CardColor.NONE, ""));
        bank.deposit(new Card("m4", CardType.MONEY, "1M", 1, CardColor.NONE, ""));
        List<Card> payment = OptimalPaymentCalculator.calculate(bank, 7);
        int total = payment.stream().mapToInt(Card::getValue).sum();
        assertTrue(total >= 7);
        assertTrue(total <= 8);
    }

    @Test
    void testFindCardById() {
        Card card = new Card("findMe", CardType.MONEY, "5M", 5, CardColor.NONE, "");
        player1.addCardToHand(card);
        Card found = player1.findCardById("findMe");
        assertNotNull(found);
        assertEquals("5M", found.getName());
        assertNull(player1.findCardById("notExists"));
    }

    @Test
    void testDiscardPileReshuffle() {
        int initialDrawSize = deck.getDrawPileSize();
        for (int i = 0; i < initialDrawSize; i++) {
            Card c = deck.draw();
            if (c != null) deck.discard(c);
        }
        assertEquals(0, deck.getDrawPileSize());
        assertTrue(deck.getDiscardPileSize() > 0);
        Card drawn = deck.draw();
        assertNotNull(drawn);
        assertTrue(deck.getDrawPileSize() > 0);
    }

    @Test
    void testGameConstants() {
        assertEquals(2, GameConstants.MIN_PLAYERS);
        assertEquals(5, GameConstants.MAX_PLAYERS);
        assertEquals(5, GameConstants.INITIAL_HAND_SIZE);
        assertEquals(3, GameConstants.DRAW_COUNT);
        assertEquals(3, GameConstants.MAX_PLAYS_PER_TURN);
        assertEquals(7, GameConstants.MAX_HAND_SIZE);
        assertEquals(30, GameConstants.TURN_TIMEOUT_SECONDS);
        assertEquals(3, GameConstants.WINNING_COMPLETE_SETS);
        assertEquals(2, GameConstants.BIRTHDAY_AMOUNT);
        assertEquals(5, GameConstants.DEBT_COLLECTOR_AMOUNT);
        assertEquals(8888, GameConstants.SERVER_PORT);
    }

    @Test
    void testMaxPlaysPerTurn() {
        player1.setActivePlayer(true);
        player1.resetTurnState();
        assertEquals(3, player1.getRemainingPlays());
        player1.incrementPlaysUsed();
        assertEquals(2, player1.getRemainingPlays());
        player1.incrementPlaysUsed();
        assertEquals(1, player1.getRemainingPlays());
        player1.incrementPlaysUsed();
        assertEquals(0, player1.getRemainingPlays());
        assertFalse(player1.canPlay());
    }

    @Test
    void testCardColorRentValues() {
        assertEquals(1, CardColor.BROWN.getRentAmount(1));
        assertEquals(2, CardColor.BROWN.getRentAmount(2));
        assertEquals(3, CardColor.BLUE.getRentAmount(1));
        assertEquals(8, CardColor.BLUE.getRentAmount(2));
        assertEquals(2, CardColor.RED.getRentAmount(1));
        assertEquals(4, CardColor.RED.getRentAmount(2));
        assertEquals(6, CardColor.RED.getRentAmount(3));
    }

    @Test
    void testBankDenominations() {
        bank.deposit(new Card("m1", CardType.MONEY, "1M", 1, CardColor.NONE, ""));
        bank.deposit(new Card("m2", CardType.MONEY, "1M", 1, CardColor.NONE, ""));
        bank.deposit(new Card("m3", CardType.MONEY, "10M", 10, CardColor.NONE, ""));
        assertEquals(2, bank.getCount(1));
        assertEquals(0, bank.getCount(2));
        assertEquals(0, bank.getCount(5));
        assertEquals(1, bank.getCount(10));
        assertEquals(12, bank.getTotal());
    }

    private void addProperties(Player player, CardColor color, int count) {
        for (int i = 0; i < count; i++) {
            player.getPropertyZone().addProperty(
                    new Card(UUID.randomUUID().toString().substring(0, 8),
                            CardType.PROPERTY,
                            color.getName() + " Property",
                            0, color, "")
            );
        }
    }
}