package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.RoundRectangle2D;
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
        // Set system look and feel for native window chrome
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        initializeUI();         // Build Swing UI
        setupMessageHandler();  // Register message handling callback
    }

    /**
     * Initialize the Swing UI.
     * Create CardLayout container, add LobbyPanel and GamePanel, default to showing lobby.
     */
    private void initializeUI() {
        setTitle("Monopoly Deal Cards Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);  // Exit program on window close
        setSize(1280, 800);
        setMinimumSize(new Dimension(1024, 768));
        setLocationRelativeTo(null);  // Center window on screen

        // Programmatic app icon — render "MD" on a gold/dark card
        BufferedImage iconImg = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D ig2 = iconImg.createGraphics();
        ig2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        ig2.setColor(new Color(20, 22, 38));
        ig2.fill(new RoundRectangle2D.Float(4, 4, 56, 56, 12, 12));
        ig2.setColor(new Color(255, 215, 0));
        ig2.setStroke(new BasicStroke(2f));
        ig2.draw(new RoundRectangle2D.Float(4, 4, 56, 56, 12, 12));
        ig2.setFont(new Font("Segoe UI", Font.BOLD, 26));
        FontMetrics fm = ig2.getFontMetrics();
        ig2.drawString("MD", (64 - fm.stringWidth("MD")) / 2,
                (64 + fm.getAscent() - fm.getDescent()) / 2 - 2);
        ig2.dispose();
        setIconImage(iconImg);

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
     * Handle game over message — show styled winner dialog and return to lobby.
     * @param payload GAME_OVER message JSON payload, contains winnerNickname etc.
     */
    private void handleGameOver(String payload) {
        try {
            JsonObject result = JsonParser.parseString(payload).getAsJsonObject();
            String winnerNickname = result.get("winnerNickname").getAsString();
            String duration = result.has("gameDuration") ? result.get("gameDuration").getAsString() : "";
            int sets = result.has("completeSets") ? result.get("completeSets").getAsInt() : 3;

            JDialog dialog = new JDialog(this, "Game Over", true);
            dialog.setUndecorated(true);
            JPanel panel = new JPanel(new BorderLayout(0, 15));
            panel.setBackground(new Color(14, 16, 28));
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 215, 0, 100), 2, true),
                BorderFactory.createEmptyBorder(30, 40, 25, 40)
            ));

            JLabel trophy = new JLabel("🏆", SwingConstants.CENTER);
            trophy.setFont(new Font("Segoe UI", Font.PLAIN, 48));

            JLabel title = new JLabel("Game Over!", SwingConstants.CENTER);
            title.setFont(new Font("Segoe UI", Font.BOLD, 24));
            title.setForeground(new Color(255, 215, 0));

            JLabel winner = new JLabel(winnerNickname + " Wins!", SwingConstants.CENTER);
            winner.setFont(new Font("Segoe UI", Font.BOLD, 18));
            winner.setForeground(new Color(100, 255, 100));

            String infoText = "Complete Sets: " + sets + "/3";
            if (!duration.isEmpty()) infoText += "  |  Duration: " + duration;
            JLabel info = new JLabel(infoText, SwingConstants.CENTER);
            info.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            info.setForeground(new Color(180, 180, 180));

            JButton okBtn = new JButton("Back to Lobby");
            okBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
            okBtn.setForeground(Color.WHITE);
            okBtn.setBackground(new Color(80, 60, 140));
            okBtn.setFocusPainted(false);
            okBtn.setBorder(BorderFactory.createLineBorder(new Color(140, 120, 200), 1));
            okBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            okBtn.addActionListener(e -> { dialog.dispose(); cardLayout.show(mainPanel, "LOBBY"); });

            JPanel centerPanel = new JPanel(new BorderLayout(0, 8));
            centerPanel.setOpaque(false);
            centerPanel.add(title, BorderLayout.NORTH);
            centerPanel.add(winner, BorderLayout.CENTER);
            centerPanel.add(info, BorderLayout.SOUTH);

            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            btnPanel.setOpaque(false);
            btnPanel.add(okBtn);

            panel.add(trophy, BorderLayout.NORTH);
            panel.add(centerPanel, BorderLayout.CENTER);
            panel.add(btnPanel, BorderLayout.SOUTH);

            dialog.add(panel);
            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Game Over!");
            cardLayout.show(mainPanel, "LOBBY");
        }
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
