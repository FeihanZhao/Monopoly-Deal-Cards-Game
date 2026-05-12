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
 * CardRenderer
 *
 * A self-contained Swing component that renders a single Monopoly Deal card
 * with full visual styling: colour-coded background, card type icon, name,
 * monetary value badge, hover lift animation, and selected-glow highlight.
 *
 * Usage – two entry points:
 *
 *   // 1. From a JsonObject (arrives from GameState.CardInfo via WebSocket)
 *   CardRenderer card = new CardRenderer(cardJsonObject);
 *
 *   // 2. Programmatically (useful in tests or when you already have the fields)
 *   CardRenderer card = new CardRenderer("abc123", "Light Blue", "PROPERTY",
 *                                        "LIGHT_BLUE", 0);
 *
 * Attach a play-listener to react when the user clicks the card:
 *   card.setPlayListener(cardId -> handlePlayCard(cardId));
 *
 * Highlight the card as selected (lifted + gold glow):
 *   card.setSelected(true);
 *
 * Disable interaction (e.g. not your turn):
 *   card.setEnabled(false);
 */
public class CardRenderer extends JPanel {

    // ── Card dimensions ────────────────────────────────────────────────────────
    public static final int CARD_W = 90;
    public static final int CARD_H = 130;
    public static final int CORNER_RADIUS = 12;

    // ── Lift offsets (pixels the card moves upward) ────────────────────────────
    private static final int LIFT_HOVER    = 12;
    private static final int LIFT_SELECTED = 22;

    // ── Colour palette: cardColorKey → { background, border, text } ───────────
    /**
     * CardPalette holds the three colours needed to paint a card face.
     * 'gradient' is an optional secondary colour; when non-null the background
     * is painted as a top-to-bottom linear gradient from 'bg' to 'gradient'.
     */
    private static final class CardPalette {
        final Color bg;
        final Color gradient;   // null = solid fill
        final Color border;
        final Color text;

        CardPalette(Color bg, Color gradient, Color border, Color text) {
            this.bg       = bg;
            this.gradient = gradient;
            this.border   = border;
            this.text     = text;
        }

        /** Convenience constructor for solid-colour cards. */
        CardPalette(Color bg, Color border, Color text) {
            this(bg, null, border, text);
        }
    }

    /**
     * Maps every colour/type key that can arrive from the server to a palette.
     * Keys are the exact strings used in CardColor.name() and CardType.name().
     */
    private static final Map<String, CardPalette> PALETTES = new HashMap<>();

    static {
        // ── Property colours ───────────────────────────────────────────────────
        PALETTES.put("BROWN",
                new CardPalette(new Color(0x8B5E3C), new Color(0x5D3A1A),
                        new Color(0xFFFFFF)));
        PALETTES.put("LIGHT_BLUE",
                new CardPalette(new Color(0x87CEEB), new Color(0x4A9EC4),
                        new Color(0x1A1A1A)));
        PALETTES.put("PINK",
                new CardPalette(new Color(0xFF69B4), new Color(0xC2185B),
                        new Color(0xFFFFFF)));
        PALETTES.put("ORANGE",
                new CardPalette(new Color(0xFF8C00), new Color(0xE65100),
                        new Color(0xFFFFFF)));
        PALETTES.put("RED",
                new CardPalette(new Color(0xDC143C), new Color(0x8B0000),
                        new Color(0xFFFFFF)));
        PALETTES.put("YELLOW",
                new CardPalette(new Color(0xFFD700), new Color(0xB8860B),
                        new Color(0x1A1A1A)));
        PALETTES.put("GREEN",
                new CardPalette(new Color(0x228B22), new Color(0x145214),
                        new Color(0xFFFFFF)));
        PALETTES.put("BLUE",
                new CardPalette(new Color(0x00008B), new Color(0x000055),
                        new Color(0xFFFFFF)));
        PALETTES.put("PURPLE",
                new CardPalette(new Color(0x6A0DAD), new Color(0x3D0070),
                        new Color(0xFFFFFF)));
        PALETTES.put("BLACK",
                new CardPalette(new Color(0x2B2B2B), new Color(0x111111),
                        new Color(0xFFFFFF)));
        PALETTES.put("LIGHT_GREEN",
                new CardPalette(new Color(0x90EE90), new Color(0x4CAF50),
                        new Color(0x1A1A1A)));

        // ── Card types (used when no specific property colour is available) ────
        PALETTES.put("MONEY",
                new CardPalette(new Color(0x2E7D32), new Color(0x1B5E20),
                        new Color(0xFFFFFF)));
        PALETTES.put("ACTION",
                new CardPalette(new Color(0x6A1B9A), new Color(0x4A0072),
                        new Color(0xFFFFFF)));
        PALETTES.put("RENT",
                new CardPalette(new Color(0xBF360C), new Color(0x870000),
                        new Color(0xFFFFFF)));

        // ── Wild: rainbow gradient ─────────────────────────────────────────────
        // Handled as a special case in paintCardBackground(); palette entry
        // is provided as a fallback only.
        PALETTES.put("WILD",
                new CardPalette(new Color(0xFF6B6B), new Color(0x4D96FF),
                        new Color(0xFFFFFF)));

        // ── Default / unknown ──────────────────────────────────────────────────
        PALETTES.put("NONE",
                new CardPalette(Color.GRAY, Color.DARK_GRAY, Color.WHITE));
    }

