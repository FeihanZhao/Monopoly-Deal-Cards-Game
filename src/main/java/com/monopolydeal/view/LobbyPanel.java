package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import com.monopolydeal.client.GameClient;
import com.monopolydeal.model.GameConstants;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

/**
 * Lobby panel — room management interface shown before the game starts.
 *
 * Provides two view modes:
 * 1. Login view (loginPanel) — enter nickname, create or join a room
 * 2. Room view (roomPanel) — show room code, player list, ready button
 *
 * Flow:
 * 1. Enter nickname → click "Create Room" or "Join Room" (requires room code)
 * 2. Auto-switch to room view after successfully joining a room
 * 3. Click "Ready" button to mark self as ready
 * 4. When all players are ready (≥2 people), the server auto-starts the game
 * 5. MainFrame switches to GamePanel when it receives GAME_STATE_UPDATE
 *
 * UI style: dark theme (deep purple-blue gradient background), gold title, rounded gradient buttons
 */
public class LobbyPanel extends JPanel {
    /** Game client connection */
    private final GameClient client;

    /** Nickname input field */
    private JTextField nicknameField;
    /** Room code input field (used when joining a room) */
    private JTextField roomCodeField;
    /** Create room button */
    private JButton createRoomButton;
    /** Join room button */
    private JButton joinRoomButton;
    /** Ready/unready button */
    private JButton readyButton;
    /** Leave room button */
    private JButton leaveButton;
    /** Start game button (only visible to host) */
    private JButton startGameButton;
    /** Whether the current player is the room host */
    private boolean amICreator = false;
    /** Player list component */
    private JList<String> playerList;
    /** Player list data model */
    private DefaultListModel<String> playerListModel;
    /** Room code label */
    private JLabel roomCodeLabel;
    /** Status hint label */
    private JLabel statusLabel;
    /** Room view panel */
    private JPanel roomPanel;
    /** Login view panel */
    private JPanel loginPanel;

    /** Whether currently in a room */
    private boolean isInRoom;
    /** Whether currently ready */
    private boolean isReady;

    /**
     * Constructor — initialize the lobby UI.
     * @param client connected GameClient instance
     */
    public LobbyPanel(GameClient client) {
        this.client = client;
        this.isInRoom = false;
        this.isReady = false;

        setLayout(new BorderLayout());
        setBackground(new Color(20, 20, 40));  // Dark background

        createLoginPanel();   // Build login view
        createRoomPanel();    // Build room view

        add(loginPanel, BorderLayout.CENTER);  // Default to login view
    }

    /**
     * Create the login view panel — contains title, nickname input, room code input, and action buttons.
     * Uses GridBagLayout with a deep purple-blue gradient background.
     */
    private void createLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                // Top-left to bottom-right deep purple-blue gradient background
                GradientPaint gradient = new GradientPaint(0, 0, new Color(15, 12, 35),
                        getWidth(), getHeight(), new Color(35, 30, 60));
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        loginPanel.setBorder(new EmptyBorder(60, 60, 60, 60));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);  // Component spacing
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ===== Title =====
        JLabel titleLabel = new JLabel("Monopoly Deal");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 52));
        titleLabel.setForeground(new Color(255, 215, 0));  // Gold
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        loginPanel.add(titleLabel, gbc);

        // ===== Subtitle =====
        JLabel subtitleLabel = new JLabel("Premium Card Game");
        subtitleLabel.setFont(new Font("SansSerif", Font.ITALIC, 24));
        subtitleLabel.setForeground(new Color(180, 180, 220));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 1;
        loginPanel.add(subtitleLabel, gbc);

        // ===== Nickname input row =====
        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        JLabel nicknameLabel = new JLabel("Nickname:");
        nicknameLabel.setForeground(new Color(220, 220, 255));
        nicknameLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        loginPanel.add(nicknameLabel, gbc);

        gbc.gridx = 1;
        nicknameField = new JTextField(20);
        nicknameField.setFont(new Font("SansSerif", Font.PLAIN, 18));
        // Generate random default nickname
        nicknameField.setText("Player" + (int)(Math.random() * 1000));
        styleTextField(nicknameField);
        loginPanel.add(nicknameField, gbc);

        // ===== Room code input row =====
        gbc.gridy = 3;
        gbc.gridx = 0;
        JLabel roomLabel = new JLabel("Room Code:");
        roomLabel.setForeground(new Color(220, 220, 255));
        roomLabel.setFont(new Font("SansSerif", Font.PLAIN, 18));
        loginPanel.add(roomLabel, gbc);

        gbc.gridx = 1;
        roomCodeField = new JTextField(20);
        roomCodeField.setFont(new Font("SansSerif", Font.PLAIN, 18));
        styleTextField(roomCodeField);
        loginPanel.add(roomCodeField, gbc);

        // ===== Button panel =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 15));
        buttonPanel.setOpaque(false);

        // Create room button (green gradient)
        createRoomButton = createGradientButton("Create Room",
                new Color(34, 186, 157), new Color(27, 156, 133));
        // Join room button (blue gradient)
        joinRoomButton = createGradientButton("Join Room",
                new Color(72, 133, 237), new Color(58, 112, 207));

        createRoomButton.addActionListener(e -> createRoom());
        joinRoomButton.addActionListener(e -> joinRoom());

        buttonPanel.add(createRoomButton);
        buttonPanel.add(joinRoomButton);

        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        loginPanel.add(buttonPanel, gbc);

        // ===== Status hint label =====
        gbc.gridy = 5;
        statusLabel = new JLabel("Enter nickname to start");
        statusLabel.setForeground(new Color(180, 180, 200));
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 16));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loginPanel.add(statusLabel, gbc);

        // ===== Rules button =====
        gbc.gridy = 5;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JButton rulesButton = new JButton("Rules");
        rulesButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        rulesButton.setForeground(new Color(200, 200, 255));
        rulesButton.setBackground(new Color(60, 55, 100));
        rulesButton.setFocusPainted(false);
        rulesButton.setBorder(BorderFactory.createLineBorder(new Color(120, 110, 180), 2, true));
        rulesButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        rulesButton.setPreferredSize(new Dimension(160, 40));
        rulesButton.addActionListener(e -> showRulesDialog());
        loginPanel.add(rulesButton, gbc);

