package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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
    private TimerBarPanel timerBarPanel;
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
    /** Cached hand card data for Just Say No card lookup */
    private final List<JsonObject> cachedHandCards = new ArrayList<>();

    // ==========================================================================
    // PREMIUM COLOR SYSTEM – NEON, GLOW, GRADIENT, DARK ELEGANCE
    // ==========================================================================
    private static final Color BG_DEEP         = new Color(6, 8, 14);
    private static final Color BG_MID          = new Color(14, 16, 28);
    private static final Color BG_CARD         = new Color(20, 22, 38);
    private static final Color GOLD_PRIMARY    = new Color(255, 215, 0);
    private static final Color GOLD_GLOW       = new Color(255, 235, 100);
    private static final Color GOLD_NEON       = new Color(255, 225, 80);
    private static final Color RED_ACCENT      = new Color(255, 50, 50);
    private static final Color RED_GLOW        = new Color(255, 100, 100);
    private static final Color RED_DARK        = new Color(140, 20, 20);
    private static final Color GREEN_FELT      = new Color(18, 52, 32);
    private static final Color GREEN_GLOW     = new Color(40, 180, 100);
    private static final Color GREEN_SHADOW    = new Color(10, 28, 16);
    private static final Color PURPLE_PRIMARY  = new Color(130, 50, 210);
    private static final Color PURPLE_GLOW     = new Color(160, 80, 255);
    private static final Color PURPLE_DARK     = new Color(70, 20, 110);
    private static final Color BLUE_STEEL      = new Color(36, 46, 70);
    private static final Color TEXT_WHITE      = new Color(245, 245, 255);
    private static final Color TEXT_GLOW       = new Color(220, 220, 255);
    private static final Color TEXT_GRAY       = new Color(140, 140, 170);
    private static final Color BORDER_GLOW     = new Color(100, 80, 160);
    private static final Color BORDER_SUBTLE  = new Color(60, 55, 90);

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
    }

    // ==========================================================================
    // TOP BAR – NEON GLowing GRADIENT, LUXURY STYLE
    // ==========================================================================
    private void createTopBar() {
        topBarPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                GradientPaint bg = new GradientPaint(0, 0, BG_MID, 0, getHeight(), BG_DEEP);
                g2.setPaint(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                GradientPaint lineGlow = new GradientPaint(0, 0, GOLD_GLOW, getWidth(), 0, PURPLE_GLOW);
                g2.setPaint(lineGlow);
                g2.drawLine(30, getHeight()-2, getWidth()-30, getHeight()-2);

                g2.setColor(new Color(255,255,255,8));
                for (int x = 40; x < getWidth(); x += 40) {
                    g2.fillOval(x, getHeight()-5, 5,5);
                }
                g2.dispose();
            }
        };
        topBarPanel.setOpaque(false);
        topBarPanel.setBorder(new EmptyBorder(18, 32, 18, 32));
        topBarPanel.setPreferredSize(new Dimension(0, 76));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 32, 0));
        leftPanel.setOpaque(false);

        phaseLabel = new JLabel("Phase: Waiting");
        phaseLabel.setForeground(GOLD_GLOW);
        phaseLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        phaseLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0,0,0,1, new Color(120,80,180,60)),
                BorderFactory.createEmptyBorder(0,0,0,16)
        ));

        turnLabel = new JLabel("Current turn: -");
        turnLabel.setForeground(TEXT_WHITE);
        turnLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));

        leftPanel.add(phaseLabel);
        leftPanel.add(turnLabel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 24, 0));
        rightPanel.setOpaque(false);

        drawPileLabel = new JLabel("Deck: 0") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();

                g2.setColor(new Color(0,0,0,90));
                g2.fillRoundRect(1,1,w-2,h-2,16,16);

                GradientPaint gp = new GradientPaint(0,0, PURPLE_DARK, 0,h, BG_DEEP);
                g2.setPaint(gp);
                g2.fillRoundRect(1,1,w-3,h-3,16,16);

                g2.setColor(BORDER_GLOW);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(1,1,w-3,h-3,16,16);

                g2.setColor(TEXT_GLOW);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(), (w-fm.stringWidth(getText()))/2, (h+fm.getAscent())/2 -2);
                g2.dispose();
            }
        };
        drawPileLabel.setPreferredSize(new Dimension(94, 34));
        drawPileLabel.setHorizontalAlignment(SwingConstants.CENTER);

        timerBarPanel = new TimerBarPanel(30);

        endTurnButton = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0,0,w,h,26,26);

                if (getModel().isPressed()) {
                    g2.setColor(RED_DARK);
                } else if (getModel().isRollover() && isEnabled()) {
                    GradientPaint gp = new GradientPaint(0,0, RED_GLOW, 0,h, RED_ACCENT);
                    g2.setPaint(gp);
                } else {
                    GradientPaint gp = new GradientPaint(0,0, isEnabled()?RED_ACCENT:new Color(50,50,50),
                                                       0,h, isEnabled()?RED_DARK:new Color(35,35,35));
                    g2.setPaint(gp);
                }
                g2.fill(shape);

                g2.setStroke(new BasicStroke(1.8f));
                g2.setColor(new Color(255,255,255, isEnabled()?35:8));
                g2.draw(shape);

                if(isEnabled()){
                    g2.setColor(new Color(255,255,255,20));
                    g2.fill(new RoundRectangle2D.Float(4,4,w-8,h/2-2,20,20));
                }

                g2.setColor(TEXT_WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                String txt = "End Turn";
                g2.drawString(txt, (w-fm.stringWidth(txt))/2, (h+fm.getAscent())/2 -2);
                g2.dispose();
            }
        };
        endTurnButton.setPreferredSize(new Dimension(140, 44));
        endTurnButton.setBorderPainted(false);
        endTurnButton.setContentAreaFilled(false);
        endTurnButton.setFocusPainted(false);
        endTurnButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        endTurnButton.setEnabled(false);
        endTurnButton.addActionListener(e -> endTurn());

        rightPanel.add(drawPileLabel);
        rightPanel.add(timerBarPanel);
        rightPanel.add(endTurnButton);

        topBarPanel.add(leftPanel, BorderLayout.WEST);
        topBarPanel.add(rightPanel, BorderLayout.EAST);
    }

    // ==========================================================================
    // MAIN GAME AREA – CASINO FELT + DYNAMIC LIGHTS + PREMIUM TEXTURE
    // ==========================================================================
    private void createMainGameArea() {
        mainGamePanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                GradientPaint table = new GradientPaint(getWidth()/2f,0, GREEN_SHADOW, getWidth()/2f, getHeight(), GREEN_FELT);
                g2.setPaint(table);
                g2.fillRect(0,0,getWidth(), getHeight());

                g2.setColor(new Color(255,255,255,3));
                for(int x=0;x<getWidth();x+=100)
                    for(int y=0;y<getHeight();y+=100)
                        g2.drawRoundRect(x+8,y+8,84,84,14,14);

                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{12,28},0));
                g2.setColor(new Color(255,215,0,10));
                int cx = getWidth()/2, cy=getHeight()/2;
                g2.drawOval(cx-170,cy-170,340,340);
                g2.drawOval(cx-210,cy-210,420,420);

                g2.setColor(new Color(40,180,100,6));
                g2.drawOval(cx-190,cy-190,380,380);

                g2.dispose();
            }
        };
        mainGamePanel.setOpaque(false);
        playerPanelsContainer = new JPanel();
        playerPanelsContainer.setLayout(new BoxLayout(playerPanelsContainer, BoxLayout.Y_AXIS));
        playerPanelsContainer.setOpaque(false);
        playerPanelsContainer.setBorder(new EmptyBorder(20,24,20,24));

        JScrollPane scroll = new JScrollPane(playerPanelsContainer);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        mainGamePanel.add(scroll, BorderLayout.CENTER);
    }

    // ==========================================================================
    // HAND PANEL – GLASSMORPHISM + NEON TOP BORDER + PREMIUM CARD TRAY
    // ==========================================================================
    private void createHandPanel() {
        handPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint bg = new GradientPaint(0,0, BG_CARD, 0,getHeight(), BG_DEEP);
                g2.setPaint(bg);
                g2.fillRect(0,0,getWidth(),getHeight());

                g2.setStroke(new BasicStroke(3f));
                GradientPaint neon = new GradientPaint(0,0, GOLD_NEON, getWidth(),0, PURPLE_GLOW);
                g2.setPaint(neon);
                g2.drawLine(0,0,getWidth(),0);

                g2.setColor(new Color(255,255,255,10));
                g2.drawLine(0,1,getWidth(),1);
                g2.dispose();
            }
        };
        handPanel.setOpaque(false);
        handPanel.setBorder(new EmptyBorder(16,24,18,24));
        handPanel.setPreferredSize(new Dimension(0, 240));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JLabel handLabel = new JLabel(" Your Hand");
        handLabel.setForeground(GOLD_GLOW);
        handLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        header.add(handLabel, BorderLayout.WEST);
        handPanel.add(header, BorderLayout.NORTH);

        handCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT,16,12)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(255,255,255,4));
                g2.fillRoundRect(6,6,getWidth()-12,getHeight()-12,22,22);

                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(BORDER_GLOW);
                g2.drawRoundRect(6,6,getWidth()-12,getHeight()-12,22,22);

                g2.dispose();
            }
        };
        handCardsPanel.setOpaque(false);

        JScrollPane handScroll = new JScrollPane(handCardsPanel);
        handScroll.setOpaque(false);
        handScroll.getViewport().setOpaque(false);
        handScroll.setBorder(null);
        handScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        handScroll.setPreferredSize(new Dimension(0,190));
        handScroll.getHorizontalScrollBar().setUnitIncrement(24);
        handPanel.add(handScroll, BorderLayout.CENTER);
    }

    // ==========================================================================
    // SIDE PANEL – DARK GLASS WITH NEON BORDER
    // ==========================================================================
    private void createSidePanel() {
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBackground(BG_DEEP);
        sidePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0,2,0,0, BORDER_GLOW),
                new EmptyBorder(0,0,0,0)
        ));
        sidePanel.setPreferredSize(new Dimension(290,0));
        actionHistoryPanel = new ActionHistoryPanel();
        sidePanel.add(actionHistoryPanel, BorderLayout.CENTER);
    }

    // ==========================================================================
    // GAME LOGIC – NO CHANGES, FULLY COMPATIBLE
    // ==========================================================================
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
                String phase = gameState.has("phase") ? gameState.get("phase").getAsString() : "Unknown";
                phaseLabel.setText("Phase: " + phase);
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
        turnLabel.setText("Current turn: " + activeNickname);
        if (isMyTurn && !wasMyTurn) {
            startCountdown();
            endTurnButton.setEnabled(true);
            handPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(3, 0, 0, 0, GOLD_PRIMARY),
                    new EmptyBorder(13, 24, 18, 24)));
        } else if (isMyTurn && wasMyTurn) {
            endTurnButton.setEnabled(true);
        } else if (!isMyTurn && wasMyTurn) {
            stopCountdown();
            endTurnButton.setEnabled(false);
            handPanel.setBorder(new EmptyBorder(16, 24, 18, 24));
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
        timerBarPanel.start(30);

        countdownTimer = new javax.swing.Timer(1000, e -> {
            secondsRemaining--;
            timerBarPanel.tick();

            if (secondsRemaining <= 0) {
                stopCountdown();
                endTurnButton.setEnabled(false);
                client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
        countdownTimer = null;
        if (timerBarPanel != null) {
            timerBarPanel.setInactive();
        }
    }

    private void updateLocalHand(JsonObject playerStates) {
        handCardsPanel.removeAll();
        cachedHandCards.clear();
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
                cachedHandCards.add(cardData);
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
            showStyledMessage("Not your turn yet!", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String[] options;
        String[] actions;
        switch (cardType) {
            case "MONEY": options = new String[]{"Deposit to bank"}; actions = new String[]{"PLAY_MONEY"}; break;
            case "PROPERTY": options = new String[]{"Place property"}; actions = new String[]{"PLAY_PROPERTY"}; break;
            case "RENT": options = new String[]{"Collect rent"}; actions = new String[]{"PLAY_RENT"}; break;
            case "ACTION": options = new String[]{"Use action card"}; actions = new String[]{"PLAY_ACTION"}; break;
            default: options = new String[]{"Deposit to bank"}; actions = new String[]{"PLAY_MONEY"};
        }
        int choice = showStyledOptionDialog("How would you like to use this card?", "Play Card", options);
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
        UIManager.put("OptionPane.background", BG_MID);
        UIManager.put("Panel.background", BG_MID);
        UIManager.put("OptionPane.messageForeground", TEXT_WHITE);
        UIManager.put("OptionPane.messageFont", new Font("Segoe UI", Font.PLAIN, 14));
        JOptionPane.showMessageDialog(this, message, title, messageType);
    }

    private int showStyledOptionDialog(String message, String title, String[] options) {
        UIManager.put("OptionPane.background", BG_MID);
        UIManager.put("Panel.background", BG_MID);
        UIManager.put("OptionPane.messageForeground", TEXT_WHITE);
        UIManager.put("OptionPane.messageFont", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Button.background", PURPLE_PRIMARY);
        UIManager.put("Button.foreground", TEXT_WHITE);
        UIManager.put("Button.font", new Font("Segoe UI", Font.BOLD, 13));
        return JOptionPane.showOptionDialog(this, message, title, JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
    }

    private String showWildColorPicker(String cardName) {
        String[] colors = WILD_COLOR_OPTIONS.get(cardName);
        if (colors == null) colors = WILD_COLOR_OPTIONS.get("Multi-Color Wild");
        return (String) JOptionPane.showInputDialog(this, "Select color for wild property:", "Wild Property Color", JOptionPane.QUESTION_MESSAGE, null, colors, colors[0]);
    }

    private String showColorPicker() {
        String[] colors = {"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED", "YELLOW", "GREEN", "BLUE", "PURPLE", "BLACK", "LIGHT_GREEN"};
        return (String) JOptionPane.showInputDialog(this, "Select color:", "Color Selection", JOptionPane.QUESTION_MESSAGE, null, colors, colors[0]);
    }

    public void handleReactionRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                // Server sends: initiatorName, actionType, cardName, resolutionId
                String initiatorName = payload.has("initiatorName") ? payload.get("initiatorName").getAsString() : "Unknown";
                String actionType = payload.has("actionType") ? payload.get("actionType").getAsString() : "Unknown";
                String cardName = payload.has("cardName") ? payload.get("cardName").getAsString() : "";

                int choice = JOptionPane.showConfirmDialog(this,
                        initiatorName + " used " + cardName + " (" + actionType + ").\nDo you want to use Just Say No to cancel?",
                        "React to Action", JOptionPane.YES_NO_OPTION);

                if (choice == JOptionPane.YES_OPTION) {
                    // Find a Just Say No card in cached hand
                    JsonObject jsnCard = null;
                    synchronized (cachedHandCards) {
                        for (JsonObject card : cachedHandCards) {
                            String name = card.has("cardName") ? card.get("cardName").getAsString() : "";
                            if (name.contains("Just Say No")) {
                                jsnCard = card;
                                break;
                            }
                        }
                    }
                    if (jsnCard != null && jsnCard.has("cardId")) {
                        JsonObject response = new JsonObject();
                        response.addProperty("cardId", jsnCard.get("cardId").getAsString());
                        client.sendMessage(MessageProtocol.MessageType.PLAY_JUST_SAY_NO, response.toString());
                    } else {
                        // No Just Say No card available — auto pass
                        client.sendMessage(MessageProtocol.MessageType.PASS_REACTION, "{}");
                    }
                } else {
                    // Player chose not to use Just Say No
                    client.sendMessage(MessageProtocol.MessageType.PASS_REACTION, "{}");
                }
            } catch (Exception e) {
                System.err.println("Error handling reaction: " + e.getMessage());
            }
        });
    }

    public void handlePaymentRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                int amount = payload.has("amount") ? payload.get("amount").getAsInt() : 0;
                String from = payload.has("from") ? payload.get("from").getAsString() : "Unknown";
                JOptionPane.showMessageDialog(this, from + " demands payment of " + amount + "M.", "Payment Required", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {}
        });
    }

    public void handleDiscardRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                int excess = payload.has("excess") ? payload.get("excess").getAsInt() : 1;
                JOptionPane.showMessageDialog(this, "Hand exceeds 7 cards! Discard " + excess + " card(s).", "Discard Required", JOptionPane.WARNING_MESSAGE);
            } catch (Exception e) {}
        });
    }

    private void endTurn() {
        stopCountdown();
        client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
        endTurnButton.setEnabled(false);
    }
}