    // ── Icon map: cardType → unicode symbol rendered at large size ─────────────
    private static final Map<String, String> TYPE_ICONS = new HashMap<>();

    static {
        TYPE_ICONS.put("MONEY",    "💵");
        TYPE_ICONS.put("PROPERTY", "🏠");
        TYPE_ICONS.put("ACTION",   "⚡");
        TYPE_ICONS.put("RENT",     "💸");
    }

    // ── Card data ──────────────────────────────────────────────────────────────
    private final String cardId;
    private final String cardName;
    private final String cardType;   // "MONEY" | "PROPERTY" | "ACTION" | "RENT"
    private final String colorKey;   // CardColor.name()
    private final int    value;      // monetary value (0 for non-money cards)

    // ── Interaction state ──────────────────────────────────────────────────────
    private boolean selected  = false;
    private boolean hovered   = false;

    // ── Animation ─────────────────────────────────────────────────────────────
    /** Current vertical lift in pixels; animated toward targetLift. */
    private float   currentLift = 0f;
    private Timer   animTimer;

    // ── Callback ───────────────────────────────────────────────────────────────
    /** Called with this card's ID when the user clicks a playable card. */
    @FunctionalInterface
    public interface PlayListener {
        void onPlay(String cardId);
    }

    private PlayListener playListener;

    // ── Cached rendering assets ────────────────────────────────────────────────
    /** Off-screen buffer; rebuilt only when size changes. */
    private BufferedImage offscreenBuffer;

    // ═══════════════════════════════════════════════════════════════════════════
    // Constructors
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Primary constructor – build from raw fields.
     *
     * @param cardId   unique ID used to identify this card when calling back
     * @param cardName display name shown on the card face
     * @param cardType one of "MONEY", "PROPERTY", "ACTION", "RENT"
     * @param colorKey CardColor enum name (e.g. "LIGHT_BLUE") or type name
     * @param value    monetary value; pass 0 for non-money cards
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

