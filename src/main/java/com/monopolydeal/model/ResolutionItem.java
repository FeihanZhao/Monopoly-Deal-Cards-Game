package com.monopolydeal.model;

import com.google.gson.JsonObject;

/**
 * Resolution stack element — represents a pending action or Just Say No.
 *
 * Each element records: who initiated, who needs to respond, and the original data.
 * When the responder passes or times out, the top element is popped and processed.
 */
public class ResolutionItem {
    /** Unique resolution identifier */
    private final String resolutionId;
    /** Action type: DEBT_COLLECTOR / DEAL_BREAKER / SLY_DEAL / FORCED_DEAL / RENT / BIRTHDAY / JUST_SAY_NO */
    private final String actionType;
    /** ID of the player who initiated this resolution (the one who played the card) */
    private final String initiatorId;
    /** ID of the player who needs to respond (the one who can play Just Say No) */
    private final String responderId;
    /** Original source card (for logging and reference) */
    private final Card sourceCard;
    /** Original request payload (contains targetPlayerId, color, etc., for deferred execution) */
    private final JsonObject actionPayload;
    /** Timestamp when this resolution was created (milliseconds) */
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
