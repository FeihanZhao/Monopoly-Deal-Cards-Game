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
    void testBankRemoveCardsFallback() throws Bank.InsufficientFundsException {
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
        assertThrows(IllegalArgumentException.class, () -> bank.removeCardsByIds(List.of("m1"), 5));
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

        // Complete set should allow house placement
        assertTrue(player1.getPropertyZone().canPlaceHouse(CardColor.BLUE));

        // Add a house
        player1.getPropertyZone().addHouse(CardColor.BLUE);

        // MAX_HOUSES_PER_SET = 1, so cannot place another house
        assertFalse(player1.getPropertyZone().canPlaceHouse(CardColor.BLUE));

        // Cannot place hotel yet (need to have MAX_HOUSES_PER_SET houses first, which is 1)
        // After placing 1 house, can place hotel
        assertTrue(player1.getPropertyZone().canPlaceHotel(CardColor.BLUE));

        // Add hotel
        player1.getPropertyZone().addHotel(CardColor.BLUE);

        // After hotel, cannot place house (hotel replaces house)
        assertFalse(player1.getPropertyZone().canPlaceHouse(CardColor.BLUE));
        // Cannot place another hotel
        assertFalse(player1.getPropertyZone().canPlaceHotel(CardColor.BLUE));
    }

    @Test
    void testRentCalculation() {
        addProperties(player1, CardColor.BLUE, 2);

        // Base rent: Blue with 2 properties = 8M
        int baseRent = player1.getPropertyZone().getRentAmount(CardColor.BLUE);
        assertEquals(8, baseRent);

        // Add house: rent = base + 3 = 11M
        player1.getPropertyZone().addHouse(CardColor.BLUE);
        int rentWithHouse = player1.getPropertyZone().getRentAmount(CardColor.BLUE);
        assertEquals(11, rentWithHouse);  // 8 + 3 = 11

        // Add hotel: house is replaced, rent = base + 4 = 12M
        player1.getPropertyZone().addHotel(CardColor.BLUE);
        int rentWithHotel = player1.getPropertyZone().getRentAmount(CardColor.BLUE);
        assertEquals(12, rentWithHotel);  // 8 + 4 = 12
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
    void testFindCardById() {
        Card card = new Card("findMe", CardType.MONEY, "5M", 5, CardColor.NONE, "");
        player1.addCardToHand(card);
        Card found = player1.findCardById("findMe");
        assertNotNull(found);
        assertEquals("5M", found.getName());
        assertNull(player1.findCardById("notExists"));
    }

    @Test
    void testGameConstants() {
        assertEquals(2, GameConstants.MIN_PLAYERS);
        assertEquals(5, GameConstants.MAX_PLAYERS);
        assertEquals(5, GameConstants.INITIAL_HAND_SIZE);
        assertEquals(2, GameConstants.DRAW_COUNT);
        assertEquals(5, GameConstants.EMPTY_HAND_DRAW_COUNT);
        assertEquals(3, GameConstants.MAX_PLAYS_PER_TURN);
        assertEquals(7, GameConstants.MAX_HAND_SIZE);
        assertEquals(30, GameConstants.TURN_TIMEOUT_SECONDS);
        assertEquals(10, GameConstants.TIMEOUT_WARNING_SECONDS);
        assertEquals(15, GameConstants.DISCARD_TIMEOUT_SECONDS);
        assertEquals(5, GameConstants.JUST_SAY_NO_TIMEOUT_SECONDS);
        assertEquals(1, GameConstants.MAX_HOUSES_PER_SET);
        assertEquals(1, GameConstants.MAX_HOTELS_PER_SET);
        assertEquals(3, GameConstants.WINNING_COMPLETE_SETS);
        assertEquals(2, GameConstants.BIRTHDAY_AMOUNT);
        assertEquals(5, GameConstants.DEBT_COLLECTOR_AMOUNT);
        assertEquals(8888, GameConstants.SERVER_PORT);
        assertEquals("localhost", GameConstants.DEFAULT_HOST);
        assertArrayEquals(new int[]{1, 2, 3, 4, 5, 10}, GameConstants.MONEY_DENOMINATIONS);
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
        // Brown: 1 card=1M, 2+=2M
        assertEquals(1, CardColor.BROWN.getRentAmount(1));
        assertEquals(2, CardColor.BROWN.getRentAmount(2));
        assertEquals(2, CardColor.BROWN.getRentAmount(3));

        // Light Blue: 1 card=1M, 2+=2M
        assertEquals(1, CardColor.LIGHT_BLUE.getRentAmount(1));
        assertEquals(2, CardColor.LIGHT_BLUE.getRentAmount(2));

        // Pink/Orange: 1 card=1M, 2+=3M
        assertEquals(1, CardColor.PINK.getRentAmount(1));
        assertEquals(3, CardColor.PINK.getRentAmount(2));
        assertEquals(3, CardColor.PINK.getRentAmount(3));

        // Red/Yellow: 1 card=2M, 2=4M, 3+=6M
        assertEquals(2, CardColor.RED.getRentAmount(1));
        assertEquals(4, CardColor.RED.getRentAmount(2));
        assertEquals(6, CardColor.RED.getRentAmount(3));
        assertEquals(6, CardColor.RED.getRentAmount(4));

        // Green: 1 card=2M, 2=4M, 3+=7M
        assertEquals(2, CardColor.GREEN.getRentAmount(1));
        assertEquals(4, CardColor.GREEN.getRentAmount(2));
        assertEquals(7, CardColor.GREEN.getRentAmount(3));

        // Blue: 1 card=3M, 2+=8M
        assertEquals(3, CardColor.BLUE.getRentAmount(1));
        assertEquals(8, CardColor.BLUE.getRentAmount(2));
        assertEquals(8, CardColor.BLUE.getRentAmount(3));

        // Black: 1 card=1M, 2=2M, 3=3M, 4+=5M
        assertEquals(1, CardColor.BLACK.getRentAmount(1));
        assertEquals(2, CardColor.BLACK.getRentAmount(2));
        assertEquals(3, CardColor.BLACK.getRentAmount(3));
        assertEquals(5, CardColor.BLACK.getRentAmount(4));
        assertEquals(5, CardColor.BLACK.getRentAmount(5));

        // Light Green: 1 card=1M, 2=2M, 3+=4M
        assertEquals(1, CardColor.LIGHT_GREEN.getRentAmount(1));
        assertEquals(2, CardColor.LIGHT_GREEN.getRentAmount(2));
        assertEquals(4, CardColor.LIGHT_GREEN.getRentAmount(3));
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

    @Test
    void testPropertyColorIdentification() {
        // Pure property colors
        assertTrue(CardColor.BROWN.isPropertyColor());
        assertTrue(CardColor.BLUE.isPropertyColor());
        assertTrue(CardColor.GREEN.isPropertyColor());
        assertTrue(CardColor.BLACK.isPropertyColor());
        assertTrue(CardColor.LIGHT_GREEN.isPropertyColor());

        // Not property colors
        assertFalse(CardColor.WILD.isPropertyColor());
        assertFalse(CardColor.NONE.isPropertyColor());
        assertFalse(CardColor.BROWN_LIGHT_BLUE.isPropertyColor());
        assertFalse(CardColor.PINK_ORANGE.isPropertyColor());
        assertFalse(CardColor.RED_YELLOW.isPropertyColor());
        assertFalse(CardColor.GREEN_BLUE.isPropertyColor());
        assertFalse(CardColor.BLACK_LIGHT_GREEN.isPropertyColor());
    }

    @Test
    void testRentColorIdentification() {
        // Dual-color rent colors
        assertTrue(CardColor.BROWN_LIGHT_BLUE.isRentColor());
        assertTrue(CardColor.PINK_ORANGE.isRentColor());
        assertTrue(CardColor.RED_YELLOW.isRentColor());
        assertTrue(CardColor.GREEN_BLUE.isRentColor());
        assertTrue(CardColor.BLACK_LIGHT_GREEN.isRentColor());

        // Wild is also a rent color
        assertTrue(CardColor.WILD.isRentColor());

        // Property colors are not rent colors
        assertFalse(CardColor.BROWN.isRentColor());
        assertFalse(CardColor.BLUE.isRentColor());
        assertFalse(CardColor.NONE.isRentColor());
    }

    @Test
    void testGetComponentColors() {
        assertArrayEquals(new CardColor[]{CardColor.BROWN, CardColor.LIGHT_BLUE},
                CardColor.BROWN_LIGHT_BLUE.getComponentColors());
        assertArrayEquals(new CardColor[]{CardColor.PINK, CardColor.ORANGE},
                CardColor.PINK_ORANGE.getComponentColors());
        assertArrayEquals(new CardColor[]{CardColor.RED, CardColor.YELLOW},
                CardColor.RED_YELLOW.getComponentColors());
        assertArrayEquals(new CardColor[]{CardColor.GREEN, CardColor.BLUE},
                CardColor.GREEN_BLUE.getComponentColors());
        assertArrayEquals(new CardColor[]{CardColor.BLACK, CardColor.LIGHT_GREEN},
                CardColor.BLACK_LIGHT_GREEN.getComponentColors());

        // Non-dual colors return empty array
        assertEquals(0, CardColor.BROWN.getComponentColors().length);
        assertEquals(0, CardColor.WILD.getComponentColors().length);
    }

    @Test
    void testSetSize() {
        assertEquals(2, CardColor.BROWN.getSetSize());
        assertEquals(3, CardColor.LIGHT_BLUE.getSetSize());
        assertEquals(3, CardColor.PINK.getSetSize());
        assertEquals(3, CardColor.ORANGE.getSetSize());
        assertEquals(3, CardColor.RED.getSetSize());
        assertEquals(3, CardColor.YELLOW.getSetSize());
        assertEquals(3, CardColor.GREEN.getSetSize());
        assertEquals(2, CardColor.BLUE.getSetSize());
        assertEquals(4, CardColor.BLACK.getSetSize());
        assertEquals(2, CardColor.LIGHT_GREEN.getSetSize());

        // Special colors have setSize 0
        assertEquals(0, CardColor.WILD.getSetSize());
        assertEquals(0, CardColor.NONE.getSetSize());
        assertEquals(0, CardColor.BROWN_LIGHT_BLUE.getSetSize());
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