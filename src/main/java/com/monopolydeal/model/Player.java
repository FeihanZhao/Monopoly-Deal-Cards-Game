package com.monopolydeal.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Player class — represents a player in the game.
 *
 * Each player has:
 * - Basic info: id (unique), nickname, avatar, ready status
 * - Hand: list of unplayed cards held
 * - Bank: holds played money cards, used for payments
 * - PropertyZone: holds played property cards, grouped by color
 * - Turn state: playsUsed, isActivePlayer
 * - Special state: doubleRentActive
 *
 * Design notes:
 * - Uses CopyOnWriteArrayList for hand storage, supporting thread-safe iteration
 * - Bank and PropertyZone are composite objects owned by Player
 * - equals/hashCode based on player ID
 */
public class Player {
    /** Unique player identifier (corresponds to network connection clientId) */
    private final String id;
    /** Player nickname (for display) */
    private final String nickname;
    /** Player avatar identifier */
    private String avatar;
    /** Whether the player has readied up (clicked ready in lobby) */
    private boolean ready;
    /** Whether the player is connected (network connection status) */
    private boolean connected;

    // ==================== Game State ====================

    /** Hand list — unplayed cards held */
    private final List<Card> hand;
    /** Bank — holds played money cards */
    private final Bank bank;
    /** PropertyZone — holds played property cards */
    private final PropertyZone propertyZone;
    /** Number of plays used this turn (max 3 non-action plays per turn) */
    private int playsUsed;
    /** Whether this is the currently active player (whose turn it is) */
    private boolean isActivePlayer;
    /** Whether double rent is active (next rent charge is doubled) */
    private boolean doubleRentActive;

    /**
     * Create a new player.
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

    /** Get read-only view of the hand */
    public List<Card> getHand() { return Collections.unmodifiableList(hand); }
    /** Get hand size */
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

    /** Remove a card from hand by object reference; returns null on failure */
    public Card removeCardFromHand(Card card) {
        return hand.remove(card) ? card : null;
    }

    /** Remove a card from hand by index */
    public Card removeCardFromHand(int index) {
        return hand.remove(index);
    }

    /** Check whether the hand contains a specific card (by object equality) */
    public boolean hasCard(Card card) {
        return hand.contains(card);
    }

    /** Check whether the hand contains a card with the given ID */
    public boolean hasCardId(String cardId) {
        return hand.stream().anyMatch(c -> c.getId().equals(cardId));
    }

    /** Find a card in hand by its ID */
    public Card findCardById(String cardId) {
        return hand.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    // ==================== Game Progress Methods ====================

    /** Get the number of complete property sets (from PropertyZone) */
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

    /** Increment the plays-used counter */
    public void incrementPlaysUsed() {
        this.playsUsed++;
    }

    /**
     * Reset turn state.
     * Called at the start of a new turn; clears play count and double rent.
     */
    public void resetTurnState() {
        setPlaysUsed(0);
        setDoubleRentActive(false);
    }

    /**
     * Whether the player needs to discard.
     * At end of turn, if hand size exceeds the limit (7), discarding is required.
     * @return true=needs to discard, false=does not
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
