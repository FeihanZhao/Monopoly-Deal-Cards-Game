package com.monopolydeal.model;

import java.util.*;

public class OptimalPaymentCalculator {

    public static List<Card> calculate(Bank bank, int amount) {
        List<Card> allCards = new ArrayList<>(bank.getAllMoneyCards());
        List<Card> bestPayment = null;
        int minCards = Integer.MAX_VALUE;
        int minExcess = Integer.MAX_VALUE;

        // Try all combinations (limit to reasonable size)
        int n = allCards.size();
        for (int i = 1; i < (1 << n); i++) {
            List<Card> combination = new ArrayList<>();
            int sum = 0;

            for (int j = 0; j < n; j++) {
                if ((i & (1 << j)) != 0) {
                    Card card = allCards.get(j);
                    combination.add(card);
                    sum += card.getValue();
                }
            }

            if (sum >= amount) {
                int excess = sum - amount;
                boolean isBetter = combination.size() < minCards ||
                        (combination.size() == minCards && excess < minExcess);

                if (isBetter) {
                    minCards = combination.size();
                    minExcess = excess;
                    bestPayment = combination;
                }
            }
        }

        return bestPayment != null ? bestPayment : Collections.emptyList();
    }
}