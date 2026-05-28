package com.monopolydeal.server;

import java.io.*;
import java.net.*;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import java.util.UUID;

/**
 * Client handler - manages all message sending/receiving for a single client connection
 *
 * Each connected client has a corresponding ClientHandler instance running in its own thread.
 * Implements Runnable interface, reads messages from the client in a loop within run().
 *
 * Message processing flow:
 * 1. Read one line of JSON message from Socket
 * 2. Parse MessageType (CREATE_ROOM/JOIN_ROOM/PLAY_CARD/etc.)
 * 3. Call appropriate handler method based on message type
 * 4. Handler methods find the corresponding GameRoom via GameServer, delegate to GameRoom or GameSession
 *
 * Supported message types:
 * - Room management: CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, PLAYER_READY
 * - Game actions: PLAY_CARD, END_TURN
 * - Communication: CHAT_MESSAGE, PING
 */
public class ClientHandler implements Runnable {
    /** Unique client identifier (assigned by server on connection) */
    private final String clientId;
    /** Client socket connection */
    private final Socket socket;
    /** Parent game server */
    private final GameServer server;
    /** Output stream - sends messages to client */
    private PrintWriter out;
    /** Input stream - reads messages from client */
    private BufferedReader in;
    /** Player nickname (set when creating/joining room) */
    private String nickname;
    /** Current room code (null if not in any room) */
    private String currentRoom;

    /**
     * Constructor
     * @param clientId unique client identifier
     * @param socket client socket connection
     * @param server parent game server
     */
    public ClientHandler(String clientId, Socket socket, GameServer server) {
        this.clientId = clientId;
        this.socket = socket;
        this.server = server;
    }

