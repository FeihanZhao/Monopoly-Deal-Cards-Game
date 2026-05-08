package com.monopolydeal.server;

import java.io.*;
import java.net.*;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import java.util.UUID;

public class ClientHandler implements Runnable {
    private final String clientId;
    private final Socket socket;
    private final GameServer server;
    private PrintWriter out;
    private BufferedReader in;
    private String nickname;
    private String currentRoom;

    public ClientHandler(String clientId, Socket socket, GameServer server) {
        this.clientId = clientId;
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String message;
            while ((message = in.readLine()) != null) {
                handleMessage(message);
            }
        } catch (IOException e) {
            System.out.println("Client " + clientId.substring(0, 8) + " disconnected");
        } finally {
            disconnect();
        }
    }

    private void handleMessage(String jsonMessage) {
        try {
            MessageProtocol.MessageType type = MessageProtocol.getType(jsonMessage);
            JsonObject payload = JsonParser.parseString(MessageProtocol.getPayload(jsonMessage)).getAsJsonObject();

            switch (type) {
                case CREATE_ROOM:
                    handleCreateRoom(payload);
                    break;
                case JOIN_ROOM:
                    handleJoinRoom(payload);
                    break;
                case LEAVE_ROOM:
                    handleLeaveRoom();
                    break;
                case PLAYER_READY:
                    handlePlayerReady(payload);
                    break;
                case PLAY_CARD:
                    handlePlayCard(payload);
                    break;
                case END_TURN:
                    handleEndTurn();
                    break;
                case PING:
                    sendMessage(MessageProtocol.MessageType.PONG, "{}");
                    break;
                case CHAT_MESSAGE:
                    handleChatMessage(payload);
                    break;
                default:
                    System.out.println("Unknown message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
            sendError("Internal server error: " + e.getMessage());
        }
    }

    private void handleCreateRoom(JsonObject payload) {
        this.nickname = payload.get("nickname").getAsString();
        String roomCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        GameRoom room = server.createRoom(roomCode, this);
        this.currentRoom = roomCode;
        System.out.println("Room created: " + roomCode + " by " + nickname);
        room.broadcastRoomUpdate();
    }

    private void handleJoinRoom(JsonObject payload) {
        this.nickname = payload.get("nickname").getAsString();
        String roomCode = payload.get("roomCode").getAsString();
        GameRoom room = server.getRoom(roomCode);

        if (room == null) {
            sendError("Room not found");
            return;
        }

        if (!room.addPlayer(this)) {
            sendError("Room is full");
            return;
        }

        this.currentRoom = roomCode;
        System.out.println(nickname + " joined room: " + roomCode);
        room.broadcastRoomUpdate();
    }

    private void handleLeaveRoom() {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.removePlayer(clientId);
            }
            currentRoom = null;
        }
    }

    private void handlePlayerReady(JsonObject payload) {
        boolean ready = payload.get("ready").getAsBoolean();
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.setPlayerReady(clientId, ready);
            }
        }
    }

    private void handlePlayCard(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handlePlayCard(clientId, payload);
            }
        }
    }

    private void handleEndTurn() {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().endTurn(clientId);
            }
        }
    }

    private void handleChatMessage(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.broadcast(MessageProtocol.MessageType.CHAT_MESSAGE, payload.toString());
            }
        }
    }

    public void sendMessage(MessageProtocol.MessageType type, String payload) {
        if (out != null) {
            String message = MessageProtocol.createMessage(type, payload);
            out.println(message);
            out.flush();
        }
    }

    private void sendError(String errorMessage) {
        JsonObject error = new JsonObject();
        error.addProperty("message", errorMessage);
        sendMessage(MessageProtocol.MessageType.ERROR, error.toString());
    }

    public String getClientId() { return clientId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getCurrentRoom() { return currentRoom; }

    public void disconnect() {
        handleLeaveRoom();
        server.removeClient(clientId);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
        }
    }
}