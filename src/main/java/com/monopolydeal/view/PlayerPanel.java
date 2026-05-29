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
 * Layout:
 * 1. Left: Avatar circle (with first letter of nickname) + info labels
 * 2. Right: Property card stacks grouped by color
 *
 * Visual highlights:
 * - Active player's panel has a gold left-border highlight
 * - Disconnected players show dimmed/red status
 * - Property cards are stacked and colored by their color
 */
public class PlayerPanel extends JPanel {
    /** Player unique identifier */
    private final String playerId;
    /** Avatar circle label (shows first letter of nickname) */
    private JLabel avatarLabel;
    /** Nickname label */
    private JLabel nicknameLabel;
    /** Status label */
    private JLabel statusLabel;
    /** Bank balance label */
    private JLabel bankTotalLabel;
    /** Complete property sets label */
    private JLabel setsLabel;
    /** Hand count label */
    private JLabel handCountLabel;
    /** Remaining plays label */
    private JLabel playsLabel;
    /** Property display panel */
    private JPanel propertyPanel;
    /** Current property card counts per color */
    private Map<String, Integer> currentPropertyCounts;
    /** Current nickname (for avatar) */
    private String currentNickname;

    /** Avatar size */
    private static final int AVATAR_SIZE = 44;

    /**
     * Constructor.
     * @param playerId player unique identifier
     */
    public PlayerPanel(String playerId) {
        this.playerId = playerId;
        this.currentPropertyCounts = new LinkedHashMap<>();
        this.currentNickname = "?";

        setLayout(new BorderLayout(12, 0));
        setOpaque(false);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 65, 75)),
                new EmptyBorder(10, 15, 10, 15)));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        createLeftInfo();
        createPropertyArea();
    }

    /** Create left info area — avatar + basic stats */
    private void createLeftInfo() {
        JPanel leftPanel = new JPanel(new BorderLayout(12, 0));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(220, 0));

        // ===== Avatar circle =====
        JPanel avatarContainer = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Avatar circle background with gradient
                String initial = currentNickname.substring(0, 1).toUpperCase();
                Color avatarColor = getAvatarColor(currentNickname);

                // Shadow
                g2.setColor(new Color(0, 0, 0, 50));
                g2.fillOval(2, 2, AVATAR_SIZE, AVATAR_SIZE);

                // Main circle
                g2.setPaint(new GradientPaint(0, 0, avatarColor.brighter(),
                        0, AVATAR_SIZE, avatarColor.darker()));
                g2.fillOval(0, 0, AVATAR_SIZE, AVATAR_SIZE);

                // Circle border
                g2.setColor(new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(0, 0, AVATAR_SIZE, AVATAR_SIZE);

                // Initial letter
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("SansSerif", Font.BOLD, 20));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (AVATAR_SIZE - fm.stringWidth(initial)) / 2;
                int ty = (AVATAR_SIZE + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(initial, tx, ty);

                g2.dispose();
            }
        };
        avatarContainer.setOpaque(false);
        avatarContainer.setPreferredSize(new Dimension(AVATAR_SIZE + 4, AVATAR_SIZE + 4));

        // ===== Text info =====
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        // Nickname + status row
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        nameRow.setOpaque(false);

        nicknameLabel = new JLabel("Player");
        nicknameLabel.setForeground(Color.WHITE);
        nicknameLabel.setFont(new Font("SansSerif", Font.BOLD, 15));

        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));

        nameRow.add(nicknameLabel);
        nameRow.add(Box.createHorizontalStrut(6));
        nameRow.add(statusLabel);

        // Stats row
        JPanel statsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        statsRow.setOpaque(false);

        bankTotalLabel = new JLabel("Bank: 0M");
        bankTotalLabel.setForeground(new Color(255, 215, 0));
        bankTotalLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        setsLabel = new JLabel("Sets: 0/3");
        setsLabel.setForeground(new Color(100, 255, 100));
        setsLabel.setFont(new Font("SansSerif", Font.BOLD, 12));

        handCountLabel = new JLabel("Hand: 0");
        handCountLabel.setForeground(new Color(180, 180, 180));
        handCountLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        playsLabel = new JLabel("");
        playsLabel.setForeground(new Color(255, 200, 100));
        playsLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        statsRow.add(bankTotalLabel);
        statsRow.add(setsLabel);
        statsRow.add(handCountLabel);
        statsRow.add(playsLabel);

        infoPanel.add(nameRow);
        infoPanel.add(Box.createVerticalStrut(4));
        infoPanel.add(statsRow);

        leftPanel.add(avatarContainer, BorderLayout.WEST);
        leftPanel.add(infoPanel, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.WEST);
    }

    /** Get a deterministic color based on player nickname */
    private Color getAvatarColor(String nickname) {
        int hash = nickname.hashCode();
        Color[] colors = {
                new Color(52, 152, 219),   // Blue
                new Color(46, 204, 113),   // Green
                new Color(155, 89, 182),   // Purple
                new Color(231, 76, 60),    // Red
                new Color(243, 156, 18),   // Orange
                new Color(26, 188, 156),   // Teal
                new Color(230, 126, 34),   // Pumpkin
                new Color(149, 165, 166),  // Gray
        };
        return colors[Math.abs(hash) % colors.length];
    }

    /** Create right property display area */
    private void createPropertyArea() {
        JPanel rightWrapper = new JPanel(new BorderLayout());
        rightWrapper.setOpaque(false);

        // "Properties" header
        JLabel propHeader = new JLabel("Properties");
        propHeader.setForeground(AppTheme.TEXT_DIM);
        propHeader.setFont(new Font("SansSerif", Font.PLAIN, 10));
        propHeader.setBorder(new EmptyBorder(0, 0, 3, 0));
        rightWrapper.add(propHeader, BorderLayout.NORTH);

        propertyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        propertyPanel.setOpaque(false);
        rightWrapper.add(propertyPanel, BorderLayout.CENTER);

        add(rightWrapper, BorderLayout.CENTER);
    }

    /**
     * Update the player panel display from JSON data.
     */
    public void updateFromJson(JsonObject data, Map<String, Integer> propertyColorCounts) {
        String nickname = data.has("nickname") ? data.get("nickname").getAsString() : "Player";
        boolean isActive = data.has("isActive") && data.get("isActive").getAsBoolean();
        boolean connected = !data.has("connected") || data.get("connected").getAsBoolean();
        int bankTotal = data.has("bankTotal") ? data.get("bankTotal").getAsInt() : 0;
        int completeSets = data.has("completeSets") ? data.get("completeSets").getAsInt() : 0;
        int handCount = data.has("handCount") ? data.get("handCount").getAsInt() : 0;
        int remainingPlays = data.has("remainingPlays") ? data.get("remainingPlays").getAsInt() : 0;

        currentNickname = nickname;

        nicknameLabel.setText(nickname);

        if (!connected) {
            statusLabel.setText("Disconnected");
            statusLabel.setForeground(Color.RED);
        } else if (isActive) {
            statusLabel.setText("Active");
            statusLabel.setForeground(new Color(255, 215, 0));
        } else {
            statusLabel.setText("Waiting");
            statusLabel.setForeground(new Color(150, 150, 150));
        }

        bankTotalLabel.setText("Bank: " + bankTotal + "M");
        setsLabel.setText("Sets: " + completeSets + "/3");
        handCountLabel.setText("Hand: " + handCount);

        if (remainingPlays > 0) {
            playsLabel.setText("Plays: " + remainingPlays);
            playsLabel.setVisible(true);
        } else {
            playsLabel.setVisible(false);
        }

        // Active player highlight
        if (isActive) {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 3, 1, 0, new Color(255, 215, 0)),
                    new EmptyBorder(10, 12, 10, 15)));
        } else {
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(60, 65, 75)),
                    new EmptyBorder(10, 15, 10, 15)));
        }

        updatePropertyDisplay(propertyColorCounts);
        revalidate();
        repaint();
    }

    /**
     * Update the property display area with stacked card visuals.
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

                    // Draw cards from back to front
                    for (int i = count - 1; i >= 0; i--) {
                        int offsetX = i * 3;
                        int offsetY = i * 3;

                        // Card shadow
                        g2.setColor(new Color(0, 0, 0, 80));
                        g2.fillRoundRect(offsetX + 1, offsetY + 1, 46, 58, 8, 8);

                        // Card body
                        g2.setColor(color);
                        g2.fillRoundRect(offsetX, offsetY, 46, 58, 8, 8);

                        // Card top highlight
                        g2.setColor(new Color(255, 255, 255, 50));
                        g2.fillRoundRect(offsetX + 3, offsetY + 3, 40, 18, 6, 6);

                        // Card border
                        g2.setColor(color.darker());
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.drawRoundRect(offsetX, offsetY, 46, 58, 8, 8);

                        // Small center highlight
                        if (i == 0) { // Top card only
                            g2.setColor(new Color(255, 255, 255, 20));
                            g2.setStroke(new BasicStroke(0.5f));
                            g2.drawRoundRect(offsetX + 8, offsetY + 24, 30, 16, 4, 4);
                        }
                    }

                    // Draw count badge on top layer
                    if (count > 0) {
                        int maxOffset = (count - 1) * 3;
                        // Count badge background
                        g2.setColor(new Color(0, 0, 0, 100));
                        g2.fillOval(maxOffset + 13, maxOffset + 22, 20, 20);
                        // Count number
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                        FontMetrics fm = g2.getFontMetrics();
                        String text = String.valueOf(count);
                        g2.drawString(text, maxOffset + 23 - fm.stringWidth(text) / 2,
                                maxOffset + 35);
                    }
                    g2.dispose();
                }
            };
            pilePanel.setOpaque(false);
            // Calculate size based on count (each card offset by 3px, max visible width)
            int maxOffset = Math.max(0, (count - 1) * 3);
            pilePanel.setPreferredSize(new Dimension(50 + maxOffset, 64 + maxOffset));
            pilePanel.setMinimumSize(pilePanel.getPreferredSize());
            pilePanel.setToolTipText(colorName + ": " + count + " cards");

            // Also show color name label below the pile
            JPanel pileWithLabel = new JPanel(new BorderLayout());
            pileWithLabel.setOpaque(false);
            pileWithLabel.add(pilePanel, BorderLayout.CENTER);

            JLabel colorLabel = new JLabel(colorName.substring(0, Math.min(4, colorName.length())));
            colorLabel.setForeground(AppTheme.TEXT_DIM);
            colorLabel.setFont(new Font("SansSerif", Font.PLAIN, 8));
            colorLabel.setHorizontalAlignment(SwingConstants.CENTER);
            pileWithLabel.add(colorLabel, BorderLayout.SOUTH);

            propertyPanel.add(pileWithLabel);
        }

        if (colorCounts.isEmpty()) {
            JLabel emptyLabel = new JLabel("No properties");
            emptyLabel.setForeground(AppTheme.TEXT_MUTED);
            emptyLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
            propertyPanel.add(emptyLabel);
        }

        propertyPanel.revalidate();
        propertyPanel.repaint();
    }

    /** Get player ID */
    public String getPlayerId() {
        return playerId;
    }
}
