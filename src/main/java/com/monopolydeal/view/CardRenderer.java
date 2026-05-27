package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * 卡牌渲染组件 - 自定义绘制的游戏卡牌UI组件
 *
 * 在玩家手牌区以可视化卡片形式展示每张卡牌。支持：
 * - 按颜色/类型自动选择背景配色（Palette系统）
 * - 鼠标悬停浮动效果（卡片上升12px）
 * - 选中状态发光效果（金色发光边框）
 * - 卡面包含：类型标签、图标、名称、面值（仅金钱卡）
 * - 点击触发playListener回调
 *
 * 卡牌尺寸：90×130px，圆角12px
 *
 * 配色方案：
 * - 每种地产颜色有对应的背景色+渐变+边框色
 * - 金钱卡：绿色系
 * - 行动卡：紫色系
 * - 租金卡：红棕色系
 * - 万能卡：彩虹渐变
 */
public class CardRenderer extends JPanel {

    /** 卡牌宽度（像素） */
    public static final int CARD_W = 90;
    /** 卡牌高度（像素） */
    public static final int CARD_H = 130;
    /** 卡牌圆角半径 */
    public static final int CORNER_RADIUS = 12;

    /** 鼠标悬停时卡牌上浮距离 */
    private static final int LIFT_HOVER    = 12;
    /** 选中时卡牌上浮距离 */
    private static final int LIFT_SELECTED = 22;

    /**
     * 卡牌配色方案内部类
     * 包含背景色、渐变色、边框色和文字色
     */
    private static final class CardPalette {
        final Color bg;        // 主背景色
        final Color gradient;  // 渐变终点色（用于从上到下的渐变）
        final Color border;    // 边框颜色
        final Color text;      // 文字颜色

        CardPalette(Color bg, Color gradient, Color border, Color text) {
            this.bg       = bg;
            this.gradient = gradient;
            this.border   = border;
            this.text     = text;
        }

        CardPalette(Color bg, Color border, Color text) {
            this(bg, null, border, text);
        }
    }

    /** 卡牌配色方案映射表 key=颜色名/类型名, value=配色方案 */
    private static final Map<String, CardPalette> PALETTES = new HashMap<>();

    static {
        // ===== 纯地产颜色配色（颜色值统一来自 AppTheme） =====
        for (Map.Entry<String, Color> entry : AppTheme.PROPERTY_COLORS.entrySet()) {
            String colorName = entry.getKey();
            Color bg = entry.getValue();
            Color gradient = AppTheme.PROPERTY_GRADIENT_COLORS.getOrDefault(
                    colorName, bg.darker().darker());
            Color text = isLightColor(colorName) ? new Color(0x1A1A1A) : Color.WHITE;
            PALETTES.put(colorName, new CardPalette(bg, gradient, text));
        }

        // ===== 双色租金卡配色（左色→右色渐变） =====
        PALETTES.put("BROWN_LIGHT_BLUE",
                new CardPalette(new Color(0x8B5E3C), new Color(0x87CEEB), new Color(0xFFFFFF)));
        PALETTES.put("PINK_ORANGE",
                new CardPalette(new Color(0xFF69B4), new Color(0xFF8C00), new Color(0xFFFFFF)));
        PALETTES.put("RED_YELLOW",
                new CardPalette(new Color(0xDC143C), new Color(0xFFD700), new Color(0x1A1A1A)));
        PALETTES.put("GREEN_BLUE",
                new CardPalette(new Color(0x228B22), new Color(0x00008B), new Color(0xFFFFFF)));
        PALETTES.put("BLACK_LIGHT_GREEN",
                new CardPalette(new Color(0x2B2B2B), new Color(0x90EE90), new Color(0xFFFFFF)));

        // ===== 按卡牌类型的默认配色 =====
        PALETTES.put("MONEY",
                new CardPalette(new Color(0x2E7D32), new Color(0x1B5E20), new Color(0xFFFFFF)));
        PALETTES.put("ACTION",
                new CardPalette(new Color(0x6A1B9A), new Color(0x4A0072), new Color(0xFFFFFF)));
        PALETTES.put("RENT",
                new CardPalette(new Color(0xBF360C), new Color(0x870000), new Color(0xFFFFFF)));

        // ===== 万能卡配色（彩虹渐变，paintComponent中特殊处理） =====
        PALETTES.put("WILD",
                new CardPalette(new Color(0xFF6B6B), new Color(0x4D96FF), new Color(0xFFFFFF)));

        // ===== 无颜色（兜底灰色） =====
        PALETTES.put("NONE",
                new CardPalette(Color.GRAY, Color.DARK_GRAY, Color.WHITE));
    }

