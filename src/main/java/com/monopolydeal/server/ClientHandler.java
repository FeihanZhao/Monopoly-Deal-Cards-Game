package com.monopolydeal.server;

import java.io.*;
import java.net.*;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import java.util.UUID;

/**
 * Client handler — manages all message sending/receiving for a single client connection.
 *
 * Each connected client corresponds to one ClientHandler instance running on its own thread.
 * Implements Runnable; the run() method loops to read messages from the client.
 *
 * Message handling flow:
 * 1. Read a line of JSON from the socket
 * 2. Parse the MessageType (CREATE_ROOM, JOIN_ROOM, PLAY_CARD, etc.)
 * 3. Dispatch to the appropriate handler method
 * 4. The handler locates the GameRoom via GameServer and delegates to GameRoom or GameSession
 *
 * Supported message types:
 * - Room management: CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, PLAYER_READY
 * - Game actions: PLAY_CARD, END_TURN
 * - Communication: CHAT_MESSAGE, PING
 */
public class ClientHandler implements Runnable {
    /** Unique client identifier (assigned by the server on connection) */
    private final String clientId;
    /** Client socket connection */
    private final Socket socket;
    /** Owning game server */
    private final GameServer server;
    /** Output stream — sends messages to the client */
    private PrintWriter out;
    /** Input stream — reads messages from the client */
    private BufferedReader in;
    /** Player nickname (set when creating/joining a room) */
    private String nickname;
    /** Current room code (null if not in any room) */
    private String currentRoom;

    /**
     * Constructor.
     * @param clientId unique client identifier
     * @param socket client socket connection
     * @param server owning game server
     */
    public ClientHandler(String clientId, Socket socket, GameServer server) {
        this.clientId = clientId;
        this.socket = socket;
        this.server = server;
    }

    /**
     * Thread main method — loops reading client messages.
     * Exits loop and disconnects on connection close or IO exception.
     */
    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);  // autoFlush=true

            String message;
            while ((message = in.readLine()) != null) {
                handleMessage(message);  // Process JSON messages line by line
            }
        } catch (IOException e) {
            System.out.println("Client " + clientId.substring(0, 8) + " disconnected");
        } finally {
            disconnect();  // Ensure resource cleanup
        }
    }

    /**
     * Handle a single JSON message — parse type and dispatch to the appropriate handler.
     * @param jsonMessage full JSON message string (format: {"type":"MESSAGE_TYPE","payload":{...}})
     */
    private void handleMessage(String jsonMessage) {
        try {
            JsonObject root = JsonParser.parseString(jsonMessage).getAsJsonObject();
            MessageProtocol.MessageType type =
                    MessageProtocol.MessageType.valueOf(root.get("type").getAsString());
            JsonObject payload = root.getAsJsonObject("payload");

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
                    handlePlayerReady(payload);    // Toggle ready status
                    break;
                case REQUEST_START_GAME:
                    handleRequestStartGame(payload); // Host requests game start
                    break;
                case PLAY_CARD:
                    handlePlayCard(payload);       // Play card request
                    break;
                case END_TURN:
                    handleEndTurn();               // End turn request
                    break;
                case FLIP_WILD_CARD:
                    handleFlipWildCard(payload);   // Change wild property color
                    break;
                case SUBMIT_PAYMENT:
                    handleSubmitPayment(payload);  // Submit payment card selection
                    break;
                case SUBMIT_DISCARD:
                    handleSubmitDiscard(payload);  // Submit discard selection
                    break;
                case PLAY_JUST_SAY_NO:
                    handlePlayJustSayNo(payload);  // Play Just Say No to counter an action
                    break;
                case PASS_REACTION:
                    handlePassReaction(payload);   // Pass on response (don't play Just Say No)
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

    /** Handle create room request — generate 6-char room code, create GameRoom */
    private void handleCreateRoom(JsonObject payload) {
        String rawNickname = payload.get("nickname").getAsString();
        if (!isValidNickname(rawNickname)) {
            sendError("Invalid nickname: 1-12 characters, no special characters");
            return;
        }
        this.nickname = rawNickname.trim();
        String roomCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        GameRoom room = server.createRoom(roomCode, this);
        this.currentRoom = roomCode;
        System.out.println("Room created: " + roomCode + ", host: " + nickname);
        room.broadcastRoomUpdate();  // Notify all players in the room
    }

    /** Handle join room request — look up an existing room by code and join it */
    private void handleJoinRoom(JsonObject payload) {
        String rawNickname = payload.get("nickname").getAsString();
        if (!isValidNickname(rawNickname)) {
            sendError("Invalid nickname: 1-12 characters, no special characters");
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

    /** Handle leave room request — remove self from room and notify other players */
    private void handleLeaveRoom() {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.removePlayer(clientId);
            }
            currentRoom = null;
        }
    }

    /** Handle player ready toggle — mark "ready" or "unready" in the lobby */
    private void handlePlayerReady(JsonObject payload) {
        boolean ready = payload.get("ready").getAsBoolean();
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.setPlayerReady(clientId, ready);
            }
        }
    }

    /** Handle host request to start the game */
    private void handleRequestStartGame(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                String error = room.requestStartGame(clientId);
                if (error != null) {
                    sendError(error);
                }
            }
        }
    }

    /** Handle play card request — delegate to GameSession for game logic */
    private void handlePlayCard(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handlePlayCard(clientId, payload);
            }
        }
    }

    /** Handle end turn request — delegate to GameSession */
    private void handleEndTurn() {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().endTurn(clientId);
            }
        }
    }

    /** Handle wild property color change — delegate to GameSession; does not consume a play */
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

    /** Handle play Just Say No — delegate to GameSession */
    private void handlePlayJustSayNo(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handlePlayJustSayNo(clientId, payload);
            }
        }
    }

    /** Handle pass reaction — delegate to GameSession */
    private void handlePassReaction(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handlePassReaction(clientId);
            }
        }
    }

    /** Handle submit payment request — delegate to GameSession for payment validation and transfer */
    private void handleSubmitPayment(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handleSubmitPayment(clientId, payload);
            }
        }
    }

    /** Handle client-submitted discard selection */
    private void handleSubmitDiscard(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handleSubmitDiscard(clientId, payload);
            }
        }
    }

    /** Handle chat message — broadcast to all players in the room */
    private void handleChatMessage(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.broadcast(MessageProtocol.MessageType.CHAT_MESSAGE, payload.toString());
            }
        }
    }

    /**
     * Send a message to this client.
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

    /** Send an error message to the client */
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

    /** Disconnect — leave room, remove from server, and close the socket */
    public void disconnect() {
        handleLeaveRoom();
        server.removeClient(clientId);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // Ignore exceptions when closing the socket
        }
    }

    /** Validate nickname: non-empty, 1-12 chars, alphanumeric/Chinese/underscore only */
    private boolean isValidNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) return false;
        String trimmed = nickname.trim();
        if (trimmed.length() < 1 || trimmed.length() > 12) return false;
        return trimmed.matches("[\\u4e00-\\u9fa5a-zA-Z0-9_]+");
    }
}
