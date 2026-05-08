package com.monopolydeal.server;

import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.*;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.Gson;

public class GameRoom {
    private final String roomCode;
    private final ClientHandler creator;
    private final Map<String, ClientHandler> players;
    private final Map<String, Boolean> readyStates;
    private GameSession gameSession;
    private boolean gameStarted;
    private final Gson gson;

    public GameRoom(String roomCode, ClientHandler creator) {
        this.roomCode = roomCode;
        this.creator = creator;
        this.players = new ConcurrentHashMap<>();
        this.readyStates = new ConcurrentHashMap<>();
        this.gameStarted = false;
        this.gson = new Gson();
        addPlayer(creator);
    }

    public boolean addPlayer(ClientHandler player) {
        if (players.size() >= GameConstants.MAX_PLAYERS) {
            return false;
        }
        players.put(player.getClientId(), player);
        readyStates.put(player.getClientId(), false);
        return true;
    }

    public void removePlayer(String clientId) {
        players.remove(clientId);
        readyStates.remove(clientId);

        if (gameStarted && gameSession != null) {
            gameSession.handlePlayerDisconnect(clientId);
        }

        if (players.isEmpty()) {
            gameStarted = false;
            gameSession = null;
        }

        broadcastRoomUpdate();
    }

    public void setPlayerReady(String clientId, boolean ready) {
        readyStates.put(clientId, ready);
        broadcastRoomUpdate();

        if (allPlayersReady() && players.size() >= GameConstants.MIN_PLAYERS && !gameStarted) {
            startGame();
        }
    }

    private boolean allPlayersReady() {
        return players.size() >= GameConstants.MIN_PLAYERS &&
                readyStates.values().stream().allMatch(Boolean::booleanValue);
    }

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

        System.out.println("Broadcasting room update: " + roomCode + " with " + players.size() + " players");
        broadcast(MessageProtocol.MessageType.ROOM_UPDATE, roomState.toString());
    }

    public void broadcast(MessageProtocol.MessageType type, String payload) {
        for (ClientHandler player : players.values()) {
            player.sendMessage(type, payload);
        }
    }

    public void sendToPlayer(String clientId, MessageProtocol.MessageType type, String payload) {
        ClientHandler player = players.get(clientId);
        if (player != null) {
            player.sendMessage(type, payload);
        }
    }

    public String getRoomCode() { return roomCode; }
    public GameSession getGameSession() { return gameSession; }
    public Map<String, ClientHandler> getPlayers() { return players; }
}