package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Card renderer component — custom-drawn game card UI component.
 *
 * Design: clean modern card face with rich color gradients,
 * subtle shadow, small type badge, central icon, and bottom name.
 *
 * Card dimensions: 90x130px, corner radius 10px
 */
public class CardRenderer extends JPanel {

    /** Card width (pixels) */
    public static final int CARD_W = 90;
    /** Card height (pixels) */
    public static final int CARD_H = 130;
    /** Card corner radius */
    public static final int CORNER_RADIUS = 10;

    /** Card lift distance on mouse hover */
    private static final int LIFT_HOVER    = 10;
    /** Card lift distance when selected */
    private static final int LIFT_SELECTED = 18;

    /**
     * Card color palette inner class.
     */
    private static final class CardPalette {
        final Color bg;        // Main background color
        final Color gradient;  // Gradient endpoint color
        final Color text;      // Text color

        CardPalette(Color bg, Color gradient, Color text) {
            this.bg       = bg;
            this.gradient = gradient;
            this.text     = text;
        }

        CardPalette(Color bg, Color text) {
            this(bg, null, text);
        }
    }

    /** Card color palette map key=color name/type name, value=palette */
    private static final Map<String, CardPalette> PALETTES = new HashMap<>();

    static {
        // ===== Pure property color palettes =====
        for (Map.Entry<String, Color> entry : AppTheme.PROPERTY_COLORS.entrySet()) {
            String colorName = entry.getKey();
            Color bg = entry.getValue();
            Color gradient = AppTheme.PROPERTY_GRADIENT_COLORS.getOrDefault(
                    colorName, bg.darker().darker());
            Color text = isLightColor(colorName) ? new Color(0x1A1A1A) : Color.WHITE;
            PALETTES.put(colorName, new CardPalette(bg, gradient, text));
        }

        // ===== Dual-color rent card palettes =====
        PALETTES.put("BROWN_LIGHT_BLUE",
                new CardPalette(new Color(0x8B5E3C), new Color(0x87CEEB), Color.WHITE));
        PALETTES.put("PINK_ORANGE",
                new CardPalette(new Color(0xFF69B4), new Color(0xFF8C00), Color.WHITE));
        PALETTES.put("RED_YELLOW",
                new CardPalette(new Color(0xDC143C), new Color(0xFFD700), new Color(0x1A1A1A)));
        PALETTES.put("GREEN_BLUE",
                new CardPalette(new Color(0x228B22), new Color(0x00008B), Color.WHITE));
        PALETTES.put("BLACK_LIGHT_GREEN",
                new CardPalette(new Color(0x2B2B2B), new Color(0x90EE90), Color.WHITE));

        // ===== Default palettes by card type =====
        PALETTES.put("MONEY",
                new CardPalette(new Color(0x2E7D32), new Color(0x1B5E20), Color.WHITE));
        PALETTES.put("ACTION",
                new CardPalette(new Color(0x6A1B9A), new Color(0x4A0072), Color.WHITE));
        PALETTES.put("RENT",
                new CardPalette(new Color(0xBF360C), new Color(0x870000), Color.WHITE));

        // ===== Wild card palette =====
        PALETTES.put("WILD",
                new CardPalette(new Color(0xFF6B6B), new Color(0x4D96FF), Color.WHITE));

        // ===== Fallback gray =====
        PALETTES.put("NONE",
                new CardPalette(Color.GRAY, Color.DARK_GRAY, Color.WHITE));
    }

    private static boolean isLightColor(String colorName) {
        return "LIGHT_BLUE".equals(colorName)
                || "YELLOW".equals(colorName)
                || "LIGHT_GREEN".equals(colorName);
    }

    /** Card unique identifier */
    private final String cardId;
    /** Card name (displayed on the card face) */
    private final String cardName;
    /** Card type (MONEY/PROPERTY/ACTION/RENT) */
    private final String cardType;
    /** Color key (used to look up the color palette) */
    private final String colorKey;
    /** Money value (only for money cards, 0 otherwise) */
    private final int    value;
    /** Card view model */
    private CardViewModel viewModel;

    /** Whether in selected state */
    private boolean selected  = false;
    /** Whether mouse is hovering */
    private boolean hovered   = false;

    /** Current lift in pixels (animation interpolation result) */
    private float   currentLift = 0f;
    /** Animation timer (16ms/frame, approx 60fps) */
    private javax.swing.Timer animTimer;

    @FunctionalInterface
    public interface PlayListener {
        void onPlay(String cardId);
    }

