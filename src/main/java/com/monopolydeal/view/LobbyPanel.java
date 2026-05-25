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
 * 大厅面板 - 游戏开始前的房间管理界面
 *
 * 提供两种视图模式：
 * 1. 登录视图（loginPanel）- 输入昵称、创建或加入房间
 * 2. 房间视图（roomPanel）- 显示房间代码、玩家列表、准备按钮
 *
 * 功能流程：
 * 1. 输入昵称 → 点击"创建房间"或"加入房间"（需输入房间代码）
 * 2. 成功加入房间后自动切换到房间视图
 * 3. 点击"准备"按钮标记自己已就绪
 * 4. 所有玩家准备就绪（>=2人）后，服务器自动开始游戏
 * 5. MainFrame收到GAME_STATE_UPDATE后切换到GamePanel
 *
 * UI风格：深色主题（深紫蓝渐变背景），金色标题，圆角渐变按钮
 */
public class LobbyPanel extends JPanel {
    /** 游戏客户端连接 */
    private final GameClient client;

    /** 昵称输入框 */
    private JTextField nicknameField;
    /** 房间代码输入框（加入房间时使用） */
    private JTextField roomCodeField;
    /** 创建房间按钮 */
    private JButton createRoomButton;
    /** 加入房间按钮 */
    private JButton joinRoomButton;
    /** 准备/取消准备按钮 */
    private JButton readyButton;
    /** 离开房间按钮 */
    private JButton leaveButton;
    /** 玩家列表组件 */
    private JList<String> playerList;
    /** 玩家列表数据模型 */
    private DefaultListModel<String> playerListModel;
    /** 房间代码标签 */
    private JLabel roomCodeLabel;
    /** 状态提示标签 */
    private JLabel statusLabel;
    /** 房间视图面板 */
    private JPanel roomPanel;
    /** 登录视图面板 */
    private JPanel loginPanel;

    /** 是否已在房间中 */
    private boolean isInRoom;
    /** 是否已准备就绪 */
    private boolean isReady;

    /**
     * 构造函数 - 初始化大厅界面
     * @param client 已连接的GameClient实例
     */
    public LobbyPanel(GameClient client) {
        this.client = client;
        this.isInRoom = false;
        this.isReady = false;

        setLayout(new BorderLayout());
        setBackground(new Color(20, 20, 40));  // 深色背景

        createLoginPanel();   // 构建登录视图
        createRoomPanel();    // 构建房间视图

        add(loginPanel, BorderLayout.CENTER);  // 默认显示登录视图
    }

