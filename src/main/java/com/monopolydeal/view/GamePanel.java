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

public class GamePanel extends JPanel {
    private final GameClient client;
    private JPanel topBarPanel;
    private JPanel mainGamePanel;
    private JPanel handPanel;
    private JPanel sidePanel;
    private JLabel phaseLabel;
    private JLabel turnLabel;
    private JLabel timerLabel;
    private JLabel drawPileLabel;
    private JButton endTurnButton;
    private JPanel playerPanelsContainer;
    private Map<String, PlayerPanel> playerPanels;
    private JPanel handCardsPanel;
    private ActionHistoryPanel actionHistoryPanel;
    private String localPlayerId;
    private boolean isMyTurn;
    private javax.swing.Timer countdownTimer;
    private int secondsRemaining;
    private JsonObject cardDataForClicked;

    private float glowPhase = 0f;
    private javax.swing.Timer ambientTimer;
    private final List<Particle> particles = new ArrayList<>();
    private final Random rng = new Random();

    private static final Color BG_DEEP = new Color(8, 10, 18);
    private static final Color GOLD_PRIMARY = new Color(255, 215, 0);
    private static final Color GOLD_GLOW = new Color(255, 235, 100);
    private static final Color RED_ACCENT = new Color(244, 67, 54);
    private static final Color RED_DARK = new Color(180, 30, 30);
    private static final Color GREEN_FELT = new Color(22, 62, 38);
    private static final Color GREEN_SHADOW = new Color(10, 32, 18);
    private static final Color TEXT_WHITE = new Color(240, 240, 248);
    private static final Color TEXT_GRAY = new Color(150, 150, 170);
    private static final Color PANEL_BG = new Color(16, 18, 30);

    private static final Map<String, String[]> WILD_COLOR_OPTIONS = new LinkedHashMap<>();
    static {
        WILD_COLOR_OPTIONS.put("Multi-Color Wild", new String[]{"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED", "YELLOW", "GREEN", "BLUE", "PURPLE", "BLACK", "LIGHT_GREEN"});
        WILD_COLOR_OPTIONS.put("Dark Blue/Green Wild", new String[]{"BLUE", "GREEN"});
        WILD_COLOR_OPTIONS.put("Red/Yellow Wild", new String[]{"RED", "YELLOW"});
        WILD_COLOR_OPTIONS.put("Brown/Light Blue Wild", new String[]{"BROWN", "LIGHT_BLUE"});
        WILD_COLOR_OPTIONS.put("Orange/Pink Wild", new String[]{"ORANGE", "PINK"});
        WILD_COLOR_OPTIONS.put("Light Green/Black Wild", new String[]{"LIGHT_GREEN", "BLACK"});
    }

    public GamePanel(GameClient client) {
        this.client = client;
        this.playerPanels = new LinkedHashMap<>();
        this.isMyTurn = false;
        this.secondsRemaining = 30;
        setLayout(new BorderLayout());
        setBackground(BG_DEEP);
        createTopBar();
        createMainGameArea();
        createHandPanel();
        createSidePanel();
        add(topBarPanel, BorderLayout.NORTH);
        add(mainGamePanel, BorderLayout.CENTER);
        add(handPanel, BorderLayout.SOUTH);
        add(sidePanel, BorderLayout.EAST);
        initAmbientEffects();
    }

    private void initAmbientEffects() {
        for (int i = 0; i < 30; i++) {
            particles.add(new Particle(rng.nextInt(1200), rng.nextInt(800)));
        }
        ambientTimer = new javax.swing.Timer(40, e -> {
            glowPhase += 0.02f;
            for (Particle p : particles) p.update();
            mainGamePanel.repaint();
            handCardsPanel.repaint();
            timerLabel.repaint();
        });
        ambientTimer.start();
    }

