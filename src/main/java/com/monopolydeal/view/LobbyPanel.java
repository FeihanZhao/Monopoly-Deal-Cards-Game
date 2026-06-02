package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import com.monopolydeal.client.GameClient;
import com.monopolydeal.model.GameConstants;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

/**
 * Lobby panel — room management interface shown before the game starts.
 *
 * UI style: luxury dark theme with rich animated gradient background,
 * decorative card fan art, glowing title, premium gradient buttons.
 */
public class LobbyPanel extends JPanel {
    private final GameClient client;

    private JTextField nicknameField;
    private JTextField roomCodeField;
    private JButton createRoomButton;
    private JButton joinRoomButton;
    private JButton readyButton;
    private JButton leaveButton;
    private JButton startGameButton;
    private boolean amICreator = false;
    private JList<String> playerList;
    private DefaultListModel<String> playerListModel;
    private JLabel roomCodeLabel;
    private JLabel statusLabel;
    private JPanel roomPanel;
    private JPanel loginPanel;

    private boolean isInRoom;
    private boolean isReady;

    /** Decorative card positions for background art */
    private static final double[][] CARD_POSITIONS = {
            {0.08, 0.15, -15}, {0.92, 0.12, 12}, {0.15, 0.78, 8}, {0.85, 0.82, -10},
            {0.05, 0.50, -20}, {0.95, 0.48, 18}, {0.50, 0.10, 0}, {0.50, 0.92, 5}
    };

    public LobbyPanel(GameClient client) {
        this.client = client;
        this.isInRoom = false;
        this.isReady = false;

        setLayout(new BorderLayout());
        setBackground(new Color(10, 8, 28));

        createLoginPanel();
        createRoomPanel();

        add(loginPanel, BorderLayout.CENTER);
    }

