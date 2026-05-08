package com.monopolydeal.model;

import java.util.*;

public class Bank {
    private final Map<Integer, List<Card>> moneyCards; // Denomination -> Cards
    private final List<Card> allMoneyCards;

    public Bank() {
        this.moneyCards = new HashMap<>();
        this.allMoneyCards = new ArrayList<>();
        // Initialize denominations
        for (int denom : GameConstants.MONEY_DENOMINATIONS) {
            moneyCards.put(denom, new ArrayList<>());
        }
    }

    public void deposit(Card moneyCard) {
        if (!moneyCard.isMoneyCard()) {
            throw new IllegalArgumentException("Only money cards can be deposited");
        }
        int denomination = moneyCard.getValue();
        moneyCards.get(denomination).add(moneyCard);
        allMoneyCards.add(moneyCard);
    }

    public int getCount(int denomination) {
        return moneyCards.getOrDefault(denomination, Collections.emptyList()).size();
    }

    public int getTotal() {
        return allMoneyCards.stream().mapToInt(Card::getValue).sum();
    }

    public List<Card> getAllMoneyCards() {
        return Collections.unmodifiableList(allMoneyCards);
    }

    public List<Card> removeCards(int amount) throws InsufficientFundsException {
        if (getTotal() < amount) {
            throw new InsufficientFundsException("Insufficient funds. Need " + amount +
                    "M but only have " + getTotal() + "M");
        }

        // Optimal payment: try to use exact change or minimal cards
        List<Card> payment = OptimalPaymentCalculator.calculate(this, amount);

        // Remove from bank
        for (Card card : payment) {
            allMoneyCards.remove(card);
            moneyCards.get(card.getValue()).remove(card);
        }

        return payment;
    }

    public boolean canPay(int amount) {
        return getTotal() >= amount;
    }

    public void clear() {
        moneyCards.values().forEach(List::clear);
        allMoneyCards.clear();
    }

    public Map<Integer, Integer> getDenominationCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int denom : GameConstants.MONEY_DENOMINATIONS) {
            counts.put(denom, getCount(denom));
        }
        return counts;
    }

    public static class InsufficientFundsException extends Exception {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }
}