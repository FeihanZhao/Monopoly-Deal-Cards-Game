package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 卡牌选择操作栏 - 点击手牌后弹出的浮动操作栏
 *
 * 替代了之前使用JOptionPane的交互方式，提供更流畅的游戏体验。
 * 当玩家点击手牌中的一张卡牌后，此面板从手牌区上方滑出，显示：
 * 1. 卡牌名称和类型
 * 2. 操作按钮（打出/存入银行/取消）
 * 3. 目标玩家选择行（需要目标的卡牌类型）
 *
 * 支持的卡牌操作：
 * - 金钱卡 → 只能存入银行（"银行"按钮）
 * - 地产卡 → 可以放置到物业区（"放置"按钮）或存入银行
 * - 行动卡 → 可以打出（"打出"按钮）或存入银行（需要目标时显示目标选择）
 * - 租金卡 → 可以打出或存入银行
 *
 * 需要选择目标的卡牌类型：租金卡、特定行动卡（偷袭、强制交换、强行交易、债务收集者）
 *
 * 生命周期（由GamePanel管理）：
 * 1. updatePlayers() - 同步玩家列表
 * 2. show() - 展示操作栏
 * 3. dismiss() - 隐藏操作栏
 */
public class CardSelectionBar extends JPanel {

    // ==================== UI常量 ====================

    /** 操作栏高度 */
    private static final int BAR_HEIGHT        = 100;
    /** 操作栏圆角半径 */
    private static final int CORNER_RADIUS     = 14;
    /** 操作按钮高度 */
    private static final int BUTTON_H          = 34;
    /** 操作按钮圆角半径 */
    private static final int BUTTON_ARC        = 8;

    /** 背景色（深色半透明） */
    private static final Color BG_COLOR        = new Color(25, 25, 35, 230);
    /** 边框色（金色） */
    private static final Color BORDER_COLOR    = new Color(255, 215, 0, 180);
    /** 确认按钮颜色（绿色） */
    private static final Color BTN_CONFIRM     = new Color(34, 139, 34);
    /** 银行按钮颜色（蓝色） */
    private static final Color BTN_BANK        = new Color(30, 100, 180);
    /** 取消按钮颜色（红色） */
    private static final Color BTN_CANCEL      = new Color(120, 40, 40);
    /** 目标按钮默认颜色 */
    private static final Color BTN_TARGET_IDLE = new Color(60, 60, 80);
    /** 目标按钮选中颜色（金色） */
    private static final Color BTN_TARGET_SEL  = new Color(180, 130, 0);
    /** 主文字颜色 */
    private static final Color TEXT_PRIMARY    = Color.WHITE;
    /** 辅助文字颜色 */
    private static final Color TEXT_MUTED      = new Color(180, 180, 180);

