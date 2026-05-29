package com.monopolydeal.view;

import com.google.gson.JsonObject;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Premium Visual Card Renderer - Monopoly Deal Game
 * Ultra-polished card UI with advanced effects:
 * - Multi-layer gradient backgrounds
 * - Neon glow & metallic borders
 * - Smooth lift + hover animation
 * - Golden radiant selection effect
 * - Glassmorphism badges & text shadows
 * - Dynamic light reflection effect
 * - Premium typography & emoji glow
 * - Professional depth & shadow system
 */
public class CardRenderer extends JPanel {

    // ==========================================================================
    // Premium Card Dimensions & Style Constants
    // ==========================================================================
    public static final int CARD_W = 92;
    public static final int CARD_H = 134;
    public static final int CORNER_RADIUS = 14;

    private static final int LIFT_HOVER    = 14;
    private static final int LIFT_SELECTED = 26;
    private static final float ANIMATION_SMOOTH = 0.22f;

    // ==========================================================================
    // Advanced Color Palette with Gradient, Neon, Border, Text, Metallic
    // ==========================================================================
    private static final class CardPalette {
        final Color base;
        final Color gradientTop;
        final Color gradientBot;
        final Color neonGlow;
        final Color border;
        final Color text;
        final Color metallic;

        CardPalette(Color base, Color gTop, Color gBot, Color neon, Color border, Color text, Color metallic) {
            this.base = base;
            this.gradientTop = gTop;
            this.gradientBot = gBot;
            this.neonGlow = neon;
            this.border = border;
            this.text = text;
            this.metallic = metallic;
        }
    }

    private static final Map<String, CardPalette> PALETTES = new HashMap<>();