    /**
     * Create the login view panel — luxurious dark theme with rich decorative elements.
     */
    private void createLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout()) {
            private BufferedImage cardArtCache = null;

            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();

                // 1. Deep luxurious gradient background (diagonal two-tone)
                GradientPaint bgGrad = new GradientPaint(0f, 0f, new Color(12, 8, 32),
                        w, h, new Color(26, 18, 55));
                g2.setPaint(bgGrad);
                g2.fillRect(0, 0, w, h);

                // 2. Rich radial glow (center spotlight)
                RadialGradientPaint centerGlow = new RadialGradientPaint(
                        w / 2f, h / 2f, Math.max(w, h) * 0.6f,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(60, 40, 120, 30), new Color(40, 25, 90, 15), new Color(0, 0, 0, 0)});
                g2.setPaint(centerGlow);
                g2.fillRect(0, 0, w, h);

                // 3. Corner decorative light beams
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.06f));
                // Top-left beam
                g2.setColor(new Color(150, 100, 255));
                g2.fill(new Polygon(new int[]{0, w/4, 0}, new int[]{0, 0, h/3}, 3));
                // Bottom-right beam
                g2.setColor(new Color(100, 200, 255));
                g2.fill(new Polygon(new int[]{w, w*3/4, w}, new int[]{h, h, h*2/3}, 3));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

                // 4. Decorative floating card silhouettes
                drawFloatingCards(g2, w, h);

                // 5. Sparkle / star particles
                drawSparkles(g2, w, h);
            }

            /** Draw decorative card silhouettes around the edges */
            private void drawFloatingCards(Graphics2D g2, int w, int h) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.07f));
                g2.setColor(new Color(200, 180, 255));
                g2.setStroke(new BasicStroke(1f));

                for (double[] pos : CARD_POSITIONS) {
                    int cx = (int) (w * pos[0]);
                    int cy = (int) (h * pos[1]);
                    double angle = Math.toRadians(pos[2]);
                    int cw = 50, ch = 70;

                    g2.rotate(angle, cx, cy);
                    // Card outline
                    g2.drawRoundRect(cx - cw/2, cy - ch/2, cw, ch, 8, 8);
                    // Card inner detail
                    g2.drawLine(cx - cw/3, cy - ch/4, cx + cw/3, cy - ch/4);
                    g2.drawLine(cx - cw/3, cy, cx + cw/3, cy);
                    g2.drawLine(cx - cw/3, cy + ch/4, cx + cw/3, cy + ch/4);
                    // Diamond center
                    int ds = 8;
                    g2.drawPolygon(new int[]{cx, cx-ds, cx, cx+ds}, new int[]{cy-ds, cy, cy+ds, cy}, 4);
                    g2.rotate(-angle, cx, cy);
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            }

            /** Draw sparkle particles */
            private void drawSparkles(Graphics2D g2, int w, int h) {
                int[][] sparkles = {
                        {w/10, h/4, 3}, {w*9/10, h/5, 4}, {w/6, h*3/4, 3},
                        {w*5/6, h*2/3, 2}, {w/3, h/5, 2}, {w*8/9, h*3/4, 3},
                        {w/4, h/2, 4}, {w*3/4, h/2, 2}
                };

                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f));
                g2.setColor(new Color(255, 230, 180));

                for (int[] s : sparkles) {
                    int size = s[2];
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawLine(s[0] - size, s[1], s[0] + size, s[1]);
                    g2.drawLine(s[0], s[1] - size, s[0], s[1] + size);
                    // Small diamond center
                    g2.drawPolygon(new int[]{s[0], s[0]+1, s[0], s[0]-1},
                                   new int[]{s[1]-1, s[1], s[1]+1, s[1]}, 4);
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            }
        };
        loginPanel.setBorder(new EmptyBorder(50, 70, 50, 70));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 15, 10, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ===================================================================
        // TITLE SECTION — glowing gold title with shadow layers
        // ===================================================================
        JPanel titlePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                // Don't paint background — just a container for positioning
            }
        };
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));

        // Main title (MONOPOLY) with glow effect via layered JLabels
        JPanel glowPanel = new JPanel(new GridBagLayout());
        glowPanel.setOpaque(false);

        // Multi-layer title: outer glow, inner glow, main text
        JLabel titleGlowOuter = new JLabel("MONOPOLY");
        titleGlowOuter.setFont(new Font("SansSerif", Font.BOLD, 56));
        titleGlowOuter.setForeground(new Color(255, 200, 50, 40));
        titleGlowOuter.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel titleGlowInner = new JLabel("MONOPOLY");
        titleGlowInner.setFont(new Font("SansSerif", Font.BOLD, 56));
        titleGlowInner.setForeground(new Color(255, 230, 100, 80));
        titleGlowInner.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel titleMain = new JLabel("MONOPOLY");
        titleMain.setFont(new Font("SansSerif", Font.BOLD, 56));
        titleMain.setForeground(new Color(255, 215, 0));
        titleMain.setHorizontalAlignment(SwingConstants.CENTER);

        // Stack them using layered layout
        JLayeredPane layeredTitle = new JLayeredPane();
        layeredTitle.setPreferredSize(new Dimension(400, 70));
        layeredTitle.setMinimumSize(new Dimension(400, 70));

        titleGlowOuter.setBounds(0, 0, 400, 70);
        titleGlowInner.setBounds(0, 0, 400, 70);
        titleMain.setBounds(0, 0, 400, 70);

        layeredTitle.add(titleGlowOuter, JLayeredPane.DEFAULT_LAYER);
        layeredTitle.add(titleGlowInner, JLayeredPane.PALETTE_LAYER);
        layeredTitle.add(titleMain, JLayeredPane.MODAL_LAYER);

        glowPanel.add(layeredTitle);
        titlePanel.add(glowPanel);

        // "DEAL" subtitle with gold color
        JLabel dealLabel = new JLabel("DEAL");
        dealLabel.setFont(new Font("SansSerif", Font.BOLD, 42));
        dealLabel.setForeground(new Color(255, 200, 80));
        dealLabel.setHorizontalAlignment(SwingConstants.CENTER);
        dealLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titlePanel.add(dealLabel);

        // Decorative divider line
        JPanel divider = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                // Gold gradient line
                LinearGradientPaint gp = new LinearGradientPaint(0f, 0f, w, 0f,
                        new float[]{0f, 0.5f, 1f},
                        new Color[]{new Color(255, 215, 0, 0),
                                    new Color(255, 215, 0, 180),
                                    new Color(255, 215, 0, 0)});
                g2.setPaint(gp);
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(0, h/2, w, h/2);
                // Center diamond ornament
                g2.setColor(new Color(255, 215, 0, 200));
                g2.fillPolygon(new int[]{w/2, w/2-5, w/2, w/2+5},
                               new int[]{h/2-5, h/2, h/2+5, h/2}, 4);
            }
        };
        divider.setOpaque(false);
        divider.setPreferredSize(new Dimension(400, 16));
        divider.setMaximumSize(new Dimension(Short.MAX_VALUE, 16));
        divider.setAlignmentX(Component.CENTER_ALIGNMENT);
        titlePanel.add(divider);

        // Premium tagline
        JLabel subtitleLabel = new JLabel("★ Premium Card Game ★");
        subtitleLabel.setFont(new Font("SansSerif", Font.ITALIC, 18));
        subtitleLabel.setForeground(new Color(200, 180, 255));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitleLabel.setBorder(new EmptyBorder(0, 0, 15, 0));
        titlePanel.add(subtitleLabel);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        loginPanel.add(titlePanel, gbc);

        // ===================================================================
        // INPUT SECTION
        // ===================================================================
        gbc.anchor = GridBagConstraints.EAST;
        gbc.gridwidth = 1;

        // Nickname
        gbc.gridy = 2;
        gbc.gridx = 0;
        gbc.insets = new Insets(8, 15, 8, 8);
        JLabel nicknameLabel = new JLabel("👤 Nickname");
        nicknameLabel.setForeground(new Color(200, 190, 230));
        nicknameLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        loginPanel.add(nicknameLabel, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(8, 8, 8, 15);
        nicknameField = new JTextField(20);
        nicknameField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        nicknameField.setText("Player" + (int)(Math.random() * 9000 + 1000));
        styleTextField(nicknameField);
        loginPanel.add(nicknameField, gbc);

        // Room Code
        gbc.gridy = 3;
        gbc.gridx = 0;
        gbc.insets = new Insets(8, 15, 8, 8);
        JLabel roomLabel = new JLabel("🔑 Room Code");
        roomLabel.setForeground(new Color(200, 190, 230));
        roomLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        loginPanel.add(roomLabel, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(8, 8, 8, 15);
        roomCodeField = new JTextField(20);
        roomCodeField.setFont(new Font("SansSerif", Font.PLAIN, 16));
        styleTextField(roomCodeField);
        loginPanel.add(roomCodeField, gbc);

        // ===================================================================
        // ACTION BUTTONS
        // ===================================================================
        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(18, 15, 8, 15);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 5));
        buttonPanel.setOpaque(false);

        createRoomButton = createPremiumButton("🎮 Create Room",
                new Color(34, 186, 157), new Color(20, 150, 125),
                new Color(50, 220, 180), new Color(30, 170, 140));
        joinRoomButton = createPremiumButton("🚀 Join Room",
                new Color(72, 133, 237), new Color(50, 100, 200),
                new Color(100, 160, 255), new Color(65, 120, 220));

        createRoomButton.addActionListener(e -> createRoom());
        joinRoomButton.addActionListener(e -> joinRoom());

        buttonPanel.add(createRoomButton);
        buttonPanel.add(joinRoomButton);

        loginPanel.add(buttonPanel, gbc);

        // ===================================================================
        // RULES BUTTON + STATUS
        // ===================================================================
        gbc.gridy = 5;
        gbc.insets = new Insets(5, 15, 5, 15);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 5));
        bottomRow.setOpaque(false);

        JButton rulesButton = createBorderButton("📖 Game Rules",
                new Color(180, 170, 220), new Color(80, 70, 130));
        rulesButton.addActionListener(e -> showRulesDialog());
        bottomRow.add(rulesButton);

        loginPanel.add(bottomRow, gbc);

        // Status
        gbc.gridy = 6;
        gbc.insets = new Insets(5, 15, 5, 15);
        statusLabel = new JLabel("✨ Enter a nickname to begin");
        statusLabel.setForeground(new Color(180, 180, 220));
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loginPanel.add(statusLabel, gbc);
    }

    /**
     * Create the room view panel — matching luxury style.
     */
    private void createRoomPanel() {
        roomPanel = new JPanel(new BorderLayout(0, 15)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Rich gradient
                GradientPaint gradient = new GradientPaint(0, 0, new Color(18, 14, 40),
                        getWidth(), getHeight(), new Color(35, 28, 65));
                g2.setPaint(gradient);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Decorative corner glow
                g2.setColor(new Color(255, 215, 0, 8));
                g2.fillOval(-50, -50, 200, 200);
                g2.fillOval(getWidth() - 150, getHeight() - 150, 200, 200);
            }
        };
        roomPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // ===== Top info bar =====
        JPanel topPanel = new JPanel(new BorderLayout(20, 0));
        topPanel.setOpaque(false);

        // Room code with decorative icon
        JLabel roomIcon = new JLabel("🏠");
        roomIcon.setFont(new Font("SansSerif", Font.PLAIN, 24));

        roomCodeLabel = new JLabel("Room: -----");
        roomCodeLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        roomCodeLabel.setForeground(new Color(255, 215, 0));

        JPanel roomTitlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        roomTitlePanel.setOpaque(false);
        roomTitlePanel.add(roomIcon);
        roomTitlePanel.add(roomCodeLabel);
        topPanel.add(roomTitlePanel, BorderLayout.WEST);

        // Buttons
        JPanel topButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        topButtonPanel.setOpaque(false);

        startGameButton = createPremiumButton("▶ Start Game",
                new Color(255, 193, 7), new Color(220, 160, 0),
                new Color(255, 220, 50), new Color(240, 180, 10));
        startGameButton.addActionListener(e -> requestStartGame());
        startGameButton.setVisible(false);

        readyButton = createPremiumButton("✅ Ready",
                new Color(46, 204, 113), new Color(30, 160, 85),
                new Color(70, 230, 140), new Color(50, 190, 100));
        readyButton.addActionListener(e -> toggleReady());

        leaveButton = createPremiumButton("🚪 Leave",
                new Color(200, 60, 50), new Color(160, 40, 35),
                new Color(230, 80, 70), new Color(185, 50, 42));
        leaveButton.addActionListener(e -> leaveRoom());

        topButtonPanel.add(startGameButton);
        topButtonPanel.add(readyButton);
        topButtonPanel.add(leaveButton);
        topPanel.add(topButtonPanel, BorderLayout.EAST);

        roomPanel.add(topPanel, BorderLayout.NORTH);

        // ===== Player list =====
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel) {
            @Override
            public void updateUI() {
                super.updateUI();
                setOpaque(false);
            }
        };
        playerList.setBackground(new Color(60, 50, 100, 180));
        playerList.setForeground(Color.WHITE);
        playerList.setFont(new Font("SansSerif", Font.PLAIN, 18));
        playerList.setFixedCellHeight(55);
        playerList.setSelectionBackground(new Color(100, 85, 160));
        playerList.setSelectionForeground(Color.WHITE);

        // Custom cell renderer with player icons
        playerList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                label.setBorder(new EmptyBorder(10, 18, 10, 18));
                String text = value != null ? value.toString() : "";
                // Add player number indicators
                String prefix = "👤";
                if (text.contains("[Host]")) prefix = "👑";
                text = prefix + " " + text;
                label.setText(text);
                return label;
            }
        });

        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(120, 100, 180), 2, true),
                new EmptyBorder(2, 2, 2, 2)));
        roomPanel.add(scrollPane, BorderLayout.CENTER);

        // Status bottom
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        bottomPanel.setOpaque(false);
        statusLabel = new JLabel("⏳ Waiting for players...");
        statusLabel.setForeground(new Color(180, 180, 220));
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 14));
        bottomPanel.add(statusLabel);
        roomPanel.add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * Create a premium button with gradient, glow hover, and rich styling.
     */
    private JButton createPremiumButton(String text, Color start, Color end,
                                        Color hoverStart, Color hoverEnd) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int w = getWidth(), h = getHeight();

                // Determine colors based on state
                boolean hover = getModel().isRollover() && isEnabled();
                boolean pressed = getModel().isPressed() && isEnabled();

                Color s = hover ? hoverStart : (Color) getClientProperty("gradientStart");
                Color e = hover ? hoverEnd : (Color) getClientProperty("gradientEnd");
                if (s == null) s = start;
                if (e == null) e = end;

                if (pressed) {
                    s = s.darker();
                    e = e.darker();
                }

                // Outer glow on hover
                if (hover) {
                    g2.setColor(new Color(255, 255, 255, 25));
                    g2.setStroke(new BasicStroke(3f));
                    g2.drawRoundRect(2, 2, w - 4, h - 4, 20, 20);
                }

                // Main gradient fill
                GradientPaint gp = new GradientPaint(0, 0, s, 0, h, e);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, w, h, 18, 18);

                // Top shine highlight
                if (!pressed) {
                    g2.setColor(new Color(255, 255, 255, 35));
                    g2.fillRoundRect(4, 3, w - 8, h / 2 - 3, 16, 16);
                }

                // Border
                g2.setColor(new Color(255, 255, 255, 60));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(1, 1, w - 2, h - 2, 18, 18);

                // Text with shadow
                g2.setColor(new Color(0, 0, 0, 60));
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2 + 1,
                        (h + fm.getAscent() - fm.getDescent()) / 2 + 1);

                g2.setColor(Color.WHITE);
                g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2,
                        (h + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };

        button.setFont(new Font("SansSerif", Font.BOLD, 15));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(190, 52));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.putClientProperty("gradientStart", start);
        button.putClientProperty("gradientEnd", end);

        // Subtle hover scale effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                button.setPreferredSize(new Dimension(195, 54));
                button.revalidate();
            }
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                button.setPreferredSize(new Dimension(190, 52));
                button.revalidate();
            }
        });

        return button;
    }

    /**
     * Create a bordered outline button (for secondary actions like Rules).
     */
    private JButton createBorderButton(String text, Color textColor, Color borderColor) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();

                boolean hover = getModel().isRollover() && isEnabled();

                // Background (subtle on hover)
                if (hover) {
                    g2.setColor(new Color(255, 255, 255, 20));
                    g2.fillRoundRect(0, 0, w, h, 15, 15);
                }

                // Border
                g2.setColor(hover ? textColor : borderColor);
                g2.setStroke(new BasicStroke(hover ? 2f : 1.5f));
                g2.drawRoundRect(1, 1, w - 2, h - 2, 15, 15);

                // Text
                g2.setColor(hover ? textColor.brighter() : textColor);
                g2.setFont(getFont());
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (w - fm.stringWidth(getText())) / 2,
                        (h + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };

        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setForeground(textColor);
        button.setPreferredSize(new Dimension(180, 44));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void updateGradientButton(JButton button, Color start, Color end, String text) {
        button.setText(text);
        button.putClientProperty("gradientStart", start);
        button.putClientProperty("gradientEnd", end);
        button.repaint();
    }

    private void styleTextField(JTextField field) {
        field.setBackground(new Color(40, 35, 70, 200));
        field.setForeground(Color.WHITE);
        field.setCaretColor(new Color(255, 215, 0));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(130, 110, 190), 2, true),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        field.setOpaque(true);
        // Focus listener for border highlight
        field.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(255, 215, 0), 2, true),
                        BorderFactory.createEmptyBorder(12, 14, 12, 14)));
            }
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(130, 110, 190), 2, true),
                        BorderFactory.createEmptyBorder(12, 14, 12, 14)));
            }
        });
    }

    private void createRoom() {
        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            setStatus("❌ Please enter a nickname", Color.RED);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        amICreator = true;
        client.sendMessage(MessageProtocol.MessageType.CREATE_ROOM, payload.toString());
        setStatus("🎲 Creating room...", new Color(255, 200, 0));
    }

    private void joinRoom() {
        String nickname = nicknameField.getText().trim();
        String roomCode = roomCodeField.getText().trim().toUpperCase();

        if (nickname.isEmpty()) {
            setStatus("❌ Please enter a nickname", Color.RED);
            return;
        }
        if (roomCode.isEmpty()) {
            setStatus("❌ Please enter room code", Color.RED);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        payload.addProperty("roomCode", roomCode);
        amICreator = false;
        client.sendMessage(MessageProtocol.MessageType.JOIN_ROOM, payload.toString());
        setStatus("🔗 Joining room...", new Color(255, 200, 0));
    }

    private void requestStartGame() {
        client.sendMessage(MessageProtocol.MessageType.REQUEST_START_GAME, "{}");
        setStatus("🚀 Starting game...", new Color(255, 200, 0));
    }

    private void toggleReady() {
        isReady = !isReady;
        if (isReady) {
            updateGradientButton(readyButton,
                    new Color(200, 60, 50), new Color(160, 40, 35), "❌ Unready");
        } else {
            updateGradientButton(readyButton,
                    new Color(46, 204, 113), new Color(30, 160, 85), "✅ Ready");
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("ready", isReady);
        client.sendMessage(MessageProtocol.MessageType.PLAYER_READY, payload.toString());
    }

    private void leaveRoom() {
        client.sendMessage(MessageProtocol.MessageType.LEAVE_ROOM, "{}");
        isInRoom = false;
        isReady = false;
        updateGradientButton(readyButton,
                new Color(46, 204, 113), new Color(30, 160, 85), "✅ Ready");
        showLoginPanel();
    }

    public void updateRoom(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String roomCode = payload.get("roomCode").getAsString();
                roomCodeLabel.setText("🏠 Room: " + roomCode);

                JsonArray players = payload.getAsJsonArray("players");
                playerListModel.clear();

                int playerNum = 0;
                for (JsonElement elem : players) {
                    JsonObject player = elem.getAsJsonObject();
                    String nickname = player.get("nickname").getAsString();
                    boolean ready = player.get("ready").getAsBoolean();
                    boolean isCreator = player.get("isCreator").getAsBoolean();
                    playerNum++;

                    String displayText = nickname;
                    if (isCreator) displayText += " [Host]";
                    displayText += ready ? "  ● Ready" : "  ○ Waiting";
                    playerListModel.addElement(displayText);
                }

                if (!isInRoom) {
                    isInRoom = true;
                    showRoomPanel();
                }

                int totalPlayers = players.size();
                long readyCount = 0;
                for (JsonElement elem : players) {
                    if (elem.getAsJsonObject().get("ready").getAsBoolean()) readyCount++;
                }
                boolean allReady = readyCount == totalPlayers && totalPlayers >= GameConstants.MIN_PLAYERS;
                startGameButton.setVisible(amICreator && allReady);

                setStatus("👥 Room: " + roomCode + " | Players: " + players.size(),
                        new Color(46, 204, 113));
            } catch (Exception e) {
                setStatus("⚠️ Failed to update room info", Color.RED);
            }
        });
    }

    private void showRoomPanel() {
        remove(loginPanel);
        add(roomPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void showLoginPanel() {
        remove(roomPanel);
        add(loginPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        setStatus("✨ Enter a nickname to begin", new Color(180, 180, 220));
    }

    private void setStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }

    private void showRulesDialog() {
        String rules =
                "MONOPOLY DEAL - GAME RULES\n\n" +
                        "OBJECTIVE\n" +
                        "Be the first player to collect 3 complete property sets.\n\n" +
                        "SETUP\n" +
                        "- 2-5 players\n" +
                        "- Each player starts with 5 cards\n" +
                        "- Draw 3 cards at the start of your turn\n\n" +
                        "YOUR TURN\n" +
                        "1. Draw Phase: Draw 3 cards from the draw pile\n" +
                        "2. Play Phase: Play up to 3 cards (30 second timer)\n" +
                        "3. Discard Phase: If you have more than 7 cards,\n" +
                        "   discard down to 7\n\n" +
                        "CARD TYPES\n" +
                        "- Money Cards (1M-10M): Deposit into your bank\n" +
                        "- Property Cards: Place in your property zone\n" +
                        "  Wild Property: Assign to any color\n" +
                        "- Rent Cards: Charge rent to other players\n" +
                        "  Wild Rent: Targets one player\n" +
                        "  Others: Target all players\n" +
                        "- Action Cards: Special one-time effects\n\n" +
                        "ACTION CARDS\n" +
                        "- Debt Collector: One player pays you 5M\n" +
                        "- Birthday: All players pay you 2M\n" +
                        "- Deal Breaker: Steal a complete property set\n" +
                        "- Pass Go: Draw 2 extra cards\n" +
                        "- Double Rent: Next rent card value is doubled\n" +
                        "- Forced Deal: Swap a property with another player\n" +
                        "- Sly Deal: Steal one property\n" +
                        "  (not from a complete set)\n" +
                        "- House/Hotel: Build on complete sets\n" +
                        "  for extra rent value\n" +
                        "- Just Say No: Cancel an action against you\n\n" +
                        "PROPERTY SET SIZES\n" +
                        "2 cards: Brown, Light Blue, Blue\n" +
                        "3 cards: Pink, Orange, Red, Yellow, Green,\n" +
                        "         Purple, Light Green\n" +
                        "4 cards: Black\n\n" +
                        "RENT VALUES (per property in set)\n" +
                        "Brown/Light Blue: 1-2M\n" +
                        "Pink/Orange: 1-3M\n" +
                        "Red/Yellow: 2-4-6M\n" +
                        "Green: 2-4-7M\n" +
                        "Blue: 3-8M\n" +
                        "Purple: 1-2-4M\n" +
                        "Black: 1-2-3-5M\n" +
                        "Light Green: 1-2-4M\n\n" +
                        "BUILDINGS\n" +
                        "House: +1M rent each (max 4 per set)\n" +
                        "Hotel: Replaces 4 houses (+3M rent)\n\n" +
                        "WINNING\n" +
                        "First player to collect 3 complete\n" +
                        "property sets wins the game!";

        JTextArea textArea = new JTextArea(rules);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setForeground(Color.WHITE);
        textArea.setBackground(new Color(20, 18, 40));
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(520, 520));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(120, 100, 180), 2));

        JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                scrollPane,
                "Game Rules - Monopoly Deal",
                JOptionPane.PLAIN_MESSAGE
        );
    }

    @Override
    public void setVisible(boolean aFlag) {
        super.setVisible(aFlag);
    }
}
