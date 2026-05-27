package com.monopolydeal.model;

import com.google.gson.JsonObject;

/**
 * Resolution stack item - represents a pending action or Just Say No to be processed
 *
 * Each item records: who initiated it, who needs to respond, and the original payload.
 * When the responder gives up or times out, the top item is popped and processed.
 */
public class ResolutionItem {
    /** Resolution unique identifier */
    private final String resolutionId;
    /** Action type: DEBT_COLLECTOR / DEAL_BREAKER / SLY_DEAL / FORCED_DEAL / RENT / BIRTHDAY / JUST_SAY_NO */
    private final String actionType;
    /** Player ID who initiated this resolution (the one who played the card) */
    private final String initiatorId;
    /** Player ID who needs to respond to this resolution (the one who can play Just Say No) */
    private final String responderId;
    /** Original source card (for logging and reference) */
    private final Card sourceCard;
    /** Original request payload data (contains targetPlayerId, color etc., for deferred execution) */
    private final JsonObject actionPayload;
    /** Resolution creation timestamp (milliseconds) */
    private final long createdAt;

    public ResolutionItem(String resolutionId, String actionType,
                          String initiatorId, String responderId,
                          Card sourceCard, JsonObject actionPayload) {
        this.resolutionId = resolutionId;
        this.actionType = actionType;
        this.initiatorId = initiatorId;
        this.responderId = responderId;
        this.sourceCard = sourceCard;
        this.actionPayload = actionPayload;
        this.createdAt = System.currentTimeMillis();
    }

    // ==================== Getters ====================
    public String getResolutionId() { return resolutionId; }
    public String getActionType() { return actionType; }
    public String getInitiatorId() { return initiatorId; }
    public String getResponderId() { return responderId; }
    public Card getSourceCard() { return sourceCard; }
    public JsonObject getActionPayload() { return actionPayload; }
    public long getCreatedAt() { return createdAt; }

    /** Whether this is a Just Say No type resolution */
    public boolean isJustSayNo() {
        return "JUST_SAY_NO".equals(actionType);
    }

    @Override
    public String toString() {
        return "Resolution[" + actionType + "] " + initiatorId + " → " + responderId;
    }
}
