package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Countdown progress bar panel — visualizes remaining turn time as a progress bar.
 *
 * Replaces the plain numeric timerLabel that was originally in GamePanel, providing a more
 * intuitive time display. Driven by GamePanel updates; this component contains no timing logic itself.
 *
 * Visual effects:
 * - Horizontal progress bar fills left to right, length proportional to remaining time
 * - Color transition: green (>10s) → red (≤10s crisis state)
 * - Seconds text grows larger and bolder during urgent state (pulse effect)
 * - Inactive state shows a gray empty progress bar and "--" text
 *
 * Usage (in GamePanel):
 * - Create: timerBarPanel = new TimerBarPanel(30);
 * - Every second: timerBarPanel.tick();
 * - Turn start: timerBarPanel.start(30);
 * - Not your turn: timerBarPanel.setInactive();
 */
public class TimerBarPanel extends JPanel {

    // ==================== Size constants ====================

    /** Progress bar width (pixels) */
    private static final int BAR_W      = 140;
    /** Progress bar height (pixels) */
    private static final int BAR_H      = 18;
    /** Progress bar corner radius */
    private static final int ARC        = 9;
    /** Full panel width (bar + number label spacing) */
    private static final int PANEL_W    = BAR_W + 48;
    /** Full panel height */
    private static final int PANEL_H    = 28;

    // ==================== Color constants ====================

    /** Progress bar track (background) color */
    private static final Color TRACK_COLOR   = new Color(60, 60, 60);
    /** Progress bar track border color */
    private static final Color TRACK_BORDER  = new Color(90, 90, 90);
    /** Safe time (>10s) fill color (green) */
    private static final Color COLOR_SAFE    = new Color(34, 139, 34);
    /** Safe time fill gradient color (lighter green) */
    private static final Color COLOR_SAFE2   = new Color(76, 175, 80);
    /** Urgent time (≤10s) fill color (red) */
    private static final Color COLOR_URGENT  = new Color(198, 40, 40);
    /** Urgent time fill gradient color (lighter red) */
    private static final Color COLOR_URGENT2 = new Color(239, 83, 80);
    /** Inactive state fill color (gray) */
    private static final Color COLOR_INACTIVE = new Color(80, 80, 80);
    /** Normal text color */
    private static final Color TEXT_NORMAL   = Color.WHITE;
    /** Urgent text color (red) */
    private static final Color TEXT_URGENT   = new Color(255, 100, 100);
    /** Inactive text color (dimmed) */
    private static final Color TEXT_INACTIVE = new Color(130, 130, 130);

    // ==================== State fields ====================

    /** Maximum number of seconds (turn duration, typically 30 seconds) */
    private int maxSeconds;
    /** Seconds remaining */
    private int secondsRemaining;
    /** Whether active (local player's turn) */
    private boolean active;

    // ==================== Constructor ====================

    /**
     * Constructor.
     * @param maxSeconds turn duration in seconds, used to compute the progress bar fill ratio
     */
    public TimerBarPanel(int maxSeconds) {
        this.maxSeconds       = maxSeconds;
        this.secondsRemaining = maxSeconds;
        this.active           = false;  // Initially inactive

        setOpaque(false);
        setPreferredSize(new Dimension(PANEL_W, PANEL_H));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
    }

    // ==================== Public API (called by GamePanel) ====================

    /**
     * Start a new countdown.
     * Called when it becomes the local player's turn.
     *
     * @param seconds total seconds for this turn
     */
    public void start(int seconds) {
        this.maxSeconds       = seconds;
        this.secondsRemaining = seconds;
        this.active           = true;
        repaint();
    }

    /**
     * Decrement by one second.
     * Called once per second by GamePanel's countdown Timer.
     */
    public void tick() {
        if (!active) return;
        if (secondsRemaining > 0) secondsRemaining--;
        repaint();
    }

    /**
     * Sync remaining seconds from server state (prevents client/server drift).
     * Called on each GAME_STATE_UPDATE during the local player's turn.
     */
    public void syncTo(int seconds) {
        if (!active) return;
        this.secondsRemaining = seconds;
        repaint();
    }

    /**
     * Switch to inactive state (gray).
     * Called when it is not the local player's turn or the game hasn't started.
     */
    public void setInactive() {
        this.active           = false;
        this.secondsRemaining = maxSeconds;  // Reset to full
        repaint();
    }

    /** Get current remaining seconds */
    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    /** Whether in active state */
    public boolean isActive() {
        return active;
    }

    // ==================== Custom painting ====================

    /**
     * Paint the countdown progress bar.
     * Structure: track background + filled progress bar + seconds text label (right of bar)
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int barTop = (getHeight() - BAR_H) / 2;  // Vertical center

        // ===== 1. Draw track (progress bar background) =====
        g2.setColor(TRACK_COLOR);
        g2.fill(new RoundRectangle2D.Float(0, barTop, BAR_W, BAR_H, ARC, ARC));
        g2.setColor(TRACK_BORDER);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(0.5f, barTop + 0.5f,
                BAR_W - 1f, BAR_H - 1f, ARC, ARC));

        // ===== 2. Draw filled progress bar =====
        if (active) {
            // Compute fill ratio (remaining time / total time)
            float fraction = maxSeconds > 0
                    ? (float) secondsRemaining / maxSeconds
                    : 0f;
            int fillW = Math.round(fraction * BAR_W);

            if (fillW > 0) {
                boolean urgent = secondsRemaining <= 10;  // Enter urgent state within 10s
                Color c1 = urgent ? COLOR_URGENT  : COLOR_SAFE;   // Bottom color
                Color c2 = urgent ? COLOR_URGENT2 : COLOR_SAFE2;  // Top color (brighter)

                // Top-to-bottom gradient fill
                GradientPaint gp = new GradientPaint(
                        0, barTop,          c2,
                        0, barTop + BAR_H,  c1);
                g2.setPaint(gp);

                // Clip fill area to rounded track shape
                Shape track = new RoundRectangle2D.Float(0, barTop, BAR_W, BAR_H, ARC, ARC);
                g2.setClip(track);
                g2.fillRect(0, barTop, fillW, BAR_H);
                g2.setClip(null);
            }
        } else {
            // Inactive state: full gray bar
            g2.setColor(COLOR_INACTIVE);
            Shape track = new RoundRectangle2D.Float(0, barTop, BAR_W, BAR_H, ARC, ARC);
            g2.setClip(track);
            g2.fillRect(0, barTop, BAR_W, BAR_H);
            g2.setClip(null);
        }

        // ===== 3. Draw seconds text label =====
        String label;
        Color  textColor;
        Font   textFont;

        if (!active) {
            // Inactive: show "--"
            label     = "- -";
            textColor = TEXT_INACTIVE;
            textFont  = new Font("SansSerif", Font.BOLD, 13);
        } else if (secondsRemaining <= 10) {
            // Urgent: large red text (pulse effect)
            label     = secondsRemaining + "s";
            textColor = TEXT_URGENT;
            textFont  = new Font("SansSerif", Font.BOLD, 15);
        } else {
            // Normal: white regular text
            label     = secondsRemaining + "s";
            textColor = TEXT_NORMAL;
            textFont  = new Font("SansSerif", Font.BOLD, 13);
        }

        g2.setFont(textFont);
        g2.setColor(textColor);
        FontMetrics fm = g2.getFontMetrics();
        int tx = BAR_W + 6;  // Text 6px to the right of the bar
        int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(label, tx, ty);

        g2.dispose();
    }
}
