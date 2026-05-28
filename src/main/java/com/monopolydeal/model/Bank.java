package com.monopolydeal.model;

import java.util.*;

/**
 * Bank class - manages a player's money card storage.
 *
 * Each player has a bank that holds their played money cards.
 * Cards are grouped by denomination (1M/2M/3M/4M/5M/10M).
 *
 * Core functionality:
 * - deposit: adds a money card to the bank
 * - removeCardsByIds: player manually selects cards to pay, validates total >= debt, no change given
 * - removeCardsFallback: auto-payment on timeout (greedy descending order)
 * - getTotal: returns total bank balance
 *
 * No-change rule: if the player pays with cards whose total exceeds the debt,
 * the excess is forfeited and not returned.
 */
public class Bank {
    /** Money cards grouped by denomination. key=denomination (1/2/3/4/5/10), value=list of cards of that denomination */
    private final Map<Integer, List<Card>> moneyCards;
    /** Flat list of all money cards (for quick total calculation) */
    private final List<Card> allMoneyCards;

    /**
     * Constructor - initializes an empty bank.
     * Creates empty lists for each money denomination (1M/2M/3M/4M/5M/10M).
     */
    public Bank() {
        this.moneyCards = new HashMap<>();
        this.allMoneyCards = new ArrayList<>();
        for (int denom : GameConstants.MONEY_DENOMINATIONS) {
            moneyCards.put(denom, new ArrayList<>());
        }
    }

    /**
     * Deposits a card with a denomination into the bank as a monetary asset.
     * Action cards and rent cards lose their original effects once deposited;
     * they are treated purely as currency.
     * @param card any card with value > 0
     * @throws IllegalArgumentException if the card has a value of 0
     */
    public void deposit(Card card) {
        if (!card.canBeUsedAsMoney()) {
            throw new IllegalArgumentException("该卡不能存入银行（面值为0）");
        }
        int denomination = card.getValue();
        moneyCards.computeIfAbsent(denomination, k -> new ArrayList<>()).add(card);
        allMoneyCards.add(card);
    }

    /**
     * Queries the number of money cards of a given denomination in the bank.
     * @param denomination the denomination (1/2/3/4/5/10)
     * @return the count of cards of that denomination
     */
    public int getCount(int denomination) {
        return moneyCards.getOrDefault(denomination, Collections.emptyList()).size();
    }

    /**
     * Gets the total bank balance (sum of all money card denominations).
     * @return total amount (in M/millions)
     */
    public int getTotal() {
        return allMoneyCards.stream().mapToInt(Card::getValue).sum();
    }

    /** Returns an unmodifiable view of all money cards in the bank */
    public List<Card> getAllMoneyCards() {
        return Collections.unmodifiableList(allMoneyCards);
    }

    /**
     * Player manually selects cards to pay — validates total value and removes them.
     *
     * Validation rules:
     * 1. All cardIds must exist in the bank
     * 2. Total value of selected cards must be >= amount
     * 3. No change given: all selected cards are removed and transferred;
     *    the excess is forfeited
     *
     * @param cardIds list of card IDs selected by the player
     * @param amount  the amount to pay
     * @return the list of removed cards
     * @throws IllegalArgumentException if a card is not in the bank or total is insufficient
     */
    public List<Card> removeCardsByIds(List<String> cardIds, int amount) {
        if (cardIds == null || cardIds.isEmpty()) {
            throw new IllegalArgumentException("No cards selected");
        }

        List<Card> selected = new ArrayList<>();
        for (String cardId : cardIds) {
            Card card = findCardById(cardId);
            if (card == null) {
                throw new IllegalArgumentException("Card not in bank: " + cardId);
            }
            selected.add(card);
        }

        int totalValue = selected.stream().mapToInt(Card::getValue).sum();
        if (totalValue < amount) {
            throw new IllegalArgumentException(
                "Insufficient payment. Required at least " + amount + "M, but only selected " + totalValue + "M");
        }

        for (Card card : selected) {
            allMoneyCards.remove(card);
            moneyCards.get(card.getValue()).remove(card);
        }
        return selected;
    }

    /**
     * Timeout fallback auto-payment — greedy algorithm selects cards descending.
     *
     * Used when the debtor times out; the server auto-selects cards to pay.
     * Cards are selected in descending order of value until the total >= amount.
     * Callers should first check balance via canPay(amount).
     *
     * @param amount the amount to pay
     * @return the list of removed cards
     * @throws InsufficientFundsException if balance is insufficient
     */
    public List<Card> removeCardsFallback(int amount) throws InsufficientFundsException {
        if (getTotal() < amount) {
            throw new InsufficientFundsException("Insufficient balance. Requires " + amount +
                    "M, but only " + getTotal() + "M");
        }

        List<Card> sorted = new ArrayList<>(allMoneyCards);
        sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<Card> selected = new ArrayList<>();
        int accumulated = 0;
        for (Card card : sorted) {
            selected.add(card);
            accumulated += card.getValue();
            if (accumulated >= amount) break;
        }

        for (Card card : selected) {
            allMoneyCards.remove(card);
            moneyCards.get(card.getValue()).remove(card);
        }
        return selected;
    }

    /** Finds a card in the bank by its ID */
    public Card findCardById(String cardId) {
        return allMoneyCards.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks whether the bank has enough balance to pay the specified amount.
     * @param amount the amount to pay
     * @return true if balance is sufficient, false otherwise
     */
    public boolean canPay(int amount) {
        return getTotal() >= amount;
    }

    /** Clears the bank (removes all money cards) */
    public void clear() {
        moneyCards.values().forEach(List::clear);
        allMoneyCards.clear();
    }

    /**
     * Gets a map of counts per denomination.
     * @return key=denomination, value=number of cards of that denomination
     */
    public Map<Integer, Integer> getDenominationCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int denom : GameConstants.MONEY_DENOMINATIONS) {
            counts.put(denom, getCount(denom));
        }
        return counts;
    }

    /**
     * Insufficient funds exception.
     * Thrown when the player's bank balance is insufficient to pay the required amount.
     */
    public static class InsufficientFundsException extends Exception {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }

}
