package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * Card renderer component — custom-drawn game card UI component.
 *
 * Displays each card as a visual card in the player's hand area. Supports:
 * - Automatic background color selection by color/type (Palette system)
 * - Mouse hover lift effect (card rises 12px)
 * - Selected state glow effect (gold glowing border)
 * - Card face contains: type badge, icon, name, value (money cards only)
 * - Click triggers playListener callback
 *
 * Card dimensions: 90x130px, corner radius 12px
 *
 * Color schemes:
 * - Each property color has a corresponding background + gradient + border color
 * - Money cards: green tones
 * - Action cards: purple tones
 * - Rent cards: reddish-brown tones
 * - Wild cards: rainbow gradient
 */
public class CardRenderer extends JPanel {

    /** Card width (pixels) */
    public static final int CARD_W = 100;
    /** Card height (pixels) */
    public static final int CARD_H = 145;
    /** Card corner radius */
    public static final int CORNER_RADIUS = 10;

    /** Card lift distance on mouse hover */
    private static final int LIFT_HOVER    = 12;
    /** Card lift distance when selected */
    private static final int LIFT_SELECTED = 22;

    /**
     * Card color palette inner class.
     * Contains background color, gradient color, border color and text color.
     */
    private static final class CardPalette {
        final Color bg;        // Main background color
        final Color gradient;  // Gradient endpoint color (for top-to-bottom gradient)
        final Color border;    // Border color
        final Color text;      // Text color

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

    /** Card color palette map key=color name/type name, value=palette */
    private static final Map<String, CardPalette> PALETTES = new HashMap<>();

    static {
        // ===== Pure property color palettes (color values from AppTheme) =====
        for (Map.Entry<String, Color> entry : AppTheme.PROPERTY_COLORS.entrySet()) {
            String colorName = entry.getKey();
            Color bg = entry.getValue();
            Color gradient = AppTheme.PROPERTY_GRADIENT_COLORS.getOrDefault(
                    colorName, bg.darker().darker());
            Color text = isLightColor(colorName) ? new Color(0x1A1A1A) : Color.WHITE;
            PALETTES.put(colorName, new CardPalette(bg, gradient, text));
        }

        // ===== Dual-color rent card palettes (left→right gradient) =====
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

        // ===== Default palettes by card type =====
        PALETTES.put("MONEY",
                new CardPalette(new Color(0x2E7D32), new Color(0x1B5E20), new Color(0xFFFFFF)));
        PALETTES.put("ACTION",
                new CardPalette(new Color(0x6A1B9A), new Color(0x4A0072), new Color(0xFFFFFF)));
        PALETTES.put("RENT",
                new CardPalette(new Color(0xBF360C), new Color(0x870000), new Color(0xFFFFFF)));

        // ===== Wild card palette (rainbow gradient, special handling in paintComponent) =====
        PALETTES.put("WILD",
                new CardPalette(new Color(0xFF6B6B), new Color(0x4D96FF), new Color(0xFFFFFF)));

        // ===== Fallback gray for no color =====
        PALETTES.put("NONE",
                new CardPalette(Color.GRAY, Color.DARK_GRAY, Color.WHITE));
    }

    /** Check whether this is a light background color (needs dark text for readability) */
    private static boolean isLightColor(String colorName) {
        return "LIGHT_BLUE".equals(colorName)
                || "YELLOW".equals(colorName)
                || "LIGHT_GREEN".equals(colorName);
    }

    /** Card type icon mapping */
    private static final Map<String, String> TYPE_ICONS = new HashMap<>();

