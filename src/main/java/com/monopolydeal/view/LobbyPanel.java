package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import com.monopolydeal.client.GameClient;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

/**
 * LobbyPanel - Main lobby UI for Monopoly Deal game
 * Handles player login, room creation/joining, ready status, and room member display
 * Uses Swing for GUI and communicates with GameClient for network operations
 */
public class LobbyPanel extends JPanel {
    // Reference to the game client for network communication
    private final GameClient client;

    // UI Input Fields
    private JTextField nicknameField;        // Input for player nickname
    private JTextField roomCodeField;       // Input for room code to join

    // UI Action Buttons
    private JButton createRoomButton;       // Button to create a new game room
    private JButton joinRoomButton;         // Button to join an existing room
    private JButton readyButton;            // Button to mark self as ready/unready
    private JButton leaveButton;            // Button to leave current room

    // Player List Components
    private JList<String> playerList;                 // Visual list of room players
    private DefaultListModel<String> playerListModel;  // Data model for player list

    // Status & Info Labels
    private JLabel roomCodeLabel;           // Displays current room code
    private JLabel statusLabel;             // Displays status/error messages

    // Panel Containers
    private JPanel roomPanel;       // Panel shown when player is inside a room
    private JPanel loginPanel;      // Panel shown for login/room joining

    // State Flags
    private boolean isInRoom;      // Whether player is currently in a game room
    private boolean isReady;       // Whether player has marked themselves ready

    /**
     * Constructor - Initializes lobby panel with game client reference
     * Sets default state and creates main UI panels
     * @param client GameClient instance for network communication
     */
    public LobbyPanel(GameClient client) {
        this.client = client;
        this.isInRoom = false;
        this.isReady = false;
        setLayout(new BorderLayout());
        setBackground(new Color(20, 20, 40));  // Dark theme background

        // Create both panels upfront and show login panel first
        createLoginPanel();
        createRoomPanel();
        add(loginPanel, BorderLayout.CENTER);
    }

