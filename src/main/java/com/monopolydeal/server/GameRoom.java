package com.monopolydeal.server;

import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.*;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.Gson;

/**
 * Game room - manages players from lobby to game start
 *
 * Lifecycle:
 * 1. Creator creates room (CREATE_ROOM), becomes first player
 * 2. Others join via room code (JOIN_ROOM), up to MAX_PLAYERS
 * 3. Players click "ready" in lobby (PLAYER_READY)
 * 4. When all players (>=MIN_PLAYERS) are ready, game auto-starts
 * 5. Creates GameSession and calls start() to begin game flow
 *
 * State broadcast:
 * - On join/leave/ready change, broadcast ROOM_UPDATE to all members
 * - ROOM_UPDATE contains room code, player list, and ready states
 */
public class GameRoom {
    /** Room code (6 alphanumeric uppercase chars) */
    private final String roomCode;
    /** Room creator */
    private final ClientHandler creator;
    /** Players in room: key=clientId, value=ClientHandler */
    private final Map<String, ClientHandler> players;
    /** Player ready states: key=clientId, value=ready */
    private final Map<String, Boolean> readyStates;
    /** Game session (created after game starts) */
    private GameSession gameSession;
    /** Whether game has started */
    private boolean gameStarted;
    /** Gson serializer */
    private final Gson gson;
    /** Parent server (for removing empty rooms) */
    private GameServer server = null;

    /**
     * Constructor - creates new room, creator auto-joins
     *
     * @param roomCode room code
     * @param creator  room creator
     */
    public GameRoom(String roomCode, ClientHandler creator) {
        this.roomCode = roomCode;
        this.creator = creator;
        this.players = new ConcurrentHashMap<>();
        this.readyStates = new ConcurrentHashMap<>();
        this.gameStarted = false;
        this.gson = new Gson();
        this.server = server;
        addPlayer(creator);
    }

    /**
     * Adds a player to the room
     * @param player player to add
     * @return true=success, false=room full
     */
    public boolean addPlayer(ClientHandler player) {
        if (players.size() >= GameConstants.MAX_PLAYERS) {
            return false;
        }
        players.put(player.getClientId(), player);
        readyStates.put(player.getClientId(), false);
        return true;
    }

    /**
     * Removes a player from the room
     * If game started, notifies GameSession of disconnection
     * If room becomes empty, marks game as ended
     *
     * @param clientId player ID to remove
     */
    public void removePlayer(String clientId) {
        players.remove(clientId);
        readyStates.remove(clientId);

        if (gameStarted && gameSession != null) {
            gameSession.handlePlayerDisconnect(clientId);
        }

        if (players.isEmpty()) {
            gameStarted = false;
            gameSession = null;
            server.removeRoom(roomCode);
        }

        broadcastRoomUpdate();
    }

    /**
     * Sets player's ready status
     * When all players (>=MIN_PLAYERS) are ready and game not started, auto-starts
     *
     * @param clientId player ID
     * @param ready true=ready, false=not ready
     */
    public void setPlayerReady(String clientId, boolean ready) {
        readyStates.put(clientId, ready);
        broadcastRoomUpdate();

        if (allPlayersReady() && players.size() >= GameConstants.MIN_PLAYERS && !gameStarted) {
            startGame();
        }
    }

    /** Checks if all players are ready */
    private boolean allPlayersReady() {
        return players.size() >= GameConstants.MIN_PLAYERS &&
                readyStates.values().stream().allMatch(Boolean::booleanValue);
    }

    /**
     * Starts the game - creates Player list, initializes GameSession
     * Player info extracted from ClientHandler (ID and nickname)
     */
    private void startGame() {
        gameStarted = true;
        List<Player> playerList = new ArrayList<>();
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            Player player = new Player(entry.getKey(), entry.getValue().getNickname());
            playerList.add(player);
        }

        gameSession = new GameSession(this, playerList);
        gameSession.start();
    }

    /**
     * Broadcasts room state update to all players
     * Contains room code, player list, ready states, creator flag
     */
    public void broadcastRoomUpdate() {
        JsonObject roomState = new JsonObject();
        roomState.addProperty("roomCode", roomCode);
        roomState.addProperty("playerCount", players.size());
        roomState.addProperty("maxPlayers", GameConstants.MAX_PLAYERS);
        roomState.addProperty("gameStarted", gameStarted);

        JsonArray playerArray = new JsonArray();
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            JsonObject playerInfo = new JsonObject();
            playerInfo.addProperty("id", entry.getKey());
            playerInfo.addProperty("nickname", entry.getValue().getNickname());
            playerInfo.addProperty("ready", readyStates.getOrDefault(entry.getKey(), false));
            playerInfo.addProperty("isCreator", entry.getKey().equals(creator.getClientId()));
            playerArray.add(playerInfo);
        }
        roomState.add("players", playerArray);

        System.out.println("Broadcasting room update: " + roomCode + ", players: " + players.size());
        broadcast(MessageProtocol.MessageType.ROOM_UPDATE, roomState.toString());
    }

    /** Broadcasts message to all players in room */
    public void broadcast(MessageProtocol.MessageType type, String payload) {
        for (ClientHandler player : players.values()) {
            player.sendMessage(type, payload);
        }
    }

    /** Sends message to a specific player */
    public void sendToPlayer(String clientId, MessageProtocol.MessageType type, String payload) {
        ClientHandler player = players.get(clientId);
        if (player != null) {
            player.sendMessage(type, payload);
        }
    }

    // ==================== Getters ====================

    public String getRoomCode() { return roomCode; }
    public GameSession getGameSession() { return gameSession; }
    public Map<String, ClientHandler> getPlayers() { return players; }
}