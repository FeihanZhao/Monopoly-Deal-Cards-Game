package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;
import com.google.gson.JsonObject;

/**
 * PropertySetPanel
 *
 * Renders all of a player's property groups as a row of compact coloured
 * badges. Each badge shows the group colour, current card count vs. required,
 * and house/hotel icons when present.
 *
 * Replaces the empty propertyPanel JPanel in PlayerPanel.
 *
 * Usage in PlayerPanel:
 *
 *   // Construction — already handled by createRightPanel() after you
 *   // replace the plain JPanel with this class.
 *
 *   // Updating — call from updateFromJson() once per server state push:
 *   propertySetPanel.updateFromJson(data);
 *
 * Expected JSON shape (subset of GameState.PlayerState):
 * {
 *   "propertyColorCounts": { "RED": 2, "GREEN": 3, ... },
 *   "completeSets": 1          // used to know which groups are full
 *   // optional future fields: "houseColors", "hotelColors"
 * }
 *
 * The required set sizes are hardcoded to match CardColor in the backend:
 *   BROWN / BLUE → 2    all others → 3    BLACK → 4
 */
public class PropertySetPanel extends JPanel {

    // Badge dimensions
    private static final int BADGE_H   = 22;
    private static final int BADGE_ARC = 6;
    private static final int DOT_SIZE  = 10;
    private static final int H_GAP     = 4;

    // Colour palette (matches CardColor enum names)
    private static final Map<String, Color> BG_COLORS   = new HashMap<>();
    private static final Map<String, Color> TEXT_COLORS = new HashMap<>();

    static {
        BG_COLORS.put("BROWN",       new Color(0x8B5E3C));
        BG_COLORS.put("LIGHT_BLUE",  new Color(0x87CEEB));
        BG_COLORS.put("PINK",        new Color(0xFF69B4));
        BG_COLORS.put("ORANGE",      new Color(0xFF8C00));
        BG_COLORS.put("RED",         new Color(0xDC143C));
        BG_COLORS.put("YELLOW",      new Color(0xFFD700));
        BG_COLORS.put("GREEN",       new Color(0x228B22));
        BG_COLORS.put("BLUE",        new Color(0x00008B));
        BG_COLORS.put("PURPLE",      new Color(0x6A0DAD));
        BG_COLORS.put("BLACK",       new Color(0x2B2B2B));
        BG_COLORS.put("LIGHT_GREEN", new Color(0x90EE90));

        // Text: dark for light backgrounds, white for dark ones
        TEXT_COLORS.put("LIGHT_BLUE",  new Color(0x1A1A1A));
        TEXT_COLORS.put("YELLOW",      new Color(0x1A1A1A));
        TEXT_COLORS.put("LIGHT_GREEN", new Color(0x1A1A1A));
    }

    /** How many cards are needed for each colour group (mirrors CardColor.getSetSize). */
    private static final Map<String, Integer> SET_SIZES = new HashMap<>();

    static {
        SET_SIZES.put("BROWN",       2);
        SET_SIZES.put("LIGHT_BLUE",  3);
        SET_SIZES.put("PINK",        3);
        SET_SIZES.put("ORANGE",      3);
        SET_SIZES.put("RED",         3);
        SET_SIZES.put("YELLOW",      3);
        SET_SIZES.put("GREEN",       3);
        SET_SIZES.put("BLUE",        2);
        SET_SIZES.put("PURPLE",      3);
        SET_SIZES.put("BLACK",       4);
        SET_SIZES.put("LIGHT_GREEN", 3);
    }

    // State
    /**
     * colorKey → count of cards currently in that group.
     * Only colours with count > 0 are stored.
     */
    private final Map<String, Integer> colorCounts = new HashMap<>();

    /**
     * colorKey → true if a house is present on that complete set.
     * Populated from JSON when the field is available.
     */
    private final Map<String, Boolean> hasHouse = new HashMap<>();

    /**
     * colorKey → true if a hotel is present on that complete set.
     */
    private final Map<String, Boolean> hasHotel = new HashMap<>();

