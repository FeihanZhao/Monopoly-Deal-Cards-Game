package com.monopolydeal.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Player {
    private final String id;
    private final String nickname;
    private String avatar;
    private boolean ready;
    private boolean connected;

    // Game state
    private final List<Card> hand;
    private final Bank bank;
    private final PropertyZone propertyZone;
    private int playsUsed;
    private boolean isActivePlayer;
    private boolean doubleRentActive;

    public Player(String id, String nickname) {
        this.id = id;
        this.nickname = nickname;
        this.ready = false;
        this.connected = true;
        this.hand = new CopyOnWriteArrayList<>();
        this.bank = new Bank();
        this.propertyZone = new PropertyZone();
        this.playsUsed = 0;
        this.isActivePlayer = false;
        this.doubleRentActive = false;
    }

    public String getId() { return id; }
    public String getNickname() { return nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    public List<Card> getHand() { return Collections.unmodifiableList(hand); }
    public int getHandCount() { return hand.size(); }
    public Bank getBank() { return bank; }
    public PropertyZone getPropertyZone() { return propertyZone; }
    public int getPlaysUsed() { return playsUsed; }
    public void setPlaysUsed(int playsUsed) { this.playsUsed = playsUsed; }
    public boolean isActivePlayer() { return isActivePlayer; }
    public void setActivePlayer(boolean activePlayer) { isActivePlayer = activePlayer; }
    public boolean isDoubleRentActive() { return doubleRentActive; }
    public void setDoubleRentActive(boolean doubleRentActive) {
        this.doubleRentActive = doubleRentActive;
    }

    public void addCardToHand(Card card) {
        hand.add(card);
    }

    public Card removeCardFromHand(Card card) {
        hand.remove(card);
        return card;
    }

    public Card removeCardFromHand(int index) {
        return hand.remove(index);
    }

    public boolean hasCard(Card card) {
        return hand.contains(card);
    }

    public boolean hasCardId(String cardId) {
        return hand.stream().anyMatch(c -> c.getId().equals(cardId));
    }

    public Card findCardById(String cardId) {
        return hand.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    public int getCompleteSetsCount() {
        return propertyZone.getCompleteSetsCount();
    }

    public int getMaxPlays() {
        return GameConstants.MAX_PLAYS_PER_TURN;
    }

    public int getRemainingPlays() {
        return getMaxPlays() - playsUsed;
    }

    public boolean canPlay() {
        return getRemainingPlays() > 0;
    }

    public void incrementPlaysUsed() {
        this.playsUsed++;
    }

    public void resetTurnState() {
        this.playsUsed = 0;
        this.doubleRentActive = false;
    }

    public boolean needsToDiscard() {
        return hand.size() > GameConstants.MAX_HAND_SIZE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}