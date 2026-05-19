package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Timer;

import com.monopolydeal.client.GameClient;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    private List<JButton> handCardButtons;

    private ActionHistoryPanel actionHistoryPanel;

    private String localPlayerId;
    private boolean isMyTurn;
    private int remainingPlays;
    private Timer countdownTimer;
    private int secondsRemaining;

    private CardSelectionBar cardSelectionBar;
    private final Map<String, String> opponentNicknameMap = new LinkedHashMap<>();

    public GamePanel(GameClient client) {
        this.client = client;
        this.playerPanels = new LinkedHashMap<>();
        this.handCardButtons = new ArrayList<>();
        this.isMyTurn = false;
        this.remainingPlays = 0;

        setLayout(new BorderLayout());
        setBackground(new Color(20, 60, 30));

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
        topBarPanel = new JPanel(new BorderLayout());
        topBarPanel.setBackground(new Color(30, 30, 30));
        topBarPanel.setBorder(new EmptyBorder(10, 20, 10, 20));

        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        leftPanel.setBackground(new Color(30, 30, 30));

        phaseLabel = new JLabel("Phase: Waiting");
        phaseLabel.setForeground(Color.WHITE);
        phaseLabel.setFont(new Font("Arial", Font.BOLD, 16));

        turnLabel = new JLabel("Current Turn: -");
        turnLabel.setForeground(new Color(255, 215, 0));
        turnLabel.setFont(new Font("Arial", Font.BOLD, 16));

        leftPanel.add(phaseLabel);
        leftPanel.add(turnLabel);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        rightPanel.setBackground(new Color(30, 30, 30));

        timerLabel = new JLabel("Time: 30");
        timerLabel.setForeground(Color.WHITE);
        timerLabel.setFont(new Font("Arial", Font.BOLD, 18));

        drawPileLabel = new JLabel("Deck: 0");
        drawPileLabel.setForeground(Color.WHITE);
        drawPileLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        endTurnButton = new JButton("End Turn");
        endTurnButton.setBackground(new Color(178, 34, 34));
        endTurnButton.setForeground(Color.WHITE);
        endTurnButton.setFont(new Font("Arial", Font.BOLD, 14));
        endTurnButton.setFocusPainted(false);
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

                g2.setColor(new Color(20, 70, 35));
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.setColor(new Color(0, 0, 0, 15));
                for(int x=0; x<getWidth(); x+=4) {
                    for(int y=0; y<getHeight(); y+=4) {
                        if((x+y)%3 == 0) g2.fillRect(x, y, 1, 1);
                    }
                }

                drawDeckStack(g2);
                g2.dispose();
            }
        };

        playerPanelsContainer = new JPanel();
        playerPanelsContainer.setLayout(new BoxLayout(playerPanelsContainer, BoxLayout.Y_AXIS));
        playerPanelsContainer.setOpaque(false);
        playerPanelsContainer.setBackground(new Color(0,0,0,0));

        JScrollPane scrollPane = new JScrollPane(playerPanelsContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);

        mainGamePanel.add(scrollPane, BorderLayout.CENTER);
    }


    private void drawDeckStack(Graphics2D g2) {
        int dx = 40, dy = 40;
        for (int i = 4; i >= 0; i--) {
            g2.setColor(new Color(0, 0, 0, 50));
            g2.fillRoundRect(dx + i, dy + i, 90, 130, 12, 12);
            g2.setColor(i == 0 ? new Color(139, 0, 0) : new Color(220, 220, 220));
            g2.fillRoundRect(dx + i - 1, dy + i - 1, 90, 130, 12, 12);
        }
    }

    private void createHandPanel() {
        handPanel = new JPanel(new BorderLayout());
        handPanel.setBackground(new Color(30, 30, 30));
        handPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        handPanel.setPreferredSize(new Dimension(0, 250));

        cardSelectionBar = new CardSelectionBar();
        cardSelectionBar.setPlayCallback((cardId, action, targetId) -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("cardId", cardId);
            payload.addProperty("action", action);
            if (targetId != null) payload.addProperty("targetPlayerId", targetId);
            client.sendMessage(MessageProtocol.MessageType.PLAY_CARD, payload.toString());
        });
        handPanel.add(cardSelectionBar, BorderLayout.NORTH);

        handCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
        handCardsPanel.setBackground(new Color(30, 30, 30));

        JScrollPane handScrollPane = new JScrollPane(handCardsPanel);
        handScrollPane.setBorder(null);
        handScrollPane.getViewport().setBackground(new Color(30, 30, 30));
        handScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);

        handPanel.add(handScrollPane, BorderLayout.CENTER);
    }

    private void createSidePanel() {
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBackground(new Color(40, 40, 40));
        sidePanel.setPreferredSize(new Dimension(250, 0));

        actionHistoryPanel = new ActionHistoryPanel();
        sidePanel.add(actionHistoryPanel, BorderLayout.CENTER);
    }

    public void updateGameState(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject gameState = JsonParser.parseString(jsonPayload).getAsJsonObject();

                String phase = gameState.get("phase").getAsString();
                phaseLabel.setText("Phase: " + phase);

                String activePlayerId = gameState.get("activePlayerId").getAsString();
                int drawPileSize = gameState.get("drawPileSize").getAsInt();
                drawPileLabel.setText("Deck: " + drawPileSize);

                JsonArray players = gameState.getAsJsonArray("players");
                updatePlayerPanels(players);

                boolean gameStarted = gameState.has("gameStarted") &&
                        gameState.get("gameStarted").getAsBoolean();

                if (gameStarted) {
                    updateTurnInfo(activePlayerId, players);
                }

                JsonArray actions = gameState.getAsJsonArray("actionHistory");
                if (actions != null) {
                    actionHistoryPanel.updateActions(actions);
                }

            } catch (Exception e) {
                System.err.println("Error updating game state: " + e.getMessage());
            }
        });
    }

    private void updatePlayerPanels(JsonArray players) {
        Set<String> existingIds = new HashSet<>(playerPanels.keySet());

        for (int i = 0; i < players.size(); i++) {
            JsonObject playerData = players.get(i).getAsJsonObject();
            String playerId = playerData.get("playerId").getAsString();
            existingIds.remove(playerId);

            PlayerPanel panel = playerPanels.get(playerId);
            if (panel == null) {
                panel = new PlayerPanel(playerId);
                playerPanels.put(playerId, panel);
                playerPanelsContainer.add(panel);
            }

            panel.updateFromJson(playerData);

            if (playerData.has("hand") && playerData.get("isActive").getAsBoolean()) {
                updateLocalHand(playerData.getAsJsonArray("hand"));
                remainingPlays = playerData.get("remainingPlays").getAsInt();
            }
        }

        for (String removedId : existingIds) {
            PlayerPanel panel = playerPanels.remove(removedId);
            if (panel != null) {
                playerPanelsContainer.remove(panel);
            }
        }

        opponentNicknameMap.clear();
        for (int i = 0; i < players.size(); i++) {
            JsonObject p = players.get(i).getAsJsonObject();
            String pid = p.get("playerId").getAsString();
            if (!pid.equals(localPlayerId)) {
                opponentNicknameMap.put(pid, p.get("nickname").getAsString());
            }
        }
        cardSelectionBar.updatePlayers(opponentNicknameMap);

        playerPanelsContainer.revalidate();
        playerPanelsContainer.repaint();
    }

    private void updateTurnInfo(String activePlayerId, JsonArray players) {
        isMyTurn = activePlayerId.equals(localPlayerId);

        for (int i = 0; i < players.size(); i++) {
            JsonObject player = players.get(i).getAsJsonObject();
            if (player.get("playerId").getAsString().equals(activePlayerId)) {
                turnLabel.setText("Current Turn: " + player.get("nickname").getAsString());
                break;
            }
        }

        endTurnButton.setEnabled(isMyTurn);

        if (isMyTurn) {
            endTurnButton.setBackground(new Color(178, 34, 34));
            startCountdown(30);
        } else {
            stopCountdown();
            cardSelectionBar.hide();
            timerLabel.setText("Time: --");
        }
        refreshHandInteractivity();
    }


    private void refreshHandInteractivity() {
        for (Component comp : handCardsPanel.getComponents()) {
            if (comp instanceof CardRenderer) {
                comp.setEnabled(isMyTurn);
            }
        }
    }


    private void updateLocalHand(JsonArray handCards) {
        handCardsPanel.removeAll();

        for (int i = 0; i < handCards.size(); i++) {
            JsonObject cardData = handCards.get(i).getAsJsonObject();
            String cardType = cardData.get("cardType").getAsString();
            String cardName = cardData.get("cardName").getAsString();

            CardRenderer card = new CardRenderer(cardData);

            card.setEnabled(isMyTurn);

            card.setPlayListener(id -> onCardClicked(id, cardType, cardName));

            handCardsPanel.add(card);
        }

        handCardsPanel.revalidate();
        handCardsPanel.repaint();
    }


    private void onCardClicked(String cardId, String cardType, String cardName) {
        if (!isMyTurn) return;
        cardSelectionBar.show(cardId, cardName, cardType);
    }

    private String[] getCardOptions(String cardType) {
        switch (cardType) {
            case "MONEY": return new String[]{"PLAY_MONEY"};
            case "PROPERTY": return new String[]{"PLAY_PROPERTY"};
            case "RENT": return new String[]{"PLAY_RENT"};
            case "ACTION": return new String[]{"PLAY_ACTION"};
            default: return new String[]{"PLAY_MONEY"};
        }
    }

    private void endTurn() {
        client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
        endTurnButton.setEnabled(false);
        stopCountdown();
    }

    private void startCountdown(int seconds) {
        stopCountdown();
        secondsRemaining = seconds;

        countdownTimer = new Timer();
        countdownTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    secondsRemaining--;
                    timerLabel.setText("Time: " + secondsRemaining);

                    if (secondsRemaining <= 10) {
                        timerLabel.setForeground(Color.RED);
                    }

                    if (secondsRemaining <= 0) {
                        stopCountdown();
                        endTurnButton.setEnabled(false);
                    }
                });
            }
        }, 1000, 1000);
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
        timerLabel.setForeground(Color.WHITE);
    }

    public void setLocalPlayerId(String playerId) {
        this.localPlayerId = playerId;
    }
}