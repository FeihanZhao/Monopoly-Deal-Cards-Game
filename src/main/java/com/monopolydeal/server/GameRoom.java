package com.monopolydeal.server;

import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.*;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.Gson;

/**
 * Manages a single game room instance for the Monopoly Deal game.
 * Handles player management, ready states, game initialization, and room state broadcasting.
 * Acts as an intermediary between connected clients and the active game session.
 */
public class GameRoom {
    // Unique 6-character alphanumeric code identifying this game room
    private final String roomCode;
    
    // The client who created and owns this game room (has admin privileges)
    private final ClientHandler creator;
    
    // Thread-safe map of all connected players in the room (clientId -> ClientHandler)
    private final Map<String, ClientHandler> players;
    
    // Thread-safe map tracking ready status for each player (clientId -> isReady)
    private final Map<String, Boolean> readyStates;
    
    // The active game session instance - null when game hasn't started
    private GameSession gameSession;
    
    // Flag indicating if the game has started (prevents new joins/ready checks)
    private boolean gameStarted;
    
    // Gson instance for JSON serialization/deserialization of network messages
    private final Gson gson;
    
    // Reference to the main game server for room management operations
    private GameServer server;

    /**
     * Constructs a new GameRoom with the specified creator and room code.
     * Initializes thread-safe collections for player management and adds the creator to the room.
     *
     * @param roomCode Unique identifier code for the game room
     * @param creator  ClientHandler representing the user who created the room
     */
    public GameRoom(String roomCode, ClientHandler creator) {
        this.roomCode = roomCode;
        this.creator = creator;
        // Use ConcurrentHashMap for thread safety in multi-client environment
        this.players = new ConcurrentHashMap<>();
        this.readyStates = new ConcurrentHashMap<>();
        this.gameStarted = false;
        this.gson = new Gson();
        this.server = null;
        // Automatically add the room creator as the first player
        addPlayer(creator);
    }

    /**
     * Adds a new player to the game room if the room isn't full.
     * Initializes their ready state to false when joining.
     *
     * @param player ClientHandler representing the connecting player
     * @return true if player was added successfully, false if room is full
     */
    public boolean addPlayer(ClientHandler player) {
        // Check if room has reached maximum player capacity
        if (players.size() >= GameConstants.MAX_PLAYERS) {
            return false;
        }
        players.put(player.getClientId(), player);
        // New players start with unready status
        readyStates.put(player.getClientId(), false);
        return true;
    }

    /**
     * Removes a player from the room when they disconnect or leave.
     * Handles game session cleanup if needed and removes empty rooms from the server.
     * Broadcasts updated room state to remaining players after removal.
     *
     * @param clientId Unique identifier of the player to remove
     */
    public void removePlayer(String clientId) {
        players.remove(clientId);
        readyStates.remove(clientId);
        
        // Notify active game session if a player leaves during gameplay
        if (gameStarted && gameSession != null) {
            gameSession.handlePlayerDisconnect(clientId);
        }
        
        // Clean up empty room - remove from server and reset game state
        if (players.isEmpty()) {
            gameStarted = false;
            gameSession = null;
            if (server != null) server.removeRoom(roomCode);
        }
        
        // Update all remaining players with new room state
        broadcastRoomUpdate();
    }

    /**
     * Updates the ready status of a player and checks if game can start.
     * Automatically starts the game if all players are ready and minimum requirements met.
     *
     * @param clientId Unique identifier of the player
     * @param ready    New ready state (true = ready, false = not ready)
     */
    public void setPlayerReady(String clientId, boolean ready) {
        readyStates.put(clientId, ready);
        broadcastRoomUpdate();
        
        // Start game automatically if: all ready, min players present, and not already started
        if (allPlayersReady() && players.size() >= GameConstants.MIN_PLAYERS && !gameStarted) {
            startGame();
        }
    }

    /**
     * Checks if ALL players in the room have marked themselves as ready.
     * Also verifies minimum player count is satisfied before allowing game start.
     *
     * @return true if all players are ready and min count reached, false otherwise
     */
    private boolean allPlayersReady() {
        return players.size() >= GameConstants.MIN_PLAYERS &&
                readyStates.values().stream().allMatch(Boolean::booleanValue);
    }

    /**
     * Initializes and starts a new game session.
     * Creates Player objects from connected clients and launches the game logic thread.
     * Sets gameStarted flag to prevent further room modifications.
     */
    private void startGame() {
        gameStarted = true;
        List<Player> playerList = new ArrayList<>();
        
        // Convert ClientHandlers to Player model objects for game session
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            Player player = new Player(entry.getKey(), entry.getValue().getNickname());
            playerList.add(player);
        }
        
        // Create and start new game session with room reference and player list
        gameSession = new GameSession(this, playerList);
        gameSession.start();
    }

    /**
     * Broadcasts the current room state to ALL connected players.
     * Creates a JSON payload containing room info, player list, ready states, and game status.
     * Used to update clients when players join/leave/change ready status.
     */
    public void broadcastRoomUpdate() {
        JsonObject roomState = new JsonObject();
        roomState.addProperty("roomCode", roomCode);
        roomState.addProperty("playerCount", players.size());
        roomState.addProperty("maxPlayers", GameConstants.MAX_PLAYERS);
        roomState.addProperty("gameStarted", gameStarted);
        
        // Build player list array with detailed info for each player
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
        
        // Send complete room state to all players
        broadcast(MessageProtocol.MessageType.ROOM_UPDATE, roomState.toString());
    }

    /**
     * Sends a message to ALL players in the room.
     * Utility method for room-wide notifications and game state updates.
     *
     * @param type    Message type enum for protocol handling
     * @param payload JSON string containing message data
     */
    public void broadcast(MessageProtocol.MessageType type, String payload) {
        for (ClientHandler player : players.values()) {
            player.sendMessage(type, payload);
        }
    }

    /**
     * Sends a targeted message to a specific player only.
     * Used for private game information (hand cards, personal notifications).
     *
     * @param clientId Target player's unique identifier
     * @param type     Message type enum for protocol handling
     * @param payload  JSON string containing message data
     */
    public void sendToPlayer(String clientId, MessageProtocol.MessageType type, String payload) {
        ClientHandler player = players.get(clientId);
        if (player != null) {
            player.sendMessage(type, payload);
        }
    }

    // --- Getter Methods ---
    /** @return Unique room code string */
    public String getRoomCode() { return roomCode; }
    
    /** @return Active game session instance (null if not started) */
    public GameSession getGameSession() { return gameSession; }
    
    /** @return Thread-safe map of current players in the room */
    public Map<String, ClientHandler> getPlayers() { return players; }
}
