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
 * - 取款（removeCards）：按指定金额取出金钱卡，使用最优支付策略
 * - 余额查询（getTotal）：获取银行总余额
 *
 * 当需要支付租金时，系统会尝试用最少数量的金钱卡凑出支付金额（通过 OptimalPaymentCalculator）。
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
     * 将一张金钱卡存入银行
     * @param moneyCard 必须是金钱卡类型，否则抛出异常
     * @throws IllegalArgumentException 如果传入的不是金钱卡
     */
    public void deposit(Card moneyCard) {
        if (!moneyCard.isMoneyCard()) {
            throw new IllegalArgumentException("只有金钱卡可以存入银行");
        }
        int denomination = moneyCard.getValue();
        moneyCards.get(denomination).add(moneyCard);
        allMoneyCards.add(moneyCard);
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
     * 从银行中取出指定金额的金钱卡
     *
     * 使用 OptimalPaymentCalculator 计算最优支付方案：
     * 优先使用最少数量的卡牌凑出目标金额，尽量减少多余支付。
     *
     * @param amount 需要取出的金额（单位：M/百万）
     * @return 被取出的金钱卡列表（从银行中移除）
     * @throws InsufficientFundsException 如果余额不足
     */
    public List<Card> removeCards(int amount) throws InsufficientFundsException {
        if (getTotal() < amount) {
            throw new InsufficientFundsException("余额不足。需要 " + amount +
                    "M，但只有 " + getTotal() + "M");
        }

        // 使用最优支付计算器找到最佳支付组合
        List<Card> payment = OptimalPaymentCalculator.calculate(this, amount);

        // 从银行中移除以选中的金钱卡
        for (Card card : payment) {
            allMoneyCards.remove(card);
            moneyCards.get(card.getValue()).remove(card);
        }

        return payment;
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
