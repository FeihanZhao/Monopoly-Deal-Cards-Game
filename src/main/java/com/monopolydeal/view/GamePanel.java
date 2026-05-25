package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.*;
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

/**
 * 游戏面板 - 游戏进行时的主界面
 *
 * 布局结构（自上而下）：
 * 1. 顶栏（topBarPanel）- 阶段标签、当前回合、倒计时、抽牌堆数量、结束回合按钮
 * 2. 主游戏区（mainGamePanel）- 绿色桌面背景，显示所有玩家的PlayerPanel
 * 3. 手牌区（handPanel）- 底部的玩家手牌展示区，可横向滚动
 * 4. 侧边栏（sidePanel）- 右侧的行动历史记录面板
 *
 * 核心工作流程：
 * - 每次收到GAME_STATE_UPDATE消息时，调用updateGameState()重建整个界面
 * - 从JSON中解析所有玩家状态、手牌信息（仅自己可见）、行动历史
 * - 当轮到本地玩家时，激活手牌交互和倒计时
 *
 * 玩家交互流程：
 * 1. 点击手牌 → 弹出操作选项对话框（打出/存入银行）
 * 2. 万能地产卡 → 弹出颜色选择对话框
 * 3. 万能租金卡 → 弹出颜色选择对话框
 */
public class GamePanel extends JPanel {
    /** 游戏客户端连接 */
    private final GameClient client;
    /** 顶栏面板 */
    private JPanel topBarPanel;
    /** 主游戏区域面板 */
    private JPanel mainGamePanel;
    /** 手牌区域面板 */
    private JPanel handPanel;
    /** 侧边栏面板 */
    private JPanel sidePanel;
    /** 阶段标签（显示当前游戏阶段：DRAW/PLAY/END等） */
    private JLabel phaseLabel;
    /** 当前回合标签（显示正在操作的玩家昵称） */
    private JLabel turnLabel;
    /** 倒计时标签（圆形计时器显示） */
    private JLabel timerLabel;
    /** 抽牌堆剩余数量标签 */
    private JLabel drawPileLabel;
    /** 结束回合按钮 */
    private JButton endTurnButton;
    /** 玩家面板容器（竖向排列所有PlayerPanel） */
    private JPanel playerPanelsContainer;
    /** 玩家面板映射表 key=playerId, value=PlayerPanel */
    private Map<String, PlayerPanel> playerPanels;
    /** 手牌卡牌面板（横向排列CardRenderer组件） */
    private JPanel handCardsPanel;
    /** 行动历史记录面板 */
    private ActionHistoryPanel actionHistoryPanel;
    /** 本地玩家ID */
    private String localPlayerId;
    /** 是否轮到本地玩家操作 */
    private boolean isMyTurn;
    /** 倒计时定时器（每秒触发一次） */
    private javax.swing.Timer countdownTimer;
    /** 倒计时剩余秒数 */
    private int secondsRemaining;
    /** 被点击的卡牌的完整数据（用于后续操作对话框） */
    private JsonObject cardDataForClicked;

    // ==================== UI颜色常量 ====================

    private static final Color DARK_BG = new Color(18, 22, 28);         // 主背景深色
    private static final Color DARKER_BG = new Color(14, 17, 22);       // 更深背景
    private static final Color GOLD = new Color(255, 215, 0);           // 金色（高亮元素）
    private static final Color RED_GLOW = new Color(220, 50, 50);       // 红色发光
    private static final Color GREEN_TABLE = new Color(25, 70, 40);     // 绿桌面色
    private static final Color GREEN_DARK = new Color(15, 50, 28);      // 深绿桌面
    private static final Color TEXT_LIGHT = new Color(220, 220, 220);   // 浅色文字
    private static final Color TEXT_DIM = new Color(150, 150, 150);     // 暗淡文字

