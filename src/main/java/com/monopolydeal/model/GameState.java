package com.monopolydeal.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GameState class — Data Transfer Object (DTO) for syncing game state from server to client.
 *
 * Each GAME_STATE_UPDATE message contains a JSON-serialized GameState object.
 * GameState holds all observable game state: room info, game phase, active player,
 * all player state snapshots (PlayerState), and recent action history (ActionRecord).
 *
 * Privacy design: a player's hand card details (handCards) are only visible to that player;
 * other players only see hand count (handCount). This is enforced via viewerId filtering
 * in createGameState().
 *
 * Inner classes:
 * - PlayerState: a single player's state snapshot (hand count, bank balance, complete sets, etc.)
 * - CardInfo: lightweight card info for serialization
 * - ActionRecord: action history entry
 */
public class GameState {
    // ==================== Basic Game Info ====================

    /** Room code (6 uppercase alphanumeric characters) */
    private String roomCode;
    /** Current game phase (INIT/DRAW/PLAY/END/DISCARD/GAME_OVER) */
    private GamePhase phase;
    /** Current active player ID (whose turn it is) */
    private String activePlayerId;
    /** Current player index in the player list */
    private int currentPlayerIndex;
    /** Turn count */
    private int turnNumber;
    /** Current turn start timestamp (milliseconds) */
    private long turnStartTime;
    /** Game start timestamp (milliseconds) */
    private long gameStartTime;
    /** Cards remaining in draw pile */
    private int drawPileSize;
    /** Cards in discard pile */
    private int discardPileSize;
    /** All player state snapshots: key=playerId, value=PlayerState */
    private final Map<String, PlayerState> playerStates;
    /** Action history list */
    private final List<ActionRecord> actionHistory;
    /** Whether the game has started */
    private boolean gameStarted;
    /** Whether the game is over */
    private boolean gameOver;
    /** Winner ID */
    private String winnerId;
    /** Winner nickname */
    private String winnerNickname;
    /** Current viewer ID (recipient of this GameState) */
    private String viewerId;

    /** Constructor — initializes default state */
    public GameState() {
        this.phase = GamePhase.INIT;
        this.activePlayerId = "";
        this.currentPlayerIndex = 0;
        this.turnNumber = 0;
        this.turnStartTime = 0;
        this.gameStartTime = 0;
        this.drawPileSize = 0;
        this.discardPileSize = 0;
        this.playerStates = new LinkedHashMap<>();  // Preserve player join order
        this.actionHistory = new ArrayList<>();
        this.gameStarted = false;
        this.gameOver = false;
        this.winnerId = "";
        this.winnerNickname = "";
        this.viewerId = "";
    }

    // ==================== Getters/Setters ====================

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

    public String getViewerId() { return viewerId; }
    public void setViewerId(String viewerId) { this.viewerId = viewerId; }

    // ==================== Player State Management ====================

    /** Add a player state snapshot */
    public void addPlayerState(String playerId, PlayerState state) {
        playerStates.put(playerId, state);
    }

    /** Get a specific player's state snapshot */
    public PlayerState getPlayerState(String playerId) {
        return playerStates.get(playerId);
    }

    /** Get a read-only map of all player state snapshots */
    public Map<String, PlayerState> getAllPlayerStates() {
        return Collections.unmodifiableMap(playerStates);
    }

    /** Get the number of players */
    public int getPlayerCount() {
        return playerStates.size();
    }

    // ==================== Action History Management ====================

    /** Set the action history (replaces existing entries) */
    public void setActionHistory(List<ActionRecord> actions) {
        actionHistory.clear();
        actionHistory.addAll(actions);
    }

    /** Get a read-only list of action history entries */
    public List<ActionRecord> getActionHistory() {
        return Collections.unmodifiableList(actionHistory);
    }

    // ==================== Inner Class: Player State Snapshot ====================

