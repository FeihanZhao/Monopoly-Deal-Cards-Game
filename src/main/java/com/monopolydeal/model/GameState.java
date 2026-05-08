package com.monopolydeal.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameState {
    private String roomCode;
    private GamePhase phase;
    private String activePlayerId;
    private int currentPlayerIndex;
    private int turnNumber;
    private long turnStartTime;
    private long gameStartTime;
    private int drawPileSize;
    private int discardPileSize;
    private final Map<String, PlayerState> playerStates;
    private final List<ActionRecord> actionHistory;
    private boolean gameStarted;
    private boolean gameOver;
    private String winnerId;
    private String winnerNickname;

    public GameState() {
        this.phase = GamePhase.INIT;
        this.activePlayerId = "";
        this.currentPlayerIndex = 0;
        this.turnNumber = 0;
        this.turnStartTime = 0;
        this.gameStartTime = 0;
        this.drawPileSize = 0;
        this.discardPileSize = 0;
        this.playerStates = new LinkedHashMap<>();
        this.actionHistory = new ArrayList<>();
        this.gameStarted = false;
        this.gameOver = false;
        this.winnerId = "";
        this.winnerNickname = "";
    }

    public String getRoomCode() { return roomCode; }
    public void setRoomCode(String roomCode) { this.roomCode = roomCode; }

    public GamePhase getPhase() { return phase; }
    public void setPhase(GamePhase phase) { this.phase = phase; }

    public String getActivePlayerId() { return activePlayerId; }
    public void setActivePlayerId(String activePlayerId) { this.activePlayerId = activePlayerId; }

    public int getCurrentPlayerIndex() { return currentPlayerIndex; }
    public void setCurrentPlayerIndex(int currentPlayerIndex) { this.currentPlayerIndex = currentPlayerIndex; }

    public int getTurnNumber() { return turnNumber; }
    public void setTurnNumber(int turnNumber) { this.turnNumber = turnNumber; }

    public long getTurnStartTime() { return turnStartTime; }
    public void setTurnStartTime(long turnStartTime) { this.turnStartTime = turnStartTime; }

    public long getGameStartTime() { return gameStartTime; }
    public void setGameStartTime(long gameStartTime) { this.gameStartTime = gameStartTime; }

    public int getDrawPileSize() { return drawPileSize; }
    public void setDrawPileSize(int drawPileSize) { this.drawPileSize = drawPileSize; }

    public int getDiscardPileSize() { return discardPileSize; }
    public void setDiscardPileSize(int discardPileSize) { this.discardPileSize = discardPileSize; }

    public boolean isGameStarted() { return gameStarted; }
    public void setGameStarted(boolean gameStarted) { this.gameStarted = gameStarted; }

    public boolean isGameOver() { return gameOver; }
    public void setGameOver(boolean gameOver) { this.gameOver = gameOver; }

    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }

    public String getWinnerNickname() { return winnerNickname; }
    public void setWinnerNickname(String winnerNickname) { this.winnerNickname = winnerNickname; }

    public void addPlayerState(String playerId, PlayerState state) {
        playerStates.put(playerId, state);
    }

    public void updatePlayerState(String playerId, PlayerState state) {
        playerStates.put(playerId, state);
    }

    public void removePlayerState(String playerId) {
        playerStates.remove(playerId);
    }

    public PlayerState getPlayerState(String playerId) {
        return playerStates.get(playerId);
    }

    public Map<String, PlayerState> getAllPlayerStates() {
        return Collections.unmodifiableMap(playerStates);
    }

    public int getPlayerCount() {
        return playerStates.size();
    }

    public void addAction(ActionRecord action) {
        actionHistory.add(0, action);
        if (actionHistory.size() > 50) {
            actionHistory.remove(actionHistory.size() - 1);
        }
    }

    public void setActionHistory(List<ActionRecord> actions) {
        actionHistory.clear();
        actionHistory.addAll(actions);
    }

    public List<ActionRecord> getActionHistory() {
        return Collections.unmodifiableList(actionHistory);
    }

    public List<ActionRecord> getRecentActions(int count) {
        int endIndex = Math.min(count, actionHistory.size());
        return new ArrayList<>(actionHistory.subList(0, endIndex));
    }

    public void clearActionHistory() {
        actionHistory.clear();
    }

    public static class PlayerState {
        private String playerId;
        private String nickname;
        private boolean isReady;
        private boolean isConnected;
        private boolean isActivePlayer;
        private int handCount;
        private int bankTotal;
        private Map<Integer, Integer> bankDenominations;
        private int propertyCount;
        private int completeSets;
        private Map<String, Integer> propertyColorCounts;
        private int playsUsed;
        private int remainingPlays;
        private boolean doubleRentActive;
        private List<CardInfo> handCards;
        private String avatar;

        public PlayerState() {
            this.playerId = "";
            this.nickname = "";
            this.isReady = false;
            this.isConnected = true;
            this.isActivePlayer = false;
            this.handCount = 0;
            this.bankTotal = 0;
            this.bankDenominations = new HashMap<>();
            this.propertyCount = 0;
            this.completeSets = 0;
            this.propertyColorCounts = new HashMap<>();
            this.playsUsed = 0;
            this.remainingPlays = 3;
            this.doubleRentActive = false;
            this.handCards = new ArrayList<>();
            this.avatar = "";
        }

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }

        public boolean isReady() { return isReady; }
        public void setReady(boolean ready) { isReady = ready; }

        public boolean isConnected() { return isConnected; }
        public void setConnected(boolean connected) { isConnected = connected; }

        public boolean isActivePlayer() { return isActivePlayer; }
        public void setActivePlayer(boolean activePlayer) { isActivePlayer = activePlayer; }

        public int getHandCount() { return handCount; }
        public void setHandCount(int handCount) { this.handCount = handCount; }

        public int getBankTotal() { return bankTotal; }
        public void setBankTotal(int bankTotal) { this.bankTotal = bankTotal; }

        public Map<Integer, Integer> getBankDenominations() {
            return Collections.unmodifiableMap(bankDenominations);
        }
        public void setBankDenominations(Map<Integer, Integer> denominations) {
            this.bankDenominations = new HashMap<>(denominations);
        }

        public int getPropertyCount() { return propertyCount; }
        public void setPropertyCount(int propertyCount) { this.propertyCount = propertyCount; }

        public int getCompleteSets() { return completeSets; }
        public void setCompleteSets(int completeSets) { this.completeSets = completeSets; }

        public Map<String, Integer> getPropertyColorCounts() {
            return Collections.unmodifiableMap(propertyColorCounts);
        }
        public void setPropertyColorCounts(Map<String, Integer> colorCounts) {
            this.propertyColorCounts = new HashMap<>(colorCounts);
        }

        public int getPlaysUsed() { return playsUsed; }
        public void setPlaysUsed(int playsUsed) { this.playsUsed = playsUsed; }

        public int getRemainingPlays() { return remainingPlays; }
        public void setRemainingPlays(int remainingPlays) { this.remainingPlays = remainingPlays; }

        public boolean isDoubleRentActive() { return doubleRentActive; }
        public void setDoubleRentActive(boolean doubleRentActive) {
            this.doubleRentActive = doubleRentActive;
        }

        public List<CardInfo> getHandCards() {
            return Collections.unmodifiableList(handCards);
        }
        public void setHandCards(List<CardInfo> handCards) {
            this.handCards = new ArrayList<>(handCards);
        }

        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }

    public static class CardInfo {
        private String cardId;
        private String cardName;
        private String cardType;
        private String color;
        private int value;
        private String description;
        private String wildColor;

        public CardInfo() {
            this.cardId = "";
            this.cardName = "";
            this.cardType = "";
            this.color = "";
            this.value = 0;
            this.description = "";
            this.wildColor = null;
        }

        public CardInfo(Card card) {
            this.cardId = card.getId();
            this.cardName = card.getName();
            this.cardType = card.getType().name();
            this.color = card.getColor().name();
            this.value = card.getValue();
            this.description = card.getDescription();
            this.wildColor = card.getWildColor() != null ? card.getWildColor().name() : null;
        }

        public String getCardId() { return cardId; }
        public void setCardId(String cardId) { this.cardId = cardId; }

        public String getCardName() { return cardName; }
        public void setCardName(String cardName) { this.cardName = cardName; }

        public String getCardType() { return cardType; }
        public void setCardType(String cardType) { this.cardType = cardType; }

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }

        public int getValue() { return value; }
        public void setValue(int value) { this.value = value; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getWildColor() { return wildColor; }
        public void setWildColor(String wildColor) { this.wildColor = wildColor; }
    }

    public static class ActionRecord {
        private int index;
        private String playerId;
        private String playerNickname;
        private String actionType;
        private String targetPlayer;
        private int amount;
        private String details;
        private long timestamp;

        public ActionRecord() {
            this.index = 0;
            this.playerId = "";
            this.playerNickname = "";
            this.actionType = "";
            this.targetPlayer = "";
            this.amount = 0;
            this.details = "";
            this.timestamp = System.currentTimeMillis();
        }

        public ActionRecord(int index, String playerId, String playerNickname,
                            String actionType, String targetPlayer, int amount, String details) {
            this.index = index;
            this.playerId = playerId;
            this.playerNickname = playerNickname;
            this.actionType = actionType;
            this.targetPlayer = targetPlayer;
            this.amount = amount;
            this.details = details;
            this.timestamp = System.currentTimeMillis();
        }

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }

        public String getPlayerNickname() { return playerNickname; }
        public void setPlayerNickname(String playerNickname) { this.playerNickname = playerNickname; }

        public String getActionType() { return actionType; }
        public void setActionType(String actionType) { this.actionType = actionType; }

        public String getTargetPlayer() { return targetPlayer; }
        public void setTargetPlayer(String targetPlayer) { this.targetPlayer = targetPlayer; }

        public int getAmount() { return amount; }
        public void setAmount(int amount) { this.amount = amount; }

        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    }
}