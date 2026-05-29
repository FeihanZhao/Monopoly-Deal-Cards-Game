package com.monopolydeal.model;

import java.util.*;

/**
 * 银行类 - 管理玩家的金钱卡存储
 *
 * 每个玩家拥有一个银行，用于存放已打出的金钱卡。
 * 银行按面值（1M/2M/3M/4M/5M/10M）分组存储金钱卡。
 *
 * 核心功能：
 * - 存款（deposit）：将金钱卡存入银行
 * - 取款（removeCardsByIds）：玩家手动选择卡牌支付，校验总面值>=欠款，不找零
 * - 兜底取款（removeCardsFallback）：超时时自动选择卡牌支付（贪心从大到小）
 * - 余额查询（getTotal）：获取银行总余额
 *
 * 不找零规则：如果玩家用总面值超过欠款额的卡牌支付，超出部分被没收，不退还。
 */
public class Bank {
    /** 按面值分组的金钱卡映射表 key=面值(1/2/3/4/5/10), value=该面值的金钱卡列表 */
    private final Map<Integer, List<Card>> moneyCards;
    /** 所有金钱卡的扁平列表（方便快速计算总金额） */
    private final List<Card> allMoneyCards;

    /**
     * 构造函数 - 初始化空银行
     * 为每种金钱面值（1M/2M/3M/4M/5M/10M）创建空列表
     */
    public Bank() {
        this.moneyCards = new HashMap<>();
        this.allMoneyCards = new ArrayList<>();
        for (int denom : GameConstants.MONEY_DENOMINATIONS) {
            moneyCards.put(denom, new ArrayList<>());
        }
    }

    /**
     * 将一张有面值的卡牌存入银行作为货币资产
     * 行动卡、租金卡一旦存入即丧失原有效果，仅作为货币计算
     * @param card 面值 > 0 的任意卡牌
     * @throws IllegalArgumentException 如果卡牌面值为 0
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
     * 查询银行中某面值的金钱卡数量
     * @param denomination 面值（1/2/3/4/5/10）
     * @return 该面值的卡片数量
     */
    public int getCount(int denomination) {
        return moneyCards.getOrDefault(denomination, Collections.emptyList()).size();
    }

    /**
     * 获取银行总余额（所有金钱卡面值之和）
     * @return 总金额（单位：M/百万）
     */
    public int getTotal() {
        return allMoneyCards.stream().mapToInt(Card::getValue).sum();
    }

    /** 获取银行中所有金钱卡的只读列表 */
    public List<Card> getAllMoneyCards() {
        return Collections.unmodifiableList(allMoneyCards);
    }

    /**
     * 玩家手动选择卡牌支付 —— 校验总面值并移除
     *
     * 校验规则：
     * 1. 所有 cardIds 必须全部存在于银行中
     * 2. 选中卡牌的总面值必须 >= amount
     * 3. 不找零：所有被选中的卡牌全部移除并转移，总面值超出部分被没收
     *
     * @param cardIds 玩家选择的卡牌ID列表
     * @param amount  需要支付的金额
     * @return 被移除的卡牌列表
     * @throws IllegalArgumentException 如果卡牌不存在或总面值不足
     */
    public List<Card> removeCardsByIds(List<String> cardIds, int amount) {
        if (cardIds == null || cardIds.isEmpty()) {
            throw new IllegalArgumentException("No cards selected");
        }
        // 防止重复卡牌ID攻击：同一张卡不能支付两次
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
     * 超时兜底自动支付 —— 贪心算法从大到小选卡
     *
     * 用于债务人超时未响应时，服务器自动选取卡牌支付。
     * 按面值降序依次取卡直到总和 >= amount。
     * 调用方应先通过 canPay(amount) 检查余额。
     *
     * @param amount 需要支付的金额
     * @return 被移除的卡牌列表
     * @throws InsufficientFundsException 如果余额不足
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

    /** 根据卡牌ID在银行中查找卡牌 */
    public Card findCardById(String cardId) {
        return allMoneyCards.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查是否有足够余额支付指定金额
     * @param amount 需要支付的金额
     * @return true=余额足够，false=余额不足
     */
    public boolean canPay(int amount) {
        return getTotal() >= amount;
    }

    /** 清空银行（移除所有金钱卡） */
    public void clear() {
        moneyCards.values().forEach(List::clear);
        allMoneyCards.clear();
    }

    /**
     * 获取每种面值的计数映射
     * @return key=面值, value=该面值的卡牌数量
     */
    public Map<Integer, Integer> getDenominationCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int denom : GameConstants.MONEY_DENOMINATIONS) {
            counts.put(denom, getCount(denom));
        }
        return counts;
    }

    /**
     * 余额不足异常
     * 当玩家银行余额不足以支付所需金额时抛出
     */
    public static class InsufficientFundsException extends Exception {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }
}