    /**
     * PlayerState — a single player's state snapshot.
     *
     * Contains all observable state for a player at a point in time.
     * Privacy: handCards is only populated when this player is the GameState recipient.
     */
    public static class PlayerState {
        private String playerId;               // Player ID
        private String nickname;               // Nickname
        private boolean isReady;               // Whether readied up
        private boolean isConnected;           // Whether online
        private boolean isActivePlayer;        // Whether this is the current turn's active player
        private int handCount;                 // Hand size (visible to all players)
        private int bankTotal;                 // Total bank balance (visible to all players)
        private Map<Integer, Integer> bankDenominations; // Bank denomination distribution
        private int propertyCount;             // Total property cards
        private int completeSets;              // Complete property set count
        private Map<String, Integer> propertyColorCounts; // Property counts by color
        private int playsUsed;                 // Plays used this turn
        private int remainingPlays;            // Remaining plays this turn
        private boolean doubleRentActive;      // Whether double rent effect is active
        private List<CardInfo> handCards;      // Hand card details (only visible to this player)
        private List<CardInfo> propertyCards; // Property zone details (public info for all players)
        private String avatar;                 // Avatar identifier

        /** Constructor — initializes defaults */
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
            this.remainingPlays = GameConstants.MAX_PLAYS_PER_TURN;  // Initially 3 plays per turn
            this.doubleRentActive = false;
            this.handCards = new ArrayList<>();
            this.propertyCards = new ArrayList<>();
            this.avatar = "";
        }

        // ==================== Getters/Setters ====================

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

        public List<CardInfo> getPropertyCards() {
            return Collections.unmodifiableList(propertyCards);
        }
        public void setPropertyCards(List<CardInfo> propertyCards) {
            this.propertyCards = new ArrayList<>(propertyCards);
        }

        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }

    // ==================== Inner Class: Card Info (Lightweight Transport Object) ====================

    /**
     * CardInfo — lightweight card information.
     *
     * Used for transmitting card info over the network instead of the full Card object.
     * Contains all data needed for client-side card rendering.
     */
    public static class CardInfo {
        private String cardId;       // Unique card ID
        private String cardName;     // Card name
        private String cardType;     // Card type (MONEY/PROPERTY/RENT/ACTION)
        private String color;        // Card color
        private int value;           // Monetary value (only valid for money cards)
        private String description;  // Description text
        private String wildColor;    // Chosen color for wild property (null if not set)
        private boolean inCompleteSet; // Whether this card belongs to a complete set

        /** Default constructor */
        public CardInfo() {
            this.cardId = "";
            this.cardName = "";
            this.cardType = "";
            this.color = "";
            this.value = 0;
            this.description = "";
            this.wildColor = null;
            this.inCompleteSet = false;
        }

        /** Create CardInfo from a Card object (for serialization) */
        public CardInfo(Card card) {
            this.cardId = card.getId();
            this.cardName = card.getName();
            this.cardType = card.getType().name();
            this.color = card.getColor().name();
            this.value = card.getValue();
            this.description = card.getDescription();
            this.wildColor = card.getWildColor() != null ? card.getWildColor().name() : null;
            this.inCompleteSet = false;
        }

        // ==================== Getters/Setters ====================

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

        public boolean isInCompleteSet() { return inCompleteSet; }
        public void setInCompleteSet(boolean inCompleteSet) { this.inCompleteSet = inCompleteSet; }
    }

    // ==================== Inner Class: Action History Record ====================

    /**
     * ActionRecord — action history entry.
     *
     * Records every operation in the game (draw, play, discard, rent collection, game over, etc.).
     * Used on the client side to display the operation log.
     */
    public static class ActionRecord {
        private int index;             // Action sequence number
        private String playerId;       // Executor ID
        private String playerNickname; // Executor nickname
        private String actionType;     // Action type (DRAW/PLAY_MONEY/PLAY_PROPERTY/RENT, etc.)
        private String targetPlayer;   // Target player nickname (empty string if no target)
        private int amount;            // Amount involved (0 if no monetary amount)
        private String details;        // Detailed description
        private long timestamp;        // Timestamp in milliseconds

        /** Default constructor */
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

        /** Parameterized constructor */
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

        // ==================== Getters/Setters ====================

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