    /**
     * Creates the login/entry panel with nickname input, room code input, and main action buttons
     * Includes game title, subtitle, and game rules button
     * Uses gradient background for modern visual style
     */
    private void createLoginPanel() {
        // Login panel with custom gradient background
        loginPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDER, RenderingHints.VALUE_RENDER_QUALITY);
                // Dark purple gradient background
                GradientPaint gradient = new GradientPaint(0, 0, new Color(15, 12, 35),
                        getWidth(), getHeight(), new Color(35, 30, 60));
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        loginPanel.setBorder(new EmptyBorder(60, 60, 60, 60));  // Padding

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);  // Component spacing
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Game Title Label
        JLabel titleLabel = new JLabel("Monopoly Deal");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 52));
        titleLabel.setForeground(new Color(255, 215, 0));  // Gold color
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        loginPanel.add(titleLabel, gbc);

        // Game Subtitle
        JLabel subtitleLabel = new JLabel("The Trading Card Game");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 22));
        subtitleLabel.setForeground(new Color(180, 180, 220));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 1;
        loginPanel.add(subtitleLabel, gbc);

        // Nickname Input Row
        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        JLabel nicknameLabel = new JLabel("Nickname:");
        nicknameLabel.setForeground(new Color(220, 220, 255));
        nicknameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        loginPanel.add(nicknameLabel, gbc);

        gbc.gridx = 1;
        nicknameField = new JTextField(20);
        nicknameField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        // Generate random default nickname
        nicknameField.setText("Player" + (int)(Math.random() * 1000));
        styleTextField(nicknameField);
        loginPanel.add(nicknameField, gbc);

        // Room Code Input Row
        gbc.gridy = 3;
        gbc.gridx = 0;
        JLabel roomLabel = new JLabel("Room Code:");
        roomLabel.setForeground(new Color(220, 220, 255));
        roomLabel.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        loginPanel.add(roomLabel, gbc);

        gbc.gridx = 1;
        roomCodeField = new JTextField(20);
        roomCodeField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        styleTextField(roomCodeField);
        loginPanel.add(roomCodeField, gbc);

        // Create & Join Room Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 15));
        buttonPanel.setOpaque(false);  // Transparent background

        // Create styled gradient buttons
        createRoomButton = createGradientButton("Create Room",
                new Color(34, 186, 157), new Color(27, 156, 133));
        joinRoomButton = createGradientButton("Join Room",
                new Color(72, 133, 237), new Color(58, 112, 207));

        // Attach click listeners
        createRoomButton.addActionListener(e -> createRoom());
        joinRoomButton.addActionListener(e -> joinRoom());

        buttonPanel.add(createRoomButton);
        buttonPanel.add(joinRoomButton);

        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        loginPanel.add(buttonPanel, gbc);

        // Status Message Label
        gbc.gridy = 5;
        statusLabel = new JLabel("Enter nickname to start");
        statusLabel.setForeground(new Color(180, 180, 200));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loginPanel.add(statusLabel, gbc);

        // Game Rules Button
        gbc.gridy = 6;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JButton rulesButton = new JButton("Game Rules");
        rulesButton.setFont(new Font("Microsoft YaHei", Font.BOLD, 14));
        rulesButton.setForeground(new Color(200, 200, 255));
        rulesButton.setBackground(new Color(60, 55, 100));
        rulesButton.setFocusPainted(false);
        rulesButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        rulesButton.setPreferredSize(new Dimension(160, 40));
        rulesButton.addActionListener(e -> showRulesDialog());
        loginPanel.add(rulesButton, gbc);
    }

    /**
     * Displays a scrollable dialog with complete Monopoly Deal game rules
     * Includes objective, setup, turn flow, card types, and winning conditions
     */
    private void showRulesDialog() {
        String rules =
                "MONOPOLY DEAL - GAME RULES\n\n" +
                        "OBJECTIVE\n" +
                        "Be the first player to collect 3 complete property sets.\n" +
                        "A complete set means owning all property cards of the same color.\n\n" +
                        "SETUP\n" +
                        "- 2-5 players\n" +
                        "- Each player starts with 5 cards\n" +
                        "- Draw 3 cards at the beginning of your turn\n\n" +
                        "ON YOUR TURN\n" +
                        "1. Draw Phase: Draw 3 cards from the draw pile\n" +
                        "2. Play Phase: Play up to 3 cards (30 seconds limit)\n" +
                        "3. Discard Phase: If you have more than 7 cards, discard down to 7\n\n" +
                        "CARD TYPES\n" +
                        "- Money Cards (1M-10M): Deposit into your bank for payments\n" +
                        "- Property Cards: Place in your property zone to build sets\n" +
                        "  Wild Property Cards can be assigned to any color\n" +
                        "- Rent Cards: Charge rent to other players\n" +
                        "  Wild Rent targets one player, others target all\n" +
                        "- Action Cards: Special one-time effects\n\n" +
                        "ACTION CARDS\n" +
                        "- Debt Collector: One player pays you 5M\n" +
                        "- Birthday: All players pay you 2M\n" +
                        "- Deal Breaker: Steal a complete property set\n" +
                        "- Pass Go: Draw 2 extra cards\n" +
                        "- Double Rent: Next rent card is doubled\n" +
                        "- Forced Deal: Swap one property with another player\n" +
                        "- Sly Deal: Steal one property (not from a complete set)\n" +
                        "- House/Hotel: Build on complete sets for extra rent\n" +
                        "- Just Say No: Cancel an action against you\n\n" +
                        "PROPERTY SET SIZES\n" +
                        "2 cards: Brown, Light Blue, Blue\n" +
                        "3 cards: Pink, Orange, Red, Yellow, Green, Purple, Light Green\n" +
                        "4 cards: Black\n\n" +
                        "WINNING\n" +
                        "First player to collect 3 complete property sets wins!";

        // Create non-editable text area for rules
        JTextArea textArea = new JTextArea(rules);
        textArea.setEditable(false);
        textArea.setFont(new Font("Microsoft YaHei", Font.PLAIN, 13));
        textArea.setForeground(Color.WHITE);
        textArea.setBackground(new Color(20, 18, 40));
        textArea.setCaretPosition(0);

        // Wrap in scroll pane for long content
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(500, 500));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(100, 60, 160), 2));

        // Show rules dialog
        JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                scrollPane,
                "Game Rules - Monopoly Deal",
                JOptionPane.PLAIN_MESSAGE
        );
    }

    /**
     * Creates the room panel shown after joining/creating a room
     * Displays room code, player list, ready/leave buttons
     * Uses modern dark gradient theme
     */
    private void createRoomPanel() {
        // Room panel with custom gradient background
        roomPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gradient = new GradientPaint(0, 0, new Color(25, 20, 45),
                        getWidth(), getHeight(), new Color(40, 35, 70));
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        roomPanel.setBorder(new EmptyBorder(30, 30, 30, 30));

        // Top panel with room code and action buttons
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        // Room code display label
        roomCodeLabel = new JLabel("Room: -----");
        roomCodeLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        roomCodeLabel.setForeground(new Color(255, 215, 0));
        topPanel.add(roomCodeLabel, BorderLayout.WEST);

        // Top-right buttons (Ready + Leave)
        JPanel topButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topButtonPanel.setOpaque(false);

        // Ready button (green)
        readyButton = createGradientButton("Ready",
                new Color(46, 204, 113), new Color(39, 174, 96));
        readyButton.addActionListener(e -> toggleReady());

        // Leave room button (red)
        leaveButton = createGradientButton("Leave Room",
                new Color(231, 76, 60), new Color(192, 57, 43));
        leaveButton.addActionListener(e -> leaveRoom());

        topButtonPanel.add(readyButton);
        topButtonPanel.add(leaveButton);
        topPanel.add(topButtonPanel, BorderLayout.EAST);

        roomPanel.add(topPanel, BorderLayout.NORTH);

        // Player list model and visual component
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        playerList.setBackground(new Color(60, 55, 100, 180));  // Semi-transparent
        playerList.setForeground(Color.WHITE);
        playerList.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        playerList.setFixedCellHeight(48);
        playerList.setSelectionBackground(new Color(100, 90, 160));
        playerList.setSelectionForeground(Color.WHITE);

        // Wrap player list in scroll pane
        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(120, 110, 180), 2, true));
        roomPanel.add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Creates a modern styled gradient button with hover animation effect
     * @param text Button display text
     * @param start Gradient start color
     * @param end Gradient end color
     * @return Styled JButton instance
     */
    private JButton createGradientButton(String text, Color start, Color end) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                // Custom paint for gradient background
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gradient = new GradientPaint(0, 0, start, 0, getHeight(), end);
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);  // Rounded corners

                // Draw centered white text
                g2d.setColor(Color.WHITE);
                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2d.drawString(getText(), x, y);
            }
        };

        // Button style and behavior settings
        button.setFont(new Font("Segoe UI", Font.BOLD, 16));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(180, 50));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hover size animation effect
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setPreferredSize(new Dimension(190, 52));
                button.revalidate();
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setPreferredSize(new Dimension(180, 50));
                button.revalidate();
            }
        });
        return button;
    }

    /**
     * Applies consistent dark theme styling to text input fields
     * @param field JTextField to style
     */
    private void styleTextField(JTextField field) {
        field.setBackground(new Color(50, 45, 80));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        // Custom border: line border + inner padding
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 90, 150), 2, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        field.setOpaque(true);
    }

    /**
     * Handles Create Room button action
     * Validates nickname input and sends room creation request to server
     */
    private void createRoom() {
        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            setStatus("Please enter nickname", Color.RED);
            return;
        }
        // Send create room request with nickname
        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        client.sendMessage(MessageProtocol.MessageType.CREATE_ROOM, payload.toString());
        setStatus("Creating room...", new Color(255, 200, 0));
    }

    /**
     * Handles Join Room button action
     * Validates nickname and room code, then sends join request
     */
    private void joinRoom() {
        String nickname = nicknameField.getText().trim();
        String roomCode = roomCodeField.getText().trim().toUpperCase();

        // Input validation
        if (nickname.isEmpty()) {
            setStatus("Please enter nickname", Color.RED);
            return;
        }
        if (roomCode.isEmpty()) {
            setStatus("Please enter room code", Color.RED);
            return;
        }

        // Send join room request
        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        payload.addProperty("roomCode", roomCode);
        client.sendMessage(MessageProtocol.MessageType.JOIN_ROOM, payload.toString());
        setStatus("Joining room...", new Color(255, 200, 0));
    }

    /**
     * Toggles player ready/unready status
     * Updates button text/color and sends ready state to server
     */
    private void toggleReady() {
        isReady = !isReady;
        // Change button appearance based on state
        if (isReady) {
            readyButton = createGradientButton("Cancel Ready",
                    new Color(231, 76, 60), new Color(192, 57, 43));
        } else {
            readyButton = createGradientButton("Ready",
                    new Color(46, 204, 113), new Color(39, 174, 96));
        }
        readyButton.addActionListener(e -> toggleReady());

        // Send ready status update to server
        JsonObject payload = new JsonObject();
        payload.addProperty("ready", isReady);
        client.sendMessage(MessageProtocol.MessageType.PLAYER_READY, payload.toString());

        // Refresh UI to show updated button
        topPanelRefresh();
    }

    /**
     * Refreshes the top button panel to show updated ready button
     * Ensures UI reflects current ready state
     */
    private void topPanelRefresh() {
        if (roomPanel != null) {
            JPanel topPanel = (JPanel) roomPanel.getComponent(0);
            JPanel topButtonPanel = (JPanel) topPanel.getComponent(1);
            topButtonPanel.removeAll();
            topButtonPanel.add(readyButton);
            topButtonPanel.add(leaveButton);
            topButtonPanel.revalidate();
            topButtonPanel.repaint();
        }
    }

    /**
     * Handles Leave Room button action
     * Sends leave request to server and returns to login panel
     */
    private void leaveRoom() {
        client.sendMessage(MessageProtocol.MessageType.LEAVE_ROOM, "{}");
        // Reset local state
        isInRoom = false;
        isReady = false;
        // Reset ready button to default state
        readyButton = createGradientButton("Ready",
                new Color(46, 204, 113), new Color(39, 174, 96));
        readyButton.addActionListener(e -> toggleReady());
        // Return to login UI
        showLoginPanel();
    }

    /**
     * Updates room UI from server room update JSON data
     * Runs on Swing EDT thread for thread safety
     * @param jsonPayload Server room state JSON
     */
    public void updateRoom(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String roomCode = payload.get("roomCode").getAsString();
                roomCodeLabel.setText("Room: " + roomCode);

                // Update player list
                JsonArray players = payload.getAsJsonArray("players");
                playerListModel.clear();
                for (JsonElement elem : players) {
                    JsonObject player = elem.getAsJsonObject();
                    String nickname = player.get("nickname").getAsString();
                    boolean ready = player.get("ready").getAsBoolean();
                    boolean isCreator = player.get("isCreator").getAsBoolean();

                    // Build display string with host/ready tags
                    String displayText = nickname;
                    if (isCreator) displayText += " [Host]";
                    displayText += ready ? " [Ready]" : " [Not Ready]";
                    playerListModel.addElement(displayText);
                }

                // Switch to room panel if not already showing
                if (!isInRoom) {
                    isInRoom = true;
                    showRoomPanel();
                }

                // Update status bar
                setStatus("Room: " + roomCode + " | Players: " + players.size(),
                        new Color(46, 204, 113));
            } catch (Exception e) {
                setStatus("Failed to update room info", Color.RED);
            }
        });
    }

    /**
     * Switches UI to show room panel and hide login panel
     */
    private void showRoomPanel() {
        remove(loginPanel);
        add(roomPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /**
     * Switches UI back to login panel and hides room panel
     */
    private void showLoginPanel() {
        remove(roomPanel);
        add(loginPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        setStatus("Enter nickname to start", new Color(180, 180, 200));
    }

    /**
     * Updates status label with message and color
     * @param message Text to display
     * @param color Text color
     */
    private void setStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }
}
