package com.monopolydeal;


import com.monopolydeal.model.Card;
import com.monopolydeal.model.CardColor;
import com.monopolydeal.model.CardType;
import com.monopolydeal.model.PropertyZone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PropertyZoneTest {
    private PropertyZone zone;
    private Card brownCard1;
    private Card brownCard2;

    @BeforeEach
    void setUp() {
        zone = new PropertyZone();
        brownCard1 = new Card("b1", CardType.PROPERTY, "Brown", 0,
                CardColor.BROWN, "Brown Property");
        brownCard2 = new Card("b2", CardType.PROPERTY, "Brown", 0,
                CardColor.BROWN, "Brown Property");
    }

    @Test
    void testAddPropertyIncreasesCount() {
        zone.addProperty(brownCard1);
        assertEquals(1, zone.getPropertyCount(CardColor.BROWN));
    }

    @Test
    void testCompleteSetDetected() {
        zone.addProperty(brownCard1);
        zone.addProperty(brownCard2);
        // Brown requires 2 cards
        assertTrue(zone.getCompleteSets().contains(CardColor.BROWN));
        assertEquals(1, zone.getCompleteSetsCount());
    }

    @Test
    void testIncompleteSetNotCounted() {
        zone.addProperty(brownCard1);
        assertFalse(zone.getCompleteSets().contains(CardColor.BROWN));
    }

    @Test
    void testRemovePropertyReducesCount() {
        zone.addProperty(brownCard1);
        zone.removeProperty(brownCard1);
        assertEquals(0, zone.getPropertyCount(CardColor.BROWN));
    }

    @Test
    void testCanPlaceHouseOnCompleteSet() {
        zone.addProperty(brownCard1);
        zone.addProperty(brownCard2);
        assertTrue(zone.canPlaceHouse(CardColor.BROWN));
    }

    @Test
    void testCannotPlaceHouseOnIncompleteSet() {
        zone.addProperty(brownCard1);
        assertFalse(zone.canPlaceHouse(CardColor.BROWN));
    }

    @Test
    void testAddHouseIncreasesRent() {
        zone.addProperty(brownCard1);
        zone.addProperty(brownCard2);
        int baseRent = zone.getRentAmount(CardColor.BROWN);
        zone.addHouse(CardColor.BROWN);
        assertEquals(baseRent + 3, zone.getRentAmount(CardColor.BROWN));
    }

    @Test
    void testCanPlaceHotelAfterHouse() {
        zone.addProperty(brownCard1);
        zone.addProperty(brownCard2);
        zone.addHouse(CardColor.BROWN);
        assertTrue(zone.canPlaceHotel(CardColor.BROWN));
    }

    @Test
    void testCannotPlaceHotelWithoutHouse() {
        zone.addProperty(brownCard1);
        zone.addProperty(brownCard2);
        assertFalse(zone.canPlaceHotel(CardColor.BROWN));
    }

    @Test
    void testAddNonPropertyCardThrows() {
        Card moneyCard = new Card("m1", CardType.MONEY, "5M", 5, CardColor.NONE, "5M");
        assertThrows(IllegalArgumentException.class,
                () -> zone.addProperty(moneyCard));
    }

    @Test
    void testClearRemovesAll() {
        zone.addProperty(brownCard1);
        zone.addProperty(brownCard2);
        zone.clear();
        assertEquals(0, zone.getPropertyCount(CardColor.BROWN));
        assertEquals(0, zone.getCompleteSetsCount());
    }
}