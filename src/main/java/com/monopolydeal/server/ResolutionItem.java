package com.monopolydeal.server;

import com.monopolydeal.model.Card;
import com.google.gson.JsonObject;

public class ResolutionItem {
    private final String resolutionId;
    private final String actionType;
    private final String initiatorId;
    private final String responderId;
    private final Card sourceCard;
    private final JsonObject actionPayload;

    public ResolutionItem(String resolutionId, String actionType, String initiatorId,
                          String responderId, Card sourceCard, JsonObject actionPayload) {
        this.resolutionId = resolutionId;
        this.actionType = actionType;
        this.initiatorId = initiatorId;
        this.responderId = responderId;
        this.sourceCard = sourceCard;
        this.actionPayload = actionPayload;
    }

    public String getResolutionId() { return resolutionId; }
    public String getActionType() { return actionType; }
    public String getInitiatorId() { return initiatorId; }
    public String getResponderId() { return responderId; }
    public Card getSourceCard() { return sourceCard; }
    public JsonObject getActionPayload() { return actionPayload; }

    public boolean isJustSayNo() {
        return "JUST_SAY_NO".equals(actionType);
    }
}