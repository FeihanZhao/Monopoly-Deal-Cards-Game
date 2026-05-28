package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Premium Animated Turn Timer Bar for Monopoly Deal Game
 * This component acts as a visual countdown progress bar for player turn time.
 * All timing logic is controlled externally by GamePanel via timer ticks.
 *
 * Visual Features:
 * 1. 3D gradient progress bar with neon outer glow
 * 2. Color transition: Green (normal time) → Red (urgent, ≤10s left)
 * 3. Pulsing animation & enlarged font when time is running out
 * 4. Dim grey style when current player is inactive (not their turn)
 * 5. High-quality anti-aliasing & smooth rendering
 *
 * Usage:
 * 1. Initialize: timerBarPanel = new TimerBarPanel(30);
 * 2. Start countdown on turn begin: timerBarPanel.start(30);
 * 3. Decrease time every second: timerBarPanel.tick();
 * 4. Set inactive when turn ends: timerBarPanel.setInactive();
 */
public class TimerBarPanel extends JPanel {

    // ---------------------- Dimension Constants ----------------------
    // Core size of progress bar
    private static final int BAR_WIDTH      = 160;
    private static final int BAR_HEIGHT     = 22;
    // Corner arc radius for rounded rectangle
    private static final int ROUND_ARC     = 12;
    // Total panel size (bar + text label area)
    private static final int PANEL_WIDTH   = BAR_WIDTH + 60;
    private static final int PANEL_HEIGHT  = 36;

    // ---------------------- Color Theme Constants ----------------------
    // Background & border of empty track
    private static final Color TRACK_BACKGROUND    = new Color(30, 32, 42);
    private static final Color TRACK_BORDER        = new Color(110, 115, 135);
    // Outer soft glow border
    private static final Color GLOW_BORDER_COLOR   = new Color(160, 165, 185, 100);

    // Normal state (plenty time left) gradient color
    private static final Color NORMAL_GRAD_TOP     = new Color(80, 220, 110);
    private static final Color NORMAL_GRAD_BOTTOM = new Color(40, 160, 70);
    private static final Color NORMAL_GLOW        = new Color(80, 220, 110, 80);

    // Urgent state (≤ 10 seconds left) gradient color
    private static final Color URGENT_GRAD_TOP     = new Color(255, 80, 90);
    private static final Color URGENT_GRAD_BOTTOM = new Color(200, 40, 60);
    private static final Color URGENT_GLOW        = new Color(255, 80, 90, 100);

    // Inactive state (not current player's turn)
    private static final Color INACTIVE_BAR_COLOR  = new Color(90, 95, 110);
    private static final Color INACTIVE_TEXT_COLOR = new Color(140, 145, 160);

    // ---------------------- Runtime State Variables ----------------------
    // Maximum seconds for one turn
    private int maxSeconds;
    // Remaining countdown seconds
    private int secondsRemaining;
    // Flag: true = turn active / counting down, false = inactive
    private boolean isActive;
    // Timestamp for pulse animation calculation
    private long pulseTimeStamp;

    /**
     * Constructor
     * @param maxSeconds Total time limit for a single player turn
     */
    public TimerBarPanel(int maxSeconds) {
        this.maxSeconds = maxSeconds;
        this.secondsRemaining = maxSeconds;
        this.isActive = false;
        this.pulseTimeStamp = System.currentTimeMillis();

        // Set panel size and transparency
        setOpaque(false);
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
    }

    // ====================== Public API Methods (Called by GamePanel) ======================
    /**
     * Start a new countdown timer for current player's turn
     * @param seconds Total time for this turn
     */
    public void start(int seconds) {
        this.maxSeconds = seconds;
        this.secondsRemaining = seconds;
        this.isActive = true;
        this.pulseTimeStamp = System.currentTimeMillis();
        repaint();
    }

    /**
     * Count down 1 second, called every tick by external timer
     */
    public void tick() {
        // Do nothing if panel is inactive
        if (!isActive) {
            return;
        }
        if (secondsRemaining > 0) {
            secondsRemaining--;
        }
        this.pulseTimeStamp = System.currentTimeMillis();
        repaint();
    }

    /**
     * Switch panel to inactive state (not current player's turn)
     * Show grey style and "--" label
     */
    public void setInactive() {
        this.isActive = false;
        this.secondsRemaining = maxSeconds;
        repaint();
    }

    /**
     * Get current remaining seconds
     * @return Remaining countdown time
     */
    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    /**
     * Check if timer is currently active (counting down)
     * @return true = active, false = inactive
     */
    public boolean isActive() {
        return isActive;
    }