    /** 判断是否为浅色背景（需要深色文字以保证可读性） */
    private static boolean isLightColor(String colorName) {
        return "LIGHT_BLUE".equals(colorName)
                || "YELLOW".equals(colorName)
                || "LIGHT_GREEN".equals(colorName);
    }

    /** 卡牌类型图标映射表 */
    private static final Map<String, String> TYPE_ICONS = new HashMap<>();

    static {
        TYPE_ICONS.put("MONEY",    "");   // 💵 美元图标
        TYPE_ICONS.put("PROPERTY", "");  // 🏠 房屋图标
        TYPE_ICONS.put("ACTION",   "");   // ⚡ 闪电图标
        TYPE_ICONS.put("RENT",     "");   // 💸 飞钱图标
    }

    /** 卡牌唯一标识符 */
    private final String cardId;
    /** 卡牌名称（显示在卡面上） */
    private final String cardName;
    /** 卡牌类型（MONEY/PROPERTY/ACTION/RENT） */
    private final String cardType;
    /** 颜色键（用于查找配色方案） */
    private final String colorKey;
    /** 金钱面值（仅金钱卡，其他=0） */
    private final int    value;
    /** 卡牌视图模型（用于外部查询 cardId、cardName 等） */
    private CardViewModel viewModel;

    /** 是否处于选中状态 */
    private boolean selected  = false;
    /** 鼠标是否悬停 */
    private boolean hovered   = false;

    /** 当前上浮像素数（动画插值结果） */
    private float   currentLift = 0f;
    /** 动画定时器（16ms/帧，约60fps） */
    private javax.swing.Timer animTimer;

    /**
     * 卡牌点击回调接口
     */
    @FunctionalInterface
    public interface PlayListener {
        void onPlay(String cardId);
    }

    /** 卡牌点击回调 */
    private PlayListener playListener;

    /**
     * 完整构造函数
     * @param cardId 卡牌ID
     * @param cardName 卡牌名称
     * @param cardType 卡牌类型
     * @param colorKey 颜色键
     * @param value 金钱面值
     */
    public CardRenderer(String cardId, String cardName,
                        String cardType, String colorKey, int value) {
        this.cardId   = cardId;
        this.cardName = cardName;
        this.cardType = cardType;
        this.colorKey = colorKey;
        this.value    = value;
        initComponent();
    }

    /** 从 CardViewModel 构造（常用方式，由 GamePanel 调用） */
    public CardRenderer(CardViewModel vm) {
        this(vm.getCardId(), vm.getCardName(), vm.getCardType(),
             vm.getColor(), vm.getValue());
        this.viewModel = vm;
    }

