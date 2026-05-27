package com.monopolydeal.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Player class - represents a player in the game
 *
 * Each player has:
 * - Basic info: id (unique identifier), nickname, avatar, ready (ready state)
 * - Hand: list of cards held but not yet played
 * - Bank: stores played money cards for payments
 * - PropertyZone: stores played property cards, grouped by color
 * - Turn state: playsUsed (number of plays used), isActivePlayer (whether it's this player's turn)
 * - Special state: doubleRentActive (whether double rent effect is active)
 *
 * Design notes:
 * - Uses CopyOnWriteArrayList for hand to support concurrent-safe iteration
 * - Bank and PropertyZone are composed objects held by Player
 * - equals/hashCode based on player ID
 */
public class Player {
    /** Player unique identifier (corresponds to the network connection's clientId) */
    private final String id;
    /** Player nickname (for display) */
    private final String nickname;
    /** Player avatar identifier */
    private String avatar;
    /** Whether the player is ready (toggled in lobby via ready button) */
    private boolean ready;
    /** Whether the player is connected (network connection status) */
    private boolean connected;

    // ==================== Game State ====================

    /** Hand - list of cards held but not yet played */
    private final List<Card> hand;
    /** Bank - stores played money cards */
    private final Bank bank;
    /** Property zone - stores played property cards */
    private final PropertyZone propertyZone;
    /** Number of plays used this turn (max 3 per turn) */
    private int playsUsed;
    /** Whether this is the current active player (it's this player's turn) */
    private boolean isActivePlayer;
    /** Whether double rent effect is active (next rent charge is doubled) */
    private boolean doubleRentActive;

    /**
     * Create a new player
     * @param id unique identifier (clientId assigned by the server)
     * @param nickname player nickname
     */
    public Player(String id, String nickname) {
        this.id = id;
        this.nickname = nickname;
        this.ready = false;
        this.connected = true;
        this.hand = new CopyOnWriteArrayList<>();  // Thread-safe list
        this.bank = new Bank();
        this.propertyZone = new PropertyZone();
        this.playsUsed = 0;
        this.isActivePlayer = false;
        this.doubleRentActive = false;
    }

    // ==================== Basic Info Getters/Setters ====================

    public String getId() { return id; }
    public String getNickname() { return nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    // ==================== Hand Operations ====================

    /** Get a read-only list of the hand */
    public List<Card> getHand() { return Collections.unmodifiableList(hand); }
    /** Get the hand card count */
    public int getHandCount() { return hand.size(); }

    /** Get the bank */
    public Bank getBank() { return bank; }
    /** Get the property zone */
    public PropertyZone getPropertyZone() { return propertyZone; }

    // ==================== Turn State ====================

    public int getPlaysUsed() { return playsUsed; }
    public void setPlaysUsed(int playsUsed) { this.playsUsed = playsUsed; }
    public boolean isActivePlayer() { return isActivePlayer; }
    public void setActivePlayer(boolean activePlayer) { isActivePlayer = activePlayer; }
    public boolean isDoubleRentActive() { return doubleRentActive; }
    public void setDoubleRentActive(boolean doubleRentActive) {
        this.doubleRentActive = doubleRentActive;
    }

    // ==================== Hand Management Methods ====================

    /** Add a card to the hand */
    public void addCardToHand(Card card) {
        hand.add(card);
    }

    /** Remove a card from the hand by object reference, returns null if removal fails */
    public Card removeCardFromHand(Card card) {
        return hand.remove(card) ? card : null;
    }

    /** Remove a card from the hand by index */
    public Card removeCardFromHand(int index) {
        return hand.remove(index);
    }

    /** Check whether the hand contains the specified card (by object comparison) */
    public boolean hasCard(Card card) {
        return hand.contains(card);
    }

    /** Check whether the hand contains a card with the specified ID */
    public boolean hasCardId(String cardId) {
        return hand.stream().anyMatch(c -> c.getId().equals(cardId));
    }

    /** Find a card in the hand by card ID */
    public Card findCardById(String cardId) {
        return hand.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    // ==================== Game Progress Methods ====================

    /** Get the number of complete property sets (from property zone stats) */
    public int getCompleteSetsCount() {
        return propertyZone.getCompleteSetsCount();
    }

    /** Maximum plays per turn (fixed at 3) */
    public int getMaxPlays() {
        return GameConstants.MAX_PLAYS_PER_TURN;
    }

    /** Remaining plays available this turn */
    public int getRemainingPlays() {
        return getMaxPlays() - playsUsed;
    }

    /** Whether the player can still play cards this turn */
    public boolean canPlay() {
        return getRemainingPlays() > 0;
    }

    /** Increment plays used count by 1 */
    public void incrementPlaysUsed() {
        this.playsUsed++;
    }

    /**
     * Reset turn state
     * Called at the start of a new turn, clears play count and disables double rent effect
     */
    public void resetTurnState() {
        setPlaysUsed(0);
        setDoubleRentActive(false);
    }

    /**
     * Whether the player needs to discard
     * At end of turn, if hand size exceeds the limit (7 cards), discarding is required
     * @return true=needs to discard, false=no discard needed
     */
    public boolean needsToDiscard() {
        return hand.size() > GameConstants.MAX_HAND_SIZE;
    }

    // ==================== equals / hashCode ====================

    /** Equality based on player ID */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id);
    }

    /** Hash code based on player ID */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
