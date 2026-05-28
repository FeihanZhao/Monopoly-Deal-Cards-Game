package com.monopolydeal.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Player class - Represents a player in the game
 *
 * Each player has:
 * - Basic info: unique ID, nickname, avatar, ready status
 * - Hand cards: unplayed cards in hand
 * - Bank: stored money cards for payments
 * - Property Zone: played property cards grouped by color
 * - Turn status: played count, active player flag
 * - Special status: double rent effect flag
 *
 * Design notes:
 * - CopyOnWriteArrayList for thread-safe hand card traversal
 * - Bank and PropertyZone are composite objects
 * - equals/hashCode based on player ID
 */
public class Player {
    /** Unique player ID (matches client connection ID) */
    private final String id;
    /** Player display name */
    private final String nickname;
    /** Player avatar identifier */
    private String avatar;
    /** Ready status in game lobby */
    private boolean ready;
    /** Network connection status */
    private boolean connected;

    // ==================== Game State ====================

    /** Hand cards - unplayed cards held by the player */
    private final List<Card> hand;
    /** Bank - stores played money cards */
    private final Bank bank;
    /** Property zone - stores played property cards */
    private final PropertyZone propertyZone;
    /** Number of plays used this turn (max 3 per turn) */
    private int playsUsed;
    /** Whether it's this player's turn to act */
    private boolean isActivePlayer;
    /** Whether double rent effect is active */
    private boolean doubleRentActive;

    /**
     * Create a new player
     * @param id Unique identifier (server-assigned client ID)
     * @param nickname Player display name
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

    // ==================== Hand Card Operations ====================

    /** Get read-only hand card list */
    public List<Card> getHand() { return Collections.unmodifiableList(hand); }
    /** Get number of cards in hand */
    public int getHandCount() { return hand.size(); }

    /** Get player's bank */
    public Bank getBank() { return bank; }
    /** Get player's property zone */
    public PropertyZone getPropertyZone() { return propertyZone; }

    // ==================== Turn Status ====================

    public int getPlaysUsed() { return playsUsed; }
    public void setPlaysUsed(int playsUsed) { this.playsUsed = playsUsed; }
    public boolean isActivePlayer() { return isActivePlayer; }
    public void setActivePlayer(boolean activePlayer) { isActivePlayer = activePlayer; }
    public boolean isDoubleRentActive() { return doubleRentActive; }
    public void setDoubleRentActive(boolean doubleRentActive) {
        this.doubleRentActive = doubleRentActive;
    }

    // ==================== Hand Card Management ====================

    /** Add a card to hand */
    public void addCardToHand(Card card) {
        hand.add(card);
    }

    /** Remove a card from hand by object */
    public Card removeCardFromHand(Card card) {
        hand.remove(card);
        return card;
    }

    /** Remove a card from hand by index */
    public Card removeCardFromHand(int index) {
        return hand.remove(index);
    }

    /** Check if hand contains the specified card */
    public boolean hasCard(Card card) {
        return hand.contains(card);
    }

    /** Check if hand contains a card with the given ID */
    public boolean hasCardId(String cardId) {
        return hand.stream().anyMatch(c -> c.getId().equals(cardId));
    }

    /** Find a card in hand by ID */
    public Card findCardById(String cardId) {
        return hand.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    // ==================== Game Progress ====================

    /** Get number of complete property sets */
    public int getCompleteSetsCount() {
        return propertyZone.getCompleteSetsCount();
    }

    /** Maximum plays per turn (fixed at 3) */
    public int getMaxPlays() {
        return GameConstants.MAX_PLAYS_PER_TURN;
    }

    /** Get remaining plays for current turn */
    public int getRemainingPlays() {
        return getMaxPlays() - playsUsed;
    }

    /** Check if player can play a card this turn */
    public boolean canPlay() {
        return getRemainingPlays() > 0;
    }

    /** Increment used play count by 1 */
    public void incrementPlaysUsed() {
        this.playsUsed++;
    }

    /**
     * Reset turn state
     * Called at start of new turn: reset play count and clear double rent
     */
    public void resetTurnState() {
        setPlaysUsed(0);
        setDoubleRentActive(false);
    }

    /**
     * Check if player needs to discard cards
     * Hand size exceeds maximum (7) at turn end
     * @return true = need to discard, false = no need
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
