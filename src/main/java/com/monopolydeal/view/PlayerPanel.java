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

    private static final Color[] PROPERTY_COLORS = {
            new Color(139, 90, 43),   // BROWN
            new Color(135, 206, 235), // LIGHT_BLUE
            new Color(255, 105, 180), // PINK
            new Color(255, 140, 0),   // ORANGE
            new Color(220, 20, 60),   // RED
            new Color(255, 215, 0),   // YELLOW
            new Color(34, 139, 34),   // GREEN
            new Color(0, 0, 139),     // BLUE
            new Color(106, 13, 173),  // PURPLE
            new Color(43, 43, 43),    // BLACK
            new Color(144, 238, 144), // LIGHT_GREEN
    };

    private static final Map<String, Color> COLOR_MAP = new LinkedHashMap<>();
    static {
        COLOR_MAP.put("BROWN", new Color(139, 90, 43));
        COLOR_MAP.put("LIGHT_BLUE", new Color(135, 206, 235));
        COLOR_MAP.put("PINK", new Color(255, 105, 180));
        COLOR_MAP.put("ORANGE", new Color(255, 140, 0));
        COLOR_MAP.put("RED", new Color(220, 20, 60));
        COLOR_MAP.put("YELLOW", new Color(255, 215, 0));
        COLOR_MAP.put("GREEN", new Color(34, 139, 34));
        COLOR_MAP.put("BLUE", new Color(0, 0, 139));
        COLOR_MAP.put("PURPLE", new Color(106, 13, 173));
        COLOR_MAP.put("BLACK", new Color(43, 43, 43));
        COLOR_MAP.put("LIGHT_GREEN", new Color(144, 238, 144));
    }

    public PlayerPanel(String playerId) {
        this.playerId = playerId;
        this.propertyGroupPanels = new LinkedHashMap<>();
        this.currentPropertyCounts = new LinkedHashMap<>();
        setLayout(new BorderLayout(15, 0));
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 65, 75)),
                new EmptyBorder(12, 15, 12, 15)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));
        createLeftInfo();
        createPropertyArea();
    }

    private void createLeftInfo() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(180, 0));

        nicknameLabel = new JLabel("Player");
        nicknameLabel.setForeground(Color.WHITE);
        nicknameLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));

        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));

        bankTotalLabel = new JLabel("Bank: 0M");
        bankTotalLabel.setForeground(new Color(255, 215, 0));
        bankTotalLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        setsLabel = new JLabel("Sets: 0");
        setsLabel.setForeground(new Color(100, 255, 100));
        setsLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        handCountLabel = new JLabel("Hand: 0 cards");
        handCountLabel.setForeground(new Color(180, 180, 180));
        handCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

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

    private void createPropertyArea() {
        propertyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        propertyPanel.setOpaque(false);
        add(propertyPanel, BorderLayout.CENTER);
    }

    public void updateFromJson(JsonObject data, Map<String, Integer> propertyColorCounts) {
        String nickname = data.has("nickname") ? data.get("nickname").getAsString() : "Player";
        boolean isActive = data.has("isActive") && data.get("isActive").getAsBoolean();
        boolean connected = !data.has("connected") || data.get("connected").getAsBoolean();
        int bankTotal = data.has("bankTotal") ? data.get("bankTotal").getAsInt() : 0;
        int completeSets = data.has("completeSets") ? data.get("completeSets").getAsInt() : 0;
        int handCount = data.has("handCount") ? data.get("handCount").getAsInt() : 0;

        nicknameLabel.setText(nickname);

        if (!connected) {
            statusLabel.setText("● Disconnected");
            statusLabel.setForeground(Color.RED);
        } else if (isActive) {
            statusLabel.setText("● Current Turn");
            statusLabel.setForeground(new Color(255, 215, 0));
        } else {
            statusLabel.setText("● Waiting");
            statusLabel.setForeground(new Color(150, 150, 150));
        }

        bankTotalLabel.setText(" Bank: " + bankTotal + "M");
        setsLabel.setText(" Sets: " + completeSets + "/3");
        handCountLabel.setText(" Hand: " + handCount + " cards");

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

    private void updatePropertyDisplay(Map<String, Integer> colorCounts) {
        if (colorCounts == null) colorCounts = new LinkedHashMap<>();

        for (String color : colorCounts.keySet()) {
            currentPropertyCounts.put(color, colorCounts.get(color));
        }
        Map<String, Integer> finalColorCounts = colorCounts;
        currentPropertyCounts.keySet().removeIf(k -> !finalColorCounts.containsKey(k));

        propertyPanel.removeAll();

        for (Map.Entry<String, Integer> entry : colorCounts.entrySet()) {
            String colorName = entry.getKey();
            int count = entry.getValue();
            Color color = COLOR_MAP.getOrDefault(colorName, Color.GRAY);

            JPanel pilePanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    for (int i = count - 1; i >= 0; i--) {
                        int offsetX = i * 3;
                        int offsetY = i * 3;
                        g2.setColor(new Color(0, 0, 0, 80));
                        g2.fillRoundRect(offsetX + 1, offsetY + 1, 44, 56, 8, 8);
                        g2.setColor(color);
                        g2.fillRoundRect(offsetX, offsetY, 44, 56, 8, 8);
                        g2.setColor(new Color(255, 255, 255, 60));
                        g2.fillRoundRect(offsetX + 3, offsetY + 3, 38, 20, 6, 6);
                        g2.setColor(color.darker());
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.drawRoundRect(offsetX, offsetY, 44, 56, 8, 8);
                    }
                    if (count > 0) {
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
                        FontMetrics fm = g2.getFontMetrics();
                        String text = count + "";
                        int maxOffset = (count - 1) * 3;
                        g2.drawString(text, maxOffset + (44 - fm.stringWidth(text)) / 2, maxOffset + 35);
                    }
                    g2.dispose();
                }
            };
            pilePanel.setOpaque(false);
            pilePanel.setPreferredSize(new Dimension(50, 64));
            pilePanel.setToolTipText(colorName + ": " + count);
            propertyPanel.add(pilePanel);
        }

        propertyPanel.revalidate();
        propertyPanel.repaint();
    }

    public String getPlayerId() {
        return playerId;
    }
}