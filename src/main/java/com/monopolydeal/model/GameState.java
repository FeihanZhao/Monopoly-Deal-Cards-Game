package com.monopolydeal.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 游戏状态类 - 数据传输对象（DTO），用于将游戏状态从服务器同步到客户端
 *
 * 每个 GAME_STATE_UPDATE 消息都包含一个序列化为JSON的GameState对象。
 * GameState包含游戏的全部可观察状态：房间信息、游戏阶段、活跃玩家、
 * 所有玩家的状态快照（PlayerState）、以及最近的行动历史记录（ActionRecord）。
 *
 * 重要隐私设计：玩家的手牌详情（handCards）仅对玩家本人可见，
 * 其他玩家只能看到手牌数量（handCount）。这通过 createGameState() 中的
 * viewerId 过滤实现。
 *
 * 内部类：
 * - PlayerState：单个玩家的状态快照（手牌数、银行余额、完整组合数等）
 * - CardInfo：卡牌的轻量级信息（用于序列化传输）
 * - ActionRecord：行动历史记录条目
 */
public class GameState {
    // ==================== 基本游戏信息 ====================

    /** 房间代码（6位大写字母数字） */
    private String roomCode;
    /** 当前游戏阶段（INIT/DRAW/PLAY/END/DISCARD/GAME_OVER） */
    private GamePhase phase;
    /** 当前活跃玩家ID（轮到谁操作） */
    private String activePlayerId;
    /** 当前玩家在玩家列表中的索引 */
    private int currentPlayerIndex;
    /** 回合计数 */
    private int turnNumber;
    /** 当前回合开始时间戳（毫秒） */
    private long turnStartTime;
    /** 游戏开始时间戳（毫秒） */
    private long gameStartTime;
    /** 抽牌堆剩余卡牌数 */
    private int drawPileSize;
    /** 弃牌堆卡牌数 */
    private int discardPileSize;
    /** 所有玩家的状态快照 key=playerId, value=PlayerState */
    private final Map<String, PlayerState> playerStates;
    /** 行动历史记录列表 */
    private final List<ActionRecord> actionHistory;
    /** 游戏是否已开始 */
    private boolean gameStarted;
    /** 游戏是否已结束 */
    private boolean gameOver;
    /** 获胜者ID */
    private String winnerId;
    /** 获胜者昵称 */
    private String winnerNickname;
    /** 当前查看者的ID（此GameState的接收者） */
    private String viewerId;

