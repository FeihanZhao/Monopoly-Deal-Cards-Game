package com.monopolydeal.model;

import com.google.gson.JsonObject;

/**
 * 决议栈元素 — 代表一个待处理的行动或 Just Say No
 *
 * 每个元素记录：谁发起的、谁需要响应、原始数据是什么。
 * 当响应人放弃或超时时，栈顶元素被弹出并处理。
 */
public class ResolutionItem {
    /** 决议唯一标识符 */
    private final String resolutionId;
    /** 行动类型：DEBT_COLLECTOR / DEAL_BREAKER / SLY_DEAL / FORCED_DEAL / RENT / BIRTHDAY / JUST_SAY_NO */
    private final String actionType;
    /** 发起此决议的玩家ID（打牌的人） */
    private final String initiatorId;
    /** 需要响应此决议的玩家ID（可以打 Just Say No 的人） */
    private final String responderId;
    /** 原始卡牌（用于日志和引用） */
    private final Card sourceCard;
    /** 原始请求负载数据（包含 targetPlayerId、color 等，用于延迟执行） */
    private final JsonObject actionPayload;
    /** 决议创建时间戳（毫秒） */
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

    /** 是否是 Just Say No 类型的决议 */
    public boolean isJustSayNo() {
        return "JUST_SAY_NO".equals(actionType);
    }

    @Override
    public String toString() {
        return "Resolution[" + actionType + "] " + initiatorId + " → " + responderId;
    }
}
