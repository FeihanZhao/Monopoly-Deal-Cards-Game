package com.monopolydeal.model;

import java.util.*;

public class OptimalPaymentCalculator {
    private static final int MAX_DEVIATION = 50;

    public static List<Card> calculate(Bank bank, int amount) {
        List<Card> allCards = new ArrayList<>(bank.getAllMoneyCards());
        if (allCards.isEmpty()) return Collections.emptyList();

        allCards.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int totalAmount = allCards.stream().mapToInt(Card::getValue).sum();
        if (totalAmount < amount) return Collections.emptyList();

        int maxAmount = Math.min(amount + MAX_DEVIATION, totalAmount);
        int[] dp = new int[maxAmount + 1];
        int[] prev = new int[maxAmount + 1];
        Card[] usedCard = new Card[maxAmount + 1];

        Arrays.fill(dp, Integer.MAX_VALUE);
        dp[0] = 0;

        for (Card card : allCards) {
            int val = card.getValue();
            for (int i = maxAmount; i >= val; i--) {
                if (dp[i - val] != Integer.MAX_VALUE && dp[i - val] + 1 < dp[i]) {
                    dp[i] = dp[i - val] + 1;
                    prev[i] = i - val;
                    usedCard[i] = card;
                }
            }
        }

        int bestAmount = -1;
        int bestCardCount = Integer.MAX_VALUE;

        for (int i = amount; i <= maxAmount; i++) {
            if (dp[i] != Integer.MAX_VALUE && dp[i] < bestCardCount) {
                bestCardCount = dp[i];
                bestAmount = i;
                if (i == amount) break;
            }
        }

        if (bestAmount == -1) return Collections.emptyList();

        List<Card> result = new ArrayList<>();
        int current = bestAmount;
        while (current > 0 && usedCard[current] != null) {
            result.add(usedCard[current]);
            current = prev[current];
        }

        return result;
    }

    public static List<Card> calculateQuick(Bank bank, int amount) {
        List<Card> allCards = new ArrayList<>(bank.getAllMoneyCards());
        allCards.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<Card> result = new ArrayList<>();
        int remaining = amount;

        for (Card card : allCards) {
            if (card.getValue() <= remaining) {
                result.add(card);
                remaining -= card.getValue();
            }
        }

        if (remaining > 0) {
            for (Card card : allCards) {
                if (!result.contains(card)) {
                    result.add(card);
                    remaining -= card.getValue();
                    if (remaining <= 0) break;
                }
            }
        }

        return result;
    }
}