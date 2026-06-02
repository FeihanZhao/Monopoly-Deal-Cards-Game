package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonObject;
import com.monopolydeal.model.CardColor;

/**
 * Property set panel — displays a player's property groupings as compact colored badges.
 *
 * Each badge shows:
 * - Color dot (represents the property color)
 * - Count text (current/required, e.g. "2/3")
 * - Complete set: gold border glow
 * - Incomplete sets: normal colored background
 */
public class PropertySetPanel extends JPanel {

    /** Badge height */
    private static final int BADGE_H   = 24;
    /** Badge corner radius */
    private static final int BADGE_ARC = 8;
    /** Color dot diameter */
    private static final int DOT_SIZE  = 12;
    /** Horizontal gap between badges */
    private static final int H_GAP     = 6;

    /** Dark text color mapping for light backgrounds */
    private static final Map<String, Color> TEXT_COLORS = Map.of(
            "LIGHT_BLUE",  new Color(0x1A1A1A),
            "YELLOW",      new Color(0x1A1A1A),
            "LIGHT_GREEN", new Color(0x1A1A1A)
    );

    /** Current card counts per property color */
    private final Map<String, Integer> colorCounts = new HashMap<>();

    /** Whether each property color has a house */
    private final Map<String, Boolean> hasHouse = new HashMap<>();

    /** Whether each property color has a hotel */
    private final Map<String, Boolean> hasHotel = new HashMap<>();

    /** Constructor */
    public PropertySetPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(300, BADGE_H + 8));
        setMinimumSize(new Dimension(0, BADGE_H + 8));
    }

    /**
     * Update badge state from server JSON data.
     */
    public void updateFromJson(JsonObject playerData) {
        colorCounts.clear();
        hasHouse.clear();
        hasHotel.clear();

        if (playerData.has("propertyColorCounts")) {
            JsonObject counts = playerData.getAsJsonObject("propertyColorCounts");
            for (String key : counts.keySet()) {
                int count = counts.get(key).getAsInt();
                if (count > 0) {
                    colorCounts.put(key, count);
                }
            }
        }

        if (playerData.has("houseColors")) {
            JsonObject houses = playerData.getAsJsonObject("houseColors");
            for (String key : houses.keySet()) {
                if (houses.get(key).getAsBoolean()) hasHouse.put(key, true);
            }
        }

        if (playerData.has("hotelColors")) {
            JsonObject hotels = playerData.getAsJsonObject("hotelColors");
            for (String key : hotels.keySet()) {
                if (hotels.get(key).getAsBoolean()) hasHotel.put(key, true);
            }
        }

        repaint();
    }

    /**
     * Get the required card count for a complete set.
     */
    private int getSetSize(String colorKey) {
        try {
            return CardColor.valueOf(colorKey).getSetSize();
        } catch (IllegalArgumentException e) {
            return 3;
        }
    }

    /**
     * Custom painting — horizontally arranged colored badges.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (colorCounts.isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        int x   = 2;
        int top = (getHeight() - BADGE_H) / 2;

        for (Map.Entry<String, Integer> entry : colorCounts.entrySet()) {
            String colorKey = entry.getKey();
            int    count    = entry.getValue();
            int    required = getSetSize(colorKey);
            boolean complete = count >= required;
            boolean house    = hasHouse.getOrDefault(colorKey, false);
            boolean hotel    = hasHotel.getOrDefault(colorKey, false);

            // Build badge text
            String countText = count + "/" + required;
            String extraText = hotel ? " H" : house ? " h" : "";
            String fullText  = countText + extraText;

            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int textW  = fm.stringWidth(fullText);
            int badgeW = DOT_SIZE + 5 + textW + 12;

            Color bg = AppTheme.PROPERTY_COLORS.getOrDefault(colorKey, Color.GRAY);

            // Complete set: gold glow outer border
            if (complete) {
                // Outer glow
                g2.setColor(new Color(255, 215, 0, 30));
                g2.setStroke(new BasicStroke(4f));
                g2.draw(new RoundRectangle2D.Float(
                        x - 1, top - 1, badgeW + 2, BADGE_H + 2,
                        BADGE_ARC + 2, BADGE_ARC + 2));
                // Inner gold border
                g2.setColor(new Color(255, 215, 0, 80));
                g2.setStroke(new BasicStroke(2f));
                g2.draw(new RoundRectangle2D.Float(
                        x, top, badgeW, BADGE_H,
                        BADGE_ARC, BADGE_ARC));
            }

            // Badge background
            g2.setPaint(bg);
            g2.fill(new RoundRectangle2D.Float(x, top, badgeW, BADGE_H, BADGE_ARC, BADGE_ARC));

            // Subtle inner shine at top of badge
            g2.setColor(new Color(255, 255, 255, 30));
            g2.fill(new RoundRectangle2D.Float(
                    x + 2, top + 1, badgeW - 4, BADGE_H / 2 - 1,
                    BADGE_ARC, BADGE_ARC));

            // Badge border
            if (!complete) {
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(bg.darker());
                g2.draw(new RoundRectangle2D.Float(
                        x + 0.6f, top + 0.6f, badgeW - 1.2f, BADGE_H - 1.2f,
                        BADGE_ARC, BADGE_ARC));
            }

            // Color dot
            int dotX = x + 6;
            int dotY = top + (BADGE_H - DOT_SIZE) / 2;
            g2.setColor(bg.brighter().brighter());
            g2.fillOval(dotX, dotY, DOT_SIZE, DOT_SIZE);
            g2.setColor(new Color(0, 0, 0, 40));
            g2.setStroke(new BasicStroke(1f));
            g2.drawOval(dotX, dotY, DOT_SIZE, DOT_SIZE);

            // Count text
            Color textColor = TEXT_COLORS.getOrDefault(colorKey, Color.WHITE);
            g2.setColor(textColor);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            int tx = dotX + DOT_SIZE + 5;
            int ty = top + (BADGE_H + fm.getAscent() - fm.getDescent()) / 2 - 1;
            g2.drawString(fullText, tx, ty);

            x += badgeW + H_GAP;

            if (x + 40 > getWidth()) break;
        }

        g2.dispose();
    }
}