    static {
        TYPE_ICONS.put("MONEY",    "💰");   // 💰 Dollar icon
        TYPE_ICONS.put("PROPERTY", "🏠");  // 🏠 House icon
        TYPE_ICONS.put("ACTION",   "⚡");         // ⚡ Lightning icon
        TYPE_ICONS.put("RENT",     "💸");   // 💸 Flying money icon
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
    /** Card view model (for external queries of cardId, cardName, etc.) */
    private CardViewModel viewModel;

    /** Whether in selected state */
    private boolean selected  = false;
    /** Whether mouse is hovering */
    private boolean hovered   = false;

    /** Current lift in pixels (animation interpolation result) */
    private float   currentLift = 0f;
    /** Animation timer (16ms/frame, approx 60fps) */
    private javax.swing.Timer animTimer;

    /**
     * Card click callback interface.
     */
    @FunctionalInterface
    public interface PlayListener {
        void onPlay(String cardId);
    }

    /** Card click callback */
    private PlayListener playListener;

    /**
     * Full constructor.
     * @param cardId card ID
     * @param cardName card name
     * @param cardType card type
     * @param colorKey color key
     * @param value money value
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

    /** Construct from CardViewModel (common path, called by GamePanel) */
    public CardRenderer(CardViewModel vm) {
        this(vm.getCardId(), vm.getCardName(), vm.getCardType(),
             vm.getColor(), vm.getValue());
        this.viewModel = vm;
    }

    /** Initialize component — set size, mouse events, and animation system */
    private void initComponent() {
        // Fixed size (height includes room for lift)
        setPreferredSize(new Dimension(CARD_W, CARD_H + LIFT_SELECTED + 4));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
        setOpaque(false);

        // Mouse events: hover detection and click callback
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

        // Lift animation timer (16ms/frame ≈ 60fps)
        animTimer = new javax.swing.Timer(16, e -> {
            float target = getTargetLift();
            float delta  = target - currentLift;
            if (Math.abs(delta) < 0.5f) {
                currentLift = target;
                ((javax.swing.Timer) e.getSource()).stop();
            } else {
                currentLift += delta * 0.25f;  // Ease interpolation
            }
            repaint();
        });

        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ==================== Public methods ====================

    /** Set the card click callback */
    public void setPlayListener(PlayListener listener) {
        this.playListener = listener;
    }

    /** Set selected state and trigger animation */
    public void setSelected(boolean selected) {
        this.selected = selected;
        animateLift();
        repaint();
    }

    /** Whether selected */
    public boolean isSelected() {
        return selected;
    }

    /** Get card ID */
    public String getCardId() {
        return cardId;
    }

    /**
     * Get the card view model (for querying cardId, cardName, etc.).
     * Only instances created via the CardViewModel constructor have a value;
     * those created via static factory methods return null.
     */
    public CardViewModel getViewModel() {
        return viewModel;
    }

    /** Set enabled/disabled state (disabled cards are semi-transparent, cursor reverts to default) */
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(enabled
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        repaint();
    }

    // ==================== Animation system ====================

    /** Get target lift distance (based on selected/hovered/default state) */
    private float getTargetLift() {
        if (selected) return LIFT_SELECTED;
        if (hovered && isEnabled()) return LIFT_HOVER;
        return 0f;
    }

    /** Start the lift animation */
    private void animateLift() {
        if (!animTimer.isRunning()) animTimer.start();
    }

    // ==================== Custom painting ====================

    /**
     * Paint the card — seven layers: shadow, background, selection glow, type badge, icon, name, value, border.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        // Enable anti-aliasing and high-quality rendering
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        int liftPx = Math.round(currentLift);
        int cardTop = (getHeight() - CARD_H) - liftPx;  // Lift from bottom

        Shape cardShape = new RoundRectangle2D.Float(0, cardTop, CARD_W, CARD_H,
                CORNER_RADIUS, CORNER_RADIUS);
        g2.setClip(cardShape);  // Clip to card rounded rectangle

        // Paint layer by layer
        paintDropShadow(g2, cardTop);      // 1. Shadow
        paintCardBackground(g2, cardTop);   // 2. Background
        paintShineOverlay(g2, cardTop);     // 3. Top shine (white gradient)
        paintShineUnderlay(g2, cardTop);    // 4. Bottom fade (black gradient)
        if (selected) paintSelectionGlow(g2, cardTop);  // 5. Selection glow

        // Disabled state: overlay semi-transparent black mask
        if (!isEnabled()) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fill(cardShape);
        }

        g2.setClip(null);  // Remove clip
        paintTypeBadge(g2, cardTop);   // 6. Type badge
        paintIcon(g2, cardTop);        // 7. Type icon
        paintName(g2, cardTop);        // 8. Card name
        if (value > 0) paintValueBadge(g2, cardTop);  // 9. Value badge
        paintBorder(g2, cardTop);      // 10. Border

        g2.dispose();
    }

    /** Draw drop shadow (softer radial gradient approach) */
    private void paintDropShadow(Graphics2D g2, int cardTop) {
        int shadowOffset = selected ? 10 : (hovered ? 7 : 4);
        for (int i = shadowOffset; i > 0; i--) {
            float alpha = selected ? (i * 12f) : (hovered ? (i * 10f) : (i * 8f));
            g2.setColor(new Color(0, 0, 0, Math.min((int)alpha, 80)));
            g2.fill(new RoundRectangle2D.Float(i, cardTop + i, CARD_W, CARD_H,
                    CORNER_RADIUS, CORNER_RADIUS));
        }
        // Add a deeper soft glow under the card
        g2.setColor(new Color(0, 0, 0, selected ? 25 : (hovered ? 18 : 12)));
        g2.fill(new RoundRectangle2D.Float(-2, cardTop + shadowOffset + 2,
                CARD_W + 4, CARD_H + 2, CORNER_RADIUS + 4, CORNER_RADIUS + 4));
    }

    /** Draw card background (solid or gradient) */
    private void paintCardBackground(Graphics2D g2, int cardTop) {
        Shape shape = new RoundRectangle2D.Float(0, cardTop, CARD_W, CARD_H,
                CORNER_RADIUS, CORNER_RADIUS);

        // Wild cards use a rainbow gradient
        if ("WILD".equals(colorKey)) {
            float[] fractions = {0f, 0.33f, 0.66f, 1f};
            Color[] colors    = {
                    new Color(0xFF6B6B),  // Red
                    new Color(0xFFD93D),  // Yellow
                    new Color(0x6BCB77),  // Green
                    new Color(0x4D96FF)   // Blue
            };
            g2.setPaint(new LinearGradientPaint(0, cardTop, CARD_W, cardTop + CARD_H,
                    fractions, colors));
        } else {
            CardPalette palette = resolvePalette();
            if (palette != null && palette.gradient != null) {
                // Top-to-bottom gradient
                g2.setPaint(new GradientPaint(0, cardTop, palette.bg,
                        0, cardTop + CARD_H, palette.gradient));
            } else if (palette != null) {
                g2.setPaint(palette.bg);  // Solid color
            } else {
                g2.setPaint(Color.GRAY);  // Fallback gray
            }
        }
        g2.fill(shape);
    }

    /** Draw top shine overlay — white-to-transparent gradient on top 30% of card */
    private void paintShineOverlay(Graphics2D g2, int cardTop) {
        Shape clip = new RoundRectangle2D.Float(0, cardTop, CARD_W, CARD_H,
                CORNER_RADIUS, CORNER_RADIUS);
        g2.setClip(clip);
        int shineH = (int)(CARD_H * 0.30f);
        GradientPaint shine = new GradientPaint(
                0, cardTop, new Color(255, 255, 255, 55),
                0, cardTop + shineH, new Color(255, 255, 255, 0));
        g2.setPaint(shine);
        g2.fillRect(0, cardTop, CARD_W, shineH);
        g2.setClip(null);
    }

    /** Draw bottom fade underlay — black-to-transparent on bottom 20% for readability */
    private void paintShineUnderlay(Graphics2D g2, int cardTop) {
        Shape clip = new RoundRectangle2D.Float(0, cardTop, CARD_W, CARD_H,
                CORNER_RADIUS, CORNER_RADIUS);
        g2.setClip(clip);
        int fadeH = (int)(CARD_H * 0.20f);
        int fadeStart = cardTop + CARD_H - fadeH;
        GradientPaint fade = new GradientPaint(
                0, fadeStart, new Color(0, 0, 0, 0),
                0, cardTop + CARD_H, new Color(0, 0, 0, 80));
        g2.setPaint(fade);
        g2.fillRect(0, fadeStart, CARD_W, fadeH);
        g2.setClip(null);
    }

    /** Draw selection glow effect (gold radial gradient) */
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

    /** Draw the top-left type badge (e.g. "ACTION") */
    private void paintTypeBadge(Graphics2D g2, int cardTop) {
        String label = cardType;
        Font font = new Font("SansSerif", Font.BOLD, 8);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(label);
        int bx = 5, by = cardTop + 5;
        int bw = tw + 8, bh = fm.getHeight() + 2;
        // Semi-transparent black rounded background
        g2.setColor(new Color(0, 0, 0, 100));
        g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 6, 6));
        g2.setColor(Color.WHITE);
        g2.drawString(label, bx + 4, by + fm.getAscent() + 1);
    }

