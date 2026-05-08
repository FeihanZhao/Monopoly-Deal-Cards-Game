package com.monopolydeal.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MessageProtocol {
    private static final Gson gson = new GsonBuilder().create();

    public enum MessageType {
        CREATE_ROOM,
        JOIN_ROOM,
        LEAVE_ROOM,
        ROOM_UPDATE,
        PLAYER_JOINED,
        PLAYER_LEFT,
        PLAYER_READY,
        START_GAME,
        GAME_STATE_UPDATE,
        GAME_STARTED,
        TURN_STARTED,
        DRAW_CARDS,
        PLAY_CARD,
        DISCARD_CARD,
        END_TURN,
        TURN_TIMEOUT,
        PLACE_PROPERTY,
        PLACE_MONEY,
        PLAY_RENT,
        PLAY_DEBT_COLLECTOR,
        PLAY_BIRTHDAY,
        PLAY_DEAL_BREAKER,
        PLAY_FORCED_DEAL,
        PLAY_SLY_DEAL,
        PLAY_DOUBLE_RENT,
        PLAY_HOUSE,
        PLAY_HOTEL,
        PLAY_JUST_SAY_NO,
        ACTION_RESULT,
        PAYMENT_REQUIRED,
        PAYMENT_MADE,
        CARD_DRAWN,
        CARD_PLAYED,
        CARD_DISCARDED,
        GAME_OVER,
        PLAYER_WON,
        GAME_DRAW,
        ERROR,
        INVALID_ACTION,
        CHAT_MESSAGE,
        PING,
        PONG,
        DISCONNECT
    }

    public static String createMessage(MessageType type, String payload) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type.name());
        message.add("payload", JsonParser.parseString(payload));
        return gson.toJson(message);
    }

    public static String createMessage(MessageType type, Object payload) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type.name());
        message.add("payload", gson.toJsonTree(payload));
        return gson.toJson(message);
    }

    public static MessageType getType(String jsonMessage) {
        JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
        return MessageType.valueOf(obj.get("type").getAsString());
    }

    public static String getPayload(String jsonMessage) {
        JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
        return obj.get("payload").toString();
    }

    public static <T> T getPayload(String jsonMessage, Class<T> clazz) {
        String payload = getPayload(jsonMessage);
        return gson.fromJson(payload, clazz);
    }
}