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
import com.monopolydeal.model.GameState;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

/**
 * Game panel — the main game interface during gameplay.
 *
 * Layout structure (top to bottom):
 * 1. Top bar (topBarPanel) — phase label, current turn, countdown, draw pile count, end turn button
 * 2. Main game area (mainGamePanel) — green table background, displays all players' PlayerPanels
 * 3. Hand area (handPanel) — bottom player hand card display, horizontally scrollable
 * 4. Sidebar (sidePanel) — right-side action history panel
 *
 * Core workflow:
 * - On every GAME_STATE_UPDATE message, updateGameState() rebuilds the entire interface
 * - Parses all player states, hand info (only own cards visible), and action history from JSON
 * - Activates hand interaction and countdown when it becomes the local player's turn
 */
public class GamePanel extends JPanel {
    /** Game client connection */
    private final GameClient client;
    /** Top bar panel */
    private JPanel topBarPanel;
    /** Main game area panel */
    private JPanel mainGamePanel;
    /** Hand area panel */
    private JPanel handPanel;
    /** Sidebar panel */
    private JPanel sidePanel;
    /** Phase label */
    private JLabel phaseLabel;
    /** Current turn label */
    private JLabel turnLabel;
    /** Countdown progress bar panel */
    private TimerBarPanel timerBarPanel;
    /** Draw pile remaining count label */
    private JLabel drawPileLabel;
    /** End turn button */
    private JButton endTurnButton;
    /** Player panel container */
    private JPanel playerPanelsContainer;
    /** Player panel map key=playerId, value=PlayerPanel */
    private Map<String, PlayerPanel> playerPanels;
    /** Hand card panel */
    private JPanel handCardsPanel;
    /** Action history panel */
    private ActionHistoryPanel actionHistoryPanel;
    /** Card selection action bar */
    private CardSelectionBar cardSelectionBar;
    /** Local player ID */
    private String localPlayerId;
    /** Whether it is the local player's turn */
    private boolean isMyTurn;
    /** Countdown timer */
    private javax.swing.Timer countdownTimer;
    /** View model of the clicked card */
    private CardViewModel cardDataForClicked;

    /**
     * Constructor — create the four main areas of the game interface.
     * @param client connected GameClient instance
     */
    public GamePanel(GameClient client) {
        this.client = client;
        this.playerPanels = new LinkedHashMap<>();
        this.isMyTurn = false;

        setLayout(new BorderLayout());
        setBackground(AppTheme.BG_DARK);

        createTopBar();
        createMainGameArea();
        createHandPanel();
        createSidePanel();

        // Create CardSelectionBar and set callback
        cardSelectionBar = new CardSelectionBar();
        cardSelectionBar.setPlayCallback((cardId, action) -> {
            onCardActionConfirmed(cardId, action);
        });

        // Wrap CardSelectionBar and handPanel in a south wrapper
        JPanel southWrapper = new JPanel(new BorderLayout());
        southWrapper.setOpaque(false);
        southWrapper.add(cardSelectionBar, BorderLayout.NORTH);
        southWrapper.add(handPanel, BorderLayout.CENTER);

        // Assemble the four areas
        add(topBarPanel, BorderLayout.NORTH);
        add(mainGamePanel, BorderLayout.CENTER);
        add(southWrapper, BorderLayout.SOUTH);
        add(sidePanel, BorderLayout.EAST);
    }

