package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import com.monopolydeal.client.GameClient;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

/**
 * Main Game Panel for Monopoly Deal
 * Contains game table, player panels, hand cards, timer, and visual effects
 */
public class GamePanel extends JPanel {
    // Game client reference
    private final GameClient client;

    // UI Panels
    private JPanel topBarPanel;
    private JPanel mainGamePanel;
    private JPanel handPanel;
    private JPanel sidePanel;

    // Top bar components
    private JLabel phaseLabel;
    private JLabel turnLabel;
    private JLabel timerLabel;
    private JLabel drawPileLabel;
    private JButton endTurnButton;

    // Player and card areas
    private JPanel playerPanelsContainer;
    private final Map<String, PlayerPanel> playerPanels;
    private JPanel handCardsPanel;
    private ActionHistoryPanel actionHistoryPanel;

    // Game state
    private String localPlayerId;
    private boolean isMyTurn;
    private javax.swing.Timer countdownTimer;
    private int secondsRemaining;
    private JsonObject cardDataForClicked;

    // Ambient animation effects
    private float glowPhase = 0f;
    private javax.swing.Timer ambientTimer;
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();

    // ===================== Color Scheme (Premium Dark Theme) =====================
    private static final Color BG_DEEP = new Color(6, 8, 14);
    private static final Color BG_MID = new Color(14, 16, 28);
    private static final Color PANEL_BG = new Color(12, 14, 24);
    private static final Color CARD_BG = new Color(20, 22, 38);

    // Gold theme
    private static final Color GOLD_PRIMARY = new Color(255, 223, 80);
    private static final Color GOLD_GLOW = new Color(255, 240, 140);

    // Red for buttons and warnings
    private static final Color RED_ACCENT = new Color(255, 72, 60);
    private static final Color RED_DARK = new Color(190, 35, 35);
    private static final Color RED_GLOW = new Color(255, 110, 90, 80);

    // Casino table green
    private static final Color GREEN_FELT = new Color(18, 54, 34);
    private static final Color GREEN_SHADOW = new Color(8, 28, 16);

    // Text colors
    private static final Color TEXT_WHITE = new Color(250, 250, 255);
    private static final Color TEXT_GRAY = new Color(160, 160, 185);
    private static final Color TEXT_DIM = new Color(110, 110, 140);

    // Border and accent colors
    private static final Color ACCENT_PURPLE = new Color(120, 70, 180);
    private static final Color ACCENT_PURPLE_LIGHT = new Color(160, 100, 220, 100);

    // Wild card color options
    private static final Map<String, String[]> WILD_COLOR_OPTIONS = new LinkedHashMap<>();
    static {
        WILD_COLOR_OPTIONS.put("Multi-Color Wild", new String[]{"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED", "YELLOW", "GREEN", "BLUE", "PURPLE", "BLACK", "LIGHT_GREEN"});
        WILD_COLOR_OPTIONS.put("Dark Blue/Green Wild", new String[]{"BLUE", "GREEN"});
        WILD_COLOR_OPTIONS.put("Red/Yellow Wild", new String[]{"RED", "YELLOW"});
        WILD_COLOR_OPTIONS.put("Brown/Light Blue Wild", new String[]{"BROWN", "LIGHT_BLUE"});
        WILD_COLOR_OPTIONS.put("Orange/Pink Wild", new String[]{"ORANGE", "PINK"});
        WILD_COLOR_OPTIONS.put("Light Green/Black Wild", new String[]{"LIGHT_GREEN", "BLACK"});
    }

    /**
     * Constructor initializes layout, components, and animations
     */
    public GamePanel(GameClient client) {
        this.client = client;
        this.playerPanels = new LinkedHashMap<>();
        this.isMyTurn = false;
        this.secondsRemaining = 30;

        // Base panel setup
        setLayout(new BorderLayout());
        setBackground(BG_DEEP);
        setDoubleBuffered(true);

        // Initialize all UI sections
        createTopBar();
        createMainGameArea();
        createHandPanel();
        createSidePanel();

        // Add components to main layout
        add(topBarPanel, BorderLayout.NORTH);
        add(mainGamePanel, BorderLayout.CENTER);
        add(handPanel, BorderLayout.SOUTH);
        add(sidePanel, BorderLayout.EAST);

        // Start visual effects
        initAmbientEffects();
        initGlobalDialogStyle();
    }