    static {
        // === Property Colors (VIBRANT, NEON, PREMIUM) ===
        PALETTES.put("BROWN",        new CardPalette(new Color(0x9C6639), new Color(0xB47B52), new Color(0x6D4728), new Color(0xFFB86B), new Color(0x5C3A21), Color.WHITE, new Color(0xFFD7A8)));
        PALETTES.put("LIGHT_BLUE",   new CardPalette(new Color(0x73C9F7), new Color(0xA1E0FF), new Color(0x4A9CD4), new Color(0x80EDFF), new Color(0x2A74A1), Color.BLACK, new Color(0xE0F7FF)));
        PALETTES.put("PINK",         new CardPalette(new Color(0xF55A9C), new Color(0xFF8DC2), new Color(0xBC2463), new Color(0xFF94E1), new Color(0x8C1647), Color.WHITE, new Color(0xFFCFE8)));
        PALETTES.put("ORANGE",       new CardPalette(new Color(0xFF7B18), new Color(0xFFB64F), new Color(0xD45A00), new Color(0xFFB569), new Color(0xA14300), Color.WHITE, new Color(0xFFE0B8)));
        PALETTES.put("RED",          new CardPalette(new Color(0xE52F4C), new Color(0xFF5C74), new Color(0xA11225), new Color(0xFF6B85), new Color(0x780A19), Color.WHITE, new Color(0xFFC4CC)));
        PALETTES.put("YELLOW",       new CardPalette(new Color(0xFFD838), new Color(0xFFE970), new Color(0xCC9900), new Color(0xFFF769), new Color(0x997200), Color.BLACK, new Color(0xFFF7C2)));
        PALETTES.put("GREEN",        new CardPalette(new Color(0x2AA855), new Color(0x54D67C), new Color(0x156B33), new Color(0x6EFF9E), new Color(0x0D4D21), Color.WHITE, new Color(0xC4FFD6)));
        PALETTES.put("BLUE",         new CardPalette(new Color(0x1A5CE2), new Color(0x4D8FFF), new Color(0x003399), new Color(0x6BA6FF), new Color(0x002266), Color.WHITE, new Color(0xB8D4FF)));
        PALETTES.put("BLACK",        new CardPalette(new Color(0x3A3A3A), new Color(0x5A5A5A), new Color(0x1A1A1A), new Color(0xA0A0A0), new Color(0x000000), Color.WHITE, new Color(0x808080)));
        PALETTES.put("LIGHT_GREEN",  new CardPalette(new Color(0x77DD77), new Color(0xAAF0A8), new Color(0x45B745), new Color(0x9CFF9C), new Color(0x2B802B), Color.BLACK, new Color(0xD4FFD4)));

        // === Dual Color RENT ===
        PALETTES.put("BROWN_LIGHT_BLUE",    new CardPalette(new Color(0x8B5E3C), new Color(0x87CEEB), new Color(0x4A90B8), new Color(0xFFFFFF), new Color(0x443322), Color.WHITE, new Color(0xFFE0C0)));
        PALETTES.put("PINK_ORANGE",         new CardPalette(new Color(0xFF69B4), new Color(0xFF8C00), new Color(0xE65100), new Color(0xFFFFFF), new Color(0x881144), Color.WHITE, new Color(0xFFD9E0)));
        PALETTES.put("RED_YELLOW",          new CardPalette(new Color(0xDC143C), new Color(0xFFD700), new Color(0xCC9900), new Color(0xFFFFFF), new Color(0x880011), Color.WHITE, new Color(0xFFE0C0)));
        PALETTES.put("GREEN_BLUE",          new CardPalette(new Color(0x228B22), new Color(0x0000CD), new Color(0x00008B), new Color(0xFFFFFF), new Color(0x004400), Color.WHITE, new Color(0xC0FFE0)));
        PALETTES.put("BLACK_LIGHT_GREEN",   new CardPalette(new Color(0x2B2B2B), new Color(0x90EE90), new Color(0x4CAF50), new Color(0xFFFFFF), new Color(0x000000), Color.WHITE, new Color(0xC0C0C0)));

        // === Card Types ===
        PALETTES.put("MONEY",    new CardPalette(new Color(0x1B692C), new Color(0x38A853), new Color(0x0D441A), new Color(0x6EFF9E), new Color(0x063310), Color.WHITE, new Color(0x9CFFB8)));
        PALETTES.put("ACTION",   new CardPalette(new Color(0x7B2CBF), new Color(0xA855F7), new Color(0x4A148C), new Color(0xD87BFF), new Color(0x300066), Color.WHITE, new Color(0xE4B0FF)));
        PALETTES.put("RENT",     new CardPalette(new Color(0xC62828), new Color(0xF44336), new Color(0x870000), new Color(0xFF7575), new Color(0x660000), Color.WHITE, new Color(0xFFB0B0)));
        PALETTES.put("WILD",     new CardPalette(new Color(0xFFFFFF), new Color(0xFFFFFF), new Color(0xFFFFFF), new Color(0xFFFFFF), new Color(0x444444), Color.BLACK, new Color(0xFFFFFF)));
        PALETTES.put("NONE",     new CardPalette(Color.GRAY, Color.LIGHT_GRAY, Color.DARK_GRAY, Color.LIGHT_GRAY, Color.BLACK, Color.WHITE, Color.LIGHT_GRAY));
    }

    // ==========================================================================
    // Icons & Symbols
    // ==========================================================================
    private static final Map<String, String> TYPE_ICONS = new HashMap<>();
    static {
        TYPE_ICONS.put("MONEY",      "\uD83D\uDCB5");
        TYPE_ICONS.put("PROPERTY",   "\uD83C\uDFE0");
        TYPE_ICONS.put("ACTION",     "\u26A1");
        TYPE_ICONS.put("RENT",       "\uD83D\uDCB8");
    }

    // ==========================================================================
    // Card Data
    // ==========================================================================
    private final String cardId;
    private final String cardName;
    private final String cardType;
    private final String colorKey;
    private final int value;