    /** Create the top bar — phase info, current turn, countdown, end turn button */
    private void createTopBar() {
        topBarPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Top-to-bottom dark gradient background
                GradientPaint gp = new GradientPaint(0, 0, new Color(25, 30, 40),
                        0, getHeight(), AppTheme.BG_DARK);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Bottom separator line (gold)
                g2.setColor(AppTheme.GOLD_DIM);
                g2.fillRect(0, getHeight() - 2, getWidth(), 2);
                g2.dispose();
            }
        };
        topBarPanel.setOpaque(false);
        topBarPanel.setBorder(new EmptyBorder(12, 25, 12, 25));
        topBarPanel.setPreferredSize(new Dimension(0, 60));

        // ===== Left info area =====
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 0));
        leftPanel.setOpaque(false);

        // Game phase label
        phaseLabel = new JLabel("Phase: Waiting");
        phaseLabel.setForeground(AppTheme.GOLD);
        phaseLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        // Current turn label
        turnLabel = new JLabel("Turn: -");
        turnLabel.setForeground(Color.WHITE);
        turnLabel.setFont(new Font("SansSerif", Font.BOLD, 15));

        leftPanel.add(phaseLabel);
        leftPanel.add(turnLabel);

        // ===== Right action area =====
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        rightPanel.setOpaque(false);

        // Draw pile count label
        drawPileLabel = new JLabel("Deck: 0");
        drawPileLabel.setForeground(AppTheme.TEXT_PRIMARY);
        drawPileLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        timerBarPanel = new TimerBarPanel(30);

        // End turn button
        endTurnButton = new JButton("End Turn") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) {
                    g2.setColor(new Color(140, 20, 20));
                } else if (getModel().isRollover() && isEnabled()) {
                    g2.setColor(new Color(200, 40, 40));
                } else {
                    g2.setColor(isEnabled() ? AppTheme.RED_DANGER : new Color(80, 80, 80));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("End Turn", (getWidth() - fm.stringWidth("End Turn")) / 2,
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
        rightPanel.add(timerBarPanel);
        rightPanel.add(endTurnButton);

        topBarPanel.add(leftPanel, BorderLayout.WEST);
        topBarPanel.add(rightPanel, BorderLayout.EAST);
    }

    /** Create the main game area — green felt table background + player panel container */
    private void createMainGameArea() {
        mainGamePanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Green gradient table
                GradientPaint gp = new GradientPaint(0, 0, AppTheme.TABLE_GREEN_DARK,
                        getWidth(), getHeight(), AppTheme.TABLE_GREEN);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Subtle felt texture
                g2.setColor(new Color(255, 255, 255, 4));
                int step = 40;
                for (int x = 0; x < getWidth(); x += step) {
                    for (int y = 0; y < getHeight(); y += step) {
                        g2.drawOval(x, y, 30, 30);
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

    /** Create the hand area — bottom card display area */
    private void createHandPanel() {
        handPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Dark gradient background
                GradientPaint gp = new GradientPaint(0, 0, new Color(22, 26, 32),
                        0, getHeight(), new Color(16, 19, 24));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Top separator line (gold glow)
                g2.setColor(AppTheme.GOLD_GLOW);
                g2.fillRect(0, 0, getWidth(), 2);
                g2.dispose();
            }
        };
        handPanel.setOpaque(false);
        handPanel.setBorder(new EmptyBorder(10, 15, 12, 15));
        handPanel.setPreferredSize(new Dimension(0, 210));

        // "Your Hand" label with card count
        JPanel handHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        handHeader.setOpaque(false);

        JLabel handIcon = new JLabel("♠"); // Spade symbol
        handIcon.setForeground(AppTheme.GOLD);
        handIcon.setFont(new Font("SansSerif", Font.BOLD, 16));

        JLabel handLabel = new JLabel("Your Hand");
        handLabel.setForeground(AppTheme.GOLD);
        handLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        handHeader.add(handIcon);
        handHeader.add(handLabel);
        handPanel.add(handHeader, BorderLayout.NORTH);

        // Card panel (wrap layout)
        handCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 8));
        handCardsPanel.setOpaque(false);

        JScrollPane handScrollPane = new JScrollPane(handCardsPanel);
        handScrollPane.setOpaque(false);
        handScrollPane.getViewport().setOpaque(false);
        handScrollPane.setBorder(null);
        handScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        handScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        handScrollPane.setPreferredSize(new Dimension(0, 180));

        handPanel.add(handScrollPane, BorderLayout.CENTER);
    }

    /** Create the sidebar — right-side action history panel */
    private void createSidePanel() {
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBackground(AppTheme.BG_DARKER);
        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, AppTheme.BG_BORDER));
        sidePanel.setPreferredSize(new Dimension(260, 0));
        actionHistoryPanel = new ActionHistoryPanel();
        sidePanel.add(actionHistoryPanel, BorderLayout.CENTER);
    }

    /**
     * Update game state — called on every GAME_STATE_UPDATE message.
     */
    public void updateGameState(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject gameState = JsonParser.parseString(jsonPayload).getAsJsonObject();

                // Update local player ID
                if (gameState.has("viewerId")) {
                    String myId = gameState.get("viewerId").getAsString();
                    if (!myId.equals(localPlayerId)) {
                        localPlayerId = myId;
                    }
                }

                // Update phase and deck info
                String phase = gameState.has("phase") ? gameState.get("phase").getAsString() : "Unknown";
                phaseLabel.setText("Phase: " + phase.toUpperCase());

                String activePlayerId = gameState.has("activePlayerId") ?
                        gameState.get("activePlayerId").getAsString() : "";
                int drawPileSize = gameState.has("drawPileSize") ?
                        gameState.get("drawPileSize").getAsInt() : 0;
                drawPileLabel.setText("Deck: " + drawPileSize);

                // Update all player panels
                JsonObject playerStates = gameState.has("playerStates") ?
                        gameState.getAsJsonObject("playerStates") : null;
                if (playerStates != null) {
                    updatePlayerPanelsFromStates(playerStates, activePlayerId);
                    long turnStartTime = gameState.has("turnStartTime") ?
                            gameState.get("turnStartTime").getAsLong() : 0;
                    updateTurnInfo(activePlayerId, playerStates, turnStartTime);
                    updateLocalHand(playerStates);
                }

                // Update action history
                JsonArray actions = gameState.has("actionHistory") ?
                        gameState.getAsJsonArray("actionHistory") : null;
                if (actions != null) {
                    actionHistoryPanel.updateActions(actions);
                }
            } catch (Exception e) {
                System.err.println("Error updating game state: " + e.getMessage());
            }
        });
    }

    /**
     * Handle Just Say No reaction request.
     */
    public void handleReactionRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject req = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String resolutionId = req.get("resolutionId").getAsString();
                String initiatorName = req.get("initiatorName").getAsString();
                String actionType = req.has("actionType") ? req.get("actionType").getAsString() : "";
                String cardName = req.has("cardName") ? req.get("cardName").getAsString() : "";
                int timeout = req.has("timeoutSeconds") ? req.get("timeoutSeconds").getAsInt() : 5;

                // If no Just Say No card in hand, auto-pass
                String jsnCardId = findJustSayNoCardInHand();
                if (jsnCardId == null) {
                    client.sendMessage(MessageProtocol.MessageType.PASS_REACTION, "{}");
                    return;
                }

                String msg = initiatorName + " used " + cardName + " (" + actionType + ") on you!\nPlay Just Say No?";
                String[] options = new String[]{"Play Just Say No", "Pass"};

                JOptionPane pane = new JOptionPane(msg, JOptionPane.QUESTION_MESSAGE,
                        JOptionPane.YES_NO_OPTION, null, options, options[1]);
                JDialog dialog = pane.createDialog(GamePanel.this, "React");

                java.util.Timer timeoutTimer = new java.util.Timer();
                timeoutTimer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        dialog.dispose();
                    }
                }, timeout * 1000L);

                dialog.setVisible(true);
                timeoutTimer.cancel();

                Object selected = pane.getValue();
                if (selected == null || Integer.valueOf(1).equals(selected)) {
                    client.sendMessage(MessageProtocol.MessageType.PASS_REACTION, "{}");
                } else if (Integer.valueOf(0).equals(selected)) {
                    JsonObject jsnPayload = new JsonObject();
                    jsnPayload.addProperty("resolutionId", resolutionId);
                    jsnPayload.addProperty("cardId", jsnCardId);
                    client.sendMessage(MessageProtocol.MessageType.PLAY_JUST_SAY_NO,
                            jsnPayload.toString());
                }
            } catch (Exception e) {
                System.err.println("Error handling REACTION_REQUIRED: " + e.getMessage());
            }
        });
    }

    /**
     * Find the first Just Say No card in the hand.
     */
    private String findJustSayNoCardInHand() {
        for (Component comp : handCardsPanel.getComponents()) {
            if (comp instanceof CardRenderer) {
                CardRenderer cr = (CardRenderer) comp;
                CardViewModel vm = cr.getViewModel();
                if (vm != null && vm.getCardName().contains("Just Say No")) {
                    return vm.getCardId();
                }
            }
        }
        return null;
    }

    /**
     * Handle payment request.
     */
    public void handlePaymentRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject req = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String creditorName = req.get("creditorName").getAsString();
                int amount = req.get("amount").getAsInt();
                JsonArray bankCardsArr = req.getAsJsonArray("bankCards");

                int n = bankCardsArr.size();
                String[] cardDescriptions = new String[n];
                for (int i = 0; i < n; i++) {
                    JsonObject c = bankCardsArr.get(i).getAsJsonObject();
                    String name = c.has("cardName") ? c.get("cardName").getAsString() : "Card";
                    int value = c.has("value") ? c.get("value").getAsInt() : 0;
                    cardDescriptions[i] = name + " (" + value + "M)";
                }

                JList<String> cardList = new JList<>(cardDescriptions);
                cardList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                cardList.setVisibleRowCount(Math.min(8, n));
                JScrollPane scrollPane = new JScrollPane(cardList);

                JPanel panel = new JPanel(new BorderLayout(0, 10));
                panel.add(new JLabel("Pay " + creditorName + " " + amount + "M. Select cards to pay:"),
                        BorderLayout.NORTH);
                panel.add(scrollPane, BorderLayout.CENTER);
                JLabel totalLabel = new JLabel("Selected: 0 M / Required: " + amount + " M");
                panel.add(totalLabel, BorderLayout.SOUTH);

                cardList.addListSelectionListener(e -> {
                    if (e.getValueIsAdjusting()) return;
                    int total = 0;
                    for (int idx : cardList.getSelectedIndices()) {
                        JsonObject c = bankCardsArr.get(idx).getAsJsonObject();
                        total += c.has("value") ? c.get("value").getAsInt() : 0;
                    }
                    totalLabel.setText("Selected: " + total + " M / Required: " + amount + " M");
                });

                int result = JOptionPane.showConfirmDialog(GamePanel.this, panel,
                        "Pay", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                if (result == JOptionPane.OK_OPTION) {
                    JsonObject submitPayload = new JsonObject();
                    JsonArray selectedIds = new JsonArray();
                    for (int idx : cardList.getSelectedIndices()) {
                        JsonObject c = bankCardsArr.get(idx).getAsJsonObject();
                        selectedIds.add(c.get("cardId").getAsString());
                    }
                    submitPayload.add("cardIds", selectedIds);
                    client.sendMessage(MessageProtocol.MessageType.SUBMIT_PAYMENT,
                            submitPayload.toString());
                }

            } catch (Exception e) {
                System.err.println("Error handling PAYMENT_REQUIRED: " + e.getMessage());
            }
        });
    }

    /**
     * Handle discard request.
     */
    public void handleDiscardRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject req = JsonParser.parseString(jsonPayload).getAsJsonObject();
                int discardCount = req.get("discardCount").getAsInt();
                int timeout = req.has("timeoutSeconds") ? req.get("timeoutSeconds").getAsInt() : 15;
                JsonArray handCardsArr = req.getAsJsonArray("handCards");

                int n = handCardsArr.size();
                String[] cardDescriptions = new String[n];
                for (int i = 0; i < n; i++) {
                    JsonObject c = handCardsArr.get(i).getAsJsonObject();
                    String name = c.has("cardName") ? c.get("cardName").getAsString() : "Card";
                    String type = c.has("cardType") ? c.get("cardType").getAsString() : "";
                    cardDescriptions[i] = name + " [" + type + "]";
                }

                JList<String> cardList = new JList<>(cardDescriptions);
                cardList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                cardList.setVisibleRowCount(Math.min(10, n));
                JScrollPane scrollPane = new JScrollPane(cardList);

                JLabel infoLabel = new JLabel("Hand limit exceeded! Select " + discardCount + " cards to discard (" + timeout + "s remaining)");
                infoLabel.setForeground(new Color(255, 200, 100));
                JLabel countLabel = new JLabel("Selected: 0 / Need: " + discardCount);

                cardList.addListSelectionListener(e -> {
                    if (e.getValueIsAdjusting()) return;
                    countLabel.setText("Selected: " + cardList.getSelectedIndices().length + " / Need: " + discardCount);
                });

                JPanel panel = new JPanel(new BorderLayout(0, 10));
                panel.setPreferredSize(new Dimension(350, 280));
                panel.add(infoLabel, BorderLayout.NORTH);
                panel.add(scrollPane, BorderLayout.CENTER);
                panel.add(countLabel, BorderLayout.SOUTH);

                String[] options = new String[]{"Confirm Discard", "Cancel (auto-discard)"};
                JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
                        JOptionPane.OK_CANCEL_OPTION, null, options, options[0]);
                JDialog dialog = pane.createDialog(GamePanel.this, "Discard");

                java.util.Timer countdownTimer = new java.util.Timer();
                final int[] remaining = {timeout};
                countdownTimer.scheduleAtFixedRate(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        remaining[0]--;
                        if (remaining[0] <= 0) {
                            dialog.dispose();
                        } else {
                            SwingUtilities.invokeLater(() ->
                                    infoLabel.setText("Hand limit exceeded! Select " + discardCount + " cards to discard (" + remaining[0] + "s remaining)"));
                        }
                    }
                }, 1000L, 1000L);

                dialog.setVisible(true);
                countdownTimer.cancel();

                Object selected = pane.getValue();
                JsonArray selectedIds = new JsonArray();
                if (selected != null && selected.equals(options[0])) {
                    if (cardList.getSelectedIndices().length < discardCount) {
                        JOptionPane.showMessageDialog(GamePanel.this,
                                "Still need " + (discardCount - cardList.getSelectedIndices().length) + " cards!\nAuto-discard from hand start will be used.",
                                "Notice", JOptionPane.WARNING_MESSAGE);
                    }
                    for (int idx : cardList.getSelectedIndices()) {
                        JsonObject c = handCardsArr.get(idx).getAsJsonObject();
                        selectedIds.add(c.get("cardId").getAsString());
                    }
                }

                JsonObject submitPayload = new JsonObject();
                submitPayload.add("cardIds", selectedIds);
                client.sendMessage(MessageProtocol.MessageType.SUBMIT_DISCARD,
                        submitPayload.toString());

            } catch (Exception e) {
                System.err.println("Error handling DISCARD_REQUIRED: " + e.getMessage());
            }
        });
    }

    /**
     * Update all PlayerPanels from player state JSON.
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

            boolean isActive = playerData.has("isActivePlayer") &&
                    playerData.get("isActivePlayer").getAsBoolean();
            String nickname = playerData.has("nickname") ?
                    playerData.get("nickname").getAsString() : "Unknown";
            int handCount = playerData.has("handCount") ?
                    playerData.get("handCount").getAsInt() : 0;
            int bankTotal = playerData.has("bankTotal") ?
                    playerData.get("bankTotal").getAsInt() : 0;
            int completeSets = playerData.has("completeSets") ?
                    playerData.get("completeSets").getAsInt() : 0;
            int remainingPlays = playerData.has("remainingPlays") ?
                    playerData.get("remainingPlays").getAsInt() : 0;
            boolean connected = !playerData.has("isConnected") ||
                    playerData.get("isConnected").getAsBoolean();

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
            // Pass bank card details to PlayerPanel
            List<GameState.CardInfo> bankCardList = new ArrayList<>();
            if (playerData.has("bankCards")) {
                JsonArray bankArr = playerData.getAsJsonArray("bankCards");
                for (JsonElement bElem : bankArr) {
                    JsonObject bc = bElem.getAsJsonObject();
                    GameState.CardInfo ci = new GameState.CardInfo();
                    ci.setCardId(bc.has("cardId") ? bc.get("cardId").getAsString() : "");
                    ci.setCardName(bc.has("cardName") ? bc.get("cardName").getAsString() : "");
                    ci.setValue(bc.has("value") ? bc.get("value").getAsInt() : 0);
                    bankCardList.add(ci);
                }
            }
            panel.updateFromJson(simplified, propertyColorCounts, bankCardList);
        }

        for (String removedId : existingIds) {
            PlayerPanel panel = playerPanels.remove(removedId);
            if (panel != null) playerPanelsContainer.remove(panel);
        }

        // Sync opponent info to CardSelectionBar
        if (cardSelectionBar != null && localPlayerId != null && playerStates != null) {
            Map<String, String> opponents = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : playerStates.entrySet()) {
                String pid = entry.getKey();
                if (!pid.equals(localPlayerId)) {
                    try {
                        JsonObject pd = entry.getValue().getAsJsonObject();
                        String nick = pd.has("nickname") ? pd.get("nickname").getAsString() : "Unknown";
                        opponents.put(pid, nick);
                    } catch (Exception ignored) {}
                }
            }
            cardSelectionBar.updatePlayers(opponents);

            Map<String, List<String[]>> opponentProps = new LinkedHashMap<>();
            List<String[]> myProps = new ArrayList<>();

            for (Map.Entry<String, JsonElement> entry : playerStates.entrySet()) {
                String pid = entry.getKey();
                JsonObject pd = entry.getValue().getAsJsonObject();
                if (pd.has("propertyCards")) {
                    JsonArray propCards = pd.getAsJsonArray("propertyCards");
                    List<String[]> propList = new ArrayList<>();
                    for (JsonElement elem : propCards) {
                        JsonObject pc = elem.getAsJsonObject();
                        String cId = pc.has("cardId") ? pc.get("cardId").getAsString() : "";
                        String cName = pc.has("cardName") ? pc.get("cardName").getAsString() : "";
                        boolean inComplete = pc.has("inCompleteSet") && pc.get("inCompleteSet").getAsBoolean();
                        propList.add(new String[]{cId, cName, String.valueOf(inComplete)});
                    }
                    if (pid.equals(localPlayerId)) {
                        myProps = propList;
                    } else {
                        opponentProps.put(pid, propList);
                    }
                }
            }
            cardSelectionBar.updateOpponentProperties(opponentProps);
            cardSelectionBar.updateMyProperties(myProps);
        }

        playerPanelsContainer.revalidate();
        playerPanelsContainer.repaint();
    }

    /**
     * Update turn info.
     */
    private void updateTurnInfo(String activePlayerId, JsonObject playerStates, long turnStartTime) {
        if (localPlayerId == null) return;

        boolean wasMyTurn = isMyTurn;
        isMyTurn = activePlayerId.equals(localPlayerId);

        String activeNickname = "Unknown";
        if (playerStates.has(activePlayerId)) {
            JsonObject activeData = playerStates.getAsJsonObject(activePlayerId);
            activeNickname = activeData.has("nickname") ?
                    activeData.get("nickname").getAsString() : "Unknown";
        }
        turnLabel.setText("Turn: " + activeNickname);

        if (isMyTurn && !wasMyTurn) {
            int elapsed = turnStartTime > 0 ?
                    (int)((System.currentTimeMillis() - turnStartTime) / 1000) : 0;
            int remaining = Math.max(1, 30 - elapsed);
            startCountdown(remaining);
            endTurnButton.setEnabled(true);
            handPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(2, 0, 0, 0, AppTheme.GOLD),
                    new EmptyBorder(8, 15, 12, 15)));
        } else if (isMyTurn && wasMyTurn) {
            if (turnStartTime > 0 && countdownTimer != null) {
                int elapsed = (int)((System.currentTimeMillis() - turnStartTime) / 1000);
                int remaining = Math.max(0, 30 - elapsed);
                timerBarPanel.syncTo(remaining);
            }
            endTurnButton.setEnabled(true);
        } else if (!isMyTurn && wasMyTurn) {
            stopCountdown();
            endTurnButton.setEnabled(false);
            cardSelectionBar.dismiss();
            handPanel.setBorder(new EmptyBorder(10, 15, 12, 15));
        }

        for (Component comp : handCardsPanel.getComponents()) {
            if (comp instanceof CardRenderer) {
                comp.setEnabled(isMyTurn);
            }
        }
    }

    /** Start countdown */
    private void startCountdown(int initialSeconds) {
        stopCountdown();
        timerBarPanel.start(initialSeconds);

        countdownTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timerBarPanel.tick();
                if (timerBarPanel.getSecondsRemaining() <= 0) {
                    stopCountdown();
                    endTurnButton.setEnabled(false);
                    client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
                }
            }
        });
        countdownTimer.start();
    }

    /** Stop countdown */
    private void stopCountdown() {
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
        countdownTimer = null;
        timerBarPanel.setInactive();
    }

    /**
     * Update the local player's hand display.
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
                JsonObject json = elem.getAsJsonObject();
                CardViewModel vm = new CardViewModel(
                        json.has("cardId")   ? json.get("cardId").getAsString()   : "",
                        json.has("cardName") ? json.get("cardName").getAsString() : "",
                        json.has("cardType") ? json.get("cardType").getAsString() : "MONEY",
                        json.has("color")    ? json.get("color").getAsString()    : "NONE",
                        json.has("value")    ? json.get("value").getAsInt()       : 0
                );

                CardRenderer card = new CardRenderer(vm);
                card.setEnabled(isMyTurn);

                card.setPlayListener(id -> {
                    cardDataForClicked = vm;
                    onCardClicked(id, vm.getCardType());
                });
                handCardsPanel.add(card);
            }
        }
        handCardsPanel.revalidate();
        handCardsPanel.repaint();
    }

    /**
     * Handle card click event.
     */
    private void onCardClicked(String cardId, String cardType) {
        if (!isMyTurn) {
            JOptionPane.showMessageDialog(this, "It's not your turn!");
            return;
        }

        String cardName = cardDataForClicked != null
                ? cardDataForClicked.getCardName() : "";

        cardSelectionBar.show(cardId, cardName, cardType);
    }

    /**
     * CardSelectionBar confirm callback.
     */
    private void onCardActionConfirmed(String cardId, String action) {
        JsonObject payload = new JsonObject();
        payload.addProperty("cardId", cardId);
        payload.addProperty("action", action);

        String targetId = cardSelectionBar.getSelectedTargetId();
        if (targetId != null && !targetId.isEmpty()) {
            payload.addProperty("targetPlayerId", targetId);
        }

        String targetCardId = cardSelectionBar.getSelectedTargetCardId();
        if (targetCardId != null && !targetCardId.isEmpty()) {
            payload.addProperty("targetCardId", targetCardId);
        }

        String myPropId = cardSelectionBar.getSelectedMyPropertyId();
        if (myPropId != null && !myPropId.isEmpty()) {
            payload.addProperty("myPropertyId", myPropId);
        }

        String theirPropId = cardSelectionBar.getSelectedTheirPropertyId();
        if (theirPropId != null && !theirPropId.isEmpty()) {
            payload.addProperty("theirPropertyId", theirPropId);
        }

        if (cardDataForClicked != null) {
            String colorStr = cardDataForClicked.getColor();
            String cardName = cardDataForClicked.getCardName();

            if ("WILD".equals(colorStr) && "PLAY_PROPERTY".equals(action)) {
                String selectedColor = showWildColorPicker(cardName);
                if (selectedColor != null) {
                    payload.addProperty("color", selectedColor);
                }
            }
            if ("WILD".equals(colorStr) && "PLAY_RENT".equals(action)) {
                String selectedColor = showColorPicker();
                if (selectedColor != null) {
                    payload.addProperty("color", selectedColor);
                }
            }
        }

        client.sendMessage(MessageProtocol.MessageType.PLAY_CARD, payload.toString());
        cardSelectionBar.dismiss();
    }

    /**
     * Show the wild property color picker dialog.
     */
    private String showWildColorPicker(String cardName) {
        String[] colors = AppTheme.WILD_COLOR_OPTIONS.get(cardName);
        if (colors == null) {
            colors = AppTheme.WILD_COLOR_OPTIONS.get("Multi-Color Wild");
        }
        return (String) JOptionPane.showInputDialog(this,
                "Choose a color for this wild property:",
                "Wild Property Color",
                JOptionPane.QUESTION_MESSAGE,
                null, colors, colors[0]);
    }

    /**
     * Show a generic color picker dialog (for wild rent cards).
     */
    private String showColorPicker() {
        String[] colors = {"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED",
                "YELLOW", "GREEN", "BLUE", "BLACK", "LIGHT_GREEN"};
        return (String) JOptionPane.showInputDialog(this,
                "Choose a color:",
                "Color Selection",
                JOptionPane.QUESTION_MESSAGE,
                null, colors, colors[0]);
    }

    /** End turn */
    private void endTurn() {
        stopCountdown();
        cardSelectionBar.dismiss();
        client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
        endTurnButton.setEnabled(false);
    }

    /** Package-visible accessors for testing */
    public String getLocalPlayerId() {return localPlayerId; }
    public boolean getIsMyTurn() { return isMyTurn; }
    public String getPhaseLabelText() { return phaseLabel.getText(); }
    public String getDrawPileLabelText() { return drawPileLabel.getText(); }
    public boolean isEndTurnEnabled() { return endTurnButton.isEnabled(); }
    public int getHandCardCount() { return handCardsPanel.getComponentCount(); }
    public int getPlayerPanelCount() { return playerPanelsContainer.getComponentCount(); }
}