    /** 需要指定单一目标玩家的卡牌名称 */
    private static final java.util.Set<String> NEEDS_TARGET_NAMES =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "Sly Deal", "Forced Deal", "Deal Breaker",
                    "Debt Collector", "Wild Rent"
            ));

    // ==================== 状态字段 ====================

    /** 当前选中的卡牌ID */
    private String selectedCardId   = null;
    /** 当前选中的卡牌名称 */
    private String selectedCardName = null;
    /** 当前选中的卡牌类型 */
    private String selectedCardType = null;
    /** 选中的目标玩家ID（null表示未选择） */
    private String selectedTargetId = null;

    /** 对手玩家映射表 id → 昵称（由GamePanel同步） */
    private final Map<String, String> opponentMap = new LinkedHashMap<>();

    /** 操作确认回调 (cardId, action, targetId) */
    private TriConsumer triCallback;

    // ==================== 子组件 ====================

    /** 卡牌名称标签 */
    private JLabel    cardNameLabel;
    /** 卡牌类型标签 */
    private JLabel    cardTypeLabel;
    /** 操作按钮行 */
    private JPanel    actionRow;
    /** 目标玩家选择行 */
    private JPanel    targetRow;
    /** 确认按钮（打出/放置） */
    private JButton   confirmButton;
    /** 存入银行按钮 */
    private JButton   bankButton;
    /** 取消按钮 */
    private JButton   cancelButton;
    /** 目标玩家按钮映射表 id → 按钮 */
    private final Map<String, JButton> targetButtons = new LinkedHashMap<>();

    // ==================== 构造函数 ====================

    /** 构造函数 - 构建操作栏UI */
    public CardSelectionBar() {
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        setPreferredSize(new Dimension(0, BAR_HEIGHT));
        buildUI();
        setVisible(false);  // 初始隐藏，必须在 buildUI() 之后
    }

    // ==================== 回调接口 ====================

    /**
     * 三参数回调接口
     * (卡牌ID, 操作类型, 目标玩家ID或null)
     */
    @FunctionalInterface
    public interface TriConsumer {
        void accept(String cardId, String action, String targetId);
    }

    /**
     * 设置操作确认回调
     * 当玩家点击"确认"或"银行"按钮时触发
     *
     * @param callback (cardId, action, targetId)
     */
    public void setPlayCallback(TriConsumer callback) {
        this.triCallback  = callback;
    }

    // ==================== 公共API ====================

    /**
     * 同步对手玩家列表
     * 由GamePanel在每个GAME_STATE_UPDATE中调用
     *
     * @param idToNickname 对手玩家ID → 昵称映射（不包含本地玩家）
     */
    public void updatePlayers(Map<String, String> idToNickname) {
        opponentMap.clear();
        opponentMap.putAll(idToNickname);
        rebuildTargetButtons();
    }

    /**
     * 显示操作栏（针对指定卡牌）
     * @param cardId 卡牌ID
     * @param cardName 卡牌名称
     * @param cardType 卡牌类型（MONEY/PROPERTY/ACTION/RENT）
     */
    public void show(String cardId, String cardName, String cardType) {
        this.selectedCardId   = cardId;
        this.selectedCardName = cardName;
        this.selectedCardType = cardType;
        this.selectedTargetId = null;

        cardNameLabel.setText(cardName);
        cardTypeLabel.setText(cardType);

        configureActionButtons(cardType, cardName);  // 配置操作按钮可见性
        configureTargetRow(cardType, cardName);       // 配置目标选择行
        resetTargetSelection();

        setVisible(true);
        revalidate();
        repaint();
    }

    /** 隐藏并重置操作栏 */
    public void dismiss() {
        if (!isVisible()) return;  // 防止递归重入
        selectedCardId   = null;
        selectedCardName = null;
        selectedCardType = null;
        selectedTargetId = null;
        setVisible(false);
    }

    // ==================== UI构建 ====================

    /** 构建操作栏的内部布局 */
    private void buildUI() {
        // 内层面板（带圆角背景和金色边框）
        JPanel inner = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                // 深色半透明圆角背景
                g2.setColor(BG_COLOR);
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
                // 金色边框
                g2.setColor(BORDER_COLOR);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(
                        0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f,
                        CORNER_RADIUS, CORNER_RADIUS));
                g2.dispose();
            }
        };
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(8, 14, 8, 14));

        // ===== 左侧：卡牌信息 =====
        JPanel infoPanel = new JPanel(new BorderLayout(0, 2));
        infoPanel.setOpaque(false);
        infoPanel.setPreferredSize(new Dimension(160, 0));

        cardNameLabel = new JLabel("—");
        cardNameLabel.setForeground(TEXT_PRIMARY);
        cardNameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        cardTypeLabel = new JLabel("—");
        cardTypeLabel.setForeground(TEXT_MUTED);
        cardTypeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        infoPanel.add(cardNameLabel, BorderLayout.CENTER);
        infoPanel.add(cardTypeLabel, BorderLayout.SOUTH);

        // ===== 中间：操作按钮 + 目标选择 =====
        JPanel centrePanel = new JPanel(new BorderLayout(0, 4));
        centrePanel.setOpaque(false);

        // 操作按钮行
        actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionRow.setOpaque(false);

        confirmButton = makeButton(" 打出",   BTN_CONFIRM);
        bankButton    = makeButton(" 银行",   BTN_BANK);
        cancelButton  = makeButton(" 取消",   BTN_CANCEL);

        confirmButton.addActionListener(e -> onConfirm());
        bankButton   .addActionListener(e -> onBank());
        cancelButton .addActionListener(e -> dismiss());

        actionRow.add(confirmButton);
        actionRow.add(bankButton);
        actionRow.add(cancelButton);

        // 目标选择行
        targetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        targetRow.setOpaque(false);
        targetRow.setVisible(false);

        JLabel targetLabel = new JLabel("目标: ");
        targetLabel.setForeground(TEXT_MUTED);
        targetLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        targetRow.add(targetLabel);

        centrePanel.add(actionRow, BorderLayout.NORTH);
        centrePanel.add(targetRow, BorderLayout.SOUTH);

        inner.add(infoPanel,   BorderLayout.WEST);
        inner.add(centrePanel, BorderLayout.CENTER);

        add(inner, BorderLayout.CENTER);
    }

    /** 创建统一样式的操作按钮 */
    private JButton makeButton(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? getBackground() : getBackground().darker());
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), BUTTON_ARC, BUTTON_ARC));
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(90, BUTTON_H));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // 悬停时略微变亮
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            final Color base = bg;
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(base.brighter());
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(base);
            }
        });

        return btn;
    }

    // ==================== 动态按钮配置 ====================

    /**
     * 根据卡牌类型配置操作按钮的可见性和文本
     * - 金钱卡：只显示"银行"按钮（无"打出"按钮）
     * - 地产卡：显示"放置" + "银行"
     * - 行动/租金卡：显示"打出" + "银行"
     */
    private void configureActionButtons(String cardType, String cardName) {
        switch (cardType) {
            case "MONEY":
                confirmButton.setVisible(false);    // 金钱卡只能存银行
                bankButton.setVisible(true);
                break;
            case "PROPERTY":
                confirmButton.setText(" 放置");     // 地产卡可以放置到物业区
                confirmButton.setVisible(true);
                bankButton.setVisible(true);
                break;
            case "ACTION":
            case "RENT":
                confirmButton.setText(" 打出");
                confirmButton.setVisible(true);
                bankButton.setVisible(true);
                break;
            default:
                confirmButton.setVisible(true);
                bankButton.setVisible(true);
        }
        actionRow.revalidate();
    }

    /**
     * 配置目标玩家选择行的可见性
     * 仅特定卡牌需要选择单一目标（Sly Deal、Forced Deal、Deal Breaker、Debt Collector、Wild Rent）
     */
    private void configureTargetRow(String cardType, String cardName) {
        boolean needsTargetPicker = NEEDS_TARGET_NAMES.contains(cardName);
        targetRow.setVisible(needsTargetPicker && !opponentMap.isEmpty());
    }

    // ==================== 目标选择 ====================

    /** 重建目标玩家按钮 */
    private void rebuildTargetButtons() {
        targetButtons.values().forEach(targetRow::remove);
        targetButtons.clear();

        for (Map.Entry<String, String> entry : opponentMap.entrySet()) {
            String id       = entry.getKey();
            String nickname = entry.getValue();

            JButton btn = makeButton(nickname, BTN_TARGET_IDLE);
            btn.setPreferredSize(new Dimension(
                    Math.max(70, nickname.length() * 8 + 16), BUTTON_H));
            btn.addActionListener(e -> selectTarget(id, btn));
            targetButtons.put(id, btn);
            targetRow.add(btn);
        }

        targetRow.revalidate();
        targetRow.repaint();
    }

    /** 选中目标玩家（高亮按钮，取消其他） */
    private void selectTarget(String playerId, JButton selectedBtn) {
        selectedTargetId = playerId;
        targetButtons.values().forEach(b -> b.setBackground(BTN_TARGET_IDLE));
        selectedBtn.setBackground(BTN_TARGET_SEL);
        updateConfirmState();
    }

    /** 重置目标选择状态 */
    private void resetTargetSelection() {
        selectedTargetId = null;
        targetButtons.values().forEach(b -> b.setBackground(BTN_TARGET_IDLE));
        updateConfirmState();
    }

    /** 更新确认按钮的启用状态（需要目标的卡牌在没有选择目标时禁用） */
    private void updateConfirmState() {
        if (confirmButton == null || !confirmButton.isVisible()) return;

        boolean needsTarget = targetRow.isVisible()
                && isStrictTargetRequired(selectedCardName);

        confirmButton.setEnabled(!needsTarget || selectedTargetId != null);
    }

    /** 检查卡牌是否需要严格指定目标（必须先选择才能确认） */
    private boolean isStrictTargetRequired(String cardName) {
        return cardName != null && NEEDS_TARGET_NAMES.contains(cardName);
    }

    // ==================== 操作处理 ====================

    /** 确认按钮点击处理 */
    private void onConfirm() {
        if (selectedCardId == null) return;

        // 验证：严格目标卡牌必须选择目标
        if (isStrictTargetRequired(selectedCardName) && selectedTargetId == null) {
            cardNameLabel.setText("请先选择目标");
            cardNameLabel.setForeground(new Color(255, 100, 100));
            javax.swing.Timer reset = new javax.swing.Timer(1200, e -> {
                cardNameLabel.setText(selectedCardName);
                cardNameLabel.setForeground(TEXT_PRIMARY);
            });
            reset.setRepeats(false);
            reset.start();
            return;
        }

        String action = resolveAction();
        if (triCallback != null) {
            triCallback.accept(selectedCardId, action, selectedTargetId);
        }
        dismiss();
    }

    /** 存入银行按钮点击处理 - 以PLAY_MONEY操作发送 */
    private void onBank() {
        if (selectedCardId == null) return;
        if (triCallback != null) {
            triCallback.accept(selectedCardId, "PLAY_MONEY", null);
        }
        dismiss();
    }

    /**
     * 根据卡牌类型解析操作字符串
     * @return 操作类型（PLAY_MONEY/PLAY_PROPERTY/PLAY_RENT/PLAY_ACTION）
     */
    private String resolveAction() {
        switch (selectedCardType) {
            case "MONEY":    return "PLAY_MONEY";
            case "PROPERTY": return "PLAY_PROPERTY";
            case "RENT":     return "PLAY_RENT";
            case "ACTION":   return "PLAY_ACTION";
            default:         return "PLAY_MONEY";
        }
    }

    /** 背景绘制为空，由内层面板负责绘制 */
    @Override
    protected void paintComponent(Graphics g) {
        // 透明背景，绘制由内层面板负责
    }
}
