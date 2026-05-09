package com.monopolydeal.server;

import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.*;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.Gson;

public class GameSession {
    private final GameRoom room;
    private final List<Player> players;
    private final Deck deck;
    private int currentPlayerIndex;
    private Player activePlayer;
    private GamePhase phase;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> turnTimer;
    private boolean gameRunning;
    private final List<ActionRecord> actionHistory;
    private long gameStartTime;
    private final Gson gson;

    public GameSession(GameRoom room, List<Player> players) {
        this.room = room;
        this.players = new CopyOnWriteArrayList<>(players);
        this.deck = new Deck();
        this.currentPlayerIndex = -1;
        this.phase = GamePhase.INIT;
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.gameRunning = false;
        this.actionHistory = new ArrayList<>();
        this.gameStartTime = 0;
        this.gson = new Gson();
    }

    public void start() {
        gameRunning = true;
        gameStartTime = System.currentTimeMillis();
        for (Player player : players) {
            List<Card> initialHand = deck.drawMultiple(GameConstants.INITIAL_HAND_SIZE);
            initialHand.forEach(player::addCardToHand);
        }
        broadcastGameState();
        startNextTurn();
    }

    private void startNextTurn() {
        if (!gameRunning) return;
        Optional<Player> winner = checkWinner();
        if (winner.isPresent()) {
            endGame(winner.get());
            return;
        }
        do {
            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            activePlayer = players.get(currentPlayerIndex);
        } while (!activePlayer.isConnected() && gameRunning);
        if (!gameRunning) return;
        activePlayer.setActivePlayer(true);
        activePlayer.resetTurnState();
        phase = GamePhase.DRAW;
        int drawCount = Math.min(GameConstants.DRAW_COUNT, deck.getDrawPileSize());
        List<Card> drawnCards = deck.drawMultiple(drawCount);
        drawnCards.forEach(activePlayer::addCardToHand);
        recordAction(activePlayer.getId(), activePlayer.getNickname(), "DRAW", "", 0, drawnCards.size() + " cards drawn");
        phase = GamePhase.PLAY;
        broadcastGameState();
        startTurnTimer();
    }

    private void startTurnTimer() {
        cancelTimer();
        turnTimer = scheduler.schedule(() -> {
            JsonObject warning = new JsonObject();
            warning.addProperty("secondsRemaining", GameConstants.TIMEOUT_WARNING_SECONDS);
            warning.addProperty("playerId", activePlayer.getId());
            room.broadcast(MessageProtocol.MessageType.TURN_TIMEOUT, warning.toString());
            scheduler.schedule(() -> {
                JsonObject timeoutMsg = new JsonObject();
                timeoutMsg.addProperty("playerId", activePlayer.getId());
                timeoutMsg.addProperty("reason", "Turn timeout");
                room.broadcast(MessageProtocol.MessageType.TURN_TIMEOUT, timeoutMsg.toString());
                forceEndTurn();
            }, GameConstants.TIMEOUT_WARNING_SECONDS, TimeUnit.SECONDS);
        }, GameConstants.TURN_TIMEOUT_SECONDS - GameConstants.TIMEOUT_WARNING_SECONDS, TimeUnit.SECONDS);
    }

    private void cancelTimer() {
        if (turnTimer != null && !turnTimer.isCancelled()) {
            turnTimer.cancel(false);
        }
    }