    /**
     * Main thread method - loops reading client messages
     * Exits loop and disconnects when connection is closed or IO exception occurs
     */
    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);  // autoFlush=true

            String message;
            while ((message = in.readLine()) != null) {
                handleMessage(message);  // Process each JSON message line
            }
        } catch (IOException e) {
            System.out.println("Client " + clientId.substring(0, 8) + " disconnected");
        } finally {
            disconnect();  // Ensure resource cleanup
        }
    }

    /**
     * Processes a single JSON message - parses message type and dispatches to appropriate handler
     * @param jsonMessage complete JSON message string (format: {"type":"MESSAGE_TYPE","payload":{...}})
     */
    private void handleMessage(String jsonMessage) {
        try {
            MessageProtocol.MessageType type = MessageProtocol.getType(jsonMessage);
            JsonObject payload = JsonParser.parseString(MessageProtocol.getPayload(jsonMessage)).getAsJsonObject();

            switch (type) {
                case CREATE_ROOM:
                    handleCreateRoom(payload);     // Create room request
                    break;
                case JOIN_ROOM:
                    handleJoinRoom(payload);       // Join room request
                    break;
                case LEAVE_ROOM:
                    handleLeaveRoom();             // Leave room request
                    break;
                case PLAYER_READY:
                    handlePlayerReady(payload);    // Player ready status toggle
                    break;
                case PLAY_CARD:
                    handlePlayCard(payload);       // Play card request
                    break;
                case END_TURN:
                    handleEndTurn();               // End turn request
                    break;
                case FLIP_WILD_CARD:
                    handleFlipWildCard(payload);   // Flip wild property color
                    break;
                case SUBMIT_PAYMENT:
                    handleSubmitPayment(payload);  // Submit payment card selection
                    break;
                case PLAY_JUST_SAY_NO:
                    handlePlayJustSayNo(payload);  // Play Just Say No to cancel action
                    break;
                case PASS_REACTION:
                    handlePassReaction(payload);   // Pass reaction (don't play Just Say No)
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
            System.err.println("Error processing message: " + e.getMessage());
            e.printStackTrace();
            sendError("Internal server error: " + e.getMessage());
        }
    }

    /** Handles create room request - generates 6-digit room code, creates GameRoom */
    private void handleCreateRoom(JsonObject payload) {
        String rawNickname = payload.get("nickname").getAsString();
        if (!isValidNickname(rawNickname)) {
            sendError("Invalid nickname: must be 1-12 characters, no special characters");
            return;
        }
        this.nickname = rawNickname.trim();
        String roomCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        GameRoom room = server.createRoom(roomCode, this);
        this.currentRoom = roomCode;
        System.out.println("Room created: " + roomCode + ", creator: " + nickname);
        room.broadcastRoomUpdate();  // Notify all players in room of state update
    }

    /** Handles join room request - finds and joins existing room by room code */
    private void handleJoinRoom(JsonObject payload) {
        String rawNickname = payload.get("nickname").getAsString();
        if (!isValidNickname(rawNickname)) {
            sendError("Invalid nickname: must be 1-12 characters, no special characters");
            return;
        }
        this.nickname = rawNickname.trim();
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

    /** Handles leave room request - removes self from room and notifies other players */
    private void handleLeaveRoom() {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.removePlayer(clientId);
            }
            currentRoom = null;
        }
    }

    /** Handles player ready status toggle - marks "ready" or "not ready" in lobby */
    private void handlePlayerReady(JsonObject payload) {
        boolean ready = payload.get("ready").getAsBoolean();
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.setPlayerReady(clientId, ready);
            }
        }
    }

    /** Handles play card request - delegates to GameSession for game logic execution */
    private void handlePlayCard(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handlePlayCard(clientId, payload);
            }
        }
    }

    /** Handles end turn request - delegates to GameSession */
    private void handleEndTurn() {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().endTurn(clientId);
            }
        }
    }

    /** Handles flip wild property color request - delegates to GameSession, does not consume play count */
    private void handleFlipWildCard(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                String cardId = payload.get("cardId").getAsString();
                String newColor = payload.get("color").getAsString();
                room.getGameSession().handleFlipWildCard(clientId, cardId, newColor);
            }
        }
    }

    /** Handles playing Just Say No - delegates to GameSession */
    private void handlePlayJustSayNo(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handlePlayJustSayNo(clientId, payload);
            }
        }
    }

    /** Handles passing reaction (not playing Just Say No) - delegates to GameSession */
    private void handlePassReaction(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handlePassReaction(clientId);
            }
        }
    }

    /** Handles submit payment request - delegates to GameSession for payment validation and transfer */
    private void handleSubmitPayment(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handleSubmitPayment(clientId, payload);
            }
        }
    }

    /** Handles chat message - broadcasts to all players in the room */
    private void handleChatMessage(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.broadcast(MessageProtocol.MessageType.CHAT_MESSAGE, payload.toString());
            }
        }
    }

    /**
     * Sends a message to this client
     * @param type message type
     * @param payload message payload (JSON string)
     */
    public void sendMessage(MessageProtocol.MessageType type, String payload) {
        if (out != null) {
            String message = MessageProtocol.createMessage(type, payload);
            out.println(message);
            out.flush();
        }
    }

    /** Sends error message to client */
    private void sendError(String errorMessage) {
        JsonObject error = new JsonObject();
        error.addProperty("message", errorMessage);
        sendMessage(MessageProtocol.MessageType.ERROR, error.toString());
    }

    // ==================== Getters/Setters ====================

    public String getClientId() { return clientId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getCurrentRoom() { return currentRoom; }

    /** Disconnects - leaves room, removes from server, closes socket */
    public void disconnect() {
        handleLeaveRoom();
        server.removeClient(clientId);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Exception during socket close can be ignored
        }
    }

    /** Validates nickname: non-empty, length 1-12, alphanumeric and underscore only */
    private boolean isValidNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) return false;
        String trimmed = nickname.trim();
        if (trimmed.length() < 1 || trimmed.length() > 12) return false;
        return trimmed.matches("[a-zA-Z0-9_]+");
    }
}