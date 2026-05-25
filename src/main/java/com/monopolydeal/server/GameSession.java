package com.monopolydeal.server;

import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.*;
import com.monopolydeal.server.GameRoom;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

/**
 * 游戏会话 - 管理一局完整游戏的运行逻辑
 *
 * 这是整个游戏的核心引擎，负责：
 * 1. 回合管理：轮流切换活跃玩家，管理抽牌→出牌→结束的完整回合生命周期
 * 2. 定时器：每回合30秒超时自动结束（10秒前发出警告）
 * 3. 游戏规则：执行所有卡牌效果（金钱、地产、租金、行动卡）
 * 4. 支付系统：处理玩家间的金钱转移，使用最优支付计算
 * 5. 胜利判定：每回合检查是否有玩家集齐3套完整地产
 * 6. 状态广播：每次操作后向所有玩家广播最新的GameState
 *
 * 回合生命周期：
 * 1. startNextTurn() - 检查胜利条件 → 切换到下一个在线玩家 → 自动抽牌 → 进入PLAY阶段 → 启动定时器
 * 2. handlePlayCard() - 玩家出牌（最多3次），每次出牌后检查是否还有剩余次数
 * 3. endTurn() / forceEndTurn() - 主动或强制结束回合 → 自动弃牌到7张上限 → 延迟1.5秒后开始下一回合
 */
public class GameSession {
    /** 所属的游戏房间 */
    private final GameRoom room;
    /** 玩家列表（按加入顺序排列） */
    private final List<Player> players;
    /** 牌堆（抽牌堆+弃牌堆） */
    private final Deck deck;
    /** 当前玩家在列表中的索引 */
    private int currentPlayerIndex;
    /** 当前活跃玩家（正在操作的玩家） */
    private Player activePlayer;
    /** 当前游戏阶段 */
    private GamePhase phase;
    /** 定时器线程池（用于回合超时控制） */
    private ScheduledExecutorService scheduler;
    /** 当前回合的定时器任务句柄（用于取消定时器） */
    private ScheduledFuture<?> turnTimer;
    /** 游戏是否正在运行 */
    private boolean gameRunning;
    /** 行动历史记录列表（最新的在前） */
    private final List<ActionRecord> actionHistory;
    /** 游戏开始时间戳（毫秒） */
    private long gameStartTime;
    /** Gson序列化器 */
    private final Gson gson;

    /**
     * 构造函数
     * @param room 所属游戏房间
     * @param players 玩家列表
     */
    public GameSession(GameRoom room, List<Player> players) {
        this.room = room;
        this.players = new CopyOnWriteArrayList<>(players);  // 线程安全列表
        this.deck = new Deck();
        this.currentPlayerIndex = -1;
        this.phase = GamePhase.INIT;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.gameRunning = false;
        this.actionHistory = new ArrayList<>();
        this.gameStartTime = 0;
        this.gson = new Gson();
    }

    /**
     * 启动游戏
     * 1. 给每位玩家发放初始手牌（5张）
     * 2. 广播初始游戏状态
     * 3. 开始第一个回合
     */
    public void start() {
        gameRunning = true;
        gameStartTime = System.currentTimeMillis();
        // 发放初始手牌
        for (Player player : players) {
            List<Card> initialHand = deck.drawMultiple(GameConstants.INITIAL_HAND_SIZE);
            initialHand.forEach(player::addCardToHand);
        }
        broadcastGameState();
        startNextTurn();  // 开始第一个回合
    }

    /**
     * 开始下一个回合
     * 1. 检查胜利条件
     * 2. 找到下一个在线玩家
     * 3. 自动抽取3张牌
     * 4. 进入PLAY阶段
     * 5. 启动30秒超时定时器
     */
    private void startNextTurn() {
        if (!gameRunning) return;

        // 检查是否有玩家获胜（集齐3套完整地产）
        Optional<Player> winner = checkWinner();
        if (winner.isPresent()) {
            endGame(winner.get());
            return;
        }

        // 找到下一个在线玩家（跳过已断线的玩家）
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            activePlayer = players.get(currentPlayerIndex);
        } while (!activePlayer.isConnected() && gameRunning);

        if (!gameRunning) return;

        // 设置活跃玩家状态
        activePlayer.setActivePlayer(true);
        activePlayer.resetTurnState();  // 重置出牌计数和双倍租金状态
        phase = GamePhase.DRAW;

