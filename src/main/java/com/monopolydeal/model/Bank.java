package com.monopolydeal.model;

import java.util.*;

/**
 * Bank class - manages the storage of money cards for a player
 *
 * Each player has a bank that stores played money cards.
 * The bank stores money cards grouped by denomination (1M/2M/3M/4M/5M/10M).
 *
 * Core features:
 * - Deposit: store money cards into the bank
 * - Withdrawal (removeCardsByIds): player manually selects cards to pay, validates total value >= debt, no change given
 * - Fallback withdrawal (removeCardsFallback): automatically selects cards to pay on timeout (greedy, largest first)
 * - Balance query (getTotal): get total bank balance
 *
 * No change rule: if the player pays with cards whose total denomination exceeds the debt, the excess is forfeited and not returned.
 */
public class Bank {
    /** Money cards map grouped by denomination. key=denomination(1/2/3/4/5/10), value=list of money cards of that denomination */
    private final Map<Integer, List<Card>> moneyCards;
    /** Flat list of all money cards (for quick total amount calculation) */
    private final List<Card> allMoneyCards;

    /**
     * Constructor - initializes an empty bank
     * Creates empty lists for each money denomination (1M/2M/3M/4M/5M/10M)
     */
    public Bank() {
        this.moneyCards = new HashMap<>();
        this.allMoneyCards = new ArrayList<>();
        for (int denom : GameConstants.MONEY_DENOMINATIONS) {
            moneyCards.put(denom, new ArrayList<>());
        }
    }

    /**
     * Deposits a card with face value into the bank as a currency asset
     * Action cards and rent cards lose their original effect once deposited, serving only as currency
     * @param card any card with value > 0
     * @throws IllegalArgumentException if the card value is 0
     */
    public void deposit(Card card) {
        if (!card.canBeUsedAsMoney()) {
            throw new IllegalArgumentException("This card cannot be deposited into the bank (value is 0)");
        }
        int denomination = card.getValue();
        moneyCards.computeIfAbsent(denomination, k -> new ArrayList<>()).add(card);
        allMoneyCards.add(card);
    }

    /**
     * Queries the number of money cards of a given denomination in the bank
     * @param denomination denomination value (1/2/3/4/5/10)
     * @return number of cards of that denomination
     */
    public int getCount(int denomination) {
        return moneyCards.getOrDefault(denomination, Collections.emptyList()).size();
    }

    /**
     * Gets the total bank balance (sum of all money card denominations)
     * @return total amount (in M/millions)
     */
    public int getTotal() {
        return allMoneyCards.stream().mapToInt(Card::getValue).sum();
    }

    /** Returns a read-only list of all money cards in the bank */
    public List<Card> getAllMoneyCards() {
        return Collections.unmodifiableList(allMoneyCards);
    }

    /**
     * Player manually selects cards to pay - validates total denomination and removes them
     *
     * Validation rules:
     * 1. All cardIds must exist in the bank
     * 2. Total denomination of selected cards must be >= amount
     * 3. No change: all selected cards are removed and transferred, excess is forfeited
     *
     * @param cardIds list of card IDs selected by the player
     * @param amount  amount to be paid
     * @return list of removed cards
     * @throws IllegalArgumentException if a card does not exist or total denomination is insufficient
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
                "Insufficient payment amount. Requires at least " + amount + "M, but only " + totalValue + "M selected");
        }

        for (Card card : selected) {
            allMoneyCards.remove(card);
            moneyCards.get(card.getValue()).remove(card);
        }
        return selected;
    }

    /**
     * Timeout fallback auto-payment - greedy algorithm selecting cards from largest to smallest
     *
     * Used when the debtor does not respond within the timeout period; the server automatically selects cards to pay.
     * Takes cards in descending denomination order until the total >= amount.
     * Caller should first check balance via canPay(amount).
     *
     * @param amount amount to be paid
     * @return list of removed cards
     * @throws InsufficientFundsException if balance is insufficient
     */
    public List<Card> removeCardsFallback(int amount) throws InsufficientFundsException {
        if (getTotal() < amount) {
            throw new InsufficientFundsException("Insufficient balance. Requires " + amount +
                    "M, but only " + getTotal() + "M available");
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
     * Checks whether the bank has sufficient balance to pay the specified amount
     * @param amount amount to be paid
     * @return true=balance is sufficient, false=balance is insufficient
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
     * Gets the count of cards per denomination
     * @return map of key=denomination, value=number of cards of that denomination
     */
    public Map<Integer, Integer> getDenominationCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int denom : GameConstants.MONEY_DENOMINATIONS) {
            counts.put(denom, getCount(denom));
        }
        return counts;
    }

    /**
     * Insufficient funds exception
     * Thrown when a player's bank balance is not enough to cover the required payment
     */
    public static class InsufficientFundsException extends Exception {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }

}
