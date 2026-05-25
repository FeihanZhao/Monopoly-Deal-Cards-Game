package com.monopolydeal.model;

import java.util.*;

/**
 * 最优支付计算器 - 使用动态规划算法计算最优的金钱卡支付方案
 *
 * 当玩家需要支付一定金额时（如支付租金），本类计算使用最少数量的金钱卡
 * 凑出目标金额的最优方案。如果不能精确凑出，则选择超出金额最少的方案。
 *
 * 算法说明：
 * - 使用0/1背包DP（动态规划），每张金钱卡只能使用一次
 * - dp[i] = 凑出金额i所需的最少卡牌数
 * - 搜索范围：[amount, amount + MAX_DEVIATION]（允许最多超出50M）
 * - 优先返回恰好凑出的方案，否则返回超出最少的方案
 *
 * 同时提供一个快速贪心版本 calculateQuick() 作为备选方案。
 */
public class OptimalPaymentCalculator {
    /** 允许的最大超额支付金额（单位：M），防止DP数组过大 */
    private static final int MAX_DEVIATION = 50;

    /**
     * 使用动态规划计算最优支付方案
     *
     * 算法步骤：
     * 1. 将所有金钱卡按面值从大到小排序
     * 2. 使用DP计算从0到maxAmount每个金额所需的最少卡牌数
     * 3. 在 [amount, amount+50] 范围内找到最接近目标且卡数最少的方案
     * 4. 回溯DP路径找出具体使用了哪些卡牌
     *
     * @param bank 玩家的银行（含有所有金钱卡）
     * @param amount 需要支付的金额（单位：M）
     * @return 被选中的金钱卡列表（使用最少卡牌数凑出的支付方案）
     */
    public static List<Card> calculate(Bank bank, int amount) {
        List<Card> allCards = new ArrayList<>(bank.getAllMoneyCards());
        if (allCards.isEmpty()) return Collections.emptyList();

        // 按面值从大到小排序，优先使用大面值卡片
        allCards.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        int totalAmount = allCards.stream().mapToInt(Card::getValue).sum();
        if (totalAmount < amount) return Collections.emptyList();

        // DP数组上限 = 目标金额 + 允许的最大超支（避免DP数组过大）
        int maxAmount = Math.min(amount + MAX_DEVIATION, totalAmount);
        int[] dp = new int[maxAmount + 1];       // dp[i] = 凑出金额i的最少卡牌数
        int[] prev = new int[maxAmount + 1];     // prev[i] = 凑出金额i时，前一状态的金额
        Card[] usedCard = new Card[maxAmount + 1]; // usedCard[i] = 凑出金额i时，最后使用的那张卡牌

        // 初始化DP：dp[0]=0（凑0元需要0张卡），其他为无穷大
        Arrays.fill(dp, Integer.MAX_VALUE);
        dp[0] = 0;

        // 0/1背包：每张卡只能用一次
        for (Card card : allCards) {
            int val = card.getValue();
            // 倒序遍历，保证每张卡最多被使用一次
            for (int i = maxAmount; i >= val; i--) {
                if (dp[i - val] != Integer.MAX_VALUE && dp[i - val] + 1 < dp[i]) {
                    dp[i] = dp[i - val] + 1;
                    prev[i] = i - val;
                    usedCard[i] = card;
                }
            }
        }

        // 找最优支付金额：在 [amount, maxAmount] 范围内找卡牌数最少的
        int bestAmount = -1;
        int bestCardCount = Integer.MAX_VALUE;

        for (int i = amount; i <= maxAmount; i++) {
            if (dp[i] != Integer.MAX_VALUE && dp[i] < bestCardCount) {
                bestCardCount = dp[i];
                bestAmount = i;
                if (i == amount) break;  // 找到精确匹配就立即停止
            }
        }

        if (bestAmount == -1) return Collections.emptyList();

        // 回溯DP路径，找出实际使用的卡牌
        List<Card> result = new ArrayList<>();
        int current = bestAmount;
        while (current > 0 && usedCard[current] != null) {
            result.add(usedCard[current]);
            current = prev[current];
        }

        return result;
    }

    /**
     * 快速贪心支付方案（备用）
     *
     * 优先使用大面值卡牌凑目标金额，不能精确凑出时补充额外的卡牌。
     * 速度更快但结果可能不是最优（可能使用比DP方案更多的卡牌）。
     *
     * @param bank 玩家的银行
     * @param amount 需要支付的金额
     * @return 被选中的金钱卡列表
     */
    public static List<Card> calculateQuick(Bank bank, int amount) {
        List<Card> allCards = new ArrayList<>(bank.getAllMoneyCards());
        allCards.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        List<Card> result = new ArrayList<>();
        int remaining = amount;

        // 第一轮：按面值从大到小选择，尽量逼近目标金额
        for (Card card : allCards) {
            if (card.getValue() <= remaining) {
                result.add(card);
                remaining -= card.getValue();
            }
        }

        // 第二轮：如果还没凑够，继续加卡直到满足
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
