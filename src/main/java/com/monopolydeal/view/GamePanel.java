package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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

    public GamePanel(GameClient client) {
        this.client = client;
        this.playerPanels = new LinkedHashMap<>();
        this.isMyTurn = false;
        this.secondsRemaining = 30;
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
        mainGamePanel.add(scrollPane, BorderLayout.CENTER);
    }

    private void createHandPanel() {
        handPanel = new JPanel(new BorderLayout());
        handPanel.setBackground(new Color(30, 30, 30));
        handPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        handPanel.setPreferredSize(new Dimension(0, 200));
        JLabel handLabel = new JLabel("Your Hand");
        handLabel.setForeground(Color.WHITE);
        handLabel.setFont(new Font("Arial", Font.BOLD, 14));
        handPanel.add(handLabel, BorderLayout.NORTH);
        handCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        handCardsPanel.setBackground(new Color(30, 30, 30));
        JScrollPane handScrollPane = new JScrollPane(handCardsPanel);
        handScrollPane.setBorder(null);
        handScrollPane.getViewport().setBackground(new Color(30, 30, 30));
        handScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        handScrollPane.setPreferredSize(new Dimension(0, 180));
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

                if (gameState.has("viewerId")) {
                    String myId = gameState.get("viewerId").getAsString();
                    if (!myId.equals(localPlayerId)) {
                        localPlayerId = myId;
                    }
                }

                String phase = gameState.has("phase") ? gameState.get("phase").getAsString() : "UNKNOWN";
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
            JsonObject simplified = new JsonObject();
            simplified.addProperty("nickname", nickname);
            simplified.addProperty("isActive", isActive);
            simplified.addProperty("handCount", handCount);
            simplified.addProperty("bankTotal", bankTotal);
            simplified.addProperty("completeSets", completeSets);
            simplified.addProperty("remainingPlays", remainingPlays);
            simplified.addProperty("connected", connected);
            panel.updateFromJson(simplified);
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
            endTurnButton.setBackground(new Color(178, 34, 34));
        } else if (isMyTurn && wasMyTurn) {
            endTurnButton.setEnabled(true);
            endTurnButton.setBackground(new Color(178, 34, 34));
        } else if (!isMyTurn && wasMyTurn) {
            stopCountdown();
            endTurnButton.setEnabled(false);
            endTurnButton.setBackground(Color.GRAY);
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
        timerLabel.setText("Time: " + secondsRemaining);
        timerLabel.setForeground(Color.WHITE);

        countdownTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                secondsRemaining--;
                timerLabel.setText("Time: " + secondsRemaining);
                if (secondsRemaining <= 10) {
                    timerLabel.setForeground(Color.RED);
                }
                if (secondsRemaining <= 0) {
                    stopCountdown();
                    endTurnButton.setEnabled(false);
                    timerLabel.setText("Time: 0");
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
        timerLabel.setForeground(Color.WHITE);
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
                card.setPlayListener(id -> onCardClicked(id, cardType));
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
            client.sendMessage(MessageProtocol.MessageType.PLAY_CARD, payload.toString());
        }
    }

    private void endTurn() {
        stopCountdown();
        client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
        endTurnButton.setEnabled(false);
    }
}