    /**
     * Convenience constructor – build directly from a JsonObject whose fields
     * match GameState.CardInfo (cardId, cardName, cardType, color, value).
     */
    public CardRenderer(JsonObject cardJson) {
        this(
                cardJson.get("cardId").getAsString(),
                cardJson.get("cardName").getAsString(),
                cardJson.get("cardType").getAsString(),
                cardJson.get("color").getAsString(),
                cardJson.has("value") ? cardJson.get("value").getAsInt() : 0
        );
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Initialisation
    // ═══════════════════════════════════════════════════════════════════════════

    private void initComponent() {
        // Fixed preferred size; the card never grows or shrinks.
        setPreferredSize(new Dimension(CARD_W, CARD_H + LIFT_SELECTED + 4));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());

        // Transparent so the lift animation doesn't leave artefacts on the
        // parent panel's background.
        setOpaque(false);

        // ── Hover detection ────────────────────────────────────────────────────
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

        // ── Smooth lift animation timer ────────────────────────────────────────
        // Fires every 16 ms (~60 fps); moves currentLift 20% toward targetLift.
        animTimer = new Timer(16, e -> {
            float target = getTargetLift();
            float delta  = target - currentLift;
            if (Math.abs(delta) < 0.5f) {
                currentLift = target;
                ((Timer) e.getSource()).stop();
            } else {
                currentLift += delta * 0.25f;
            }
            repaint();
        });

        // Change cursor to indicate interactivity.
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Public API
    // ═══════════════════════════════════════════════════════════════════════════

    /** Attach a listener that fires when this card is clicked. */
    public void setPlayListener(PlayListener listener) {
        this.playListener = listener;
    }

    /**
     * Mark or unmark this card as selected (highlighted with a gold glow and
     * lifted higher than the hover state).
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
        animateLift();
        repaint();
    }

    public boolean isSelected() {
        return selected;
    }

    /** Returns the card's unique server-side identifier. */
    public String getCardId() {
        return cardId;
    }

    // ── Override setEnabled so the cursor changes and the card dims ────────────
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(enabled
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        repaint();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Animation helpers
    // ═══════════════════════════════════════════════════════════════════════════

    private float getTargetLift() {
        if (selected) return LIFT_SELECTED;
        if (hovered && isEnabled()) return LIFT_HOVER;
        return 0f;
    }

    private void animateLift() {
        if (!animTimer.isRunning()) animTimer.start();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Painting
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * All painting is done here.  We translate the graphics context upward by
     * currentLift so the card appears to float; the component bounds are tall
     * enough to accommodate the maximum lift without clipping.
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        // Translate upward so the card lifts from its baseline.
        int liftPx = Math.round(currentLift);
        int cardTop = (getHeight() - CARD_H) - liftPx;

        // Clip to the card rectangle so we don't paint outside.
        Shape cardShape = new RoundRectangle2D.Float(
                0, cardTop, CARD_W, CARD_H, CORNER_RADIUS, CORNER_RADIUS);
        g2.setClip(cardShape);

        // ── 1. Drop shadow ─────────────────────────────────────────────────────
        paintDropShadow(g2, cardTop);

        // ── 2. Card background ─────────────────────────────────────────────────
        paintCardBackground(g2, cardTop);

        // ── 3. Selected glow overlay ───────────────────────────────────────────
        if (selected) paintSelectionGlow(g2, cardTop);

        // ── 4. Disabled dim overlay ────────────────────────────────────────────
        if (!isEnabled()) {
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fill(cardShape);
        }

        // Remove clip so text can paint inside without artifacts.
        g2.setClip(null);

        // ── 5. Card type label (top-left badge) ────────────────────────────────
        paintTypeBadge(g2, cardTop);

        // ── 6. Centre icon ─────────────────────────────────────────────────────
        paintIcon(g2, cardTop);

        // ── 7. Card name ───────────────────────────────────────────────────────
        paintName(g2, cardTop);

        // ── 8. Value badge (bottom, money cards only) ──────────────────────────
        if (value > 0) paintValueBadge(g2, cardTop);

        // ── 9. Gold border when selected ───────────────────────────────────────
        paintBorder(g2, cardTop);

        g2.dispose();
    }

    // ── Shadow ─────────────────────────────────────────────────────────────────
    private void paintDropShadow(Graphics2D g2, int cardTop) {
        int shadowOffset = selected ? 8 : (hovered ? 6 : 3);
        int shadowAlpha  = selected ? 120 : (hovered ? 100 : 70);

        for (int i = shadowOffset; i > 0; i--) {
            float alpha = shadowAlpha * ((float) (shadowOffset - i + 1) / shadowOffset);
            g2.setColor(new Color(0, 0, 0, (int) alpha));
            g2.fill(new RoundRectangle2D.Float(
                    i, cardTop + i, CARD_W, CARD_H, CORNER_RADIUS, CORNER_RADIUS));
        }
    }

    // ── Background (gradient or wild rainbow) ──────────────────────────────────
    private void paintCardBackground(Graphics2D g2, int cardTop) {
        Shape shape = new RoundRectangle2D.Float(
                0, cardTop, CARD_W, CARD_H, CORNER_RADIUS, CORNER_RADIUS);

        if ("WILD".equals(colorKey)) {
            // Rainbow diagonal gradient for wild property/rent cards.
            float[] fractions = {0f, 0.33f, 0.66f, 1f};
            Color[] colors    = {
                    new Color(0xFF6B6B),
                    new Color(0xFFD93D),
                    new Color(0x6BCB77),
                    new Color(0x4D96FF)
            };
            g2.setPaint(new LinearGradientPaint(
                    0, cardTop, CARD_W, cardTop + CARD_H, fractions, colors));
        } else {
            CardPalette palette = resolvePalette();
            if (palette.gradient != null) {
                g2.setPaint(new GradientPaint(
                        0, cardTop,            palette.bg,
                        0, cardTop + CARD_H,   palette.gradient));
            } else {
                g2.setPaint(palette.bg);
            }
        }
        g2.fill(shape);
    }

    // ── Golden glow overlay when selected ─────────────────────────────────────
    private void paintSelectionGlow(Graphics2D g2, int cardTop) {
        // Inner radial glow: semi-transparent golden highlight in the centre.
        RadialGradientPaint glow = new RadialGradientPaint(
                new Point2D.Float(CARD_W / 2f, cardTop + CARD_H / 2f),
                CARD_W * 0.7f,
                new float[]{0f, 1f},
                new Color[]{new Color(255, 215, 0, 60), new Color(255, 215, 0, 0)}
        );
        g2.setPaint(glow);
        g2.fill(new RoundRectangle2D.Float(
                0, cardTop, CARD_W, CARD_H, CORNER_RADIUS, CORNER_RADIUS));
    }

    // ── Type badge (top-left corner) ───────────────────────────────────────────
    private void paintTypeBadge(Graphics2D g2, int cardTop) {
        String label = cardType;   // "MONEY", "PROPERTY", etc.
        Font  font   = new Font("SansSerif", Font.BOLD, 8);
        g2.setFont(font);

        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(label);

        int bx = 5, by = cardTop + 5;
        int bw = tw + 8, bh = fm.getHeight() + 2;

        // Badge pill background.
        g2.setColor(new Color(0, 0, 0, 100));
        g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 6, 6));

        g2.setColor(Color.WHITE);
        g2.drawString(label, bx + 4, by + fm.getAscent() + 1);
    }

