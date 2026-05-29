package com.monopolydeal.server;

import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.*;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.Gson;

/**
 * Game room — manages a group of players from lobby through game start.
 *
 * Lifecycle:
 * 1. Host creates the room (CREATE_ROOM) and automatically becomes the first player
 * 2. Other players join via room code (JOIN_ROOM), up to MAX_PLAYERS
 * 3. Players click "Ready" in the lobby (PLAYER_READY)
 * 4. When all players (>= MIN_PLAYERS) are ready, the game can start
 * 5. A GameSession is created and start() is called to begin gameplay
 *
 * State broadcast:
 * - ROOM_UPDATE is sent to all room members on every join/leave/ready state change
 * - ROOM_UPDATE includes room code, player list, and ready states
 */
public class GameRoom {
    /** Room code (6 uppercase alphanumeric characters) */
    private final String roomCode;
    /** Host (room creator) */
    private final ClientHandler creator;
    /** All players in the room: key=clientId, value=ClientHandler */
    private final Map<String, ClientHandler> players;
    /** Player ready states: key=clientId, value=whether ready */
    private final Map<String, Boolean> readyStates;
    /** Game session (created after the game starts) */
    private GameSession gameSession;
    /** Whether the game has started */
    private boolean gameStarted;
    /** Gson serializer */
    private final Gson gson;
    /** Owning server (for destroying empty rooms) */
    private final GameServer server;

    /**
     * Constructor — creates a new room; host auto-joins.
     * @param roomCode room code
     * @param creator host
     */
    public GameRoom(String roomCode, ClientHandler creator, GameServer server) {
        this.roomCode = roomCode;
        this.creator = creator;
        this.players = new ConcurrentHashMap<>();
        this.readyStates = new ConcurrentHashMap<>();
        this.gameStarted = false;
        this.gson = new Gson();
        this.server = server;
        addPlayer(creator);  // Host auto-joins
    }

    /**
     * Add a player to the room.
     * @param player the player to add
     * @return true=joined successfully, false=room is full
     */
    public boolean addPlayer(ClientHandler player) {
        if (players.size() >= GameConstants.MAX_PLAYERS) {
            return false;  // Room full, reject
        }
        players.put(player.getClientId(), player);
        readyStates.put(player.getClientId(), false);  // Initially not ready
        return true;
    }

    /**
     * Remove a player from the room.
     * If the game has already started, notifies GameSession to handle the disconnect.
     * If the room becomes empty, marks the game as ended.
     *
     * @param clientId ID of the player to remove
     */
    public void removePlayer(String clientId) {
        players.remove(clientId);
        readyStates.remove(clientId);

        // If game has started, notify GameSession of the disconnect
        if (gameStarted && gameSession != null) {
            gameSession.handlePlayerDisconnect(clientId);
        }

        // If room is empty, clean up game state
        if (players.isEmpty()) {
            gameStarted = false;
            gameSession = null;
            server.removeRoom(roomCode);
        }

        broadcastRoomUpdate();  // Notify remaining players of the room state change
    }

    /**
     * Set a player's ready state.
     * Auto-starts the game when all players (>= MIN_PLAYERS) are ready and the game hasn't started yet.
     *
     * @param clientId player ID
     * @param ready true=ready, false=unready
     */
    public void setPlayerReady(String clientId, boolean ready) {
        readyStates.put(clientId, ready);
        broadcastRoomUpdate();
        // Auto-start if all players (>= MIN_PLAYERS) are ready
        if (!gameStarted && allPlayersReady()) {
            startGame();
        }
    }

    /** Check whether all players are ready */
    private boolean allPlayersReady() {
        return players.size() >= GameConstants.MIN_PLAYERS &&
                readyStates.values().stream().allMatch(Boolean::booleanValue);
    }

    /**
     * Start the game — creates the Player list and initializes GameSession.
     * Player info is extracted from ClientHandler (ID and nickname).
     */
    private void startGame() {
        gameStarted = true;
        List<Player> playerList = new ArrayList<>();
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            Player player = new Player(entry.getKey(), entry.getValue().getNickname());
            playerList.add(player);
        }

        gameSession = new GameSession(this, playerList);
        gameSession.start();  // Start the game session (deal cards, begin first turn)
    }

    /**
     * Broadcast room state update to all players.
     * Includes room code, player list, ready states, and host indicator.
     */
    public void broadcastRoomUpdate() {
        JsonObject roomState = new JsonObject();
        roomState.addProperty("roomCode", roomCode);
        roomState.addProperty("playerCount", players.size());
        roomState.addProperty("maxPlayers", GameConstants.MAX_PLAYERS);
        roomState.addProperty("gameStarted", gameStarted);

        // Build player info array
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

    /**
     * Host requests to start the game.
     * Only the host can call this. All players must be ready and the minimum player count must be met.
     * @param clientId requester ID
     * @return null=success, otherwise an error message string
     */
    public String requestStartGame(String clientId) {
        if (!clientId.equals(creator.getClientId())) {
            return "Only the host can start the game";
        }
        if (players.size() < GameConstants.MIN_PLAYERS) {
            return "At least " + GameConstants.MIN_PLAYERS + " players required";
        }
        if (!allPlayersReady()) {
            return "Not all players are ready";
        }
        if (gameStarted) {
            return "Game has already started";
        }
        startGame();
        return null;
    }

    /** Broadcast a message to all players in the room */
    public void broadcast(MessageProtocol.MessageType type, String payload) {
        for (ClientHandler player : players.values()) {
            player.sendMessage(type, payload);
        }
    }

    /** Send a message to a specific player */
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
