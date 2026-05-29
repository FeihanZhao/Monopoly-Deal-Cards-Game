package com.monopolydeal.model;

import java.util.*;

/**
 * Bank class — manages a player's money card storage.
 *
 * Each player has one bank that holds their played money cards.
 * The bank stores money cards grouped by denomination (1M/2M/3M/4M/5M/10M).
 *
 * Core features:
 * - Deposit: add a money card to the bank
 * - Withdraw by IDs (removeCardsByIds): player manually selects cards to pay; validates total >= debt; no change given
 * - Fallback withdraw (removeCardsFallback): auto-select cards on timeout (greedy, largest first)
 * - Balance query (getTotal): get the bank's total balance
 *
 * No-change rule: if a player pays with cards exceeding the debt, the excess is forfeit (no refund).
 */
public class Bank {
    /** Money cards grouped by denomination: key=denomination (1/2/3/4/5/10), value=list of cards */
    private final Map<Integer, List<Card>> moneyCards;
    /** Flat list of all money cards (for fast total calculation) */
    private final List<Card> allMoneyCards;

    /**
     * Constructor — initializes an empty bank.
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
     * Deposit a valued card into the bank as a monetary asset.
     * Action cards and rent cards lose their original effects once banked; they only count as currency.
     * @param card any card with value > 0
     * @throws IllegalArgumentException if the card's value is 0
     */
    public void deposit(Card card) {
        if (!card.canBeUsedAsMoney()) {
            throw new IllegalArgumentException("Card cannot be banked (value is 0)");
        }
        int denomination = card.getValue();
        moneyCards.computeIfAbsent(denomination, k -> new ArrayList<>()).add(card);
        allMoneyCards.add(card);
    }

    /**
     * Count the number of money cards of a specific denomination.
     * @param denomination value (1/2/3/4/5/10)
     * @return number of cards of that denomination
     */
    public int getCount(int denomination) {
        return moneyCards.getOrDefault(denomination, Collections.emptyList()).size();
    }

    /**
     * Get the bank's total balance (sum of all money card values).
     * @return total amount (unit: M / millions)
     */
    public int getTotal() {
        return allMoneyCards.stream().mapToInt(Card::getValue).sum();
    }

    /** Get a read-only list of all money cards in the bank */
    public List<Card> getAllMoneyCards() {
        return Collections.unmodifiableList(allMoneyCards);
    }

    /**
     * Player manually selects cards for payment — validates total value and removes them.
     *
     * Validation rules:
     * 1. All cardIds must exist in the bank
     * 2. Total value of selected cards must be >= amount
     * 3. No change given: all selected cards are removed and transferred; excess is forfeit
     *
     * @param cardIds list of card IDs selected by the player
     * @param amount  amount due
     * @return list of removed cards
     * @throws IllegalArgumentException if cards don't exist or total value is insufficient
     */
    public List<Card> removeCardsByIds(List<String> cardIds, int amount) {
        if (cardIds == null || cardIds.isEmpty()) {
            throw new IllegalArgumentException("No cards selected");
        }
        // Prevent duplicate card ID attacks: the same card cannot be paid twice
        if (new HashSet<>(cardIds).size() != cardIds.size()) {
            throw new IllegalArgumentException("Duplicate card IDs");
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
                "Insufficient payment. Need at least " + amount + "M, but only selected " + totalValue + "M");
        }

        for (Card card : selected) {
            allMoneyCards.remove(card);
            moneyCards.get(card.getValue()).remove(card);
        }
        return selected;
    }

    /**
     * Timeout fallback auto-payment — greedy algorithm, largest denominations first.
     *
     * Used when the debtor fails to respond in time; the server auto-selects cards.
     * Takes cards in descending denomination order until the total >= amount.
     * Callers should check canPay(amount) first.
     *
     * @param amount amount due
     * @return list of removed cards
     * @throws InsufficientFundsException if balance is insufficient
     */
    public List<Card> removeCardsFallback(int amount) throws InsufficientFundsException {
        if (getTotal() < amount) {
            throw new InsufficientFundsException("Insufficient balance. Need " + amount +
                    "M, but only have " + getTotal() + "M");
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

    /** Find a card in the bank by its ID */
    public Card findCardById(String cardId) {
        return allMoneyCards.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check whether the bank has enough funds to pay the specified amount.
     * @param amount amount due
     * @return true=sufficient funds, false=insufficient
     */
    public boolean canPay(int amount) {
        return getTotal() >= amount;
    }

    /** Clear the bank (remove all money cards) */
    public void clear() {
        moneyCards.values().forEach(List::clear);
        allMoneyCards.clear();
    }

    /**
     * Get a map of denomination counts.
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
     * Thrown when the player's bank balance is not enough to cover the required payment.
     */
    public static class InsufficientFundsException extends Exception {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }
}