    private boolean selected = false;
    private boolean hovered = false;
    private float currentLift = 0f;
    private javax.swing.Timer animTimer;
    private PlayListener playListener;

    @FunctionalInterface
    public interface PlayListener {
        void onPlay(String cardId);
    }

    // ==========================================================================
    // Constructors
    // ==========================================================================
    public CardRenderer(String cardId, String cardName, String cardType, String colorKey, int value) {
        this.cardId = cardId;
        this.cardName = cardName;
        this.cardType = cardType;
        this.colorKey = colorKey;
        this.value = value;
        init();
    }

    public CardRenderer(JsonObject cardJson) {
        this(
            cardJson.get("cardId").getAsString(),
            cardJson.get("cardName").getAsString(),
            cardJson.get("cardType").getAsString(),
            cardJson.get("color").getAsString(),
            cardJson.has("value") ? cardJson.get("value").getAsInt() : 0
        );
    }

    // ==========================================================================
    // Component Initialization
    // ==========================================================================
    private void init() {
        setPreferredSize(new Dimension(CARD_W, CARD_H + LIFT_SELECTED + 6));
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        setupMouse();
        setupAnimation();
    }

    private void setupMouse() {
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true; animate(); }
            @Override public void mouseExited(MouseEvent e) { hovered = false; animate(); }
            @Override public void mouseClicked(MouseEvent e) { if (isEnabled() && playListener != null) playListener.onPlay(cardId); }
        });
    }

    private void setupAnimation() {
        animTimer = new javax.swing.Timer(16, e -> {
            float target = getTargetLift();
            float diff = target - currentLift;
            if (Math.abs(diff) < 0.4f) {
                currentLift = target;
                animTimer.stop();
            } else {
                currentLift += diff * ANIMATION_SMOOTH;
            }
            repaint();
        });
    }

    // ==========================================================================
    // Public API
    // ==========================================================================
    public void setPlayListener(PlayListener listener) { this.playListener = listener; }
    public void setSelected(boolean s) { selected = s; animate(); repaint(); }
    public boolean isSelected() { return selected; }
    public String getCardId() { return cardId; }

    @Override
    public void setEnabled(boolean b) {
        super.setEnabled(b);
        setCursor(b ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        repaint();
    }

    // ==========================================================================
    // Animation Logic
    // ==========================================================================
    private float getTargetLift() {
        if (selected) return LIFT_SELECTED;
        if (hovered && isEnabled()) return LIFT_HOVER;
        return 0;
    }

    private void animate() {
        if (!animTimer.isRunning()) animTimer.start();
    }

    // ==========================================================================
    // Ultra Premium Painting
    // ==========================================================================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        enableQualityRendering(g2);

        int lift = Math.round(currentLift);
        int cardY = (getHeight() - CARD_H) - lift;
        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, cardY, CARD_W, CARD_H, CORNER_RADIUS, CORNER_RADIUS);

        g2.setClip(shape);
        drawShadow(g2, cardY);
        drawBackground(g2, cardY);
        drawLightReflection(g2, cardY);
        drawSelectionGlow(g2, cardY);
        drawDisabledOverlay(g2, shape);
        g2.setClip(null);

        drawBorder(g2, cardY);
        drawTypeBadge(g2, cardY);
        drawIcon(g2, cardY);
        drawCardName(g2, cardY);
        if (value > 0) drawValueBadge(g2, cardY);

        g2.dispose();
    }

    private void enableQualityRendering(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    // ==========================================================================
    // Shadow (Depth)
    // ==========================================================================
    private void drawShadow(Graphics2D g2, int y) {
        int depth = selected ? 9 : hovered ? 6 : 3;
        int alpha = selected ? 130 : hovered ? 100 : 60;
        for (int k = depth; k > 0; k--) {
            float a = alpha * ((depth - k + 1f) / depth);
            g2.setColor(new Color(0, 0, 0, (int) a));
            g2.fill(new RoundRectangle2D.Float(k, y + k, CARD_W, CARD_H, CORNER_RADIUS, CORNER_RADIUS));
        }
    }

    // ==========================================================================
    // Background (Multi-Layer Gradient)
    // ==========================================================================
    private void drawBackground(Graphics2D g2, int y) {
        CardPalette p = resolvePalette();
        if (colorKey.equals("WILD")) {
            drawRainbowBackground(g2, y);
        } else {
            GradientPaint gp = new GradientPaint(0, y, p.gradientTop, 0, y + CARD_H, p.gradientBot);
            g2.setPaint(gp);
            g2.fill(new RoundRectangle2D.Float(0, y, CARD_W, CARD_H, CORNER_RADIUS, CORNER_RADIUS));
        }
    }

    private void drawRainbowBackground(Graphics2D g2, int y) {
        float[] f = {0.0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f};
        Color[] c = {new Color(255,80,80), new Color(255,180,80), new Color(255,240,80), new Color(100,240,100), new Color(80,160,255), new Color(180,80,255)};
        g2.setPaint(new LinearGradientPaint(0, y, CARD_W, y + CARD_H, f, c));
        g2.fill(new RoundRectangle2D.Float(0, y, CARD_W, CARD_H, CORNER_RADIUS, CORNER_RADIUS));
    }

    // ==========================================================================
    // Light Reflection (Premium Glass Effect)
    // ==========================================================================
    private void drawLightReflection(Graphics2D g2, int y) {
        int h = CARD_H / 3;
        GradientPaint gp = new GradientPaint(0, y + 4, new Color(255,255,255,70), 0, y + h, new Color(255,255,255,0));
        g2.setPaint(gp);
        g2.fill(new RoundRectangle2D.Float(2, y + 2, CARD_W - 4, h, CORNER_RADIUS / 2, CORNER_RADIUS / 2));
    }

    // ==========================================================================
    // Selection Glow (Golden Radiant Aura)
    // ==========================================================================
    private void drawSelectionGlow(Graphics2D g2, int y) {
        if (!selected) return;
        Point2D.Float center = new Point2D.Float(CARD_W / 2f, y + CARD_H / 2f);
        RadialGradientPaint rgp = new RadialGradientPaint(center, CARD_W * 0.85f,
            new float[]{0f, 1f}, new Color[]{new Color(255,215,0,90), new Color(255,215,0,0)});
        g2.setPaint(rgp);
        g2.fill(new RoundRectangle2D.Float(0, y, CARD_W, CARD_H, CORNER_RADIUS, CORNER_RADIUS));
    }

    // ==========================================================================
    // Border (Neon + Metallic + Golden Selected)
    // ==========================================================================
    private void drawBorder(Graphics2D g2, int y) {
        CardPalette p = resolvePalette();
        RoundRectangle2D.Float border = new RoundRectangle2D.Float(1, y + 1, CARD_W - 2, CARD_H - 2, CORNER_RADIUS, CORNER_RADIUS);

        if (selected) {
            for (int i = 3; i >= 1; i--) {
                g2.setColor(new Color(255, 215, 0, i * 35));
                g2.setStroke(new BasicStroke(i * 2f));
                g2.draw(border);
            }
            g2.setColor(new Color(255, 215, 0));
            g2.setStroke(new BasicStroke(2.2f));
        } else {
            g2.setColor(p.border);
            g2.setStroke(new BasicStroke(1.8f));
        }
        g2.draw(border);
    }

    // ==========================================================================
    // Badges & Text (Premium Style)
    // ==========================================================================
    private void drawTypeBadge(Graphics2D g2, int y) {
        String txt = cardType;
        g2.setFont(new Font("Segoe UI", Font.BOLD, 9));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(txt) + 10;
        int h = fm.getHeight() + 4;
        int x = 6;
        int yy = y + 6;

        g2.setColor(new Color(0,0,0,140));
        g2.fill(new RoundRectangle2D.Float(x, yy, w, h, 6,6));
        g2.setColor(Color.WHITE);
        g2.drawString(txt, x + 5, yy + fm.getAscent() + 1);
    }

    private void drawIcon(Graphics2D g2, int y) {
        String icon = TYPE_ICONS.getOrDefault(cardType, "");
        g2.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 30));
        FontMetrics fm = g2.getFontMetrics();
        int x = (CARD_W - fm.stringWidth(icon)) / 2;
        int yy = y + 36 + fm.getAscent();

        g2.setColor(new Color(0,0,0,100));
        g2.drawString(icon, x + 2, yy + 2);
        g2.setColor(Color.WHITE);
        g2.drawString(icon, x, yy);
    }

    private void drawCardName(Graphics2D g2, int y) {
        CardPalette p = resolvePalette();
        int size = cardName.length() > 12 ? 10 : (cardName.length() > 8 ? 11 : 12);
        g2.setFont(new Font("Segoe UI", Font.BOLD, size));
        FontMetrics fm = g2.getFontMetrics();
        String[] lines = wrapText(cardName, fm, CARD_W - 14);
        int lineH = fm.getHeight();
        int totalH = lines.length * lineH;
        int startY = y + CARD_H - (value > 0 ? 36 : 20) - totalH;

        for (String line : lines) {
            int x = (CARD_W - fm.stringWidth(line)) / 2;
            g2.setColor(new Color(0,0,0,140));
            g2.drawString(line, x + 1, startY + 1);
            g2.setColor(p.text);
            g2.drawString(line, x, startY);
            startY += lineH;
        }
    }

    private void drawValueBadge(Graphics2D g2, int y) {
        String txt = "$" + value + "M";
        g2.setFont(new Font("Segoe UI", Font.BOLD, 13));
        FontMetrics fm = g2.getFontMetrics();
        int w = fm.stringWidth(txt) + 16;
        int h = fm.getHeight() + 6;
        int x = (CARD_W - w) / 2;
        int yy = y + CARD_H - h - 8;

        g2.setColor(new Color(0,0,0,180));
        g2.fill(new RoundRectangle2D.Float(x, yy, w, h, 8,8));
        g2.setColor(new Color(255,215,0));
        g2.drawString(txt, x + 8, yy + fm.getAscent() + 2);
    }

    private void drawDisabledOverlay(Graphics2D g2, Shape s) {
        if (isEnabled()) return;
        g2.setColor(new Color(0,0,0,140));
        g2.fill(s);
    }

    // ==========================================================================
    // Utility
    // ==========================================================================
    private CardPalette resolvePalette() {
        CardPalette p = PALETTES.get(colorKey);
        if (p == null) p = PALETTES.get(cardType);
        return p != null ? p : PALETTES.get("NONE");
    }

    private String[] wrapText(String text, FontMetrics fm, int max) {
        if (fm.stringWidth(text) <= max) return new String[]{text};
        String[] words = text.split(" ");
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        boolean split = false;
        for (String w : words) {
            if (!split) {
                String t = a.length() == 0 ? w : a + " " + w;
                if (fm.stringWidth(t) <= max) a = new StringBuilder(t);
                else { split = true; b.append(w); }
            } else b.append(" ").append(w);
        }
        return b.length() == 0 ? new String[]{a.toString()} : new String[]{a.toString(), b.toString()};
    }

    // ==========================================================================
    // Factory Methods
    // ==========================================================================
    public static CardRenderer money(String id, int v) { return new CardRenderer(id, v + "M", "MONEY", "MONEY", v); }
    public static CardRenderer property(String id, String n, String c) { return new CardRenderer(id, n, "PROPERTY", c, 0); }
    public static CardRenderer action(String id, String n) { return new CardRenderer(id, n, "ACTION", "ACTION", 0); }
    public static CardRenderer rent(String id, String n, String c) { return new CardRenderer(id, n, "RENT", c, 0); }
}
