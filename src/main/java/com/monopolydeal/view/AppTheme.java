package com.monopolydeal.view;

import java.awt.Color;
import java.util.Map;

/**
 * 应用主题常量 — 视图层所有共享颜色和样式的唯一数据源
 *
 * 设计原则：
 * - 地产颜色以 CardRenderer 中的十六进制值为准（已在两处使用）
 * - 所有视图组件通过静态字段引用，不再各自定义颜色映射
 * - 工具类模式：私有构造函数防止实例化
 */
public final class AppTheme {

    private AppTheme() {
        // 工具类，禁止实例化
    }

    // ==================== 地产颜色映射（10 种纯地产颜色 → RGB） ====================

    /**
     * 纯地产颜色映射表。
     * key = CardColor 枚举名称（如 "BROWN"、"RED"），value = 对应的 RGB 颜色。
     * 用于：PlayerPanel 地产卡堆叠绘制、PropertySetPanel 标签背景、CardRenderer 卡牌配色。
     */
    public static final Map<String, Color> PROPERTY_COLORS = Map.ofEntries(
            Map.entry("BROWN",       new Color(0x8B5E3C)),
            Map.entry("LIGHT_BLUE",  new Color(0x87CEEB)),
            Map.entry("PINK",        new Color(0xFF69B4)),
            Map.entry("ORANGE",      new Color(0xFF8C00)),
            Map.entry("RED",         new Color(0xDC143C)),
            Map.entry("YELLOW",      new Color(0xFFD700)),
            Map.entry("GREEN",       new Color(0x228B22)),
            Map.entry("BLUE",        new Color(0x00008B)),
            Map.entry("BLACK",       new Color(0x2B2B2B)),
            Map.entry("LIGHT_GREEN", new Color(0x90EE90))
    );

    /**
     * 地产卡牌渐变色映射表 — CardRenderer 中卡面上半部的渐变终点色。
     * key = CardColor 枚举名称，value = 较深色的渐变终点 RGB。
     * 这些值来自原版手工配色，需与 PROPERTY_COLORS 的 key 一一对应。
     */
    public static final Map<String, Color> PROPERTY_GRADIENT_COLORS = Map.ofEntries(
            Map.entry("BROWN",       new Color(0x5D3A1A)),
            Map.entry("LIGHT_BLUE",  new Color(0x4A9EC4)),
            Map.entry("PINK",        new Color(0xC2185B)),
            Map.entry("ORANGE",      new Color(0xE65100)),
            Map.entry("RED",         new Color(0x8B0000)),
            Map.entry("YELLOW",      new Color(0xB8860B)),
            Map.entry("GREEN",       new Color(0x145214)),
            Map.entry("BLUE",        new Color(0x000055)),
            Map.entry("BLACK",       new Color(0x111111)),
            Map.entry("LIGHT_GREEN", new Color(0x4CAF50))
    );

    // ==================== 万能卡配置 ====================

    /**
     * 万能地产卡颜色选择映射 — key=万能卡名称, value=可选的地产颜色列表。
     * 用于 showWildColorPicker() 中根据卡牌类型提供限定的颜色选项。
     */
    public static final Map<String, String[]> WILD_COLOR_OPTIONS = Map.ofEntries(
            Map.entry("Multi-Color Wild",
                    new String[]{"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED",
                            "YELLOW", "GREEN", "BLUE", "BLACK", "LIGHT_GREEN"}),
            Map.entry("Dark Blue/Green Wild",
                    new String[]{"BLUE", "GREEN"}),
            Map.entry("Red/Yellow Wild",
                    new String[]{"RED", "YELLOW"}),
            Map.entry("Brown/Light Blue Wild",
                    new String[]{"BROWN", "LIGHT_BLUE"}),
            Map.entry("Orange/Pink Wild",
                    new String[]{"ORANGE", "PINK"}),
            Map.entry("Light Green/Black Wild",
                    new String[]{"LIGHT_GREEN", "BLACK"})
    );

    // ==================== 品牌色 ====================

    /** 金色 — 用于标题、高亮边框、回合标识等 */
    public static final Color GOLD = new Color(255, 215, 0);

    // ==================== 主背景色 ====================

    /** 主背景深色 — GamePanel 主背景 */
    public static final Color BG_DARK = new Color(18, 22, 28);

    /** 更深背景色 — 侧边栏等 */
    public static final Color BG_DARKER = new Color(14, 17, 22);

    /** 绿色桌面色（亮端）— 玩家区域渐变背景 */
    public static final Color TABLE_GREEN = new Color(25, 70, 40);

    /** 绿色桌面色（暗端）— 玩家区域渐变背景 */
    public static final Color TABLE_GREEN_DARK = new Color(15, 50, 28);

    // ==================== 文字色 ====================

    /** 主要文字色 — 白色内容文字 */
    public static final Color TEXT_PRIMARY = new Color(220, 220, 220);

    /** 暗淡文字色 — 次要/禁用状态 */
    public static final Color TEXT_DIM = new Color(150, 150, 150);

    // ==================== 语义色 ====================

    /** 红色危险/警告色 — 倒计时告警、错误状态 */
    public static final Color RED_DANGER = new Color(220, 50, 50);
}
