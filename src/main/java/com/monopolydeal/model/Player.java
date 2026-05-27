package com.monopolydeal.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Player class - represents a player in the game
 *
 * Each player has:
 * - Basic info: id (unique identifier), nickname, avatar, ready (ready status)
 * - Hand: list of unplayed cards held
 * - Bank: stores played money cards for payments
 * - PropertyZone: stores played property cards, grouped by color
 * - Turn state: playsUsed (number of plays used), isActivePlayer (whether it's this player's turn)
 * - Special state: doubleRentActive (whether double rent effect is active)
 *
 * Design notes:
 * - Uses CopyOnWriteArrayList for hand storage, supporting concurrent-safe traversal
 * - Bank and PropertyZone are composite objects owned by Player
 * - equals/hashCode based on player ID
 */
public class Player {
    /** Player unique identifier (corresponds to network connection's clientId) */
    private final String id;
    /** Player nickname (display purpose) */
    private final String nickname;
    /** Player avatar identifier */
    private String avatar;
    /** Whether the player is ready (clicked ready button in lobby) */
    private boolean ready;
    /** Whether the player is connected (network connection status) */
    private boolean connected;

    // ==================== Game State ====================

    /** Hand list - unplayed cards held */
    private final List<Card> hand;
    /** Bank - stores played money cards */
    private final Bank bank;
    /** Property zone - stores played property cards */
    private final PropertyZone propertyZone;
    /** Number of plays used in the current turn (max 3 per turn) */
    private int playsUsed;
    /** Whether this is the current active player */
    private boolean isActivePlayer;
    /** Whether double rent effect is active (next rent charge is doubled) */
    private boolean doubleRentActive;

    /**
     * Creates a new player
     * @param id unique identifier (from server-assigned clientId)
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

    /** Gets a read-only list of hand cards */
    public List<Card> getHand() { return Collections.unmodifiableList(hand); }
    /** Gets the hand card count */
    public int getHandCount() { return hand.size(); }

    /** Gets the bank */
    public Bank getBank() { return bank; }
    /** Gets the property zone */
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

    /** Adds a card to the hand */
    public void addCardToHand(Card card) {
        hand.add(card);
    }

    /** Removes a card from the hand by card object */
    public Card removeCardFromHand(Card card) {
        hand.remove(card);
        return card;
    }

    /** Removes a card from the hand by index */
    public Card removeCardFromHand(int index) {
        return hand.remove(index);
    }

    /** Checks whether the hand contains the specified card (by object comparison) */
    public boolean hasCard(Card card) {
        return hand.contains(card);
    }

    /** Checks whether the hand contains a card with the specified ID */
    public boolean hasCardId(String cardId) {
        return hand.stream().anyMatch(c -> c.getId().equals(cardId));
    }

    /** Finds a card in the hand by its ID */
    public Card findCardById(String cardId) {
        return hand.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    // ==================== Game Progress Methods ====================

    /** Gets the number of complete property sets (from property zone statistics) */
    public int getCompleteSetsCount() {
        return propertyZone.getCompleteSetsCount();
    }

    /** Maximum plays per turn (fixed at 3) */
    public int getMaxPlays() {
        return GameConstants.MAX_PLAYS_PER_TURN;
    }

    /** Remaining plays available in the current turn */
    public int getRemainingPlays() {
        return getMaxPlays() - playsUsed;
    }

    /** Whether the player can still play cards this turn */
    public boolean canPlay() {
        return getRemainingPlays() > 0;
    }

    /** Increments the plays used count by 1 */
    public void incrementPlaysUsed() {
        this.playsUsed++;
    }

    /**
     * Resets the turn state
     * Called at the start of a new turn; resets play count and clears double rent effect
     */
    public void resetTurnState() {
        setPlaysUsed(0);
        setDoubleRentActive(false);
    }

    /**
     * Whether the player needs to discard
     * At the end of a turn, if hand size exceeds the max (7 cards), a discard is needed
     * @return true=discard needed, false=not needed
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
