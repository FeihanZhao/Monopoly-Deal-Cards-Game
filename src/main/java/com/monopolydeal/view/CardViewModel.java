package com.monopolydeal.view;

/**
 * 卡牌视图模型 — CardRenderer 的纯数据载体
 *
 * 在 GamePanel.updateLocalHand() 中从 JSON 解析一次，之后所有视图组件
 * 通过 getter 访问字段，不再直接依赖 Gson/JsonObject。
 */
public class CardViewModel {

    private final String cardId;
    private final String cardName;
    private final String cardType;   // MONEY, PROPERTY, ACTION, RENT
    private final String color;      // 颜色键名，如 "BROWN", "WILD", "NONE"
    private final int    value;      // 金钱面值（非金钱卡 = 0）

    public CardViewModel(String cardId, String cardName, String cardType,
                         String color, int value) {
        this.cardId   = cardId;
        this.cardName = cardName;
        this.cardType = cardType;
        this.color    = color;
        this.value    = value;
    }

    // ==================== Getters ====================

    public String getCardId()   { return cardId; }
    public String getCardName() { return cardName; }
    public String getCardType() { return cardType; }
    public String getColor()    { return color; }
    public int    getValue()    { return value; }

    // ==================== 类型判断便捷方法 ====================

    public boolean isWild()     { return "WILD".equals(color); }
    public boolean isMoney()    { return "MONEY".equals(cardType); }
    public boolean isProperty() { return "PROPERTY".equals(cardType); }
    public boolean isAction()   { return "ACTION".equals(cardType); }
    public boolean isRent()     { return "RENT".equals(cardType); }
}
