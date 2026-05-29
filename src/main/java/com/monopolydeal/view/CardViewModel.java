package com.monopolydeal.view;

/**
 * Card view model - encapsulates card data needed for UI display
 *
 * Acts as a bridge between server-side card JSON data and UI components (CardRenderer).
 * Contains basic card properties: ID, name, type, color, and monetary value.
 */
public class CardViewModel {

    /** Unique card identifier */
    private final String cardId;
    /** Card display name */
    private final String cardName;
    /** Card type (MONEY/PROPERTY/ACTION/RENT) */
    private final String cardType;
    /** Card color key (corresponds to CardColor enum name, e.g. BROWN, WILD) */
    private final String color;
    /** Monetary value (only money cards have a value; 0 for other types) */
    private final int value;

    /**
     * Constructor
     * @param cardId unique card identifier
     * @param cardName card display name
     * @param cardType card type
     * @param color color key
     * @param value monetary value
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
