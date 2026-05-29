package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import com.monopolydeal.client.GameClient;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Main window — the game client's main UI container.
 *
 * Uses CardLayout to manage two main panels:
 * 1. LobbyPanel — create/join rooms, ready up
 * 2. GamePanel — the actual game interface
 *
 * Acts as a message relay for the client, responsible for:
 * - Registering as the GameClient's message handler
 * - Routing messages to the appropriate panel based on message type (ROOM_UPDATE/GAME_STATE_UPDATE/GAME_OVER/ERROR)
 * - Automatically switching display panels (from lobby to game, or back to lobby after game ends)
 *
 * UI configuration:
 * - Default window size: 1280x800
 * - Minimum window size: 1024x768
 * - Exit on close (EXIT_ON_CLOSE)
 */
public class MainFrame extends JFrame {
    /** Game client connection */
    private final GameClient client;
    /** Card layout manager (used to switch between lobby/game panels) */
    private CardLayout cardLayout;
    /** Main panel (container for all child panels) */
    private JPanel mainPanel;
    /** Lobby panel */
    private LobbyPanel lobbyPanel;
    /** Game panel */
    private GamePanel gamePanel;
    /** Local player ID (set after receiving server messages) */
    private String localPlayerId;

    /**
     * Constructor — initialize UI and set up message handler.
     * @param client connected GameClient instance
     */
    public MainFrame(GameClient client) {
        this.client = client;
        this.localPlayerId = null;
        initializeUI();         // Build Swing UI
        setupMessageHandler();  // Register message handling callback
    }

    /**
     * Initialize the Swing UI.
     * Create CardLayout container, add LobbyPanel and GamePanel, default to showing lobby.
     */
    private void initializeUI() {
        setTitle("MONOPOLY DEAL ★ Premium Card Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);  // Exit program on window close
        setSize(1280, 800);
        setMinimumSize(new Dimension(1024, 768));
        setLocationRelativeTo(null);  // Center window on screen

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        lobbyPanel = new LobbyPanel(client);   // Lobby panel
        gamePanel = new GamePanel(client);     // Game panel

        mainPanel.add(lobbyPanel, "LOBBY");
        mainPanel.add(gamePanel, "GAME");
        add(mainPanel);

        cardLayout.show(mainPanel, "LOBBY");  // Default to lobby view
    }

    /**
     * Set up the message handler — register with GameClient to receive server messages.
     *
     * Message routing rules:
     * - ROOM_UPDATE → forward to LobbyPanel to update room state
     * - GAME_STATE_UPDATE → switch to game panel and update game state
     * - GAME_OVER → show winner info and return to lobby
     * - ERROR → show error dialog
     *
     * All UI operations go through SwingUtilities.invokeLater to ensure EDT execution.
     */
    private void setupMessageHandler() {
        client.setMessageHandler(message -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    MessageProtocol.MessageType type = MessageProtocol.getType(message);
                    String payload = MessageProtocol.getPayload(message);

                    switch (type) {
                        case ROOM_UPDATE:
                            // Lobby panel updates room info (player list, ready state, etc.)
                            lobbyPanel.updateRoom(payload);
                            break;
                        case GAME_STATE_UPDATE:
                            // Switch to game panel and update game state
                            cardLayout.show(mainPanel, "GAME");
                            gamePanel.updateGameState(payload);
                            break;
                        case GAME_OVER:
                            // Game over, show winner and return to lobby
                            handleGameOver(payload);
                            break;
                        case REACTION_REQUIRED:
                            // Just Say No reaction request
                            cardLayout.show(mainPanel, "GAME");
                            gamePanel.handleReactionRequired(payload);
                            break;
                        case PAYMENT_REQUIRED:
                            // Payment request
                            cardLayout.show(mainPanel, "GAME");
                            gamePanel.handlePaymentRequired(payload);
                            break;
                        case DISCARD_REQUIRED:
                            // Discard request (hand exceeds limit at end of turn)
                            cardLayout.show(mainPanel, "GAME");
                            gamePanel.handleDiscardRequired(payload);
                            break;
                        case ERROR:
                            // Show error message
                            handleError(payload);
                            break;
                        default:
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("Error processing message: " + e.getMessage());
                }
            });
        });
    }

    /**
     * Handle game over message — show winner dialog and return to lobby.
     * @param payload GAME_OVER message JSON payload, contains winnerNickname etc.
     */
    private void handleGameOver(String payload) {
        try {
            JsonObject result = JsonParser.parseString(payload).getAsJsonObject();
            String winnerNickname = result.get("winnerNickname").getAsString();
            JOptionPane.showMessageDialog(this, "Game Over!\nWinner: " + winnerNickname);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Game Over!");
        }
        cardLayout.show(mainPanel, "LOBBY");  // Return to lobby
    }

    /**
     * Handle error message — show error dialog.
     * @param payload ERROR message JSON payload, contains message field
     */
    private void handleError(String payload) {
        try {
            JsonObject error = JsonParser.parseString(payload).getAsJsonObject();
            String errorMessage = error.has("message") ?
                    error.get("message").getAsString() : "Unknown error";
            JOptionPane.showMessageDialog(this, errorMessage, "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "An error occurred", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