    /** 构造函数 - 初始化默认状态 */
    public GameState() {
        this.phase = GamePhase.INIT;
        this.activePlayerId = "";
        this.currentPlayerIndex = 0;
        this.turnNumber = 0;
        this.turnStartTime = 0;
        this.gameStartTime = 0;
        this.drawPileSize = 0;
        this.discardPileSize = 0;
        this.playerStates = new LinkedHashMap<>();  // 保持玩家加入顺序
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

    // ==================== 玩家状态管理 ====================

    /** 添加玩家状态快照 */
    public void addPlayerState(String playerId, PlayerState state) {
        playerStates.put(playerId, state);
    }

    /** 获取指定玩家的状态快照 */
    public PlayerState getPlayerState(String playerId) {
        return playerStates.get(playerId);
    }

    /** 获取所有玩家状态快照的只读映射 */
    public Map<String, PlayerState> getAllPlayerStates() {
        return Collections.unmodifiableMap(playerStates);
    }

    /** 获取玩家数量 */
    public int getPlayerCount() {
        return playerStates.size();
    }

    // ==================== 行动历史管理 ====================

    /** 设置行动历史记录（覆盖原有记录） */
    public void setActionHistory(List<ActionRecord> actions) {
        actionHistory.clear();
        actionHistory.addAll(actions);
    }

    /** 获取行动历史记录的只读列表 */
    public List<ActionRecord> getActionHistory() {
        return Collections.unmodifiableList(actionHistory);
    }

    // ==================== 内部类：玩家状态快照 ====================

    /**
     * PlayerState - 单个玩家的状态快照
     *
     * 包含该玩家在某个时刻的全部可观察状态。
     * 隐私保护：handCards字段仅在该玩家是当前GameState的接收者时才填充。
     */
    public static class PlayerState {
        private String playerId;               // 玩家ID
        private String nickname;               // 昵称
        private boolean isReady;               // 是否已准备
        private boolean isConnected;           // 是否在线
        private boolean isActivePlayer;        // 是否是当前回合的活跃玩家
        private int handCount;                 // 手牌数量（所有玩家可见）
        private int bankTotal;                 // 银行总余额（所有玩家可见）
        private Map<Integer, Integer> bankDenominations; // 银行中各面值金钱卡分布
        private int propertyCount;             // 地产卡总数
        private int completeSets;              // 完整地产组合数
        private Map<String, Integer> propertyColorCounts; // 各颜色地产卡数量
        private int playsUsed;                 // 已使用的出牌数
        private int remainingPlays;            // 剩余可出牌数
        private boolean doubleRentActive;      // 双倍租金效果是否激活
        private List<CardInfo> handCards;      // 手牌详情（仅玩家本人可见）
        private String avatar;                 // 头像标识

        /** 构造函数 - 初始化默认值 */
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
            this.remainingPlays = GameConstants.MAX_PLAYS_PER_TURN;  // 初始每回合最多3次出牌
            this.doubleRentActive = false;
            this.handCards = new ArrayList<>();
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

        public String getAvatar() { return avatar; }
        public void setAvatar(String avatar) { this.avatar = avatar; }
    }

    // ==================== 内部类：卡牌信息（轻量级传输对象） ====================

    /**
     * CardInfo - 卡牌的轻量级信息
     *
     * 用于在网络中传输卡牌信息，避免传输完整的Card对象。
     * 包含卡牌在客户端渲染所需的全部数据。
     */
    public static class CardInfo {
        private String cardId;       // 卡牌唯一ID
        private String cardName;     // 卡牌名称
        private String cardType;     // 卡牌类型（MONEY/PROPERTY/RENT/ACTION）
        private String color;        // 卡牌颜色
        private int value;           // 金钱面值（仅金钱卡有效）
        private String description;  // 描述文本
        private String wildColor;    // 万能地产的选定颜色（null表示未选定）

        /** 默认构造函数 */
        public CardInfo() {
            this.cardId = "";
            this.cardName = "";
            this.cardType = "";
            this.color = "";
            this.value = 0;
            this.description = "";
            this.wildColor = null;
        }

        /** 从Card对象创建CardInfo（用于序列化传输） */
        public CardInfo(Card card) {
            this.cardId = card.getId();
            this.cardName = card.getName();
            this.cardType = card.getType().name();
            this.color = card.getColor().name();
            this.value = card.getValue();
            this.description = card.getDescription();
            this.wildColor = card.getWildColor() != null ? card.getWildColor().name() : null;
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
    }

    // ==================== 内部类：行动历史记录 ====================

    /**
     * ActionRecord - 行动历史记录条目
     *
     * 记录游戏中的每一次操作（抽牌、出牌、弃牌、收租、游戏结束等）。
     * 在客户端用于显示操作日志。
     */
    public static class ActionRecord {
        private int index;             // 行动序号
        private String playerId;       // 执行者ID
        private String playerNickname; // 执行者昵称
        private String actionType;     // 行动类型（DRAW/PLAY_MONEY/PLAY_PROPERTY/RENT等）
        private String targetPlayer;   // 目标玩家昵称（无目标则为空字符串）
        private int amount;            // 涉及金额（无金额则为0）
        private String details;        // 详细描述
        private long timestamp;        // 发生时间戳（毫秒）

        /** 默认构造函数 */
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

        /** 带参数的构造函数 */
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
