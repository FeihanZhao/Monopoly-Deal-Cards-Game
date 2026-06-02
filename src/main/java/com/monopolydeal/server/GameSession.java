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
 * Game session — manages the complete lifecycle of a single game.
 *
 * This is the core game engine, responsible for:
 * 1. Turn management: rotating active players through draw → play → end phases
 * 2. Timer: 30-second turn timeout with a 10-second warning
 * 3. Game rules: executing all card effects (money, property, rent, action)
 * 4. Payment system: handling money transfers between players with optimal payment calculation
 * 5. Victory detection: checking each turn whether a player has 3 complete property sets
 * 6. State broadcast: sending the latest GameState to all players after every action
 *
 * Turn lifecycle:
 * 1. startNextTurn() — check win condition → advance to next online player → auto-draw → enter PLAY phase → start timer
 * 2. handlePlayCard() — player plays cards (max 3 non-action plays), check remaining plays after each
 * 3. endTurn() / forceEndTurn() — voluntary or forced end → auto-discard down to 7 cards → 1.5s delay → next turn
 */
public class GameSession {
    /** Owning game room */
    private final GameRoom room;
    /** Player list (in join order) */
    private final List<Player> players;
    /** Deck (draw pile + discard pile) */
    private final Deck deck;
    /** Index of current player in the player list */
    private int currentPlayerIndex;
    /** Currently active player (the one whose turn it is) */
    private Player activePlayer;
    /** Current game phase */
    private GamePhase phase;
    /** Timer thread pool for turn timeout control */
    private ScheduledExecutorService scheduler;
    /** Current turn timer future handle (for cancellation) */
    private ScheduledFuture<?> turnTimer;
    /** Second-stage timer handle (warning → timeout), must be independently cancelled */
    private ScheduledFuture<?> turnTimerWarning;
    /** Whether the game is currently running */
    private boolean gameRunning;
    /** Action history list (newest first) */
    private final List<ActionRecord> actionHistory;
    /** Pending payment request: debtor ID */
    private String pendingPaymentDebtorId;
    /** Pending payment request: creditor ID */
    private String pendingPaymentCreditorId;
    /** Pending payment request: amount */
    private int pendingPaymentAmount;
    /** Pending payment queue (FIFO for multi-debtor scenarios): [debtorId, creditorId, amount] */
    private final Queue<String[]> pendingPaymentQueue = new LinkedList<>();
    /** Payment timeout future handle (cancelled on manual submit to avoid races) */
    private ScheduledFuture<?> paymentTimeoutTask;
    /** Resolution stack — pending action / Just Say No chain; top is current responder */
    private final Deque<ResolutionItem> resolutionStack = new ArrayDeque<>();
    /** Reaction timeout future handle (for cancellation) */
    private ScheduledFuture<?> reactionTimeoutTask;
    /** Discard timeout future handle (for cancellation) */
    private ScheduledFuture<?> discardTimeoutTask;
    /** Pending multi-target resolution (next target processed after current payment completes) */
    private ResolutionItem pendingMultiTargetResolution;
    /** Game start timestamp in milliseconds */
    private long gameStartTime;
    /** Gson serializer instance */
    private final Gson gson;

    /**
     * Constructor.
     * @param room owning game room
     * @param players player list
     */
    public GameSession(GameRoom room, List<Player> players) {
        this.room = room;
        this.players = new CopyOnWriteArrayList<>(players);  // thread-safe list
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
     * Start the game.
     * 1. Deal initial hand (5 cards) to each player
     * 2. Broadcast initial game state
     * 3. Begin the first turn
     */
    public synchronized void start() {
        gameRunning = true;
        gameStartTime = System.currentTimeMillis();
        // Deal initial hands
        for (Player player : players) {
            List<Card> initialHand = deck.drawMultiple(GameConstants.INITIAL_HAND_SIZE);
            initialHand.forEach(player::addCardToHand);
        }
        broadcastGameState();
        startNextTurn();  // Start the first turn
    }

    /**
     * Start the next turn.
     * 1. Check win condition
     * 2. Find the next online player
     * 3. Auto-draw 2 cards (5 if hand is empty)
     * 4. Enter PLAY phase
     * 5. Start 30-second timeout timer
     */
    private synchronized void startNextTurn() {
        if (!gameRunning) return;

        // Check if any player has won (3 complete property sets)
        Optional<Player> winner = checkWinner();
        if (winner.isPresent()) {
            endGame(winner.get());
            return;
        }

        // Find the next online player (skip disconnected)
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            activePlayer = players.get(currentPlayerIndex);
        } while (!activePlayer.isConnected() && gameRunning);

        if (!gameRunning) return;

        // Activate the player
        activePlayer.setActivePlayer(true);
        activePlayer.resetTurnState();  // Reset play count and double rent flag
        phase = GamePhase.DRAW;

        // Auto-draw: 5 cards if hand was empty, otherwise 2 (official rules)
        int baseDraw = activePlayer.getHandCount() == 0
                ? GameConstants.EMPTY_HAND_DRAW_COUNT
                : GameConstants.DRAW_COUNT;
        int drawCount = baseDraw;
        // drawMultiple() internally triggers reshuffleDiscardPile() when draw pile is empty
        List<Card> drawnCards = deck.drawMultiple(drawCount);
        drawnCards.forEach(activePlayer::addCardToHand);
        recordAction(activePlayer.getId(), activePlayer.getNickname(), "DRAW", "", 0,
                "drew " + drawnCards.size() + " cards");

        phase = GamePhase.PLAY;
        broadcastGameState();
        startTurnTimer();  // Start 30-second turn timer
    }

