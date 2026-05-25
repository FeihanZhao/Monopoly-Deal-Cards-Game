package com.monopolydeal.model;

/**
 * 卡牌颜色枚举类
 *
 * 定义了《大富翁纸牌游戏》中所有卡牌的颜色类型，分为三大类：
 * 1. 纯地产颜色（BROWN ~ LIGHT_GREEN）：可用于组成完整地产组合的颜色
 * 2. 双色租金颜色（BROWN_LIGHT_BLUE ~ BLACK_LIGHT_GREEN）：租金卡使用的双色组合
 * 3. 特殊颜色（WILD、NONE）：万能卡和无颜色卡
 *
 * 每种纯地产颜色都有对应的 setSize（组成一套完整地产所需的卡牌数量），
 * 以及对应的租金计算规则。
 */
public enum CardColor {
    // ==================== 纯地产颜色 ====================
    // 格式：颜色(显示名称, 建成一套所需卡片数)
    BROWN("Brown", 2),               // 棕色 - 需要2张组成一套
    LIGHT_BLUE("Light Blue", 3),     // 浅蓝色 - 需要3张组成一套
    PINK("Pink", 3),                 // 粉色 - 需要3张组成一套
    ORANGE("Orange", 3),             // 橙色 - 需要3张组成一套
    RED("Red", 3),                   // 红色 - 需要3张组成一套
    YELLOW("Yellow", 3),             // 黄色 - 需要3张组成一套
    GREEN("Green", 3),               // 绿色 - 需要3张组成一套
    BLUE("Blue", 2),                 // 蓝色 - 需要2张组成一套
    PURPLE("Purple", 3),             // 紫色 - 需要3张组成一套
    BLACK("Black", 4),               // 黑色 - 需要4张组成一套
    LIGHT_GREEN("Light Green", 3),   // 浅绿色 - 需要3张组成一套

    // ==================== 双色租金颜色 ====================
    // 用于租金卡，表示该租金卡可以针对这两种颜色的地产收租
    BROWN_LIGHT_BLUE("Brown/Light Blue", 0),
    PINK_ORANGE("Pink/Orange", 0),
    RED_YELLOW("Red/Yellow", 0),
    GREEN_BLUE("Green/Blue", 0),
    PURPLE_ORANGE("Purple/Orange", 0),
    BLACK_LIGHT_GREEN("Black/Light Green", 0),

    // ==================== 特殊颜色 ====================
    WILD("Wild", 0),    // 万能色 - 万能地产卡和万能租金卡使用
    NONE("None", 0);    // 无色 - 金钱卡和行动卡使用

    /** 颜色的显示名称 */
    private final String name;
    /** 建成一套完整地产所需的卡牌数量（纯地产颜色>0，双色/特殊颜色=0） */
    private final int setSize;

    /**
     * 构造函数
     * @param name 显示名称
     * @param setSize 建成一套所需卡片数（0表示非纯地产颜色）
     */
    CardColor(String name, int setSize) {
        this.name = name;
        this.setSize = setSize;
    }

    /** 获取颜色的显示名称 */
    public String getName() { return name; }
    /** 获取建成一套完整地产所需的卡牌数量 */
    public int getSetSize() { return setSize; }

    /**
     * 根据该颜色地产的持有数量计算租金金额
     *
     * 每种颜色的租金规则不同，规则来源于《大富翁纸牌游戏》官方规则：
     * - 棕色/浅蓝：1张=1M, 2张+=2M
     * - 粉色/橙色：1张=1M, 2张+=3M
     * - 红色/黄色：1张=2M, 2张=4M, 3张+=6M
     * - 绿色：    1张=2M, 2张=4M, 3张+=7M
     * - 蓝色：    1张=3M, 2张+=8M
     * - 紫色：    1张=1M, 2张=2M, 3张+=4M
     * - 黑色：    1张=1M, 2张=2M, 3张=3M, 4张+=5M
     * - 浅绿色：  1张=1M, 2张=2M, 3张+=4M
     *
     * @param propertiesInSet 该颜色已经持有的地产数量
     * @return 应收的租金金额（单位：M/百万）
     */
    public int getRentAmount(int propertiesInSet) {
        if (this == BROWN || this == LIGHT_BLUE) {
            return propertiesInSet >= 2 ? 2 : 1;
        }
        if (this == PINK || this == ORANGE) {
            return propertiesInSet >= 2 ? 3 : 1;
        }
        if (this == RED || this == YELLOW) {
            if (propertiesInSet >= 3) return 6;
            if (propertiesInSet >= 2) return 4;
            return 2;
        }
        if (this == GREEN) {
            if (propertiesInSet >= 3) return 7;
            if (propertiesInSet >= 2) return 4;
            return 2;
        }
        if (this == BLUE) {
            return propertiesInSet >= 2 ? 8 : 3;
        }
        if (this == PURPLE) {
            if (propertiesInSet >= 3) return 4;
            if (propertiesInSet >= 2) return 2;
            return 1;
        }
        if (this == BLACK) {
            if (propertiesInSet >= 4) return 5;
            if (propertiesInSet >= 3) return 3;
            if (propertiesInSet >= 2) return 2;
            return 1;
        }
        if (this == LIGHT_GREEN) {
            if (propertiesInSet >= 3) return 4;
            if (propertiesInSet >= 2) return 2;
            return 1;
        }
        return 0;
    }

    /**
     * 判断该颜色是否为纯地产颜色（可以放置地产卡的颜色）
     * 排除双色租金颜色、WILD万能色和NONE无色
     * @return true=可放置地产，false=不可
     */
    public boolean isPropertyColor() {
        return setSize > 0 && this != WILD && this != NONE
                && this != BROWN_LIGHT_BLUE && this != PINK_ORANGE
                && this != RED_YELLOW && this != GREEN_BLUE
                && this != PURPLE_ORANGE && this != BLACK_LIGHT_GREEN;
    }

    /**
     * 判断该颜色是否为租金卡颜色（双色组合或万能色）
     * 租金卡使用双色组合来表示可以对这两种颜色的地产进行收租
     * @return true=是租金卡颜色，false=不是
     */
    public boolean isRentColor() {
        return this == BROWN_LIGHT_BLUE || this == PINK_ORANGE
                || this == RED_YELLOW || this == GREEN_BLUE
                || this == PURPLE_ORANGE || this == BLACK_LIGHT_GREEN
                || this == WILD;
    }
}