    /** Draw the central type icon (emoji with font fallback) */
    private void paintIcon(Graphics2D g2, int cardTop) {
        // Try emoji font, fall back to plain text symbols
        String icon = TYPE_ICONS.getOrDefault(cardType, "");
        Font emojiFont = new Font(AppTheme.FONT_EMOJI, Font.PLAIN, 28);
        // If emoji font not available, use fallback with text symbols
        if (!emojiFont.getFamily().equals(AppTheme.FONT_EMOJI) && !icon.isEmpty()) {
            Map<String, String> fallback = new HashMap<>();
            fallback.put("💰", "$");
            fallback.put("🏠", "H");
            fallback.put("⚡", "!");
            fallback.put("💸", "~");
            icon = fallback.getOrDefault(icon, icon);
            emojiFont = new Font(AppTheme.FONT_FALLBACK, Font.PLAIN, 24);
        }
        g2.setFont(emojiFont);
        FontMetrics fm = g2.getFontMetrics();
        int x = (CARD_W - fm.stringWidth(icon)) / 2;
        int y = cardTop + 32 + fm.getAscent();
        // Icon with shadow
        g2.setColor(AppTheme.SHADOW);
        g2.drawString(icon, x + 1, y + 2);
        g2.setColor(Color.WHITE);
        g2.drawString(icon, x, y);
    }

