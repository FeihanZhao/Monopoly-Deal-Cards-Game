package com.monopolydeal.view;

import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.Map;

/**
 * Player panel — displays a single player's status information.
 *
 * In GamePanel, each player gets one PlayerPanel, arranged horizontally, showing:
 * 1. Left info area (180px wide) — nickname, online status, bank balance, complete sets, hand count
 * 2. Right property display area — stacked cards showing property counts by color
 *
 * Visual highlights:
 * - Active player's panel has a gold left-border highlight
 * - Disconnected players show a red "Disconnected" status
 * - Property cards are grouped and stacked by color, with colors matching CardColor definitions
 *
 * Color mapping:
 * Each property color has a corresponding RGB value used to draw colored cards in the display area.
 */
public class PlayerPanel extends JPanel {
    /** Player unique identifier */
    private final String playerId;
    /** Nickname label */
    private JLabel nicknameLabel;
    /** Status label (online/active/disconnected) */
    private JLabel statusLabel;
    /** Bank balance label */
    private JLabel bankTotalLabel;
    /** Complete property sets label */
    private JLabel setsLabel;
    /** Hand count label */
    private JLabel handCountLabel;
    /** Property display panel */
    private JPanel propertyPanel;
    /** Property group panel map key=color name, value=stack drawing panel */
    private Map<String, JPanel> propertyGroupPanels;
    /** Current property card counts per color (cached) */
    private Map<String, Integer> currentPropertyCounts;

    /**
     * Constructor — create the player panel UI layout.
     * @param playerId player unique identifier
     */
    public PlayerPanel(String playerId) {
        this.playerId = playerId;
        this.propertyGroupPanels = new LinkedHashMap<>();
        this.currentPropertyCounts = new LinkedHashMap<>();

        setLayout(new BorderLayout(15, 0));
        setOpaque(false);
        // Default bottom gray separator line
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 65, 75)),
                new EmptyBorder(12, 15, 12, 15)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 160));

        createLeftInfo();      // Create left info area
        createPropertyArea();  // Create right property display area
    }

    /** Create left info area — nickname, status, balance, sets, hand count */
    private void createLeftInfo() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(180, 0));

        // Nickname (white bold)
        nicknameLabel = new JLabel("Player");
        nicknameLabel.setForeground(Color.WHITE);
        nicknameLabel.setFont(new Font("SansSerif", Font.BOLD, 15));

        // Status indicator (online/active/disconnected)
        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));

        // Bank balance (gold)
        bankTotalLabel = new JLabel("Bank: 0M");
        bankTotalLabel.setForeground(new Color(255, 215, 0));
        bankTotalLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        // Complete sets (green)
        setsLabel = new JLabel("Sets: 0");
        setsLabel.setForeground(new Color(100, 255, 100));
        setsLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        // Hand count (gray)
        handCountLabel = new JLabel("Hand: 0 cards");
        handCountLabel.setForeground(new Color(180, 180, 180));
        handCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        // Vertical layout with spacing
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

    /** Create right property display area — horizontally arranged stacked cards per color */
    private void createPropertyArea() {
        propertyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        propertyPanel.setOpaque(false);
        add(propertyPanel, BorderLayout.CENTER);
    }

    /**
     * Update the player panel display from JSON data.
     * Called by GamePanel.updatePlayerPanelsFromStates() on every GAME_STATE_UPDATE.
     *
     * @param data simplified player JSON data
     * @param propertyColorCounts property card counts per color
     */
    public void updateFromJson(JsonObject data, Map<String, Integer> propertyColorCounts) {
        // Parse JSON fields
        String nickname = data.has("nickname") ? data.get("nickname").getAsString() : "Player";
        boolean isActive = data.has("isActive") && data.get("isActive").getAsBoolean();
        boolean connected = !data.has("connected") || data.get("connected").getAsBoolean();
        int bankTotal = data.has("bankTotal") ? data.get("bankTotal").getAsInt() : 0;
        int completeSets = data.has("completeSets") ? data.get("completeSets").getAsInt() : 0;
        int handCount = data.has("handCount") ? data.get("handCount").getAsInt() : 0;

        // Update display info
        nicknameLabel.setText(nickname);

        // Status indicator
        if (!connected) {
            statusLabel.setText("Disconnected");
            statusLabel.setForeground(Color.RED);
        } else if (isActive) {
            statusLabel.setText("Active");
            statusLabel.setForeground(new Color(255, 215, 0));  // Gold
        } else {
            statusLabel.setText("Waiting");
            statusLabel.setForeground(new Color(150, 150, 150));
        }

        bankTotalLabel.setText("Bank: " + bankTotal + "M");
        setsLabel.setText("Sets: " + completeSets + "/3");
        handCountLabel.setText("Hand: " + handCount + " cards");

        // Active player gets a gold left-border highlight
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
     * Update the property display area.
     * Creates a stacked card panel for each color that has cards.
     *
     * @param colorCounts color name → card count
     */
    private void updatePropertyDisplay(Map<String, Integer> colorCounts) {
        if (colorCounts == null) colorCounts = new LinkedHashMap<>();

        // Update cache
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

            // Create custom-drawn stacked card panel
            JPanel pilePanel = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);

                    // Draw cards from back to front (each offset by 3px for stack effect)
                    for (int i = count - 1; i >= 0; i--) {
                        int offsetX = i * 3;
                        int offsetY = i * 3;

                        // Card shadow
                        g2.setColor(new Color(0, 0, 0, 80));
                        g2.fillRoundRect(offsetX + 1, offsetY + 1, 44, 56, 8, 8);

                        // Card body (corresponding color)
                        g2.setColor(color);
                        g2.fillRoundRect(offsetX, offsetY, 44, 56, 8, 8);

                        // Card highlight (semi-transparent white bar at top)
                        g2.setColor(new Color(255, 255, 255, 60));
                        g2.fillRoundRect(offsetX + 3, offsetY + 3, 38, 20, 6, 6);

                        // Card border
                        g2.setColor(color.darker());
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.drawRoundRect(offsetX, offsetY, 44, 56, 8, 8);
                    }

                    // Draw count text on top layer
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
            pilePanel.setToolTipText(colorName + ": " + count + " cards");
            propertyPanel.add(pilePanel);
        }

        propertyPanel.revalidate();
        propertyPanel.repaint();
    }

    /** Get player ID */
    public String getPlayerId() {
        return playerId;
    }
}
