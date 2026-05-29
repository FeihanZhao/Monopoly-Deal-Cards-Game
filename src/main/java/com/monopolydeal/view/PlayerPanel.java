package com.monopolydeal.view;

import com.google.gson.JsonObject;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
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
    /** Set completion display panel */
    private JPanel setBadgePanel;
    /** Local player ID (for showing "Your Turn") */
    private String localPlayerId;
    /** Active player pulse animation */
    private float activePulse;
    private javax.swing.Timer pulseTimer;
    /** Whether currently active */
    private boolean isCurrentlyActive;

    /**
     * Constructor — create the player panel UI layout.
     * @param playerId player unique identifier
     */
    public PlayerPanel(String playerId) {
        this.playerId = playerId;
        this.propertyGroupPanels = new LinkedHashMap<>();
        this.currentPropertyCounts = new LinkedHashMap<>();
        this.activePulse = 0f;
        this.isCurrentlyActive = false;

        setLayout(new BorderLayout(15, 0));
        setOpaque(false);
        // Default bottom gray separator line
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 65, 75)),
                new EmptyBorder(12, 15, 12, 15)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));

        createLeftInfo();      // Create left info area
        createPropertyArea();  // Create right property display area
    }

    /** Set local player ID for "Your Turn" badge */
    public void setLocalPlayerId(String id) {
        this.localPlayerId = id;
    }

    /** Create left info area — nickname, status, balance, sets, hand count */
    private void createLeftInfo() {
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(180, 0));

        // Nickname (white bold, larger)
        nicknameLabel = new JLabel("Player");
        nicknameLabel.setForeground(AppTheme.TEXT_PRIMARY);
        nicknameLabel.setFont(new Font(AppTheme.FONT_MAIN, Font.BOLD, 16));

        // Status indicator (online/active/disconnected)
        statusLabel = new JLabel("");
        statusLabel.setFont(new Font(AppTheme.FONT_MAIN, Font.PLAIN, 10));

        // Bank balance (gold)
        bankTotalLabel = new JLabel("Bank: 0M");
        bankTotalLabel.setForeground(AppTheme.GOLD);
        bankTotalLabel.setFont(new Font(AppTheme.FONT_MAIN, Font.BOLD, 13));

        // Complete sets badge row
        JPanel setsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        setsRow.setOpaque(false);

        setsLabel = new JLabel("Sets: 0");
        setsLabel.setForeground(AppTheme.GREEN_GLOW);
        setsLabel.setFont(new Font(AppTheme.FONT_MAIN, Font.BOLD, 12));
        setsRow.add(setsLabel);

        setBadgePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        setBadgePanel.setOpaque(false);
        setsRow.add(setBadgePanel);

        // Hand count (gray)
        handCountLabel = new JLabel("Hand: 0 cards");
        handCountLabel.setForeground(AppTheme.TEXT_DIM);
        handCountLabel.setFont(new Font(AppTheme.FONT_MAIN, Font.PLAIN, 11));

        // Vertical layout with spacing
        leftPanel.add(nicknameLabel);
        leftPanel.add(Box.createVerticalStrut(2));
        leftPanel.add(statusLabel);
        leftPanel.add(Box.createVerticalStrut(6));
        leftPanel.add(bankTotalLabel);
        leftPanel.add(Box.createVerticalStrut(3));
        leftPanel.add(setsRow);
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
            statusLabel.setText("✕ Disconnected");
            statusLabel.setForeground(AppTheme.RED_DANGER);
        } else if (isActive) {
            statusLabel.setText("▶ Active");
            statusLabel.setForeground(AppTheme.GOLD);
        } else {
            statusLabel.setText("○ Waiting");
            statusLabel.setForeground(AppTheme.TEXT_DIM);
        }

        bankTotalLabel.setText("Bank: " + bankTotal + "M");
        setsLabel.setText("Sets: " + completeSets + "/3");
        handCountLabel.setText("Hand: " + handCount + " cards");

        // Animated active border with pulse
        if (isActive != isCurrentlyActive) {
            isCurrentlyActive = isActive;
            if (isActive) {
                pulseTimer = new javax.swing.Timer(50, e -> {
                    activePulse += 0.08f;
                    repaint();
                });
                pulseTimer.start();
            } else if (pulseTimer != null) {
                pulseTimer.stop();
                activePulse = 0f;
                repaint();
            }
        }

        if (isActive) {
            float pulse = (float)Math.sin(activePulse) * 0.4f + 0.6f;
            int alpha = (int)(255 * pulse);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 1, 0, new Color(255, 215, 0, alpha)),
                    new EmptyBorder(12, 12, 12, 15)));
        } else {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 65, 75)),
                    new EmptyBorder(12, 15, 12, 15)));
        }

        // Update set completion badges
        updateSetBadges(propertyColorCounts);

        updatePropertyDisplay(propertyColorCounts);
        revalidate();
        repaint();
    }

    /** Update set completion badges — colored dots shown below the sets label */
    private void updateSetBadges(Map<String, Integer> colorCounts) {
        setBadgePanel.removeAll();
        if (colorCounts == null) return;

        // Determine which color groups are complete (count >= required)
        // Display as small colored circles with checkmark for complete sets
        for (Map.Entry<String, Integer> entry : colorCounts.entrySet()) {
            String colorName = entry.getKey();
            int count = entry.getValue();
            // Heuristic: a set is "complete" if count >= 2 for most colors, >=3 for some
            // For display purposes, just show what they have
            Color c = AppTheme.PROPERTY_COLORS.getOrDefault(colorName, Color.GRAY);
            JLabel dot = new JLabel("●");
            dot.setFont(new Font(AppTheme.FONT_MAIN, Font.BOLD, 10));
            dot.setForeground(c);
            dot.setToolTipText(colorName + ": " + count + " card(s)");
            setBadgePanel.add(dot);
        }
        setBadgePanel.revalidate();
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
                    int pw = 50, ph = 62;
                    for (int i = count - 1; i >= 0; i--) {
                        int offsetX = i * 3;
                        int offsetY = i * 3;

                        // Card shadow
                        g2.setColor(AppTheme.SHADOW);
                        g2.fillRoundRect(offsetX + 1, offsetY + 1, pw, ph, 8, 8);

                        // Card body (corresponding color)
                        g2.setColor(color);
                        g2.fillRoundRect(offsetX, offsetY, pw, ph, 8, 8);

                        // Gradient shine overlay on top
                        g2.setPaint(new GradientPaint(
                            0, offsetY, new Color(255, 255, 255, 50),
                            0, offsetY + ph / 3, new Color(255, 255, 255, 0)));
                        g2.fillRoundRect(offsetX, offsetY, pw, ph, 8, 8);

                        // Card highlight strip (glass effect at top)
                        g2.setColor(new Color(255, 255, 255, 40));
                        g2.fillRoundRect(offsetX + 3, offsetY + 2, pw - 6, 15, 6, 6);

                        // Card border
                        g2.setColor(color.darker());
                        g2.setStroke(new BasicStroke(1.0f));
                        g2.drawRoundRect(offsetX, offsetY, pw, ph, 8, 8);
                    }

                    // Draw count text on top layer
                    if (count > 0) {
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font(AppTheme.FONT_MAIN, Font.BOLD, 10));
                        FontMetrics fm = g2.getFontMetrics();
                        String text = count + "";
                        int maxOffset = (count - 1) * 3;
                        g2.drawString(text, maxOffset + (pw - fm.stringWidth(text)) / 2,
                                maxOffset + 38);
                    }
                    g2.dispose();
                }
            };
            pilePanel.setOpaque(false);
            pilePanel.setPreferredSize(new Dimension(56, 70));
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