    /** Draw card name (supports auto line wrap) */
    private void paintName(Graphics2D g2, int cardTop) {
        CardPalette palette = resolvePalette();
        g2.setColor(palette != null ? palette.text : Color.WHITE);
        // Auto-adjust font size based on name length
        int fontSize = cardName.length() > 12 ? 9 : (cardName.length() > 8 ? 10 : 11);
        g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();
        String[] lines = wrapText(cardName, fm, CARD_W - 10);
        int lineH = fm.getHeight();
        int totalH = lines.length * lineH;
        int startY = cardTop + CARD_H - (value > 0 ? 32 : 16) - totalH;
        for (String line : lines) {
            int x = (CARD_W - fm.stringWidth(line)) / 2;
            // Text with shadow
            g2.setColor(new Color(0, 0, 0, 100));
            g2.drawString(line, x + 1, startY + 1);
            g2.setColor(palette != null ? palette.text : Color.WHITE);
            g2.drawString(line, x, startY);
            startY += lineH;
        }
    }

    /** Draw bottom value badge (money cards only, e.g. "$5M") */
    private void paintValueBadge(Graphics2D g2, int cardTop) {
        String label = "$" + value + "M";
        Font font = new Font("SansSerif", Font.BOLD, 12);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        int bw = fm.stringWidth(label) + 14;
        int bh = fm.getHeight() + 4;
        int bx = (CARD_W - bw) / 2;
        int by = cardTop + CARD_H - bh - 6;
        // Semi-transparent black rounded background + gold text
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 8, 8));
        g2.setColor(new Color(0xFFD700));
        g2.drawString(label, bx + 7, by + fm.getAscent() + 2);
    }

    /** Draw card border (selected=gold glow, default=dark border + inner highlight) */
    private void paintBorder(Graphics2D g2, int cardTop) {
        RoundRectangle2D.Float border = new RoundRectangle2D.Float(
                0.5f, cardTop + 0.5f, CARD_W - 1f, CARD_H - 1f,
                CORNER_RADIUS, CORNER_RADIUS);

        if (selected) {
            // Multi-layer gold glow border
            for (int i = 3; i > 0; i--) {
                g2.setColor(new Color(255, 215, 0, 60 * i));
                g2.setStroke(new BasicStroke(i * 2 + 1f));
                g2.draw(border);
            }
            g2.setColor(new Color(0xFFD700));
            g2.setStroke(new BasicStroke(2.5f));
            g2.draw(border);
            // Inner highlight
            g2.setColor(new Color(255, 255, 255, 20));
            g2.setStroke(new BasicStroke(0.8f));
            g2.draw(new RoundRectangle2D.Float(2.5f, cardTop + 2.5f,
                    CARD_W - 5f, CARD_H - 5f, CORNER_RADIUS - 2, CORNER_RADIUS - 2));
        } else {
            CardPalette palette = resolvePalette();
            g2.setColor(palette != null ? palette.border.darker() : Color.GRAY);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(border);
            // Inner bevel highlight
            g2.setColor(new Color(255, 255, 255, 18));
            g2.setStroke(new BasicStroke(0.6f));
            g2.draw(new RoundRectangle2D.Float(2f, cardTop + 2f,
                    CARD_W - 4f, CARD_H - 4f, CORNER_RADIUS - 2, CORNER_RADIUS - 2));
        }
    }

    /**
     * Resolve the current card's color palette.
     * Priority: colorKey → cardType → fallback gray.
     */
    private CardPalette resolvePalette() {
        CardPalette p = PALETTES.get(colorKey);
        if (p == null) p = PALETTES.get(cardType);
        if (p == null) p = PALETTES.get("NONE");
        return p;
    }

    /**
     * Text wrapping utility — splits a long name into at most two lines.
     * @param text original text
     * @param fm font metrics
     * @param maxWidth maximum width (pixels)
     * @return array of split lines (1-2 lines)
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

    // ==================== Static factory methods (convenience constructors) ====================

    /** Create a money card renderer */
    public static CardRenderer money(String cardId, int value) {
        return new CardRenderer(cardId, value + "M", "MONEY", "MONEY", value);
    }

    /** Create a property card renderer */
    public static CardRenderer property(String cardId, String name, String colorKey) {
        return new CardRenderer(cardId, name, "PROPERTY", colorKey, 0);
    }

    /** Create an action card renderer */
    public static CardRenderer action(String cardId, String name) {
        return new CardRenderer(cardId, name, "ACTION", "ACTION", 0);
    }

    /** Create a rent card renderer */
    public static CardRenderer rent(String cardId, String name, String colorKey) {
        return new CardRenderer(cardId, name, "RENT", colorKey, 0);
    }
}