    /** 万能地产卡的颜色选择映射表 key=万能卡名称, value=可选颜色列表 */
    private static final Map<String, String[]> WILD_COLOR_OPTIONS = new LinkedHashMap<>();
    static {
        WILD_COLOR_OPTIONS.put("Multi-Color Wild",
                new String[]{"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED",
                        "YELLOW", "GREEN", "BLUE", "PURPLE", "BLACK", "LIGHT_GREEN"});
        WILD_COLOR_OPTIONS.put("Dark Blue/Green Wild",
                new String[]{"BLUE", "GREEN"});
        WILD_COLOR_OPTIONS.put("Red/Yellow Wild",
                new String[]{"RED", "YELLOW"});
        WILD_COLOR_OPTIONS.put("Brown/Light Blue Wild",
                new String[]{"BROWN", "LIGHT_BLUE"});
        WILD_COLOR_OPTIONS.put("Orange/Pink Wild",
                new String[]{"ORANGE", "PINK"});
        WILD_COLOR_OPTIONS.put("Light Green/Black Wild",
                new String[]{"LIGHT_GREEN", "BLACK"});
    }

    /**
     * 构造函数 - 创建游戏界面的四个主要区域
     * @param client 已连接的GameClient实例
     */
    public GamePanel(GameClient client) {
        this.client = client;
        this.playerPanels = new LinkedHashMap<>();
        this.isMyTurn = false;
        this.secondsRemaining = 30;

        setLayout(new BorderLayout());
        setBackground(DARK_BG);

        createTopBar();        // 创建顶栏
        createMainGameArea();  // 创建主游戏区
        createHandPanel();     // 创建手牌区
        createSidePanel();     // 创建侧边栏

        // 组装四个区域
        add(topBarPanel, BorderLayout.NORTH);
        add(mainGamePanel, BorderLayout.CENTER);
        add(handPanel, BorderLayout.SOUTH);
        add(sidePanel, BorderLayout.EAST);
    }