    // Constructor
    public PropertySetPanel() {
        setOpaque(false);
        setPreferredSize(new Dimension(300, BADGE_H + 6));
        setMinimumSize(new Dimension(0, BADGE_H + 6));
    }


    // Public API
    /**
     * Update badge state from a GameState.PlayerState JSON object.
     * Call this from PlayerPanel.updateFromJson().
     *
     * @param playerData the JsonObject for one player from the server payload
     */
    public void updateFromJson(JsonObject playerData) {
        colorCounts.clear();
        hasHouse.clear();
        hasHotel.clear();

        // propertyColorCounts
        if (playerData.has("propertyColorCounts")) {
            JsonObject counts = playerData.getAsJsonObject("propertyColorCounts");
            for (String key : counts.keySet()) {
                int count = counts.get(key).getAsInt();
                if (count > 0) {
                    colorCounts.put(key, count);
                }
            }
        }

        // houseColors (optional — backend may not send this yet)
        if (playerData.has("houseColors")) {
            JsonObject houses = playerData.getAsJsonObject("houseColors");
            for (String key : houses.keySet()) {
                if (houses.get(key).getAsBoolean()) hasHouse.put(key, true);
            }
        }

        // hotelColors (optional)
        if (playerData.has("hotelColors")) {
            JsonObject hotels = playerData.getAsJsonObject("hotelColors");
            for (String key : hotels.keySet()) {
                if (hotels.get(key).getAsBoolean()) hasHotel.put(key, true);
            }
        }

        repaint();
    }


    // Painting
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
            int    required = SET_SIZES.getOrDefault(colorKey, 3);
            boolean complete = count >= required;
            boolean house    = hasHouse.getOrDefault(colorKey, false);
            boolean hotel    = hasHotel.getOrDefault(colorKey, false);

            // Measure badge width
            String countText = count + "/" + required;
            String extraText = hotel ? " 🏨" : house ? " 🏠" : "";
            String fullText  = countText + extraText;

            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int textW  = fm.stringWidth(fullText);
            int badgeW = DOT_SIZE + 4 + textW + 10;   // dot + gap + text + padding

            // Badge background
            Color bg = BG_COLORS.getOrDefault(colorKey, Color.GRAY);

            // Complete sets get a gold outer glow
            if (complete) {
                g2.setColor(new Color(255, 215, 0, 80));
                g2.setStroke(new BasicStroke(3f));
                g2.draw(new RoundRectangle2D.Float(
                        x - 1, top - 1, badgeW + 2, BADGE_H + 2, BADGE_ARC + 2, BADGE_ARC + 2));
            }

            g2.setPaint(bg);
            g2.fill(new RoundRectangle2D.Float(x, top, badgeW, BADGE_H, BADGE_ARC, BADGE_ARC));

            // Border: gold if complete, darker shade otherwise
            g2.setStroke(new BasicStroke(1.2f));
            g2.setColor(complete ? new Color(255, 215, 0) : bg.darker());
            g2.draw(new RoundRectangle2D.Float(
                    x + 0.6f, top + 0.6f, badgeW - 1.2f, BADGE_H - 1.2f,
                    BADGE_ARC, BADGE_ARC));

            // Colour dot
            int dotX = x + 5;
            int dotY = top + (BADGE_H - DOT_SIZE) / 2;
            g2.setColor(bg.brighter());
            g2.fillOval(dotX, dotY, DOT_SIZE, DOT_SIZE);
            g2.setColor(bg.darker().darker());
            g2.setStroke(new BasicStroke(0.8f));
            g2.drawOval(dotX, dotY, DOT_SIZE, DOT_SIZE);

            // Count text
            Color textColor = TEXT_COLORS.getOrDefault(colorKey, Color.WHITE);
            g2.setColor(textColor);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            int tx = dotX + DOT_SIZE + 4;
            int ty = top + (BADGE_H + fm.getAscent() - fm.getDescent()) / 2 - 1;
            g2.drawString(fullText, tx, ty);

            x += badgeW + H_GAP;

            // Stop drawing if we've run out of horizontal space
            if (x + 40 > getWidth()) break;
        }

        g2.dispose();
    }
}