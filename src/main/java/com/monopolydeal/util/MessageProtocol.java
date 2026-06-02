package com.monopolydeal.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Message protocol — defines the communication protocol between client and server.
 *
 * All network communication uses JSON format. Each message has the structure:
 * {"type": "MESSAGE_TYPE", "payload": {...}}
 *
 * The MessageType enum contains all supported communication types, grouped as:
 * - Room management: CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, ROOM_UPDATE, PLAYER_READY
 * - Game flow: START_GAME, GAME_STATE_UPDATE, DRAW_CARDS, PLAY_CARD, END_TURN
 * - Action cards: PLAY_DEBT_COLLECTOR, PLAY_BIRTHDAY, PLAY_DEAL_BREAKER, etc.
 * - System messages: ERROR, PING, PONG, DISCONNECT, CHAT_MESSAGE
 *
 * Utility methods:
 * - createMessage(): build a JSON-formatted message
 * - getType(): extract the message type from a JSON message
 * - getPayload(): extract the payload data from a JSON message
 */
public class MessageProtocol {
    /** Gson serializer instance */
    private static final Gson gson = new GsonBuilder().create();

    /**
     * Message type enum — defines all possible communication types between client and server.
     */
    public enum MessageType {
        // ===== Room management =====
        CREATE_ROOM,       // Client requests to create a room
        JOIN_ROOM,         // Client requests to join a room
        LEAVE_ROOM,        // Client requests to leave a room
        ROOM_UPDATE,       // Server broadcasts room state update
        PLAYER_JOINED,     // Server notifies that a player has joined
        PLAYER_LEFT,       // Server notifies that a player has left
        PLAYER_READY,      // Client toggles ready/unready state

        // ===== Game flow =====
        START_GAME,        // Server notifies that the game has started
        REQUEST_START_GAME,// Host requests to start the game
        GAME_STATE_UPDATE, // Server broadcasts game state update (core message, contains full GameState)
        GAME_STARTED,      // Server notifies game has started
        TURN_STARTED,      // Server notifies a new turn has started
        DRAW_CARDS,        // Server notifies draw result
        PLAY_CARD,         // Client requests to play a card
        DISCARD_CARD,      // Client requests to discard a card
        END_TURN,          // Client requests to end the turn
        TURN_TIMEOUT,      // Server notifies turn timeout
        PLACE_PROPERTY,    // Client requests to place a property card
        PLACE_MONEY,       // Client requests to place a money card
        FLIP_WILD_CARD,    // Client requests to change wild property color (does not consume a play)

        // ===== Specific action types =====
        PLAY_RENT,              // Client uses a rent card
        PLAY_DEBT_COLLECTOR,    // Use Debt Collector
        PLAY_BIRTHDAY,          // Use Birthday
        PLAY_DEAL_BREAKER,      // Use Deal Breaker
        PLAY_FORCED_DEAL,       // Use Forced Deal
        PLAY_SLY_DEAL,          // Use Sly Deal
        PLAY_DOUBLE_RENT,       // Use Double the Rent
        PLAY_HOUSE,             // Use House
        PLAY_HOTEL,             // Use Hotel
        PLAY_JUST_SAY_NO,       // Use Just Say No

        // ===== Resolution chain =====
        REACTION_REQUIRED,  // Server notifies a player they can respond with Just Say No
        PASS_REACTION,      // Client declines to respond (pass on Just Say No)

        // ===== Action results =====
        ACTION_RESULT,     // Server returns action execution result
        PAYMENT_REQUIRED,  // Server requires a player to pay
        SUBMIT_PAYMENT,    // Client submits payment (selects money cards to pay)
        DISCARD_REQUIRED,  // Server requires player to discard (hand exceeds limit at end of turn)
        SUBMIT_DISCARD,    // Client submits discard selection
        PAYMENT_MADE,      // Server notifies payment completed
        CARD_DRAWN,        // Server notifies card drawn
        CARD_PLAYED,       // Server notifies card played
        CARD_DISCARDED,    // Server notifies card discarded

        // ===== Game results =====
        GAME_OVER,         // Server notifies game over (a player has won)
        PLAYER_WON,        // Server notifies player won
        GAME_DRAW,         // Server notifies game draw (insufficient players)

        // ===== System messages =====
        ERROR,             // Server returns error message
        INVALID_ACTION,    // Server notifies invalid action
        CHAT_MESSAGE,      // Chat message (client to server broadcast)
        PING,              // Heartbeat request
        PONG,              // Heartbeat response
        DISCONNECT         // Server notifies player disconnect
    }

    /**
     * Create a JSON-formatted message.
     * @param type message type
     * @param payload message payload (JSON string)
     * @return full JSON message string, format: {"type":"...","payload":{...}}
     */
    public static String createMessage(MessageType type, String payload) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type.name());
        message.add("payload", JsonParser.parseString(payload));
        return gson.toJson(message);
    }

    /**
     * Create a JSON-formatted message (auto-serializes object to JSON).
     * @param type message type
     * @param payload message payload object (will be auto-converted to JSON)
     * @return full JSON message string
     */
    public static String createMessage(MessageType type, Object payload) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type.name());
        message.add("payload", gson.toJsonTree(payload));
        return gson.toJson(message);
    }

    /**
     * Extract the message type from a JSON message string.
     * @param jsonMessage full JSON message string
     * @return parsed MessageType enum value
     */
    public static MessageType getType(String jsonMessage) {
        JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
        return MessageType.valueOf(obj.get("type").getAsString());
    }

    /**
     * Extract the payload data from a JSON message string (returns JSON string).
     * @param jsonMessage full JSON message string
     * @return payload portion as a JSON string
     */
    public static String getPayload(String jsonMessage) {
        JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
        return obj.get("payload").toString();
    }

    /**
     * Extract the payload data from a JSON message string and deserialize to the specified type.
     * @param jsonMessage full JSON message string
     * @param clazz target Java type
     * @param <T> generic type
     * @return deserialized object instance
     */
    public static <T> T getPayload(String jsonMessage, Class<T> clazz) {
        String payload = getPayload(jsonMessage);
        return gson.fromJson(payload, clazz);
    }
}
