package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonObject;

/**
 * 地产组合面板 - 以紧凑的彩色标签形式展示玩家的地产分组情况
 *
 * 替代PlayerPanel中原本的propertyPanel JPanel，提供更直观的地产展示。
 * 每个标签显示：
 * - 颜色圆点（代表地产颜色）
 * - 数量文本（当前数量/所需数量，如 "2/3"）
 * - 房屋/酒店图标（如有建造）
 *
 * 视觉效果：
 * - 完整组合有金色边框发光效果
 * - 不完整组合显示普通颜色背景
 * - 颜色背景与CardColor定义一一对应
 *
 * JSON数据格式（来自GameState.PlayerState）：
 * {
 *   "propertyColorCounts": { "RED": 2, "GREEN": 3, ... },
 *   "completeSets": 1
 * }
 */
public class PropertySetPanel extends JPanel {

    /** 标签高度 */
    private static final int BADGE_H   = 22;
    /** 标签圆角半径 */
    private static final int BADGE_ARC = 6;
    /** 颜色圆点直径 */
    private static final int DOT_SIZE  = 10;
    /** 标签之间的水平间距 */
    private static final int H_GAP     = 4;

    /** 颜色背景映射表（与CardColor定义对应） */
    private static final Map<String, Color> BG_COLORS   = new HashMap<>();
    /** 颜色文字映射表（浅色背景用深色文字，深色背景用白色文字） */
    private static final Map<String, Color> TEXT_COLORS = new HashMap<>();

    static {
        // 颜色背景
        BG_COLORS.put("BROWN",       new Color(0x8B5E3C));
        BG_COLORS.put("LIGHT_BLUE",  new Color(0x87CEEB));
        BG_COLORS.put("PINK",        new Color(0xFF69B4));
        BG_COLORS.put("ORANGE",      new Color(0xFF8C00));
        BG_COLORS.put("RED",         new Color(0xDC143C));
        BG_COLORS.put("YELLOW",      new Color(0xFFD700));
        BG_COLORS.put("GREEN",       new Color(0x228B22));
        BG_COLORS.put("BLUE",        new Color(0x00008B));
        BG_COLORS.put("PURPLE",      new Color(0x6A0DAD));
        BG_COLORS.put("BLACK",       new Color(0x2B2B2B));
        BG_COLORS.put("LIGHT_GREEN", new Color(0x90EE90));

        // 浅色背景对应深色文字，深色背景对应白色文字
        TEXT_COLORS.put("LIGHT_BLUE",  new Color(0x1A1A1A));
        TEXT_COLORS.put("YELLOW",      new Color(0x1A1A1A));
        TEXT_COLORS.put("LIGHT_GREEN", new Color(0x1A1A1A));
    }

    /** 每种颜色所需的卡牌数（与CardColor.getSetSize()一致） */
    private static final Map<String, Integer> SET_SIZES = new HashMap<>();

    static {
        SET_SIZES.put("BROWN",       2);
        SET_SIZES.put("LIGHT_BLUE",  3);
        SET_SIZES.put("PINK",        3);
        SET_SIZES.put("ORANGE",      3);
        SET_SIZES.put("RED",         3);
        SET_SIZES.put("YELLOW",      3);
        SET_SIZES.put("GREEN",       3);
        SET_SIZES.put("BLUE",        2);
        SET_SIZES.put("PURPLE",      3);
        SET_SIZES.put("BLACK",       4);
        SET_SIZES.put("LIGHT_GREEN", 3);
    }

    /** 各地产颜色的当前卡牌数量 */
    private final Map<String, Integer> colorCounts = new HashMap<>();

    /** 各地产颜色是否有房屋 */
    private final Map<String, Boolean> hasHouse = new HashMap<>();

    /** 各地产颜色是否有酒店 */
    private final Map<String, Boolean> hasHotel = new HashMap<>();

