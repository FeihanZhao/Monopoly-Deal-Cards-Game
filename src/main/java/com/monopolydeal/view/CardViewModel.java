package com.monopolydeal.view;

/**
 * 卡牌视图模型 - 封装卡牌在UI层展示所需的数据
 *
 * 作为服务端下发的卡牌JSON数据与UI组件（CardRenderer）之间的桥梁。
 * 包含卡牌的基本属性：ID、名称、类型、颜色和面值。
 */
public class CardViewModel {

    /** 卡牌唯一标识符 */
    private final String cardId;
    /** 卡牌显示名称 */
    private final String cardName;
    /** 卡牌类型（MONEY/PROPERTY/ACTION/RENT） */
    private final String cardType;
    /** 卡牌颜色键（对应CardColor枚举名称，如BROWN、WILD等） */
    private final String color;
    /** 金钱面值（仅金钱卡有值，其他类型为0） */
    private final int value;

    /**
     * 构造函数
     * @param cardId 卡牌唯一标识符
     * @param cardName 卡牌显示名称
     * @param cardType 卡牌类型
     * @param color 颜色键
     * @param value 金钱面值
     */
    public CardViewModel(String cardId, String cardName, String cardType, String color, int value) {
        this.cardId = cardId;
        this.cardName = cardName;
        this.cardType = cardType;
        this.color = color;
        this.value = value;
    }

    public String getCardId() {
        return cardId;
    }

    public String getCardName() {
        return cardName;
    }

    public String getCardType() {
        return cardType;
    }

    public String getColor() {
        return color;
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "CardViewModel{" +
                "cardId='" + cardId + '\'' +
                ", cardName='" + cardName + '\'' +
                ", cardType='" + cardType + '\'' +
                ", color='" + color + '\'' +
                ", value=" + value +
                '}';
    }
}