    private PlayListener playListener;

    public CardRenderer(String cardId, String cardName,
                        String cardType, String colorKey, int value) {
        this.cardId   = cardId;
        this.cardName = cardName;
        this.cardType = cardType;
        this.colorKey = colorKey;
        this.value    = value;
        initComponent();
    }

    public CardRenderer(CardViewModel vm) {
        this(vm.getCardId(), vm.getCardName(), vm.getCardType(),
             vm.getColor(), vm.getValue());
        this.viewModel = vm;
    }

    private void initComponent() {
        setPreferredSize(new Dimension(CARD_W, CARD_H + LIFT_SELECTED + 4));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
        setOpaque(false);

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

        animTimer = new javax.swing.Timer(16, e -> {
            float target = getTargetLift();
            float delta  = target - currentLift;
            if (Math.abs(delta) < 0.5f) {
                currentLift = target;
                ((javax.swing.Timer) e.getSource()).stop();
            } else {
                currentLift += delta * 0.25f;
            }
            repaint();
        });

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ==================== Public methods ====================

    public void setPlayListener(PlayListener listener) {
        this.playListener = listener;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        animateLift();
        repaint();
    }

    public boolean isSelected() {
        return selected;
    }

    public String getCardId() {
        return cardId;
    }

    public CardViewModel getViewModel() {
        return viewModel;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(enabled
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        repaint();
    }

    // ==================== Animation ====================

    private float getTargetLift() {
        if (selected) return LIFT_SELECTED;
        if (hovered && isEnabled()) return LIFT_HOVER;
        return 0f;
    }

    private void animateLift() {
        if (!animTimer.isRunning()) animTimer.start();
    }

    // ==================== Custom painting ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int liftPx = Math.round(currentLift);
        int cardTop = (getHeight() - CARD_H) - liftPx;

        Shape cardShape = new RoundRectangle2D.Float(0, cardTop, CARD_W, CARD_H,
                CORNER_RADIUS, CORNER_RADIUS);
        g2.setClip(cardShape);

        // 1. Drop shadow
        paintShadow(g2, cardTop);
        // 2. Card background gradient
        paintBackground(g2, cardTop);
        // 3. Selection glow
        if (selected) paintSelectionGlow(g2, cardTop);
        // 4. Disabled overlay
        if (!isEnabled()) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fill(cardShape);
        }
        g2.setClip(null);

        // Elements drawn outside clip
        // 5. Type badge
        paintTypeBadge(g2, cardTop);
        // 6. Central icon
        paintCenterIcon(g2, cardTop);
        // 7. Card name
        paintCardName(g2, cardTop);
        // 8. Value badge
        if (value > 0) paintValueBadge(g2, cardTop);
        // 9. Border
        paintBorder(g2, cardTop);

        g2.dispose();
    }

    /** Multi-layer drop shadow */
    private void paintShadow(Graphics2D g2, int cardTop) {
        int offset = selected ? 8 : (hovered ? 6 : 3);
        int alpha  = selected ? 100 : (hovered ? 80 : 60);

        for (int i = offset; i > 0; i--) {
            float a = alpha * ((float) (offset - i + 1) / offset);
            g2.setColor(new Color(0, 0, 0, (int) a));
            g2.fill(new RoundRectangle2D.Float(i, cardTop + i, CARD_W, CARD_H,
                    CORNER_RADIUS, CORNER_RADIUS));
        }
    }

    /** Card background with gradient */
    private void paintBackground(Graphics2D g2, int cardTop) {
        Shape shape = new RoundRectangle2D.Float(0, cardTop, CARD_W, CARD_H,
                CORNER_RADIUS, CORNER_RADIUS);

        if ("WILD".equals(colorKey)) {
            float[] fractions = {0f, 0.33f, 0.66f, 1f};
            Color[] colors = {
                    new Color(0xFF6B6B), new Color(0xFFD93D),
                    new Color(0x6BCB77), new Color(0x4D96FF)
            };
            g2.setPaint(new LinearGradientPaint(0, cardTop, CARD_W, cardTop + CARD_H,
                    fractions, colors));
        } else {
            CardPalette p = resolvePalette();
            if (p != null && p.gradient != null) {
                g2.setPaint(new GradientPaint(0, cardTop, p.bg,
                        0, cardTop + CARD_H, p.gradient));
            } else if (p != null) {
                g2.setPaint(p.bg);
            } else {
                g2.setPaint(Color.GRAY);
            }
        }
        g2.fill(shape);

        // Subtle top shine
        g2.setColor(new Color(255, 255, 255, 18));
        g2.fillRoundRect(2, cardTop + 2, CARD_W - 4, (int)(CARD_H * 0.35f),
                CORNER_RADIUS - 2, CORNER_RADIUS - 2);
    }

