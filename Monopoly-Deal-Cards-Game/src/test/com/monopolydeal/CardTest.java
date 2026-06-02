package com.monopolydeal;


import com.monopolydeal.model.Card;
import com.monopolydeal.model.CardColor;
import com.monopolydeal.model.CardType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CardTest {

    @Test
    void testMoneyCardCanBeUsedAsMoney() {
        Card money = new Card("m1", CardType.MONEY, "5M", 5, CardColor.NONE, "5M");
        assertTrue(money.canBeUsedAsMoney());
        assertTrue(money.isMoneyCard());
    }

    @Test
    void testPropertyCardCannotBeUsedAsMoney() {
        Card prop = new Card("p1", CardType.PROPERTY, "Brown", 0,
                CardColor.BROWN, "Brown Property");
        assertFalse(prop.canBeUsedAsMoney());
    }

    @Test
    void testActionCardCanBeUsedAsMoney() {
        Card action = new Card("a1", CardType.ACTION, "Pass Go", 1,
                CardColor.NONE, "Pass Go");
        assertTrue(action.canBeUsedAsMoney());
    }

    @Test
    void testWildPropertyDetected() {
        Card wild = new Card("w1", CardType.PROPERTY, "Multi-Color Wild", 0,
                CardColor.WILD, "Multi-Color Wild");
        assertTrue(wild.isWildProperty());
    }

    @Test
    void testWildColorAllowed() {
        Card wild = new Card("w1", CardType.PROPERTY, "Red/Yellow Wild", 0,
                CardColor.WILD, "Red/Yellow Wild");
        assertTrue(wild.isColorAllowed(CardColor.RED));
        assertTrue(wild.isColorAllowed(CardColor.YELLOW));
        assertFalse(wild.isColorAllowed(CardColor.BLUE));
    }

    @Test
    void testEffectiveColorReturnsWildColorWhenSet() {
        Card wild = new Card("w1", CardType.PROPERTY, "Red/Yellow Wild", 0,
                CardColor.WILD, "Red/Yellow Wild");
        wild.setWildColor(CardColor.RED);
        assertEquals(CardColor.RED, wild.getEffectiveColor());
    }

    @Test
    void testEffectiveColorReturnsOwnColorWhenNoWild() {
        Card prop = new Card("p1", CardType.PROPERTY, "Brown", 0,
                CardColor.BROWN, "Brown Property");
        assertEquals(CardColor.BROWN, prop.getEffectiveColor());
    }

    @Test
    void testTransferCopyHasDifferentId() {
        Card original = new Card("orig", CardType.MONEY, "5M", 5, CardColor.NONE, "5M");
        Card copy = original.transferCopy();
        assertNotEquals(original.getId(), copy.getId());
        assertEquals(original.getValue(), copy.getValue());
    }

    @Test
    void testClonePreservesId() {
        Card original = new Card("orig", CardType.MONEY, "5M", 5, CardColor.NONE, "5M");
        Card clone = original.clone();
        assertEquals(original.getId(), clone.getId());
    }

    @Test
    void testEqualityById() {
        Card c1 = new Card("same", CardType.MONEY, "5M", 5, CardColor.NONE, "5M");
        Card c2 = new Card("same", CardType.MONEY, "3M", 3, CardColor.NONE, "3M");
        assertEquals(c1, c2);
    }
}