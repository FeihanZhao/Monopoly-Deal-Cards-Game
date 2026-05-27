package com.monopolydeal.server;

import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.*;
import com.monopolydeal.server.GameRoom;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
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
    /** 两段式定时器第二阶段句柄（警告→超时），须独立取消防止回合结束后误触发 */
    private ScheduledFuture<?> turnTimerWarning;
    /** 游戏是否正在运行 */
    private boolean gameRunning;
    /** 行动历史记录列表（最新的在前） */
    private final List<ActionRecord> actionHistory;
    /** 待处理的支付请求：债务人ID */
    private String pendingPaymentDebtorId;
    /** 待处理的支付请求：债权人ID */
    private String pendingPaymentCreditorId;
    /** 待处理的支付请求：金额 */
    private int pendingPaymentAmount;
    /** 待支付队列（多债务人时FIFO顺序处理）：[debtorId, creditorId, amount] */
    private final Queue<String[]> pendingPaymentQueue = new LinkedList<>();
    /** 支付超时定时器句柄（用于在玩家手动提交后取消超时任务，避免竞态） */
    private ScheduledFuture<?> paymentTimeoutTask;
    /** 决议栈 —— 待处理的行动/Just Say No 链条，栈顶是当前等待响应的元素 */
    private final Deque<ResolutionItem> resolutionStack = new ArrayDeque<>();
    /** 反应超时定时器句柄（用于取消） */
    private ScheduledFuture<?> reactionTimeoutTask;
    /** 弃牌超时定时器句柄（用于取消） */
    private ScheduledFuture<?> discardTimeoutTask;
    /** 挂起的多目标决议（当前目标付款完成后继续处理下一个目标） */
    private ResolutionItem pendingMultiTargetResolution;
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
    public synchronized void start() {
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
     * 3. 自动抽取2张牌（手牌为空时抽5张）
     * 4. 进入PLAY阶段
     * 5. 启动30秒超时定时器
     */
    private synchronized void startNextTurn() {
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

        // 自动抽牌阶段：手牌为空时抽5张（符合官方规则）
        int baseDraw = activePlayer.getHandCount() == 0
                ? GameConstants.EMPTY_HAND_DRAW_COUNT
                : GameConstants.DRAW_COUNT;
        int drawCount = baseDraw;
        // drawMultiple() 内部会在抽牌堆为空时自动触发 reshuffleDiscardPile()
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
            turnTimerWarning = scheduler.schedule(() -> {
                room.broadcast(MessageProtocol.MessageType.TURN_TIMEOUT,
                        "{\"playerId\":\"" + activePlayer.getId() + "\",\"reason\":\"回合超时\"}");
                forceEndTurn();
            }, GameConstants.TIMEOUT_WARNING_SECONDS, TimeUnit.SECONDS);
        }, GameConstants.TURN_TIMEOUT_SECONDS - GameConstants.TIMEOUT_WARNING_SECONDS, TimeUnit.SECONDS);
    }

    /** 取消当前的回合定时器（含两段式定时器） */
    private void cancelTimer() {
        if (turnTimer != null && !turnTimer.isCancelled()) {
            turnTimer.cancel(false);
        }
        if (turnTimerWarning != null && !turnTimerWarning.isCancelled()) {
            turnTimerWarning.cancel(false);
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
    public synchronized void handlePlayCard(String playerId, JsonObject payload) {
        // 权限验证：游戏必须运行中、必须是活跃玩家、必须在 PLAY 阶段
        if (!gameRunning || activePlayer == null) return;
        if (!playerId.equals(activePlayer.getId())) return;
        if (phase != GamePhase.PLAY) {
            sendError(playerId, "当前阶段不允许出牌");
            return;
        }
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

                // 仅当仍在 PLAY 阶段时才自动结束回合（若已进入 WAITING_FOR_PAYMENT 或
                // WAITING_FOR_REACTION，由各自的完成回调处理）
                if (phase == GamePhase.PLAY && activePlayer.getRemainingPlays() <= 0) {
                    scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            sendError(playerId, e.getMessage());
        }
    }

    /**
     * 将卡牌作为货币存入银行 - 从手牌中移除并存入银行
     * 任何 canBeUsedAsMoney() 的卡均可存入（含行动卡/租金卡），
     * 存入后仅作为货币资产，丧失原有效果。
     * @param card 面值 > 0 的卡牌
     * @return true=执行成功
     */
    private boolean playMoneyCard(Card card) {
        if (!card.canBeUsedAsMoney()) return false;
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

        // 步骤1：确定租金颜色（先验证，不移除卡牌）
        CardColor rentColor = CardColor.WILD;
        if (payload.has("color")) {
            try {
                rentColor = CardColor.valueOf(payload.get("color").getAsString());
            } catch (IllegalArgumentException e) {
                rentColor = CardColor.WILD;
            }
        }

        boolean isWildRent = card.getColor() == CardColor.WILD ||
                card.getName().contains("Wild");

        // 万能租金卡客户端颜色校验：必须是有效地产颜色
        if (isWildRent && rentColor != CardColor.WILD && !rentColor.isPropertyColor()) {
            rentColor = CardColor.WILD;
        }
        payload.addProperty("isWildRent", isWildRent);

        // 步骤2：预计算租金金额
        int baseRentAmount;
        if (isWildRent && rentColor == CardColor.WILD) {
            // 万能租金未选颜色：默认 2M
            baseRentAmount = 2;
        } else {
            // 双色租金卡：对两个成分色分别计算，取较大值
            CardColor[] components = card.getColor().getComponentColors();
            if (components.length == 2) {
                int rent1 = activePlayer.getPropertyZone().getRentAmount(components[0]);
                int rent2 = activePlayer.getPropertyZone().getRentAmount(components[1]);
                baseRentAmount = Math.max(rent1, rent2);
                // 选取收益更高的颜色作为正式租金颜色
                rentColor = (rent1 >= rent2) ? components[0] : components[1];
            } else {
                baseRentAmount = activePlayer.getPropertyZone().getRentAmount(rentColor);
            }
            // 没有对应颜色的地产时拒绝出牌
            if (baseRentAmount == 0) {
                sendError(activePlayer.getId(),
                    "你没有对应颜色的地产，无法收取租金");
                return false;
            }
        }
        payload.addProperty("color", rentColor.name());

        int rentAmount = activePlayer.isDoubleRentActive() ? baseRentAmount * 2 : baseRentAmount;
        activePlayer.setDoubleRentActive(false);  // 立即消耗，防止JSN取消后flag泄漏
        payload.addProperty("_preCalculatedRent", rentAmount);

        // 步骤3：收集所有目标玩家列表
        java.util.List<String> allTargets = new java.util.ArrayList<>();
        if (isWildRent) {
            String targetId = extractTargetId(payload);
            if (!targetId.isEmpty()) {
                allTargets.add(targetId);
            } else {
                for (Player p : players) {
                    if (!p.equals(activePlayer)) {
                        allTargets.add(p.getId());
                        break;
                    }
                }
            }
        } else {
            for (Player p : players) {
                if (!p.equals(activePlayer)) {
                    allTargets.add(p.getId());
                }
            }
        }
        if (allTargets.isEmpty()) return false;

        // 步骤4：验证通过，从手牌移除并弃牌
        activePlayer.removeCardFromHand(card);
        deck.discard(card);

        // 步骤5：第一个目标作为当前响应人，剩余的存入 _remainingTargets
        String firstTarget = allTargets.remove(0);
        payload.addProperty("targetPlayerId", firstTarget);
        if (!allTargets.isEmpty()) {
            com.google.gson.JsonArray remaining = new com.google.gson.JsonArray();
            for (String id : allTargets) {
                remaining.add(id);
            }
            payload.add("_remainingTargets", remaining);
        }

        pushResolution("RENT", activePlayer.getId(), firstTarget, card, payload);
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

        String actionName = card.getName();

        // === 步骤1：无目标行动 — 立即执行（校验通过后才弃牌） ===
        if (actionName.contains("Pass Go")) {
            activePlayer.removeCardFromHand(card);
            deck.discard(card);
            List<Card> extraCards = deck.drawMultiple(2);
            extraCards.forEach(activePlayer::addCardToHand);
            return true;
        }

        if (actionName.contains("Double")) {
            activePlayer.removeCardFromHand(card);
            deck.discard(card);
            activePlayer.setDoubleRentActive(true);
            return true;
        }

        if (actionName.contains("House") && !actionName.contains("Hotel")) {
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
                activePlayer.removeCardFromHand(card);
                deck.discard(card);
                activePlayer.getPropertyZone().addHouse(houseColor);
                return true;
            }
            return false;
        }

        if (actionName.contains("Hotel")) {
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
                activePlayer.removeCardFromHand(card);
                deck.discard(card);
                activePlayer.getPropertyZone().addHotel(hotelColor);
                return true;
            }
            return false;
        }

        // === 步骤3：有目标行动 — 推入决议栈，延迟执行 ===
        String actionType = mapActionNameToType(actionName);
        String targetId = extractTargetId(payload);

        // 确定目标玩家（客户端未指定时自动选择）
        if (targetId.isEmpty()) {
            if (actionName.contains("Deal Breaker")) {
                for (Player p : players) {
                    if (!p.equals(activePlayer) && p.getCompleteSetsCount() > 0) {
                        targetId = p.getId();
                        break;
                    }
                }
            } else if (actionName.contains("Forced Deal")) {
                // 自动选择：第一个有地产的其他玩家作为目标
                for (Player p : players) {
                    if (!p.equals(activePlayer) && !p.getPropertyZone().getAllPropertyGroups().isEmpty()) {
                        targetId = p.getId();
                        break;
                    }
                }
            } else if (actionName.contains("Sly Deal")) {
                // 自动选择：第一个有可偷取地产的玩家作为目标
                for (Player p : players) {
                    if (!p.equals(activePlayer)) {
                        boolean found = false;
                        for (List<Card> group : p.getPropertyZone().getAllPropertyGroups().values()) {
                            if (!group.isEmpty() && !p.getPropertyZone().getCompleteSets()
                                    .contains(group.get(0).getEffectiveColor())) {
                                targetId = p.getId();
                                found = true;
                                break;
                            }
                        }
                        if (found) break;
                    }
                }
            } else if (actionName.contains("Birthday")) {
                // Birthday：收集所有其他玩家，第一个作为响应人，剩余存入 _remainingTargets
                java.util.List<String> allTargets = new java.util.ArrayList<>();
                for (Player p : players) {
                    if (!p.equals(activePlayer)) {
                        allTargets.add(p.getId());
                    }
                }
                if (allTargets.isEmpty()) return false;
                targetId = allTargets.remove(0);
                if (!allTargets.isEmpty()) {
                    com.google.gson.JsonArray remaining = new com.google.gson.JsonArray();
                    for (String id : allTargets) {
                        remaining.add(id);
                    }
                    payload.add("_remainingTargets", remaining);
                }
                payload.addProperty("_amount", GameConstants.BIRTHDAY_AMOUNT);
            } else {
                // 默认选择第一个其他玩家
                for (Player p : players) {
                    if (!p.equals(activePlayer)) {
                        targetId = p.getId();
                        break;
                    }
                }
            }
        }

        if (targetId.isEmpty()) return false;

        // 偷取类卡牌：补充目标地产选择（无论 targetId 来自客户端还是服务端自动选择，都必须执行）
        if (actionName.contains("Sly Deal") && !payload.has("targetCardId")) {
            Player victim = findPlayer(targetId);
            if (victim != null) {
                Card toSteal = null;
                for (List<Card> group : victim.getPropertyZone().getAllPropertyGroups().values()) {
                    if (!group.isEmpty() && !victim.getPropertyZone().getCompleteSets()
                            .contains(group.get(0).getEffectiveColor())) {
                        toSteal = group.get(0);
                        break;
                    }
                }
                if (toSteal == null) return false;
                payload.addProperty("targetCardId", toSteal.getId());
            }
        }

        if (actionName.contains("Forced Deal") && (!payload.has("myPropertyId") || !payload.has("theirPropertyId"))) {
            Player target = findPlayer(targetId);
            if (target != null) {
                Card myProp = null;
                for (List<Card> group : activePlayer.getPropertyZone().getAllPropertyGroups().values()) {
                    if (!group.isEmpty()) { myProp = group.get(0); break; }
                }
                Card theirProp = null;
                for (List<Card> group : target.getPropertyZone().getAllPropertyGroups().values()) {
                    if (!group.isEmpty()) { theirProp = group.get(0); break; }
                }
                if (myProp == null || theirProp == null) return false;
                payload.addProperty("myPropertyId", myProp.getId());
                payload.addProperty("theirPropertyId", theirProp.getId());
            }
        }

        // 确保 payload 中包含 targetPlayerId
        if (!payload.has("targetPlayerId")) {
            payload.addProperty("targetPlayerId", targetId);
        }

        // 校验通过，从手牌移除并弃牌
        activePlayer.removeCardFromHand(card);
        deck.discard(card);

        // 推入决议栈
        pushResolution(actionType, activePlayer.getId(), targetId, card, payload);

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

    // ==================== 决议栈核心方法 ====================

    /**
     * 将行动推入决议栈，进入 WAITING_FOR_REACTION 阶段
     *
     * @param actionType   行动类型
     * @param initiatorId  发起人ID
     * @param responderId  响应人ID
     * @param sourceCard   打出的卡牌
     * @param actionPayload 原始请求数据
     */
    private void pushResolution(String actionType, String initiatorId,
                                String responderId, Card sourceCard,
                                JsonObject actionPayload) {
        String resolutionId = UUID.randomUUID().toString().substring(0, 8);
        ResolutionItem item = new ResolutionItem(resolutionId, actionType,
                initiatorId, responderId, sourceCard, actionPayload);
        resolutionStack.push(item);

        // 进入等待反应阶段，暂停回合
        phase = GamePhase.WAITING_FOR_REACTION;
        cancelTimer();

        // 通知响应人
        sendReactionRequired(responderId, item);

        // 启动5秒反应超时
        startReactionTimeout(responderId);
    }

    /** 向响应人发送 REACTION_REQUIRED 消息 */
    private void sendReactionRequired(String responderId, ResolutionItem item) {
        Player responder = findPlayer(responderId);
        Player initiator = findPlayer(item.getInitiatorId());
        if (responder == null || initiator == null) return;

        JsonObject req = new JsonObject();
        req.addProperty("resolutionId", item.getResolutionId());
        req.addProperty("actionType", item.getActionType());
        req.addProperty("initiatorName", initiator.getNickname());
        req.addProperty("initiatorId", initiator.getId());
        req.addProperty("cardName", item.getSourceCard().getName());
        req.addProperty("timeoutSeconds", GameConstants.JUST_SAY_NO_TIMEOUT_SECONDS);

        room.sendToPlayer(responderId, MessageProtocol.MessageType.REACTION_REQUIRED,
                req.toString());
    }

    /** 启动反应超时定时器 */
    private void startReactionTimeout(String responderId) {
        cancelReactionTimeout();
        reactionTimeoutTask = scheduler.schedule(
                () -> handleReactionTimeout(responderId),
                GameConstants.JUST_SAY_NO_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** 取消反应超时定时器 */
    private void cancelReactionTimeout() {
        if (reactionTimeoutTask != null && !reactionTimeoutTask.isCancelled()) {
            reactionTimeoutTask.cancel(false);
        }
    }

    /** 反应超时 —— 视为放弃，自动 resolveTop */
    private synchronized void handleReactionTimeout(String responderId) {
        if (resolutionStack.isEmpty()) return;
        ResolutionItem top = resolutionStack.peek();
        if (top == null || !top.getResponderId().equals(responderId)) return;

        // 超时视为放弃
        recordAction(responderId, findPlayer(responderId) != null ?
                findPlayer(responderId).getNickname() : "",
                "PASS_REACTION", "", 0, "超时未响应");

        resolveTopResolution();
    }

    /** 处理玩家打出 Just Say No */
    public synchronized void handlePlayJustSayNo(String playerId, JsonObject payload) {
        if (phase != GamePhase.WAITING_FOR_REACTION) {
            sendError(playerId, "当前阶段不允许打出 Just Say No");
            return;
        }
        if (resolutionStack.isEmpty()) {
            sendError(playerId, "当前没有待响应的行动");
            return;
        }

        ResolutionItem currentTop = resolutionStack.peek();
        // 校验：必须是当前响应人在操作
        if (!playerId.equals(currentTop.getResponderId())) {
            sendError(playerId, "当前不需要你响应");
            return;
        }

        // 从手牌中找到 Just Say No 卡
        String cardId = payload.get("cardId").getAsString();
        Player responder = findPlayer(playerId);
        if (responder == null) return;

        Card jsnCard = responder.findCardById(cardId);
        if (jsnCard == null || !jsnCard.getName().contains("Just Say No")) {
            sendError(playerId, "手牌中未找到 Just Say No 卡");
            return;
        }

        // 打出 Just Say No：从手牌移除并弃牌
        responder.removeCardFromHand(jsnCard);
        deck.discard(jsnCard);
        cancelReactionTimeout();

        // 将 Just Say No 推入栈顶，响应人切换为原发起人
        String newResponderId = currentTop.getInitiatorId();
        JsonObject jsnPayload = new JsonObject();
        jsnPayload.addProperty("counteredResolutionId", currentTop.getResolutionId());

        pushResolution("JUST_SAY_NO", playerId, newResponderId, jsnCard, jsnPayload);

        recordAction(playerId, responder.getNickname(), "JUST_SAY_NO",
                findPlayer(newResponderId) != null ?
                        findPlayer(newResponderId).getNickname() : "",
                0, "打出 Just Say No 拒绝 " + currentTop.getActionType());
        broadcastGameState();
    }

    /** 处理玩家放弃响应（不打 Just Say No） */
    public synchronized void handlePassReaction(String playerId) {
        if (resolutionStack.isEmpty()) {
            sendError(playerId, "当前没有待响应的行动");
            return;
        }

        ResolutionItem top = resolutionStack.peek();
        if (!playerId.equals(top.getResponderId())) {
            sendError(playerId, "当前不需要你响应");
            return;
        }

        cancelReactionTimeout();
        recordAction(playerId, findPlayer(playerId) != null ?
                findPlayer(playerId).getNickname() : "",
                "PASS_REACTION", "", 0, "放弃响应 " + top.getActionType());

        resolveTopResolution();
    }

    /**
     * 弹出栈顶决议并处理
     *
     * 出栈规则：
     * - 如果栈顶是 JUST_SAY_NO → 它成功了 → 再弹出下面一个元素（被取消）
     * - 如果栈顶是原始行动 → 它未被取消 → 执行延迟效果
     * - 处理完后如果栈非空 → 继续等待下一个响应人
     * - 如果栈为空 → 回到 PLAY 或进入 WAITING_FOR_PAYMENT
     */
    private void resolveTopResolution() {
        if (resolutionStack.isEmpty()) return;

        ResolutionItem resolved = resolutionStack.pop();

        if (resolved.isJustSayNo()) {
            // Just Say No 成功 → 取消被它压住的下一个决议
            if (!resolutionStack.isEmpty()) {
                ResolutionItem cancelled = resolutionStack.pop();
                recordAction(resolved.getInitiatorId(),
                        findPlayer(resolved.getInitiatorId()) != null ?
                                findPlayer(resolved.getInitiatorId()).getNickname() : "",
                        "ACTION_CANCELLED", "",
                        0, cancelled.getActionType() + " 被 Just Say No 取消");
                // 多目标：被 JSN 取消的只是当前玩家的义务，继续处理下一个目标
                if (continueMultiTargetResolution(cancelled)) return;
            }
        } else {
            // 原始行动未被取消，执行延迟效果
            executeDeferredAction(resolved);
            // 多目标：暂存决议，等当前目标付款完成后再推进
            if (hasRemainingTargets(resolved)) {
                pendingMultiTargetResolution = resolved;
            }
        }

        // 栈非空 → 继续等待下一个响应人
        if (!resolutionStack.isEmpty()) {
            ResolutionItem nextTop = resolutionStack.peek();
            sendReactionRequired(nextTop.getResponderId(), nextTop);
            startReactionTimeout(nextTop.getResponderId());
            return;
        }

        // 栈空 → 决议阶段全部结束
        if (pendingPaymentDebtorId != null) {
            broadcastGameState();
            return;
        }

        // 如果还有挂起的多目标决议（如前一个目标余额为0跳过支付），推进到下一个目标
        if (pendingMultiTargetResolution != null) {
            ResolutionItem saved = pendingMultiTargetResolution;
            pendingMultiTargetResolution = null;
            if (continueMultiTargetResolution(saved)) {
                broadcastGameState();
                return;
            }
        }

        // 无待处理支付且无挂起多目标 → 直接恢复出牌阶段
        phase = GamePhase.PLAY;
        startTurnTimer();
        broadcastGameState();

        if (activePlayer != null && activePlayer.getRemainingPlays() <= 0) {
            scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
        }
    }

    /** 检查是否还有剩余的多目标需要处理 */
    private boolean hasRemainingTargets(ResolutionItem item) {
        JsonObject payload = item.getActionPayload();
        if (!payload.has("_remainingTargets")) return false;
        com.google.gson.JsonArray remaining = payload.getAsJsonArray("_remainingTargets");
        return remaining != null && remaining.size() > 0;
    }

    /**
     * 多目标行动辅助方法 —— 从未处理的目标列表中取出下一个推入决议栈
     * 若 _remainingTargets 不存在或为空，则什么都不做
     */
    private boolean continueMultiTargetResolution(ResolutionItem resolvedItem) {
        JsonObject payload = resolvedItem.getActionPayload();
        if (!payload.has("_remainingTargets")) return false;

        com.google.gson.JsonArray remaining = payload.getAsJsonArray("_remainingTargets");
        if (remaining == null || remaining.size() == 0) return false;

        // 取出下一个目标玩家ID
        String nextTarget = remaining.remove(0).getAsString();
        payload.addProperty("targetPlayerId", nextTarget);

        // 若已无剩余目标，移除标记字段
        if (remaining.size() == 0) {
            payload.remove("_remainingTargets");
        }

        // 推入新决议，下一个目标成为新的响应人
        pushResolution(resolvedItem.getActionType(),
                resolvedItem.getInitiatorId(),
                nextTarget,
                resolvedItem.getSourceCard(),
                payload);
        return true;
    }

    /**
     * 执行被延迟的行动效果（决议通过后调用）
     */
    private void executeDeferredAction(ResolutionItem item) {
        String actionType = item.getActionType();
        JsonObject payload = item.getActionPayload();
        Player initiator = findPlayer(item.getInitiatorId());
        if (initiator == null) return;

        switch (actionType) {
            case "DEBT_COLLECTOR":
                executeDebtCollector(initiator, payload);
                break;
            case "BIRTHDAY":
                executeBirthday(initiator, payload);
                break;
            case "RENT":
                executeRent(initiator, payload);
                break;
            case "DEAL_BREAKER":
                executeDealBreaker(initiator, payload);
                break;
            case "SLY_DEAL":
                executeSlyDeal(initiator, payload);
                break;
            case "FORCED_DEAL":
                executeForcedDeal(initiator, payload);
                break;
            default:
                System.err.println("错误：executeDeferredAction 收到未知行动类型 '" + actionType + "'");
                break;
        }
    }

    // ==================== 各行动效果延迟执行 ====================

    private void executeDebtCollector(Player initiator, JsonObject payload) {
        String targetId = payload.has("targetPlayerId") ?
                payload.get("targetPlayerId").getAsString() : "";
        Player target = findPlayer(targetId);
        if (target == null && !players.isEmpty()) {
            for (Player p : players) {
                if (!p.equals(initiator)) { target = p; break; }
            }
        }
        if (target != null) {
            requirePayment(target, initiator, GameConstants.DEBT_COLLECTOR_AMOUNT);
            recordAction(initiator.getId(), initiator.getNickname(),
                    "DEBT_COLLECTOR", target.getNickname(),
                    GameConstants.DEBT_COLLECTOR_AMOUNT,
                    "收取 " + GameConstants.DEBT_COLLECTOR_AMOUNT + "M");
        }
    }

    private void executeBirthday(Player initiator, JsonObject payload) {
        String targetId = payload.get("targetPlayerId").getAsString();
        Player target = findPlayer(targetId);
        int amount = payload.has("_amount")
                ? payload.get("_amount").getAsInt()
                : GameConstants.BIRTHDAY_AMOUNT;
        if (target != null) {
            requirePayment(target, initiator, amount);
            recordAction(initiator.getId(), initiator.getNickname(),
                    "BIRTHDAY", target.getNickname(), amount,
                    target.getNickname() + " 支付 " + amount + "M");
        }
    }

    private void executeRent(Player initiator, JsonObject payload) {
        // 使用 playRentCard 预计算的租金金额（若缺失则现场计算兜底）
        int rentAmount;
        if (payload.has("_preCalculatedRent")) {
            rentAmount = payload.get("_preCalculatedRent").getAsInt();
        } else {
            // 兜底计算（与 playRentCard 逻辑一致）
            CardColor rentColor = CardColor.WILD;
            if (payload.has("color")) {
                try {
                    rentColor = CardColor.valueOf(payload.get("color").getAsString());
                } catch (IllegalArgumentException e) {
                    rentColor = CardColor.WILD;
                }
            }
            int baseRentAmount;
            if (rentColor == CardColor.WILD) {
                baseRentAmount = 2;
            } else {
                // 双色租金卡：对两个成分色取 max
                CardColor[] components = rentColor.getComponentColors();
                if (components.length == 2) {
                    int rent1 = initiator.getPropertyZone().getRentAmount(components[0]);
                    int rent2 = initiator.getPropertyZone().getRentAmount(components[1]);
                    baseRentAmount = Math.max(rent1, rent2);
                } else {
                    baseRentAmount = initiator.getPropertyZone().getRentAmount(rentColor);
                }
                if (baseRentAmount == 0) baseRentAmount = 2;
            }
            rentAmount = initiator.isDoubleRentActive() ? baseRentAmount * 2 : baseRentAmount;
        }

        // 单目标收费（多目标场景下每个玩家由各自的决议元素逐个处理）
        String targetPlayerId = payload.has("targetPlayerId")
                ? payload.get("targetPlayerId").getAsString() : "";
        Player targetPlayer = findPlayer(targetPlayerId);
        if (targetPlayer != null) {
            requirePayment(targetPlayer, initiator, rentAmount);
            recordAction(initiator.getId(), initiator.getNickname(), "RENT",
                    targetPlayer.getNickname(), rentAmount,
                    "向 " + targetPlayer.getNickname() + " 收取租金 " + rentAmount + "M");
        }
    }

    private void executeDealBreaker(Player initiator, JsonObject payload) {
        Player target = null;
        if (payload.has("targetPlayerId")) {
            target = findPlayer(payload.get("targetPlayerId").getAsString());
        } else {
            for (Player p : players) {
                if (!p.equals(initiator) && p.getCompleteSetsCount() > 0) {
                    target = p;
                    break;
                }
            }
        }
        if (target != null && target.getCompleteSetsCount() > 0) {
            stealCompleteSet(initiator, target);
        }
    }

    /** 偷取完整地产组合（Deal Breaker 效果） */
    private void stealCompleteSet(Player initiator, Player target) {
        List<CardColor> completeSets = target.getPropertyZone().getCompleteSets();
        if (completeSets.isEmpty()) return;

        CardColor setToSteal = completeSets.get(0);
        List<Card> properties = new ArrayList<>(
                target.getPropertyZone().getPropertiesByColor(setToSteal));
        for (Card prop : properties) {
            target.getPropertyZone().removeProperty(prop);
            if (prop.isWildProperty()) prop.setWildColor(null);
            initiator.getPropertyZone().addProperty(prop);
        }
        recordAction(initiator.getId(), initiator.getNickname(),
                "DEAL_BREAKER", target.getNickname(), 0,
                "偷取了完整组合：" + setToSteal.getName());
        // 检查是否因此获胜
        if (initiator.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
            endGame(initiator);
        }
    }

    /** 偷取单张地产卡（Sly Deal 效果 — 延迟执行版） */
    private void executeSlyDeal(Player initiator, JsonObject payload) {
        if (!payload.has("targetPlayerId") || !payload.has("targetCardId")) return;
        String targetPlayerId = payload.get("targetPlayerId").getAsString();
        String targetCardId = payload.get("targetCardId").getAsString();
        Player target = findPlayer(targetPlayerId);
        if (target != null && targetCardId != null) {
            Card stolenCard = findPropertyInZone(target, targetCardId);
            if (stolenCard == null) return;
            // 不能偷取完整组合中的地产卡
            if (target.getPropertyZone().getCompleteSets()
                    .contains(stolenCard.getEffectiveColor())) return;
            target.getPropertyZone().removeProperty(stolenCard);
            if (stolenCard.isWildProperty()) stolenCard.setWildColor(null);
            initiator.getPropertyZone().addProperty(stolenCard);
            recordAction(initiator.getId(), initiator.getNickname(), "SLY_DEAL",
                    target.getNickname(), 0, "偷取了 " + stolenCard.getName());
        }
    }

    /** 强制交换（Forced Deal 效果 — 延迟执行版） */
    private void executeForcedDeal(Player initiator, JsonObject payload) {
        if (!payload.has("targetPlayerId") || !payload.has("myPropertyId")
                || !payload.has("theirPropertyId")) return;
        String targetPlayerId = payload.get("targetPlayerId").getAsString();
        Player otherPlayer = findPlayer(targetPlayerId);
        if (otherPlayer == null) return;
        String myPropId = payload.get("myPropertyId").getAsString();
        String theirPropId = payload.get("theirPropertyId").getAsString();
        executeForcedDeal(initiator, otherPlayer, myPropId, theirPropId);
    }

    // ==================== 辅助方法 ====================

    /** 将卡牌名称映射为标准行动类型字符串 */
    private String mapActionNameToType(String actionName) {
        if (actionName.contains("Debt Collector")) return "DEBT_COLLECTOR";
        if (actionName.contains("Birthday")) return "BIRTHDAY";
        if (actionName.contains("Deal Breaker")) return "DEAL_BREAKER";
        if (actionName.contains("Sly Deal")) return "SLY_DEAL";
        if (actionName.contains("Forced Deal")) return "FORCED_DEAL";
        if (actionName.contains("Rent") || actionName.contains("rent")) return "RENT";
        System.err.println("警告：未识别的行动卡名称 '" + actionName + "'，将作为 UNKNOWN 处理");
        return "UNKNOWN";
    }

    /** 从 payload 中提取目标玩家ID */
    private String extractTargetId(JsonObject payload) {
        if (payload.has("targetPlayerId")) {
            String id = payload.get("targetPlayerId").getAsString();
            if (id != null && !id.isEmpty()) return id;
        }
        return "";
    }

    // ==================== 支付系统 ====================

    /**
     * 发起异步支付请求 —— 向债务人客户端发送 PAYMENT_REQUIRED
     *
     * 支付不再同步执行。债务人收到消息后在客户端选择卡牌，
     * 通过 SUBMIT_PAYMENT 提交选择，由 handleSubmitPayment 执行转账。
     * 多债务人时通过 FIFO 队列顺序处理。
     *
     * @param debtor   支付方
     * @param creditor 收款方
     * @param amount   支付金额
     */
    private void requirePayment(Player debtor, Player creditor, int amount) {
        if (debtor.getBank().getTotal() == 0) {
            recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_SKIPPED",
                    creditor.getNickname(), amount, "余额为零，无需支付");
            broadcastGameState();
            return;
        }

        int actualAmount = Math.min(amount, debtor.getBank().getTotal());

        if (pendingPaymentDebtorId != null) {
            pendingPaymentQueue.add(new String[]{
                debtor.getId(), creditor.getId(), String.valueOf(actualAmount)});
            return;
        }

        sendPaymentRequest(debtor, creditor, actualAmount);
    }

    /** 发送支付请求消息给指定债务人 */
    private void sendPaymentRequest(Player debtor, Player creditor, int amount) {
        this.pendingPaymentDebtorId = debtor.getId();
        this.pendingPaymentCreditorId = creditor.getId();
        this.pendingPaymentAmount = amount;

        JsonObject paymentReq = new JsonObject();
        paymentReq.addProperty("creditorName", creditor.getNickname());
        paymentReq.addProperty("creditorId", creditor.getId());
        paymentReq.addProperty("amount", amount);
        paymentReq.addProperty("totalBank", debtor.getBank().getTotal());
        List<GameState.CardInfo> bankCards = new ArrayList<>();
        for (Card c : debtor.getBank().getAllMoneyCards()) {
            bankCards.add(new GameState.CardInfo(c));
        }
        paymentReq.add("bankCards", gson.toJsonTree(bankCards));

        room.sendToPlayer(debtor.getId(), MessageProtocol.MessageType.PAYMENT_REQUIRED,
                paymentReq.toString());

        // 暂停回合：进入支付等待阶段，取消回合定时器防止活跃玩家超时
        phase = GamePhase.WAITING_FOR_PAYMENT;
        cancelTimer();

        // 30秒超时兜底
        final int capturedAmount = amount;
        this.paymentTimeoutTask = scheduler.schedule(
                () -> handlePaymentTimeout(debtor, creditor, capturedAmount),
                30, TimeUnit.SECONDS);
    }

    /** 支付超时兜底 —— 使用贪心算法自动选取卡牌支付 */
    private synchronized void handlePaymentTimeout(Player debtor, Player creditor, int expectedAmount) {
        if (pendingPaymentDebtorId == null
                || !pendingPaymentDebtorId.equals(debtor.getId())
                || pendingPaymentAmount != expectedAmount) {
            return;
        }

        try {
            List<Card> payment = debtor.getBank().removeCardsFallback(pendingPaymentAmount);
            int actualPaid = 0;
            for (Card moneyCard : payment) {
                actualPaid += moneyCard.getValue();
                creditor.getBank().deposit(moneyCard.transferCopy());
            }
            recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_TIMEOUT",
                    creditor.getNickname(), actualPaid,
                    "超时自动支付 " + actualPaid + "M");
        } catch (Bank.InsufficientFundsException ignored) {}

        clearPendingPayment();
        broadcastGameState();
    }

    /**
     * 处理玩家提交的支付选择 —— 由 ClientHandler 路由
     * 债务人选择要支付的卡牌后调用，执行校验和转账
     */
    public synchronized void handleSubmitPayment(String playerId, JsonObject payload) {
        if (phase != GamePhase.WAITING_FOR_PAYMENT) {
            sendError(playerId, "当前阶段不允许支付");
            return;
        }
        if (!playerId.equals(pendingPaymentDebtorId)) {
            sendError(playerId, "当前没有待处理的支付请求");
            return;
        }

        Player debtor = findPlayer(playerId);
        Player creditor = findPlayer(pendingPaymentCreditorId);
        if (debtor == null || creditor == null) {
            clearPendingPayment();
            return;
        }

        List<String> cardIds = new ArrayList<>();
        for (JsonElement elem : payload.getAsJsonArray("cardIds")) {
            cardIds.add(elem.getAsString());
        }

        try {
            List<Card> payment = debtor.getBank().removeCardsByIds(cardIds, pendingPaymentAmount);
            int totalPaid = 0;
            for (Card moneyCard : payment) {
                totalPaid += moneyCard.getValue();
                creditor.getBank().deposit(moneyCard.transferCopy());
            }
            recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_MADE",
                    creditor.getNickname(), totalPaid,
                    "支付了 " + totalPaid + "M (需付 " + pendingPaymentAmount + "M)");
        } catch (IllegalArgumentException e) {
            sendError(playerId, e.getMessage());
            return;
        }

        if (paymentTimeoutTask != null) {
            paymentTimeoutTask.cancel(false);
        }
        clearPendingPayment();
        broadcastGameState();
    }

    /** 清除当前待处理支付状态，并出队下一个支付请求 */
    private void clearPendingPayment() {
        pendingPaymentDebtorId = null;
        pendingPaymentCreditorId = null;
        pendingPaymentAmount = 0;

        if (!pendingPaymentQueue.isEmpty()) {
            // 处理队列中的下一个支付（保持 WAITING_FOR_PAYMENT 阶段不变）
            String[] next = pendingPaymentQueue.poll();
            Player nextDebtor = findPlayer(next[0]);
            Player nextCreditor = findPlayer(next[1]);
            int nextAmount = Integer.parseInt(next[2]);
            if (nextDebtor != null && nextCreditor != null) {
                sendPaymentRequest(nextDebtor, nextCreditor, nextAmount);
            } else {
                // 下一个支付无效（玩家已断线等），递归清理继续处理
                clearPendingPayment();
            }
            return;  // 仍有待处理支付，不恢复 PLAY 阶段
        }

        // 所有支付已完成
        // 如果还有挂起的多目标决议，推进到下一个目标
        if (pendingMultiTargetResolution != null) {
            ResolutionItem saved = pendingMultiTargetResolution;
            pendingMultiTargetResolution = null;
            if (continueMultiTargetResolution(saved)) {
                return; // 已推入下一个目标，等待 JSN 响应
            }
        }

        // 所有目标处理完毕，恢复出牌阶段
        phase = GamePhase.PLAY;
        startTurnTimer();
        broadcastGameState();

        // 如果活跃玩家出牌次数已用完，自动结束回合
        if (activePlayer != null && activePlayer.getRemainingPlays() <= 0) {
            scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
        }
    }

    /** 处理玩家主动结束回合 */
    public synchronized void endTurn(String playerId) {
        if (activePlayer == null || !playerId.equals(activePlayer.getId())) return;

        if (phase == GamePhase.WAITING_FOR_PAYMENT || phase == GamePhase.WAITING_FOR_REACTION) {
            sendError(playerId, "请等待当前操作完成后再结束回合");
            return;
        }
        if (phase == GamePhase.DISCARD) {
            sendError(playerId, "请在弃牌完成后等待自动结束回合");
            return;
        }

        forceEndTurn();
    }

    /**
     * 强制结算所有待处理支付（回合超时/玩家断线时调用）
     * 对当前待处理和队列中的所有支付使用兜底贪心算法自动结算
     */
    private void forceSettleAllPendingPayments() {
        // 取消支付超时定时器，防止竞态
        if (paymentTimeoutTask != null && !paymentTimeoutTask.isCancelled()) {
            paymentTimeoutTask.cancel(false);
        }
        // 先结算当前待处理的支付
        if (pendingPaymentDebtorId != null) {
            Player debtor = findPlayer(pendingPaymentDebtorId);
            Player creditor = findPlayer(pendingPaymentCreditorId);
            if (debtor != null && creditor != null && debtor.getBank().getTotal() > 0) {
                try {
                    int actualAmount = Math.min(pendingPaymentAmount, debtor.getBank().getTotal());
                    List<Card> payment = debtor.getBank().removeCardsFallback(actualAmount);
                    for (Card c : payment) {
                        creditor.getBank().deposit(c.transferCopy());
                    }
                    recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_TIMEOUT",
                            creditor.getNickname(),
                            payment.stream().mapToInt(Card::getValue).sum(),
                            "回合结束自动支付 " + actualAmount + "M");
                } catch (Bank.InsufficientFundsException ignored) {}
            }
        }

        // 结算队列中所有剩余的支付
        while (!pendingPaymentQueue.isEmpty()) {
            String[] next = pendingPaymentQueue.poll();
            Player debtor = findPlayer(next[0]);
            Player creditor = findPlayer(next[1]);
            int amount = Integer.parseInt(next[2]);
            if (debtor != null && creditor != null && debtor.getBank().getTotal() > 0) {
                try {
                    int actualAmount = Math.min(amount, debtor.getBank().getTotal());
                    List<Card> payment = debtor.getBank().removeCardsFallback(actualAmount);
                    for (Card c : payment) {
                        creditor.getBank().deposit(c.transferCopy());
                    }
                    recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_TIMEOUT",
                            creditor.getNickname(),
                            payment.stream().mapToInt(Card::getValue).sum(),
                            "回合结束自动支付 " + actualAmount + "M");
                } catch (Bank.InsufficientFundsException ignored) {}
            }
        }

        // 重置支付状态
        pendingPaymentDebtorId = null;
        pendingPaymentCreditorId = null;
        pendingPaymentAmount = 0;
        phase = GamePhase.PLAY;
    }

    /**
     * 强制结束当前回合
     * 1. 取消定时器
     * 2. 自动弃牌到手牌上限（7张）
     * 3. 清除活跃玩家状态
     * 4. 广播游戏状态
     * 5. 延迟1.5秒后开始下一回合
     */
    private synchronized void forceEndTurn() {
        cancelTimer();
        cancelReactionTimeout();

        // 清空决议栈（回合结束，所有未响应的决议视为被目标放弃）
        pendingMultiTargetResolution = null;
        while (!resolutionStack.isEmpty()) {
            ResolutionItem item = resolutionStack.pop();
            if (!item.isJustSayNo()) {
                // 原始行动被放弃，回合结束不执行延迟效果（行动效果丢失作为惩罚）
                recordAction(item.getInitiatorId(),
                        findPlayer(item.getInitiatorId()) != null ?
                                findPlayer(item.getInitiatorId()).getNickname() : "",
                        "ACTION_EXPIRED", "", 0,
                        item.getActionType() + " 因回合结束失效");
            }
        }

        // 如果有待处理支付，使用兜底方案强制结算所有支付
        if (phase == GamePhase.WAITING_FOR_PAYMENT) {
            forceSettleAllPendingPayments();
        }

        if (activePlayer != null && activePlayer.needsToDiscard()) {
            // 进入弃牌阶段：通知客户端选择要弃的牌
            startDiscardPhase();
            return;
        }
        finalizeEndTurn();
    }

    /**
     * 开始弃牌阶段
     * 通知客户端选择要弃掉的牌，启动15秒超时定时器
     */
    private void startDiscardPhase() {
        phase = GamePhase.DISCARD;

        // 构建手牌列表JSON
        com.google.gson.JsonArray handCardsArr = new com.google.gson.JsonArray();
        for (Card card : activePlayer.getHand()) {
            JsonObject cardObj = new JsonObject();
            cardObj.addProperty("cardId", card.getId());
            cardObj.addProperty("cardName", card.getName());
            cardObj.addProperty("cardType", card.getType().name());
            cardObj.addProperty("color", card.getColor().name());
            cardObj.addProperty("value", card.getValue());
            handCardsArr.add(cardObj);
        }

        int discardCount = activePlayer.getHand().size() - GameConstants.MAX_HAND_SIZE;

        JsonObject payload = new JsonObject();
        payload.add("handCards", handCardsArr);
        payload.addProperty("discardCount", discardCount);
        payload.addProperty("timeoutSeconds", GameConstants.DISCARD_TIMEOUT_SECONDS);

        room.sendToPlayer(activePlayer.getId(), MessageProtocol.MessageType.DISCARD_REQUIRED,
                payload.toString());

        broadcastGameState();

        // 启动弃牌超时定时器：超时后自动从手牌开头弃牌
        discardTimeoutTask = scheduler.schedule(() -> {
            synchronized (GameSession.this) {
                if (phase == GamePhase.DISCARD && activePlayer != null) {
                    int needToDiscard = activePlayer.getHand().size() - GameConstants.MAX_HAND_SIZE;
                    for (int i = 0; i < needToDiscard && !activePlayer.getHand().isEmpty(); i++) {
                        Card discarded = activePlayer.removeCardFromHand(0);
                        deck.discard(discarded);
                        recordAction(activePlayer.getId(), activePlayer.getNickname(),
                                "DISCARD_TIMEOUT", "", 0, "超时自动弃掉了 " + discarded.getName());
                    }
                    finalizeEndTurn();
                }
            }
        }, GameConstants.DISCARD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 处理客户端提交的弃牌选择
     * 移除用户选中的卡牌，如果不够则自动补齐
     */
    public synchronized void handleSubmitDiscard(String playerId, JsonObject payload) {
        if (activePlayer == null || !playerId.equals(activePlayer.getId())) {
            sendError(playerId, "不是你的回合");
            return;
        }
        if (phase != GamePhase.DISCARD) {
            sendError(playerId, "当前不在弃牌阶段");
            return;
        }

        // 取消超时定时器
        if (discardTimeoutTask != null && !discardTimeoutTask.isCancelled()) {
            discardTimeoutTask.cancel(false);
        }

        // 移除用户选择的卡牌
        com.google.gson.JsonArray cardIdsArr = payload.getAsJsonArray("cardIds");
        java.util.Set<String> selectedIds = new java.util.HashSet<>();
        for (com.google.gson.JsonElement elem : cardIdsArr) {
            selectedIds.add(elem.getAsString());
        }

        java.util.List<Card> toRemove = new java.util.ArrayList<>();
        for (Card card : activePlayer.getHand()) {
            if (selectedIds.contains(card.getId())) {
                toRemove.add(card);
            }
        }
        for (Card card : toRemove) {
            activePlayer.removeCardFromHand(card);
            deck.discard(card);
            recordAction(activePlayer.getId(), activePlayer.getNickname(),
                    "DISCARD", "", 0, "弃掉了 " + card.getName());
        }

        // 兜底：如果还不够，自动从手牌开头补齐弃牌（防止客户端作弊少选）
        while (activePlayer.needsToDiscard() && !activePlayer.getHand().isEmpty()) {
            Card discarded = activePlayer.removeCardFromHand(0);
            deck.discard(discarded);
            recordAction(activePlayer.getId(), activePlayer.getNickname(),
                    "DISCARD", "", 0, "弃掉了 " + discarded.getName());
        }

        finalizeEndTurn();
    }

    /**
     * 完成回合结束的最后步骤
     * 取消定时器、清除活跃玩家、广播状态、调度下一回合
     */
    private void finalizeEndTurn() {
        if (discardTimeoutTask != null && !discardTimeoutTask.isCancelled()) {
            discardTimeoutTask.cancel(false);
        }

        if (activePlayer != null) {
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
        cancelReactionTimeout();
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
     * 切换万能地产卡颜色 —— 野蛮卡免费变色入口
     * 校验：必须是活跃玩家、在 PLAY 阶段、卡牌必须在物业区
     * 核心规则：不消耗出牌次数（不调用 incrementPlaysUsed）
     */
    public synchronized void handleFlipWildCard(String playerId, String cardId, String newColor) {
        if (!gameRunning || activePlayer == null) return;
        if (!playerId.equals(activePlayer.getId())) return;
        if (phase != GamePhase.PLAY) return;

        Player player = findPlayer(playerId);
        if (player == null) return;

        try {
            CardColor color = CardColor.valueOf(newColor);
            if (!color.isPropertyColor()) {
                sendError(playerId, "无效的地产颜色: " + newColor);
                return;
            }

            boolean ok = player.getPropertyZone().changeWildCardColor(cardId, color);
            if (!ok) {
                sendError(playerId,
                    "变色失败：万能卡不存在、该卡不支持此颜色，"
                    + "或原地产组建有房屋/酒店无法移走");
                return;
            }

            recordAction(playerId, player.getNickname(), "FLIP_WILD", "", 0,
                    "切换万能地产为 " + color.getName());
            broadcastGameState();

            if (player.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
                endGame(player);
            }
        } catch (IllegalArgumentException e) {
            sendError(playerId, "无效的颜色名称: " + newColor);
        }
    }

    /**
     * 处理玩家断线
     * 如果剩余在线玩家不足2人，游戏平局结束。
     * 如果断线的是活跃玩家，自动结束其回合。
     */
    public synchronized void handlePlayerDisconnect(String clientId) {
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
            cancelReactionTimeout();
            if (paymentTimeoutTask != null && !paymentTimeoutTask.isCancelled()) {
                paymentTimeoutTask.cancel(false);
            }
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