    // ====================== Custom Painting Logic ======================
    /**
     * Override paint method to draw gradient bar, glow effect, text and animation
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g.create();

        // Enable highest rendering quality
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setStroke(new BasicStroke(1.8f));

        // Calculate vertical position to center the progress bar
        int barCenterY = getHeight() / 2;
        int barTopY = barCenterY - BAR_HEIGHT / 2;

        // ---------------------- Step 1: Draw empty track background ----------------------
        RoundRectangle2D.Float trackShape = new RoundRectangle2D.Float(0, barTopY, BAR_WIDTH, BAR_HEIGHT, ROUND_ARC, ROUND_ARC);
        // Fill track background
        g2d.setColor(TRACK_BACKGROUND);
        g2d.fill(trackShape);
        // Draw outer soft glow border
        g2d.setColor(GLOW_BORDER_COLOR);
        g2d.draw(new RoundRectangle2D.Float(1, barTopY + 1, BAR_WIDTH - 2, BAR_HEIGHT - 2, ROUND_ARC - 2, ROUND_ARC - 2));
        // Draw main solid border
        g2d.setColor(TRACK_BORDER);
        g2d.draw(trackShape);

        // ---------------------- Step 2: Draw progress fill area ----------------------
        if (isActive && secondsRemaining > 0) {
            // Calculate fill width by time ratio
            float timeRatio = (float) secondsRemaining / maxSeconds;
            int fillWidth = Math.round(timeRatio * BAR_WIDTH);

            if (fillWidth > 0) {
                // Switch color scheme based on remaining time
                boolean isUrgent = secondsRemaining <= 10;
                Color gradTop = isUrgent ? URGENT_GRAD_TOP : NORMAL_GRAD_TOP;
                Color gradBottom = isUrgent ? URGENT_GRAD_BOTTOM : NORMAL_GRAD_BOTTOM;
                Color glowColor = isUrgent ? URGENT_GLOW : NORMAL_GLOW;

                // Create vertical gradient paint
                GradientPaint gradient = new GradientPaint(0, barTopY, gradTop, 0, barTopY + BAR_HEIGHT, gradBottom);
                g2d.setPaint(gradient);

                // Clip area to rounded track shape
                g2d.setClip(trackShape);
                g2d.fillRect(0, barTopY, fillWidth, BAR_HEIGHT);
                g2d.setClip(null);

                // Draw extra glow effect for urgent state
                if (isUrgent) {
                    long currentTime = System.currentTimeMillis();
                    // Calculate pulse alpha for flickering effect
                    float pulseAlpha = (float) (Math.sin((currentTime - pulseTimeStamp) / 180.0) * 0.3 + 0.5);
                    Color dynamicGlow = new Color(
                            glowColor.getRed(),
                            glowColor.getGreen(),
                            glowColor.getBlue(),
                            (int) (pulseAlpha * 255)
                    );
                    g2d.setColor(dynamicGlow);
                    g2d.draw(new RoundRectangle2D.Float(-2, barTopY - 2, BAR_WIDTH + 4, BAR_HEIGHT + 4, ROUND_ARC + 4, ROUND_ARC + 4));
                }
            }
        } else {
            // Draw dim grey bar when inactive
            g2d.setColor(INACTIVE_BAR_COLOR);
            g2d.setClip(trackShape);
            g2d.fillRect(0, barTopY, BAR_WIDTH, BAR_HEIGHT);
            g2d.setClip(null);
        }

        // ---------------------- Step 3: Draw countdown text label ----------------------
        String displayText;
        Color textColor;
        Font textFont;

        if (!isActive) {
            // Inactive status text
            displayText = "- -";
            textColor = INACTIVE_TEXT_COLOR;
            textFont = new Font("Segoe UI", Font.BOLD, 14);
        } else if (secondsRemaining <= 10) {
            // Urgent: enlarged font + red text
            displayText = secondsRemaining + "s";
            textColor = URGENT_GRAD_TOP;
            textFont = new Font("Segoe UI", Font.BOLD, 17);
        } else {
            // Normal status text
            displayText = secondsRemaining + "s";
            textColor = Color.WHITE;
            textFont = new Font("Segoe UI", Font.BOLD, 14);
        }

        // Draw text beside progress bar
        g2d.setFont(textFont);
        g2d.setColor(textColor);
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int textPosX = BAR_WIDTH + 8;
        int textPosY = (getHeight() + fontMetrics.getAscent() - fontMetrics.getDescent()) / 2;
        g2d.drawString(displayText, textPosX, textPosY);

        g2d.dispose();
    }
}
