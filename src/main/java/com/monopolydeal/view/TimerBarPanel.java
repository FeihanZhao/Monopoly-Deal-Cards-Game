package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * TimerBarPanel
 *
 * A visual countdown progress bar that replaces the plain timerLabel in
 * GamePanel. Driven entirely by GamePanel — no internal ticking logic.
 * GamePanel keeps its existing java.util.Timer and calls tick() every second.
 *
 * Visual behaviour:
 *  - Bar fills left-to-right proportional to remaining time
 *  - Colour transitions: green (>10 s) → red (≤10 s)
 *  - At ≤10 s the seconds number pulses (bold + slightly larger)
 *  - When inactive (not your turn) shows "- -" and a grey empty bar
 *
 * Usage in GamePanel:
 *
 *   // Construction — done once in createTopBar():
 *   timerBarPanel = new TimerBarPanel(30);
 *   rightPanel.add(timerBarPanel);          // replaces rightPanel.add(timerLabel)
 *
 *   // Each second inside the TimerTask:
 *   SwingUtilities.invokeLater(() -> timerBarPanel.tick());
 *
 *   // When the turn starts (your turn):
 *   timerBarPanel.start(30);
 *
 *   // When the turn ends or it is not your turn:
 *   timerBarPanel.setInactive();
 */
public class TimerBarPanel extends JPanel {

    // Dimensions
    private static final int BAR_W      = 140;
    private static final int BAR_H      = 18;
    private static final int ARC        = 9;
    private static final int PANEL_W    = BAR_W + 48;   // bar + number label gap
    private static final int PANEL_H    = 28;

    // Colour constants
    private static final Color TRACK_COLOR   = new Color(60, 60, 60);
    private static final Color TRACK_BORDER  = new Color(90, 90, 90);
    private static final Color COLOR_SAFE    = new Color(34, 139, 34);   // green
    private static final Color COLOR_SAFE2   = new Color(76, 175, 80);   // lighter green
    private static final Color COLOR_URGENT  = new Color(198, 40, 40);   // red
    private static final Color COLOR_URGENT2 = new Color(239, 83, 80);   // lighter red
    private static final Color COLOR_INACTIVE = new Color(80, 80, 80);
    private static final Color TEXT_NORMAL   = Color.WHITE;
    private static final Color TEXT_URGENT   = new Color(255, 100, 100);
    private static final Color TEXT_INACTIVE = new Color(130, 130, 130);

    // State
    private int maxSeconds;
    private int secondsRemaining;
    private boolean active;   // false = "not your turn" grey state


    // Constructor
    /**
     * @param maxSeconds the turn duration (typically 30); used to calculate
     *                   the fill proportion
     */
    public TimerBarPanel(int maxSeconds) {
        this.maxSeconds       = maxSeconds;
        this.secondsRemaining = maxSeconds;
        this.active           = false;

        setOpaque(false);
        setPreferredSize(new Dimension(PANEL_W, PANEL_H));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
    }

    // Public API  (called from GamePanel)
    /**
     * Start a new countdown. Call this when it becomes the local player's turn.
     * Must be called on the EDT (wrap in SwingUtilities.invokeLater if needed).
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
     * Decrement by one second. Call this from inside the TimerTask every tick.
     * Already expects to be called on the EDT via SwingUtilities.invokeLater.
     */
    public void tick() {
        if (!active) return;
        if (secondsRemaining > 0) secondsRemaining--;
        repaint();
    }

    /**
     * Switch to the inactive (grey) state — call when it is not the local
     * player's turn, or when the game hasn't started yet.
     */
    public void setInactive() {
        this.active           = false;
        this.secondsRemaining = maxSeconds;
        repaint();
    }

    /** Returns the current seconds remaining (useful for GamePanel logic). */
    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public boolean isActive() {
        return active;
    }


    // Painting
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int barTop  = (getHeight() - BAR_H) / 2;

        //1. Track (background of the bar)
        g2.setColor(TRACK_COLOR);
        g2.fill(new RoundRectangle2D.Float(0, barTop, BAR_W, BAR_H, ARC, ARC));
        g2.setColor(TRACK_BORDER);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Float(0.5f, barTop + 0.5f,
                BAR_W - 1f, BAR_H - 1f, ARC, ARC));

        //2. Fill
        if (active) {
            float fraction = maxSeconds > 0
                    ? (float) secondsRemaining / maxSeconds
                    : 0f;
            int fillW = Math.round(fraction * BAR_W);

            if (fillW > 0) {
                boolean urgent = secondsRemaining <= 10;
                Color c1 = urgent ? COLOR_URGENT  : COLOR_SAFE;
                Color c2 = urgent ? COLOR_URGENT2 : COLOR_SAFE2;

                // Gradient fill: lighter on top
                GradientPaint gp = new GradientPaint(
                        0, barTop,          c2,
                        0, barTop + BAR_H,  c1);
                g2.setPaint(gp);

                // Clip fill to rounded track shape
                Shape track = new RoundRectangle2D.Float(
                        0, barTop, BAR_W, BAR_H, ARC, ARC);
                g2.setClip(track);
                g2.fillRect(0, barTop, fillW, BAR_H);
                g2.setClip(null);
            }
        } else {
            // Inactive: faint grey fill at full width
            g2.setColor(COLOR_INACTIVE);
            Shape track = new RoundRectangle2D.Float(
                    0, barTop, BAR_W, BAR_H, ARC, ARC);
            g2.setClip(track);
            g2.fillRect(0, barTop, BAR_W, BAR_H);
            g2.setClip(null);
        }

        // 3. Seconds label (to the right of the bar)
        String label;
        Color  textColor;
        Font   textFont;

        if (!active) {
            label     = "- -";
            textColor = TEXT_INACTIVE;
            textFont  = new Font("SansSerif", Font.BOLD, 13);
        } else if (secondsRemaining <= 10) {
            label     = secondsRemaining + "s";
            textColor = TEXT_URGENT;
            textFont  = new Font("SansSerif", Font.BOLD, 15);   // slightly larger pulse
        } else {
            label     = secondsRemaining + "s";
            textColor = TEXT_NORMAL;
            textFont  = new Font("SansSerif", Font.BOLD, 13);
        }

        g2.setFont(textFont);
        g2.setColor(textColor);
        FontMetrics fm = g2.getFontMetrics();
        int tx = BAR_W + 6;
        int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(label, tx, ty);

        g2.dispose();
    }
}
