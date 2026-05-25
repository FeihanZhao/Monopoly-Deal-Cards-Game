package com.monopolydeal.model;

/**
 * 游戏阶段枚举类
 *
 * 定义了《大富翁纸牌游戏》一回合中的各个阶段：
 * 1. INIT（初始化）：游戏尚未开始，等待玩家准备
 * 2. DRAW（抽牌阶段）：回合开始时自动抽3张牌
 * 3. PLAY（出牌阶段）：玩家可以打出最多3张牌
 * 4. END（结束阶段）：检查手牌是否超过7张上限，超出的需要弃牌
 * 5. DISCARD（弃牌阶段）：玩家被迫弃牌到手牌上限
 * 6. GAME_OVER（游戏结束）：有玩家集齐3套完整地产，游戏结束
 */
public enum GamePhase {
    INIT("Initialization"),  // 初始化阶段 - 等待所有玩家准备
    DRAW("Draw Phase"),      // 抽牌阶段 - 每回合自动抽3张牌
    PLAY("Play Phase"),      // 出牌阶段 - 玩家主动出牌（最多3张）
    END("End Phase"),        // 结束阶段 - 回合收尾，自动弃牌到7张上限
    DISCARD("Discard Phase"),// 弃牌阶段 - 因行动卡效果需强制弃牌
    GAME_OVER("Game Over");  // 游戏结束 - 有玩家获胜

    /** 阶段的显示名称 */
    private final String displayName;

    /**
     * 构造函数
     * @param displayName 阶段的显示名称
     */
    GamePhase(String displayName) {
        this.displayName = displayName;
    }

    /** 获取阶段的显示名称 */
    public String getDisplayName() {
        return displayName;
    }

    /** 返回阶段的显示名称字符串 */
    @Override
    public String toString() {
        return displayName;
    }
}
