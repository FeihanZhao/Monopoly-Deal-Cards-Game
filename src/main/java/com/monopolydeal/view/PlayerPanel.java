package com.monopolydeal.view;

import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.Map;

/**
 * 玩家面板 - 显示单个玩家的状态信息
 *
 * 在GamePanel中每个玩家对应一个PlayerPanel，横向排列显示：
 * 1. 左侧信息区（180px宽）- 昵称、在线状态、银行余额、完整组合数、手牌数
 * 2. 右侧地产展示区 - 以叠放卡片形式展示各颜色的地产数量
 *
 * 视觉特点：
 * - 活跃玩家面板左侧有金色边框高亮
 * - 断线玩家显示红色"已断线"状态
 * - 地产卡按颜色分组叠放显示，颜色与CardColor定义一致
 *
 * 颜色映射：
 * 每种地产颜色都有对应的RGB值，用于在地产展示区中绘制彩色卡片
 */
public class PlayerPanel extends JPanel {
    /** 玩家唯一标识符 */
    private final String playerId;
    /** 昵称标签 */
    private JLabel nicknameLabel;
    /** 状态标签（在线/活跃/断线） */
    private JLabel statusLabel;
    /** 银行余额标签 */
    private JLabel bankTotalLabel;
    /** 完整地产组合数标签 */
    private JLabel setsLabel;
    /** 手牌数量标签 */
    private JLabel handCountLabel;
    /** 地产展示面板 */
    private JPanel propertyPanel;
    /** 地产分组面板映射表 key=颜色名称, value=叠放绘制面板 */
    private Map<String, JPanel> propertyGroupPanels;
    /** 当前各地产颜色的卡牌数量缓存 */
    private Map<String, Integer> currentPropertyCounts;

