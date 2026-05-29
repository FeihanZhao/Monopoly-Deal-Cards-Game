package com.monopolydeal.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Message protocol - defines communication between client and server
 *
 * All network messages use JSON format: {"type": "MESSAGE_TYPE", "payload": {...}}
 *
 * Message types are grouped into:
 * - Room management: CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, ROOM_UPDATE, PLAYER_READY
 * - Game flow: START_GAME, GAME_STATE_UPDATE, DRAW_CARDS, PLAY_CARD, END_TURN
 * - Action cards: PLAY_DEBT_COLLECTOR, PLAY_BIRTHDAY, PLAY_DEAL_BREAKER, etc.
 * - System: ERROR, PING, PONG, DISCONNECT, CHAT_MESSAGE
 *
 * Utility methods:
 * - createMessage(): Builds JSON message
 * - getType(): Extracts message type from JSON
 * - getPayload(): Extracts payload from JSON
 */
public class MessageProtocol {
    /** Gson serializer instance */
    private static final Gson gson = new GsonBuilder().create();

    /**
     * Message type enum - all possible communication types between client and server
     */
    public enum MessageType {
        // ===== Room Management =====
        CREATE_ROOM,       // Client requests room creation
        JOIN_ROOM,         // Client requests to join room
        LEAVE_ROOM,        // Client requests to leave room
        ROOM_UPDATE,       // Server broadcasts room state update
        PLAYER_JOINED,     // Server notifies player joined
        PLAYER_LEFT,       // Server notifies player left
        PLAYER_READY,      // Client sets/cancels ready status

        // ===== Game Flow =====
        START_GAME,        // Server notifies game start
        GAME_STATE_UPDATE, // Server broadcasts game state (core message, includes full GameState)
        GAME_STARTED,      // Server notifies game has started
        TURN_STARTED,      // Server notifies new turn started
        DRAW_CARDS,        // Server notifies draw result
        PLAY_CARD,         // Client requests to play card
        DISCARD_CARD,      // Client requests to discard card
        END_TURN,          // Client requests to end turn
        TURN_TIMEOUT,      // Server notifies turn timeout
        PLACE_PROPERTY,    // Client requests to place property card
        PLACE_MONEY,       // Client requests to place money card
        FLIP_WILD_CARD,    // Client requests to flip wild property color (no action cost)

        // ===== Specific Action Types =====
        PLAY_RENT,              // Client uses rent card
        PLAY_DEBT_COLLECTOR,    // Use debt collector
        PLAY_BIRTHDAY,          // Use birthday card
        PLAY_DEAL_BREAKER,      // Use deal breaker
        PLAY_FORCED_DEAL,       // Use forced deal
        PLAY_SLY_DEAL,          // Use sly deal
        PLAY_DOUBLE_RENT,       // Use double rent
        PLAY_HOUSE,             // Use house card
        PLAY_HOTEL,             // Use hotel card
        PLAY_JUST_SAY_NO,       // Use just say no card

        // ===== Reaction Chain =====
        REACTION_REQUIRED,  // Server notifies player can play Just Say No
        PASS_REACTION,      // Client declines to react (no Just Say No)

        // ===== Action Results =====
        ACTION_RESULT,     // Server returns action execution result
        PAYMENT_REQUIRED,  // Server requests player to pay
        SUBMIT_PAYMENT,    // Client submits payment (selects money cards)
        PAYMENT_MADE,      // Server notifies payment complete
        CARD_DRAWN,        // Server notifies card drawn
        CARD_PLAYED,       // Server notifies card played
        CARD_DISCARDED,    // Server notifies card discarded

        // ===== Game Results =====
        GAME_OVER,         // Server notifies game over (player won)
        PLAYER_WON,        // Server notifies player victory
        GAME_DRAW,         // Server notifies game draw (insufficient players)

        // ===== System Messages =====
        ERROR,             // Server returns error message
        INVALID_ACTION,    // Server notifies invalid action
        CHAT_MESSAGE,      // Chat message (client to server broadcast)
        PING,              // Heartbeat request
        PONG,              // Heartbeat response
        DISCONNECT,        // Server notifies player disconnect

        DISCARD_REQUIRED   // Server requests player to discard
    }

    /**
     * Creates JSON format message
     * @param type message type
     * @param payload message payload (JSON string)
     * @return complete JSON message string: {"type":"...","payload":{...}}
     */
    public static String createMessage(MessageType type, String payload) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type.name());
        message.add("payload", JsonParser.parseString(payload));
        return gson.toJson(message);
    }

    /**
     * Creates JSON format message (auto-serializes object to JSON)
     * @param type message type
     * @param payload message payload object (auto-converted to JSON)
     * @return complete JSON message string
     */
    public static String createMessage(MessageType type, Object payload) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type.name());
        message.add("payload", gson.toJsonTree(payload));
        return gson.toJson(message);
    }

    /**
     * Extracts message type from JSON message string
     * @param jsonMessage complete JSON message string
     * @return parsed MessageType enum value
     */
    public static MessageType getType(String jsonMessage) {
        JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
        return MessageType.valueOf(obj.get("type").getAsString());
    }

    /**
     * Extracts payload from JSON message string (returns JSON string)
     * @param jsonMessage complete JSON message string
     * @return payload JSON string
     */
    public static String getPayload(String jsonMessage) {
        JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
        return obj.get("payload").toString();
    }

    /**
     * Extracts payload from JSON message string and deserializes to specified type
     * @param jsonMessage complete JSON message string
     * @param clazz target Java type
     * @param <T> generic type
     * @return deserialized object instance
     */
    public static <T> T getPayload(String jsonMessage, Class<T> clazz) {
        String payload = getPayload(jsonMessage);
        return gson.fromJson(payload, clazz);
    }
}