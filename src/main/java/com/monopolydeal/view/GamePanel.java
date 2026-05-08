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
        mainGamePanel = new JPanel(new BorderLayout());
        mainGamePanel.setBackground(new Color(20, 60, 30));

        playerPanelsContainer = new JPanel();
        playerPanelsContainer.setLayout(new BoxLayout(playerPanelsContainer, BoxLayout.Y_AXIS));
        playerPanelsContainer.setBackground(new Color(20, 60, 30));
        playerPanelsContainer.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scrollPane = new JScrollPane(playerPanelsContainer);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(20, 60, 30));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        mainGamePanel.add(scrollPane, BorderLayout.CENTER);
    }

    private void createHandPanel() {
        handPanel = new JPanel(new BorderLayout());
        handPanel.setBackground(new Color(30, 30, 30));
        handPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        handPanel.setPreferredSize(new Dimension(0, 180));

        JLabel handLabel = new JLabel("Your Hand");
        handLabel.setForeground(Color.WHITE);
        handLabel.setFont(new Font("Arial", Font.BOLD, 14));
        handPanel.add(handLabel, BorderLayout.NORTH);

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
            timerLabel.setText("Time: --");
        }
    }

    private void updateLocalHand(JsonArray handCards) {
        handCardsPanel.removeAll();
        handCardButtons.clear();

        for (int i = 0; i < handCards.size(); i++) {
            JsonObject cardData = handCards.get(i).getAsJsonObject();
            String cardName = cardData.get("cardName").getAsString();
            String cardType = cardData.get("cardType").getAsString();
            String cardId = cardData.get("cardId").getAsString();
            int value = cardData.get("value").getAsInt();

            JButton cardButton = createCardButton(cardName, cardType, value, cardId);
            handCardButtons.add(cardButton);
            handCardsPanel.add(cardButton);
        }

        handCardsPanel.revalidate();
        handCardsPanel.repaint();
    }

    private JButton createCardButton(String name, String type, int value, String cardId) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout());

        Color bgColor = getCardColor(type);
        button.setBackground(bgColor);
        button.setPreferredSize(new Dimension(100, 140));
        button.setMaximumSize(new Dimension(100, 140));
        button.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
        button.setFocusPainted(false);

        JLabel nameLabel = new JLabel("<html><center>" + name + "</center></html>");
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 10));
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel typeLabel = new JLabel(type);
        typeLabel.setForeground(Color.WHITE);
        typeLabel.setFont(new Font("Arial", Font.PLAIN, 9));
        typeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        button.add(nameLabel, BorderLayout.CENTER);
        button.add(typeLabel, BorderLayout.SOUTH);

        button.addActionListener(e -> onCardClicked(cardId, type));
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setLocation(button.getX(), button.getY() - 10);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setLocation(button.getX(), button.getY() + 10);
            }
        });

        return button;
    }

    private Color getCardColor(String type) {
        switch (type) {
            case "MONEY": return new Color(34, 139, 34);
            case "PROPERTY": return new Color(70, 130, 180);
            case "RENT": return new Color(255, 140, 0);
            case "ACTION": return new Color(218, 165, 32);
            default: return Color.GRAY;
        }
    }

    private void onCardClicked(String cardId, String cardType) {
        if (!isMyTurn) return;

        String[] options = getCardOptions(cardType);
        int choice = JOptionPane.showOptionDialog(this,
                "What would you like to do with this card?",
                "Play Card",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]);

        if (choice >= 0) {
            JsonObject payload = new JsonObject();
            payload.addProperty("cardId", cardId);
            payload.addProperty("action", options[choice]);
            client.sendMessage(MessageProtocol.MessageType.PLAY_CARD, payload.toString());
        }
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