    /**
     * 构造函数 - 创建玩家面板的UI布局
     * @param playerId 玩家唯一标识符
     */
    public PlayerPanel(String playerId) {
        this.playerId = playerId;
        this.propertyGroupPanels = new LinkedHashMap<>();
        this.currentPropertyCounts = new LinkedHashMap<>();

        setLayout(new BorderLayout(15, 0));
        setOpaque(false);
        // 默认底部灰色分隔线
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 65, 75)),
                new EmptyBorder(12, 15, 12, 15)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        createLeftInfo();      // 创建左侧信息区
        createPropertyArea();  // 创建右侧地产展示区
    }

    /** 创建左侧信息区 - 包含昵称、状态、余额、组合数、手牌数 */
    private void createLeftInfo() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(180, 0));

        // 昵称（白色加粗）
        nicknameLabel = new JLabel("玩家");
        nicknameLabel.setForeground(Color.WHITE);
        nicknameLabel.setFont(new Font("SansSerif", Font.BOLD, 15));

        // 状态指示器（在线/活跃/断线）
        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));

        // 银行余额（金色）
        bankTotalLabel = new JLabel("银行: 0M");
        bankTotalLabel.setForeground(new Color(255, 215, 0));
        bankTotalLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        // 完整组合数（绿色）
        setsLabel = new JLabel("组合: 0");
        setsLabel.setForeground(new Color(100, 255, 100));
        setsLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        // 手牌数量（灰色）
        handCountLabel = new JLabel("手牌: 0 张");
        handCountLabel.setForeground(new Color(180, 180, 180));
        handCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        // 垂直排列各标签（带间距）
        leftPanel.add(nicknameLabel);
        leftPanel.add(Box.createVerticalStrut(2));
        leftPanel.add(statusLabel);
        leftPanel.add(Box.createVerticalStrut(8));
        leftPanel.add(bankTotalLabel);
        leftPanel.add(Box.createVerticalStrut(3));
        leftPanel.add(setsLabel);
        leftPanel.add(Box.createVerticalStrut(3));
        leftPanel.add(handCountLabel);

        add(leftPanel, BorderLayout.WEST);
    }

    /** 创建右侧地产展示区 - 水平排列各颜色的地产叠放卡片 */
    private void createPropertyArea() {
        propertyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        propertyPanel.setOpaque(false);
        add(propertyPanel, BorderLayout.CENTER);
    }

    /**
     * 根据JSON数据更新玩家面板显示
     * 由GamePanel.updatePlayerPanelsFromStates()在每个GAME_STATE_UPDATE中调用
     *
     * @param data 简化的玩家JSON数据
     * @param propertyColorCounts 各颜色地产卡数量映射
     */
    public void updateFromJson(JsonObject data, Map<String, Integer> propertyColorCounts) {
        // 解析JSON字段
        String nickname = data.has("nickname") ? data.get("nickname").getAsString() : "玩家";
        boolean isActive = data.has("isActive") && data.get("isActive").getAsBoolean();
        boolean connected = !data.has("connected") || data.get("connected").getAsBoolean();
        int bankTotal = data.has("bankTotal") ? data.get("bankTotal").getAsInt() : 0;
        int completeSets = data.has("completeSets") ? data.get("completeSets").getAsInt() : 0;
        int handCount = data.has("handCount") ? data.get("handCount").getAsInt() : 0;

        // 更新显示信息
        nicknameLabel.setText(nickname);

        // 状态指示器
        if (!connected) {
            statusLabel.setText("已断线");
            statusLabel.setForeground(Color.RED);
        } else if (isActive) {
            statusLabel.setText("当前回合");
            statusLabel.setForeground(new Color(255, 215, 0));  // 金色
        } else {
            statusLabel.setText("等待中");
            statusLabel.setForeground(new Color(150, 150, 150));
        }

        bankTotalLabel.setText("银行: " + bankTotal + "M");
        setsLabel.setText("组合: " + completeSets + "/3");
        handCountLabel.setText("手牌: " + handCount + " 张");

        // 活跃玩家左侧金色高亮边框
        if (isActive) {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 1, 0, new Color(255, 215, 0)),
                    new EmptyBorder(12, 12, 12, 15)));
        } else {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 65, 75)),
                    new EmptyBorder(12, 15, 12, 15)));
        }

        updatePropertyDisplay(propertyColorCounts);
        revalidate();
        repaint();
    }

    /**
     * 更新地产展示区域
     * 为每种有卡牌的颜色创建叠放卡片面板
     *
     * @param colorCounts 颜色名称 → 卡牌数量
     */
    private void updatePropertyDisplay(Map<String, Integer> colorCounts) {
        if (colorCounts == null) colorCounts = new LinkedHashMap<>();

        // 更新缓存
        for (String color : colorCounts.keySet()) {
            currentPropertyCounts.put(color, colorCounts.get(color));
        }
        Map<String, Integer> finalColorCounts = colorCounts;
        currentPropertyCounts.keySet().removeIf(k -> !finalColorCounts.containsKey(k));

        propertyPanel.removeAll();

        for (Map.Entry<String, Integer> entry : colorCounts.entrySet()) {
            String colorName = entry.getKey();
            int count = entry.getValue();
            Color color = AppTheme.PROPERTY_COLORS.getOrDefault(colorName, Color.GRAY);

            // 创建自定义绘制的叠放卡片面板
            JPanel pilePanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

                    // 从后向前绘制每张卡片（后面的卡片偏移3px做叠放效果）
                    for (int i = count - 1; i >= 0; i--) {
                        int offsetX = i * 3;
                        int offsetY = i * 3;

                        // 卡片阴影
                        g2.setColor(new Color(0, 0, 0, 80));
                        g2.fillRoundRect(offsetX + 1, offsetY + 1, 44, 56, 8, 8);

                        // 卡片主体（对应颜色）
                        g2.setColor(color);
                        g2.fillRoundRect(offsetX, offsetY, 44, 56, 8, 8);

                        // 卡片高光（顶部白色半透明条）
                        g2.setColor(new Color(255, 255, 255, 60));
                        g2.fillRoundRect(offsetX + 3, offsetY + 3, 38, 20, 6, 6);

                        // 卡片边框
                        g2.setColor(color.darker());
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.drawRoundRect(offsetX, offsetY, 44, 56, 8, 8);
                    }

                    // 在最上层绘制数量文本
                    if (count > 0) {
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
                        FontMetrics fm = g2.getFontMetrics();
                        String text = count + "";
                        int maxOffset = (count - 1) * 3;
                        g2.drawString(text, maxOffset + (44 - fm.stringWidth(text)) / 2,
                                maxOffset + 35);
                    }
                    g2.dispose();
                }
            };
            pilePanel.setOpaque(false);
            pilePanel.setPreferredSize(new Dimension(50, 64));
            pilePanel.setToolTipText(colorName + ": " + count + "张");  // 鼠标悬停提示
            propertyPanel.add(pilePanel);
        }

        propertyPanel.revalidate();
        propertyPanel.repaint();
    }

    /** 获取玩家ID */
    public String getPlayerId() {
        return playerId;
    }
}
