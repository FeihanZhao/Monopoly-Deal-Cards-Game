package com.monopolydeal.model;

/**
 * 游戏常量类（工具类，不可实例化）
 *
 * 集中管理《大富翁纸牌游戏》中所有可调整的游戏规则参数和网络配置。
 * 所有字段均为 public static final，可以在编译时内联优化。
 */
public final class GameConstants {
    /** 私有构造函数，防止外部实例化 */
    private GameConstants() {}

    // ==================== 玩家相关常量 ====================

    /** 最小玩家数（少于则无法开始游戏） */
    public static final int MIN_PLAYERS = 2;
    /** 最大玩家数（房间满员的上限） */
    public static final int MAX_PLAYERS = 5;
    /** 游戏开始时的初始手牌数量 */
    public static final int INITIAL_HAND_SIZE = 5;
    /** 每回合自动抽取的卡牌数量 */
    public static final int DRAW_COUNT = 2;
    /** 回合开始时手牌为空时的额外摸牌数 */
    public static final int EMPTY_HAND_DRAW_COUNT = 5;
    /** 每回合最多可以打出的牌数 */
    public static final int MAX_PLAYS_PER_TURN = 3;
    /** 手牌上限（回合结束时超过此数量需要弃牌） */
    public static final int MAX_HAND_SIZE = 7;

    // ==================== 时间相关常量 ====================

    /** 每回合的时限（秒），超时自动结束回合 */
    public static final int TURN_TIMEOUT_SECONDS = 30;
    /** 超时警告提前时间（秒），在回合结束前N秒向玩家发出警告 */
    public static final int TIMEOUT_WARNING_SECONDS = 10;
    /** 弃牌阶段的倒计时（秒） */
    public static final int DISCARD_TIMEOUT_SECONDS = 15;
    /** 决定是否打出just say no的时间 */
    public static final int JUST_SAY_NO_TIMEOUT_SECONDS = 5;

    // ==================== 建筑限制 ====================

    /** 每套完整地产上最多可建造的房屋数 */
    public static final int MAX_HOUSES_PER_SET = 1;
    /** 每套完整地产上最多可建造的酒店数 */
    public static final int MAX_HOTELS_PER_SET = 1;

    // ==================== 胜利条件 ====================

    /** 获胜所需的完整地产组合数量（集齐3套不同颜色的完整地产即获胜） */
    public static final int WINNING_COMPLETE_SETS = 3;

    // ==================== 行动卡金额常量 ====================

    /** "生日"行动卡：所有其他玩家各支付2M给当前玩家 */
    public static final int BIRTHDAY_AMOUNT = 2;
    /** "债务收集者"行动卡：指定一名玩家支付5M给当前玩家 */
    public static final int DEBT_COLLECTOR_AMOUNT = 5;

    // ==================== 网络配置常量 ====================

    /** 服务器默认监听端口号 */
    public static final int SERVER_PORT = 8888;
    /** 客户端默认连接的主机地址 */
    public static final String DEFAULT_HOST = "localhost";

    // ==================== 金钱面值 ====================

    /** 金钱卡的所有可能面值（单位：M/百万） */
    public static final int[] MONEY_DENOMINATIONS = {1, 2, 3, 4, 5, 10};
}