    /**
     * Start the turn timeout timer.
     * Sends a warning 10 seconds before timeout, then forces end of turn on expiry.
     */
    private void startTurnTimer() {
        cancelTimer();
        // Warn at 20s, force-end 10s later
        turnTimer = scheduler.schedule(() -> {
            room.sendToPlayer(activePlayer.getId(), MessageProtocol.MessageType.TURN_TIMEOUT,
                    "{\"secondsRemaining\":" + GameConstants.TIMEOUT_WARNING_SECONDS + "}");
            turnTimerWarning = scheduler.schedule(() -> {
                room.broadcast(MessageProtocol.MessageType.TURN_TIMEOUT,
                        "{\"playerId\":\"" + activePlayer.getId() + "\",\"reason\":\"Turn timeout\"}");
                forceEndTurn();
            }, GameConstants.TIMEOUT_WARNING_SECONDS, TimeUnit.SECONDS);
        }, GameConstants.TURN_TIMEOUT_SECONDS - GameConstants.TIMEOUT_WARNING_SECONDS, TimeUnit.SECONDS);
    }

    /** Cancel the current turn timer (both stages) */
    private void cancelTimer() {
        if (turnTimer != null && !turnTimer.isCancelled()) {
            turnTimer.cancel(false);
        }
        if (turnTimerWarning != null && !turnTimerWarning.isCancelled()) {
            turnTimerWarning.cancel(false);
        }
    }