    /** Selection gold glow */
    private void paintSelectionGlow(Graphics2D g2, int cardTop) {
        RadialGradientPaint glow = new RadialGradientPaint(
                new Point2D.Float(CARD_W / 2f, cardTop + CARD_H / 2f),
                CARD_W * 0.7f,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 215, 0, 70), new Color(255, 215, 0, 0)}
        );
        g2.setPaint(glow);
        g2.fill(new RoundRectangle2D.Float(0, cardTop, CARD_W, CARD_H,
                CORNER_RADIUS, CORNER_RADIUS));
    }

    /** Type badge: small rounded pill top-left */
    private void paintTypeBadge(Graphics2D g2, int cardTop) {
        String label = cardType;
        Font font = new Font("SansSerif", Font.BOLD, 8);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(label);
        int px = 5, py = cardTop + 5;
        int pw = tw + 10, ph = fm.getHeight() + 3;

        // Shadow
        g2.setColor(new Color(0, 0, 0, 80));
        g2.fillRoundRect(px + 1, py + 1, pw, ph, 6, 6);

        // Background
        g2.setColor(new Color(0, 0, 0, 100));
        g2.fillRoundRect(px, py, pw, ph, 6, 6);

        // Border
        g2.setColor(new Color(255, 255, 255, 60));
        g2.setStroke(new BasicStroke(0.5f));
        g2.drawRoundRect(px, py, pw, ph, 6, 6);

        // Text
        g2.setColor(Color.WHITE);
        g2.drawString(label, px + 5, py + fm.getAscent() + 1);
    }

    /** Large central icon drawn with Graphics2D */
    private void paintCenterIcon(Graphics2D g2, int cardTop) {
        int cx = CARD_W / 2;
        int cy = cardTop + CARD_H / 2 - 4;
        int s = 26;

        // Icon shadow
        g2.setColor(new Color(0, 0, 0, 40));
        g2.translate(1, 2);
        drawIcon(g2, cx, cy, s, true);
        g2.translate(-1, -2);

        // Main icon
        g2.setColor(new Color(255, 255, 255, 180));
        drawIcon(g2, cx, cy, s, false);
    }

    private void drawIcon(Graphics2D g2, int cx, int cy, int s, boolean shadow) {
        g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (cardType) {
            case "MONEY":    drawMoneyIcon(g2, cx, cy, s); break;
            case "PROPERTY": drawHouseIcon(g2, cx, cy, s); break;
            case "ACTION":   drawBoltIcon(g2, cx, cy, s); break;
            case "RENT":     drawDiamondIcon(g2, cx, cy, s); break;
            default:         drawDiamondIcon(g2, cx, cy, s); break;
        }
    }

    private void drawMoneyIcon(Graphics2D g2, int cx, int cy, int s) {
        // Circle with $
        g2.drawOval(cx - s/2, cy - s/2, s, s);
        g2.setFont(new Font("SansSerif", Font.BOLD, s - 4));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("$", cx - fm.stringWidth("$") / 2,
                cy + (fm.getAscent() - fm.getDescent()) / 2);
    }

    private void drawHouseIcon(Graphics2D g2, int cx, int cy, int s) {
        int hw = s * 3 / 5;
        int hh = s * 2 / 5;
        int[] rx = {cx - hw, cx, cx + hw};
        int[] ry = {cy, cy - s/2, cy};
        g2.fillPolygon(rx, ry, 3);
        g2.fillRect(cx - hw/2, cy, hw, hh);
        // Door
        g2.setColor(new Color(0, 0, 0, 50));
        g2.fillRect(cx - hw/6, cy + hh/3, hw/3, hh * 2/3);
    }

    private void drawBoltIcon(Graphics2D g2, int cx, int cy, int s) {
        int hs = s / 2;
        Path2D.Float bolt = new Path2D.Float();
        bolt.moveTo(cx + hs * 0.2f, cy - hs);
        bolt.lineTo(cx - hs * 0.3f, cy - hs * 0.1f);
        bolt.lineTo(cx + hs * 0.1f, cy - hs * 0.1f);
        bolt.lineTo(cx - hs * 0.2f, cy + hs);
        bolt.lineTo(cx + hs * 0.4f, cy + hs * 0.1f);
        bolt.lineTo(cx + hs * 0.1f, cy + hs * 0.1f);
        bolt.closePath();
        g2.fill(bolt);
    }

    private void drawDiamondIcon(Graphics2D g2, int cx, int cy, int s) {
        int[] dx = {cx, cx - s/2, cx, cx + s/2};
        int[] dy = {cy - s/2, cy, cy + s/2, cy};
        g2.fillPolygon(dx, dy, 4);
        g2.setColor(new Color(0, 0, 0, 40));
        g2.setFont(new Font("SansSerif", Font.BOLD, s/2));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString("$", cx - fm.stringWidth("$") / 2,
                cy + (fm.getAscent() - fm.getDescent()) / 2);
    }

    /** Card name centered at bottom */
    private void paintCardName(Graphics2D g2, int cardTop) {
        CardPalette p = resolvePalette();
        g2.setColor(p != null ? p.text : Color.WHITE);

        int fontSize = cardName.length() > 12 ? 9 : (cardName.length() > 8 ? 10 : 11);
        g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();

        String[] lines = wrapText(cardName, fm, CARD_W - 12);
        int lineH = fm.getHeight();
        int totalH = lines.length * lineH;

        int startY;
        if (value > 0) {
            startY = cardTop + CARD_H - 36 - totalH;
        } else {
            startY = cardTop + CARD_H - 14 - totalH;
        }

        for (String line : lines) {
            int x = (CARD_W - fm.stringWidth(line)) / 2;
            g2.setColor(new Color(0, 0, 0, 80));
            g2.drawString(line, x + 1, startY + 1);
            g2.setColor(p != null ? p.text : Color.WHITE);
            g2.drawString(line, x, startY);
            startY += lineH;
        }
    }

    /** Value badge (money cards) */
    private void paintValueBadge(Graphics2D g2, int cardTop) {
        String label = "$" + value + "M";
        Font font = new Font("SansSerif", Font.BOLD, 12);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int bw = fm.stringWidth(label) + 14;
        int bh = fm.getHeight() + 4;
        int bx = (CARD_W - bw) / 2;
        int by = cardTop + CARD_H - bh - 6;

        g2.setColor(new Color(0, 0, 0, 150));
        g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 8, 8));

        g2.setColor(new Color(0xFFD700));
        g2.drawString(label, bx + 7, by + fm.getAscent() + 2);
    }

    /** Card border */
    private void paintBorder(Graphics2D g2, int cardTop) {
        RoundRectangle2D.Float border = new RoundRectangle2D.Float(
                0.5f, cardTop + 0.5f, CARD_W - 1f, CARD_H - 1f,
                CORNER_RADIUS, CORNER_RADIUS);

        if (selected) {
            for (int i = 3; i > 0; i--) {
                g2.setColor(new Color(255, 215, 0, 50 * i));
                g2.setStroke(new BasicStroke(i * 2 + 1f));
                g2.draw(border);
            }
            g2.setColor(new Color(0xFFD700));
            g2.setStroke(new BasicStroke(2.5f));
        } else {
            CardPalette p = resolvePalette();
            Color borderColor = p != null ? p.bg.darker() : Color.GRAY;
            // Inner thin highlight
            g2.setColor(new Color(255, 255, 255, 15));
            g2.setStroke(new BasicStroke(0.5f));
            g2.draw(new RoundRectangle2D.Float(1.5f, cardTop + 1.5f,
                    CARD_W - 3f, CARD_H - 3f, CORNER_RADIUS - 1, CORNER_RADIUS - 1));
            g2.setColor(borderColor);
            g2.setStroke(new BasicStroke(1.5f));
        }
        g2.draw(border);
    }

    private CardPalette resolvePalette() {
        CardPalette p = PALETTES.get(colorKey);
        if (p == null) p = PALETTES.get(cardType);
        if (p == null) p = PALETTES.get("NONE");
        return p;
    }

    /** Text wrapping: max 2 lines */
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

    // ==================== Static factory methods ====================

    public static CardRenderer money(String cardId, int value) {
        return new CardRenderer(cardId, value + "M", "MONEY", "MONEY", value);
    }

    public static CardRenderer property(String cardId, String name, String colorKey) {
        return new CardRenderer(cardId, name, "PROPERTY", colorKey, 0);
    }

    public static CardRenderer action(String cardId, String name) {
        return new CardRenderer(cardId, name, "ACTION", "ACTION", 0);
    }

    public static CardRenderer rent(String cardId, String name, String colorKey) {
        return new CardRenderer(cardId, name, "RENT", colorKey, 0);
    }
}
