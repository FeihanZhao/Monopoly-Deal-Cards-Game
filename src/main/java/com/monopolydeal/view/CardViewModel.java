package com.monopolydeal.view;

/**
 * Card view model — pure data carrier for CardRenderer.
 *
 * Parsed once from JSON in GamePanel.updateLocalHand(); all view components
 * then access fields via getters without depending directly on Gson/JsonObject.
 */
public class CardViewModel {

    private final String cardId;
    private final String cardName;
    private final String cardType;   // MONEY, PROPERTY, ACTION, RENT
    private final String color;      // Color key, e.g. "BROWN", "WILD", "NONE"
    private final int    value;      // Monetary value (0 for non-money cards)

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

    // ==================== Type-check convenience methods ====================

    public boolean isWild()     { return "WILD".equals(color); }
    public boolean isMoney()    { return "MONEY".equals(cardType); }
    public boolean isProperty() { return "PROPERTY".equals(cardType); }
    public boolean isAction()   { return "ACTION".equals(cardType); }
    public boolean isRent()     { return "RENT".equals(cardType); }
}
