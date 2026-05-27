package com.monopolydeal.model;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

/**
 * 卡牌类 - 代表游戏中的一张卡牌
 *
 * 每张卡牌拥有不可变的身份标识（id、type、name、value、color、description）
 * 和一个可变的 wildColor（仅万能地产卡在使用时由玩家设定颜色）。
 *
 * 卡牌类型决定其使用方式：
 * - MONEY（金钱卡）：存入银行，面值1M-10M
 * - PROPERTY（地产卡）：放置在物业区，按颜色组队
 * - RENT（租金卡）：向其他玩家收取租金
 * - ACTION（行动卡）：执行特殊效果
 *
 * 设计要点：Card 是可克隆的（Cloneable），支持复制构造函数用于测试和深拷贝。
 */
public class Card implements Cloneable {
    /** 卡牌唯一标识符（UUID前8位） */
    private final String id;
    /** 卡牌类型（金钱/地产/租金/行动） */
    private final CardType type;
    /** 卡牌名称（如 "Deal Breaker"、"5M"、"Red Property"） */
    private final String name;
    /** 金钱面值（仅金钱卡有效，其他卡为0） - 单位：M（百万） */
    private final int value;
    /** 卡牌颜色（地产颜色/双色租金颜色/WILD/NONE） */
    private final CardColor color;
    /** 卡牌描述文本 */
    private final String description;
    /** 万能地产卡的实际选定颜色（仅万能地产卡非null，由玩家在出牌时设定） */
    private CardColor wildColor;

