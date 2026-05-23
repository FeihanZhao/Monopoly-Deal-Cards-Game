package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

    private static final Color DARK_BG = new Color(18, 22, 28);
    private static final Color DARKER_BG = new Color(14, 17, 22);
    private static final Color GOLD = new Color(255, 215, 0);
    private static final Color RED_GLOW = new Color(220, 50, 50);
    private static final Color GREEN_TABLE = new Color(25, 70, 40);
    private static final Color GREEN_DARK = new Color(15, 50, 28);
    private static final Color TEXT_LIGHT = new Color(220, 220, 220);
    private static final Color TEXT_DIM = new Color(150, 150, 150);

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
        setBackground(DARK_BG);
        createTopBar();
        createMainGameArea();
        createHandPanel();
        createSidePanel();
        add(topBarPanel, BorderLayout.NORTH);
        add(mainGamePanel, BorderLayout.CENTER);
        add(handPanel, BorderLayout.SOUTH);
        add(sidePanel, BorderLayout.EAST);
    }

    private void createTopBar() {
        topBarPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, new Color(25, 30, 40), 0, getHeight(), new Color(18, 22, 28));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 215, 0, 40));
                g2.fillRect(0, getHeight() - 2, getWidth(), 2);
                g2.dispose();
            }
        };
        topBarPanel.setOpaque(false);
        topBarPanel.setBorder(new EmptyBorder(12, 25, 12, 25));
        topBarPanel.setPreferredSize(new Dimension(0, 60));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 0));
        leftPanel.setOpaque(false);

        phaseLabel = new JLabel("PHASE: WAITING");
        phaseLabel.setForeground(GOLD);
        phaseLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        turnLabel = new JLabel("Current Turn: -");
        turnLabel.setForeground(Color.WHITE);
        turnLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));

        leftPanel.add(phaseLabel);
        leftPanel.add(turnLabel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        rightPanel.setOpaque(false);

        drawPileLabel = new JLabel("Deck: 0");
        drawPileLabel.setForeground(TEXT_LIGHT);
        drawPileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        timerLabel = new JLabel("30") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 0, 0, 80));
                g2.fillOval(2, 2, 46, 46);
                g2.setColor(new Color(40, 45, 55));
                g2.fillOval(0, 0, 46, 46);
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(timerLabel.getForeground());
                g2.drawOval(1, 1, 44, 44);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                String text = getText();
                int tx = (50 - fm.stringWidth(text)) / 2;
                int ty = (50 - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);
                g2.dispose();
            }
        };
        timerLabel.setForeground(Color.WHITE);
        timerLabel.setPreferredSize(new Dimension(50, 50));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        endTurnButton = new JButton("END TURN") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(new Color(140, 20, 20));
                } else if (getModel().isRollover() && isEnabled()) {
                    g2.setColor(new Color(200, 40, 40));
                } else {
                    g2.setColor(isEnabled() ? RED_GLOW : new Color(80, 80, 80));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("END TURN", (getWidth() - fm.stringWidth("END TURN")) / 2,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        endTurnButton.setPreferredSize(new Dimension(110, 38));
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
                GradientPaint gp = new GradientPaint(0, 0, GREEN_DARK, getWidth(), getHeight(), GREEN_TABLE);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(255, 255, 255, 3));
                for (int x = 0; x < getWidth(); x += 60) {
                    for (int y = 0; y < getHeight(); y += 60) {
                        g2.drawOval(x, y, 40, 40);
                    }
                }
                g2.dispose();
            }
        };
        mainGamePanel.setOpaque(false);

        playerPanelsContainer = new JPanel();
        playerPanelsContainer.setLayout(new BoxLayout(playerPanelsContainer, BoxLayout.Y_AXIS));
        playerPanelsContainer.setOpaque(false);
        playerPanelsContainer.setBorder(new EmptyBorder(10, 15, 10, 15));

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
                GradientPaint gp = new GradientPaint(0, 0, new Color(22, 26, 32), 0, getHeight(), new Color(16, 19, 24));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 60));
                g2.fillRect(0, 0, getWidth(), 2);
                g2.dispose();
            }
        };
        handPanel.setOpaque(false);
        handPanel.setBorder(new EmptyBorder(10, 15, 12, 15));
        handPanel.setPreferredSize(new Dimension(0, 210));

        JLabel handLabel = new JLabel("YOUR HAND");
        handLabel.setForeground(GOLD);
        handLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        handPanel.add(handLabel, BorderLayout.NORTH);

        handCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        handCardsPanel.setOpaque(false);

        JScrollPane handScrollPane = new JScrollPane(handCardsPanel);
        handScrollPane.setOpaque(false);
        handScrollPane.getViewport().setOpaque(false);
        handScrollPane.setBorder(null);
        handScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        handScrollPane.setPreferredSize(new Dimension(0, 170));
        handScrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        handPanel.add(handScrollPane, BorderLayout.CENTER);
    }

    private void createSidePanel() {
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBackground(DARKER_BG);
        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(50, 55, 65)));
        sidePanel.setPreferredSize(new Dimension(260, 0));
        actionHistoryPanel = new ActionHistoryPanel();
        sidePanel.add(actionHistoryPanel, BorderLayout.CENTER);
    }

    public void updateGameState(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject gameState = JsonParser.parseString(jsonPayload).getAsJsonObject();

                if (gameState.has("viewerId")) {
                    String myId = gameState.get("viewerId").getAsString();
                    if (!myId.equals(localPlayerId)) {
                        localPlayerId = myId;
                    }
                }

                String phase = gameState.has("phase") ? gameState.get("phase").getAsString() : "UNKNOWN";
                phaseLabel.setText("PHASE: " + phase.toUpperCase());

                String activePlayerId = gameState.has("activePlayerId") ? gameState.get("activePlayerId").getAsString() : "";
                int drawPileSize = gameState.has("drawPileSize") ? gameState.get("drawPileSize").getAsInt() : 0;
                drawPileLabel.setText("Deck: " + drawPileSize);

                JsonObject playerStates = gameState.has("playerStates") ? gameState.getAsJsonObject("playerStates") : null;
                if (playerStates != null) {
                    updatePlayerPanelsFromStates(playerStates, activePlayerId);
                    updateTurnInfo(activePlayerId, playerStates);
                    updateLocalHand(playerStates);
                }

                JsonArray actions = gameState.has("actionHistory") ? gameState.getAsJsonArray("actionHistory") : null;
                if (actions != null) {
                    actionHistoryPanel.updateActions(actions);
                }
            } catch (Exception e) {
                System.err.println("Error updating game state: " + e.getMessage());
            }
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
            String nickname = playerData.has("nickname") ? playerData.get("nickname").getAsString() : "Unknown";
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

        String activeNickname = "Unknown";
        if (playerStates.has(activePlayerId)) {
            JsonObject activeData = playerStates.getAsJsonObject(activePlayerId);
            activeNickname = activeData.has("nickname") ? activeData.get("nickname").getAsString() : "Unknown";
        }
        turnLabel.setText("Current Turn: " + activeNickname);

        if (isMyTurn && !wasMyTurn) {
            startCountdown();
            endTurnButton.setEnabled(true);
            handPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD),
                    new EmptyBorder(8, 15, 12, 15)));
        } else if (isMyTurn && wasMyTurn) {
            endTurnButton.setEnabled(true);
        } else if (!isMyTurn && wasMyTurn) {
            stopCountdown();
            endTurnButton.setEnabled(false);
            handPanel.setBorder(new EmptyBorder(10, 15, 12, 15));
        }

        for (Component comp : handCardsPanel.getComponents()) {
            if (comp instanceof CardRenderer) {
                comp.setEnabled(isMyTurn);
            }
        }
    }

    private void startCountdown() {
        stopCountdown();
        secondsRemaining = 30;
        timerLabel.setText("30");
        timerLabel.setForeground(Color.WHITE);

        countdownTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                secondsRemaining--;
                timerLabel.setText(String.valueOf(secondsRemaining));
                if (secondsRemaining <= 10) {
                    timerLabel.setForeground(RED_GLOW);
                }
                if (secondsRemaining <= 0) {
                    stopCountdown();
                    endTurnButton.setEnabled(false);
                    timerLabel.setText("0");
                    client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
                }
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
        countdownTimer = null;
        timerLabel.setText("--");
        timerLabel.setForeground(TEXT_DIM);
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
            JOptionPane.showMessageDialog(this, "It's not your turn!");
            return;
        }
        String[] options;
        switch (cardType) {
            case "MONEY": options = new String[]{"PLAY_MONEY"}; break;
            case "PROPERTY": options = new String[]{"PLAY_PROPERTY"}; break;
            case "RENT": options = new String[]{"PLAY_RENT"}; break;
            case "ACTION": options = new String[]{"PLAY_ACTION"}; break;
            default: options = new String[]{"PLAY_MONEY"};
        }
        int choice = JOptionPane.showOptionDialog(this,
                "What would you like to do with this card?",
                "Play Card",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice >= 0) {
            JsonObject payload = new JsonObject();
            payload.addProperty("cardId", cardId);
            payload.addProperty("action", options[choice]);

            if (cardDataForClicked != null && cardDataForClicked.has("color")) {
                String colorStr = cardDataForClicked.get("color").getAsString();
                String cardName = cardDataForClicked.has("cardName") ? cardDataForClicked.get("cardName").getAsString() : "";

                if ("WILD".equals(colorStr) && "PLAY_PROPERTY".equals(options[choice])) {
                    String selectedColor = showWildColorPicker(cardName);
                    if (selectedColor != null) {
                        payload.addProperty("color", selectedColor);
                    }
                }

                if ("WILD".equals(colorStr) && "PLAY_RENT".equals(options[choice])) {
                    String selectedColor = showColorPicker();
                    if (selectedColor != null) {
                        payload.addProperty("color", selectedColor);
                    }
                }
            }

            client.sendMessage(MessageProtocol.MessageType.PLAY_CARD, payload.toString());
        }
    }

    private String showWildColorPicker(String cardName) {
        String[] colors = WILD_COLOR_OPTIONS.get(cardName);
        if (colors == null) {
            colors = WILD_COLOR_OPTIONS.get("Multi-Color Wild");
        }
        return (String) JOptionPane.showInputDialog(this,
                "Choose a color for this wild property:",
                "Wild Property Color",
                JOptionPane.QUESTION_MESSAGE,
                null, colors, colors[0]);
    }

    private String showColorPicker() {
        String[] colors = {"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED",
                "YELLOW", "GREEN", "BLUE", "PURPLE", "BLACK", "LIGHT_GREEN"};
        return (String) JOptionPane.showInputDialog(this,
                "Choose a color:",
                "Color Selection",
                JOptionPane.QUESTION_MESSAGE,
                null, colors, colors[0]);
    }

    private void endTurn() {
        stopCountdown();
        client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
        endTurnButton.setEnabled(false);
    }
}