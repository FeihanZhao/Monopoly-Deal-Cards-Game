package com.monopolydeal.view;

import com.google.gson.JsonObject;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.Map;

public class PlayerPanel extends JPanel {
    private final String playerId;
    private JLabel nicknameLabel;
    private JLabel statusLabel;
    private JLabel bankTotalLabel;
    private JLabel setsLabel;
    private JLabel handCountLabel;
    private JPanel propertyPanel;
    private Map<String, JPanel> propertyGroupPanels;
    private Map<String, Integer> currentPropertyCounts;

    // ========================= 高级配色系统 =========================
    private static final Map<String, Color> COLOR_MAP = new LinkedHashMap<>();
    static {
        COLOR_MAP.put("BROWN", new Color(150, 100, 50));
        COLOR_MAP.put("LIGHT_BLUE", new Color(140, 210, 255));
        COLOR_MAP.put("PINK", new Color(255, 110, 185));
        COLOR_MAP.put("ORANGE", new Color(255, 150, 20));
        COLOR_MAP.put("RED", new Color(230, 30, 70));
        COLOR_MAP.put("YELLOW", new Color(255, 225, 0));
        COLOR_MAP.put("GREEN", new Color(40, 150, 40));
        COLOR_MAP.put("BLUE", new Color(0, 0, 160));
        COLOR_MAP.put("PURPLE", new Color(110, 20, 180));
        COLOR_MAP.put("BLACK", new Color(50, 50, 50));
        COLOR_MAP.put("LIGHT_GREEN", new Color(150, 240, 150));
    }

    private static final Color BG_TRANSPARENT = new Color(0,0,0,0);
    private static final Color BORDER_NORMAL = new Color(70,75,90);
    private static final Color BORDER_ACTIVE = new Color(255,215,0);
    private static final Color GLOW_ACTIVE = new Color(255,215,0,60);
    private static final Color TEXT_MAIN = Color.WHITE;
    private static final Color TEXT_BANK = new Color(255,220,80);
    private static final Color TEXT_SETS = new Color(100,255,140);
    private static final Color TEXT_HAND = new Color(190,190,210);
    private static final Color SHADOW = new Color(0,0,0,100);

