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
 * Game session - manages the game logic for a complete game
 *
 * This is the core engine of the game, responsible for:
 * 1. Turn management: cycle through active players, manage complete turn lifecycle (draw -> play -> end)
 * 2. Timer: 30-second timeout per turn (warning 10 seconds before timeout)
 * 3. Game rules: execute all card effects (money, property, rent, action cards)
 * 4. Payment system: handle money transfers between players using optimal payment calculation
 * 5. Win condition: check after each turn if any player has collected 3 complete property sets
 * 6. State broadcast: broadcast the latest GameState to all players after each action
 *
 * Turn lifecycle:
 * 1. startNextTurn() - check win condition -> switch to next online player -> auto-draw -> enter PLAY phase -> start timer
 * 2. handlePlayCard() - player plays cards (max 3 times), check remaining plays after each
 * 3. endTurn() / forceEndTurn() - voluntary or forced turn end -> auto-discard to 7 card limit -> start next turn after 1.5s delay
 */
public class GameSession {
    /** Associated game room */
    private final GameRoom room;
    /** Player list (in join order) */
    private final List<Player> players;
    /** Deck (draw pile + discard pile) */
    private final Deck deck;
    /** Current player index in list */
    private int currentPlayerIndex;
    /** Current active player (the one taking the turn) */
    private Player activePlayer;
    /** Current game phase */
    private GamePhase phase;
    /** Timer thread pool (for turn timeout control) */
    private ScheduledExecutorService scheduler;
    /** Current turn timer task handle (for cancelling timer) */
    private ScheduledFuture<?> turnTimer;
    /** Whether game is running */
    private boolean gameRunning;
    /** Action history list (newest first) */
    private final List<ActionRecord> actionHistory;
    /** Pending payment request: debtor ID */
    private String pendingPaymentDebtorId;
    /** Pending payment request: creditor ID */
    private String pendingPaymentCreditorId;
    /** Pending payment request: amount */
    private int pendingPaymentAmount;
    /** Pending payment queue (FIFO order for multiple debtors): [debtorId, creditorId, amount] */
    private final Queue<String[]> pendingPaymentQueue = new LinkedList<>();
    /** Payment timeout timer handle (to cancel timeout when player manually submits payment) */
    private ScheduledFuture<?> paymentTimeoutTask;
    /** Resolution stack - pending action/Just Say No chain, top is the element awaiting response */
    private final Deque<ResolutionItem> resolutionStack = new ArrayDeque<>();
    /** Reaction timeout timer handle (for cancellation) */
    private ScheduledFuture<?> reactionTimeoutTask;
    /** Pending multi-target resolution (continue processing next target after current payment completes) */
    private ResolutionItem pendingMultiTargetResolution;
    /** Game start timestamp (milliseconds) */
    private long gameStartTime;
    /** Gson serializer */
    private final Gson gson;

    /**
     * Constructor
     * @param room associated game room
     * @param players player list
     */
    public GameSession(GameRoom room, List<Player> players) {
        this.room = room;
        this.players = new CopyOnWriteArrayList<>(players);  // Thread-safe list
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
     * Start the game
     * 1. Deal initial hand (5 cards) to each player
     * 2. Broadcast initial game state
     * 3. Start first turn
     */
    public void start() {
        gameRunning = true;
        gameStartTime = System.currentTimeMillis();
        // Deal initial hand
        for (Player player : players) {
            List<Card> initialHand = deck.drawMultiple(GameConstants.INITIAL_HAND_SIZE);
            initialHand.forEach(player::addCardToHand);
        }
        broadcastGameState();
        startNextTurn();  // Start first turn
    }

    /**
     * Start next turn
     * 1. Check win condition
     * 2. Find next online player
     * 3. Auto-draw 3 cards
     * 4. Enter PLAY phase
     * 5. Start 30-second timeout timer
     */
    private void startNextTurn() {
        if (!gameRunning) return;

        // Check if any player has won (collected 3 complete property sets)
        Optional<Player> winner = checkWinner();
        if (winner.isPresent()) {
            endGame(winner.get());
            return;
        }

        // Find next online player (skip disconnected players)
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            activePlayer = players.get(currentPlayerIndex);
        } while (!activePlayer.isConnected() && gameRunning);

        if (!gameRunning) return;

        // Set active player state
        activePlayer.setActivePlayer(true);
        activePlayer.resetTurnState();  // Reset play count and double rent status
        phase = GamePhase.DRAW;

        // Auto-draw phase: draw 5 cards if hand is empty (according to official rules)
        int baseDraw = activePlayer.getHandCount() == 0
                ? GameConstants.EMPTY_HAND_DRAW_COUNT
                : GameConstants.DRAW_COUNT;
        int drawCount = baseDraw;
        // drawMultiple() will automatically trigger reshuffleDiscardPile() when draw pile is empty
        List<Card> drawnCards = deck.drawMultiple(drawCount);
        drawnCards.forEach(activePlayer::addCardToHand);
        recordAction(activePlayer.getId(), activePlayer.getNickname(), "DRAW", "", 0,
                "Drew " + drawnCards.size() + " cards");

        phase = GamePhase.PLAY;
        broadcastGameState();
        startTurnTimer();  // Start 30-second turn timer
    }

