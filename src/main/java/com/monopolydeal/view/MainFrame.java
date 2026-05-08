package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import com.monopolydeal.client.GameClient;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class MainFrame extends JFrame {
    private final GameClient client;
    private CardLayout cardLayout;
    private JPanel mainPanel;

    private LobbyPanel lobbyPanel;
    private GamePanel gamePanel;
    private String localPlayerId;

    public MainFrame(GameClient client) {
        this.client = client;
        this.localPlayerId = null;
        initializeUI();
        setupMessageHandler();
    }

    private void initializeUI() {
        setTitle("Monopoly Deal Cards Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1024, 768));
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        lobbyPanel = new LobbyPanel(client);
        gamePanel = new GamePanel(client);

        mainPanel.add(lobbyPanel, "LOBBY");
        mainPanel.add(gamePanel, "GAME");

        add(mainPanel);
        cardLayout.show(mainPanel, "LOBBY");
    }

    private void setupMessageHandler() {
        client.setMessageHandler(message -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    MessageProtocol.MessageType type = MessageProtocol.getType(message);
                    String payload = MessageProtocol.getPayload(message);

                    switch (type) {
                        case ROOM_UPDATE:
                            lobbyPanel.updateRoom(payload);
                            break;
                        case GAME_STATE_UPDATE:
                            JsonObject stateObj = JsonParser.parseString(payload).getAsJsonObject();
                            if (stateObj.has("activePlayerId")) {
                                String activeId = stateObj.get("activePlayerId").getAsString();
                                if (activeId != null && !activeId.isEmpty()) {
                                    gamePanel.setLocalPlayerId(activeId);
                                }
                            }
                            cardLayout.show(mainPanel, "GAME");
                            gamePanel.updateGameState(payload);
                            break;
                        case GAME_OVER:
                            handleGameOver(payload);
                            break;
                        case ERROR:
                            handleError(payload);
                            break;
                        case TURN_TIMEOUT:
                            break;
                        case GAME_DRAW:
                            handleGameDraw(payload);
                            break;
                        default:
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("Error handling message: " + e.getMessage());
                }
            });
        });
    }

    private void handleGameOver(String payload) {
        try {
            JsonObject result = JsonParser.parseString(payload).getAsJsonObject();
            String winnerNickname = result.get("winnerNickname").getAsString();
            String gameDuration = result.get("gameDuration").getAsString();

            String message = String.format(
                    "Game Over!\n\nWinner: %s\nDuration: %s\n\nWould you like to return to the lobby?",
                    winnerNickname, gameDuration
            );

            int choice = JOptionPane.showConfirmDialog(
                    this,
                    message,
                    "Game Over",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.INFORMATION_MESSAGE
            );

            if (choice == JOptionPane.YES_OPTION) {
                cardLayout.show(mainPanel, "LOBBY");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Game Over!", "Game Over", JOptionPane.INFORMATION_MESSAGE);
            cardLayout.show(mainPanel, "LOBBY");
        }
    }

    private void handleError(String payload) {
        try {
            JsonObject error = JsonParser.parseString(payload).getAsJsonObject();
            String errorMessage = error.has("message") ? error.get("message").getAsString() : "Unknown error";
            JOptionPane.showMessageDialog(
                    this,
                    errorMessage,
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    "An error occurred",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void handleGameDraw(String payload) {
        try {
            JsonObject drawResult = JsonParser.parseString(payload).getAsJsonObject();
            String reason = drawResult.has("reason") ? drawResult.get("reason").getAsString() : "Game ended in a draw";

            JOptionPane.showMessageDialog(
                    this,
                    "Game ended in a draw.\nReason: " + reason,
                    "Game Draw",
                    JOptionPane.INFORMATION_MESSAGE
            );

            cardLayout.show(mainPanel, "LOBBY");
        } catch (Exception e) {
            cardLayout.show(mainPanel, "LOBBY");
        }
    }

    public void showLobby() {
        cardLayout.show(mainPanel, "LOBBY");
    }

    public void showGame() {
        cardLayout.show(mainPanel, "GAME");
    }
}