    /** 创建顶栏 - 包含阶段信息、当前回合、倒计时、结束回合按钮 */
    private void createTopBar() {
        topBarPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 从上到下的暗色渐变背景
                GradientPaint gp = new GradientPaint(0, 0, new Color(25, 30, 40),
                        0, getHeight(), new Color(18, 22, 28));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // 底部分隔线（微妙的金色）
                g2.setColor(new Color(255, 215, 0, 40));
                g2.fillRect(0, getHeight() - 2, getWidth(), 2);
                g2.dispose();
            }
        };
        topBarPanel.setOpaque(false);
        topBarPanel.setBorder(new EmptyBorder(12, 25, 12, 25));
        topBarPanel.setPreferredSize(new Dimension(0, 60));

        // ===== 左侧信息区 =====
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 0));
        leftPanel.setOpaque(false);

        // 游戏阶段标签（金色）
        phaseLabel = new JLabel("阶段: 等待中");
        phaseLabel.setForeground(GOLD);
        phaseLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        // 当前回合标签（白色）
        turnLabel = new JLabel("当前回合: -");
        turnLabel.setForeground(Color.WHITE);
        turnLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));

        leftPanel.add(phaseLabel);
        leftPanel.add(turnLabel);

        // ===== 右侧操作区 =====
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        rightPanel.setOpaque(false);

        // 抽牌堆数量标签
        drawPileLabel = new JLabel("牌堆: 0");
        drawPileLabel.setForeground(TEXT_LIGHT);
        drawPileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // 圆形倒计时标签（自定义绘制圆形背景）
        timerLabel = new JLabel("30") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 绘制黑色半透明圆形背景
                g2.setColor(new Color(0, 0, 0, 80));
                g2.fillOval(2, 2, 46, 46);
                g2.setColor(new Color(40, 45, 55));
                g2.fillOval(0, 0, 46, 46);
                // 绘制圆形边框
                g2.setStroke(new BasicStroke(2.5f));
                g2.setColor(timerLabel.getForeground());
                g2.drawOval(1, 1, 44, 44);
                // 绘制倒计时数字
                g2.setFont(new Font("Segoe UI", Font.BOLD, 16));
                FontMetrics fm = g2.getFontMetrics();
                String text = getText();
                int tx = (50 - fm.stringWidth(text)) / 2;
                int ty = (50 - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(text, tx, ty);
                g2.dispose();
            }
        };
        timerLabel.setForeground(Color.WHITE);
        timerLabel.setPreferredSize(new Dimension(50, 50));
        timerLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // 结束回合按钮（红色圆角，带按压和悬停效果）
        endTurnButton = new JButton("结束回合") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 根据状态选择背景色：按下>悬停>正常>禁用
                if (getModel().isPressed()) {
                    g2.setColor(new Color(140, 20, 20));
                } else if (getModel().isRollover() && isEnabled()) {
                    g2.setColor(new Color(200, 40, 40));
                } else {
                    g2.setColor(isEnabled() ? RED_GLOW : new Color(80, 80, 80));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 25, 25);
                // 绘制白色文字
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("结束回合", (getWidth() - fm.stringWidth("结束回合")) / 2,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        };
        endTurnButton.setPreferredSize(new Dimension(110, 38));
        endTurnButton.setBorderPainted(false);
        endTurnButton.setContentAreaFilled(false);
        endTurnButton.setFocusPainted(false);
        endTurnButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        endTurnButton.setEnabled(false);  // 初始禁用
        endTurnButton.addActionListener(e -> endTurn());

        rightPanel.add(drawPileLabel);
        rightPanel.add(timerLabel);
        rightPanel.add(endTurnButton);

        topBarPanel.add(leftPanel, BorderLayout.WEST);
        topBarPanel.add(rightPanel, BorderLayout.EAST);
    }

    /** 创建主游戏区 - 绿色毛毡桌面背景 + 玩家面板容器 */
    private void createMainGameArea() {
        mainGamePanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 从深绿到浅绿的渐变桌面
                GradientPaint gp = new GradientPaint(0, 0, GREEN_DARK,
                        getWidth(), getHeight(), GREEN_TABLE);
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // 装饰性椭圆形纹理（扑克桌风格）
                g2.setColor(new Color(255, 255, 255, 3));
                for (int x = 0; x < getWidth(); x += 60) {
                    for (int y = 0; y < getHeight(); y += 60) {
                        g2.drawOval(x, y, 40, 40);
                    }
                }
                g2.dispose();
            }
        };
        mainGamePanel.setOpaque(false);

        // 玩家面板垂直排列容器
        playerPanelsContainer = new JPanel();
        playerPanelsContainer.setLayout(new BoxLayout(playerPanelsContainer, BoxLayout.Y_AXIS));
        playerPanelsContainer.setOpaque(false);
        playerPanelsContainer.setBorder(new EmptyBorder(10, 15, 10, 15));

        JScrollPane scrollPane = new JScrollPane(playerPanelsContainer);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);

        mainGamePanel.add(scrollPane, BorderLayout.CENTER);
    }

    /** 创建手牌区 - 底部横向滚动的卡牌展示区 */
    private void createHandPanel() {
        handPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // 暗色渐变背景
                GradientPaint gp = new GradientPaint(0, 0, new Color(22, 26, 32),
                        0, getHeight(), new Color(16, 19, 24));
                g2.setPaint(gp);
                g2.fillRect(0, 0, getWidth(), getHeight());
                // 顶部分隔线（金色微光）
                g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 60));
                g2.fillRect(0, 0, getWidth(), 2);
                g2.dispose();
            }
        };
        handPanel.setOpaque(false);
        handPanel.setBorder(new EmptyBorder(10, 15, 12, 15));
        handPanel.setPreferredSize(new Dimension(0, 210));

        // "你的手牌"标签
        JLabel handLabel = new JLabel("你的手牌");
        handLabel.setForeground(GOLD);
        handLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        handPanel.add(handLabel, BorderLayout.NORTH);

        // 卡牌面板（横向排列，可左右滚动）
        handCardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        handCardsPanel.setOpaque(false);

        JScrollPane handScrollPane = new JScrollPane(handCardsPanel);
        handScrollPane.setOpaque(false);
        handScrollPane.getViewport().setOpaque(false);
        handScrollPane.setBorder(null);
        handScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        handScrollPane.setPreferredSize(new Dimension(0, 170));
        handScrollPane.getHorizontalScrollBar().setUnitIncrement(20);

        handPanel.add(handScrollPane, BorderLayout.CENTER);
    }

    /** 创建侧边栏 - 右侧的行动历史记录面板 */
    private void createSidePanel() {
        sidePanel = new JPanel(new BorderLayout());
        sidePanel.setBackground(DARKER_BG);
        sidePanel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(50, 55, 65)));
        sidePanel.setPreferredSize(new Dimension(260, 0));
        actionHistoryPanel = new ActionHistoryPanel();
        sidePanel.add(actionHistoryPanel, BorderLayout.CENTER);
    }

    /**
     * 更新游戏状态 - 收到GAME_STATE_UPDATE消息时调用
     * 解析完整的游戏状态JSON，更新所有UI组件
     *
     * @param jsonPayload GAME_STATE_UPDATE消息的JSON负载
     */
    public void updateGameState(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject gameState = JsonParser.parseString(jsonPayload).getAsJsonObject();

                // 更新本地玩家ID（服务器分配的viewerId）
                if (gameState.has("viewerId")) {
                    String myId = gameState.get("viewerId").getAsString();
                    if (!myId.equals(localPlayerId)) {
                        localPlayerId = myId;
                    }
                }

                // 更新阶段和牌堆信息
                String phase = gameState.has("phase") ? gameState.get("phase").getAsString() : "未知";
                phaseLabel.setText("阶段: " + phase.toUpperCase());

                String activePlayerId = gameState.has("activePlayerId") ?
                        gameState.get("activePlayerId").getAsString() : "";
                int drawPileSize = gameState.has("drawPileSize") ?
                        gameState.get("drawPileSize").getAsInt() : 0;
                drawPileLabel.setText("牌堆: " + drawPileSize);

                // 更新所有玩家面板
                JsonObject playerStates = gameState.has("playerStates") ?
                        gameState.getAsJsonObject("playerStates") : null;
                if (playerStates != null) {
                    updatePlayerPanelsFromStates(playerStates, activePlayerId);
                    updateTurnInfo(activePlayerId, playerStates);
                    updateLocalHand(playerStates);
                }

                // 更新行动历史
                JsonArray actions = gameState.has("actionHistory") ?
                        gameState.getAsJsonArray("actionHistory") : null;
                if (actions != null) {
                    actionHistoryPanel.updateActions(actions);
                }
            } catch (Exception e) {
                System.err.println("更新游戏状态时出错：" + e.getMessage());
            }
        });
    }

    /**
     * 根据玩家状态JSON更新所有PlayerPanel
     * 自动创建新玩家的面板，移除已离开玩家的面板
     */
    private void updatePlayerPanelsFromStates(JsonObject playerStates, String activePlayerId) {
        Set<String> existingIds = new HashSet<>(playerPanels.keySet());
        for (Map.Entry<String, JsonElement> entry : playerStates.entrySet()) {
            String playerId = entry.getKey();
            JsonObject playerData = entry.getValue().getAsJsonObject();
            existingIds.remove(playerId);

            // 创建或获取PlayerPanel
            PlayerPanel panel = playerPanels.get(playerId);
            if (panel == null) {
                panel = new PlayerPanel(playerId);
                playerPanels.put(playerId, panel);
                playerPanelsContainer.add(panel);
            }

            // 解析玩家数据
            boolean isActive = playerData.has("isActivePlayer") &&
                    playerData.get("isActivePlayer").getAsBoolean();
            String nickname = playerData.has("nickname") ?
                    playerData.get("nickname").getAsString() : "未知";
            int handCount = playerData.has("handCount") ?
                    playerData.get("handCount").getAsInt() : 0;
            int bankTotal = playerData.has("bankTotal") ?
                    playerData.get("bankTotal").getAsInt() : 0;
            int completeSets = playerData.has("completeSets") ?
                    playerData.get("completeSets").getAsInt() : 0;
            int remainingPlays = playerData.has("remainingPlays") ?
                    playerData.get("remainingPlays").getAsInt() : 0;
            boolean connected = !playerData.has("isConnected") ||
                    playerData.get("isConnected").getAsBoolean();

            // 解析各颜色地产数量
            Map<String, Integer> propertyColorCounts = new LinkedHashMap<>();
            if (playerData.has("propertyColorCounts")) {
                JsonObject colorCounts = playerData.getAsJsonObject("propertyColorCounts");
                for (Map.Entry<String, JsonElement> colorEntry : colorCounts.entrySet()) {
                    propertyColorCounts.put(colorEntry.getKey(), colorEntry.getValue().getAsInt());
                }
            }

            // 构建简化的更新数据
            JsonObject simplified = new JsonObject();
            simplified.addProperty("nickname", nickname);
            simplified.addProperty("isActive", isActive);
            simplified.addProperty("handCount", handCount);
            simplified.addProperty("bankTotal", bankTotal);
            simplified.addProperty("completeSets", completeSets);
            simplified.addProperty("remainingPlays", remainingPlays);
            simplified.addProperty("connected", connected);
            panel.updateFromJson(simplified, propertyColorCounts);
        }

        // 移除已离开的玩家面板
        for (String removedId : existingIds) {
            PlayerPanel panel = playerPanels.remove(removedId);
            if (panel != null) playerPanelsContainer.remove(panel);
        }
        playerPanelsContainer.revalidate();
        playerPanelsContainer.repaint();
    }

    /**
     * 更新回合信息 - 检测是否轮到本地玩家、更新活跃玩家昵称显示
     */
    private void updateTurnInfo(String activePlayerId, JsonObject playerStates) {
        if (localPlayerId == null) return;

        boolean wasMyTurn = isMyTurn;
        isMyTurn = activePlayerId.equals(localPlayerId);

        // 更新当前回合玩家昵称
        String activeNickname = "未知";
        if (playerStates.has(activePlayerId)) {
            JsonObject activeData = playerStates.getAsJsonObject(activePlayerId);
            activeNickname = activeData.has("nickname") ?
                    activeData.get("nickname").getAsString() : "未知";
        }
        turnLabel.setText("当前回合: " + activeNickname);

        // 回合切换时更新UI状态
        if (isMyTurn && !wasMyTurn) {
            // 刚轮到本地玩家：启动倒计时、启用按钮、高亮手牌区
            startCountdown();
            endTurnButton.setEnabled(true);
            handPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(2, 0, 0, 0, GOLD),
                    new EmptyBorder(8, 15, 12, 15)));
        } else if (isMyTurn && wasMyTurn) {
            // 仍然是本地玩家的回合
            endTurnButton.setEnabled(true);
        } else if (!isMyTurn && wasMyTurn) {
            // 刚结束本地玩家的回合：停止倒计时、禁用按钮
            stopCountdown();
            endTurnButton.setEnabled(false);
            handPanel.setBorder(new EmptyBorder(10, 15, 12, 15));
        }

        // 同步手牌卡片的可交互状态
        for (Component comp : handCardsPanel.getComponents()) {
            if (comp instanceof CardRenderer) {
                comp.setEnabled(isMyTurn);
            }
        }
    }

    /** 启动倒计时 - 创建每秒触发的Swing Timer */
    private void startCountdown() {
        stopCountdown();
        secondsRemaining = 30;
        timerLabel.setText("30");
        timerLabel.setForeground(Color.WHITE);

        countdownTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                secondsRemaining--;
                timerLabel.setText(String.valueOf(secondsRemaining));
                // 剩余10秒内变红警告
                if (secondsRemaining <= 10) {
                    timerLabel.setForeground(RED_GLOW);
                }
                // 倒计时结束自动结束回合
                if (secondsRemaining <= 0) {
                    stopCountdown();
                    endTurnButton.setEnabled(false);
                    timerLabel.setText("0");
                    client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
                }
            }
        });
        countdownTimer.start();
    }

    /** 停止倒计时 */
    private void stopCountdown() {
        if (countdownTimer != null && countdownTimer.isRunning()) {
            countdownTimer.stop();
        }
        countdownTimer = null;
        timerLabel.setText("--");
        timerLabel.setForeground(TEXT_DIM);
    }

    /**
     * 更新本地玩家的手牌显示
     * 从playerStates中提取自己（viewerId匹配）的手牌数组
     */
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

                String cardType = cardData.has("cardType") ?
                        cardData.get("cardType").getAsString() : "MONEY";
                String cardId = cardData.has("cardId") ?
                        cardData.get("cardId").getAsString() : "";

                // 设置卡牌点击回调
                card.setPlayListener(id -> {
                    cardDataForClicked = cardData;
                    onCardClicked(id, cardType);
                });
                handCardsPanel.add(card);
            }
        }
        handCardsPanel.revalidate();
        handCardsPanel.repaint();
    }

    /**
     * 处理卡牌点击事件
     * 根据卡牌类型弹出不同的操作选项对话框
     *
     * @param cardId 被点击的卡牌ID
     * @param cardType 卡牌类型（MONEY/PROPERTY/RENT/ACTION）
     */
    private void onCardClicked(String cardId, String cardType) {
        if (!isMyTurn) {
            JOptionPane.showMessageDialog(this, "还没轮到你的回合！");
            return;
        }

        // 根据卡牌类型确定可选操作
        String[] options;
        switch (cardType) {
            case "MONEY":    options = new String[]{"存入银行"}; break;
            case "PROPERTY": options = new String[]{"放置地产"}; break;
            case "RENT":     options = new String[]{"使用租金"}; break;
            case "ACTION":   options = new String[]{"使用行动卡"}; break;
            default:         options = new String[]{"存入银行"};
        }

        int choice = JOptionPane.showOptionDialog(this,
                "这张卡牌要怎么使用？",
                "出牌",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        if (choice >= 0) {
            JsonObject payload = new JsonObject();
            payload.addProperty("cardId", cardId);
            payload.addProperty("action", options[choice]);

            // 万能地产卡/万能租金卡：弹出颜色选择对话框
            if (cardDataForClicked != null && cardDataForClicked.has("color")) {
                String colorStr = cardDataForClicked.get("color").getAsString();
                String cardName = cardDataForClicked.has("cardName") ?
                        cardDataForClicked.get("cardName").getAsString() : "";

                if ("WILD".equals(colorStr) && "放置地产".equals(options[choice])) {
                    String selectedColor = showWildColorPicker(cardName);
                    if (selectedColor != null) {
                        payload.addProperty("color", selectedColor);
                    }
                }

                if ("WILD".equals(colorStr) && "使用租金".equals(options[choice])) {
                    String selectedColor = showColorPicker();
                    if (selectedColor != null) {
                        payload.addProperty("color", selectedColor);
                    }
                }
            }

            client.sendMessage(MessageProtocol.MessageType.PLAY_CARD, payload.toString());
        }
    }

    /**
     * 显示万能地产卡的颜色选择对话框
     * 根据万能卡的具体类型（多彩/双色）提供不同的可选颜色列表
     *
     * @param cardName 万能卡名称
     * @return 选中的颜色名称（取消返回null）
     */
    private String showWildColorPicker(String cardName) {
        String[] colors = WILD_COLOR_OPTIONS.get(cardName);
        if (colors == null) {
            colors = WILD_COLOR_OPTIONS.get("Multi-Color Wild");
        }
        return (String) JOptionPane.showInputDialog(this,
                "为这张万能地产选择颜色：",
                "万能地产颜色选择",
                JOptionPane.QUESTION_MESSAGE,
                null, colors, colors[0]);
    }

    /**
     * 显示通用颜色选择对话框（用于万能租金卡）
     * @return 选中的颜色名称
     */
    private String showColorPicker() {
        String[] colors = {"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED",
                "YELLOW", "GREEN", "BLUE", "PURPLE", "BLACK", "LIGHT_GREEN"};
        return (String) JOptionPane.showInputDialog(this,
                "选择颜色：",
                "颜色选择",
                JOptionPane.QUESTION_MESSAGE,
                null, colors, colors[0]);
    }

    /** 结束回合 - 停止倒计时并发送END_TURN消息 */
    private void endTurn() {
        stopCountdown();
        client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
        endTurnButton.setEnabled(false);
    }
}