    /**
     * 创建登录视图面板 - 包含标题、昵称输入、房间代码输入和操作按钮
     * 使用GridBagLayout布局，深紫蓝色渐变背景
     */
    private void createLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                // 从左上到右下的深紫蓝色渐变背景
                GradientPaint gradient = new GradientPaint(0, 0, new Color(15, 12, 35),
                        getWidth(), getHeight(), new Color(35, 30, 60));
                g2d.setPaint(gradient);
                g2d.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        loginPanel.setBorder(new EmptyBorder(60, 60, 60, 60));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);  // 组件间距
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // ===== 标题 =====
        JLabel titleLabel = new JLabel("Monopoly Deal");
        titleLabel.setFont(new Font("Arial Black", Font.BOLD, 52));
        titleLabel.setForeground(new Color(255, 215, 0));  // 金色
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        loginPanel.add(titleLabel, gbc);

        // ===== 副标题 =====
        JLabel subtitleLabel = new JLabel("Premium Card Game");
        subtitleLabel.setFont(new Font("Arial", Font.ITALIC, 24));
        subtitleLabel.setForeground(new Color(180, 180, 220));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridy = 1;
        loginPanel.add(subtitleLabel, gbc);

        // ===== 昵称输入行 =====
        gbc.gridwidth = 1;
        gbc.gridy = 2;
        gbc.gridx = 0;
        JLabel nicknameLabel = new JLabel("昵称:");
        nicknameLabel.setForeground(new Color(220, 220, 255));
        nicknameLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        loginPanel.add(nicknameLabel, gbc);

        gbc.gridx = 1;
        nicknameField = new JTextField(20);
        nicknameField.setFont(new Font("Arial", Font.PLAIN, 18));
        // 随机生成默认昵称
        nicknameField.setText("Player" + (int)(Math.random() * 1000));
        styleTextField(nicknameField);
        loginPanel.add(nicknameField, gbc);

        // ===== 房间代码输入行 =====
        gbc.gridy = 3;
        gbc.gridx = 0;
        JLabel roomLabel = new JLabel("房间代码:");
        roomLabel.setForeground(new Color(220, 220, 255));
        roomLabel.setFont(new Font("Arial", Font.PLAIN, 18));
        loginPanel.add(roomLabel, gbc);

        gbc.gridx = 1;
        roomCodeField = new JTextField(20);
        roomCodeField.setFont(new Font("Arial", Font.PLAIN, 18));
        styleTextField(roomCodeField);
        loginPanel.add(roomCodeField, gbc);

        // ===== 按钮面板 =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 15));
        buttonPanel.setOpaque(false);

        // 创建房间按钮（绿色渐变）
        createRoomButton = createGradientButton("创建房间",
                new Color(34, 186, 157), new Color(27, 156, 133));
        // 加入房间按钮（蓝色渐变）
        joinRoomButton = createGradientButton("加入房间",
                new Color(72, 133, 237), new Color(58, 112, 207));

        createRoomButton.addActionListener(e -> createRoom());
        joinRoomButton.addActionListener(e -> joinRoom());

        buttonPanel.add(createRoomButton);
        buttonPanel.add(joinRoomButton);

        gbc.gridy = 4;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        loginPanel.add(buttonPanel, gbc);

        // ===== 状态提示标签 =====
        gbc.gridy = 5;
        statusLabel = new JLabel("输入昵称开始游戏");
        statusLabel.setForeground(new Color(180, 180, 200));
        statusLabel.setFont(new Font("Arial", Font.ITALIC, 16));
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        loginPanel.add(statusLabel, gbc);
    }

    /**
     * 创建房间视图面板 - 进入房间后显示
     * 包含房间代码、玩家列表、准备/离开按钮
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

        // ===== 顶部信息栏 =====
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);

        // 房间代码标签（金色大字）
        roomCodeLabel = new JLabel("房间: -----");
        roomCodeLabel.setFont(new Font("Arial Black", Font.BOLD, 28));
        roomCodeLabel.setForeground(new Color(255, 215, 0));
        topPanel.add(roomCodeLabel, BorderLayout.WEST);

        // 准备/离开按钮
        JPanel topButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topButtonPanel.setOpaque(false);

        // 准备按钮（绿色渐变）
        readyButton = createGradientButton("准备",
                new Color(46, 204, 113), new Color(39, 174, 96));
        readyButton.addActionListener(e -> toggleReady());

        // 离开按钮（红色渐变）
        leaveButton = createGradientButton("离开房间",
                new Color(231, 76, 60), new Color(192, 57, 43));
        leaveButton.addActionListener(e -> leaveRoom());

        topButtonPanel.add(readyButton);
        topButtonPanel.add(leaveButton);
        topPanel.add(topButtonPanel, BorderLayout.EAST);

        roomPanel.add(topPanel, BorderLayout.NORTH);

        // ===== 玩家列表 =====
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel) {
            @Override
            public void updateUI() {
                super.updateUI();
                setOpaque(false);
            }
        };
        playerList.setBackground(new Color(60, 55, 100, 180));  // 半透明背景
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

    /**
     * 创建带渐变色背景的按钮
     * 按钮使用自定义绘制，支持渐变背景、圆角、悬停放大效果
     *
     * @param text 按钮文本
     * @param start 渐变起始颜色
     * @param end 渐变结束颜色
     * @return 自定义绘制的按钮
     */
    private JButton createGradientButton(String text, Color start, Color end) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 绘制渐变背景
                GradientPaint gradient = new GradientPaint(0, 0, start, 0, getHeight(), end);
                g2d.setPaint(gradient);
                g2d.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
                // 绘制白色文字
                g2d.setColor(Color.WHITE);
                g2d.setFont(getFont());
                FontMetrics fm = g2d.getFontMetrics();
                int x = (getWidth() - fm.stringWidth(getText())) / 2;
                int y = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2d.drawString(getText(), x, y);
            }
        };

        button.setFont(new Font("Arial", Font.BOLD, 16));
        button.setFocusPainted(false);       // 不绘制焦点框
        button.setBorderPainted(false);      // 不绘制边框
        button.setContentAreaFilled(false);  // 不填充默认背景
        button.setPreferredSize(new Dimension(180, 50));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));  // 鼠标悬停变手型

        // 鼠标悬停时按钮略微放大
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

    /** 设置文本框的统一样式（深色背景、白色文字、圆角边框） */
    private void styleTextField(JTextField field) {
        field.setBackground(new Color(50, 45, 80));
        field.setForeground(Color.WHITE);
        field.setCaretColor(Color.WHITE);  // 光标颜色
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(100, 90, 150), 2, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        field.setOpaque(true);
    }

    /** 处理创建房间按钮点击 - 验证昵称后向服务器发送CREATE_ROOM请求 */
    private void createRoom() {
        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            setStatus("请输入昵称", Color.RED);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        client.sendMessage(MessageProtocol.MessageType.CREATE_ROOM, payload.toString());
        setStatus("正在创建房间...", new Color(255, 200, 0));
    }

    /** 处理加入房间按钮点击 - 验证昵称和房间代码后向服务器发送JOIN_ROOM请求 */
    private void joinRoom() {
        String nickname = nicknameField.getText().trim();
        String roomCode = roomCodeField.getText().trim().toUpperCase();  // 房间代码自动转大写

        if (nickname.isEmpty()) {
            setStatus("请输入昵称", Color.RED);
            return;
        }
        if (roomCode.isEmpty()) {
            setStatus("请输入房间代码", Color.RED);
            return;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        payload.addProperty("roomCode", roomCode);
        client.sendMessage(MessageProtocol.MessageType.JOIN_ROOM, payload.toString());
        setStatus("正在加入房间...", new Color(255, 200, 0));
    }

    /** 切换准备状态 - 在"准备"和"取消准备"之间切换 */
    private void toggleReady() {
        isReady = !isReady;
        readyButton.setText(isReady ? "取消准备" : "准备");

        if (isReady) {
            readyButton = createGradientButton("取消准备",
                    new Color(231, 76, 60), new Color(192, 57, 43));
        } else {
            readyButton = createGradientButton("准备",
                    new Color(46, 204, 113), new Color(39, 174, 96));
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("ready", isReady);
        client.sendMessage(MessageProtocol.MessageType.PLAYER_READY, payload.toString());
    }

    /** 离开当前房间 - 发送LEAVE_ROOM请求并返回登录视图 */
    private void leaveRoom() {
        client.sendMessage(MessageProtocol.MessageType.LEAVE_ROOM, "{}");
        isInRoom = false;
        isReady = false;
        readyButton = createGradientButton("准备",
                new Color(46, 204, 113), new Color(39, 174, 96));
        showLoginPanel();
    }

    /**
     * 更新房间状态 - 由MainFrame在收到ROOM_UPDATE消息时调用
     * 解析服务器返回的房间和玩家信息，更新UI显示
     *
     * @param jsonPayload ROOM_UPDATE消息的JSON负载
     */
    public void updateRoom(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String roomCode = payload.get("roomCode").getAsString();
                roomCodeLabel.setText("房间: " + roomCode);

                // 解析玩家列表
                JsonArray players = payload.getAsJsonArray("players");
                playerListModel.clear();

                for (JsonElement elem : players) {
                    JsonObject player = elem.getAsJsonObject();
                    String nickname = player.get("nickname").getAsString();
                    boolean ready = player.get("ready").getAsBoolean();
                    boolean isCreator = player.get("isCreator").getAsBoolean();

                    // 构建显示文本：昵称 + 房主标识 + 准备状态
                    String displayText = nickname;
                    if (isCreator) displayText += " 房主";
                    displayText += ready ? " 已准备" : " 未准备";
                    playerListModel.addElement(displayText);
                }

                // 首次进入房间时切换到房间视图
                if (!isInRoom) {
                    isInRoom = true;
                    showRoomPanel();
                }
                setStatus("房间: " + roomCode + " | 玩家: " + players.size(),
                        new Color(46, 204, 113));
            } catch (Exception e) {
                setStatus("更新房间信息失败", Color.RED);
            }
        });
    }

    /** 切换到房间视图 */
    private void showRoomPanel() {
        remove(loginPanel);
        add(roomPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    /** 切换回登录视图 */
    private void showLoginPanel() {
        remove(roomPanel);
        add(loginPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        setStatus("输入昵称开始游戏", new Color(180, 180, 200));
    }

    /** 设置状态栏提示文本和颜色 */
    private void setStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }
}