    /**
     * Start turn timeout timer
     * Warns 10 seconds before 30-second timeout, then auto-forces end turn after timeout
     */
    private void startTurnTimer() {
        cancelTimer();
        // Warn after 20 seconds, force end after another 10 seconds
        turnTimer = scheduler.schedule(() -> {
            room.sendToPlayer(activePlayer.getId(), MessageProtocol.MessageType.TURN_TIMEOUT,
                    "{\"secondsRemaining\":" + GameConstants.TIMEOUT_WARNING_SECONDS + "}");
            scheduler.schedule(() -> {
                room.broadcast(MessageProtocol.MessageType.TURN_TIMEOUT,
                        "{\"playerId\":\"" + activePlayer.getId() + "\",\"reason\":\"Turn timeout\"}");
                forceEndTurn();
            }, GameConstants.TIMEOUT_WARNING_SECONDS, TimeUnit.SECONDS);
        }, GameConstants.TURN_TIMEOUT_SECONDS - GameConstants.TIMEOUT_WARNING_SECONDS, TimeUnit.SECONDS);
    }

    /** Cancel current turn timer */
    private void cancelTimer() {
        if (turnTimer != null && !turnTimer.isCancelled()) {
            turnTimer.cancel(false);
        }
    }

    /**
     * Handle play card request - validate permissions then dispatch to appropriate card handler
     *
     * Supported action types:
     * - PLAY_MONEY: Deposit money card to bank
     * - PLAY_PROPERTY: Place property card in property zone
     * - PLAY_RENT: Use rent card to collect money
     * - PLAY_ACTION: Use action card to execute special effect
     *
     * @param playerId ID of player making request
     * @param payload JSON object containing cardId, action, and extra parameters (color selection, target player, etc.)
     */
    public void handlePlayCard(String playerId, JsonObject payload) {
        // Permission validation: game must be running, must be active player, must be in PLAY phase
        if (!gameRunning || activePlayer == null) return;
        if (!playerId.equals(activePlayer.getId())) return;
        if (phase != GamePhase.PLAY) {
            sendError(playerId, "Cannot play cards in current phase");
            return;
        }
        if (!activePlayer.canPlay()) {
            sendError(playerId, "No plays remaining this turn");
            return;
        }

        String cardId = payload.get("cardId").getAsString();
        String action = payload.get("action").getAsString();
        Card card = activePlayer.findCardById(cardId);

        if (card == null) {
            sendError(playerId, "Card not found in hand");
            return;
        }

        try {
            boolean played = false;
            switch (action) {
                case "PLAY_MONEY":
                    played = playMoneyCard(card);       // Deposit to bank
                    break;
                case "PLAY_PROPERTY":
                    played = playPropertyCard(card, payload); // Place property
                    break;
                case "PLAY_RENT":
                    played = playRentCard(card, payload);    // Collect rent
                    break;
                case "PLAY_ACTION":
                    played = playActionCard(card, payload);  // Execute action
                    break;
                default:
                    sendError(playerId, "Unknown action: " + action);
                    return;
            }

            if (played) {
                activePlayer.incrementPlaysUsed();  // Increment play count
                recordAction(playerId, activePlayer.getNickname(), action, "", 0, card.getName());
                broadcastGameState();

                // Only auto-end turn if still in PLAY phase (if in WAITING_FOR_PAYMENT or
                // WAITING_FOR_REACTION, let the completion callback handle it)
                if (phase == GamePhase.PLAY && activePlayer.getRemainingPlays() <= 0) {
                    scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Exception e) {
            sendError(playerId, e.getMessage());
        }
    }

    /**
     * Play card as money - remove from hand and deposit to bank
     * Any card with canBeUsedAsMoney() can be deposited (including action/rent cards),
     * after deposit it only serves as money asset, losing original effect.
     * @param card card with value > 0
     * @return true on success
     */
    private boolean playMoneyCard(Card card) {
        if (!card.canBeUsedAsMoney()) return false;
        activePlayer.removeCardFromHand(card);
        activePlayer.getBank().deposit(card);
        return true;
    }

    /**
     * Execute property card effect - place property card in property zone
     * For wild property cards, supports player color selection
     * Checks win condition (3 complete property sets) after placement
     *
     * @param card property card
     * @param payload may contain color field (color selection for wild property)
     * @return true on success
     */
    private boolean playPropertyCard(Card card, JsonObject payload) {
        if (!card.isPropertyCard()) return false;
        activePlayer.removeCardFromHand(card);

        // Wild property card: set player-selected color
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
     * Execute rent card effect - collect rent from one or more players
     *
     * Rent calculation logic:
     * 1. Determine rent color (normal rent card has fixed dual colors, wild rent requires color selection)
     * 2. Calculate base rent = base rate based on number held of that color
     * 3. If double rent active, amount × 2
     * 4. Dual-color rent collects from all players, wild rent collects from single specified target
     *
     * @param card rent card
     * @param payload may contain color and targetPlayerId fields
     * @return true on success
     */
    private boolean playRentCard(Card card, JsonObject payload) {
        if (!card.isRentCard()) return false;

        // Step 1: Remove from hand and discard
        activePlayer.removeCardFromHand(card);
        deck.discard(card);

        // Step 2: Determine rent color
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
        payload.addProperty("isWildRent", isWildRent);

        // Step 3: Pre-calculate rent amount
        int baseRentAmount;
        if (isWildRent && rentColor == CardColor.WILD) {
            // Wild rent without color selected: default 2M
            baseRentAmount = 2;
        } else {
            // Dual-color rent card: calculate for both component colors, take the larger
            CardColor[] components = card.getColor().getComponentColors();
            if (components.length == 2) {
                int rent1 = activePlayer.getPropertyZone().getRentAmount(components[0]);
                int rent2 = activePlayer.getPropertyZone().getRentAmount(components[1]);
                baseRentAmount = Math.max(rent1, rent2);
                // Choose the higher-yielding color as the official rent color
                rentColor = (rent1 >= rent2) ? components[0] : components[1];
            } else {
                baseRentAmount = activePlayer.getPropertyZone().getRentAmount(rentColor);
            }
            if (baseRentAmount == 0) baseRentAmount = 2;
        }
        payload.addProperty("color", rentColor.name());

        int rentAmount = activePlayer.isDoubleRentActive() ? baseRentAmount * 2 : baseRentAmount;
        payload.addProperty("_preCalculatedRent", rentAmount);

        // Step 4: Collect all target player list
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

        // Step 5: First target as current responder, remaining go into _remainingTargets
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
     * Execute action card effect - dispatch to specific action effect based on card name
     *
     * Supported action card types:
     * - Debt Collector: Collect 5M from specified player
     * - Birthday: Each other player pays 2M
     * - Deal Breaker: Steal a complete property set from a player
     * - Pass Go: Draw 2 extra cards
     * - Double Rent: Double next rent collection
     * - House: Build a house on complete set (+1 rent per house, max 4)
     * - Hotel: Upgrade to hotel on 4 houses (+3 rent)
     * - Forced Deal: Exchange property card with another player
     * - Sly Deal: Steal a single property card from another player
     * - Just Say No: Cancel an action card targeting you
     *
     * @param card action card
     * @param payload may contain targetPlayerId, color, etc.
     * @return true on success
     */
    private boolean playActionCard(Card card, JsonObject payload) {
        if (!card.isActionCard()) return false;

        String actionName = card.getName();

        // Just Say No cannot be played via PLAY_ACTION — it must be played through
        // handlePlayJustSayNo() during WAITING_FOR_REACTION phase when targeted by an action.
        // If sent through PLAY_ACTION, send an error and do not consume the card.
        if (actionName.contains("Just Say No")) {
            // Card is still in hand — reject without consuming
            sendError(activePlayer.getId(),
                    "Just Say No can only be used when an action card is played against you");
            return false;
        }

        // === Step 1: Remove from hand and discard (card is played regardless of cancellation) ===
        activePlayer.removeCardFromHand(card);
        deck.discard(card);

        // === Step 2: No-target actions — execute immediately ===
        if (actionName.contains("Pass Go")) {
            List<Card> extraCards = deck.drawMultiple(2);
            extraCards.forEach(activePlayer::addCardToHand);
            return true;
        }

        if (actionName.contains("Double")) {
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
                activePlayer.getPropertyZone().addHotel(hotelColor);
                return true;
            }
            return false;
        }

        // === Step 3: Target actions — push to resolution stack for deferred execution ===
        String actionType = mapActionNameToType(actionName);
        String targetId = extractTargetId(payload);

        // Determine target player
        if (targetId.isEmpty()) {
            if (actionName.contains("Deal Breaker")) {
                for (Player p : players) {
                    if (!p.equals(activePlayer) && p.getCompleteSetsCount() > 0) {
                        targetId = p.getId();
                        break;
                    }
                }
            } else if (actionName.contains("Forced Deal")) {
                // Auto-select: first other player with property, and auto-select swap cards
                Player target = null;
                for (Player p : players) {
                    if (!p.equals(activePlayer) && !p.getPropertyZone().getAllPropertyGroups().isEmpty()) {
                        target = p;
                        break;
                    }
                }
                if (target != null) {
                    targetId = target.getId();
                    Card myProp = null;
                    for (List<Card> group : activePlayer.getPropertyZone().getAllPropertyGroups().values()) {
                        if (!group.isEmpty()) { myProp = group.get(0); break; }
                    }
                    Card theirProp = null;
                    for (List<Card> group : target.getPropertyZone().getAllPropertyGroups().values()) {
                        if (!group.isEmpty()) { theirProp = group.get(0); break; }
                    }
                    if (myProp != null) payload.addProperty("myPropertyId", myProp.getId());
                    if (theirProp != null) payload.addProperty("theirPropertyId", theirProp.getId());
                }
            } else if (actionName.contains("Sly Deal")) {
                // Only steal property cards not in complete sets (per official rules)
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
                // No legal target (all other players' properties are in complete sets)
                if (victim == null || toSteal == null) return false;
                targetId = victim.getId();
                payload.addProperty("targetCardId", toSteal.getId());
            } else if (actionName.contains("Birthday")) {
                // Birthday: collect all other players, first as responder, rest into _remainingTargets
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
                // Default: select first other player
                for (Player p : players) {
                    if (!p.equals(activePlayer)) {
                        targetId = p.getId();
                        break;
                    }
                }
            }
        }

        if (targetId.isEmpty()) return false;

        // Ensure payload contains targetPlayerId
        if (!payload.has("targetPlayerId")) {
            payload.addProperty("targetPlayerId", targetId);
        }

        // Push to resolution stack
        pushResolution(actionType, activePlayer.getId(), targetId, card, payload);

        return true;
    }

    /**
     * Execute forced deal: swap specified property cards between two players
     * @param player1 first player
     * @param player2 second player
     * @param cardId1 property card ID to swap out from player1
     * @param cardId2 property card ID to swap out from player2
     */
    private void executeForcedDeal(Player player1, Player player2, String cardId1, String cardId2) {
        Card card1 = findPropertyInZone(player1, cardId1);
        Card card2 = findPropertyInZone(player2, cardId2);
        if (card1 == null || card2 == null) return;

        // Remove from respective property zones
        player1.getPropertyZone().removeProperty(card1);
        player2.getPropertyZone().removeProperty(card2);

        // Reset wild property colors
        if (card1.isWildProperty()) card1.setWildColor(null);
        if (card2.isWildProperty()) card2.setWildColor(null);

        // Swap into opponent's property zone
        player1.getPropertyZone().addProperty(card2);
        player2.getPropertyZone().addProperty(card1);

        recordAction(player1.getId(), player1.getNickname(), "FORCED_DEAL",
                player2.getNickname(), 0, card1.getName() + " <-> " + card2.getName());
    }

    /**
     * Execute sly deal: steal a specified property card from a player
     * @param thief stealing player
     * @param victim victim player
     * @param cardId property card ID to steal
     */
    private void executeSlyDeal(Player thief, Player victim, String cardId) {
        Card stolenCard = findPropertyInZone(victim, cardId);
        if (stolenCard == null) return;

        victim.getPropertyZone().removeProperty(stolenCard);
        if (stolenCard.isWildProperty()) stolenCard.setWildColor(null);  // Reset wild color
        thief.getPropertyZone().addProperty(stolenCard);

        recordAction(thief.getId(), thief.getNickname(), "SLY_DEAL",
                victim.getNickname(), 0, "Stole " + stolenCard.getName());
    }

    /** Find property card in player's property zone by ID */
    private Card findPropertyInZone(Player player, String cardId) {
        for (List<Card> properties : player.getPropertyZone().getAllPropertyGroups().values()) {
            for (Card card : properties) {
                if (card.getId().equals(cardId)) return card;
            }
        }
        return null;
    }

    // ==================== Resolution Stack Core Methods ====================

    /**
     * Push action to resolution stack, enter WAITING_FOR_REACTION phase
     *
     * @param actionType   action type
     * @param initiatorId  initiator ID
     * @param responderId  responder ID
     * @param sourceCard   card played
     * @param actionPayload original request data
     */
    private void pushResolution(String actionType, String initiatorId,
                                String responderId, Card sourceCard,
                                JsonObject actionPayload) {
        String resolutionId = UUID.randomUUID().toString().substring(0, 8);
        ResolutionItem item = new ResolutionItem(resolutionId, actionType,
                initiatorId, responderId, sourceCard, actionPayload);
        resolutionStack.push(item);

        // Enter waiting for reaction phase, pause turn
        phase = GamePhase.WAITING_FOR_REACTION;
        cancelTimer();

        // Notify responder
        sendReactionRequired(responderId, item);

        // Start 5-second reaction timeout
        startReactionTimeout(responderId);
    }

    /** Send REACTION_REQUIRED message to responder */
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

    /** Start reaction timeout timer */
    private void startReactionTimeout(String responderId) {
        cancelReactionTimeout();
        reactionTimeoutTask = scheduler.schedule(
                () -> handleReactionTimeout(responderId),
                GameConstants.JUST_SAY_NO_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /** Cancel reaction timeout timer */
    private void cancelReactionTimeout() {
        if (reactionTimeoutTask != null && !reactionTimeoutTask.isCancelled()) {
            reactionTimeoutTask.cancel(false);
        }
    }

    /** Reaction timeout — treat as pass, auto resolveTop */
    private void handleReactionTimeout(String responderId) {
        if (resolutionStack.isEmpty()) return;
        ResolutionItem top = resolutionStack.peek();
        if (top == null || !top.getResponderId().equals(responderId)) return;

        // Timeout treated as pass
        recordAction(responderId, findPlayer(responderId) != null ?
                        findPlayer(responderId).getNickname() : "",
                "PASS_REACTION", "", 0, "Timeout - no response");

        resolveTopResolution();
    }

    /** Handle player playing Just Say No */
    public void handlePlayJustSayNo(String playerId, JsonObject payload) {
        if (phase != GamePhase.WAITING_FOR_REACTION) {
            sendError(playerId, "Cannot play Just Say No in current phase");
            return;
        }
        if (resolutionStack.isEmpty()) {
            sendError(playerId, "No pending action to respond to");
            return;
        }

        ResolutionItem currentTop = resolutionStack.peek();
        // Validate: must be the current responder taking action
        if (!playerId.equals(currentTop.getResponderId())) {
            sendError(playerId, "Your response is not required at this time");
            return;
        }

        // Find Just Say No card in hand
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

        // Push Just Say No to stack, responder becomes original initiator
        String newResponderId = currentTop.getInitiatorId();
        JsonObject jsnPayload = new JsonObject();
        jsnPayload.addProperty("counteredResolutionId", currentTop.getResolutionId());

        pushResolution("JUST_SAY_NO", playerId, newResponderId, jsnCard, jsnPayload);

        recordAction(playerId, responder.getNickname(), "JUST_SAY_NO",
                findPlayer(newResponderId) != null ?
                        findPlayer(newResponderId).getNickname() : "",
                0, "Played Just Say No to cancel " + currentTop.getActionType());
        broadcastGameState();
    }

    /** Handle player passing reaction (not playing Just Say No) */
    public void handlePassReaction(String playerId) {
        if (resolutionStack.isEmpty()) {
            sendError(playerId, "No pending action to respond to");
            return;
        }

        ResolutionItem top = resolutionStack.peek();
        if (!playerId.equals(top.getResponderId())) {
            sendError(playerId, "Your response is not required at this time");
            return;
        }

        cancelReactionTimeout();
        recordAction(playerId, findPlayer(playerId) != null ?
                        findPlayer(playerId).getNickname() : "",
                "PASS_REACTION", "", 0, "Passed response to " + top.getActionType());

        resolveTopResolution();
    }

    /**
     * Pop top resolution and process
     *
     * Pop rules:
     * - If top is JUST_SAY_NO → it succeeded → pop the element below it (cancelled)
     * - If top is original action → not cancelled → execute deferred effect
     * - If stack not empty after processing → continue waiting for next responder
     * - If stack empty → return to PLAY or enter WAITING_FOR_PAYMENT
     */
    private void resolveTopResolution() {
        if (resolutionStack.isEmpty()) return;

        ResolutionItem resolved = resolutionStack.pop();

        if (resolved.isJustSayNo()) {
            // Just Say No succeeded → cancel the resolution it blocked
            if (!resolutionStack.isEmpty()) {
                ResolutionItem cancelled = resolutionStack.pop();
                recordAction(resolved.getInitiatorId(),
                        findPlayer(resolved.getInitiatorId()) != null ?
                                findPlayer(resolved.getInitiatorId()).getNickname() : "",
                        "ACTION_CANCELLED", "",
                        0, cancelled.getActionType() + " was cancelled by Just Say No");
                // Official rule: "Just Say No — Cancel the action."
                // A Just Say No cancels the ENTIRE action card, not just the current target's obligation.
                // Clear remaining multi-targets so the action doesn't continue to other players.
                clearRemainingTargets(cancelled);
                // Do NOT call continueMultiTargetResolution — the action is fully cancelled.
            }
        } else {
            // Original action not cancelled, execute deferred effect
            executeDeferredAction(resolved);
            // Multi-target: temporarily store resolution, wait for current target payment to complete before proceeding
            if (hasRemainingTargets(resolved)) {
                pendingMultiTargetResolution = resolved;
            }
        }

        // Stack not empty → continue waiting for next responder
        if (!resolutionStack.isEmpty()) {
            ResolutionItem nextTop = resolutionStack.peek();
            sendReactionRequired(nextTop.getResponderId(), nextTop);
            startReactionTimeout(nextTop.getResponderId());
            return;
        }

        // Stack empty → resolution phase complete
        if (pendingPaymentDebtorId != null) {
            broadcastGameState();
            return;
        }

        // No pending payment → return to play phase
        phase = GamePhase.PLAY;
        broadcastGameState();

        if (activePlayer != null && activePlayer.getRemainingPlays() <= 0) {
            scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
        }
    }

    /** Check if there are remaining multi-targets to process */
    private boolean hasRemainingTargets(ResolutionItem item) {
        JsonObject payload = item.getActionPayload();
        if (!payload.has("_remainingTargets")) return false;
        com.google.gson.JsonArray remaining = payload.getAsJsonArray("_remainingTargets");
        return remaining != null && remaining.size() > 0;
    }

    /**
     * Multi-target action helper method — take next target from unprocessed list and push to resolution stack
     * If _remainingTargets doesn't exist or is empty, do nothing
     */
    private boolean continueMultiTargetResolution(ResolutionItem resolvedItem) {
        JsonObject payload = resolvedItem.getActionPayload();
        if (!payload.has("_remainingTargets")) return false;

        com.google.gson.JsonArray remaining = payload.getAsJsonArray("_remainingTargets");
        if (remaining == null || remaining.size() == 0) return false;

        // Get next target player ID
        String nextTarget = remaining.remove(0).getAsString();
        payload.addProperty("targetPlayerId", nextTarget);

        // Remove marker field if no remaining targets
        if (remaining.size() == 0) {
            payload.remove("_remainingTargets");
        }

        // Push new resolution, next target becomes new responder
        pushResolution(resolvedItem.getActionType(),
                resolvedItem.getInitiatorId(),
                nextTarget,
                resolvedItem.getSourceCard(),
                payload);
        return true;
    }

    /**
     * Clear remaining multi-targets when an action is fully cancelled by Just Say No.
     * Official rule: "Just Say No — Cancel the action" means the ENTIRE action is cancelled,
     * not just the current target's obligation. Remaining targets are cleared so the action
     * does not continue after the JSN chain resolves.
     */
    private void clearRemainingTargets(ResolutionItem item) {
        JsonObject payload = item.getActionPayload();
        if (payload != null && payload.has("_remainingTargets")) {
            payload.remove("_remainingTargets");
        }
    }

    /**
     * Execute deferred action effect (called after resolution passes)
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

    // ==================== Deferred Action Execution ====================

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
                    "Collected " + GameConstants.DEBT_COLLECTOR_AMOUNT + "M");
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
                    target.getNickname() + " paid " + amount + "M");
        }
    }

    private void executeRent(Player initiator, JsonObject payload) {
        // Use pre-calculated rent amount from playRentCard (calculate on the fly if missing)
        int rentAmount;
        if (payload.has("_preCalculatedRent")) {
            rentAmount = payload.get("_preCalculatedRent").getAsInt();
        } else {
            // Fallback calculation (same logic as playRentCard)
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
                // Dual-color rent card: take max of both component colors
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
        initiator.setDoubleRentActive(false);

        // Single target collection (multi-target scenario each player handled by separate resolution element)
        String targetPlayerId = payload.has("targetPlayerId")
                ? payload.get("targetPlayerId").getAsString() : "";
        Player targetPlayer = findPlayer(targetPlayerId);
        if (targetPlayer != null) {
            requirePayment(targetPlayer, initiator, rentAmount);
            recordAction(initiator.getId(), initiator.getNickname(), "RENT",
                    targetPlayer.getNickname(), rentAmount,
                    "Collected rent of " + rentAmount + "M from " + targetPlayer.getNickname());
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

    /** Steal complete property set (Deal Breaker effect) */
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
                "Stole complete set: " + setToSteal.getName());
        // Check if this wins the game
        if (initiator.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
            endGame(initiator);
        }
    }

    /** Steal single property card (Sly Deal effect - deferred execution version) */
    private void executeSlyDeal(Player initiator, JsonObject payload) {
        if (!payload.has("targetPlayerId") || !payload.has("targetCardId")) return;
        String targetPlayerId = payload.get("targetPlayerId").getAsString();
        String targetCardId = payload.get("targetCardId").getAsString();
        Player target = findPlayer(targetPlayerId);
        if (target != null && targetCardId != null) {
            Card stolenCard = findPropertyInZone(target, targetCardId);
            if (stolenCard == null) return;
            // Cannot steal property cards from complete sets
            if (target.getPropertyZone().getCompleteSets()
                    .contains(stolenCard.getEffectiveColor())) return;
            target.getPropertyZone().removeProperty(stolenCard);
            if (stolenCard.isWildProperty()) stolenCard.setWildColor(null);
            initiator.getPropertyZone().addProperty(stolenCard);
            recordAction(initiator.getId(), initiator.getNickname(), "SLY_DEAL",
                    target.getNickname(), 0, "Stole " + stolenCard.getName());
        }
    }

    /** Forced exchange (Forced Deal effect - deferred execution version) */
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

    // ==================== Helper Methods ====================

    /** Map card name to standard action type string */
    private String mapActionNameToType(String actionName) {
        if (actionName.contains("Debt Collector")) return "DEBT_COLLECTOR";
        if (actionName.contains("Birthday")) return "BIRTHDAY";
        if (actionName.contains("Deal Breaker")) return "DEAL_BREAKER";
        if (actionName.contains("Sly Deal")) return "SLY_DEAL";
        if (actionName.contains("Forced Deal")) return "FORCED_DEAL";
        if (actionName.contains("Rent") || actionName.contains("rent")) return "RENT";
        System.err.println("Warning: Unrecognized action card name '" + actionName + "', treating as UNKNOWN");
        return "UNKNOWN";
    }

    /** Extract target player ID from payload */
    private String extractTargetId(JsonObject payload) {
        if (payload.has("targetPlayerId")) {
            String id = payload.get("targetPlayerId").getAsString();
            if (id != null && !id.isEmpty()) return id;
        }
        return "";
    }

    // ==================== Payment System ====================

    /**
     * Initiate asynchronous payment request — send PAYMENT_REQUIRED to debtor client
     *
     * Payment is no longer synchronous. Debtor selects cards on client side after receiving message,
     * submits selection via SUBMIT_PAYMENT, and handleSubmitPayment executes the transfer.
     * Multiple debtors are processed via FIFO queue.
     *
     * @param debtor   paying player
     * @param creditor receiving player
     * @param amount   payment amount
     */
    private void requirePayment(Player debtor, Player creditor, int amount) {
        if (debtor.getBank().getTotal() == 0) return;

        int actualAmount = Math.min(amount, debtor.getBank().getTotal());

        if (pendingPaymentDebtorId != null) {
            pendingPaymentQueue.add(new String[]{
                    debtor.getId(), creditor.getId(), String.valueOf(actualAmount)});
            return;
        }

        sendPaymentRequest(debtor, creditor, actualAmount);
    }

    /** Send payment request message to specified debtor */
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

        // Pause turn: enter payment waiting phase, cancel turn timer to prevent active player timeout
        phase = GamePhase.WAITING_FOR_PAYMENT;
        cancelTimer();

        // 30-second timeout fallback
        final int capturedAmount = amount;
        this.paymentTimeoutTask = scheduler.schedule(
                () -> handlePaymentTimeout(debtor, creditor, capturedAmount),
                30, TimeUnit.SECONDS);
    }

    /** Payment timeout fallback — automatically select cards for payment using greedy algorithm */
    private void handlePaymentTimeout(Player debtor, Player creditor, int expectedAmount) {
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
                creditor.getBank().deposit(moneyCard.clone());
            }
            recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_TIMEOUT",
                    creditor.getNickname(), actualPaid,
                    "Auto-paid " + actualPaid + "M due to timeout");
        } catch (Bank.InsufficientFundsException ignored) {}

