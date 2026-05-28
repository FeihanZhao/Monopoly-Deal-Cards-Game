package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
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

    // ========================= 顶级配色系统 =========================
    private static final Color BG_DEEP = new Color(10, 8, 24);
    private static final Color BG_MID = new Color(20, 16, 42);
    private static final Color BG_LIGHT = new Color(32, 26, 60);
    private static final Color GOLD = new Color(255, 215, 0);
    private static final Color GOLD_GLOW = new Color(255, 230, 100);
    private static final Color CYAN = new Color(0, 210, 255);
    private static final Color PURPLE = new Color(150, 80, 255);
    private static final Color GREEN = new Color(40, 220, 120);
    private static final Color RED = new Color(255, 70, 70);
    private static final Color BLUE = new Color(70, 140, 255);
    private static final Color TEXT_WHITE = new Color(245, 245, 255);
    private static final Color TEXT_MUTED = new Color(170, 160, 200);
    private static final Color INPUT_BG = new Color(42, 36, 70);
    private static final Color INPUT_BORDER = new Color(100, 80, 160);
    private static final Color LIST_BG = new Color(36, 30, 64, 200);
    private static final Color LIST_BORDER = new Color(120, 100, 180);

    public LobbyPanel(GameClient client) {
        this.client = client;
        this.isInRoom = false;
        this.isReady = false;
        setLayout(new BorderLayout());
        setBackground(BG_DEEP);
        createLoginPanel();
        createRoomPanel();
        add(loginPanel, BorderLayout.CENTER);
    }

    // ========================= 登录面板（炫酷主界面） =========================
    private void createLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                
                LinearGradientPaint gradient = new LinearGradientPaint(
                        0, 0, getWidth(), getHeight(),
                        new float[]{0, 1},
                        new Color[]{BG_DEEP, BG_MID}
                );
                g2.setPaint(gradient);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // 装饰光效
                g2.setColor(new Color(255, 215, 0, 6));
                g2.fillOval(-100, getHeight()/2-200, 400, 400);
                g2.setColor(new Color(150, 80, 255, 6));
                g2.fillOval(getWidth()-300, getHeight()/2-200, 400, 400);
            }
        };
        loginPanel.setBorder(new EmptyBorder(50, 70, 50, 70));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(16, 16, 16, 16);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 标题
        JLabel titleLabel = new JLabel("MONOPOLY DEAL");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 58));
        titleLabel.setForeground(GOLD);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        loginPanel.add(titleLabel, gbc);

        // 副标题
        JLabel subtitleLabel = new JLabel("Premium Card Game", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 22));
        subtitleLabel.setForeground(CYAN);
        gbc.gridy = 1;
        loginPanel.add(subtitleLabel, gbc);

        // 昵称输入
        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        JLabel nicknameLabel = new JLabel("Nickname");
        nicknameLabel.setForeground(TEXT_WHITE);
        nicknameLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        loginPanel.add(nicknameLabel, gbc);

        gbc.gridx = 1;
        nicknameField = new JTextField();
        nicknameField.setFont(new Font("Segoe UI", Font.PLAIN, 19));
        nicknameField.setText("Player" + (int)(Math.random() * 1000));
        stylePremiumTextField(nicknameField);
        loginPanel.add(nicknameField, gbc);

        // 房间码输入
        gbc.gridy = 3;
        gbc.gridx = 0;
        JLabel roomLabel = new JLabel("Room Code");
        roomLabel.setForeground(TEXT_WHITE);
        roomLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        loginPanel.add(roomLabel, gbc);

        gbc.gridx = 1;
        roomCodeField = new JTextField();
        roomCodeField.setFont(new Font("Segoe UI", Font.PLAIN, 19));
        stylePremiumTextField(roomCodeField);
        loginPanel.add(roomCodeField, gbc);

        // 按钮组
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 20));
        buttonPanel.setOpaque(false);

        createRoomButton = createNeonButton("Create Room", GREEN, new Color(25, 170, 80));
        joinRoomButton = createNeonButton("Join Room", BLUE, new Color(40, 100, 200));

        createRoomButton.addActionListener(e -> createRoom());
        joinRoomButton.addActionListener(e -> joinRoom());

        buttonPanel.add(createRoomButton);
        buttonPanel.add(joinRoomButton);

        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        loginPanel.add(buttonPanel, gbc);

        // 状态提示
        gbc.gridy = 5;
        statusLabel = new JLabel("Enter nickname to start", SwingConstants.CENTER);
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        loginPanel.add(statusLabel, gbc);
    }

    // ========================= 房间面板（高级玻璃质感） =========================
    private void createRoomPanel() {
        roomPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                LinearGradientPaint gradient = new LinearGradientPaint(
                        0, 0, getWidth(), getHeight(),
                        new float[]{0, 1},
                        new Color[]{BG_MID, BG_LIGHT}
                );
                g2.setPaint(gradient);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        roomPanel.setBorder(new EmptyBorder(35, 35, 35, 35));

        // 顶部栏
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        roomCodeLabel = new JLabel("Room: -----");
        roomCodeLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        roomCodeLabel.setForeground(GOLD_GLOW);
        topPanel.add(roomCodeLabel, BorderLayout.WEST);

        // 顶部按钮
        JPanel topButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        topButtonPanel.setOpaque(false);

        readyButton = createNeonButton("Ready", GREEN, new Color(25, 170, 80));
        readyButton.addActionListener(e -> toggleReady());

        leaveButton = createNeonButton("Leave Room", RED, new Color(180, 40, 40));
        leaveButton.addActionListener(e -> leaveRoom());

        topButtonPanel.add(readyButton);
        topButtonPanel.add(leaveButton);
        topPanel.add(topButtonPanel, BorderLayout.EAST);
        roomPanel.add(topPanel, BorderLayout.NORTH);

        // 玩家列表
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel);
        playerList.setBackground(LIST_BG);
        playerList.setForeground(TEXT_WHITE);
        playerList.setFont(new Font("Segoe UI", Font.PLAIN, 19));
        playerList.setFixedCellHeight(52);
        playerList.setSelectionBackground(new Color(100, 80, 180));
        playerList.setSelectionForeground(TEXT_WHITE);
        playerList.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createLineBorder(LIST_BORDER, 3, true));
        roomPanel.add(scrollPane, BorderLayout.CENTER);
    }

    // ========================= 霓虹发光按钮（核心炫酷效果） =========================
    private JButton createNeonButton(String text, Color start, Color end) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int w = getWidth();
                int h = getHeight();
                float arc = 20;

                // 外层发光
                if (getModel().isRollover()) {
                    g2.setColor(new Color(start.getRed(), start.getGreen(), start.getBlue(), 80));
                    g2.fillRoundRect(-2, -2, w+4, h+4, (int)arc+4, (int)arc+4);
                }

                // 渐变背景
                GradientPaint gradient = new GradientPaint(0, 0, start, 0, h, end);
                g2.setPaint(gradient);
                g2.fillRoundRect(0, 0, w, h, (int)arc, (int)arc);

                // 高光
                g2.setColor(new Color(255,255,255,30));
                g2.fillRoundRect(2, 2, w-4, h/2-2, 16, 16);

                // 文字
                g2.setColor(TEXT_WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 17));
                FontMetrics fm = g2.getFontMetrics();
                int x = (w - fm.stringWidth(getText())) / 2;
                int y = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), x, y);
            }
        };
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setPreferredSize(new Dimension(200, 55));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    // ========================= 高级输入框样式 =========================
    private void stylePremiumTextField(JTextField field) {
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT_WHITE);
        field.setCaretColor(GOLD);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 3, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        field.setOpaque(true);
    }

    // ========================= 业务逻辑（完全不变） =========================
    private void createRoom() {
        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            setStatus("Please enter nickname", Color.RED);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        client.sendMessage(MessageProtocol.MessageType.CREATE_ROOM, payload.toString());
        setStatus("Creating room...", GOLD);
    }

    private void joinRoom() {
        String nickname = nicknameField.getText().trim();
        String roomCode = roomCodeField.getText().trim().toUpperCase();
        if (nickname.isEmpty()) {
            setStatus("Please enter nickname", Color.RED);
            return;
        }
        if (roomCode.isEmpty()) {
            setStatus("Please enter room code", Color.RED);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        payload.addProperty("roomCode", roomCode);
        client.sendMessage(MessageProtocol.MessageType.JOIN_ROOM, payload.toString());
        setStatus("Joining room...", GOLD);
    }

    private void toggleReady() {
        isReady = !isReady;
        if (isReady) {
            readyButton = createNeonButton("Cancel Ready", RED, new Color(180, 40, 40));
        } else {
            readyButton = createNeonButton("Ready", GREEN, new Color(25, 170, 80));
        }
        readyButton.addActionListener(e -> toggleReady());
        JsonObject payload = new JsonObject();
        payload.addProperty("ready", isReady);
        client.sendMessage(MessageProtocol.MessageType.PLAYER_READY, payload.toString());
        topPanelRefresh();
    }

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

    private void leaveRoom() {
        client.sendMessage(MessageProtocol.MessageType.LEAVE_ROOM, "{}");
        isInRoom = false;
        isReady = false;
        readyButton = createNeonButton("Ready", GREEN, new Color(25, 170, 80));
        readyButton.addActionListener(e -> toggleReady());
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
                    if (isCreator) displayText += " [Host]";
                    displayText += ready ? " ✔ Ready" : " ⚪ Not Ready";
                    playerListModel.addElement(displayText);
                }
                if (!isInRoom) {
                    isInRoom = true;
                    showRoomPanel();
                }
                setStatus("Room: " + roomCode + " | Players: " + players.size(), GREEN);
            } catch (Exception e) {
                setStatus("Failed to update room info", Color.RED);
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
        setStatus("Enter nickname to start", TEXT_MUTED);
    }

    private void setStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }
}
