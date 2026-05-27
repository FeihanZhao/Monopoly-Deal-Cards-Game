package com.monopolydeal.model;

import java.util.*;

/**
 * Bank class - manages a player's money card storage
 *
 * Each player owns a bank for storing played money cards.
 * The bank groups money cards by denomination (1M/2M/3M/4M/5M/10M).
 *
 * Core features:
 * - deposit: store a money card into the bank
 * - removeCardsByIds: player manually selects cards for payment, validates total >= debt, no change given
 * - removeCardsFallback: auto-selects cards for payment on timeout (greedy, largest first)
 * - getTotal: query the total balance
 *
 * No-change rule: if the total face value exceeds the debt, the surplus is forfeited.
 */
public class Bank {
    /** Money cards grouped by denomination: key=denomination(1/2/3/4/5/10), value=list of cards */
    private final Map<Integer, List<Card>> moneyCards;
    /** Flat list of all money cards (convenient for quick total calculation) */
    private final List<Card> allMoneyCards;

    /**
     * Constructor - initializes an empty bank
     * Creates an empty list for each money denomination (1M/2M/3M/4M/5M/10M)
     */
    public Bank() {
        this.moneyCards = new HashMap<>();
        this.allMoneyCards = new ArrayList<>();
        for (int denom : GameConstants.MONEY_DENOMINATIONS) {
            moneyCards.put(denom, new ArrayList<>());
        }
    }

    /**
     * Deposit a card with face value into the bank as currency
     * Action cards and rent cards lose their original effect once deposited, serving only as currency
     * @param card any card with value > 0
     * @throws IllegalArgumentException if the card's value is 0
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
     * Query the number of money cards of a given denomination in the bank
     * @param denomination face value (1/2/3/4/5/10)
     * @return number of cards with that denomination
     */
    public int getCount(int denomination) {
        return moneyCards.getOrDefault(denomination, Collections.emptyList()).size();
    }

    /**
     * Get the total balance of the bank (sum of all money card values)
     * @return total amount (in M/millions)
     */
    public int getTotal() {
        return allMoneyCards.stream().mapToInt(Card::getValue).sum();
    }

    /** Get a read-only list of all money cards in the bank */
    public List<Card> getAllMoneyCards() {
        return Collections.unmodifiableList(allMoneyCards);
    }

    /**
     * Player manually selects cards for payment - validates total and removes them
     *
     * Validation rules:
     * 1. All cardIds must exist in the bank
     * 2. Selected cards' total value must be >= amount
     * 3. No change: all selected cards are removed, surplus is forfeited
     *
     * @param cardIds list of card IDs selected by the player
     * @param amount  the amount to pay
     * @return list of removed cards
     * @throws IllegalArgumentException if a card does not exist or total is insufficient
     */
    public List<Card> removeCardsByIds(List<String> cardIds, int amount) {
        if (cardIds == null || cardIds.isEmpty()) {
            throw new IllegalArgumentException("No cards selected");
        }
        // Prevent duplicate card ID attack: same card cannot be paid twice
        if (new HashSet<>(cardIds).size() != cardIds.size()) {
            throw new IllegalArgumentException("Duplicate card IDs detected");
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
     * Timeout fallback auto-payment - greedy algorithm selecting cards from largest to smallest
     *
     * Used by the server to auto-select cards when the debtor does not respond in time.
     * Picks cards in descending denomination order until total >= amount.
     * Caller should check canPay(amount) first.
     *
     * @param amount the amount to pay
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

    /** Find a card by its ID in the bank */
    public Card findCardById(String cardId) {
        return allMoneyCards.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Check whether the bank has sufficient balance to pay the specified amount
     * @param amount the amount to pay
     * @return true=balance sufficient, false=insufficient
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
     * Get the count of cards per denomination
     * @return map of key=denomination, value=number of cards with that denomination
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
