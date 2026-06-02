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
 *
 * Player interaction flow:
 * 1. Click hand card → show action option bar (play/bank)
 * 2. Wild property card → show color picker dialog
 * 3. Wild rent card → show color picker dialog
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
    /** Phase label (displays current game phase: DRAW/PLAY/END, etc.) */
    private JLabel phaseLabel;
    /** Current turn label (displays the active player's nickname) */
    private JLabel turnLabel;
    /** Countdown progress bar panel */
    private TimerBarPanel timerBarPanel;
    /** Draw pile remaining count label */
    private JLabel drawPileLabel;
    /** End turn button */
    private JButton endTurnButton;
    /** Player panel container (vertical list of all PlayerPanels) */
    private JPanel playerPanelsContainer;
    /** Player panel map key=playerId, value=PlayerPanel */
    private Map<String, PlayerPanel> playerPanels;
    /** Hand card panel (horizontal arrangement of CardRenderer components) */
    private JPanel handCardsPanel;
    /** Action history panel */
    private ActionHistoryPanel actionHistoryPanel;
    /** Card selection action bar (floating above hand area) */
    private CardSelectionBar cardSelectionBar;
    /** Local player ID */
    private String localPlayerId;
    /** Whether it is the local player's turn */
    private boolean isMyTurn;
    /** Countdown timer (fires once per second) */
    private javax.swing.Timer countdownTimer;
    /** View model of the clicked card (for use by the action dialog) */
    private CardViewModel cardDataForClicked;

    // ==================== UI color constants ====================

    private static final Color DARK_BG = new Color(18, 22, 28);         // Main dark background
    private static final Color DARKER_BG = new Color(14, 17, 22);       // Darker background
    private static final Color GOLD = new Color(255, 215, 0);           // Gold (highlight elements)
    private static final Color RED_GLOW = new Color(220, 50, 50);       // Red glow
    private static final Color GREEN_TABLE = new Color(25, 70, 40);     // Green table color
    private static final Color GREEN_DARK = new Color(15, 50, 28);      // Dark green table
    private static final Color TEXT_LIGHT = new Color(220, 220, 220);   // Light text
    private static final Color TEXT_DIM = new Color(150, 150, 150);     // Dimmed text

    /**
     * Constructor — create the four main areas of the game interface.
     * @param client connected GameClient instance
     */
    public GamePanel(GameClient client) {
        this.client = client;
        this.playerPanels = new LinkedHashMap<>();
        this.isMyTurn = false;

        setLayout(new BorderLayout());
        setBackground(DARK_BG);

        createTopBar();        // Create top bar
        createMainGameArea();  // Create main game area
        createHandPanel();     // Create hand area
        createSidePanel();     // Create sidebar

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
                        0, getHeight(), new Color(18, 22, 28));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Bottom separator line (subtle gold)
                g2.setColor(new Color(255, 215, 0, 40));
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

        // Game phase label (gold)
        phaseLabel = new JLabel("Phase: Waiting");
        phaseLabel.setForeground(GOLD);
        phaseLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        // Current turn label (white)
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
        drawPileLabel.setForeground(TEXT_LIGHT);
        drawPileLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        timerBarPanel = new TimerBarPanel(30);

        // End turn button (red rounded, with press and hover effects)
        endTurnButton = new JButton("End Turn") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Choose background color based on state: pressed > hover > normal > disabled
                if (getModel().isPressed()) {
                    g2.setColor(new Color(140, 20, 20));
                } else if (getModel().isRollover() && isEnabled()) {
                    g2.setColor(new Color(200, 40, 40));
                } else {
                    g2.setColor(isEnabled() ? RED_GLOW : new Color(80, 80, 80));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                // Draw white text
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
        endTurnButton.setEnabled(false);  // Initially disabled
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
                // Green gradient table from dark to light
                GradientPaint gp = new GradientPaint(0, 0, GREEN_DARK,
                        getWidth(), getHeight(), GREEN_TABLE);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // Decorative oval texture (poker table style)
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

        // Vertical arrangement container for player panels
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

    /** Create the hand area — bottom horizontally-scrollable card display area */
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
                // Top separator line (subtle gold glow)
                g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 60));
                g2.fillRect(0, 0, getWidth(), 2);
                g2.dispose();
            }
        };
        handPanel.setOpaque(false);
        handPanel.setBorder(new EmptyBorder(10, 15, 12, 15));
        handPanel.setPreferredSize(new Dimension(0, 210));

        // "Your Hand" label
        JLabel handLabel = new JLabel("Your Hand");
        handLabel.setForeground(GOLD);
        handLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        handPanel.add(handLabel, BorderLayout.NORTH);

        // Card panel (wrap layout, cards wrap to next row when width is insufficient)
        handCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 8));
        handCardsPanel.setOpaque(false);

        JScrollPane handScrollPane = new JScrollPane(handCardsPanel);
        handScrollPane.setOpaque(false);
        handScrollPane.getViewport().setOpaque(false);
        handScrollPane.setBorder(null);
        handScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        handScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        handScrollPane.setPreferredSize(new Dimension(0, 170));

        handPanel.add(handScrollPane, BorderLayout.CENTER);
    }

    /** Create the sidebar — right-side action history panel */
    private void createSidePanel() {
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBackground(DARKER_BG);
        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(50, 55, 65)));
        sidePanel.setPreferredSize(new Dimension(260, 0));
        actionHistoryPanel = new ActionHistoryPanel();
        sidePanel.add(actionHistoryPanel, BorderLayout.CENTER);
    }

    /**
     * Update game state — called on every GAME_STATE_UPDATE message.
     * Parses the complete game state JSON and updates all UI components.
     *
     * @param jsonPayload GAME_STATE_UPDATE message JSON payload
     */
    public void updateGameState(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject gameState = JsonParser.parseString(jsonPayload).getAsJsonObject();

                // Update local player ID (viewerId assigned by server)
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
     * Server payload format:
     * {"resolutionId":"...","actionType":"RENT|DEBT_COLLECTOR|...",
     *  "initiatorName":"PlayerName","initiatorId":"...","cardName":"...","timeoutSeconds":5}
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

                // If no Just Say No card in hand, auto-pass without showing the dialog
                String jsnCardId = findJustSayNoCardInHand();
                if (jsnCardId == null) {
                    client.sendMessage(MessageProtocol.MessageType.PASS_REACTION, "{}");
                    return;
                }

                String msg = initiatorName + " used " + cardName + " (" + actionType + ") on you!\nPlay Just Say No?";
                String[] options = new String[]{"Play Just Say No", "Pass"};

                // Modal dialog + independent thread timeout timer
                // Key: keep modal (don't call setModal(false)); setVisible blocks EDT waiting for user input
                // Use java.util.Timer (non-EDT thread) to dispose the dialog on timeout
                JOptionPane pane = new JOptionPane(msg, JOptionPane.QUESTION_MESSAGE,
                        JOptionPane.YES_NO_OPTION, null, options, options[1]);
                JDialog dialog = pane.createDialog(GamePanel.this, "React");

                java.util.Timer timeoutTimer = new java.util.Timer();
                timeoutTimer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        dialog.dispose();   // Window.dispose() is thread-safe
                    }
                }, timeout * 1000L);

                dialog.setVisible(true);    // Block EDT until user clicks or timer disposes
                timeoutTimer.cancel();      // Clear timer

                Object selected = pane.getValue();
                // selected == null → timeout (dialog disposed by timer)
                // Custom selectionValues return the actual String labels, not Integer indices
                if (selected == null || options[1].equals(selected)) {
                    client.sendMessage(MessageProtocol.MessageType.PASS_REACTION, "{}");
                } else if (options[0].equals(selected)) {
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
     * Server payload format:
     * {"creditorName":"Receiver","creditorId":"...","amount":5,"totalBank":10,
     *  "bankCards":[{"cardId":"...","cardName":"...","value":5,...},...]}
     */
    public void handlePaymentRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject req = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String creditorName = req.get("creditorName").getAsString();
                int amount = req.get("amount").getAsInt();
                int totalBank = req.has("totalBank") ? req.get("totalBank").getAsInt() : 0;
                JsonArray bankCardsArr = req.getAsJsonArray("bankCards");

                boolean canAfford = totalBank >= amount;

                // Build info area
                String info;
                if (canAfford) {
                    info = "Pay " + creditorName + " " + amount + "M. Select cards (total ≥ " + amount + "M).";
                } else {
                    info = "Cannot afford " + amount + "M! All " + totalBank + "M will be paid automatically.";
                }

                JPanel panel = new JPanel(new BorderLayout(0, 10));

                JLabel infoLabel = new JLabel("<html><b>" + info + "</b><br>Your bank: " + totalBank + "M</html>");
                panel.add(infoLabel, BorderLayout.NORTH);

                // Build checkbox list for each bank card
                JCheckBox[] checkBoxes = new JCheckBox[bankCardsArr.size()];
                int[] cardValues = new int[bankCardsArr.size()];
                String[] cardIds = new String[bankCardsArr.size()];

                JPanel cardsPanel = new JPanel();
                cardsPanel.setLayout(new BoxLayout(cardsPanel, BoxLayout.Y_AXIS));
                for (int i = 0; i < bankCardsArr.size(); i++) {
                    JsonObject c = bankCardsArr.get(i).getAsJsonObject();
                    String name = c.has("cardName") ? c.get("cardName").getAsString() : "Card";
                    int value = c.has("value") ? c.get("value").getAsInt() : 0;
                    cardValues[i] = value;
                    cardIds[i] = c.has("cardId") ? c.get("cardId").getAsString() : "";

                    JCheckBox cb = new JCheckBox(name + "  (" + value + "M)");
                    cb.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 13));
                    checkBoxes[i] = cb;
                    cardsPanel.add(cb);
                }
                JScrollPane scrollPane = new JScrollPane(cardsPanel);
                scrollPane.setPreferredSize(new Dimension(300, 180));
                panel.add(scrollPane, BorderLayout.CENTER);

                JLabel totalLabel = new JLabel("Selected: 0 M / Required: " + amount + " M");
                totalLabel.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 13));
                panel.add(totalLabel, BorderLayout.SOUTH);

                // Real-time total update on checkbox change
                java.awt.event.ActionListener checkboxListener = e -> {
                    int total = 0;
                    for (int i = 0; i < checkBoxes.length; i++) {
                        if (checkBoxes[i].isSelected()) {
                            total += cardValues[i];
                        }
                    }
                    if (canAfford) {
                        totalLabel.setText("Selected: " + total + " M / Required: " + amount + " M");
                        totalLabel.setForeground(total >= amount ? new java.awt.Color(34, 139, 34) : java.awt.Color.RED);
                    } else {
                        totalLabel.setText("Selected: " + total + " M (all bank will be taken)");
                        totalLabel.setForeground(java.awt.Color.RED);
                    }
                };
                for (JCheckBox cb : checkBoxes) {
                    cb.addActionListener(checkboxListener);
                }

                // If can't afford, auto-select all and disable changes (no choice)
                if (!canAfford) {
                    for (JCheckBox cb : checkBoxes) {
                        cb.setSelected(true);
                        cb.setEnabled(false);
                    }
                    totalLabel.setText("All " + totalBank + " M will be paid (cannot afford " + amount + " M)");
                    totalLabel.setForeground(new java.awt.Color(200, 140, 0));
                }

                int result = JOptionPane.showConfirmDialog(GamePanel.this, panel,
                        "Pay", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                if (result == JOptionPane.OK_OPTION) {
                    JsonObject submitPayload = new JsonObject();
                    JsonArray selectedIds = new JsonArray();
                    int selectedTotal = 0;
                    for (int i = 0; i < checkBoxes.length; i++) {
                        if (checkBoxes[i].isSelected()) {
                            selectedIds.add(cardIds[i]);
                            selectedTotal += cardValues[i];
                        }
                    }

                    if (selectedIds.size() == 0) {
                        JOptionPane.showMessageDialog(GamePanel.this,
                                "Please select at least one card to pay.",
                                "No Selection", JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    if (canAfford && selectedTotal < amount) {
                        JOptionPane.showMessageDialog(GamePanel.this,
                                "Insufficient payment. Need at least " + amount + "M, but selected " + selectedTotal + "M.",
                                "Insufficient", JOptionPane.WARNING_MESSAGE);
                        return;
                    }

                    submitPayload.add("cardIds", selectedIds);
                    client.sendMessage(MessageProtocol.MessageType.SUBMIT_PAYMENT,
                            submitPayload.toString());
                }
                // Cancel: let server handle via timeout

            } catch (Exception e) {
                System.err.println("Error handling PAYMENT_REQUIRED: " + e.getMessage());
            }
        });
    }

    /**
     * Handle discard request (hand exceeds limit at end of turn).
     * Server payload format:
     * {"handCards":[{"cardId":"...","cardName":"Rent Card","cardType":"RENT","color":"RED","value":0},...],
     *  "discardCount":2, "timeoutSeconds":15}
     */
    public void handleDiscardRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject req = JsonParser.parseString(jsonPayload).getAsJsonObject();
                int discardCount = req.get("discardCount").getAsInt();
                int timeout = req.has("timeoutSeconds") ? req.get("timeoutSeconds").getAsInt() : 15;
                JsonArray handCardsArr = req.getAsJsonArray("handCards");

                // Build card description list
                int n = handCardsArr.size();
                String[] cardDescriptions = new String[n];
                for (int i = 0; i < n; i++) {
                    JsonObject c = handCardsArr.get(i).getAsJsonObject();
                    String name = c.has("cardName") ? c.get("cardName").getAsString() : "Card";
                    String type = c.has("cardType") ? c.get("cardType").getAsString() : "";
                    cardDescriptions[i] = name + " [" + type + "]";
                }

                // Hand card multi-select list
                JList<String> cardList = new JList<>(cardDescriptions);
                cardList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                cardList.setVisibleRowCount(Math.min(10, n));
                JScrollPane scrollPane = new JScrollPane(cardList);

                // Info label (with countdown)
                JLabel infoLabel = new JLabel("Hand limit exceeded! Select " + discardCount + " cards to discard (" + timeout + "s remaining)");
                infoLabel.setForeground(new Color(255, 200, 100));
                JLabel countLabel = new JLabel("Selected: 0 / Need: " + discardCount);

                // Selection change listener: update selected count in real-time
                cardList.addListSelectionListener(e -> {
                    if (e.getValueIsAdjusting()) return;
                    countLabel.setText("Selected: " + cardList.getSelectedIndices().length + " / Need: " + discardCount);
                });

                // Assemble panel
                JPanel panel = new JPanel(new BorderLayout(0, 10));
                panel.setPreferredSize(new Dimension(350, 280));
                panel.add(infoLabel, BorderLayout.NORTH);
                panel.add(scrollPane, BorderLayout.CENTER);
                panel.add(countLabel, BorderLayout.SOUTH);

                // Confirm/cancel buttons
                String[] options = new String[]{"Confirm Discard", "Cancel (auto-discard)"};
                JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
                        JOptionPane.OK_CANCEL_OPTION, null, options, options[0]);
                JDialog dialog = pane.createDialog(GamePanel.this, "Discard");

                // Countdown timer (non-EDT thread, disposes dialog on timeout)
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

                dialog.setVisible(true);  // Block EDT until user clicks or timer disposes
                countdownTimer.cancel();

                Object selected = pane.getValue();

                // Build list of selected card IDs
                JsonArray selectedIds = new JsonArray();
                if (selected != null && selected.equals(options[0])) {
                    // User clicked "Confirm Discard"
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
                // Timeout or cancel: selectedIds is empty array; server auto-backfills

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
     * Auto-creates panels for new players, removes panels for departed players.
     */
    private void updatePlayerPanelsFromStates(JsonObject playerStates, String activePlayerId) {
        Set<String> existingIds = new HashSet<>(playerPanels.keySet());
        for (Map.Entry<String, JsonElement> entry : playerStates.entrySet()) {
            String playerId = entry.getKey();
            JsonObject playerData = entry.getValue().getAsJsonObject();
            existingIds.remove(playerId);

            // Create or get PlayerPanel
            PlayerPanel panel = playerPanels.get(playerId);
            if (panel == null) {
                panel = new PlayerPanel(playerId);
                playerPanels.put(playerId, panel);
                playerPanelsContainer.add(panel);
            }

            // Parse player data
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

            // Parse property counts per color
            Map<String, Integer> propertyColorCounts = new LinkedHashMap<>();
            if (playerData.has("propertyColorCounts")) {
                JsonObject colorCounts = playerData.getAsJsonObject("propertyColorCounts");
                for (Map.Entry<String, JsonElement> colorEntry : colorCounts.entrySet()) {
                    propertyColorCounts.put(colorEntry.getKey(), colorEntry.getValue().getAsInt());
                }
            }

            // Build simplified update data
            JsonObject simplified = new JsonObject();
            simplified.addProperty("nickname", nickname);
            simplified.addProperty("isActive", isActive);
            simplified.addProperty("handCount", handCount);
            simplified.addProperty("bankTotal", bankTotal);
            simplified.addProperty("completeSets", completeSets);
            simplified.addProperty("remainingPlays", remainingPlays);
            simplified.addProperty("connected", connected);

            // House/hotel data for PropertySetPanel / PlayerPanel display
            if (playerData.has("houseColors")) {
                simplified.add("houseColors", playerData.getAsJsonObject("houseColors"));
            }
            if (playerData.has("hotelColors")) {
                simplified.add("hotelColors", playerData.getAsJsonObject("hotelColors"));
            }

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

        // Remove departed players' panels
        for (String removedId : existingIds) {
            PlayerPanel panel = playerPanels.remove(removedId);
            if (panel != null) playerPanelsContainer.remove(panel);
        }

        // Sync opponent player info to CardSelectionBar
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

            // Extract property card details for CardSelectionBar (Sly Deal / Forced Deal)
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

            // Pass eligible complete sets for House/Hotel card selection
            List<String> houseEligibleColors = new ArrayList<>();
            List<String> hotelEligibleColors = new ArrayList<>();
            for (Map.Entry<String, JsonElement> entry : playerStates.entrySet()) {
                String pid = entry.getKey();
                if (!pid.equals(localPlayerId)) continue;
                JsonObject pd = entry.getValue().getAsJsonObject();
                if (pd.has("completeSetColors")) {
                    JsonArray csColors = pd.getAsJsonArray("completeSetColors");
                    JsonObject houseMap = pd.has("houseColors") ? pd.getAsJsonObject("houseColors") : null;
                    JsonObject hotelMap = pd.has("hotelColors") ? pd.getAsJsonObject("hotelColors") : null;
                    for (JsonElement elem : csColors) {
                        String colorName = elem.getAsString();
                        // BLACK and LIGHT_GREEN cannot have houses/hotels
                        if ("BLACK".equals(colorName) || "LIGHT_GREEN".equals(colorName)) continue;
                        boolean hasHouse = houseMap != null && houseMap.has(colorName) && houseMap.get(colorName).getAsBoolean();
                        boolean hasHotel = hotelMap != null && hotelMap.has(colorName) && hotelMap.get(colorName).getAsBoolean();
                        if (!hasHotel) {
                            houseEligibleColors.add(colorName);
                        }
                        if (hasHouse && !hasHotel) {
                            hotelEligibleColors.add(colorName);
                        }
                    }
                }
                break;
            }
            cardSelectionBar.updateEligibleSets(houseEligibleColors, hotelEligibleColors);
        }

        playerPanelsContainer.revalidate();
        playerPanelsContainer.repaint();
    }

    /**
     * Update turn info — detect if it's the local player's turn, update active player nickname display.
     */
    private void updateTurnInfo(String activePlayerId, JsonObject playerStates, long turnStartTime) {
        if (localPlayerId == null) return;

        boolean wasMyTurn = isMyTurn;
        isMyTurn = activePlayerId.equals(localPlayerId);

        // Update current turn player nickname
        String activeNickname = "Unknown";
        if (playerStates.has(activePlayerId)) {
            JsonObject activeData = playerStates.getAsJsonObject(activePlayerId);
            activeNickname = activeData.has("nickname") ?
                    activeData.get("nickname").getAsString() : "Unknown";
        }
        turnLabel.setText("Turn: " + activeNickname);

        // Update UI state on turn switch
        if (isMyTurn && !wasMyTurn) {
            int elapsed = turnStartTime > 0 ?
                    (int)((System.currentTimeMillis() - turnStartTime) / 1000) : 0;
            int remaining = Math.max(1, 30 - elapsed);
            // Just became local player's turn: start countdown, enable buttons, highlight hand area
            startCountdown(remaining);
            endTurnButton.setEnabled(true);
            handPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD),
                    new EmptyBorder(8, 15, 12, 15)));
        } else if (isMyTurn && wasMyTurn) {
            // Still local player's turn
            if (turnStartTime > 0 && countdownTimer != null) {
                int elapsed = (int)((System.currentTimeMillis() - turnStartTime) / 1000);
                int remaining = Math.max(0, 30 - elapsed);
                timerBarPanel.syncTo(remaining);
            }
            endTurnButton.setEnabled(true);
        } else if (!isMyTurn && wasMyTurn) {
            // Just ended local player's turn: stop countdown, disable buttons
            stopCountdown();
            endTurnButton.setEnabled(false);
            cardSelectionBar.dismiss();
            handPanel.setBorder(new EmptyBorder(10, 15, 12, 15));
        }

        // Sync hand card interactivity state
        for (Component comp : handCardsPanel.getComponents()) {
            if (comp instanceof CardRenderer) {
                comp.setEnabled(isMyTurn);
            }
        }
    }

    /** Start countdown — create a Swing Timer that fires once per second */
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
     * Extracts own hand cards from playerStates (matching viewerId).
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

                // Set card click callback
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
     * Handle card click event — show the CardSelectionBar action bar.
     *
     * @param cardId clicked card's ID
     * @param cardType card type (MONEY/PROPERTY/RENT/ACTION)
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
     * CardSelectionBar confirm callback — builds and sends the PLAY_CARD message.
     * Reads target and property selections from CardSelectionBar getters.
     *
     * @param cardId card ID
     * @param action action type (PLAY_MONEY/PLAY_PROPERTY/PLAY_RENT/PLAY_ACTION)
     */
    private void onCardActionConfirmed(String cardId, String action) {
        JsonObject payload = new JsonObject();
        payload.addProperty("cardId", cardId);
        payload.addProperty("action", action);

        // Attach target player ID (rent cards, action cards need a target)
        String targetId = cardSelectionBar.getSelectedTargetId();
        if (targetId != null && !targetId.isEmpty()) {
            payload.addProperty("targetPlayerId", targetId);
        }

        // Sly Deal: property card to steal from target
        String targetCardId = cardSelectionBar.getSelectedTargetCardId();
        if (targetCardId != null && !targetCardId.isEmpty()) {
            payload.addProperty("targetCardId", targetCardId);
        }

        // Forced Deal: own property to give up
        String myPropId = cardSelectionBar.getSelectedMyPropertyId();
        if (myPropId != null && !myPropId.isEmpty()) {
            payload.addProperty("myPropertyId", myPropId);
        }

        // Forced Deal: their property to take
        String theirPropId = cardSelectionBar.getSelectedTheirPropertyId();
        if (theirPropId != null && !theirPropId.isEmpty()) {
            payload.addProperty("theirPropertyId", theirPropId);
        }

        // Wild card color selection: show color picker after action confirmed
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

            // House/Hotel card: attach selected set color
            if (cardName.contains("House") && !cardName.contains("Hotel")) {
                String houseColor = cardSelectionBar.getSelectedHouseColor();
                if (houseColor != null && !houseColor.isEmpty()) {
                    payload.addProperty("color", houseColor);
                }
            }
            if (cardName.contains("Hotel")) {
                String hotelColor = cardSelectionBar.getSelectedHotelColor();
                if (hotelColor != null && !hotelColor.isEmpty()) {
                    payload.addProperty("color", hotelColor);
                }
            }
        }

        client.sendMessage(MessageProtocol.MessageType.PLAY_CARD, payload.toString());
        cardSelectionBar.dismiss();
    }

    /**
     * Show the wild property color picker dialog.
     * Provides different selectable color lists based on the specific wild card type (multi-color/dual-color).
     *
     * @param cardName wild card name
     * @return selected color name (null if cancelled)
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
     * @return selected color name
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

    /** End turn — stop countdown and send END_TURN message */
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