    private void createTopBar() {
        topBarPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(18, 16, 34), 0, getHeight(), new Color(10, 8, 20));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setStroke(new BasicStroke(2f));
                float alpha = 30 + (float)(Math.sin(glowPhase) * 15);
                g2.setColor(new Color(255, 215, 0, (int)alpha));
                g2.drawLine(20, getHeight() - 1, getWidth() - 20, getHeight() - 1);
                g2.setColor(new Color(100, 30, 140, (int)(alpha * 2)));
                g2.drawLine(20, getHeight() - 2, getWidth() - 20, getHeight() - 2);
                int dotSpacing = 40;
                for (int x = dotSpacing; x < getWidth(); x += dotSpacing) {
                    float dotAlpha = 10 + (float)(Math.sin(glowPhase + x * 0.01) * 8);
                    g2.setColor(new Color(255, 215, 0, (int)dotAlpha));
                    g2.fillOval(x, getHeight() - 4, 4, 4);
                }
                g2.dispose();
            }
        };
        topBarPanel.setOpaque(false);
        topBarPanel.setBorder(new EmptyBorder(16, 30, 16, 30));
        topBarPanel.setPreferredSize(new Dimension(0, 70));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 30, 0));
        leftPanel.setOpaque(false);

        phaseLabel = new JLabel("阶段: 等待中");
        phaseLabel.setForeground(GOLD_PRIMARY);
        phaseLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
        phaseLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(100, 30, 140, 100)),
                BorderFactory.createEmptyBorder(0, 0, 0, 15)));

        turnLabel = new JLabel("当前回合: -");
        turnLabel.setForeground(Color.WHITE);
        turnLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 16));

        leftPanel.add(phaseLabel);
        leftPanel.add(turnLabel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 25, 0));
        rightPanel.setOpaque(false);

        drawPileLabel = new JLabel("牌堆: 0") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                GradientPaint gp = new GradientPaint(0, 0, new Color(40, 35, 65), 0, getHeight(), new Color(25, 20, 45));
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 15, 15);
                g2.setColor(TEXT_WHITE);
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2, (getHeight() + fm.getAscent()) / 2 - 2);
                g2.dispose();
            }
        };
        drawPileLabel.setForeground(TEXT_WHITE);
        drawPileLabel.setPreferredSize(new Dimension(90, 32));
        drawPileLabel.setHorizontalAlignment(SwingConstants.CENTER);

        timerLabel = new JLabel("30") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = 26, cy = 26, r = 23;
                g2.setColor(new Color(0, 0, 0, 80));
                g2.fillOval(cx - r + 2, cy - r + 2, r * 2, r * 2);
                GradientPaint bgGp = new GradientPaint(cx - r, cy - r, new Color(35, 30, 55), cx + r, cy + r, new Color(20, 18, 40));
                g2.setPaint(bgGp);
                g2.fillOval(cx - r, cy - r, r * 2, r * 2);
                float pulse = 1f + (float)(Math.sin(glowPhase * 3) * 0.06);
                g2.setStroke(new BasicStroke(2.5f * pulse, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(255, 255, 255, 15));
                g2.drawOval(cx - r, cy - r, r * 2, r * 2);
                int angle = (int)(360 * secondsRemaining / 30.0);
                g2.setStroke(new BasicStroke(3f * pulse, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(timerLabel.getForeground());
                g2.drawArc(cx - r + 2, cy - r + 2, r * 2 - 4, r * 2 - 4, 90, -angle);
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                String text = getText();
                g2.drawString(text, cx - fm.stringWidth(text) / 2, cy + fm.getAscent() / 2 - 1);
                g2.setColor(new Color(255, 255, 255, (int)(20 + Math.sin(glowPhase * 2) * 10)));
                g2.fillOval(cx - 6, cy - r + 6, 5, 5);
                g2.dispose();
            }
        };
        timerLabel.setForeground(GOLD_PRIMARY);
        timerLabel.setPreferredSize(new Dimension(52, 52));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        endTurnButton = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 25, 25);
                if (getModel().isPressed()) {
                    g2.setColor(RED_DARK);
                } else if (getModel().isRollover() && isEnabled()) {
                    GradientPaint gp = new GradientPaint(0, 0, new Color(240, 70, 70), 0, h, RED_ACCENT);
                    g2.setPaint(gp);
                } else {
                    GradientPaint gp = new GradientPaint(0, 0, isEnabled() ? RED_ACCENT : new Color(60, 60, 60), 0, h, isEnabled() ? RED_DARK : new Color(40, 40, 40));
                    g2.setPaint(gp);
                }
                g2.fill(shape);
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(255, 255, 255, isEnabled() ? 40 : 10));
                g2.draw(shape);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Microsoft YaHei", Font.BOLD, 13));
                FontMetrics fm = g2.getFontMetrics();
                String text = "结束回合";
                g2.drawString(text, (w - fm.stringWidth(text)) / 2, (h + fm.getAscent()) / 2 - 2);
                g2.dispose();
            }
        };
        endTurnButton.setPreferredSize(new Dimension(130, 42));
        endTurnButton.setBorderPainted(false);
        endTurnButton.setContentAreaFilled(false);
        endTurnButton.setFocusPainted(false);
        endTurnButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        endTurnButton.setEnabled(false);
        endTurnButton.addActionListener(e -> endTurn());

        rightPanel.add(drawPileLabel);
        rightPanel.add(timerLabel);
        rightPanel.add(endTurnButton);

        topBarPanel.add(leftPanel, BorderLayout.WEST);
        topBarPanel.add(rightPanel, BorderLayout.EAST);
    }

    private void createMainGameArea() {
        mainGamePanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(getWidth() / 2f, 0, GREEN_SHADOW, getWidth() / 2f, getHeight(), GREEN_FELT);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 4));
                g2.setStroke(new BasicStroke(0.8f));
                for (int x = 0; x < getWidth(); x += 100) {
                    for (int y = 0; y < getHeight(); y += 100) {
                        g2.drawRoundRect(x + 5, y + 5, 90, 90, 12, 12);
                    }
                }
                float ringAlpha = 5 + (float)(Math.sin(glowPhase * 0.5) * 4);
                g2.setColor(new Color(255, 215, 0, (int)ringAlpha));
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{10, 25}, 0));
                int cx = getWidth() / 2, cy = getHeight() / 2;
                g2.drawOval(cx - 160, cy - 160, 320, 320);
                g2.drawOval(cx - 200, cy - 200, 400, 400);
                for (Particle p : particles) {
                    if (p.y > cy - 200 && p.y < cy + 200 && p.x > cx - 200 && p.x < cx + 200) {
                        g2.setColor(new Color(255, 215, 0, (int)(p.alpha * 0.4)));
                        g2.fillOval((int)p.x, (int)p.y, (int) p.size, (int) p.size);
                    }
                }
                g2.dispose();
            }
        };
        mainGamePanel.setOpaque(false);
        playerPanelsContainer = new JPanel();
        playerPanelsContainer.setLayout(new BoxLayout(playerPanelsContainer, BoxLayout.Y_AXIS));
        playerPanelsContainer.setOpaque(false);
        playerPanelsContainer.setBorder(new EmptyBorder(15, 20, 15, 20));
        JScrollPane scrollPane = new JScrollPane(playerPanelsContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        mainGamePanel.add(scrollPane, BorderLayout.CENTER);
    }

    private void createHandPanel() {
        handPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(20, 18, 36), 0, getHeight(), new Color(10, 8, 20));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setStroke(new BasicStroke(2f));
                GradientPaint lineGp = new GradientPaint(0, 0, GOLD_PRIMARY, getWidth(), 0, new Color(100, 30, 140));
                g2.setPaint(lineGp);
                g2.drawLine(0, 0, getWidth(), 0);
                g2.setColor(new Color(255, 255, 255, 10));
                g2.drawLine(0, 1, getWidth(), 1);
                g2.dispose();
            }
        };
        handPanel.setOpaque(false);
        handPanel.setBorder(new EmptyBorder(14, 20, 16, 20));
        handPanel.setPreferredSize(new Dimension(0, 230));

        JPanel handHeader = new JPanel(new BorderLayout());
        handHeader.setOpaque(false);
        JLabel handLabel = new JLabel("你的手牌");
        handLabel.setForeground(GOLD_GLOW);
        handLabel.setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        handHeader.add(handLabel, BorderLayout.WEST);
        handPanel.add(handHeader, BorderLayout.NORTH);

        handCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 3));
                g2.fillRoundRect(5, 5, getWidth() - 10, getHeight() - 10, 20, 20);
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(new Color(255, 255, 255, 8));
                g2.drawRoundRect(5, 5, getWidth() - 10, getHeight() - 10, 20, 20);
                g2.dispose();
            }
        };
        handCardsPanel.setOpaque(false);
        JScrollPane handScrollPane = new JScrollPane(handCardsPanel);
        handScrollPane.setOpaque(false);
        handScrollPane.getViewport().setOpaque(false);
        handScrollPane.setBorder(null);
        handScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        handScrollPane.setPreferredSize(new Dimension(0, 180));
        handScrollPane.getHorizontalScrollBar().setUnitIncrement(20);
        handPanel.add(handScrollPane, BorderLayout.CENTER);
    }

    private void createSidePanel() {
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBackground(new Color(10, 12, 20));
        sidePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(80, 60, 120, 120)),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        sidePanel.setPreferredSize(new Dimension(280, 0));
        actionHistoryPanel = new ActionHistoryPanel();
        sidePanel.add(actionHistoryPanel, BorderLayout.CENTER);
    }

    public void updateGameState(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject gameState = JsonParser.parseString(jsonPayload).getAsJsonObject();
                if (gameState.has("viewerId")) {
                    String myId = gameState.get("viewerId").getAsString();
                    if (!myId.equals(localPlayerId)) localPlayerId = myId;
                }
                String phase = gameState.has("phase") ? gameState.get("phase").getAsString() : "未知";
                phaseLabel.setText("阶段: " + phase);
                String activePlayerId = gameState.has("activePlayerId") ? gameState.get("activePlayerId").getAsString() : "";
                int drawPileSize = gameState.has("drawPileSize") ? gameState.get("drawPileSize").getAsInt() : 0;
                drawPileLabel.setText("牌堆: " + drawPileSize);
                JsonObject playerStates = gameState.has("playerStates") ? gameState.getAsJsonObject("playerStates") : null;
                if (playerStates != null) {
                    updatePlayerPanelsFromStates(playerStates, activePlayerId);
                    updateTurnInfo(activePlayerId, playerStates);
                    updateLocalHand(playerStates);
                }
                JsonArray actions = gameState.has("actionHistory") ? gameState.getAsJsonArray("actionHistory") : null;
                if (actions != null) actionHistoryPanel.updateActions(actions);
            } catch (Exception e) {}
        });
    }

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
            boolean isActive = playerData.has("isActivePlayer") && playerData.get("isActivePlayer").getAsBoolean();
            String nickname = playerData.has("nickname") ? playerData.get("nickname").getAsString() : "未知";
            int handCount = playerData.has("handCount") ? playerData.get("handCount").getAsInt() : 0;
            int bankTotal = playerData.has("bankTotal") ? playerData.get("bankTotal").getAsInt() : 0;
            int completeSets = playerData.has("completeSets") ? playerData.get("completeSets").getAsInt() : 0;
            int remainingPlays = playerData.has("remainingPlays") ? playerData.get("remainingPlays").getAsInt() : 0;
            boolean connected = !playerData.has("isConnected") || playerData.get("isConnected").getAsBoolean();
            Map<String, Integer> propertyColorCounts = new LinkedHashMap<>();
            if (playerData.has("propertyColorCounts")) {
                JsonObject colorCounts = playerData.getAsJsonObject("propertyColorCounts");
                for (Map.Entry<String, JsonElement> colorEntry : colorCounts.entrySet()) {
                    propertyColorCounts.put(colorEntry.getKey(), colorEntry.getValue().getAsInt());
                }
            }
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
        for (String removedId : existingIds) {
            PlayerPanel panel = playerPanels.remove(removedId);
            if (panel != null) playerPanelsContainer.remove(panel);
        }
        playerPanelsContainer.revalidate();
        playerPanelsContainer.repaint();
    }

    private void updateTurnInfo(String activePlayerId, JsonObject playerStates) {
        if (localPlayerId == null) return;
        boolean wasMyTurn = isMyTurn;
        isMyTurn = activePlayerId.equals(localPlayerId);
        String activeNickname = "未知";
        if (playerStates.has(activePlayerId)) {
            JsonObject activeData = playerStates.getAsJsonObject(activePlayerId);
            activeNickname = activeData.has("nickname") ? activeData.get("nickname").getAsString() : "未知";
        }
        turnLabel.setText("当前回合: " + activeNickname);
        if (isMyTurn && !wasMyTurn) {
            startCountdown();
            endTurnButton.setEnabled(true);
            handPanel.setBorder(BorderFactory.createMatteBorder(3, 0, 0, 0, GOLD_PRIMARY));
        } else if (isMyTurn && wasMyTurn) {
            endTurnButton.setEnabled(true);
        } else if (!isMyTurn && wasMyTurn) {
            stopCountdown();
            endTurnButton.setEnabled(false);
            handPanel.setBorder(new EmptyBorder(14, 20, 16, 20));
        }
        for (Component comp : handCardsPanel.getComponents()) {
            if (comp instanceof CardRenderer) comp.setEnabled(isMyTurn);
        }
    }

    private void startCountdown() {
        stopCountdown();
        secondsRemaining = 30;
        timerLabel.setText("30");
        timerLabel.setForeground(GOLD_PRIMARY);
        countdownTimer = new javax.swing.Timer(1000, e -> {
            secondsRemaining--;
            timerLabel.setText(String.valueOf(secondsRemaining));
            if (secondsRemaining <= 10) timerLabel.setForeground(RED_ACCENT);
            if (secondsRemaining <= 0) {
                stopCountdown();
                endTurnButton.setEnabled(false);
                timerLabel.setText("0");
                client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null && countdownTimer.isRunning()) countdownTimer.stop();
        countdownTimer = null;
        timerLabel.setText("--");
        timerLabel.setForeground(TEXT_GRAY);
    }

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

    private void onCardClicked(String cardId, String cardType) {
        if (!isMyTurn) {
            showStyledMessage("还没轮到你的回合！", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] options;
        String[] actions;
        switch (cardType) {
            case "MONEY": options = new String[]{"存入银行"}; actions = new String[]{"PLAY_MONEY"}; break;
            case "PROPERTY": options = new String[]{"放置地产"}; actions = new String[]{"PLAY_PROPERTY"}; break;
            case "RENT": options = new String[]{"收取租金"}; actions = new String[]{"PLAY_RENT"}; break;
            case "ACTION": options = new String[]{"使用行动卡"}; actions = new String[]{"PLAY_ACTION"}; break;
            default: options = new String[]{"存入银行"}; actions = new String[]{"PLAY_MONEY"};
        }
        int choice = showStyledOptionDialog("这张卡牌要怎么使用？", "出牌", options);
        if (choice >= 0) {
            JsonObject payload = new JsonObject();
            payload.addProperty("cardId", cardId);
            payload.addProperty("action", actions[choice]);
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

    private void showStyledMessage(String message, String title, int messageType) {
        UIManager.put("OptionPane.background", PANEL_BG);
        UIManager.put("Panel.background", PANEL_BG);
        UIManager.put("OptionPane.messageForeground", Color.WHITE);
        UIManager.put("OptionPane.messageFont", new Font("Microsoft YaHei", Font.PLAIN, 14));
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    private int showStyledOptionDialog(String message, String title, String[] options) {
        UIManager.put("OptionPane.background", PANEL_BG);
        UIManager.put("Panel.background", PANEL_BG);
        UIManager.put("OptionPane.messageForeground", Color.WHITE);
        UIManager.put("OptionPane.messageFont", new Font("Microsoft YaHei", Font.PLAIN, 14));
        UIManager.put("Button.background", new Color(80, 55, 140));
        UIManager.put("Button.foreground", Color.WHITE);
        UIManager.put("Button.font", new Font("Microsoft YaHei", Font.BOLD, 13));
        return JOptionPane.showOptionDialog(this, message, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
    }

    private String showWildColorPicker(String cardName) {
        String[] colors = WILD_COLOR_OPTIONS.get(cardName);
        if (colors == null) colors = WILD_COLOR_OPTIONS.get("Multi-Color Wild");
        return (String) JOptionPane.showInputDialog(this, "为万能地产选择颜色：", "万能地产颜色", JOptionPane.QUESTION_MESSAGE, null, colors, colors[0]);
    }

    private String showColorPicker() {
        String[] colors = {"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED", "YELLOW", "GREEN", "BLUE", "PURPLE", "BLACK", "LIGHT_GREEN"};
        return (String) JOptionPane.showInputDialog(this, "选择颜色：", "颜色选择", JOptionPane.QUESTION_MESSAGE, null, colors, colors[0]);
    }

    public void handleReactionRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String targetPlayer = payload.has("targetPlayer") ? payload.get("targetPlayer").getAsString() : "";
                String actionType = payload.has("actionType") ? payload.get("actionType").getAsString() : "";
                int amount = payload.has("amount") ? payload.get("amount").getAsInt() : 0;
                int choice = JOptionPane.showConfirmDialog(this, targetPlayer + " 使用了 " + actionType + (amount > 0 ? " 索要 " + amount + "M" : "") + "。\n是否使用 Just Say No 拒绝？", "响应操作", JOptionPane.YES_NO_OPTION);
                JsonObject response = new JsonObject();
                response.addProperty("useJustSayNo", choice == JOptionPane.YES_OPTION);
                client.sendMessage(MessageProtocol.MessageType.PLAY_JUST_SAY_NO, response.toString());
            } catch (Exception e) {}
        });
    }

    public void handlePaymentRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                int amount = payload.has("amount") ? payload.get("amount").getAsInt() : 0;
                String from = payload.has("from") ? payload.get("from").getAsString() : "未知";
                JOptionPane.showMessageDialog(this, from + " 要求支付 " + amount + "M。", "需要付款", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {}
        });
    }

    public void handleDiscardRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                int excess = payload.has("excess") ? payload.get("excess").getAsInt() : 1;
                JOptionPane.showMessageDialog(this, "手牌超过7张！需弃掉 " + excess + " 张。", "需要弃牌", JOptionPane.WARNING_MESSAGE);
            } catch (Exception e) {}
        });
    }

    private void endTurn() {
        stopCountdown();
        client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
        endTurnButton.setEnabled(false);
    }

    private static class Particle {
        float x, y, vx, vy, alpha, size;
        Particle(int x, int y) {
            this.x = x;
            this.y = y;
            this.vx = (float)(Math.random() - 0.5) * 0.3f;
            this.vy = (float)(Math.random() - 0.5) * 0.3f - 0.1f;
            this.alpha = 10 + (float)Math.random() * 20;
            this.size = 2 + (float)Math.random() * 3;
        }
        void update() {
            x += vx;
            y += vy;
            alpha += (float)(Math.random() - 0.5) * 0.3f;
            if (alpha < 0) alpha = 0;
            if (alpha > 30) alpha = 30;
            if (x < 0) x = 1200;
            if (x > 1200) x = 0;
            if (y < 0) y = 800;
            if (y > 800) y = 0;
        }
    }
}