    public PlayerPanel(String playerId) {
        this.playerId = playerId;
        this.propertyGroupPanels = new LinkedHashMap<>();
        this.currentPropertyCounts = new LinkedHashMap<>();
        setLayout(new BorderLayout(18, 0));
        setOpaque(false);
        setBorder(createNormalBorder());
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));
        createLeftInfo();
        createPropertyArea();
    }

    // ========================= 左侧玩家信息（精致排版） =========================
    private void createLeftInfo() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(200, 0));

        nicknameLabel = new JLabel("Player");
        nicknameLabel.setForeground(TEXT_MAIN);
        nicknameLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));

        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        bankTotalLabel = new JLabel("Bank: 0M");
        bankTotalLabel.setForeground(TEXT_BANK);
        bankTotalLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));

        setsLabel = new JLabel("Sets: 0");
        setsLabel.setForeground(TEXT_SETS);
        setsLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        handCountLabel = new JLabel("Hand: 0 cards");
        handCountLabel.setForeground(TEXT_HAND);
        handCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        leftPanel.add(nicknameLabel);
        leftPanel.add(Box.createVerticalStrut(4));
        leftPanel.add(statusLabel);
        leftPanel.add(Box.createVerticalStrut(10));
        leftPanel.add(bankTotalLabel);
        leftPanel.add(Box.createVerticalStrut(4));
        leftPanel.add(setsLabel);
        leftPanel.add(Box.createVerticalStrut(4));
        leftPanel.add(handCountLabel);

        add(leftPanel, BorderLayout.WEST);
    }

    // ========================= 卡牌区域（3D堆叠） =========================
    private void createPropertyArea() {
        propertyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        propertyPanel.setOpaque(false);
        add(propertyPanel, BorderLayout.CENTER);
    }

    // ========================= 数据更新 =========================
    public void updateFromJson(JsonObject data, Map<String, Integer> propertyColorCounts) {
        String nickname = data.has("nickname") ? data.get("nickname").getAsString() : "Player";
        boolean isActive = data.has("isActive") && data.get("isActive").getAsBoolean();
        boolean connected = !data.has("connected") || data.get("connected").getAsBoolean();
        int bankTotal = data.has("bankTotal") ? data.get("bankTotal").getAsInt() : 0;
        int completeSets = data.has("completeSets") ? data.get("completeSets").getAsInt() : 0;
        int handCount = data.has("handCount") ? data.get("handCount").getAsInt() : 0;

        nicknameLabel.setText(nickname);

        // 状态样式强化
        if (!connected) {
            statusLabel.setText("● Disconnected");
            statusLabel.setForeground(new Color(255,60,60));
        } else if (isActive) {
            statusLabel.setText("● CURRENT TURN");
            statusLabel.setForeground(BORDER_ACTIVE);
        } else {
            statusLabel.setText("● Waiting");
            statusLabel.setForeground(new Color(160,160,180));
        }

        bankTotalLabel.setText("Bank: " + bankTotal + "M");
        setsLabel.setText("Sets: " + completeSets + "/3");
        handCountLabel.setText("Hand: " + handCount + " cards");

        // 边框发光效果
        setBorder(isActive ? createActiveBorder() : createNormalBorder());

        updatePropertyDisplay(propertyColorCounts);
        revalidate();
        repaint();
    }

    // ========================= 终极3D卡牌渲染 =========================
    private void updatePropertyDisplay(Map<String, Integer> colorCounts) {
        if (colorCounts == null) colorCounts = new LinkedHashMap<>();
        currentPropertyCounts.clear();
        currentPropertyCounts.putAll(colorCounts);

        propertyPanel.removeAll();

        for (Map.Entry<String, Integer> entry : colorCounts.entrySet()) {
            String colorName = entry.getKey();
            int count = entry.getValue();
            Color baseColor = COLOR_MAP.getOrDefault(colorName, Color.GRAY);

            JPanel pilePanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                    int cardW = 48;
                    int cardH = 62;
                    int shift = 3;

                    // 绘制阴影层
                    for (int i = count - 1; i >= 0; i--) {
                        int x = i * shift;
                        int y = i * shift;

                        // 投影
                        g2.setColor(SHADOW);
                        g2.fillRoundRect(x + 2, y + 2, cardW, cardH, 10, 10);
                    }

                    // 绘制卡牌主体
                    for (int i = count - 1; i >= 0; i--) {
                        int x = i * shift;
                        int y = i * shift;

                        // 卡牌底色
                        g2.setColor(baseColor);
                        g2.fillRoundRect(x, y, cardW, cardH, 10, 10);

                        // 高光
                        g2.setColor(new Color(255,255,255,50));
                        g2.fillRoundRect(x + 4, y + 4, cardW - 8, 22, 8, 8);

                        // 边框
                        g2.setColor(baseColor.brighter());
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.drawRoundRect(x, y, cardW, cardH, 10, 10);
                    }

                    // 数量文字
                    if (count > 0) {
                        int topX = (count - 1) * shift;
                        int topY = (count - 1) * shift;

                        g2.setColor(TEXT_MAIN);
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                        FontMetrics fm = g2.getFontMetrics();
                        String num = String.valueOf(count);
                        int tx = topX + (cardW - fm.stringWidth(num)) / 2;
                        int ty = topY + 38;
                        g2.drawString(num, tx, ty);
                    }

                    g2.dispose();
                }
            };

            pilePanel.setOpaque(false);
            pilePanel.setPreferredSize(new Dimension(58, 72));
            pilePanel.setToolTipText(colorName + " · " + count + " cards");
            propertyPanel.add(pilePanel);
        }

        propertyPanel.revalidate();
        propertyPanel.repaint();
    }

    // ========================= 边框样式 =========================
    private Border createNormalBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_NORMAL),
                new EmptyBorder(14, 16, 14, 16)
        );
    }

    private Border createActiveBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 1, 0, BORDER_ACTIVE),
                new EmptyBorder(14, 12, 14, 16)
        );
    }

    public String getPlayerId() {
        return playerId;
    }
}