    /** 构造函数 - 初始化透明背景的固定高度面板 */
    public PropertySetPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(300, BADGE_H + 6));
        setMinimumSize(new Dimension(0, BADGE_H + 6));
    }

    /**
     * 根据服务器JSON数据更新标签状态
     * 由PlayerPanel.updateFromJson()调用
     *
     * @param playerData 单个玩家的JSON数据
     */
    public void updateFromJson(JsonObject playerData) {
        colorCounts.clear();
        hasHouse.clear();
        hasHotel.clear();

        // 解析各颜色地产卡数量
        if (playerData.has("propertyColorCounts")) {
            JsonObject counts = playerData.getAsJsonObject("propertyColorCounts");
            for (String key : counts.keySet()) {
                int count = counts.get(key).getAsInt();
                if (count > 0) {
                    colorCounts.put(key, count);
                }
            }
        }

        // 解析房屋信息（可选字段，后端可能尚未提供）
        if (playerData.has("houseColors")) {
            JsonObject houses = playerData.getAsJsonObject("houseColors");
            for (String key : houses.keySet()) {
                if (houses.get(key).getAsBoolean()) hasHouse.put(key, true);
            }
        }

        // 解析酒店信息（可选字段）
        if (playerData.has("hotelColors")) {
            JsonObject hotels = playerData.getAsJsonObject("hotelColors");
            for (String key : hotels.keySet()) {
                if (hotels.get(key).getAsBoolean()) hasHotel.put(key, true);
            }
        }

        repaint();
    }

    /**
     * 自定义绘制 - 水平排列颜色标签
     * 每个标签包含：颜色圆点 + 数量文本（当前/所需）+ 房屋/酒店图标
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (colorCounts.isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int x   = 2;
        int top = (getHeight() - BADGE_H) / 2;  // 垂直居中

        for (Map.Entry<String, Integer> entry : colorCounts.entrySet()) {
            String colorKey = entry.getKey();
            int    count    = entry.getValue();
            int    required = SET_SIZES.getOrDefault(colorKey, 3);
            boolean complete = count >= required;
            boolean house    = hasHouse.getOrDefault(colorKey, false);
            boolean hotel    = hasHotel.getOrDefault(colorKey, false);

            // 构建标签文本：数量/需求 + 房屋/酒店图标
            String countText = count + "/" + required;
            String extraText = hotel ? " H" : house ? " h" : "";
            String fullText  = countText + extraText;

            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int textW  = fm.stringWidth(fullText);
            int badgeW = DOT_SIZE + 4 + textW + 10;  // 圆点 + 间距 + 文本 + 内边距

            Color bg = BG_COLORS.getOrDefault(colorKey, Color.GRAY);

            // 完整组合绘制金色发光外边框
            if (complete) {
                g2.setColor(new Color(255, 215, 0, 80));
                g2.setStroke(new BasicStroke(3f));
                g2.draw(new RoundRectangle2D.Float(
                        x - 1, top - 1, badgeW + 2, BADGE_H + 2,
                        BADGE_ARC + 2, BADGE_ARC + 2));
            }

            // 标签背景
            g2.setPaint(bg);
            g2.fill(new RoundRectangle2D.Float(x, top, badgeW, BADGE_H, BADGE_ARC, BADGE_ARC));

            // 标签边框（完整=金色，不完整=深色）
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(complete ? new Color(255, 215, 0) : bg.darker());
            g2.draw(new RoundRectangle2D.Float(
                    x + 0.6f, top + 0.6f, badgeW - 1.2f, BADGE_H - 1.2f,
                    BADGE_ARC, BADGE_ARC));

            // 颜色圆点
            int dotX = x + 5;
            int dotY = top + (BADGE_H - DOT_SIZE) / 2;
            g2.setColor(bg.brighter());
            g2.fillOval(dotX, dotY, DOT_SIZE, DOT_SIZE);
            g2.setColor(bg.darker().darker());
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawOval(dotX, dotY, DOT_SIZE, DOT_SIZE);

            // 数量文本
            Color textColor = TEXT_COLORS.getOrDefault(colorKey, Color.WHITE);
            g2.setColor(textColor);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            int tx = dotX + DOT_SIZE + 4;
            int ty = top + (BADGE_H + fm.getAscent() - fm.getDescent()) / 2 - 1;
            g2.drawString(fullText, tx, ty);

            x += badgeW + H_GAP;

            // 超出面板宽度时停止绘制
            if (x + 40 > getWidth()) break;
        }

        g2.dispose();
    }
}