    // ── Centre emoji icon ──────────────────────────────────────────────────────
    private void paintIcon(Graphics2D g2, int cardTop) {
        String icon = TYPE_ICONS.getOrDefault(cardType, "🃏");

        // Emoji rendering via a large font.
        Font emojiFont = new Font("Segoe UI Emoji", Font.PLAIN, 28);
        g2.setFont(emojiFont);
        FontMetrics fm = g2.getFontMetrics();

        int x = (CARD_W - fm.stringWidth(icon)) / 2;
        int y = cardTop + 30 + fm.getAscent();

        // Subtle text shadow.
        g2.setColor(new Color(0, 0, 0, 80));
        g2.drawString(icon, x + 1, y + 2);
        g2.setColor(Color.WHITE);
        g2.drawString(icon, x, y);
    }

    // ── Card name (centre, below icon) ────────────────────────────────────────
    private void paintName(Graphics2D g2, int cardTop) {
        CardPalette palette = resolvePalette();
        g2.setColor(palette.text);

        // Shrink font if the name is long.
        int fontSize = cardName.length() > 12 ? 9 : (cardName.length() > 8 ? 10 : 11);
        g2.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics fm = g2.getFontMetrics();

        // Word-wrap into at most two lines.
        String[] lines = wrapText(cardName, fm, CARD_W - 10);
        int lineH = fm.getHeight();
        int totalH = lines.length * lineH;
        int startY = cardTop + CARD_H - (value > 0 ? 32 : 16) - totalH;

        for (String line : lines) {
            int x = (CARD_W - fm.stringWidth(line)) / 2;
            // Shadow.
            g2.setColor(new Color(0, 0, 0, 100));
            g2.drawString(line, x + 1, startY + 1);
            g2.setColor(palette.text);
            g2.drawString(line, x, startY);
            startY += lineH;
        }
    }

