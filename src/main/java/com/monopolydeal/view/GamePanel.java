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
    /** 倒计时进度条面板 */
    private TimerBarPanel timerBarPanel;
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
    /** 卡牌选择操作栏（浮动在手牌区上方） */
    private CardSelectionBar cardSelectionBar;
    /** 本地玩家ID */
    private String localPlayerId;
    /** 是否轮到本地玩家操作 */
    private boolean isMyTurn;
    /** 倒计时定时器（每秒触发一次） */
    private javax.swing.Timer countdownTimer;
    /** 被点击的卡牌的视图模型（用于后续操作对话框） */
    private CardViewModel cardDataForClicked;

    // ==================== UI颜色常量 ====================

    private static final Color DARK_BG = new Color(18, 22, 28);         // 主背景深色
    private static final Color DARKER_BG = new Color(14, 17, 22);       // 更深背景
    private static final Color GOLD = new Color(255, 215, 0);           // 金色（高亮元素）
    private static final Color RED_GLOW = new Color(220, 50, 50);       // 红色发光
    private static final Color GREEN_TABLE = new Color(25, 70, 40);     // 绿桌面色
    private static final Color GREEN_DARK = new Color(15, 50, 28);      // 深绿桌面
    private static final Color TEXT_LIGHT = new Color(220, 220, 220);   // 浅色文字
    private static final Color TEXT_DIM = new Color(150, 150, 150);     // 暗淡文字

    /**
     * 构造函数 - 创建游戏界面的四个主要区域
     * @param client 已连接的GameClient实例
     */
    public GamePanel(GameClient client) {
        this.client = client;
        this.playerPanels = new LinkedHashMap<>();
        this.isMyTurn = false;

        setLayout(new BorderLayout());
        setBackground(DARK_BG);

        createTopBar();        // 创建顶栏
        createMainGameArea();  // 创建主游戏区
        createHandPanel();     // 创建手牌区
        createSidePanel();     // 创建侧边栏

        // 创建 CardSelectionBar 并设置回调
        cardSelectionBar = new CardSelectionBar();
        cardSelectionBar.setPlayCallback((cardId, action, targetId) -> {
            onCardActionConfirmed(cardId, action, targetId);
        });

        // 将 CardSelectionBar 和 handPanel 包装在 southWrapper 中
        JPanel southWrapper = new JPanel(new BorderLayout());
        southWrapper.setOpaque(false);
        southWrapper.add(cardSelectionBar, BorderLayout.NORTH);
        southWrapper.add(handPanel, BorderLayout.CENTER);

        // 组装四个区域
        add(topBarPanel, BorderLayout.NORTH);
        add(mainGamePanel, BorderLayout.CENTER);
        add(southWrapper, BorderLayout.SOUTH);
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
        phaseLabel = new JLabel("Phase: Waiting");
        phaseLabel.setForeground(GOLD);
        phaseLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

        // 当前回合标签（白色）
        turnLabel = new JLabel("Turn: -");
        turnLabel.setForeground(Color.WHITE);
        turnLabel.setFont(new Font("SansSerif", Font.BOLD, 15));

        leftPanel.add(phaseLabel);
        leftPanel.add(turnLabel);

        // ===== 右侧操作区 =====
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        rightPanel.setOpaque(false);

        // 抽牌堆数量标签
        drawPileLabel = new JLabel("Deck: 0");
        drawPileLabel.setForeground(TEXT_LIGHT);
        drawPileLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

        timerBarPanel = new TimerBarPanel(30);

        // 结束回合按钮（红色圆角，带按压和悬停效果）
        endTurnButton = new JButton("End Turn") {
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
                g2.setFont(new Font("SansSerif", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("End Turn", (getWidth() - fm.stringWidth("End Turn")) / 2,
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
        rightPanel.add(timerBarPanel);
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
        JLabel handLabel = new JLabel("Your Hand");
        handLabel.setForeground(GOLD);
        handLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        handPanel.add(handLabel, BorderLayout.NORTH);

        // 卡牌面板（自动换行布局，宽度不足时折行显示）
        handCardsPanel = new JPanel(new WrapLayout(FlowLayout.LEFT, 10, 8));
        handCardsPanel.setOpaque(false);

        JScrollPane handScrollPane = new JScrollPane(handCardsPanel);
        handScrollPane.setOpaque(false);
        handScrollPane.getViewport().setOpaque(false);
        handScrollPane.setBorder(null);
        handScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        handScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        handScrollPane.setPreferredSize(new Dimension(0, 170));

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
                String phase = gameState.has("phase") ? gameState.get("phase").getAsString() : "Unknown";
                phaseLabel.setText("Phase: " + phase.toUpperCase());

                String activePlayerId = gameState.has("activePlayerId") ?
                        gameState.get("activePlayerId").getAsString() : "";
                int drawPileSize = gameState.has("drawPileSize") ?
                        gameState.get("drawPileSize").getAsInt() : 0;
                drawPileLabel.setText("Deck: " + drawPileSize);

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
     * 处理 Just Say No 响应请求
     * 服务器 payload 格式:
     * {"resolutionId":"...","actionType":"RENT|DEBT_COLLECTOR|...",
     *  "initiatorName":"玩家名","initiatorId":"...","cardName":"...","timeoutSeconds":5}
     */
    public void handleReactionRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject req = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String resolutionId = req.get("resolutionId").getAsString();
                String initiatorName = req.get("initiatorName").getAsString();
                String actionType = req.has("actionType") ? req.get("actionType").getAsString() : "";
                String cardName = req.has("cardName") ? req.get("cardName").getAsString() : "";
                int timeout = req.has("timeoutSeconds") ? req.get("timeoutSeconds").getAsInt() : 5;

                String msg = initiatorName + " used " + cardName + " (" + actionType + ") on you!\nPlay Just Say No?";
                String[] options = new String[]{"Play Just Say No", "Pass"};

                // 模态对话框 + 独立线程超时定时器
                // 关键：保持模态（不调用 setModal(false)），setVisible 会阻塞 EDT 等待用户操作
                // 使用 java.util.Timer（非 EDT 线程）在超时时 dispose 对话框解除阻塞
                JOptionPane pane = new JOptionPane(msg, JOptionPane.QUESTION_MESSAGE,
                        JOptionPane.YES_NO_OPTION, null, options, options[1]);
                JDialog dialog = pane.createDialog(GamePanel.this, "React");

                java.util.Timer timeoutTimer = new java.util.Timer();
                timeoutTimer.schedule(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        dialog.dispose();   // Window.dispose() 是线程安全的
                    }
                }, timeout * 1000L);

                dialog.setVisible(true);    // 阻塞 EDT，直到用户点击 或 定时器 dispose
                timeoutTimer.cancel();      // 清除定时器

                Object selected = pane.getValue();
                // selected == null → 超时（对话框被 Timer dispose）
                // selected == 1 → 用户点击"放弃"
                // selected == 0 → 用户点击"打出 Just Say No"
                if (selected == null || Integer.valueOf(1).equals(selected)) {
                    client.sendMessage(MessageProtocol.MessageType.PASS_REACTION, "{}");
                } else if (Integer.valueOf(0).equals(selected)) {
                    String jsnCardId = findJustSayNoCardInHand();
                    if (jsnCardId != null) {
                        JsonObject jsnPayload = new JsonObject();
                        jsnPayload.addProperty("resolutionId", resolutionId);
                        jsnPayload.addProperty("cardId", jsnCardId);
                        client.sendMessage(MessageProtocol.MessageType.PLAY_JUST_SAY_NO,
                                jsnPayload.toString());
                    } else {
                        JOptionPane.showMessageDialog(GamePanel.this,
                                "You don't have a Just Say No card!", "Notice", JOptionPane.WARNING_MESSAGE);
                        client.sendMessage(MessageProtocol.MessageType.PASS_REACTION, "{}");
                    }
                }
            } catch (Exception e) {
                System.err.println("处理 REACTION_REQUIRED 时出错：" + e.getMessage());
            }
        });
    }

    /**
     * 在手牌中找到第一张 Just Say No 卡牌
     */
    private String findJustSayNoCardInHand() {
        for (Component comp : handCardsPanel.getComponents()) {
            if (comp instanceof CardRenderer) {
                CardRenderer cr = (CardRenderer) comp;
                CardViewModel vm = cr.getViewModel();
                if (vm != null && vm.getCardName().contains("Just Say No")) {
                    return vm.getCardId();
                }
            }
        }
        return null;
    }

    /**
     * 处理支付请求
     * 服务器 payload 格式:
     * {"creditorName":"收款方","creditorId":"...","amount":5,"totalBank":10,
     *  "bankCards":[{"cardId":"...","cardName":"...","value":5,...},...]}
     */
    public void handlePaymentRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject req = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String creditorName = req.get("creditorName").getAsString();
                int amount = req.get("amount").getAsInt();
                JsonArray bankCardsArr = req.getAsJsonArray("bankCards");

                // 构建选项列表
                int n = bankCardsArr.size();
                String[] cardDescriptions = new String[n];
                for (int i = 0; i < n; i++) {
                    JsonObject c = bankCardsArr.get(i).getAsJsonObject();
                    String name = c.has("cardName") ? c.get("cardName").getAsString() : "Card";
                    int value = c.has("value") ? c.get("value").getAsInt() : 0;
                    cardDescriptions[i] = name + " (" + value + "M)";
                }

                // 复选列表让玩家选择要支付的卡牌
                JList<String> cardList = new JList<>(cardDescriptions);
                cardList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                cardList.setVisibleRowCount(Math.min(8, n));
                JScrollPane scrollPane = new JScrollPane(cardList);

                JPanel panel = new JPanel(new BorderLayout(0, 10));
                panel.add(new JLabel("Pay " + creditorName + " " + amount + "M. Select cards to pay:"),
                        BorderLayout.NORTH);
                panel.add(scrollPane, BorderLayout.CENTER);
                JLabel totalLabel = new JLabel("Selected: 0 M / Required: " + amount + " M");
                panel.add(totalLabel, BorderLayout.SOUTH);

                // 监听选择变化，实时显示已选总额
                cardList.addListSelectionListener(e -> {
                    if (e.getValueIsAdjusting()) return;
                    int total = 0;
                    for (int idx : cardList.getSelectedIndices()) {
                        JsonObject c = bankCardsArr.get(idx).getAsJsonObject();
                        total += c.has("value") ? c.get("value").getAsInt() : 0;
                    }
                    totalLabel.setText("Selected: " + total + " M / Required: " + amount + " M");
                });

                int result = JOptionPane.showConfirmDialog(GamePanel.this, panel,
                        "Pay", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

                if (result == JOptionPane.OK_OPTION) {
                    JsonObject submitPayload = new JsonObject();
                    JsonArray selectedIds = new JsonArray();
                    for (int idx : cardList.getSelectedIndices()) {
                        JsonObject c = bankCardsArr.get(idx).getAsJsonObject();
                        selectedIds.add(c.get("cardId").getAsString());
                    }
                    submitPayload.add("cardIds", selectedIds);
                    client.sendMessage(MessageProtocol.MessageType.SUBMIT_PAYMENT,
                            submitPayload.toString());
                }
                // 取消则让服务器超时自动处理

            } catch (Exception e) {
                System.err.println("处理 PAYMENT_REQUIRED 时出错：" + e.getMessage());
            }
        });
    }

    /**
     * 处理弃牌请求（回合结束时手牌超上限）
     * 服务器 payload 格式:
     * {"handCards":[{"cardId":"...","cardName":"租金卡","cardType":"RENT","color":"RED","value":0},...],
     *  "discardCount":2, "timeoutSeconds":15}
     */
    public void handleDiscardRequired(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject req = JsonParser.parseString(jsonPayload).getAsJsonObject();
                int discardCount = req.get("discardCount").getAsInt();
                int timeout = req.has("timeoutSeconds") ? req.get("timeoutSeconds").getAsInt() : 15;
                JsonArray handCardsArr = req.getAsJsonArray("handCards");

                // 构建卡牌描述列表
                int n = handCardsArr.size();
                String[] cardDescriptions = new String[n];
                for (int i = 0; i < n; i++) {
                    JsonObject c = handCardsArr.get(i).getAsJsonObject();
                    String name = c.has("cardName") ? c.get("cardName").getAsString() : "Card";
                    String type = c.has("cardType") ? c.get("cardType").getAsString() : "";
                    cardDescriptions[i] = name + " [" + type + "]";
                }

                // 手牌多选列表
                JList<String> cardList = new JList<>(cardDescriptions);
                cardList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
                cardList.setVisibleRowCount(Math.min(10, n));
                JScrollPane scrollPane = new JScrollPane(cardList);

                // 信息标签（含倒计时）
                JLabel infoLabel = new JLabel("Hand limit exceeded! Select " + discardCount + " cards to discard (" + timeout + "s remaining)");
                infoLabel.setForeground(new Color(255, 200, 100));
                JLabel countLabel = new JLabel("Selected: 0 / Need: " + discardCount);

                // 选择变化监听：实时更新已选数量
                cardList.addListSelectionListener(e -> {
                    if (e.getValueIsAdjusting()) return;
                    countLabel.setText("Selected: " + cardList.getSelectedIndices().length + " / Need: " + discardCount);
                });

                // 组装面板
                JPanel panel = new JPanel(new BorderLayout(0, 10));
                panel.setPreferredSize(new Dimension(350, 280));
                panel.add(infoLabel, BorderLayout.NORTH);
                panel.add(scrollPane, BorderLayout.CENTER);
                panel.add(countLabel, BorderLayout.SOUTH);

                // 确认/取消按钮
                String[] options = new String[]{"Confirm Discard", "Cancel (auto-discard)"};
                JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE,
                        JOptionPane.OK_CANCEL_OPTION, null, options, options[0]);
                JDialog dialog = pane.createDialog(GamePanel.this, "Discard");

                // 倒计时定时器（非EDT线程，超时时 dispose 对话框）
                java.util.Timer countdownTimer = new java.util.Timer();
                final int[] remaining = {timeout};
                countdownTimer.scheduleAtFixedRate(new java.util.TimerTask() {
                    @Override
                    public void run() {
                        remaining[0]--;
                        if (remaining[0] <= 0) {
                            dialog.dispose();
                        } else {
                            SwingUtilities.invokeLater(() ->
                                    infoLabel.setText("Hand limit exceeded! Select " + discardCount + " cards to discard (" + remaining[0] + "s remaining)"));
                        }
                    }
                }, 1000L, 1000L);

                dialog.setVisible(true);  // 阻塞 EDT，直到用户点击 或 定时器 dispose
                countdownTimer.cancel();

                Object selected = pane.getValue();

                // 构建提交的卡牌ID列表
                JsonArray selectedIds = new JsonArray();
                if (selected != null && selected.equals(options[0])) {
                    // 用户点击"确认弃牌"
                    if (cardList.getSelectedIndices().length < discardCount) {
                        JOptionPane.showMessageDialog(GamePanel.this,
                                "Still need " + (discardCount - cardList.getSelectedIndices().length) + " cards!\nAuto-discard from hand start will be used.",
                                "Notice", JOptionPane.WARNING_MESSAGE);
                    }
                    for (int idx : cardList.getSelectedIndices()) {
                        JsonObject c = handCardsArr.get(idx).getAsJsonObject();
                        selectedIds.add(c.get("cardId").getAsString());
                    }
                }
                // 超时或取消时 selectedIds 为空数组，服务端自动兜底

                JsonObject submitPayload = new JsonObject();
                submitPayload.add("cardIds", selectedIds);
                client.sendMessage(MessageProtocol.MessageType.SUBMIT_DISCARD,
                        submitPayload.toString());

            } catch (Exception e) {
                System.err.println("处理 DISCARD_REQUIRED 时出错：" + e.getMessage());
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
                    playerData.get("nickname").getAsString() : "Unknown";
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

        // 同步对手玩家信息到 CardSelectionBar
        if (cardSelectionBar != null && localPlayerId != null && playerStates != null) {
            Map<String, String> opponents = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : playerStates.entrySet()) {
                String pid = entry.getKey();
                if (!pid.equals(localPlayerId)) {
                    try {
                        JsonObject pd = entry.getValue().getAsJsonObject();
                        String nick = pd.has("nickname") ? pd.get("nickname").getAsString() : "Unknown";
                        opponents.put(pid, nick);
                    } catch (Exception ignored) {}
                }
            }
            cardSelectionBar.updatePlayers(opponents);
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
        String activeNickname = "Unknown";
        if (playerStates.has(activePlayerId)) {
            JsonObject activeData = playerStates.getAsJsonObject(activePlayerId);
            activeNickname = activeData.has("nickname") ?
                    activeData.get("nickname").getAsString() : "Unknown";
        }
        turnLabel.setText("Turn: " + activeNickname);

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
            cardSelectionBar.dismiss();
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
        timerBarPanel.start(30);

        countdownTimer = new javax.swing.Timer(1000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                timerBarPanel.tick();
                if (timerBarPanel.getSecondsRemaining() <= 0) {
                    stopCountdown();
                    endTurnButton.setEnabled(false);
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
        timerBarPanel.setInactive();
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
                JsonObject json = elem.getAsJsonObject();
                CardViewModel vm = new CardViewModel(
                        json.has("cardId")   ? json.get("cardId").getAsString()   : "",
                        json.has("cardName") ? json.get("cardName").getAsString() : "",
                        json.has("cardType") ? json.get("cardType").getAsString() : "MONEY",
                        json.has("color")    ? json.get("color").getAsString()    : "NONE",
                        json.has("value")    ? json.get("value").getAsInt()       : 0
                );

                CardRenderer card = new CardRenderer(vm);
                card.setEnabled(isMyTurn);

                // 设置卡牌点击回调
                card.setPlayListener(id -> {
                    cardDataForClicked = vm;
                    onCardClicked(id, vm.getCardType());
                });
                handCardsPanel.add(card);
            }
        }
        handCardsPanel.revalidate();
        handCardsPanel.repaint();
    }

    /**
     * 处理卡牌点击事件 — 显示 CardSelectionBar 操作栏
     *
     * @param cardId 被点击的卡牌ID
     * @param cardType 卡牌类型（MONEY/PROPERTY/RENT/ACTION）
     */
    private void onCardClicked(String cardId, String cardType) {
        if (!isMyTurn) {
            JOptionPane.showMessageDialog(this, "It's not your turn!");
            return;
        }

        String cardName = cardDataForClicked != null
                ? cardDataForClicked.getCardName() : "";

        cardSelectionBar.show(cardId, cardName, cardType);
    }

    /**
     * CardSelectionBar 确认回调 — 构建并发送 PLAY_CARD 消息
     *
     * @param cardId 卡牌ID
     * @param action 操作类型（PLAY_MONEY/PLAY_PROPERTY/PLAY_RENT/PLAY_ACTION）
     * @param targetId 目标玩家ID（可为null）
     */
    private void onCardActionConfirmed(String cardId, String action, String targetId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("cardId", cardId);
        payload.addProperty("action", action);

        // 附加目标玩家ID（租金卡、行动卡需要指定目标）
        if (targetId != null && !targetId.isEmpty()) {
            payload.addProperty("targetPlayerId", targetId);
        }

        // 万能卡颜色选择：在动作确认后弹出颜色选择器
        if (cardDataForClicked != null) {
            String colorStr = cardDataForClicked.getColor();
            String cardName = cardDataForClicked.getCardName();

            if ("WILD".equals(colorStr) && "PLAY_PROPERTY".equals(action)) {
                String selectedColor = showWildColorPicker(cardName);
                if (selectedColor != null) {
                    payload.addProperty("color", selectedColor);
                }
            }
            if ("WILD".equals(colorStr) && "PLAY_RENT".equals(action)) {
                String selectedColor = showColorPicker();
                if (selectedColor != null) {
                    payload.addProperty("color", selectedColor);
                }
            }
        }

        client.sendMessage(MessageProtocol.MessageType.PLAY_CARD, payload.toString());
        cardSelectionBar.dismiss();
    }

    /**
     * 显示万能地产卡的颜色选择对话框
     * 根据万能卡的具体类型（多彩/双色）提供不同的可选颜色列表
     *
     * @param cardName 万能卡名称
     * @return 选中的颜色名称（取消返回null）
     */
    private String showWildColorPicker(String cardName) {
        String[] colors = AppTheme.WILD_COLOR_OPTIONS.get(cardName);
        if (colors == null) {
            colors = AppTheme.WILD_COLOR_OPTIONS.get("Multi-Color Wild");
        }
        return (String) JOptionPane.showInputDialog(this,
                "Choose a color for this wild property:",
                "Wild Property Color",
                JOptionPane.QUESTION_MESSAGE,
                null, colors, colors[0]);
    }

    /**
     * 显示通用颜色选择对话框（用于万能租金卡）
     * @return 选中的颜色名称
     */
    private String showColorPicker() {
        String[] colors = {"BROWN", "LIGHT_BLUE", "PINK", "ORANGE", "RED",
                "YELLOW", "GREEN", "BLUE", "BLACK", "LIGHT_GREEN"};
        return (String) JOptionPane.showInputDialog(this,
                "Choose a color:",
                "Color Selection",
                JOptionPane.QUESTION_MESSAGE,
                null, colors, colors[0]);
    }

    /** 结束回合 - 停止倒计时并发送END_TURN消息 */
    private void endTurn() {
        stopCountdown();
        cardSelectionBar.dismiss();
        client.sendMessage(MessageProtocol.MessageType.END_TURN, "{}");
        endTurnButton.setEnabled(false);
    }
}
