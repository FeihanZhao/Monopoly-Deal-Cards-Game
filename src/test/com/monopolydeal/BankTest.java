package com.monopolydeal;


import com.monopolydeal.model.Bank;
import com.monopolydeal.model.Card;
import com.monopolydeal.model.CardColor;
import com.monopolydeal.model.CardType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

public class BankTest {
    private Bank bank;
    private Card money5M;
    private Card money3M;
    private Card money2M;

    @BeforeEach
    void setUp() {
        bank = new Bank();
        money5M = new Card("id001", CardType.MONEY, "5M", 5, CardColor.NONE, "5M");
        money3M = new Card("id002", CardType.MONEY, "3M", 3, CardColor.NONE, "3M");
        money2M = new Card("id003", CardType.MONEY, "2M", 2, CardColor.NONE, "2M");
    }

    @Test
    void testDepositIncreasesTotal() {
        bank.deposit(money5M);
        assertEquals(5, bank.getTotal());
    }

    @Test
    void testDepositMultipleCards() {
        bank.deposit(money5M);
        bank.deposit(money3M);
        assertEquals(8, bank.getTotal());
    }

    @Test
    void testDepositZeroValueCardThrows() {
        Card zeroCard = new Card("id004", CardType.PROPERTY, "Brown", 0,
                CardColor.BROWN, "Brown Property");
        assertThrows(IllegalArgumentException.class, () -> bank.deposit(zeroCard));
    }

    @Test
    void testCanPayReturnsTrueWhenSufficient() {
        bank.deposit(money5M);
        assertTrue(bank.canPay(5));
        assertTrue(bank.canPay(3));
    }

    @Test
    void testCanPayReturnsFalseWhenInsufficient() {
        bank.deposit(money3M);
        assertFalse(bank.canPay(5));
    }

    @Test
    void testRemoveCardsByIdsReducesTotal() {
        bank.deposit(money5M);
        bank.deposit(money3M);
        List<Card> removed = bank.removeCardsByIds(List.of("id001"), 5);
        assertEquals(1, removed.size());
        assertEquals(3, bank.getTotal());
    }

    @Test
    void testRemoveCardsByIdsThrowsIfInsufficient() {
        bank.deposit(money2M);
        assertThrows(IllegalArgumentException.class,
                () -> bank.removeCardsByIds(List.of("id003"), 5));
    }

    @Test
    void testRemoveCardsByIdsDuplicateThrows() {
        bank.deposit(money5M);
        assertThrows(IllegalArgumentException.class,
                () -> bank.removeCardsByIds(List.of("id001", "id001"), 5));
    }

    @Test
    void testFallbackRemoveLargestFirst() throws Bank.InsufficientFundsException {
        bank.deposit(money2M);
        bank.deposit(money5M);
        bank.deposit(money3M);
        List<Card> removed = bank.removeCardsFallback(5);
        int total = removed.stream().mapToInt(Card::getValue).sum();
        assertTrue(total >= 5);
        // Should pick 5M first (greedy largest-first)
        assertEquals("5M", removed.get(0).getName());
    }

    @Test
    void testFallbackThrowsIfInsufficient() {
        bank.deposit(money2M);
        assertThrows(Bank.InsufficientFundsException.class,
                () -> bank.removeCardsFallback(5));
    }

    @Test
    void testClearEmptiesBank() {
        bank.deposit(money5M);
        bank.clear();
        assertEquals(0, bank.getTotal());
        assertTrue(bank.getAllMoneyCards().isEmpty());
    }
}