    public void handlePlayCard(String playerId, JsonObject payload) {
        if (!gameRunning || activePlayer == null) return;
        if (!playerId.equals(activePlayer.getId())) return;
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
            switch (action) {
                case "PLAY_MONEY":
                    playMoneyCard(card);
                    break;
                case "PLAY_PROPERTY":
                    playPropertyCard(card, payload);
                    break;
                case "PLAY_RENT":
                    playRentCard(card, payload);
                    break;
                case "PLAY_ACTION":
                    playActionCard(card, payload);
                    break;
                default:
                    sendError(playerId, "Unknown action: " + action);
                    return;
            }
            activePlayer.incrementPlaysUsed();
            recordAction(playerId, activePlayer.getNickname(), action, "", 0, card.getName());
            broadcastGameState();
            if (activePlayer.getRemainingPlays() <= 0) {
                scheduler.schedule(this::forceEndTurn, 500, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            sendError(playerId, e.getMessage());
        }
    }

    private void playMoneyCard(Card card) {
        activePlayer.removeCardFromHand(card);
        activePlayer.getBank().deposit(card);
    }

    private void playPropertyCard(Card card, JsonObject payload) {
        activePlayer.removeCardFromHand(card);
        if (card.isWildProperty() && payload.has("color")) {
            String colorName = payload.get("color").getAsString();
            card.setWildColor(CardColor.valueOf(colorName));
        }
        activePlayer.getPropertyZone().addProperty(card);
        if (activePlayer.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
            endGame(activePlayer);
        }
    }

    private void playRentCard(Card card, JsonObject payload) {
        activePlayer.removeCardFromHand(card);
        deck.discard(card);
        String rentColorName = payload.get("color").getAsString();
        CardColor rentColor = CardColor.valueOf(rentColorName);
        int baseRentAmount = activePlayer.getPropertyZone().getRentAmount(rentColor);
        int rentAmount = activePlayer.isDoubleRentActive() ? baseRentAmount * 2 : baseRentAmount;
        activePlayer.setDoubleRentActive(false);
        if (card.getColor() == CardColor.WILD) {
            String targetPlayerId = payload.get("targetPlayerId").getAsString();
            Player targetPlayer = findPlayer(targetPlayerId);
            if (targetPlayer != null) {
                requirePayment(targetPlayer, activePlayer, rentAmount);
            }
        } else {
            for (Player player : players) {
                if (!player.equals(activePlayer)) {
                    requirePayment(player, activePlayer, rentAmount);
                }
            }
        }
    }

    private void playActionCard(Card card, JsonObject payload) {
        activePlayer.removeCardFromHand(card);
        deck.discard(card);
        String actionName = card.getName();
        switch (actionName) {
            case "Debt Collector":
                String targetId = payload.get("targetPlayerId").getAsString();
                Player target = findPlayer(targetId);
                if (target != null) requirePayment(target, activePlayer, GameConstants.DEBT_COLLECTOR_AMOUNT);
                break;
            case "Birthday":
                for (Player player : players) {
                    if (!player.equals(activePlayer)) requirePayment(player, activePlayer, GameConstants.BIRTHDAY_AMOUNT);
                }
                break;
            case "Deal Breaker":
            case "Pass Go":
                List<Card> drawnCards = deck.drawMultiple(2);
                drawnCards.forEach(activePlayer::addCardToHand);
                break;
            case "Double The Rent":
            case "Double Rent":
                activePlayer.setDoubleRentActive(true);
                break;
            case "House":
                CardColor houseColor = CardColor.valueOf(payload.get("color").getAsString());
                activePlayer.getPropertyZone().addHouse(houseColor);
                break;
            case "Hotel":
                CardColor hotelColor = CardColor.valueOf(payload.get("color").getAsString());
                activePlayer.getPropertyZone().addHotel(hotelColor);
                break;
            case "Forced Deal":
                String myCardId = payload.get("myCardId").getAsString();
                String theirCardId = payload.get("theirCardId").getAsString();
                Player otherPlayer = findPlayer(payload.get("targetPlayerId").getAsString());
                if (otherPlayer != null) executeForcedDeal(activePlayer, otherPlayer, myCardId, theirCardId);
                break;
            case "Sly Deal":
                String stealCardId = payload.get("targetCardId").getAsString();
                Player stealFrom = findPlayer(payload.get("targetPlayerId").getAsString());
                if (stealFrom != null) executeSlyDeal(activePlayer, stealFrom, stealCardId);
                break;
        }
    }

    private void executeForcedDeal(Player player1, Player player2, String cardId1, String cardId2) {
        Card card1 = findPropertyInZone(player1, cardId1);
        Card card2 = findPropertyInZone(player2, cardId2);
        if (card1 == null || card2 == null) return;
        player1.getPropertyZone().removeProperty(card1);
        player2.getPropertyZone().removeProperty(card2);
        if (card1.isWildProperty()) card1.setWildColor(null);
        if (card2.isWildProperty()) card2.setWildColor(null);
        player1.getPropertyZone().addProperty(card2);
        player2.getPropertyZone().addProperty(card1);
    }

    private void executeSlyDeal(Player thief, Player victim, String cardId) {
        Card stolenCard = findPropertyInZone(victim, cardId);
        if (stolenCard == null) return;
        victim.getPropertyZone().removeProperty(stolenCard);
        if (stolenCard.isWildProperty()) stolenCard.setWildColor(null);
        thief.getPropertyZone().addProperty(stolenCard);
    }

    private Card findPropertyInZone(Player player, String cardId) {
        for (List<Card> properties : player.getPropertyZone().getAllPropertyGroups().values()) {
            for (Card card : properties) {
                if (card.getId().equals(cardId)) return card;
            }
        }
        return null;
    }

    private void requirePayment(Player debtor, Player creditor, int amount) {
        try {
            List<Card> payment = debtor.getBank().removeCards(amount);
            for (Card moneyCard : payment) {
                creditor.getBank().deposit(moneyCard);
            }
        } catch (Bank.InsufficientFundsException e) {
            int available = debtor.getBank().getTotal();
            if (available > 0) {
                try {
                    List<Card> partial = debtor.getBank().removeCards(available);
                    partial.forEach(m -> creditor.getBank().deposit(m));
                } catch (Bank.InsufficientFundsException ignored) {}
            }
        }
    }

    public void endTurn(String playerId) {
        if (activePlayer != null && playerId.equals(activePlayer.getId())) {
            forceEndTurn();
        }
    }

    private void forceEndTurn() {
        cancelTimer();
        if (activePlayer != null) {
            while (activePlayer.needsToDiscard() && !activePlayer.getHand().isEmpty()) {
                Card discarded = activePlayer.removeCardFromHand(0);
                deck.discard(discarded);
            }
            activePlayer.setActivePlayer(false);
            recordAction(activePlayer.getId(), activePlayer.getNickname(), "END_TURN", "", 0, "Turn ended");
        }
        phase = GamePhase.END;
        broadcastGameState();
        scheduler.schedule(this::startNextTurn, 1500, TimeUnit.MILLISECONDS);
    }

    private Optional<Player> checkWinner() {
        return players.stream().filter(p -> p.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS).findFirst();
    }

    private void endGame(Player winner) {
        gameRunning = false;
        cancelTimer();
        phase = GamePhase.GAME_OVER;
        JsonObject result = new JsonObject();
        result.addProperty("winnerId", winner.getId());
        result.addProperty("winnerNickname", winner.getNickname());
        result.addProperty("gameDuration", getGameDuration());
        result.addProperty("completeSets", winner.getCompleteSetsCount());
        room.broadcast(MessageProtocol.MessageType.GAME_OVER, result.toString());
    }

    public void handlePlayerDisconnect(String clientId) {
        Player disconnected = findPlayer(clientId);
        if (disconnected == null) return;
        disconnected.setConnected(false);
        broadcastGameState();
        long connectedPlayers = players.stream().filter(Player::isConnected).count();
        if (connectedPlayers < GameConstants.MIN_PLAYERS) {
            gameRunning = false;
            cancelTimer();
            JsonObject drawResult = new JsonObject();
            drawResult.addProperty("reason", "Not enough players");
            room.broadcast(MessageProtocol.MessageType.GAME_DRAW, drawResult.toString());
        } else if (activePlayer != null && clientId.equals(activePlayer.getId())) {
            forceEndTurn();
        }
    }

    private Player findPlayer(String playerId) {
        return players.stream().filter(p -> p.getId().equals(playerId)).findFirst().orElse(null);
    }

    private void recordAction(String playerId, String nickname, String action,
                              String targetPlayer, int amount, String details) {
        ActionRecord record = new ActionRecord(actionHistory.size() + 1, playerId, nickname,
                action, targetPlayer, amount, details, System.currentTimeMillis());
        actionHistory.add(0, record);
        if (actionHistory.size() > 100) {
            actionHistory.remove(actionHistory.size() - 1);
        }
    }

    private void broadcastGameState() {
        for (Player viewer : players) {
            GameState gameState = createGameState(viewer.getId());
            String stateJson = gson.toJson(gameState);
            room.sendToPlayer(viewer.getId(), MessageProtocol.MessageType.GAME_STATE_UPDATE, stateJson);
        }
    }

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
            Map<Integer, Integer> denominations = new HashMap<>();
            for (int denom : GameConstants.MONEY_DENOMINATIONS) {
                int count = player.getBank().getCount(denom);
                if (count > 0) denominations.put(denom, count);
            }
            playerState.setBankDenominations(denominations);
            Map<String, Integer> colorCounts = new HashMap<>();
            for (Map.Entry<CardColor, List<Card>> entry : player.getPropertyZone().getAllPropertyGroups().entrySet()) {
                if (!entry.getValue().isEmpty()) colorCounts.put(entry.getKey().name(), entry.getValue().size());
            }
            playerState.setPropertyColorCounts(colorCounts);
            if (player.getId().equals(viewerId)) {
                List<GameState.CardInfo> handCards = new ArrayList<>();
                for (Card card : player.getHand()) {
                    handCards.add(new GameState.CardInfo(card));
                }
                playerState.setHandCards(handCards);
            }
            state.addPlayerState(player.getId(), playerState);
        }
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

    private void sendError(String playerId, String message) {
        JsonObject error = new JsonObject();
        error.addProperty("message", message);
        room.sendToPlayer(playerId, MessageProtocol.MessageType.ERROR, error.toString());
    }

    private String getGameDuration() {
        long duration = System.currentTimeMillis() - gameStartTime;
        long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    static class ActionRecord {
        int index;
        String playerId;
        String playerNickname;
        String action;
        String targetPlayer;
        int amount;
        String details;
        long timestamp;

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