    /**
     * Set global style for all popup dialogs
     */
    private void initGlobalDialogStyle() {
        UIManager.put("OptionPane.background", PANEL_BG);
        UIManager.put("Panel.background", PANEL_BG);
        UIManager.put("OptionPane.messageForeground", TEXT_WHITE);
        UIManager.put("OptionPane.messageFont", new Font("Microsoft YaHei", Font.PLAIN, 14));
        UIManager.put("OptionPane.buttonFont", new Font("Microsoft YaHei", Font.BOLD, 13));
        UIManager.put("Button.background", new Color(70, 45, 120));
        UIManager.put("Button.foreground", TEXT_WHITE);
        UIManager.put("Button.border", BorderFactory.createLineBorder(ACCENT_PURPLE_LIGHT, 1));
        UIManager.put("ComboBox.background", CARD_BG);
        UIManager.put("ComboBox.foreground", TEXT_WHITE);
    }

    /**
     * Initialize floating particles and glow animations
     */
    private void initAmbientEffects() {
        // Create background particles
        for (int i = 0; i < 40; i++) {
            particles.add(new Particle(rng.nextInt(1400), rng.nextInt(900)));
        }

        // Animation timer
        ambientTimer = new javax.swing.Timer(35, e -> {
            glowPhase += 0.025f;
            for (Particle p : particles) p.update();
            mainGamePanel.repaint();
            handCardsPanel.repaint();
            timerLabel.repaint();
            topBarPanel.repaint();
        });
        ambientTimer.start();
    }

