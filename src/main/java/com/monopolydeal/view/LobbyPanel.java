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

public class LobbyPanel extends JPanel {
    private final GameClient client;

    private JTextField nicknameField;
    private JTextField roomCodeField;
    private JButton createRoomButton;
    private JButton joinRoomButton;
    private JButton readyButton;
    private JButton leaveButton;
    private JList<String> playerList;
    private DefaultListModel<String> playerListModel;
    private JLabel roomCodeLabel;
    private JLabel statusLabel;
    private JPanel roomPanel;
    private JPanel loginPanel;

    private boolean isInRoom;
    private boolean isReady;

    public LobbyPanel(GameClient client) {
        this.client = client;
        this.isInRoom = false;
        this.isReady = false;

        setLayout(new BorderLayout());
    
        setBackground(new Color(20, 20, 40));

        createLoginPanel();
        createRoomPanel();

        add(loginPanel, BorderLayout.CENTER);
    }

    private void createLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
         
                GradientPaint gradient = new GradientPaint(0, 0, new Color(15, 12, 35),
                        getWidth(), getHeight(), new Color(35, 30, 60));
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        loginPanel.setBorder(new EmptyBorder(60, 60, 60, 60));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Monopoly Deal");
        titleLabel.setFont(new Font("Arial Black", Font.BOLD, 52));
        titleLabel.setForeground(new Color(255, 215, 0));
      
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        loginPanel.add(titleLabel, gbc);

