package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * 倒计时进度条面板 - 可视化显示回合剩余时间的进度条
 *
 * 替代了GamePanel中原本的纯数字timerLabel，提供更直观的时间显示。
 * 由GamePanel驱动更新，自身不包含计时逻辑。
 *
 * 视觉效果：
 * - 水平进度条从左到右填充，长度与剩余时间成正比
 * - 颜色过渡：绿色（>10秒）→ 红色（≤10秒危机状态）
 * - 紧急状态下秒数文本变大变粗（脉冲效果）
 * - 非活跃状态显示灰色空进度条和"--"文本
 *
 * 使用方式（在GamePanel中）：
 * - 创建：timerBarPanel = new TimerBarPanel(30);
 * - 每秒钟：timerBarPanel.tick();
 * - 回合开始：timerBarPanel.start(30);
 * - 非自己的回合：timerBarPanel.setInactive();
 */
public class TimerBarPanel extends JPanel {

    // ==================== 尺寸常量 ====================

    /** 进度条宽度（像素） */
    private static final int BAR_W      = 140;
    /** 进度条高度（像素） */
    private static final int BAR_H      = 18;
    /** 进度条圆角半径 */
    private static final int ARC        = 9;
    /** 整个面板宽度（进度条 + 数字标签间距） */
    private static final int PANEL_W    = BAR_W + 48;
    /** 整个面板高度 */
    private static final int PANEL_H    = 28;

    // ==================== 颜色常量 ====================

    /** 进度条轨道（背景）颜色 */
    private static final Color TRACK_COLOR   = new Color(60, 60, 60);
    /** 进度条轨道边框颜色 */
    private static final Color TRACK_BORDER  = new Color(90, 90, 90);
    /** 安全时间（>10秒）的填充主色（绿色） */
    private static final Color COLOR_SAFE    = new Color(34, 139, 34);
    /** 安全时间的填充渐变色（浅绿色） */
    private static final Color COLOR_SAFE2   = new Color(76, 175, 80);
    /** 紧急时间（≤10秒）的填充主色（红色） */
    private static final Color COLOR_URGENT  = new Color(198, 40, 40);
    /** 紧急时间的填充渐变色（浅红色） */
    private static final Color COLOR_URGENT2 = new Color(239, 83, 80);
    /** 非活跃状态的填充颜色（灰色） */
    private static final Color COLOR_INACTIVE = new Color(80, 80, 80);
    /** 正常文字颜色 */
    private static final Color TEXT_NORMAL   = Color.WHITE;
    /** 紧急文字颜色（红色） */
    private static final Color TEXT_URGENT   = new Color(255, 100, 100);
    /** 非活跃文字颜色（灰暗） */
    private static final Color TEXT_INACTIVE = new Color(130, 130, 130);

    // ==================== 状态字段 ====================

    /** 最大秒数（回合时长，通常为30秒） */
    private int maxSeconds;
    /** 剩余秒数 */
    private int secondsRemaining;
    /** 是否活跃（轮到本地玩家操作） */
    private boolean active;

    // ==================== 构造函数 ====================

    /**
     * 构造函数
     * @param maxSeconds 回合时长（秒），用于计算进度条填充比例
     */
    public TimerBarPanel(int maxSeconds) {
        this.maxSeconds       = maxSeconds;
        this.secondsRemaining = maxSeconds;
        this.active           = false;  // 初始非活跃

        setOpaque(false);
        setPreferredSize(new Dimension(PANEL_W, PANEL_H));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
    }

    // ==================== 公共API（由GamePanel调用） ====================

    /**
     * 启动新的倒计时
     * 当轮到本地玩家操作时调用
     *
     * @param seconds 本回合总秒数
     */
    public void start(int seconds) {
        this.maxSeconds       = seconds;
        this.secondsRemaining = seconds;
        this.active           = true;
        repaint();
    }

    /**
     * 每秒递减一次
     * 由GamePanel的倒计时Timer每秒调用一次
     */
    public void tick() {
        if (!active) return;
        if (secondsRemaining > 0) secondsRemaining--;
        repaint();
    }

    /**
     * 切换到非活跃状态（灰色）
     * 当不是本地玩家的回合或游戏未开始时调用
     */
    public void setInactive() {
        this.active           = false;
        this.secondsRemaining = maxSeconds;  // 重置到满值
        repaint();
    }

    /** 获取当前剩余秒数 */
    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    /** 是否处于活跃状态 */
    public boolean isActive() {
        return active;
    }

    // ==================== 自定义绘制 ====================

    /**
     * 绘制倒计时进度条
     * 结构：轨道背景 + 填充进度条 + 秒数文本标签（进度条右侧）
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int barTop = (getHeight() - BAR_H) / 2;  // 垂直居中

        // ===== 1. 绘制轨道（进度条背景） =====
        g2.setColor(TRACK_COLOR);
        g2.fill(new RoundRectangle2D.Float(0, barTop, BAR_W, BAR_H, ARC, ARC));
        g2.setColor(TRACK_BORDER);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(0.5f, barTop + 0.5f,
                BAR_W - 1f, BAR_H - 1f, ARC, ARC));

        // ===== 2. 绘制填充进度条 =====
        if (active) {
            // 计算填充比例（剩余时间 / 总时间）
            float fraction = maxSeconds > 0
                    ? (float) secondsRemaining / maxSeconds
                    : 0f;
            int fillW = Math.round(fraction * BAR_W);

            if (fillW > 0) {
                boolean urgent = secondsRemaining <= 10;  // 10秒内进入紧急状态
                Color c1 = urgent ? COLOR_URGENT  : COLOR_SAFE;   // 底部颜色
                Color c2 = urgent ? COLOR_URGENT2 : COLOR_SAFE2;  // 顶部颜色（更亮）

                // 从上到下的渐变填充
                GradientPaint gp = new GradientPaint(
                        0, barTop,          c2,
                        0, barTop + BAR_H,  c1);
                g2.setPaint(gp);

                // 裁剪填充区域到圆角轨道形状
                Shape track = new RoundRectangle2D.Float(0, barTop, BAR_W, BAR_H, ARC, ARC);
                g2.setClip(track);
                g2.fillRect(0, barTop, fillW, BAR_H);
                g2.setClip(null);
            }
        } else {
            // 非活跃状态：灰色满条
            g2.setColor(COLOR_INACTIVE);
            Shape track = new RoundRectangle2D.Float(0, barTop, BAR_W, BAR_H, ARC, ARC);
            g2.setClip(track);
            g2.fillRect(0, barTop, BAR_W, BAR_H);
            g2.setClip(null);
        }

        // ===== 3. 绘制秒数文本标签 =====
        String label;
        Color  textColor;
        Font   textFont;

        if (!active) {
            // 非活跃：显示"--"
            label     = "- -";
            textColor = TEXT_INACTIVE;
            textFont  = new Font("SansSerif", Font.BOLD, 13);
        } else if (secondsRemaining <= 10) {
            // 紧急：红色大字（脉冲效果）
            label     = secondsRemaining + "秒";
            textColor = TEXT_URGENT;
            textFont  = new Font("SansSerif", Font.BOLD, 15);
        } else {
            // 正常：白色普通字
            label     = secondsRemaining + "秒";
            textColor = TEXT_NORMAL;
            textFont  = new Font("SansSerif", Font.BOLD, 13);
        }

        g2.setFont(textFont);
        g2.setColor(textColor);
        FontMetrics fm = g2.getFontMetrics();
        int tx = BAR_W + 6;  // 文本在进度条右侧6px处
        int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(label, tx, ty);

        g2.dispose();
    }
}