    /**
     * Create top bar with game status, timer, and end turn button
     */
    private void createTopBar() {
        topBarPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                // Gradient background
                GradientPaint bgGradient = new GradientPaint(0, 0, BG_MID, 0, getHeight(), BG_DEEP);
                g2.setPaint(bgGradient);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Animated bottom border
                float baseAlpha = 40 + (float) (Math.sin(glowPhase) * 20);
                g2.setStroke(new BasicStroke(2f));
                g2.setColor(new Color(GOLD_PRIMARY.getRed(), GOLD_PRIMARY.getGreen(), GOLD_PRIMARY.getBlue(), (int) baseAlpha));
                g2.drawLine(25, getHeight() - 1, getWidth() - 25, getHeight() - 1);

                g2.setColor(new Color(ACCENT_PURPLE.getRed(), ACCENT_PURPLE.getGreen(), ACCENT_PURPLE.getBlue(), (int) (baseAlpha * 1.2)));
                g2.drawLine(25, getHeight() - 2, getWidth() - 25, getHeight() - 2);

                // Animated gold dots
                int dotSpacing = 35;
                for (int x = dotSpacing; x < getWidth(); x += dotSpacing) {
                    float dotAlpha = 12 + (float) (Math.sin(glowPhase + x * 0.012) * 10);
                    float dotSize = 2.5f + (float) (Math.sin(glowPhase * 2 + x) * 1f);
                    g2.setColor(new Color(GOLD_GLOW.getRed(), GOLD_GLOW.getGreen(), GOLD_GLOW.getBlue(), (int) dotAlpha));
                    g2.fillOval(x, getHeight() - 5, (int) dotSize, (int) dotSize);
                }
                g2.dispose();
            }
        };
        topBarPanel.setOpaque(false);
        topBarPanel.setBorder(new EmptyBorder(18, 35, 18, 35));
        topBarPanel.setPreferredSize(new Dimension(0, 78));

        // Left section: game phase and turn info
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 35, 0));
        leftPanel.setOpaque(false);

        phaseLabel = new JLabel("Phase: Waiting");
        phaseLabel.setForeground(GOLD_PRIMARY);
        phaseLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        phaseLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, ACCENT_PURPLE_LIGHT),
                BorderFactory.createEmptyBorder(0, 0, 0, 18)));

        turnLabel = new JLabel("Current Turn: -");
        turnLabel.setForeground(TEXT_WHITE);
        turnLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 17));

        leftPanel.add(phaseLabel);
        leftPanel.add(turnLabel);

        // Right section: draw pile, timer, end turn button
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 28, 0));
        rightPanel.setOpaque(false);

        // Draw pile counter
        drawPileLabel = new JLabel("Deck: 0") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int radius = 18;

                // Drop shadow
                g2.setColor(new Color(0, 0, 0, 70));
                g2.fillRoundRect(2, 2, w, h, radius, radius);

                // Gradient background
                GradientPaint gp = new GradientPaint(0, 0, new Color(45, 38, 70), 0, h, new Color(28, 22, 50));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);

                // Border
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(ACCENT_PURPLE_LIGHT);
                g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);

                // Text
                g2.setColor(TEXT_WHITE);
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                String text = getText();
                g2.drawString(text, (w - fm.stringWidth(text)) / 2, (h + fm.getAscent()) / 2 - 2);
                g2.dispose();
            }
        };
        drawPileLabel.setForeground(TEXT_WHITE);
        drawPileLabel.setPreferredSize(new Dimension(96, 34));
        drawPileLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Circular countdown timer
        timerLabel = new JLabel("30") {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int r = 24;

                // Shadow
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fillOval(cx - r + 3, cy - r + 3, r * 2, r * 2);

                // Background
                GradientPaint bgGp = new GradientPaint(cx - r, cy - r, new Color(40, 34, 65), cx + r, cy + r, new Color(22, 18, 42));
                g2.setPaint(bgGp);
                g2.fillOval(cx - r, cy - r, r * 2, r * 2);

                // Breathing pulse effect
                float pulse = 1f + (float) (Math.sin(glowPhase * 3.2) * 0.07f);
                float strokeW = 3f * pulse;

                // Base circle
                g2.setStroke(new BasicStroke(2f * pulse, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 255, 255, 20));
                g2.drawOval(cx - r, cy - r, r * 2, r * 2);

                // Countdown arc
                int angle = (int) (360 * secondsRemaining / 30.0);
                g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(timerLabel.getForeground());
                g2.drawArc(cx - r + 2, cy - r + 2, r * 2 - 4, r * 2 - 4, 90, -angle);

                // Timer text
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
                g2.setColor(TEXT_WHITE);
                FontMetrics fm = g2.getFontMetrics();
                String text = getText();
                g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2 - 1);

                // Animated top dot
                float pointAlpha = 25 + (float) (Math.sin(glowPhase * 2.5) * 12);
                g2.setColor(new Color(GOLD_GLOW.getRed(), GOLD_GLOW.getGreen(), GOLD_GLOW.getBlue(), (int) pointAlpha));
                g2.fillOval(cx - 7, cy - r + 7, 6, 6);

                g2.dispose();
            }
        };
        timerLabel.setForeground(GOLD_PRIMARY);
        timerLabel.setPreferredSize(new Dimension(54, 54));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // End Turn button with custom styling
        endTurnButton = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int round = 28;
                RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, round, round);

                // Button states
                if (!isEnabled()) {
                    // Disabled state
                    g2.setColor(new Color(50, 50, 65));
                    g2.fill(shape);
                    g2.setColor(TEXT_DIM);
                } else if (getModel().isPressed()) {
                    // Pressed state
                    g2.setColor(RED_DARK);
                    g2.fill(shape);
                } else if (getModel().isRollover()) {
                    // Hover state with glow
                    GradientPaint gp = new GradientPaint(0, 0, new Color(255, 85, 72), 0, h, RED_ACCENT);
                    g2.setPaint(gp);
                    g2.fill(shape);
                    // Glow effect
                    g2.setColor(RED_GLOW);
                    g2.setStroke(new BasicStroke(3f));
                    g2.draw(shape);
                } else {
                    // Normal state
                    GradientPaint gp = new GradientPaint(0, 0, RED_ACCENT, 0, h, RED_DARK);
                    g2.setPaint(gp);
                    g2.fill(shape);
                }

                // Highlight border
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(new Color(255, 255, 255, 50));
                g2.draw(shape);

                // Button text
                g2.setColor(TEXT_WHITE);
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                String text = "End Turn";
                g2.drawString(text, (w - fm.stringWidth(text)) / 2, (h + fm.getAscent()) / 2 - 2);
                g2.dispose();
            }
        };
        endTurnButton.setPreferredSize(new Dimension(136, 44));
        endTurnButton.setBorderPainted(false);
        endTurnButton.setContentAreaFilled(false);
        endTurnButton.setFocusPainted(false);
        endTurnButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        endTurnButton.setEnabled(false);
        endTurnButton.addActionListener(e -> endTurn());

        // Add components to right panel
        rightPanel.add(drawPileLabel);
        rightPanel.add(timerLabel);
        rightPanel.add(endTurnButton);

        topBarPanel.add(leftPanel, BorderLayout.WEST);
        topBarPanel.add(rightPanel, BorderLayout.EAST);
    }

    /**
     * Create main game table with player panels
     */
    private void createMainGameArea() {
        mainGamePanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

                int w = getWidth();
                int h = getHeight();
                Color feltDark = new Color(10, 70, 30);
                Color feltLight = new Color(25, 115, 55);
                GradientPaint tableGradient = new GradientPaint(0, 0, feltDark, 0, h, feltLight);
                g2.setPaint(tableGradient);
                g2.fillRect(0, 0, w, h);

                // 绒布细微纹理（模拟布纹质感）
                g2.setColor(new Color(0, 0, 0, 5));
                for (int y = 0; y < h; y += 2) {
                    g2.fillRect(0, y, w, 1);
                }

                // 桌面网格纹理（更清晰、更有质感）
                g2.setColor(new Color(255, 255, 255, 7));
                g2.setStroke(new BasicStroke(1.0f));
                int grid = 100;
                for (int x = 0; x < w; x += grid) {
                    for (int y = 0; y < h; y += grid) {
                        g2.drawRoundRect(x + 5, y + 5, grid - 10, grid - 10, 16, 16);
                    }
                }

                // 桌面外发光边框（更高级）
                g2.setColor(new Color(255, 255, 255, 10));
                g2.setStroke(new BasicStroke(4.0f));
                g2.drawRoundRect(20, 20, w - 40, h - 40, 60, 60);

                g2.setColor(new Color(255, 215, 0, 15));
                g2.setStroke(new BasicStroke(2.0f));
                g2.drawRoundRect(26, 26, w - 52, h - 52, 52, 52);

                // 中心动态光环（更明显、更高级）
                float ringAlpha = 10 + (float) (Math.sin(glowPhase * 0.4f) * 6f);
                g2.setColor(new Color(255, 220, 80, (int) ringAlpha));
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{14, 30}, 0));
                int cx = w / 2;
                int cy = h / 2;
                g2.drawOval(cx - 180, cy - 180, 360, 360);
                g2.drawOval(cx - 220, cy - 220, 440, 440);

                // 粒子光点
                for (Particle p : particles) {
                    if (p.y > cy - 240 && p.y < cy + 240 && p.x > cx - 240 && p.x < cx + 240) {
                        g2.setColor(new Color(255, 230, 100, (int) (p.alpha * 0.6f)));
                        g2.fillOval((int) p.x, (int) p.y, (int) p.size + 1, (int) p.size + 1);
                    }
                }

                g2.dispose();
            }
        };
        mainGamePanel.setOpaque(false);

        playerPanelsContainer = new JPanel();
        playerPanelsContainer.setLayout(new BoxLayout(playerPanelsContainer, BoxLayout.Y_AXIS));
        playerPanelsContainer.setOpaque(false);
        playerPanelsContainer.setBorder(new EmptyBorder(18, 24, 18, 24));

        JScrollPane scrollPane = new JScrollPane(playerPanelsContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(22);
        scrollPane.getVerticalScrollBar().setBackground(BG_DEEP);

        mainGamePanel.add(scrollPane, BorderLayout.CENTER);
    }
    /**
     * Create bottom panel for player's hand cards
     */
    private void createHandPanel() {
        handPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Gradient background
                GradientPaint gp = new GradientPaint(0, 0, new Color(22, 20, 40), 0, getHeight(), BG_DEEP);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Top gradient border
                g2.setStroke(new BasicStroke(2.2f));
                GradientPaint lineGp = new GradientPaint(0, 0, GOLD_PRIMARY, getWidth() * 0.6f, 0, ACCENT_PURPLE);
                g2.setPaint(lineGp);
                g2.drawLine(0, 0, getWidth(), 0);

                // Subtle highlight
                g2.setColor(new Color(255, 255, 255, 12));
                g2.setStroke(new BasicStroke(1f));
                g2.drawLine(0, 1, getWidth(), 1);
                g2.dispose();
            }
        };
        handPanel.setOpaque(false);
        handPanel.setBorder(new EmptyBorder(16, 24, 18, 24));
        handPanel.setPreferredSize(new Dimension(0, 240));

        // Hand panel header
        JPanel handHeader = new JPanel(new BorderLayout());
        handHeader.setOpaque(false);
        JLabel handLabel = new JLabel("Your Hand");
        handLabel.setForeground(GOLD_GLOW);
        handLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));
        handHeader.add(handLabel, BorderLayout.WEST);
        handPanel.add(handHeader, BorderLayout.NORTH);

        // Card container with rounded background
        handCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 12)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Inner background
                g2.setColor(new Color(255, 255, 255, 4));
                g2.fillRoundRect(8, 8, getWidth() - 16, getHeight() - 16, 22, 22);

                // Border
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(new Color(255, 255, 255, 10));
                g2.drawRoundRect(8, 8, getWidth() - 16, getHeight() - 16, 22, 22);
                g2.dispose();
            }
        };
        handCardsPanel.setOpaque(false);

        // Scrollable hand area
        JScrollPane handScrollPane = new JScrollPane(handCardsPanel);
        handScrollPane.setOpaque(false);
        handScrollPane.getViewport().setOpaque(false);
        handScrollPane.setBorder(null);
        handScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        handScrollPane.setPreferredSize(new Dimension(0, 186));
        handScrollPane.getHorizontalScrollBar().setUnitIncrement(22);
        handScrollPane.getHorizontalScrollBar().setBackground(BG_DEEP);

        handPanel.add(handScrollPane, BorderLayout.CENTER);
    }

    /**
     * Create right side panel for action history
     */
    private void createSidePanel() {
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBackground(PANEL_BG);
        sidePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, ACCENT_PURPLE_LIGHT),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        sidePanel.setPreferredSize(new Dimension(290, 0));
        actionHistoryPanel = new ActionHistoryPanel();
        sidePanel.add(actionHistoryPanel, BorderLayout.CENTER);
    }

    /**
     * Update entire game state from server JSON
     */
    public void updateGameState(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject gameState = JsonParser.parseString(jsonPayload).getAsJsonObject();

                // Set local player ID
                if (gameState.has("viewerId")) {
                    String myId = gameState.get("viewerId").getAsString();
                    if (!myId.equals(localPlayerId)) localPlayerId = myId;
                }

                // Update phase display
                String phase = gameState.has("phase") ? gameState.get("phase").getAsString() : "Unknown";
                phaseLabel.setText("Phase: " + phase);

                // Update current turn
                String activePlayerId = gameState.has("activePlayerId") ? gameState.get("activePlayerId").getAsString() : "";

                // Update deck size
                int drawPileSize = gameState.has("drawPileSize") ? gameState.get("drawPileSize").getAsInt() : 0;
                drawPileLabel.setText("Deck: " + drawPileSize);

                // Update players and cards
                JsonObject playerStates = gameState.has("playerStates") ? gameState.getAsJsonObject("playerStates") : null;
                if (playerStates != null) {
                    updatePlayerPanelsFromStates(playerStates, activePlayerId);
                    updateTurnInfo(activePlayerId, playerStates);
                    updateLocalHand(playerStates);
                }

                // Update action log
                JsonArray actions = gameState.has("actionHistory") ? gameState.getAsJsonArray("actionHistory") : null;
                if (actions != null) actionHistoryPanel.updateActions(actions);

            } catch (Exception e) {
                // Silent fail for UI stability
            }
        });
    }

    /**
     * Create or update player panels based on game state
     */
    private void updatePlayerPanelsFromStates(JsonObject playerStates, String activePlayerId) {
        Set<String> existingIds = new HashSet<>(playerPanels.keySet());

        for (Map.Entry<String, JsonElement> entry : playerStates.entrySet()) {
            String playerId = entry.getKey();
            JsonObject playerData = entry.getValue().getAsJsonObject();
            existingIds.remove(playerId);

            PlayerPanel panel = playerPanels.get(playerId);
            if (panel == null) {
                panel = new PlayerPanel(playerId);
                playerPanels.put(playerId, panel);
                playerPanelsContainer.add(panel);
            }

            // Extract player data
            boolean isActive = playerData.has("isActivePlayer") && playerData.get("isActivePlayer").getAsBoolean();
            String nickname = playerData.has("nickname") ? playerData.get("nickname").getAsString() : "Unknown";
            int handCount = playerData.has("handCount") ? playerData.get("handCount").getAsInt() : 0;
            int bankTotal = playerData.has("bankTotal") ? playerData.get("bankTotal").getAsInt() : 0;
            int completeSets = playerData.has("completeSets") ? playerData.get("completeSets").getAsInt() : 0;
            int remainingPlays = playerData.has("remainingPlays") ? playerData.get("remainingPlays").getAsInt() : 0;
            boolean connected = !playerData.has("isConnected") || playerData.get("isConnected").getAsBoolean();

            // Property color counts
            Map<String, Integer> propertyColorCounts = new LinkedHashMap<>();
            if (playerData.has("propertyColorCounts")) {
                JsonObject colorCounts = playerData.getAsJsonObject("propertyColorCounts");
                for (Map.Entry<String, JsonElement> colorEntry : colorCounts.entrySet()) {
                    propertyColorCounts.put(colorEntry.getKey(), colorEntry.getValue().getAsInt());
                }
            }

            // Update panel UI
            JsonObject simplified = new JsonObject();
            simplified.addProperty("nickname", nickname);
            simplified.addProperty("isActive", isActive);
            simplified.addProperty("handCount", handCount);
            simplified.addProperty("bankTotal", bankTotal);
            simplified.addProperty("completeSets", completeSets);
            simplified.addProperty("remainingPlays", remainingPlays);
            simplified.addProperty("connected", connected);
            panel.updateFromJson(simplified, propertyColorCounts);
        }

        // Remove disconnected players
        for (String removedId : existingIds) {
            PlayerPanel panel = playerPanels.remove(removedId);
            if (panel != null) playerPanelsContainer.remove(panel);
        }

        playerPanelsContainer.revalidate();
        playerPanelsContainer.repaint();
    }

    /**
     * Update turn status and enable/disable player controls
     */
    private void updateTurnInfo(String activePlayerId, JsonObject playerStates) {
        if (localPlayerId == null) return;

        boolean wasMyTurn = isMyTurn;
        isMyTurn = activePlayerId.equals(localPlayerId);

        // Get active player name
        String activeNickname = "Unknown";
        if (playerStates.has(activePlayerId)) {
            JsonObject activeData = playerStates.getAsJsonObject(activePlayerId);
            activeNickname = activeData.has("nickname") ? activeData.get("nickname").getAsString() : "Unknown";
        }
        turnLabel.setText("Current Turn: " + activeNickname);

        // Handle turn start/end
        if (isMyTurn && !wasMyTurn) {
            startCountdown();
            endTurnButton.setEnabled(true);
            handPanel.setBorder(BorderFactory.createMatteBorder(3, 0, 0, 0, GOLD_PRIMARY));
        } else if (isMyTurn && wasMyTurn) {
            endTurnButton.setEnabled(true);
        } else if (!isMyTurn && wasMyTurn) {
            stopCountdown();
            endTurnButton.setEnabled(false);
            handPanel.setBorder(new EmptyBorder(16, 24, 18, 24));
        }

        // Enable/disable cards during my turn
        for (Component comp : handCardsPanel.getComponents()) {
            if (comp instanceof CardRenderer) comp.setEnabled(isMyTurn);
        }
    }

    /**
     * Start 30-second turn countdown
     */
    private void startCountdown() {
        stopCountdown();
        secondsRemaining = 30;
        timerLabel.setText("30");
        timerLabel.setForeground(GOLD_PRIMARY);

        countdownTimer = new javax.swing.Timer(1000, e -> {
            secondsRemaining--;
            timerLabel.setText(String.valueOf(secondsRemaining));

            // Turn red when low on time
            if (secondsRemaining <= 10) timerLabel.setForeground(RED_ACCENT);

            // Auto end turn when time up
            if (secondsRemaining <= 0) {
                stopCountdown();
                endTurnButton.setEnabled(false);
                timerLabel.setText("0");
                client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
            }
        });
        countdownTimer.start();
    }

    /**
     * Stop countdown timer
     */
    private void stopCountdown() {
        if (countdownTimer != null && countdownTimer.isRunning()) countdownTimer.stop();
        countdownTimer = null;
        timerLabel.setText("--");
        timerLabel.setForeground(TEXT_GRAY);
    }

    /**
     * Refresh local player's hand cards display
     */
    private void updateLocalHand(JsonObject playerStates) {
        handCardsPanel.removeAll();

        if (localPlayerId == null || !playerStates.has(localPlayerId)) {
            handCardsPanel.revalidate();
            handCardsPanel.repaint();
            return;
        }

        JsonObject myData = playerStates.getAsJsonObject(localPlayerId);
        if (myData.has("handCards")) {
            JsonArray handCards = myData.getAsJsonArray("handCards");
            for (JsonElement elem : handCards) {
                JsonObject cardData = elem.getAsJsonObject();
                CardRenderer card = new CardRenderer(cardData);
                card.setEnabled(isMyTurn);

                String cardType = cardData.has("cardType") ? cardData.get("cardType").getAsString() : "MONEY";
                String cardId = cardData.has("cardId") ? cardData.get("cardId").getAsString() : "";

                // Set click listener for playing cards
                card.setPlayListener(id -> {
                    cardDataForClicked = cardData;
                    onCardClicked(id, cardType);
                });

                handCardsPanel.add(card);
            }
        }

        handCardsPanel.revalidate();
        handCardsPanel.repaint();
    }

    /**
     * Handle card click and show action menu
     */
    private void onCardClicked(String cardId, String cardType) {
        if (!isMyTurn) {
            showStyledMessage("It's not your turn!", "Notice", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Determine available actions
        String[] options;
        String[] actions;
        switch (cardType) {
            case "MONEY":
                options = new String[]{"Deposit to Bank"};
                actions = new String[]{"PLAY_MONEY"};
                break;
            case "PROPERTY":
                options = new String[]{"Play Property"};
                actions = new String[]{"PLAY_PROPERTY"};
                break;
            case "RENT":
                options = new String[]{"Collect Rent"};
                actions = new String[]{"PLAY_RENT"};
                break;
            case "ACTION":
                options = new String[]{"Use Action Card"};
                actions = new String[]{"PLAY_ACTION"};
                break;
            default:
                options = new String[]{"Deposit to Bank"};
                actions = new String[]{"PLAY_MONEY"};
        }

        // Show action dialog
        int choice = showStyledOptionDialog("How to use this card?", "Play Card", options);
        if (choice >= 0) {
            JsonObject payload = new JsonObject();
            payload.addProperty("cardId", cardId);
            payload.addProperty("action", actions[choice]);

            // Handle wild card color selection
            if (cardDataForClicked != null && cardDataForClicked.has("color")) {
                String colorStr = cardDataForClicked.get("color").getAsString();
                String cardName = cardDataForClicked.has("cardName") ? cardDataForClicked.get("cardName").getAsString() : "";

                if ("WILD".equals(colorStr) && "PLAY_PROPERTY".equals(actions[choice])) {
                    String selectedColor = showWildColorPicker(cardName);
                    if (selectedColor != null) payload.addProperty("color", selectedColor);
                }
                if ("WILD".equals(colorStr) && "PLAY_RENT".equals(actions[choice])) {
                    String selectedColor = showColorPicker();
                    if (selectedColor != null) payload.addProperty("color", selectedColor);
                }
            }

            client.sendMessage(MessageProtocol.MessageType.PLAY_CARD, payload.toString());
        }
    }

    /**
     * Show color picker for wild property cards
     */
    private String showWildColorPicker(String cardName) {
        String[] colors = WILD_COLOR_OPTIONS.get(cardName);
        if (colors == null) colors = WILD_COLOR_OPTIONS.get("Multi-Color Wild");
        return (String) JOptionPane.showInputDialog(this, "Choose color for wild property:", "Wild Color", JOptionPane.QUESTION_MESSAGE, null, colors, colors[0]);
    }

    /**
     * General color picker dialog
     */
    private String showColorPicker() {
        String[] colors = {"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED", "YELLOW", "GREEN", "BLUE", "PURPLE", "BLACK", "LIGHT_GREEN"};
        return (String) JOptionPane.showInputDialog(this, "Choose color:", "Color Select", JOptionPane.QUESTION_MESSAGE, null, colors, colors[0]);
    }

    /**
     * Handle "Just Say No" reaction request from server
     */
    public void handleReactionRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String targetPlayer = payload.has("targetPlayer") ? payload.get("targetPlayer").getAsString() : "";
                String actionType = payload.has("actionType") ? payload.get("actionType").getAsString() : "";
                int amount = payload.has("amount") ? payload.get("amount").getAsInt() : 0;

                int choice = JOptionPane.showConfirmDialog(this,
                        targetPlayer + " used " + actionType + (amount > 0 ? " asking for " + amount + "M" : "") + ".\nUse Just Say No to cancel?",
                        "Respond to Action", JOptionPane.YES_NO_OPTION);

                JsonObject response = new JsonObject();
                response.addProperty("useJustSayNo", choice == JOptionPane.YES_OPTION);
                client.sendMessage(MessageProtocol.MessageType.PLAY_JUST_SAY_NO, response.toString());
            } catch (Exception e) {}
        });
    }

    /**
     * Show payment required dialog
     */
    public void handlePaymentRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                int amount = payload.has("amount") ? payload.get("amount").getAsInt() : 0;
                String from = payload.has("from") ? payload.get("from").getAsString() : "Unknown";
                JOptionPane.showMessageDialog(this, from + " requires payment of " + amount + "M.", "Payment Required", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {}
        });
    }

    /**
     * Show discard warning when hand is full
     */
    public void handleDiscardRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                int excess = payload.has("excess") ? payload.get("excess").getAsInt() : 1;
                JOptionPane.showMessageDialog(this, "Hand limit exceeded! Discard " + excess + " cards.", "Discard Required", JOptionPane.WARNING_MESSAGE);
            } catch (Exception e) {}
        });
    }

    /**
     * End current turn and notify server
     */
    private void endTurn() {
        stopCountdown();
        client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
        endTurnButton.setEnabled(false);
    }

    /**
     * Show styled message dialog
     */
    private void showStyledMessage(String message, String title, int messageType) {
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    /**
     * Show styled option dialog
     */
    private int showStyledOptionDialog(String message, String title, String[] options) {
        return JOptionPane.showOptionDialog(this, message, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
    }

    /**
     * Floating particle for background animation
     */
    private static class Particle {
        float x, y, vx, vy, alpha, size;

        Particle(int x, int y) {
            this.x = x;
            this.y = y;
            this.vx = (float) (Math.random() - 0.5) * 0.35f;
            this.vy = (float) (Math.random() - 0.5) * 0.35f - 0.12f;
            this.alpha = 12 + (float) Math.random() * 22;
            this.size = 2.2f + (float) Math.random() * 3.2f;
        }

        void update() {
            x += vx;
            y += vy;
            alpha += (float) (Math.random() - 0.5) * 0.35f;

            // Keep values within bounds
            alpha = Math.max(0, Math.min(32, alpha));
            if (x < 0) x = 1400;
            if (x > 1400) x = 0;
            if (y < 0) y = 900;
            if (y > 900) y = 0;
        }
    }
}