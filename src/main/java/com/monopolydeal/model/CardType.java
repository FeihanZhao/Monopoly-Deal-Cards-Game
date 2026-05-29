package com.monopolydeal.model;

/**
 * 卡牌类型枚举类
 *
 * 定义了《大富翁纸牌游戏》中的四种卡牌类型：
 * - MONEY（金钱卡）：存入银行作为资金，用于支付租金等
 * - PROPERTY（地产卡）：放置在物业区组成颜色组合，集齐即可获胜
 * - RENT（租金卡）：向其他玩家收取租金
 * - ACTION（行动卡）：执行特殊效果的卡牌（如强行交易、债务收集等）
 */
public enum CardType {
    MONEY("Money"),        // 金钱卡 - 面值有1M/2M/3M/4M/5M/10M
    PROPERTY("Property"),  // 地产卡 - 分为11种纯地产颜色+万能地产
    RENT("Rent"),          // 租金卡 - 可针对特定颜色地产向其他玩家收租
    ACTION("Action");      // 行动卡 - 包含多种特殊效果

    /** 类型的显示名称 */
    private final String displayName;

    /**
     * 构造函数
     * @param displayName 类型的显示名称
     */
    CardType(String displayName) {
        this.displayName = displayName;
    }

    /** 获取类型的显示名称 */
    public String getDisplayName() {
        return displayName;
    }
}
