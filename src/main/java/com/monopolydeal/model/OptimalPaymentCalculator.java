package com.monopolydeal.model;

import java.util.*;

public class OptimalPaymentCalculator {
    private static final int MAX_DEVIATION = 50;

    /**
     * 0/1 背包 DP —— 计算支付 amount 所需的最少张数卡牌组合
     * 使用 lastCardIdx + prevAmount 替代 usedCard 引用，
     * 避免同面值卡牌在回溯时重复使用同一张卡。
     */
    public static List<Card> calculate(Bank bank, int amount) {
        List<Card> allCards = new ArrayList<>(bank.getAllMoneyCards());
        if (allCards.isEmpty()) return Collections.emptyList();

        allCards.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int totalAmount = allCards.stream().mapToInt(Card::getValue).sum();
        if (totalAmount < amount) return Collections.emptyList();

        int n = allCards.size();
        int maxAmount = Math.min(amount + MAX_DEVIATION, totalAmount);
        int[] dp = new int[maxAmount + 1];
        int[] prevAmount = new int[maxAmount + 1];
        int[] lastCardIdx = new int[maxAmount + 1];

        Arrays.fill(dp, Integer.MAX_VALUE);
        Arrays.fill(lastCardIdx, -1);
        dp[0] = 0;

        // 0/1 背包：外层遍历卡牌，内层逆向容量，保证每张卡只用一次
        for (int idx = 0; idx < n; idx++) {
            int val = allCards.get(idx).getValue();
            for (int i = maxAmount; i >= val; i--) {
                if (dp[i - val] != Integer.MAX_VALUE && dp[i - val] + 1 < dp[i]) {
                    dp[i] = dp[i - val] + 1;
                    prevAmount[i] = i - val;
                    lastCardIdx[i] = idx;
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

        // 回溯：通过 prevAmount 链式恢复，用 used 数组防重
        List<Card> result = new ArrayList<>();
        boolean[] used = new boolean[n];
        int current = bestAmount;
        while (current > 0 && lastCardIdx[current] >= 0) {
            int idx = lastCardIdx[current];
            if (!used[idx]) {
                result.add(allCards.get(idx));
                used[idx] = true;
            }
            current = prevAmount[current];
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