    /** 初始化组件 - 设置尺寸、鼠标事件和动画系统 */
    private void initComponent() {
        // 固定尺寸（高度包含浮动空间）
        setPreferredSize(new Dimension(CARD_W, CARD_H + LIFT_SELECTED + 4));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
        setOpaque(false);

        // 鼠标事件：悬停检测和点击回调
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (isEnabled()) { hovered = true;  animateLift(); }
            }
            @Override public void mouseExited(MouseEvent e) {
                hovered = false; animateLift();
            }
            @Override public void mouseClicked(MouseEvent e) {
                if (isEnabled() && playListener != null) {
                    playListener.onPlay(cardId);
                }
            }
        });

        // 浮动动画定时器（16ms/帧 ≈ 60fps）
        animTimer = new javax.swing.Timer(16, e -> {
            float target = getTargetLift();
            float delta  = target - currentLift;
            if (Math.abs(delta) < 0.5f) {
                currentLift = target;
                ((javax.swing.Timer) e.getSource()).stop();
            } else {
                currentLift += delta * 0.25f;  // 缓动插值
            }
            repaint();
        });

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ==================== 公共方法 ====================

    /** 设置卡牌点击回调 */
    public void setPlayListener(PlayListener listener) {
        this.playListener = listener;
    }

    /** 设置选中状态并触发动画 */
    public void setSelected(boolean selected) {
        this.selected = selected;
        animateLift();
        repaint();
    }

    /** 是否选中 */
    public boolean isSelected() {
        return selected;
    }

    /** 获取卡牌ID */
    public String getCardId() {
        return cardId;
    }

    /**
     * 获取卡牌视图模型（用于查询 cardId、cardName 等信息）
     * 仅通过 CardViewModel 构造函数创建的实例有值；静态工厂创建的返回 null。
     */
    public CardViewModel getViewModel() {
        return viewModel;
    }

    /** 设置启用/禁用状态（禁用时卡片半透明、鼠标变回默认） */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(enabled
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        repaint();
    }

    // ==================== 动画系统 ====================

    /** 获取目标浮动高度（根据选中/悬停/默认状态） */
    private float getTargetLift() {
        if (selected) return LIFT_SELECTED;
        if (hovered && isEnabled()) return LIFT_HOVER;
        return 0f;
    }

    /** 启动浮动动画 */
    private void animateLift() {
        if (!animTimer.isRunning()) animTimer.start();
    }

    // ==================== 自定义绘制 ====================

    /**
     * 绘制卡牌 - 分为背影、发光、类型标签、图标、名称、面值、边框七个层次
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        // 开启抗锯齿和高质量渲染
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int liftPx = Math.round(currentLift);
        int cardTop = (getHeight() - CARD_H) - liftPx;  // 从底部上浮

        Shape cardShape = new RoundRectangle2D.Float(0, cardTop, CARD_W, CARD_H,
                CORNER_RADIUS, CORNER_RADIUS);
        g2.setClip(cardShape);  // 裁剪到卡牌圆角区域

        // 逐层绘制
        paintDropShadow(g2, cardTop);      // 1. 阴影
        paintCardBackground(g2, cardTop);   // 2. 背景
        if (selected) paintSelectionGlow(g2, cardTop);  // 3. 选中发光

        // 禁用状态：覆盖半透明黑色遮罩
        if (!isEnabled()) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fill(cardShape);
        }

        g2.setClip(null);  // 取消裁剪
        paintTypeBadge(g2, cardTop);   // 4. 类型标签
        paintIcon(g2, cardTop);        // 5. 类型图标
        paintName(g2, cardTop);        // 6. 卡牌名称
        if (value > 0) paintValueBadge(g2, cardTop);  // 7. 面值标签
        paintBorder(g2, cardTop);      // 8. 边框

        g2.dispose();
    }

    /** 绘制投影阴影（多层半透明黑色圆角矩形） */
    private void paintDropShadow(Graphics2D g2, int cardTop) {
        int shadowOffset = selected ? 8 : (hovered ? 6 : 3);
        int shadowAlpha  = selected ? 120 : (hovered ? 100 : 70);

        for (int i = shadowOffset; i > 0; i--) {
            float alpha = shadowAlpha * ((float) (shadowOffset - i + 1) / shadowOffset);
            g2.setColor(new Color(0, 0, 0, (int) alpha));
            g2.fill(new RoundRectangle2D.Float(i, cardTop + i, CARD_W, CARD_H,
                    CORNER_RADIUS, CORNER_RADIUS));
        }
    }

    /** 绘制卡牌背景（纯色或渐变） */
    private void paintCardBackground(Graphics2D g2, int cardTop) {
        Shape shape = new RoundRectangle2D.Float(0, cardTop, CARD_W, CARD_H,
                CORNER_RADIUS, CORNER_RADIUS);

        // 万能卡使用彩虹渐变色
        if ("WILD".equals(colorKey)) {
            float[] fractions = {0f, 0.33f, 0.66f, 1f};
            Color[] colors    = {
                    new Color(0xFF6B6B),  // 红
                    new Color(0xFFD93D),  // 黄
                    new Color(0x6BCB77),  // 绿
                    new Color(0x4D96FF)   // 蓝
            };
            g2.setPaint(new LinearGradientPaint(0, cardTop, CARD_W, cardTop + CARD_H,
                    fractions, colors));
        } else {
            CardPalette palette = resolvePalette();
            if (palette != null && palette.gradient != null) {
                // 从上到下的渐变色
                g2.setPaint(new GradientPaint(0, cardTop, palette.bg,
                        0, cardTop + CARD_H, palette.gradient));
            } else if (palette != null) {
                g2.setPaint(palette.bg);  // 纯色
            } else {
                g2.setPaint(Color.GRAY);  // 兜底灰色
            }
        }
        g2.fill(shape);
    }

    /** 绘制选中发光效果（金色径向渐变） */
    private void paintSelectionGlow(Graphics2D g2, int cardTop) {
        RadialGradientPaint glow = new RadialGradientPaint(
                new Point2D.Float(CARD_W / 2f, cardTop + CARD_H / 2f),
                CARD_W * 0.7f,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 215, 0, 60), new Color(255, 215, 0, 0)}
        );
        g2.setPaint(glow);
        g2.fill(new RoundRectangle2D.Float(0, cardTop, CARD_W, CARD_H,
                CORNER_RADIUS, CORNER_RADIUS));
    }

    /** 绘制左上角的类型标签（如"ACTION"） */
    private void paintTypeBadge(Graphics2D g2, int cardTop) {
        String label = cardType;
        Font font = new Font("SansSerif", Font.BOLD, 8);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(label);
        int bx = 5, by = cardTop + 5;
        int bw = tw + 8, bh = fm.getHeight() + 2;
        // 半透明黑色圆角背景
        g2.setColor(new Color(0, 0, 0, 100));
        g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 6, 6));
        g2.setColor(Color.WHITE);
        g2.drawString(label, bx + 4, by + fm.getAscent() + 1);
    }

    /** 绘制卡面中央的类型图标（Emoji） */
    private void paintIcon(Graphics2D g2, int cardTop) {
        String icon = TYPE_ICONS.getOrDefault(cardType, "");  // 默认扑克牌图标
        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 28);
        g2.setFont(emojiFont);
        FontMetrics fm = g2.getFontMetrics();
        int x = (CARD_W - fm.stringWidth(icon)) / 2;
        int y = cardTop + 30 + fm.getAscent();
        // 图标带阴影
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawString(icon, x + 1, y + 2);
        g2.setColor(Color.WHITE);
        g2.drawString(icon, x, y);
    }

    /** 绘制卡牌名称（支持自动换行） */
    private void paintName(Graphics2D g2, int cardTop) {
        CardPalette palette = resolvePalette();
        g2.setColor(palette != null ? palette.text : Color.WHITE);
        // 根据名称长度自动调整字号
        int fontSize = cardName.length() > 12 ? 9 : (cardName.length() > 8 ? 10 : 11);
        g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        String[] lines = wrapText(cardName, fm, CARD_W - 10);
        int lineH = fm.getHeight();
        int totalH = lines.length * lineH;
        int startY = cardTop + CARD_H - (value > 0 ? 32 : 16) - totalH;
        for (String line : lines) {
            int x = (CARD_W - fm.stringWidth(line)) / 2;
            // 文字带阴影
            g2.setColor(new Color(0, 0, 0, 100));
            g2.drawString(line, x + 1, startY + 1);
            g2.setColor(palette != null ? palette.text : Color.WHITE);
            g2.drawString(line, x, startY);
            startY += lineH;
        }
    }

    /** 绘制底部面值标签（仅金钱卡，如 "$5M"） */
    private void paintValueBadge(Graphics2D g2, int cardTop) {
        String label = "$" + value + "M";
        Font font = new Font("SansSerif", Font.BOLD, 12);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int bw = fm.stringWidth(label) + 14;
        int bh = fm.getHeight() + 4;
        int bx = (CARD_W - bw) / 2;
        int by = cardTop + CARD_H - bh - 6;
        // 半透明黑色圆角背景 + 金色文字
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 8, 8));
        g2.setColor(new Color(0xFFD700));
        g2.drawString(label, bx + 7, by + fm.getAscent() + 2);
    }

    /** 绘制卡牌边框（选中=金色发光，默认=深色边框） */
    private void paintBorder(Graphics2D g2, int cardTop) {
        RoundRectangle2D.Float border = new RoundRectangle2D.Float(
                0.5f, cardTop + 0.5f, CARD_W - 1f, CARD_H - 1f,
                CORNER_RADIUS, CORNER_RADIUS);

        if (selected) {
            // 多层金色发光边框
            for (int i = 3; i > 0; i--) {
                g2.setColor(new Color(255, 215, 0, 60 * i));
                g2.setStroke(new BasicStroke(i * 2 + 1f));
                g2.draw(border);
            }
            g2.setColor(new Color(0xFFD700));
            g2.setStroke(new BasicStroke(2.5f));
        } else {
            CardPalette palette = resolvePalette();
            g2.setColor(palette != null ? palette.border.darker() : Color.GRAY);
            g2.setStroke(new BasicStroke(1.5f));
        }
        g2.draw(border);
    }

    /**
     * 解析当前卡牌的配色方案
     * 优先级：colorKey → cardType → 兜底灰色
     */
    private CardPalette resolvePalette() {
        CardPalette p = PALETTES.get(colorKey);
        if (p == null) p = PALETTES.get(cardType);
        if (p == null) p = PALETTES.get("NONE");
        return p;
    }

    /**
     * 文本换行工具 - 将长名称拆分为最多两行
     * @param text 原始文本
     * @param fm 字体度量器
     * @param maxWidth 最大宽度（像素）
     * @return 拆分后的行数组（1-2行）
     */
    private static String[] wrapText(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return new String[]{text};
        String[] words = text.split(" ");
        StringBuilder line1 = new StringBuilder();
        StringBuilder line2 = new StringBuilder();
        boolean onLine2 = false;
        for (String word : words) {
            if (!onLine2) {
                String candidate = line1.length() == 0 ? word : line1 + " " + word;
                if (fm.stringWidth(candidate) <= maxWidth) {
                    line1 = new StringBuilder(candidate);
                } else {
                    onLine2 = true;
                    line2 = new StringBuilder(word);
                }
            } else {
                if (line2.length() > 0) line2.append(" ");
                line2.append(word);
            }
        }
        return line2.length() == 0 ?
                new String[]{line1.toString()} :
                new String[]{line1.toString(), line2.toString()};
    }

    // ==================== 静态工厂方法（便捷创建） ====================

    /** 创建金钱卡渲染器 */
    public static CardRenderer money(String cardId, int value) {
        return new CardRenderer(cardId, value + "M", "MONEY", "MONEY", value);
    }

    /** 创建地产卡渲染器 */
    public static CardRenderer property(String cardId, String name, String colorKey) {
        return new CardRenderer(cardId, name, "PROPERTY", colorKey, 0);
    }

    /** 创建行动卡渲染器 */
    public static CardRenderer action(String cardId, String name) {
        return new CardRenderer(cardId, name, "ACTION", "ACTION", 0);
    }

    /** 创建租金卡渲染器 */
    public static CardRenderer rent(String cardId, String name, String colorKey) {
        return new CardRenderer(cardId, name, "RENT", colorKey, 0);
    }
}