    // ── Value badge (bottom-centre, money cards) ───────────────────────────────
    private void paintValueBadge(Graphics2D g2, int cardTop) {
        String label = "$" + value + "M";
        Font   font  = new Font("SansSerif", Font.BOLD, 12);
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        int bw = fm.stringWidth(label) + 14;
        int bh = fm.getHeight() + 4;
        int bx = (CARD_W - bw) / 2;
        int by = cardTop + CARD_H - bh - 6;

        // Dark pill background.
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fill(new RoundRectangle2D.Float(bx, by, bw, bh, 8, 8));

        g2.setColor(new Color(0xFFD700));   // Gold text for money value.
        g2.drawString(label, bx + 7, by + fm.getAscent() + 2);
    }

    // ── Outer border ───────────────────────────────────────────────────────────
    private void paintBorder(Graphics2D g2, int cardTop) {
        RoundRectangle2D.Float border = new RoundRectangle2D.Float(
                0.5f, cardTop + 0.5f,
                CARD_W - 1f, CARD_H - 1f,
                CORNER_RADIUS, CORNER_RADIUS);

        if (selected) {
            // Multi-pass gold glow border.
            for (int i = 3; i > 0; i--) {
                g2.setColor(new Color(255, 215, 0, 60 * i));
                g2.setStroke(new BasicStroke(i * 2 + 1f));
                g2.draw(border);
            }
            g2.setColor(new Color(0xFFD700));
            g2.setStroke(new BasicStroke(2.5f));
        } else {
            CardPalette palette = resolvePalette();
            g2.setColor(palette.border.darker());
            g2.setStroke(new BasicStroke(1.5f));
        }
        g2.draw(border);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Resolves the colour palette for this card.
     * Priority: colorKey (e.g. "LIGHT_BLUE") → cardType → NONE fallback.
     */
    private CardPalette resolvePalette() {
        CardPalette p = PALETTES.get(colorKey);
        if (p == null) p = PALETTES.get(cardType);
        if (p == null) p = PALETTES.get("NONE");
        return p;
    }

    /**
     * Naïve word-wrapper: splits on spaces and keeps lines under maxWidth.
     * Returns at most 2 lines; excess words are appended to the second line.
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

        return line2.length() == 0
                ? new String[]{line1.toString()}
                : new String[]{line1.toString(), line2.toString()};
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Static factory methods (convenience)
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Creates a CardRenderer for a money card.
     *
     * Example:
     *   CardRenderer card = CardRenderer.money("id1", 5);
     *   // Renders a "$5M" green money card.
     */
    public static CardRenderer money(String cardId, int value) {
        return new CardRenderer(cardId, value + "M", "MONEY", "MONEY", value);
    }

    /**
     * Creates a CardRenderer for a property card.
     *
     * @param colorKey CardColor enum name, e.g. "LIGHT_BLUE"
     *
     * Example:
     *   CardRenderer card = CardRenderer.property("id2", "Light Blue", "LIGHT_BLUE");
     */
    public static CardRenderer property(String cardId, String name, String colorKey) {
        return new CardRenderer(cardId, name, "PROPERTY", colorKey, 0);
    }

    /**
     * Creates a CardRenderer for an action card.
     *
     * Example:
     *   CardRenderer card = CardRenderer.action("id3", "Deal Breaker");
     */
    public static CardRenderer action(String cardId, String name) {
        return new CardRenderer(cardId, name, "ACTION", "ACTION", 0);
    }

    /**
     * Creates a CardRenderer for a rent card.
     *
     * @param colorKey e.g. "WILD" for wild rent, or a paired colour key
     */
    public static CardRenderer rent(String cardId, String name, String colorKey) {
        return new CardRenderer(cardId, name, "RENT", colorKey, 0);
    }
}