        clearPendingPayment();
        broadcastGameState();
    }

    /**
     * Handle player-submitted payment selection — routed by ClientHandler
     * Debtor selects cards to pay, then validation and transfer are executed
     */
    public void handleSubmitPayment(String playerId, JsonObject payload) {
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
                creditor.getBank().deposit(moneyCard.clone());
            }
            recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_MADE",
                    creditor.getNickname(), totalPaid,
                    "Paid " + totalPaid + "M (required " + pendingPaymentAmount + "M)");
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

    /** Clear current pending payment state and dequeue next payment request */
    private void clearPendingPayment() {
        pendingPaymentDebtorId = null;
        pendingPaymentCreditorId = null;
        pendingPaymentAmount = 0;

        if (!pendingPaymentQueue.isEmpty()) {
            // Process next payment in queue (keep WAITING_FOR_PAYMENT phase unchanged)
            String[] next = pendingPaymentQueue.poll();
            Player nextDebtor = findPlayer(next[0]);
            Player nextCreditor = findPlayer(next[1]);
            int nextAmount = Integer.parseInt(next[2]);
            if (nextDebtor != null && nextCreditor != null) {
                sendPaymentRequest(nextDebtor, nextCreditor, nextAmount);
            } else {
                // Next payment invalid (player disconnected, etc.), recursively clean and continue
                clearPendingPayment();
            }
            return;  // Still have pending payment, do not restore PLAY phase
        }

        // All payments completed
        // If there is a pending multi-target resolution, advance to next target
        if (pendingMultiTargetResolution != null) {
            ResolutionItem saved = pendingMultiTargetResolution;
            pendingMultiTargetResolution = null;
            if (continueMultiTargetResolution(saved)) {
                return; // Next target pushed, waiting for JSN response
            }
        }

        // All targets processed, return to play phase
        phase = GamePhase.PLAY;
        broadcastGameState();

        // If active player has no plays remaining, automatically end turn
        if (activePlayer != null && activePlayer.getRemainingPlays() <= 0) {
            scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
        }
    }

    /** Handle player voluntarily ending turn */
    public void endTurn(String playerId) {
        if (activePlayer == null || !playerId.equals(activePlayer.getId())) return;

        // Cannot end turn while waiting for payment or reaction
        if (phase == GamePhase.WAITING_FOR_PAYMENT || phase == GamePhase.WAITING_FOR_REACTION) {
            sendError(playerId, "Please wait for current operation to complete before ending turn");
            return;
        }

        forceEndTurn();
    }

    /**
     * Force settle all pending payments (called on turn timeout/player disconnect)
     * Use fallback greedy algorithm to auto-settle current and queued payments
     */
    private void forceSettleAllPendingPayments() {
        // Settle current pending payment first
        if (pendingPaymentDebtorId != null) {
            Player debtor = findPlayer(pendingPaymentDebtorId);
            Player creditor = findPlayer(pendingPaymentCreditorId);
            if (debtor != null && creditor != null && debtor.getBank().getTotal() > 0) {
                try {
                    int actualAmount = Math.min(pendingPaymentAmount, debtor.getBank().getTotal());
                    List<Card> payment = debtor.getBank().removeCardsFallback(actualAmount);
                    for (Card c : payment) {
                        creditor.getBank().deposit(c.clone());
                    }
                    recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_TIMEOUT",
                            creditor.getNickname(),
                            payment.stream().mapToInt(Card::getValue).sum(),
                            "Auto-paid " + actualAmount + "M at turn end");
                } catch (Bank.InsufficientFundsException ignored) {}
            }
        }

        // Settle all remaining payments in queue
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
                        creditor.getBank().deposit(c.clone());
                    }
                    recordAction(debtor.getId(), debtor.getNickname(), "PAYMENT_TIMEOUT",
                            creditor.getNickname(),
                            payment.stream().mapToInt(Card::getValue).sum(),
                            "Auto-paid " + actualAmount + "M at turn end");
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
     * Force end current turn
     * 1. Cancel timer
     * 2. Auto-discard to hand limit (7 cards)
     * 3. Clear active player state
     * 4. Broadcast game state
     * 5. Start next turn after 1.5 second delay
     */
    private void forceEndTurn() {
        cancelTimer();
        cancelReactionTimeout();

        // Clear resolution stack (turn ends, all unresolved resolutions are considered abandoned by target)
        while (!resolutionStack.isEmpty()) {
            ResolutionItem item = resolutionStack.pop();
            if (!item.isJustSayNo()) {
                // Original action abandoned, do not execute deferred effect at turn end (action effect lost as penalty)
                recordAction(item.getInitiatorId(),
                        findPlayer(item.getInitiatorId()) != null ?
                                findPlayer(item.getInitiatorId()).getNickname() : "",
                        "ACTION_EXPIRED", "", 0,
                        item.getActionType() + " expired due to turn end");
            }
        }

        // If there are pending payments, use fallback to force settle all payments
        if (phase == GamePhase.WAITING_FOR_PAYMENT) {
            forceSettleAllPendingPayments();
        }

        if (activePlayer != null) {
            if (activePlayer.needsToDiscard()) {
                phase = GamePhase.DISCARD;
            }
            // Auto-discard: discard from start of hand until hand size <= 7
            while (activePlayer.needsToDiscard() && !activePlayer.getHand().isEmpty()) {
                Card discarded = activePlayer.removeCardFromHand(0);
                deck.discard(discarded);
                recordAction(activePlayer.getId(), activePlayer.getNickname(),
                        "DISCARD", "", 0, "Discarded " + discarded.getName());
            }
            activePlayer.setActivePlayer(false);
            recordAction(activePlayer.getId(), activePlayer.getNickname(),
                    "END_TURN", "", 0, "Turn ended");
        }
        phase = GamePhase.END;
        broadcastGameState();
        // Delay 1.5 seconds before starting next turn (give players time to see results of previous turn)
        scheduler.schedule(this::startNextTurn, 1500, TimeUnit.MILLISECONDS);
    }

    /** Check if any player meets win condition (collected 3 complete property sets) */
    private Optional<Player> checkWinner() {
        return players.stream()
                .filter(p -> p.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS)
                .findFirst();
    }

    /** End game - broadcast GAME_OVER message */
    private void endGame(Player winner) {
        gameRunning = false;
        cancelTimer();
        phase = GamePhase.GAME_OVER;

        JsonObject result = new JsonObject();
        result.addProperty("winnerId", winner.getId());
        result.addProperty("winnerNickname", winner.getNickname());
        result.addProperty("gameDuration", getGameDuration());
        result.addProperty("completeSets", winner.getCompleteSetsCount());

        com.google.gson.JsonArray playersArr = new com.google.gson.JsonArray();
        for (Player p : players) {
            JsonObject pJson = new JsonObject();
            pJson.addProperty("nickname", p.getNickname());
            pJson.addProperty("completeSets", p.getCompleteSetsCount());
            pJson.addProperty("bankTotal", p.getBank().getTotal());
            playersArr.add(pJson);
        }
        result.add("players", playersArr);

        recordAction(winner.getId(), winner.getNickname(), "WINNER", "", 0, "Won the game!");
        broadcastGameState();
        room.broadcast(MessageProtocol.MessageType.GAME_OVER, result.toString());
    }

    /**
     * Flip wild property card color — wild card free color change entry point
     * Validation: must be active player, in PLAY phase, card must be in property zone
     * Core rule: does not consume play count (does not call incrementPlaysUsed)
     */
    public void handleFlipWildCard(String playerId, String cardId, String newColor) {
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
                        "Color change failed: wild card not found, card does not support this color, "
                                + "or existing property set has houses/hotels that cannot be removed");
                return;
            }

            recordAction(playerId, player.getNickname(), "FLIP_WILD", "", 0,
                    "Changed wild property to " + color.getName());
            broadcastGameState();

            if (player.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
                endGame(player);
            }
        } catch (IllegalArgumentException e) {
            sendError(playerId, "Invalid color name: " + newColor);
        }
    }

    /**
     * Handle player disconnect
     * If remaining online players are less than 2, game ends in draw.
     * If disconnected player is the active player, automatically end their turn.
     */
    public void handlePlayerDisconnect(String clientId) {
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
            JsonObject drawResult = new JsonObject();
            drawResult.addProperty("reason", "Insufficient online players");
            drawResult.addProperty("connectedPlayers", connectedPlayers);
            room.broadcast(MessageProtocol.MessageType.GAME_DRAW, drawResult.toString());
        } else if (activePlayer != null && clientId.equals(activePlayer.getId())) {
            // Disconnected player is current active player, force end their turn
            forceEndTurn();
        }
    }

    /** Find player object by player ID */
    private Player findPlayer(String playerId) {
        if (playerId == null || playerId.isEmpty()) return null;
        return players.stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Record an action in history
     * New records inserted at head of list (newest first), keep up to 100 records
     */
    private void recordAction(String playerId, String nickname, String action,
                              String targetPlayer, int amount, String details) {
        ActionRecord record = new ActionRecord(
                actionHistory.size() + 1, playerId, nickname, action,
                targetPlayer, amount, details, System.currentTimeMillis());
        actionHistory.add(0, record);  // Insert at head, newest first
        if (actionHistory.size() > 100) {
            actionHistory.remove(actionHistory.size() - 1);  // Remove oldest record
        }
    }

    /**
     * Broadcast game state to all players
     * Each player receives a customized GameState (their own hand cards visible, others only see count)
     */
    private void broadcastGameState() {
        for (Player viewer : players) {
            GameState gameState = createGameState(viewer.getId());
            String stateJson = gson.toJson(gameState);
            room.sendToPlayer(viewer.getId(), MessageProtocol.MessageType.GAME_STATE_UPDATE, stateJson);
        }
    }

    /**
     * Create GameState snapshot for specified viewer
     * Privacy protection: only viewer's own hand card details (handCards) are populated, others only see hand count
     *
     * @param viewerId viewer ID
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

            // Property count by color
            Map<String, Integer> colorCounts = new HashMap<>();
            for (Map.Entry<CardColor, List<Card>> entry :
                    player.getPropertyZone().getAllPropertyGroups().entrySet()) {
                if (!entry.getValue().isEmpty())
                    colorCounts.put(entry.getKey().name(), entry.getValue().size());
            }
            playerState.setPropertyColorCounts(colorCounts);

            // Privacy protection: only viewer can see their own hand cards
            if (player.getId().equals(viewerId)) {
                List<GameState.CardInfo> handCards = new ArrayList<>();
                for (Card card : player.getHand()) {
                    handCards.add(new GameState.CardInfo(card));
                }
                playerState.setHandCards(handCards);
            }
            state.addPlayerState(player.getId(), playerState);
        }

        // Populate recent action history (up to 20 records)
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

    /** Send error message to specified player */
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
     * Inner class: Action history record (for internal storage in GameSession)
     * Separate from GameState.ActionRecord to avoid package dependency confusion
     */
    static class ActionRecord {
        int index;              // Action sequence number
        String playerId;        // Executor ID
        String playerNickname;  // Executor nickname
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