    /**
     * 主构造函数 - 创建一张新卡牌
     *
     * @param id 唯一标识符
     * @param type 卡牌类型
     * @param name 卡牌名称
     * @param value 金钱面值（非金钱卡传0）
     * @param color 卡牌颜色
     * @param description 描述文本
     */
    public Card(String id, CardType type, String name, int value,
                CardColor color, String description) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.value = value;
        this.color = color;
        this.description = description;
        this.wildColor = null;  // 默认没有选定万能颜色，由玩家在放置地产时指定
    }

    /**
     * 复制构造函数 - 创建一张卡牌的深拷贝（保留原ID）
     * 用于测试和GameState序列化时的卡牌克隆
     * @param other 要复制的卡牌
     */
    public Card(Card other) {
        this.id = other.id;
        this.type = other.type;
        this.name = other.name;
        this.value = other.value;
        this.color = other.color;
        this.description = other.description;
        this.wildColor = other.wildColor;
    }

    /**
     * 转账复制构造函数 - 创建内容相同但ID不同的卡牌副本
     * 用于卡牌在不同玩家间转移时避免ID冲突
     */
    private Card(Card template, String newId) {
        this.id = newId;
        this.type = template.type;
        this.name = template.name;
        this.value = template.value;
        this.color = template.color;
        this.description = template.description;
        this.wildColor = template.wildColor;
    }

    /** 克隆当前卡牌（深拷贝，保留原ID） */
    @Override
    public Card clone() {
        return new Card(this);
    }

    /** 创建带有新ID的转账副本（用于支付/交换等跨玩家转移） */
    public Card transferCopy() {
        return new Card(this, UUID.randomUUID().toString().substring(0, 8));
    }

    // ==================== Getters ====================

    public String getId() { return id; }
    public CardType getType() { return type; }
    public String getName() { return name; }
    public int getValue() { return value; }
    public CardColor getColor() { return color; }
    public String getDescription() { return description; }

    /** 获取万能地产卡选定的颜色（非万能卡返回null） */
    public CardColor getWildColor() { return wildColor; }
    /** 设置万能地产卡的实际颜色 */
    public void setWildColor(CardColor wildColor) { this.wildColor = wildColor; }

    /**
     * 获取卡牌的有效颜色
     * 如果玩家为万能地产卡选定了颜色，返回选定颜色；否则返回卡牌本身的颜色
     * @return 有效颜色
     */
    public CardColor getEffectiveColor() {
        return wildColor != null ? wildColor : color;
    }

    // ==================== 类型判断 ====================

    /** 是否是金钱卡（仅指MONEY类型） */
    public boolean isMoneyCard() { return type == CardType.MONEY; }
    /** 是否可存入银行作为货币使用（面值>0即可，含行动卡/租金卡） */
    public boolean canBeUsedAsMoney() { return value > 0; }
    /** 是否是地产卡 */
    public boolean isPropertyCard() { return type == CardType.PROPERTY; }
    /** 是否是行动卡 */
    public boolean isActionCard() { return type == CardType.ACTION; }
    /** 是否是租金卡 */
    public boolean isRentCard() { return type == CardType.RENT; }
    /** 是否是万能地产卡（地产卡且颜色为WILD） */
    public boolean isWildProperty() {
        return type == CardType.PROPERTY && color == CardColor.WILD;
    }

    // ==================== 万能卡颜色规则 ====================

    /** 万能地产卡名称 → 允许切换的颜色列表 */
    private static final Map<String, List<CardColor>> WILD_COLOR_RULES = new HashMap<>();
    static {
        WILD_COLOR_RULES.put("Multi-Color Wild", Arrays.asList(
                CardColor.BROWN, CardColor.LIGHT_BLUE, CardColor.PINK, CardColor.ORANGE,
                CardColor.RED, CardColor.YELLOW, CardColor.GREEN, CardColor.BLUE,
                CardColor.BLACK, CardColor.LIGHT_GREEN));
        WILD_COLOR_RULES.put("Dark Blue/Green Wild",
                Arrays.asList(CardColor.BLUE, CardColor.GREEN));
        WILD_COLOR_RULES.put("Brown/Light Blue Wild",
                Arrays.asList(CardColor.BROWN, CardColor.LIGHT_BLUE));
        WILD_COLOR_RULES.put("Orange/Pink Wild",
                Arrays.asList(CardColor.ORANGE, CardColor.PINK));
        WILD_COLOR_RULES.put("Red/Yellow Wild",
                Arrays.asList(CardColor.RED, CardColor.YELLOW));
        WILD_COLOR_RULES.put("Railroad/Utility Wild",
                Arrays.asList(CardColor.BLACK, CardColor.LIGHT_GREEN));
        WILD_COLOR_RULES.put("Green/Railroad Wild",
                Arrays.asList(CardColor.GREEN, CardColor.BLACK));
        WILD_COLOR_RULES.put("Light Blue/Railroad Wild",
                Arrays.asList(CardColor.LIGHT_BLUE, CardColor.BLACK));
    }

    /**
     * 校验该万能地产卡是否可以切换到目标颜色
     * 双色卡只能在其两个颜色间切换，十色卡可切换任意地产颜色
     * @param targetColor 目标颜色
     * @return true=允许切换，false=不在允许范围内
     */
    public boolean isColorAllowed(CardColor targetColor) {
        if (!isWildProperty()) return false;
        List<CardColor> allowed = WILD_COLOR_RULES.get(name);
        if (allowed == null) {
            // 未知万能卡类型：保守拒绝，防止利用未知名称绕过校验
            return false;
        }
        return allowed.contains(targetColor);
    }

    /**
     * 获取该万能地产卡可切换的颜色列表（只读）
     * @return 允许的颜色列表，非万能卡返回空列表
     */
    public List<CardColor> getAllowedColors() {
        if (!isWildProperty()) return Collections.emptyList();
        List<CardColor> allowed = WILD_COLOR_RULES.get(name);
        return allowed != null ? Collections.unmodifiableList(allowed) : Collections.emptyList();
    }

    // ==================== equals / hashCode / toString ====================

    /** 基于卡牌ID判断相等性 */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return Objects.equals(id, card.id);
    }

    /** 基于卡牌ID计算哈希值 */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /** 生成可读的卡牌描述字符串 */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (type == CardType.MONEY) {
            sb.append(" (").append(value).append("M)");
        }
        if (isWildProperty() && wildColor != null) {
            sb.append(" [").append(wildColor.getName()).append("]");
        }
        return sb.toString();
    }
}