// ===== Status hint label (shifted to row 6) =====
        gbc.gridy = 6;
        statusLabel = new JLabel("Enter nickname to start");
        statusLabel.setForeground(new Color(180, 180, 200));
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 16));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loginPanel.add(statusLabel, gbc);
    }

    /**
     * Create the room view panel — shown after entering a room.
     * Contains room code, player list, ready/leave buttons.
     */
    private void createRoomPanel() {
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

        // ===== Top info bar =====
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        // Room code label (large gold text)
        roomCodeLabel = new JLabel("Room: -----");
        roomCodeLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        roomCodeLabel.setForeground(new Color(255, 215, 0));
        topPanel.add(roomCodeLabel, BorderLayout.WEST);

        // Ready/Leave buttons
        JPanel topButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topButtonPanel.setOpaque(false);

        // Ready button (green gradient)
        readyButton = createGradientButton("Ready",
                new Color(46, 204, 113), new Color(39, 174, 96));
        readyButton.addActionListener(e -> toggleReady());

        // Start game button (host only, gold gradient, hidden by default)
        startGameButton = createGradientButton("Start Game",
                new Color(255, 193, 7), new Color(255, 152, 0));
        startGameButton.addActionListener(e -> requestStartGame());
        startGameButton.setVisible(false);

        // Leave button (red gradient)
        leaveButton = createGradientButton("Leave Room",
                new Color(231, 76, 60), new Color(192, 57, 43));
        leaveButton.addActionListener(e -> leaveRoom());

        topButtonPanel.add(startGameButton);
        topButtonPanel.add(readyButton);
        topButtonPanel.add(leaveButton);
        topPanel.add(topButtonPanel, BorderLayout.EAST);

        roomPanel.add(topPanel, BorderLayout.NORTH);

        // ===== Player list =====
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel) {
            @Override
            public void updateUI() {
                super.updateUI();
                setOpaque(false);
            }
        };
        playerList.setBackground(new Color(60, 55, 100, 180));  // Semi-transparent background
        playerList.setForeground(Color.WHITE);
        playerList.setFont(new Font("SansSerif", Font.PLAIN, 18));
        playerList.setFixedCellHeight(48);
        playerList.setSelectionBackground(new Color(100, 90, 160));
        playerList.setSelectionForeground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(120, 110, 180), 2, true));
        roomPanel.add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Create a button with a gradient background.
     * The button uses custom painting for gradient background, rounded corners, and hover scale effect.
     *
     * @param text button text
     * @param start gradient start color
     * @param end gradient end color
     * @return custom-painted button
     */
    private JButton createGradientButton(String text, Color start, Color end) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Draw gradient background (supports runtime color change via client property)
                Color s = (Color) getClientProperty("gradientStart");
                Color e = (Color) getClientProperty("gradientEnd");
                if (s == null) s = start;
                if (e == null) e = end;
                GradientPaint gradient = new GradientPaint(0, 0, s, 0, getHeight(), e);
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                // Draw white text
                g2d.setColor(Color.WHITE);
                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2d.drawString(getText(), x, y);
            }
        };

        button.setFont(new Font("SansSerif", Font.BOLD, 16));
        button.setFocusPainted(false);       // Don't paint focus indicator
        button.setBorderPainted(false);      // Don't paint border
        button.setContentAreaFilled(false);  // Don't fill default background
        button.setPreferredSize(new Dimension(180, 50));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));  // Hand cursor on hover

        // Slightly enlarge button on mouse hover
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

        button.putClientProperty("gradientStart", start);
        button.putClientProperty("gradientEnd", end);
        return button;
    }

    /**
     * Update a gradient button's color and text (without creating a new button instance).
     * Used by toggleReady() and leaveRoom() for dynamic button color switching.
     */
    private void updateGradientButton(JButton button, Color start, Color end, String text) {
        button.setText(text);
        button.putClientProperty("gradientStart", start);
        button.putClientProperty("gradientEnd", end);
        button.repaint();
    }

    /** Apply uniform styling to text fields (dark background, white text, rounded border) */
    private void styleTextField(JTextField field) {
        field.setBackground(new Color(50, 45, 80));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);  // Cursor color
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 90, 150), 2, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        field.setOpaque(true);
    }

    /** Handle create room button click — validate nickname then send CREATE_ROOM to server */
    private void createRoom() {
        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            setStatus("Please enter a nickname", Color.RED);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        amICreator = true;
        client.sendMessage(MessageProtocol.MessageType.CREATE_ROOM, payload.toString());
        setStatus("Creating room...", new Color(255, 200, 0));
    }

    /** Handle join room button click — validate nickname and room code then send JOIN_ROOM to server */
    private void joinRoom() {
        String nickname = nicknameField.getText().trim();
        String roomCode = roomCodeField.getText().trim().toUpperCase();  // Auto-uppercase room code

        if (nickname.isEmpty()) {
            setStatus("Please enter a nickname", Color.RED);
            return;
        }
        if (roomCode.isEmpty()) {
            setStatus("Please enter room code", Color.RED);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        payload.addProperty("roomCode", roomCode);
        amICreator = false;
        client.sendMessage(MessageProtocol.MessageType.JOIN_ROOM, payload.toString());
        setStatus("Joining room...", new Color(255, 200, 0));
    }

    /** Host requests to start the game */
    private void requestStartGame() {
        client.sendMessage(MessageProtocol.MessageType.REQUEST_START_GAME, "{}");
        setStatus("Starting game...", new Color(255, 200, 0));
    }

    /** Toggle ready state — switches between "Ready" and "Unready" */
    private void toggleReady() {
        isReady = !isReady;
        if (isReady) {
            updateGradientButton(readyButton,
                    new Color(231, 76, 60), new Color(192, 57, 43), "Unready");
        } else {
            updateGradientButton(readyButton,
                    new Color(46, 204, 113), new Color(39, 174, 96), "Ready");
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("ready", isReady);
        client.sendMessage(MessageProtocol.MessageType.PLAYER_READY, payload.toString());
    }

    /** Leave current room — send LEAVE_ROOM and return to login view */
    private void leaveRoom() {
        client.sendMessage(MessageProtocol.MessageType.LEAVE_ROOM, "{}");
        isInRoom = false;
        isReady = false;
        updateGradientButton(readyButton,
                new Color(46, 204, 113), new Color(39, 174, 96), "Ready");
        showLoginPanel();
    }

    /**
     * Update room state — called by MainFrame when receiving ROOM_UPDATE messages.
     * Parses server-returned room and player info and updates the UI.
     *
     * @param jsonPayload ROOM_UPDATE message JSON payload
     */
    public void updateRoom(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String roomCode = payload.get("roomCode").getAsString();
                roomCodeLabel.setText("Room: " + roomCode);

                // Parse player list
                JsonArray players = payload.getAsJsonArray("players");
                playerListModel.clear();

                for (JsonElement elem : players) {
                    JsonObject player = elem.getAsJsonObject();
                    String nickname = player.get("nickname").getAsString();
                    boolean ready = player.get("ready").getAsBoolean();
                    boolean isCreator = player.get("isCreator").getAsBoolean();

                    // Build display text: nickname + host indicator + ready state
                    String displayText = nickname;
                    if (isCreator) displayText += " Host";
                    displayText += ready ? " Ready" : " Not Ready";
                    playerListModel.addElement(displayText);
                }

                // Switch to room view on first entry
                if (!isInRoom) {
                    isInRoom = true;
                    showRoomPanel();
                }

                // Control start game button visibility: host + all ready + at least 2 players
                int totalPlayers = players.size();
                long readyCount = 0;
                for (JsonElement elem : players) {
                    if (elem.getAsJsonObject().get("ready").getAsBoolean()) readyCount++;
                }
                boolean allReady = readyCount == totalPlayers && totalPlayers >= GameConstants.MIN_PLAYERS;
                startGameButton.setVisible(amICreator && allReady);

                setStatus("Room: " + roomCode + " | Players: " + players.size(),
                        new Color(46, 204, 113));
            } catch (Exception e) {
                setStatus("Failed to update room info", Color.RED);
            }
        });
    }

    /** Switch to room view */
    private void showRoomPanel() {
        remove(loginPanel);
        add(roomPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** Switch back to login view */
    private void showLoginPanel() {
        remove(roomPanel);
        add(loginPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        setStatus("Enter nickname to start", new Color(180, 180, 200));
    }

    /** Set the status bar hint text and color */
    private void setStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }

    private void showRulesDialog() {
        String rules =
                "MONOPOLY DEAL - GAME RULES\n\n" +
                        "OBJECTIVE\n" +
                        "Be the first player to collect 3 complete property sets.\n\n" +
                        "SETUP\n" +
                        "- 2-5 players\n" +
                        "- Each player starts with 5 cards\n" +
                        "- Draw 3 cards at the start of your turn\n\n" +
                        "YOUR TURN\n" +
                        "1. Draw Phase: Draw 3 cards from the draw pile\n" +
                        "2. Play Phase: Play up to 3 cards (30 second timer)\n" +
                        "3. Discard Phase: If you have more than 7 cards,\n" +
                        "   discard down to 7\n\n" +
                        "CARD TYPES\n" +
                        "- Money Cards (1M-10M): Deposit into your bank\n" +
                        "- Property Cards: Place in your property zone\n" +
                        "  Wild Property: Assign to any color\n" +
                        "- Rent Cards: Charge rent to other players\n" +
                        "  Wild Rent: Targets one player\n" +
                        "  Others: Target all players\n" +
                        "- Action Cards: Special one-time effects\n\n" +
                        "ACTION CARDS\n" +
                        "- Debt Collector: One player pays you 5M\n" +
                        "- Birthday: All players pay you 2M\n" +
                        "- Deal Breaker: Steal a complete property set\n" +
                        "- Pass Go: Draw 2 extra cards\n" +
                        "- Double Rent: Next rent card value is doubled\n" +
                        "- Forced Deal: Swap a property with another player\n" +
                        "- Sly Deal: Steal one property\n" +
                        "  (not from a complete set)\n" +
                        "- House/Hotel: Build on complete sets\n" +
                        "  for extra rent value\n" +
                        "- Just Say No: Cancel an action against you\n\n" +
                        "PROPERTY SET SIZES\n" +
                        "2 cards: Brown, Light Blue, Blue\n" +
                        "3 cards: Pink, Orange, Red, Yellow, Green,\n" +
                        "         Purple, Light Green\n" +
                        "4 cards: Black\n\n" +
                        "RENT VALUES (per property in set)\n" +
                        "Brown/Light Blue: 1-2M\n" +
                        "Pink/Orange: 1-3M\n" +
                        "Red/Yellow: 2-4-6M\n" +
                        "Green: 2-4-7M\n" +
                        "Blue: 3-8M\n" +
                        "Purple: 1-2-4M\n" +
                        "Black: 1-2-3-5M\n" +
                        "Light Green: 1-2-4M\n\n" +
                        "BUILDINGS\n" +
                        "House: +1M rent each (max 4 per set)\n" +
                        "Hotel: Replaces 4 houses (+3M rent)\n\n" +
                        "WINNING\n" +
                        "First player to collect 3 complete\n" +
                        "property sets wins the game!";

        JTextArea textArea = new JTextArea(rules);
        textArea.setEditable(false);
        textArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        textArea.setForeground(Color.WHITE);
        textArea.setBackground(new Color(20, 18, 40));
        textArea.setCaretPosition(0);

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(520, 520));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(120, 100, 180), 2));

        JOptionPane.showMessageDialog(
                SwingUtilities.getWindowAncestor(this),
                scrollPane,
                "Game Rules - Monopoly Deal",
                JOptionPane.PLAIN_MESSAGE
        );
    }
}
