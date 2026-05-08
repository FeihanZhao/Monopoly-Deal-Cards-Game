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
        setBackground(new Color(30, 30, 30));

        createLoginPanel();
        createRoomPanel();

        add(loginPanel, BorderLayout.CENTER);
    }

    private void createLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout());
        loginPanel.setBackground(new Color(30, 30, 30));
        loginPanel.setBorder(new EmptyBorder(50, 50, 50, 50));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Monopoly Deal");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        titleLabel.setForeground(new Color(255, 215, 0));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        loginPanel.add(titleLabel, gbc);

        JLabel subtitleLabel = new JLabel("Card Game");
        subtitleLabel.setFont(new Font("Arial", Font.ITALIC, 20));
        subtitleLabel.setForeground(Color.WHITE);
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 1;
        loginPanel.add(subtitleLabel, gbc);

        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        JLabel nicknameLabel = new JLabel("Nickname:");
        nicknameLabel.setForeground(Color.WHITE);
        nicknameLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        loginPanel.add(nicknameLabel, gbc);

        gbc.gridx = 1;
        nicknameField = new JTextField(20);
        nicknameField.setFont(new Font("Arial", Font.PLAIN, 16));
        nicknameField.setText("Player" + (int)(Math.random() * 1000));
        loginPanel.add(nicknameField, gbc);

        gbc.gridy = 3;
        gbc.gridx = 0;
        JLabel roomLabel = new JLabel("Room Code:");
        roomLabel.setForeground(Color.WHITE);
        roomLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        loginPanel.add(roomLabel, gbc);

        gbc.gridx = 1;
        roomCodeField = new JTextField(20);
        roomCodeField.setFont(new Font("Arial", Font.PLAIN, 16));
        loginPanel.add(roomCodeField, gbc);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        buttonPanel.setBackground(new Color(30, 30, 30));

        createRoomButton = createStyledButton("Create Room", new Color(46, 139, 87));
        joinRoomButton = createStyledButton("Join Room", new Color(70, 130, 180));

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
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 14));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loginPanel.add(statusLabel, gbc);
    }

    private void createRoomPanel() {
        roomPanel = new JPanel(new BorderLayout());
        roomPanel.setBackground(new Color(40, 40, 40));
        roomPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(40, 40, 40));

        roomCodeLabel = new JLabel("Room: -----");
        roomCodeLabel.setFont(new Font("Arial", Font.BOLD, 24));
        roomCodeLabel.setForeground(new Color(255, 215, 0));
        topPanel.add(roomCodeLabel, BorderLayout.WEST);

        JPanel topButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        topButtonPanel.setBackground(new Color(40, 40, 40));

        readyButton = createStyledButton("Ready", new Color(46, 139, 87));
        readyButton.addActionListener(e -> toggleReady());

        leaveButton = createStyledButton("Leave Room", new Color(178, 34, 34));
        leaveButton.addActionListener(e -> leaveRoom());

        topButtonPanel.add(readyButton);
        topButtonPanel.add(leaveButton);
        topPanel.add(topButtonPanel, BorderLayout.EAST);

        roomPanel.add(topPanel, BorderLayout.NORTH);

        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        playerList.setBackground(new Color(50, 50, 50));
        playerList.setForeground(Color.WHITE);
        playerList.setFont(new Font("Arial", Font.PLAIN, 16));
        playerList.setFixedCellHeight(40);

        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(100, 100, 100)));
        roomPanel.add(scrollPane, BorderLayout.CENTER);
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Arial", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setPreferredSize(new Dimension(150, 40));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor.brighter());
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor);
            }
        });

        return button;
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
        setStatus("Creating room...", Color.YELLOW);
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
        setStatus("Joining room...", Color.YELLOW);
    }

    private void toggleReady() {
        isReady = !isReady;
        readyButton.setText(isReady ? "Not Ready" : "Ready");
        readyButton.setBackground(isReady ? new Color(178, 34, 34) : new Color(46, 139, 87));

        JsonObject payload = new JsonObject();
        payload.addProperty("ready", isReady);
        client.sendMessage(MessageProtocol.MessageType.PLAYER_READY, payload.toString());
    }

    private void leaveRoom() {
        client.sendMessage(MessageProtocol.MessageType.LEAVE_ROOM, "{}");
        isInRoom = false;
        isReady = false;
        readyButton.setText("Ready");
        readyButton.setBackground(new Color(46, 139, 87));
        showLoginPanel();
    }

    public void updateRoom(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                System.out.println("Received room update: " + jsonPayload);
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
                    if (isCreator) displayText += " (Host)";
                    displayText += ready ? " [Ready]" : " [Not Ready]";

                    playerListModel.addElement(displayText);
                }

                if (!isInRoom) {
                    isInRoom = true;
                    showRoomPanel();
                }

                setStatus("Room: " + roomCode + " | Players: " + players.size(), Color.GREEN);
            } catch (Exception e) {
                System.err.println("Error updating room: " + e.getMessage());
                e.printStackTrace();
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
        setStatus("Enter a nickname to start", Color.LIGHT_GRAY);
    }

    private void setStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }
}