    /**
     * Handle a play-card request — validate permissions then dispatch to the appropriate handler
     * based on the action field.
     *
     * Supported action types:
     * - PLAY_MONEY: deposit a money card into the bank
     * - PLAY_PROPERTY: place a property card into the property zone
     * - PLAY_RENT: charge rent using a rent card
     * - PLAY_ACTION: execute a special effect using an action card
     *
     * @param playerId ID of the requesting player
     * @param payload JSON object containing cardId, action, and additional parameters
     *                (e.g. color selection, target player, etc.)
     */
    public synchronized void handlePlayCard(String playerId, JsonObject payload) {
        // Permission check: game must be running, must be active player, must be in PLAY phase
        if (!gameRunning || activePlayer == null) return;
        if (!playerId.equals(activePlayer.getId())) return;
        if (phase != GamePhase.PLAY) {
            sendError(playerId, "Cannot play cards in the current phase");
            return;
        }
        String cardId = payload.get("cardId").getAsString();
        String action = payload.get("action").getAsString();

        // Action cards don't count toward the 3-plays-per-turn limit
        if (!"PLAY_ACTION".equals(action) && !activePlayer.canPlay()) {
            sendError(playerId, "No remaining plays this turn");
            return;
        }
        Card card = activePlayer.findCardById(cardId);

        if (card == null) {
            sendError(playerId, "Card not found in hand");
            return;
        }

        try {
            boolean played = false;
            switch (action) {
                case "PLAY_MONEY":
                    played = playMoneyCard(card);       // deposit to bank
                    break;
                case "PLAY_PROPERTY":
                    played = playPropertyCard(card, payload); // place property
                    break;
                case "PLAY_RENT":
                    played = playRentCard(card, payload);    // charge rent
                    break;
                case "PLAY_ACTION":
                    played = playActionCard(card, payload);  // execute action
                    break;
                default:
                    sendError(playerId, "Unknown action: " + action);
                    return;
            }

            if (played) {
                // Action cards don't consume a play; money/property/rent cards do
                if (!"PLAY_ACTION".equals(action)) {
                    activePlayer.incrementPlaysUsed();
                }
                recordAction(playerId, activePlayer.getNickname(), action, "", 0, card.getName());
                broadcastGameState();

                // Only auto-end turn if still in PLAY phase (if already in WAITING_FOR_PAYMENT or
                // WAITING_FOR_REACTION, the respective completion callbacks handle it)
                if (phase == GamePhase.PLAY && activePlayer.getRemainingPlays() <= 0) {
                    scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            sendError(playerId, e.getMessage());
        }
    }

    /**
     * Play a card as money — remove from hand and deposit into bank.
     * Any card with canBeUsedAsMoney() true can be banked (including action/rent cards).
     * Once banked, the card only counts as a monetary asset; original effects are lost.
     * @param card card with value > 0
     * @return true=success
     */
    private boolean playMoneyCard(Card card) {
        if (!card.canBeUsedAsMoney()) return false;
        activePlayer.removeCardFromHand(card);
        activePlayer.getBank().deposit(card);
        return true;
    }

    /**
     * Execute a property card effect — place a property card into the property zone.
     * For wild property cards, supports player color selection.
     * After placing, checks whether the win condition is met (3 complete sets).
     *
     * @param card property card
     * @param payload may contain a color field (wild property color selection)
     * @return true=success
     */
    private boolean playPropertyCard(Card card, JsonObject payload) {
        if (!card.isPropertyCard()) return false;
        activePlayer.removeCardFromHand(card);

        // Wild property: set player-chosen color
        if (card.isWildProperty() && payload.has("color")) {
            try {
                String colorName = payload.get("color").getAsString();
                card.setWildColor(CardColor.valueOf(colorName));
            } catch (IllegalArgumentException ignored) {}
        }

        activePlayer.getPropertyZone().addProperty(card);

        // Check win condition after placing property
        if (activePlayer.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
            endGame(activePlayer);
        }
        return true;
    }

    /**
     * Execute a rent card effect — charge rent from one or more players.
     *
     * Rent calculation logic:
     * 1. Determine the rent color (standard rent cards have two fixed colors; wild rent requires color selection)
     * 2. Calculate base rent = base rate based on how many properties of that color are held
     * 3. If double rent is active, amount x2
     * 4. Dual-color rent charges all players; wild rent charges a single specified target
     *
     * @param card rent card
     * @param payload may contain color and targetPlayerId fields
     * @return true=executed successfully
     */
    private boolean playRentCard(Card card, JsonObject payload) {
        if (!card.isRentCard()) return false;

        // Step 1: determine rent color (validate first, don't remove card yet)
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

        // Wild rent card client color validation: must be a valid property color
        if (isWildRent && rentColor != CardColor.WILD && !rentColor.isPropertyColor()) {
            rentColor = CardColor.WILD;
        }
        payload.addProperty("isWildRent", isWildRent);

        // Step 2: pre-calculate rent amount
        int baseRentAmount;
        if (isWildRent && rentColor == CardColor.WILD) {
            // Wild rent with no color selected: default 2M
            baseRentAmount = 2;
        } else {
            // Dual-color rent card: compute for both component colors, take the larger
            CardColor[] components = card.getColor().getComponentColors();
            if (components.length == 2) {
                int rent1 = activePlayer.getPropertyZone().getRentAmount(components[0]);
                int rent2 = activePlayer.getPropertyZone().getRentAmount(components[1]);
                baseRentAmount = Math.max(rent1, rent2);
                // Pick the higher-yield color as the formal rent color
                rentColor = (rent1 >= rent2) ? components[0] : components[1];
            } else {
                baseRentAmount = activePlayer.getPropertyZone().getRentAmount(rentColor);
            }
            // Reject if no matching property color to charge rent on
            if (baseRentAmount == 0) {
                sendError(activePlayer.getId(),
                        "You don't have matching property color to charge rent");
                return false;
            }
        }
        payload.addProperty("color", rentColor.name());

        int rentAmount = activePlayer.isDoubleRentActive() ? baseRentAmount * 2 : baseRentAmount;
        activePlayer.setDoubleRentActive(false);  // Consume immediately to prevent flag leak after JSN cancel
        payload.addProperty("_preCalculatedRent", rentAmount);

        // Step 3: collect all target players
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

        // Step 4: validation passed, remove from hand and discard
        activePlayer.removeCardFromHand(card);
        deck.discard(card);

        // Step 5: first target is the current responder, store the rest in _remainingTargets
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
     * Execute an action card effect — dispatch to the specific effect based on card name.
     *
     * Supported action card types:
     * - Debt Collector: collect 5M from a specified player
     * - Birthday: all players pay 2M each
     * - Deal Breaker: steal a complete property set from a player
     * - Pass Go: draw 2 extra cards
     * - Double the Rent: next rent charge is doubled
     * - House: build a house on a complete set (+1 rent/house, max 4)
     * - Hotel: upgrade 4 houses to a hotel (+3 rent)
     * - Forced Deal: swap a property card with another player
     * - Sly Deal: steal a single property card from another player
     * - Just Say No: cancel an action card targeting you
     *
     * @param card action card
     * @param payload may contain extra parameters like targetPlayerId, color, etc.
     * @return true=executed successfully
     */
    private boolean playActionCard(Card card, JsonObject payload) {
        if (!card.isActionCard()) return false;

        String actionName = card.getName();

        // Just Say No cannot be played from hand — only used reactively
        if (actionName.contains("Just Say No")) return false;

        // === Step 1: self-targeting actions — execute immediately (discard only after validation) ===
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

        // === Step 2: targeted actions — push to resolution stack, defer execution ===
        String actionType = mapActionNameToType(actionName);
        String targetId = extractTargetId(payload);

        // Determine target player (auto-select if client didn't specify)
        if (targetId.isEmpty()) {
            if (actionName.contains("Deal Breaker")) {
                for (Player p : players) {
                    if (!p.equals(activePlayer) && p.getCompleteSetsCount() > 0) {
                        targetId = p.getId();
                        break;
                    }
                }
            } else if (actionName.contains("Forced Deal")) {
                // Auto-select: first other player with properties
                for (Player p : players) {
                    if (!p.equals(activePlayer) && !p.getPropertyZone().getAllPropertyGroups().isEmpty()) {
                        targetId = p.getId();
                        break;
                    }
                }
            } else if (actionName.contains("Sly Deal")) {
                // Auto-select: first player with a stealable property
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
                // Birthday: collect all other players, first as responder, rest stored in _remainingTargets
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
                // Default: pick the first other player
                for (Player p : players) {
                    if (!p.equals(activePlayer)) {
                        targetId = p.getId();
                        break;
                    }
                }
            }
        }

        if (targetId.isEmpty()) return false;

        // Steal-type cards: fill in target property selection (must do this regardless of whether targetId came from client or server auto-select)
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

        // Ensure targetPlayerId is present in payload
        if (!payload.has("targetPlayerId")) {
            payload.addProperty("targetPlayerId", targetId);
        }

        // Validation passed, remove from hand and discard
        activePlayer.removeCardFromHand(card);
        deck.discard(card);

        // Push to resolution stack
        pushResolution(actionType, activePlayer.getId(), targetId, card, payload);

        return true;
    }

    /**
     * Execute Forced Deal: swap specified property cards between two players.
     * @param player1 first player
     * @param player2 second player
     * @param cardId1 property card ID that player1 is giving up
     * @param cardId2 property card ID that player2 is giving up
     */
    private void executeForcedDeal(Player player1, Player player2, String cardId1, String cardId2) {
        Card card1 = findPropertyInZone(player1, cardId1);
        Card card2 = findPropertyInZone(player2, cardId2);
        if (card1 == null || card2 == null) return;

        // Remove from respective property zones
        player1.getPropertyZone().removeProperty(card1);
        player2.getPropertyZone().removeProperty(card2);

        // Swap into each other's property zones
        player1.getPropertyZone().addProperty(card2);
        player2.getPropertyZone().addProperty(card1);

        recordAction(player1.getId(), player1.getNickname(), "FORCED_DEAL",
                player2.getNickname(), 0, card1.getName() + " <-> " + card2.getName());
    }

    /**
     * Execute Sly Deal: steal a specified property card from another player.
     * @param thief stealing player
     * @param victim target player
     * @param cardId ID of the property card to steal
     */
    private void executeSlyDeal(Player thief, Player victim, String cardId) {
        Card stolenCard = findPropertyInZone(victim, cardId);
        if (stolenCard == null) return;

        victim.getPropertyZone().removeProperty(stolenCard);
        if (stolenCard.isWildProperty()) stolenCard.setWildColor(null);  // Reset wild color
        thief.getPropertyZone().addProperty(stolenCard);

        recordAction(thief.getId(), thief.getNickname(), "SLY_DEAL",
                victim.getNickname(), 0, "stole " + stolenCard.getName());
    }

    /** Find a property card by ID in a player's property zone */
    private Card findPropertyInZone(Player player, String cardId) {
        for (List<Card> properties : player.getPropertyZone().getAllPropertyGroups().values()) {
            for (Card card : properties) {
                if (card.getId().equals(cardId)) return card;
            }
        }
        return null;
    }

    // ==================== Resolution stack core methods ====================

    /**
     * Push an action onto the resolution stack and enter WAITING_FOR_REACTION phase.
     *
     * @param actionType   action type
     * @param initiatorId  initiator player ID
     * @param responderId  responder player ID
     * @param sourceCard   the card that was played
     * @param actionPayload original request payload
     */
    private void pushResolution(String actionType, String initiatorId,
                                String responderId, Card sourceCard,
                                JsonObject actionPayload) {
        String resolutionId = UUID.randomUUID().toString().substring(0, 8);
        ResolutionItem item = new ResolutionItem(resolutionId, actionType,
                initiatorId, responderId, sourceCard, actionPayload);
        resolutionStack.push(item);

        // Enter waiting-for-reaction phase (turn timer keeps running independently)
        phase = GamePhase.WAITING_FOR_REACTION;

        // Notify the responder
        sendReactionRequired(responderId, item);

        // Start 5-second reaction timeout
        startReactionTimeout(responderId);
    }

    /** Send REACTION_REQUIRED message to the responder */
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

    /** Start the reaction timeout timer */
    private void startReactionTimeout(String responderId) {
        cancelReactionTimeout();
        reactionTimeoutTask = scheduler.schedule(
                () -> handleReactionTimeout(responderId),
                GameConstants.JUST_SAY_NO_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** Cancel the reaction timeout timer */
    private void cancelReactionTimeout() {
        if (reactionTimeoutTask != null && !reactionTimeoutTask.isCancelled()) {
            reactionTimeoutTask.cancel(false);
        }
    }

    /** Reaction timeout — treated as pass, auto-resolve top */
    private synchronized void handleReactionTimeout(String responderId) {
        if (resolutionStack.isEmpty()) return;
        ResolutionItem top = resolutionStack.peek();
        if (top == null || !top.getResponderId().equals(responderId)) return;

        // Timeout is treated as pass
        recordAction(responderId, findPlayer(responderId) != null ?
                        findPlayer(responderId).getNickname() : "",
                "PASS_REACTION", "", 0, "reaction timeout");

        resolveTopResolution();
    }

    /** Handle a player playing Just Say No */
    public synchronized void handlePlayJustSayNo(String playerId, JsonObject payload) {
        if (phase != GamePhase.WAITING_FOR_REACTION) {
            sendError(playerId, "Cannot play Just Say No in the current phase");
            return;
        }
        if (resolutionStack.isEmpty()) {
            sendError(playerId, "No pending action to react to");
            return;
        }

        ResolutionItem currentTop = resolutionStack.peek();
        // Validate: must be the current responder
        if (!playerId.equals(currentTop.getResponderId())) {
            sendError(playerId, "You are not the current responder");
            return;
        }

        // Find the Just Say No card in hand
        String cardId = payload.get("cardId").getAsString();
        Player responder = findPlayer(playerId);
        if (responder == null) return;

        Card jsnCard = responder.findCardById(cardId);
        if (jsnCard == null || !jsnCard.getName().contains("Just Say No")) {
            sendError(playerId, "Just Say No card not found in hand");
            return;
        }

        // Play Just Say No: remove from hand and discard
        responder.removeCardFromHand(jsnCard);
        deck.discard(jsnCard);
        cancelReactionTimeout();

        // Push Just Say No onto stack top, responder becomes the original initiator
        String newResponderId = currentTop.getInitiatorId();
        JsonObject jsnPayload = new JsonObject();
        jsnPayload.addProperty("counteredResolutionId", currentTop.getResolutionId());

        pushResolution("JUST_SAY_NO", playerId, newResponderId, jsnCard, jsnPayload);

        recordAction(playerId, responder.getNickname(), "JUST_SAY_NO",
                findPlayer(newResponderId) != null ?
                        findPlayer(newResponderId).getNickname() : "",
                0, "played Just Say No to counter " + currentTop.getActionType());
        broadcastGameState();
    }

    /** Handle a player passing on their reaction (declining to play Just Say No) */
    public synchronized void handlePassReaction(String playerId) {
        if (resolutionStack.isEmpty()) {
            sendError(playerId, "No pending action to react to");
            return;
        }

        ResolutionItem top = resolutionStack.peek();
        if (!playerId.equals(top.getResponderId())) {
            sendError(playerId, "You are not the current responder");
            return;
        }

        cancelReactionTimeout();
        recordAction(playerId, findPlayer(playerId) != null ?
                        findPlayer(playerId).getNickname() : "",
                "PASS_REACTION", "", 0, "passed on " + top.getActionType());

        resolveTopResolution();
    }

    /**
     * Pop the top resolution from the stack and process it.
     *
     * Pop rules:
     * - If top is JUST_SAY_NO → it succeeded → pop the element below it (cancelled)
     * - If top is an original action → it was not cancelled → execute deferred effect
     * - After processing, if stack is non-empty → continue waiting for next responder
     * - If stack is empty → return to PLAY or enter WAITING_FOR_PAYMENT
     */
    private void resolveTopResolution() {
        if (resolutionStack.isEmpty()) return;

        ResolutionItem resolved = resolutionStack.pop();

        if (resolved.isJustSayNo()) {
            // Just Say No succeeded → cancel the resolution it was stacked on top of
            if (!resolutionStack.isEmpty()) {
                ResolutionItem cancelled = resolutionStack.pop();
                recordAction(resolved.getInitiatorId(),
                        findPlayer(resolved.getInitiatorId()) != null ?
                                findPlayer(resolved.getInitiatorId()).getNickname() : "",
                        "ACTION_CANCELLED", "",
                        0, cancelled.getActionType() + " cancelled by Just Say No");
                // Multi-target: JSN only cancels the current player's obligation, continue to next target
                if (continueMultiTargetResolution(cancelled)) return;
            }
        } else {
            // Original action was not cancelled, execute deferred effect
            executeDeferredAction(resolved);
            // Multi-target: stash resolution, advance after current payment completes
            if (hasRemainingTargets(resolved)) {
                pendingMultiTargetResolution = resolved;
            }
        }

        // Stack non-empty → continue waiting for next responder
        if (!resolutionStack.isEmpty()) {
            ResolutionItem nextTop = resolutionStack.peek();
            sendReactionRequired(nextTop.getResponderId(), nextTop);
            startReactionTimeout(nextTop.getResponderId());
            return;
        }

        // Stack empty → resolution phase fully complete
        if (pendingPaymentDebtorId != null) {
            broadcastGameState();
            return;
        }

        // If a pending multi-target resolution remains (e.g. previous target had zero balance, skip payment), advance to next
        if (pendingMultiTargetResolution != null) {
            ResolutionItem saved = pendingMultiTargetResolution;
            pendingMultiTargetResolution = null;
            if (continueMultiTargetResolution(saved)) {
                broadcastGameState();
                return;
            }
        }

        // No pending payment and no pending multi-target → directly resume play phase
        phase = GamePhase.PLAY;
        broadcastGameState();

        if (activePlayer != null && activePlayer.getRemainingPlays() <= 0) {
            scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
        }
    }

    /** Check whether there are remaining multi-targets to process */
    private boolean hasRemainingTargets(ResolutionItem item) {
        JsonObject payload = item.getActionPayload();
        if (!payload.has("_remainingTargets")) return false;
        com.google.gson.JsonArray remaining = payload.getAsJsonArray("_remainingTargets");
        return remaining != null && remaining.size() > 0;
    }

    /**
     * Multi-target action helper — pop the next target from the unprocessed list and push to resolution stack.
     * Does nothing if _remainingTargets is absent or empty.
     */
    private boolean continueMultiTargetResolution(ResolutionItem resolvedItem) {
        JsonObject payload = resolvedItem.getActionPayload();
        if (!payload.has("_remainingTargets")) return false;

        com.google.gson.JsonArray remaining = payload.getAsJsonArray("_remainingTargets");
        if (remaining == null || remaining.size() == 0) return false;

        // Pop the next target player ID
        String nextTarget = remaining.remove(0).getAsString();
        payload.addProperty("targetPlayerId", nextTarget);

        // Remove the marker field if no remaining targets
        if (remaining.size() == 0) {
            payload.remove("_remainingTargets");
        }

        // Push new resolution; next target becomes the new responder
        pushResolution(resolvedItem.getActionType(),
                resolvedItem.getInitiatorId(),
                nextTarget,
                resolvedItem.getSourceCard(),
                payload);
        return true;
    }

    /**
     * Execute a deferred action effect (called after resolution passes).
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
                System.err.println("Error: executeDeferredAction received unknown action type '" + actionType + "'");
                break;
        }
    }

    // ==================== Deferred action effect execution ====================

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
                    "collecting " + GameConstants.DEBT_COLLECTOR_AMOUNT + "M");
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
                    target.getNickname() + " pays " + amount + "M");
        }
    }

    private void executeRent(Player initiator, JsonObject payload) {
        // Use pre-calculated rent amount from playRentCard (compute fallback if missing)
        int rentAmount;
        if (payload.has("_preCalculatedRent")) {
            rentAmount = payload.get("_preCalculatedRent").getAsInt();
        } else {
            // Fallback calculation (consistent with playRentCard logic)
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
                // Dual-color rent card: take max of the two component colors
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

        // Single-target charge (in multi-target scenarios, each player is handled by their own resolution item)
        String targetPlayerId = payload.has("targetPlayerId")
                ? payload.get("targetPlayerId").getAsString() : "";
        Player targetPlayer = findPlayer(targetPlayerId);
        if (targetPlayer != null) {
            requirePayment(targetPlayer, initiator, rentAmount);
            recordAction(initiator.getId(), initiator.getNickname(), "RENT",
                    targetPlayer.getNickname(), rentAmount,
                    "charged " + targetPlayer.getNickname() + " rent " + rentAmount + "M");
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

    /** Steal a complete property set (Deal Breaker effect) */
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
                "stole complete set: " + setToSteal.getName());
        // Check if this triggers a win
        if (initiator.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
            endGame(initiator);
        }
    }

    /** Steal a single property card (Sly Deal effect — deferred execution version) */
    private void executeSlyDeal(Player initiator, JsonObject payload) {
        if (!payload.has("targetPlayerId") || !payload.has("targetCardId")) return;
        String targetPlayerId = payload.get("targetPlayerId").getAsString();
        String targetCardId = payload.get("targetCardId").getAsString();
        Player target = findPlayer(targetPlayerId);
        if (target != null && targetCardId != null) {
            Card stolenCard = findPropertyInZone(target, targetCardId);
            if (stolenCard == null) return;
            // Cannot steal a property from a complete set
            if (target.getPropertyZone().getCompleteSets()
                    .contains(stolenCard.getEffectiveColor())) return;
            target.getPropertyZone().removeProperty(stolenCard);
            initiator.getPropertyZone().addProperty(stolenCard);
            recordAction(initiator.getId(), initiator.getNickname(), "SLY_DEAL",
                    target.getNickname(), 0, "stole " + stolenCard.getName());
        }
    }

    /** Forced Deal (deferred execution version) */
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

    // ==================== Helper methods ====================

    /** Map a card name to the standard action type string */
    private String mapActionNameToType(String actionName) {
        if (actionName.contains("Debt Collector")) return "DEBT_COLLECTOR";
        if (actionName.contains("Birthday")) return "BIRTHDAY";
        if (actionName.contains("Deal Breaker")) return "DEAL_BREAKER";
        if (actionName.contains("Sly Deal")) return "SLY_DEAL";
        if (actionName.contains("Forced Deal")) return "FORCED_DEAL";
        if (actionName.contains("Rent") || actionName.contains("rent")) return "RENT";
        System.err.println("Warning: unrecognized action card name '" + actionName + "', treating as UNKNOWN");
        return "UNKNOWN";
    }

    /** Extract the target player ID from payload */
    private String extractTargetId(JsonObject payload) {
        if (payload.has("targetPlayerId")) {
            String id = payload.get("targetPlayerId").getAsString();
            if (id != null && !id.isEmpty()) return id;
        }
        return "";
    }

    // ==================== Payment system ====================

    /**
     * Initiate an async payment request — send PAYMENT_REQUIRED to the debtor client.
     *
     * Payment is no longer synchronous. The debtor receives the message, selects cards
     * in the client, and submits the selection via SUBMIT_PAYMENT; handleSubmitPayment
     * executes the transfer. Multiple debtors are processed sequentially via a FIFO queue.
     *
     * @param debtor   paying player
     * @param creditor receiving player
     * @param amount   payment amount
     */
    private void requirePayment(Player debtor, Player creditor, int amount) {
        if (debtor.getBank().getTotal() == 0) {
            recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_SKIPPED",
                    creditor.getNickname(), amount, "zero balance, no payment needed");
            broadcastGameState();
            return;
        }

        // Cannot afford the full debt — auto-pay everything immediately (no choice to make)
        if (debtor.getBank().getTotal() < amount) {
            List<Card> allCards = debtor.getBank().removeAllCards();
            int totalPaid = 0;
            for (Card c : allCards) {
                totalPaid += c.getValue();
                creditor.getBank().deposit(c.transferCopy());
            }
            recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_MADE",
                    creditor.getNickname(), totalPaid,
                    "paid all " + totalPaid + "M (could not afford " + amount + "M)");
            broadcastGameState();
            return;
        }

        // Can afford — ask player to select which cards to pay
        if (pendingPaymentDebtorId != null) {
            pendingPaymentQueue.add(new String[]{
                    debtor.getId(), creditor.getId(), String.valueOf(amount)});
            return;
        }

        sendPaymentRequest(debtor, creditor, amount);
    }

    /** Send a payment request message to the specified debtor */
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

        // Enter payment waiting phase (turn timer keeps running independently)
        phase = GamePhase.WAITING_FOR_PAYMENT;

        // 30-second timeout fallback
        final int capturedAmount = amount;
        this.paymentTimeoutTask = scheduler.schedule(
                () -> handlePaymentTimeout(debtor, creditor, capturedAmount),
                30, TimeUnit.SECONDS);
    }