        JLabel subtitleLabel = new JLabel("Premium Card Game");
        subtitleLabel.setFont(new Font("Arial", Font.ITALIC, 24));
        subtitleLabel.setForeground(new Color(180, 180, 220));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 1;
        loginPanel.add(subtitleLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        JLabel nicknameLabel = new JLabel("Nickname:");
        nicknameLabel.setForeground(new Color(220, 220, 255));
        nicknameLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        loginPanel.add(nicknameLabel, gbc);

        gbc.gridx = 1;
        nicknameField = new JTextField(20);
        nicknameField.setFont(new Font("Arial", Font.PLAIN, 18));
        nicknameField.setText("Player" + (int)(Math.random() * 1000));
        styleTextField(nicknameField);
        loginPanel.add(nicknameField, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        JLabel roomLabel = new JLabel("Room Code:");
        roomLabel.setForeground(new Color(220, 220, 255));
        roomLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        loginPanel.add(roomLabel, gbc);

        gbc.gridx = 1;
        roomCodeField = new JTextField(20);
        roomCodeField.setFont(new Font("Arial", Font.PLAIN, 18));
        styleTextField(roomCodeField);
        loginPanel.add(roomCodeField, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 15));
        buttonPanel.setOpaque(false);

        createRoomButton = createGradientButton("Create Room",
                new Color(34, 186, 157), new Color(27, 156, 133));
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

        gbc.gridy = 5;
        statusLabel = new JLabel("Enter a nickname to start");
        statusLabel.setForeground(new Color(180, 180, 200));
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 16));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loginPanel.add(statusLabel, gbc);
    }

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

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        roomCodeLabel = new JLabel("Room: -----");
        roomCodeLabel.setFont(new Font("Arial Black", Font.BOLD, 28));
        roomCodeLabel.setForeground(new Color(255, 215, 0));
        topPanel.add(roomCodeLabel, BorderLayout.WEST);

        JPanel topButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topButtonPanel.setOpaque(false);

        readyButton = createGradientButton("Ready",
                new Color(46, 204, 113), new Color(39, 174, 96));
        readyButton.addActionListener(e -> toggleReady());

        leaveButton = createGradientButton("Leave Room",
                new Color(231, 76, 60), new Color(192, 57, 43));
        leaveButton.addActionListener(e -> leaveRoom());

        topButtonPanel.add(readyButton);
        topButtonPanel.add(leaveButton);
        topPanel.add(topButtonPanel, BorderLayout.EAST);

        roomPanel.add(topPanel, BorderLayout.NORTH);

        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel) {
            @Override
            public void updateUI() {
                super.updateUI();
                setOpaque(false);
            }
        };
        playerList.setBackground(new Color(60, 55, 100, 180));
        playerList.setForeground(Color.WHITE);
        playerList.setFont(new Font("Arial", Font.PLAIN, 18));
        playerList.setFixedCellHeight(48);
        playerList.setSelectionBackground(new Color(100, 90, 160));
        playerList.setSelectionForeground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(120, 110, 180), 2, true));
        roomPanel.add(scrollPane, BorderLayout.CENTER);
    }

    private JButton createGradientButton(String text, Color start, Color end) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                GradientPaint gradient = new GradientPaint(0, 0, start, 0, getHeight(), end);
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);

                g2d.setColor(Color.WHITE);
                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2d.drawString(getText(), x, y);
            }
        };

        button.setFont(new Font("Arial", Font.BOLD, 16));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(180, 50));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

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

    private void styleTextField(JTextField field) {
        field.setBackground(new Color(50, 45, 80));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 90, 150), 2, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        field.setOpaque(true);
    }

    private void createRoom() {
        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            setStatus("Please enter a nickname", Color.RED);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        client.sendMessage(MessageProtocol.MessageType.CREATE_ROOM, payload.toString());
        setStatus("Creating room...", new Color(255, 200, 0));
    }

    private void joinRoom() {
        String nickname = nicknameField.getText().trim();
        String roomCode = roomCodeField.getText().trim().toUpperCase();

        if (nickname.isEmpty()) {
            setStatus("Please enter a nickname", Color.RED);
            return;
        }
        if (roomCode.isEmpty()) {
            setStatus("Please enter a room code", Color.RED);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        payload.addProperty("roomCode", roomCode);
        client.sendMessage(MessageProtocol.MessageType.JOIN_ROOM, payload.toString());
        setStatus("Joining room...", new Color(255, 200, 0));
    }

    private void toggleReady() {
        isReady = !isReady;
        readyButton.setText(isReady ? "Not Ready" : "Ready");

        if (isReady) {
            readyButton = createGradientButton("Not Ready",
                    new Color(231, 76, 60), new Color(192, 57, 43));
        } else {
            readyButton = createGradientButton("Ready",
                    new Color(46, 204, 113), new Color(39, 174, 96));
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("ready", isReady);
        client.sendMessage(MessageProtocol.MessageType.PLAYER_READY, payload.toString());
    }

    private void leaveRoom() {
        client.sendMessage(MessageProtocol.MessageType.LEAVE_ROOM, "{}");
        isInRoom = false;
        isReady = false;
        readyButton = createGradientButton("Ready",
                new Color(46, 204, 113), new Color(39, 174, 96));
        showLoginPanel();
    }

    public void updateRoom(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String roomCode = payload.get("roomCode").getAsString();
                roomCodeLabel.setText("Room: " + roomCode);

                JsonArray players = payload.getAsJsonArray("players");
                playerListModel.clear();

                for (JsonElement elem : players) {
                    JsonObject player = elem.getAsJsonObject();
                    String nickname = player.get("nickname").getAsString();
                    boolean ready = player.get("ready").getAsBoolean();
                    boolean isCreator = player.get("isCreator").getAsBoolean();

                    String displayText = nickname;
                    if (isCreator) displayText += " 👑 HOST";
                    displayText += ready ? " ✔ READY" : " ⏳ NOT READY";
                    playerListModel.addElement(displayText);
                }

                if (!isInRoom) {
                    isInRoom = true;
                    showRoomPanel();
                }
                setStatus("Room: " + roomCode + " | Players: " + players.size(), new Color(46, 204, 113));
            } catch (Exception e) {
                setStatus("Error updating room", Color.RED);
            }
        });
    }

    private void showRoomPanel() {
        remove(loginPanel);
        add(roomPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void showLoginPanel() {
        remove(roomPanel);
        add(loginPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        setStatus("Enter a nickname to start", new Color(180, 180, 200));
    }

    private void setStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }
}