        // 自动抽牌阶段
        int drawCount = Math.min(GameConstants.DRAW_COUNT, deck.getDrawPileSize());
        List<Card> drawnCards = deck.drawMultiple(drawCount);
        drawnCards.forEach(activePlayer::addCardToHand);
        recordAction(activePlayer.getId(), activePlayer.getNickname(), "DRAW", "", 0,
                "抽了 " + drawnCards.size() + " 张牌");

        phase = GamePhase.PLAY;
        broadcastGameState();
        startTurnTimer();  // 启动30秒回合定时器
    }

    /**
     * 启动回合超时定时器
     * 在30秒超时前10秒发出警告，超时后自动强制结束回合
     */
    private void startTurnTimer() {
        cancelTimer();
        // 20秒后发出警告，再10秒后强制结束
        turnTimer = scheduler.schedule(() -> {
            room.sendToPlayer(activePlayer.getId(), MessageProtocol.MessageType.TURN_TIMEOUT,
                    "{\"secondsRemaining\":" + GameConstants.TIMEOUT_WARNING_SECONDS + "}");
            scheduler.schedule(() -> {
                room.broadcast(MessageProtocol.MessageType.TURN_TIMEOUT,
                        "{\"playerId\":\"" + activePlayer.getId() + "\",\"reason\":\"回合超时\"}");
                forceEndTurn();
            }, GameConstants.TIMEOUT_WARNING_SECONDS, TimeUnit.SECONDS);
        }, GameConstants.TURN_TIMEOUT_SECONDS - GameConstants.TIMEOUT_WARNING_SECONDS, TimeUnit.SECONDS);
    }

    /** 取消当前的回合定时器 */
    private void cancelTimer() {
        if (turnTimer != null && !turnTimer.isCancelled()) {
            turnTimer.cancel(false);
        }
    }

    /**
     * 处理出牌请求 - 验证权限后根据action字段分派到对应的卡牌处理方法
     *
     * 支持的action类型：
     * - PLAY_MONEY：将金钱卡存入银行
     * - PLAY_PROPERTY：将地产卡放入物业区
     * - PLAY_RENT：使用租金卡收费
     * - PLAY_ACTION：使用行动卡执行特殊效果
     *
     * @param playerId 发出请求的玩家ID
     * @param payload 包含cardId、action以及额外参数（如颜色选择、目标玩家等）的JSON对象
     */
    public void handlePlayCard(String playerId, JsonObject payload) {
        // 权限验证：游戏必须运行中、必须是活跃玩家
        if (!gameRunning || activePlayer == null) return;
        if (!playerId.equals(activePlayer.getId())) return;
        if (!activePlayer.canPlay()) {
            sendError(playerId, "本回合已无出牌次数");
            return;
        }

        String cardId = payload.get("cardId").getAsString();
        String action = payload.get("action").getAsString();
        Card card = activePlayer.findCardById(cardId);

        if (card == null) {
            sendError(playerId, "手牌中未找到该卡牌");
            return;
        }

        try {
            boolean played = false;
            switch (action) {
                case "PLAY_MONEY":
                    played = playMoneyCard(card);       // 存入银行
                    break;
                case "PLAY_PROPERTY":
                    played = playPropertyCard(card, payload); // 放置地产
                    break;
                case "PLAY_RENT":
                    played = playRentCard(card, payload);    // 收取租金
                    break;
                case "PLAY_ACTION":
                    played = playActionCard(card, payload);  // 执行行动
                    break;
                default:
                    sendError(playerId, "未知的操作：" + action);
                    return;
            }

            if (played) {
                activePlayer.incrementPlaysUsed();  // 出牌计数+1
                recordAction(playerId, activePlayer.getNickname(), action, "", 0, card.getName());
                broadcastGameState();

                // 如果用完了所有出牌次数，自动结束回合
                if (activePlayer.getRemainingPlays() <= 0) {
                    scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            sendError(playerId, e.getMessage());
        }
    }

    /**
     * 执行金钱卡效果 - 从手牌中移除并存入银行
     * @param card 金钱卡
     * @return true=执行成功
     */
    private boolean playMoneyCard(Card card) {
        if (!card.isMoneyCard()) return false;
        activePlayer.removeCardFromHand(card);
        activePlayer.getBank().deposit(card);
        return true;
    }

    /**
     * 执行地产卡效果 - 将地产卡放入物业区
     * 如果是万能地产卡，支持玩家选择颜色
     * 放置后检查是否达到胜利条件（3套完整地产）
     *
     * @param card 地产卡
     * @param payload 可能包含color字段（万能地产的颜色选择）
     * @return true=执行成功
     */
    private boolean playPropertyCard(Card card, JsonObject payload) {
        if (!card.isPropertyCard()) return false;
        activePlayer.removeCardFromHand(card);

        // 万能地产卡：设置玩家选择的颜色
        if (card.isWildProperty() && payload.has("color")) {
            try {
                String colorName = payload.get("color").getAsString();
                card.setWildColor(CardColor.valueOf(colorName));
            } catch (IllegalArgumentException ignored) {}
        }

        activePlayer.getPropertyZone().addProperty(card);

        // 放置地产后检查胜利条件
        if (activePlayer.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
            endGame(activePlayer);
        }
        return true;
    }

    /**
     * 执行租金卡效果 - 向一个或多个玩家收取租金
     *
     * 租金计算逻辑：
     * 1. 确定租金颜色（普通租金卡有固定双色、万能租金需选择颜色）
     * 2. 计算基础租金 = 根据该颜色持有数量的基础费率
     * 3. 如果双倍租金激活，金额×2
     * 4. 双色租金向所有玩家收取，万能租金向指定单一目标收取
     *
     * @param card 租金卡
     * @param payload 可能包含color和targetPlayerId字段
     * @return true=执行成功
     */
    private boolean playRentCard(Card card, JsonObject payload) {
        if (!card.isRentCard()) return false;
        activePlayer.removeCardFromHand(card);
        deck.discard(card);

        // 确定租金颜色
        CardColor rentColor = CardColor.WILD;
        if (payload.has("color")) {
            try {
                rentColor = CardColor.valueOf(payload.get("color").getAsString());
            } catch (IllegalArgumentException e) {
                rentColor = CardColor.WILD;
            }
        }

        // 计算基础租金
        int baseRentAmount = 0;
        if (rentColor == CardColor.WILD) {
            baseRentAmount = 2;  // 万能租金基础2M
        } else {
            baseRentAmount = activePlayer.getPropertyZone().getRentAmount(rentColor);
            if (baseRentAmount == 0) baseRentAmount = 2;
        }

        // 双倍租金效果
        int rentAmount = activePlayer.isDoubleRentActive() ? baseRentAmount * 2 : baseRentAmount;
        activePlayer.setDoubleRentActive(false);  // 使用后清除效果

        // 判断是向所有玩家还是单个目标收取租金
        boolean isWildRent = card.getColor() == CardColor.WILD || card.getName().contains("Wild");

        if (isWildRent) {
            // 万能租金：向指定的单一目标收取
            String targetPlayerId = payload.has("targetPlayerId") ?
                    payload.get("targetPlayerId").getAsString() : "";
            Player targetPlayer = findPlayer(targetPlayerId);
            if (targetPlayer != null) {
                requirePayment(targetPlayer, activePlayer, rentAmount);
                recordAction(activePlayer.getId(), activePlayer.getNickname(), "RENT",
                        targetPlayer.getNickname(), rentAmount, "收取租金 " + rentAmount + "M");
            }
        } else {
            // 双色租金：向所有其他玩家收取
            for (Player player : players) {
                if (!player.equals(activePlayer)) {
                    requirePayment(player, activePlayer, rentAmount);
                }
            }
            recordAction(activePlayer.getId(), activePlayer.getNickname(), "RENT_ALL",
                    "", rentAmount, "向所有玩家收取 " + rentAmount + "M 租金");
        }
        return true;
    }

    /**
     * 执行行动卡效果 - 根据卡牌名称分派到具体的行动效果
     *
     * 支持的行动卡类型：
     * - Debt Collector（债务收集者）：向指定玩家收5M
     * - Birthday（生日）：所有玩家各付2M
     * - Deal Breaker（强行交易）：偷取一名玩家的完整地产组合
     * - Pass Go（通过起点）：额外抽2张牌
     * - Double Rent（双倍租金）：下次租金翻倍
     * - House（房屋）：在完整组合上建房（+1租金/栋，最多4栋）
     * - Hotel（酒店）：在4栋房屋上升级为酒店（+3租金）
     * - Forced Deal（强制交换）：与另一名玩家交换地产卡
     * - Sly Deal（偷袭）：偷取另一名玩家的单张地产卡
     * - Just Say No（拒绝）：抵消针对自己的行动卡效果
     *
     * @param card 行动卡
     * @param payload 可能包含targetPlayerId、color等额外参数
     * @return true=执行成功
     */
    private boolean playActionCard(Card card, JsonObject payload) {
        if (!card.isActionCard()) return false;
        activePlayer.removeCardFromHand(card);
        deck.discard(card);
        String actionName = card.getName();

        if (actionName.contains("Debt Collector")) {
            // 债务收集者：向指定玩家收取5M
            // 如果没有指定目标，默认选择第一个其他玩家
            String targetId = payload.has("targetPlayerId") ?
                    payload.get("targetPlayerId").getAsString() : "";
            Player target = findPlayer(targetId);
            if (target == null && !players.isEmpty()) {
                List<Player> targets = new ArrayList<>();
                for (Player p : players) {
                    if (!p.equals(activePlayer)) targets.add(p);
                }
                if (!targets.isEmpty()) target = targets.get(0);
            }
            if (target != null) {
                requirePayment(target, activePlayer, GameConstants.DEBT_COLLECTOR_AMOUNT);
                recordAction(activePlayer.getId(), activePlayer.getNickname(),
                        "DEBT_COLLECTOR", target.getNickname(),
                        GameConstants.DEBT_COLLECTOR_AMOUNT,
                        "收取 " + GameConstants.DEBT_COLLECTOR_AMOUNT + "M");
            }
        } else if (actionName.contains("Birthday")) {
            // 生日：所有其他玩家各付2M
            for (Player player : players) {
                if (!player.equals(activePlayer)) {
                    requirePayment(player, activePlayer, GameConstants.BIRTHDAY_AMOUNT);
                }
            }
            recordAction(activePlayer.getId(), activePlayer.getNickname(),
                    "BIRTHDAY", "", GameConstants.BIRTHDAY_AMOUNT,
                    "所有人支付 " + GameConstants.BIRTHDAY_AMOUNT + "M");
        } else if (actionName.contains("Deal Breaker")) {
            // 强行交易：偷取一名玩家的完整地产组合
            // 如果没有指定目标，自动找第一个有完整组合的玩家
            Player target = null;
            if (payload.has("targetPlayerId")) {
                target = findPlayer(payload.get("targetPlayerId").getAsString());
            } else {
                for (Player p : players) {
                    if (!p.equals(activePlayer) && p.getCompleteSetsCount() > 0) {
                        target = p;
                        break;
                    }
                }
            }
            if (target != null && target.getCompleteSetsCount() > 0) {
                List<CardColor> completeSets = target.getPropertyZone().getCompleteSets();
                if (!completeSets.isEmpty()) {
                    CardColor setToSteal = completeSets.get(0);
                    // 偷取该完整组合中的所有地产卡
                    List<Card> properties = new ArrayList<>(
                            target.getPropertyZone().getPropertiesByColor(setToSteal));
                    for (Card prop : properties) {
                        target.getPropertyZone().removeProperty(prop);
                        if (prop.isWildProperty()) prop.setWildColor(null);  // 重置万能颜色
                        activePlayer.getPropertyZone().addProperty(prop);
                    }
                    recordAction(activePlayer.getId(), activePlayer.getNickname(),
                            "DEAL_BREAKER", target.getNickname(), 0,
                            "偷取了完整组合：" + setToSteal.getName());
                    // 检查是否因此获胜
                    if (activePlayer.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
                        endGame(activePlayer);
                    }
                }
            }
        } else if (actionName.contains("Pass Go")) {
            // 通过起点：额外抽2张牌
            List<Card> drawnCards = deck.drawMultiple(2);
            drawnCards.forEach(activePlayer::addCardToHand);
            recordAction(activePlayer.getId(), activePlayer.getNickname(),
                    "PASS_GO", "", 0, "额外抽了2张牌");
        } else if (actionName.contains("Double")) {
            // 双倍租金：激活下一次租金翻倍效果
            activePlayer.setDoubleRentActive(true);
            recordAction(activePlayer.getId(), activePlayer.getNickname(),
                    "DOUBLE_RENT", "", 0, "下次租金翻倍");
        } else if (actionName.contains("House") && !actionName.contains("Hotel")) {
            // 房屋：在完整组合上建造房屋
            // 自动或手动选择颜色
            CardColor houseColor = null;
            if (payload.has("color")) {
                try {
                    houseColor = CardColor.valueOf(payload.get("color").getAsString());
                } catch (IllegalArgumentException ignored) {}
            }
            if (houseColor == null) {
                for (CardColor c : activePlayer.getPropertyZone().getCompleteSets()) {
                    if (activePlayer.getPropertyZone().canPlaceHouse(c)) {
                        houseColor = c;
                        break;
                    }
                }
            }
            if (houseColor != null && activePlayer.getPropertyZone().canPlaceHouse(houseColor)) {
                activePlayer.getPropertyZone().addHouse(houseColor);
                recordAction(activePlayer.getId(), activePlayer.getNickname(),
                        "HOUSE", "", 0, "在 " + houseColor.getName() + " 上建造了房屋");
            }
        } else if (actionName.contains("Hotel")) {
            // 酒店：在4栋房屋上升级为酒店
            CardColor hotelColor = null;
            if (payload.has("color")) {
                try {
                    hotelColor = CardColor.valueOf(payload.get("color").getAsString());
                } catch (IllegalArgumentException ignored) {}
            }
            if (hotelColor == null) {
                for (CardColor c : activePlayer.getPropertyZone().getCompleteSets()) {
                    if (activePlayer.getPropertyZone().canPlaceHotel(c)) {
                        hotelColor = c;
                        break;
                    }
                }
            }
            if (hotelColor != null && activePlayer.getPropertyZone().canPlaceHotel(hotelColor)) {
                activePlayer.getPropertyZone().addHotel(hotelColor);
                recordAction(activePlayer.getId(), activePlayer.getNickname(),
                        "HOTEL", "", 0, "在 " + hotelColor.getName() + " 上建造了酒店");
            }
        } else if (actionName.contains("Forced Deal")) {
            // 强制交换：与另一名玩家交换地产卡
            if (payload.has("targetPlayerId") && payload.has("myPropertyId") &&
                    payload.has("theirPropertyId")) {
                Player otherPlayer = findPlayer(payload.get("targetPlayerId").getAsString());
                if (otherPlayer != null) {
                    String myPropId = payload.get("myPropertyId").getAsString();
                    String theirPropId = payload.get("theirPropertyId").getAsString();
                    executeForcedDeal(activePlayer, otherPlayer, myPropId, theirPropId);
                }
            } else {
                // 无指定参数时的默认行为：自动选择
                Player target = null;
                for (Player p : players) {
                    if (!p.equals(activePlayer) && !p.getPropertyZone().getAllPropertyGroups().isEmpty()) {
                        target = p;
                        break;
                    }
                }
                if (target != null) {
                    Card myProp = null;
                    for (List<Card> group : activePlayer.getPropertyZone().getAllPropertyGroups().values()) {
                        if (!group.isEmpty()) { myProp = group.get(0); break; }
                    }
                    Card theirProp = null;
                    for (List<Card> group : target.getPropertyZone().getAllPropertyGroups().values()) {
                        if (!group.isEmpty()) { theirProp = group.get(0); break; }
                    }
                    if (myProp != null && theirProp != null) {
                        executeForcedDeal(activePlayer, target, myProp.getId(), theirProp.getId());
                    }
                }
            }
        } else if (actionName.contains("Sly Deal")) {
            // 偷袭：偷取一名玩家的单张地产卡（不能偷完整组合中的）
            if (payload.has("targetPlayerId") && payload.has("targetCardId")) {
                Player stealFrom = findPlayer(payload.get("targetPlayerId").getAsString());
                String stealCardId = payload.get("targetCardId").getAsString();
                if (stealFrom != null) executeSlyDeal(activePlayer, stealFrom, stealCardId);
            } else {
                // 自动选择目标：优先偷非完整组合的地产卡
                Player victim = null;
                Card toSteal = null;
                for (Player p : players) {
                    if (!p.equals(activePlayer)) {
                        for (List<Card> group : p.getPropertyZone().getAllPropertyGroups().values()) {
                            if (!group.isEmpty() && !p.getPropertyZone().getCompleteSets()
                                    .contains(group.get(0).getEffectiveColor())) {
                                victim = p;
                                toSteal = group.get(0);
                                break;
                            }
                        }
                        if (victim != null) break;
                    }
                }
                // 如果没有非完整组合的卡牌，偷任意一张
                if (victim == null) {
                    for (Player p : players) {
                        if (!p.equals(activePlayer)) {
                            for (List<Card> group : p.getPropertyZone().getAllPropertyGroups().values()) {
                                if (!group.isEmpty()) {
                                    victim = p;
                                    toSteal = group.get(0);
                                    break;
                                }
                            }
                            if (victim != null) break;
                        }
                    }
                }
                if (victim != null && toSteal != null) {
                    executeSlyDeal(activePlayer, victim, toSteal.getId());
                }
            }
        } else if (actionName.contains("Just Say No")) {
            // 拒绝：抵消针对自己的行动卡（效果由客户端处理）
            recordAction(activePlayer.getId(), activePlayer.getNickname(),
                    "JUST_SAY_NO", "", 0, "使用了拒绝卡");
        }
        return true;
    }

    /**
     * 执行强制交换：交换两名玩家指定ID的地产卡
     * @param player1 玩家1
     * @param player2 玩家2
     * @param cardId1 玩家1要交换出的地产卡ID
     * @param cardId2 玩家2要交换出的地产卡ID
     */
    private void executeForcedDeal(Player player1, Player player2, String cardId1, String cardId2) {
        Card card1 = findPropertyInZone(player1, cardId1);
        Card card2 = findPropertyInZone(player2, cardId2);
        if (card1 == null || card2 == null) return;

        // 从各自物业区移除
        player1.getPropertyZone().removeProperty(card1);
        player2.getPropertyZone().removeProperty(card2);

        // 重置万能地产颜色
        if (card1.isWildProperty()) card1.setWildColor(null);
        if (card2.isWildProperty()) card2.setWildColor(null);

        // 交换放入对方物业区
        player1.getPropertyZone().addProperty(card2);
        player2.getPropertyZone().addProperty(card1);

        recordAction(player1.getId(), player1.getNickname(), "FORCED_DEAL",
                player2.getNickname(), 0, card1.getName() + " <-> " + card2.getName());
    }

    /**
     * 执行偷袭：从一名玩家处偷取指定地产卡
     * @param thief 偷取者
     * @param victim 受害者
     * @param cardId 要偷取的地产卡ID
     */
    private void executeSlyDeal(Player thief, Player victim, String cardId) {
        Card stolenCard = findPropertyInZone(victim, cardId);
        if (stolenCard == null) return;

        victim.getPropertyZone().removeProperty(stolenCard);
        if (stolenCard.isWildProperty()) stolenCard.setWildColor(null);  // 重置万能颜色
        thief.getPropertyZone().addProperty(stolenCard);

        recordAction(thief.getId(), thief.getNickname(), "SLY_DEAL",
                victim.getNickname(), 0, "偷取了 " + stolenCard.getName());
    }

    /** 在玩家的物业区中根据ID查找地产卡 */
    private Card findPropertyInZone(Player player, String cardId) {
        for (List<Card> properties : player.getPropertyZone().getAllPropertyGroups().values()) {
            for (Card card : properties) {
                if (card.getId().equals(cardId)) return card;
            }
        }
        return null;
    }

    /**
     * 执行支付 - 从债务人银行取款并存入债权人银行
     * 使用最优支付计算器自动选择最少卡牌的支付方案。
     * 如果余额不足，支付所有可用余额。
     *
     * @param debtor 支付方（债务人）
     * @param creditor 收款方（债权人）
     * @param amount 支付金额
     */
    private void requirePayment(Player debtor, Player creditor, int amount) {
        try {
            List<Card> payment = debtor.getBank().removeCards(amount);
            for (Card moneyCard : payment) {
                creditor.getBank().deposit(moneyCard);
            }
        } catch (Bank.InsufficientFundsException e) {
            // 余额不足时，支付所有可用余额
            int available = debtor.getBank().getTotal();
            if (available > 0) {
                try {
                    List<Card> partial = debtor.getBank().removeCards(available);
                    partial.forEach(m -> creditor.getBank().deposit(m));
                } catch (Bank.InsufficientFundsException ignored) {}
            }
        }
    }

    /** 处理玩家主动结束回合 */
    public void endTurn(String playerId) {
        if (activePlayer != null && playerId.equals(activePlayer.getId())) {
            forceEndTurn();
        }
    }

    /**
     * 强制结束当前回合
     * 1. 取消定时器
     * 2. 自动弃牌到手牌上限（7张）
     * 3. 清除活跃玩家状态
     * 4. 广播游戏状态
     * 5. 延迟1.5秒后开始下一回合
     */
    private void forceEndTurn() {
        cancelTimer();
        if (activePlayer != null) {
            // 自动弃牌：从手牌开头开始弃，直到手牌数<=7
            while (activePlayer.needsToDiscard() && !activePlayer.getHand().isEmpty()) {
                Card discarded = activePlayer.removeCardFromHand(0);
                deck.discard(discarded);
                recordAction(activePlayer.getId(), activePlayer.getNickname(),
                        "DISCARD", "", 0, "弃掉了 " + discarded.getName());
            }
            activePlayer.setActivePlayer(false);
            recordAction(activePlayer.getId(), activePlayer.getNickname(),
                    "END_TURN", "", 0, "回合结束");
        }
        phase = GamePhase.END;
        broadcastGameState();
        // 延迟1.5秒后开始下一回合（给玩家时间看到上个回合的结果）
        scheduler.schedule(this::startNextTurn, 1500, TimeUnit.MILLISECONDS);
    }

    /** 检查是否有玩家满足胜利条件（集齐3套完整地产） */
    private Optional<Player> checkWinner() {
        return players.stream()
                .filter(p -> p.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS)
                .findFirst();
    }

    /** 游戏结束 - 广播GAME_OVER消息 */
    private void endGame(Player winner) {
        gameRunning = false;
        cancelTimer();
        phase = GamePhase.GAME_OVER;

        JsonObject result = new JsonObject();
        result.addProperty("winnerId", winner.getId());
        result.addProperty("winnerNickname", winner.getNickname());
        result.addProperty("gameDuration", getGameDuration());
        result.addProperty("completeSets", winner.getCompleteSetsCount());

        recordAction(winner.getId(), winner.getNickname(), "WINNER", "", 0, "赢得了游戏！");
        broadcastGameState();
        room.broadcast(MessageProtocol.MessageType.GAME_OVER, result.toString());
    }

    /**
     * 处理玩家断线
     * 如果剩余在线玩家不足2人，游戏平局结束。
     * 如果断线的是活跃玩家，自动结束其回合。
     */
    public void handlePlayerDisconnect(String clientId) {
        Player disconnected = findPlayer(clientId);
        if (disconnected == null) return;

        disconnected.setConnected(false);
        disconnected.setReady(false);
        recordAction(clientId, disconnected.getNickname(), "DISCONNECT", "", 0, "玩家断线");

        broadcastGameState();

        long connectedPlayers = players.stream().filter(Player::isConnected).count();
        if (connectedPlayers < GameConstants.MIN_PLAYERS) {
            // 在线玩家不足，游戏结束（平局）
            gameRunning = false;
            cancelTimer();
            JsonObject drawResult = new JsonObject();
            drawResult.addProperty("reason", "在线玩家不足");
            drawResult.addProperty("connectedPlayers", connectedPlayers);
            room.broadcast(MessageProtocol.MessageType.GAME_DRAW, drawResult.toString());
        } else if (activePlayer != null && clientId.equals(activePlayer.getId())) {
            // 断线的是当前活跃玩家，强制结束其回合
            forceEndTurn();
        }
    }

    /** 根据玩家ID查找玩家对象 */
    private Player findPlayer(String playerId) {
        if (playerId == null || playerId.isEmpty()) return null;
        return players.stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 记录一条行动历史
     * 新记录插入列表头部（最新的在前），最多保留100条
     */
    private void recordAction(String playerId, String nickname, String action,
                              String targetPlayer, int amount, String details) {
        ActionRecord record = new ActionRecord(
                actionHistory.size() + 1, playerId, nickname, action,
                targetPlayer, amount, details, System.currentTimeMillis());
        actionHistory.add(0, record);  // 头插法，最新的在最前面
        if (actionHistory.size() > 100) {
            actionHistory.remove(actionHistory.size() - 1);  // 移除最旧的记录
        }
    }

    /**
     * 向所有玩家广播游戏状态
     * 每个玩家收到定制化的GameState（自己的手牌可见，其他玩家只看到数量）
     */
    private void broadcastGameState() {
        for (Player viewer : players) {
            GameState gameState = createGameState(viewer.getId());
            String stateJson = gson.toJson(gameState);
            room.sendToPlayer(viewer.getId(), MessageProtocol.MessageType.GAME_STATE_UPDATE, stateJson);
        }
    }

    /**
     * 创建指定查看者的GameState快照
     * 隐私保护：仅查看者自己的手牌详情（handCards）会被填充，其他玩家只看到手牌数量
     *
     * @param viewerId 查看者ID
     * @return 定制化的GameState
     */
    private GameState createGameState(String viewerId) {
        GameState state = new GameState();
        state.setRoomCode(room.getRoomCode());
        state.setPhase(phase);
        state.setActivePlayerId(activePlayer != null ? activePlayer.getId() : "");
        state.setCurrentPlayerIndex(currentPlayerIndex);
        state.setTurnNumber(actionHistory.size());
        state.setDrawPileSize(deck.getDrawPileSize());
        state.setDiscardPileSize(deck.getDiscardPileSize());
        state.setGameStarted(gameRunning);
        state.setGameStartTime(gameStartTime);
        state.setViewerId(viewerId);

        // 填充每个玩家的状态快照
        for (Player player : players) {
            GameState.PlayerState playerState = new GameState.PlayerState();
            playerState.setPlayerId(player.getId());
            playerState.setNickname(player.getNickname());
            playerState.setReady(player.isReady());
            playerState.setConnected(player.isConnected());
            playerState.setActivePlayer(player.isActivePlayer());
            playerState.setHandCount(player.getHandCount());
            playerState.setBankTotal(player.getBank().getTotal());
            playerState.setCompleteSets(player.getCompleteSetsCount());
            playerState.setPlaysUsed(player.getPlaysUsed());
            playerState.setRemainingPlays(player.getRemainingPlays());
            playerState.setDoubleRentActive(player.isDoubleRentActive());
            playerState.setAvatar(player.getAvatar());

            // 银行面值分布
            Map<Integer, Integer> denominations = new HashMap<>();
            for (int denom : GameConstants.MONEY_DENOMINATIONS) {
                int count = player.getBank().getCount(denom);
                if (count > 0) denominations.put(denom, count);
            }
            playerState.setBankDenominations(denominations);

            // 各颜色地产数量
            Map<String, Integer> colorCounts = new HashMap<>();
            for (Map.Entry<CardColor, List<Card>> entry :
                    player.getPropertyZone().getAllPropertyGroups().entrySet()) {
                if (!entry.getValue().isEmpty())
                    colorCounts.put(entry.getKey().name(), entry.getValue().size());
            }
            playerState.setPropertyColorCounts(colorCounts);

            // 隐私保护：仅查看者自己能看到手牌详情
            if (player.getId().equals(viewerId)) {
                List<GameState.CardInfo> handCards = new ArrayList<>();
                for (Card card : player.getHand()) {
                    handCards.add(new GameState.CardInfo(card));
                }
                playerState.setHandCards(handCards);
            }
            state.addPlayerState(player.getId(), playerState);
        }

        // 填充最近的行动历史（最多20条）
        List<GameState.ActionRecord> recentActions = new ArrayList<>();
        int limit = Math.min(20, actionHistory.size());
        for (int i = 0; i < limit; i++) {
            ActionRecord ar = actionHistory.get(i);
            GameState.ActionRecord stateRecord = new GameState.ActionRecord(
                    ar.index, ar.playerId, ar.playerNickname, ar.action,
                    ar.targetPlayer, ar.amount, ar.details);
            stateRecord.setTimestamp(ar.timestamp);
            recentActions.add(stateRecord);
        }
        state.setActionHistory(recentActions);

        return state;
    }

    /** 向指定玩家发送错误消息 */
    private void sendError(String playerId, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("message", message);
        error.addProperty("timestamp", System.currentTimeMillis());
        room.sendToPlayer(playerId, MessageProtocol.MessageType.ERROR, error.toString());
    }

    /** 计算游戏持续时间（格式：MM:SS） */
    private String getGameDuration() {
        long duration = System.currentTimeMillis() - gameStartTime;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    // ==================== Getters ====================

    public List<Player> getPlayers() { return Collections.unmodifiableList(players); }
    public Player getActivePlayer() { return activePlayer; }
    public GamePhase getPhase() { return phase; }
    public boolean isGameRunning() { return gameRunning; }
    public Deck getDeck() { return deck; }

    /**
     * 内部类：行动历史记录（用于GameSession内部存储）
     * 与GameState.ActionRecord分开，避免包依赖混淆
     */
    static class ActionRecord {
        int index;              // 行动序号
        String playerId;        // 执行者ID
        String playerNickname;  // 执行者昵称
        String action;          // 行动类型
        String targetPlayer;    // 目标玩家
        int amount;             // 涉及金额
        String details;         // 详细描述
        long timestamp;         // 时间戳

        ActionRecord(int index, String playerId, String playerNickname,
                     String action, String targetPlayer, int amount,
                     String details, long timestamp) {
            this.index = index;
            this.playerId = playerId;
            this.playerNickname = playerNickname;
            this.action = action;
            this.targetPlayer = targetPlayer;
            this.amount = amount;
            this.details = details;
            this.timestamp = timestamp;
        }
    }
}