    /** Payment timeout fallback — auto-select cards using greedy algorithm */
    private synchronized void handlePaymentTimeout(Player debtor, Player creditor, int expectedAmount) {
        if (pendingPaymentDebtorId == null
                || !pendingPaymentDebtorId.equals(debtor.getId())
                || pendingPaymentAmount != expectedAmount) {
            return;
        }

        List<Card> payment;
        if (debtor.getBank().getTotal() < pendingPaymentAmount) {
            payment = debtor.getBank().removeAllCards();
        } else {
            try {
                payment = debtor.getBank().removeCardsFallback(pendingPaymentAmount);
            } catch (Bank.InsufficientFundsException e) {
                payment = debtor.getBank().removeAllCards();
            }
        }
        int actualPaid = 0;
        for (Card moneyCard : payment) {
            actualPaid += moneyCard.getValue();
            creditor.getBank().deposit(moneyCard.transferCopy());
        }
        recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_TIMEOUT",
                creditor.getNickname(), actualPaid,
                "auto-paid on timeout " + actualPaid + "M");

        clearPendingPayment();
        broadcastGameState();
    }

    /**
     * Handle a player's submitted payment selection — routed by ClientHandler.
     * Called after the debtor selects cards to pay; validates and executes the transfer.
     */
    public synchronized void handleSubmitPayment(String playerId, JsonObject payload) {
        if (phase != GamePhase.WAITING_FOR_PAYMENT) {
            sendError(playerId, "Cannot pay in the current phase");
            return;
        }
        if (!playerId.equals(pendingPaymentDebtorId)) {
            sendError(playerId, "No pending payment request");
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
                    "paid " + totalPaid + "M (required " + pendingPaymentAmount + "M)");
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

    /** Clear current pending payment state and dequeue the next payment request */
    private void clearPendingPayment() {
        pendingPaymentDebtorId = null;
        pendingPaymentCreditorId = null;
        pendingPaymentAmount = 0;

        if (!pendingPaymentQueue.isEmpty()) {
            // Process next payment in queue (stay in WAITING_FOR_PAYMENT phase)
            String[] next = pendingPaymentQueue.poll();
            Player nextDebtor = findPlayer(next[0]);
            Player nextCreditor = findPlayer(next[1]);
            int nextAmount = Integer.parseInt(next[2]);
            if (nextDebtor != null && nextCreditor != null) {
                requirePayment(nextDebtor, nextCreditor, nextAmount);
            } else {
                // Next payment invalid (player disconnected, etc.), skip and continue
                clearPendingPayment();
            }
            return;  // Still have pending payments, don't restore PLAY phase
        }

        // All payments complete
        // If a pending multi-target resolution remains, advance to next target
        if (pendingMultiTargetResolution != null) {
            ResolutionItem saved = pendingMultiTargetResolution;
            pendingMultiTargetResolution = null;
            if (continueMultiTargetResolution(saved)) {
                return; // Pushed next target, waiting for JSN response
            }
        }

        // All targets processed, restore play phase
        phase = GamePhase.PLAY;
        broadcastGameState();

        // Auto-end turn if active player has no remaining plays
        if (activePlayer != null && activePlayer.getRemainingPlays() <= 0) {
            scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
        }
    }

    /** Handle player voluntarily ending their turn */
    public synchronized void endTurn(String playerId) {
        if (activePlayer == null || !playerId.equals(activePlayer.getId())) return;

        if (phase == GamePhase.WAITING_FOR_PAYMENT || phase == GamePhase.WAITING_FOR_REACTION) {
            sendError(playerId, "Please wait for current action to finish before ending turn");
            return;
        }
        if (phase == GamePhase.DISCARD) {
            sendError(playerId, "Please wait for discard to complete, turn will end automatically");
            return;
        }

        forceEndTurn();
    }

    /**
     * Force-settle all pending payments (called on turn timeout / player disconnect).
     * Auto-settles both the current pending payment and all queued payments using greedy fallback.
     */
    private void forceSettleAllPendingPayments() {
        // Cancel payment timeout timer to prevent race conditions
        if (paymentTimeoutTask != null && !paymentTimeoutTask.isCancelled()) {
            paymentTimeoutTask.cancel(false);
        }
        // Settle current pending payment first
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
                            "auto-paid at end of turn " + actualAmount + "M");
                } catch (Bank.InsufficientFundsException ignored) {}
            }
        }

        // Settle all remaining queued payments
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
                            "auto-paid at end of turn " + actualAmount + "M");
                } catch (Bank.InsufficientFundsException ignored) {}
            }
        }

        // Reset payment state
        pendingPaymentDebtorId = null;
        pendingPaymentCreditorId = null;
        pendingPaymentAmount = 0;
        phase = GamePhase.PLAY;
    }

    /**
     * Force-end the current turn.
     * 1. Cancel timers
     * 2. Auto-discard down to hand limit (7 cards)
     * 3. Clear active player state
     * 4. Broadcast game state
     * 5. Delay 1.5 seconds then start next turn
     */
    private synchronized void forceEndTurn() {
        cancelTimer();
        cancelReactionTimeout();

        // Clear resolution stack (turn ended, all unresponded resolutions treated as abandoned by target)
        pendingMultiTargetResolution = null;
        while (!resolutionStack.isEmpty()) {
            ResolutionItem item = resolutionStack.pop();
            if (!item.isJustSayNo()) {
                // Original action abandoned — deferred effect is lost at end of turn (penalty for letting it expire)
                recordAction(item.getInitiatorId(),
                        findPlayer(item.getInitiatorId()) != null ?
                                findPlayer(item.getInitiatorId()).getNickname() : "",
                        "ACTION_EXPIRED", "", 0,
                        item.getActionType() + " expired (turn ended)");
            }
        }

        // If there are pending payments, force-settle all using fallback
        if (phase == GamePhase.WAITING_FOR_PAYMENT) {
            forceSettleAllPendingPayments();
        }

        if (activePlayer != null && activePlayer.needsToDiscard()) {
            // Enter discard phase: notify client to select cards to discard
            startDiscardPhase();
            return;
        }
        finalizeEndTurn();
    }

    /**
     * Start the discard phase.
     * Notify the client to select cards to discard and start a 15-second timeout timer.
     */
    private void startDiscardPhase() {
        phase = GamePhase.DISCARD;

        // Build hand card list JSON
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

        // Start discard timeout: auto-discard from front of hand on expiry
        discardTimeoutTask = scheduler.schedule(() -> {
            synchronized (GameSession.this) {
                if (phase == GamePhase.DISCARD && activePlayer != null) {
                    int needToDiscard = activePlayer.getHand().size() - GameConstants.MAX_HAND_SIZE;
                    for (int i = 0; i < needToDiscard && !activePlayer.getHand().isEmpty(); i++) {
                        Card discarded = activePlayer.removeCardFromHand(0);
                        deck.discard(discarded);
                        recordAction(activePlayer.getId(), activePlayer.getNickname(),
                                "DISCARD_TIMEOUT", "", 0, "auto-discarded on timeout " + discarded.getName());
                    }
                    finalizeEndTurn();
                }
            }
        }, GameConstants.DISCARD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Handle a client's discard selection submission.
     * Removes the user-selected cards; auto-fills from front of hand if not enough were selected.
     */
    public synchronized void handleSubmitDiscard(String playerId, JsonObject payload) {
        if (activePlayer == null || !playerId.equals(activePlayer.getId())) {
            sendError(playerId, "Not your turn");
            return;
        }
        if (phase != GamePhase.DISCARD) {
            sendError(playerId, "Not in discard phase");
            return;
        }

        // Cancel timeout timer
        if (discardTimeoutTask != null && !discardTimeoutTask.isCancelled()) {
            discardTimeoutTask.cancel(false);
        }

        // Remove user-selected cards
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
                    "DISCARD", "", 0, "discarded " + card.getName());
        }

        // Fallback: if not enough, auto-discard from front of hand (prevents client cheating by under-selecting)
        while (activePlayer.needsToDiscard() && !activePlayer.getHand().isEmpty()) {
            Card discarded = activePlayer.removeCardFromHand(0);
            deck.discard(discarded);
            recordAction(activePlayer.getId(), activePlayer.getNickname(),
                    "DISCARD", "", 0, "discarded " + discarded.getName());
        }

        finalizeEndTurn();
    }

    /**
     * Finalize the end-of-turn steps.
     * Cancel timers, clear active player, broadcast state, schedule next turn.
     */
    private void finalizeEndTurn() {
        if (discardTimeoutTask != null && !discardTimeoutTask.isCancelled()) {
            discardTimeoutTask.cancel(false);
        }

        if (activePlayer != null) {
            activePlayer.setActivePlayer(false);
            recordAction(activePlayer.getId(), activePlayer.getNickname(),
                    "END_TURN", "", 0, "turn ended");
        }
        phase = GamePhase.END;
        broadcastGameState();
        // Delay 1.5 seconds before starting next turn (gives players time to see last turn's results)
        scheduler.schedule(this::startNextTurn, 1500, TimeUnit.MILLISECONDS);
    }

    /** Check if any player has met the win condition (3 complete property sets) */
    private Optional<Player> checkWinner() {
        return players.stream()
                .filter(p -> p.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS)
                .findFirst();
    }

    /** End the game — broadcast GAME_OVER message */
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

        recordAction(winner.getId(), winner.getNickname(), "WINNER", "", 0, "won the game!");
        broadcastGameState();
        room.broadcast(MessageProtocol.MessageType.GAME_OVER, result.toString());
    }

    /**
     * Flip wild property card color — free color-change entry point for wild cards.
     * Validates: must be active player, in PLAY phase, card must be in property zone.
     * Core rule: does NOT consume a play (does not call incrementPlaysUsed).
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
                sendError(playerId, "Invalid property color: " + newColor);
                return;
            }

            boolean ok = player.getPropertyZone().changeWildCardColor(cardId, color);
            if (!ok) {
                sendError(playerId,
                        "Color change failed: card not found, color not supported,"
                                + " or set has house/hotel");
                return;
            }

            recordAction(playerId, player.getNickname(), "FLIP_WILD", "", 0,
                    "flipped wild property to " + color.getName());
            broadcastGameState();

            if (player.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
                endGame(player);
            }
        } catch (IllegalArgumentException e) {
            sendError(playerId, "Invalid color name: " + newColor);
        }
    }

    /**
     * Handle a player disconnecting.
     * If fewer than 2 players remain online, the game ends in a draw.
     * If the disconnecting player is the active player, force-end their turn.
     */
    public synchronized void handlePlayerDisconnect(String clientId) {
        Player disconnected = findPlayer(clientId);
        if (disconnected == null) return;

        disconnected.setConnected(false);
        disconnected.setReady(false);
        recordAction(clientId, disconnected.getNickname(), "DISCONNECT", "", 0, "Player disconnected");

        broadcastGameState();

        long connectedPlayers = players.stream().filter(Player::isConnected).count();
        if (connectedPlayers < GameConstants.MIN_PLAYERS) {
            // Insufficient online players, game ends (draw)
            gameRunning = false;
            cancelTimer();
            cancelReactionTimeout();
            if (paymentTimeoutTask != null && !paymentTimeoutTask.isCancelled()) {
                paymentTimeoutTask.cancel(false);
            }
            JsonObject drawResult = new JsonObject();
            drawResult.addProperty("reason", "Insufficient online players");
            drawResult.addProperty("connectedPlayers", connectedPlayers);
            room.broadcast(MessageProtocol.MessageType.GAME_DRAW, drawResult.toString());
        } else if (activePlayer != null && clientId.equals(activePlayer.getId())) {
            // Disconnecting player is the active player, force-end their turn
            forceEndTurn();
        }
    }

    /** Find a player object by player ID */
    private Player findPlayer(String playerId) {
        if (playerId == null || playerId.isEmpty()) return null;
        return players.stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Record an action history entry.
     * New records are inserted at the head (newest first), capped at 100 entries.
     */
    private void recordAction(String playerId, String nickname, String action,
                              String targetPlayer, int amount, String details) {
        ActionRecord record = new ActionRecord(
                actionHistory.size() + 1, playerId, nickname, action,
                targetPlayer, amount, details, System.currentTimeMillis());
        actionHistory.add(0, record);  // Head-insert: newest first
        if (actionHistory.size() > 100) {
            actionHistory.remove(actionHistory.size() - 1);  // Remove oldest entry
        }
    }

    /**
     * Broadcast game state to all players.
     * Each player receives a customized GameState (their own hand cards visible, others see only counts).
     */
    private void broadcastGameState() {
        for (Player viewer : players) {
            GameState gameState = createGameState(viewer.getId());
            String stateJson = gson.toJson(gameState);
            room.sendToPlayer(viewer.getId(), MessageProtocol.MessageType.GAME_STATE_UPDATE, stateJson);
        }
    }

    /**
     * Create a GameState snapshot for the specified viewer.
     * Privacy protection: only the viewer's own hand card details (handCards) are populated;
     * other players only see hand counts.
     *
     * @param viewerId viewer's player ID
     * @return customized GameState
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

        // Populate state snapshot for each player
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

            // Bank denomination distribution
            Map<Integer, Integer> denominations = new HashMap<>();
            for (int denom : GameConstants.MONEY_DENOMINATIONS) {
                int count = player.getBank().getCount(denom);
                if (count > 0) denominations.put(denom, count);
            }
            playerState.setBankDenominations(denominations);

            // Property counts per color
            Map<String, Integer> colorCounts = new HashMap<>();
            for (Map.Entry<CardColor, List<Card>> entry :
                    player.getPropertyZone().getAllPropertyGroups().entrySet()) {
                if (!entry.getValue().isEmpty())
                    colorCounts.put(entry.getKey().name(), entry.getValue().size());
            }
            playerState.setPropertyColorCounts(colorCounts);

            // Property zone details — public info, all players see these
            List<CardColor> completeSetColors = player.getPropertyZone().getCompleteSets();
            List<GameState.CardInfo> propertyCards = new ArrayList<>();
            for (List<Card> group : player.getPropertyZone().getAllPropertyGroups().values()) {
                for (Card card : group) {
                    GameState.CardInfo ci = new GameState.CardInfo(card);
                    ci.setInCompleteSet(completeSetColors.contains(card.getEffectiveColor()));
                    propertyCards.add(ci);
                }
            }
            playerState.setPropertyCards(propertyCards);

            // Complete set colors
            List<String> completeColorNames = new ArrayList<>();
            for (CardColor c : completeSetColors) {
                completeColorNames.add(c.name());
            }
            playerState.setCompleteSetColors(completeColorNames);

            // House/hotel data
            Map<String, Boolean> houseMap = new HashMap<>();
            Map<String, Boolean> hotelMap = new HashMap<>();
            for (CardColor color : CardColor.values()) {
                if (color.isPropertyColor()) {
                    int hc = player.getPropertyZone().getHouseCount(color);
                    if (hc > 0) houseMap.put(color.name(), true);
                    if (player.getPropertyZone().hasHotel(color)) hotelMap.put(color.name(), true);
                }
            }
            playerState.setHouseColors(houseMap);
            playerState.setHotelColors(hotelMap);

            // Privacy: only the viewer sees their own hand card details
            if (player.getId().equals(viewerId)) {
                List<GameState.CardInfo> handCards = new ArrayList<>();
                for (Card card : player.getHand()) {
                    handCards.add(new GameState.CardInfo(card));
                }
                playerState.setHandCards(handCards);
            }
            state.addPlayerState(player.getId(), playerState);
        }

        // Populate recent action history (capped at 20 entries)
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

    /** Send an error message to the specified player */
    private void sendError(String playerId, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("message", message);
        error.addProperty("timestamp", System.currentTimeMillis());
        room.sendToPlayer(playerId, MessageProtocol.MessageType.ERROR, error.toString());
    }

    /** Calculate game duration (format: MM:SS) */
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
     * Inner class: action history record (for internal storage within GameSession).
     * Separate from GameState.ActionRecord to avoid package dependency confusion.
     */
    static class ActionRecord {
        int index;              // Action sequence number
        String playerId;        // Actor's player ID
        String playerNickname;  // Actor's nickname
        String action;          // Action type
        String targetPlayer;    // Target player
        int amount;             // Amount involved
        String details;         // Detailed description
        long timestamp;         // Timestamp

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
