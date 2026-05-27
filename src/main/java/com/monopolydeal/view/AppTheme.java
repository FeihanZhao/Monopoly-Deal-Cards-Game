package com.monopolydeal.view;

import java.awt.Color;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 应用主题类 - 集中管理游戏UI的配色方案
 *
 * 定义了大富翁纸牌游戏中所有地产颜色的标准RGB值、渐变色、
 * 以及万能卡的颜色选择选项。
 *
 * 颜色名称与 CardColor 枚举中的名称保持一致（如 BROWN、LIGHT_BLUE 等）。
 */
public final class AppTheme {

    private AppTheme() {}

    /** 地产颜色映射表 key=颜色名（与CardColor枚举名一致）, value=标准显示颜色 */
    public static final Map<String, Color> PROPERTY_COLORS;

    /** 地产渐变色映射表 key=颜色名, value=渐变终点色（用于卡牌从上到下的渐变效果） */
    public static final Map<String, Color> PROPERTY_GRADIENT_COLORS;

    /**
     * 万能卡颜色选择选项映射表
     * key=万能卡名称, value=可选颜色名称数组
     * 用于万能地产卡和万能租金卡打出时弹出颜色选择对话框
     */
    public static final Map<String, String[]> WILD_COLOR_OPTIONS;

    static {
        // ===== 十种纯地产颜色的标准配色 =====
        Map<String, Color> colors = new HashMap<>();
        colors.put("BROWN",        new Color(0x8B5E3C));  // 棕色
        colors.put("LIGHT_BLUE",   new Color(0x87CEEB));  // 浅蓝色
        colors.put("PINK",         new Color(0xFF69B4));  // 粉色
        colors.put("ORANGE",       new Color(0xFF8C00));  // 橙色
        colors.put("RED",          new Color(0xDC143C));  // 红色
        colors.put("YELLOW",       new Color(0xFFD700));  // 黄色
        colors.put("GREEN",        new Color(0x228B22));  // 绿色
        colors.put("BLUE",         new Color(0x0000CD));  // 蓝色
        colors.put("BLACK",        new Color(0x2B2B2B));  // 黑色
        colors.put("LIGHT_GREEN",  new Color(0x90EE90));  // 浅绿色
        PROPERTY_COLORS = Collections.unmodifiableMap(colors);

        // ===== 地产渐变色（卡牌从上到下由PROPERTY_COLORS渐变到此处） =====
        Map<String, Color> gradients = new HashMap<>();
        gradients.put("BROWN",        new Color(0x5C3A1E));  // 深棕
        gradients.put("LIGHT_BLUE",   new Color(0x4A90B8));  // 深蓝
        gradients.put("PINK",         new Color(0xC44A8A));  // 深粉
        gradients.put("ORANGE",       new Color(0xCC6600));  // 深橙
        gradients.put("RED",          new Color(0x8B0000));  // 深红
        gradients.put("YELLOW",       new Color(0xCC9900));  // 深黄
        gradients.put("GREEN",        new Color(0x145214));  // 深绿
        gradients.put("BLUE",         new Color(0x000080));  // 深蓝
        gradients.put("BLACK",        new Color(0x111111));  // 更深黑
        gradients.put("LIGHT_GREEN",  new Color(0x4CAF50));  // 深浅绿
        PROPERTY_GRADIENT_COLORS = Collections.unmodifiableMap(gradients);

        // ===== 万能卡颜色选择选项 =====
        // 多彩万能卡（Multi-Color Wild）可以选择任意一种地产颜色
        String[] allColors = {
                "BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED",
                "YELLOW", "GREEN", "BLUE", "BLACK", "LIGHT_GREEN"
        };

        // 双色万能卡只能选择对应的两种颜色
        String[] brownLightBlue  = {"BROWN", "LIGHT_BLUE"};
        String[] pinkOrange      = {"PINK", "ORANGE"};
        String[] redYellow       = {"RED", "YELLOW"};
        String[] greenBlue       = {"GREEN", "BLUE"};
        String[] blackLightGreen = {"BLACK", "LIGHT_GREEN"};

        Map<String, String[]> wildOptions = new HashMap<>();
        wildOptions.put("Multi-Color Wild",     allColors);
        wildOptions.put("Wild Property",         allColors);
        wildOptions.put("Brown/Light Blue Wild", brownLightBlue);
        wildOptions.put("Pink/Orange Wild",      pinkOrange);
        wildOptions.put("Red/Yellow Wild",       redYellow);
        wildOptions.put("Green/Blue Wild",       greenBlue);
        wildOptions.put("Black/Light Green Wild", blackLightGreen);
        // 通用默认
        wildOptions.put("Wild",                  allColors);
        WILD_COLOR_OPTIONS = Collections.unmodifiableMap(wildOptions);
    }
}
