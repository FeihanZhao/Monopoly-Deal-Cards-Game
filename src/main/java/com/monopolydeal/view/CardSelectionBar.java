package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * CardSelectionBar
 *
 * A floating action bar that slides up from the bottom of the hand panel
 * when the player clicks a card, replacing the previous JOptionPane approach.
 *
 * Responsibilities:
 *  - Show the selected card's name and type
 *  - Offer contextually correct action buttons (Play / Bank / Select Target)
 *  - Show a target-player row when the card requires a target
 *  - Call back to GamePanel with (cardId, action, targetPlayerId) when confirmed
 *  - Hide itself on cancel or after a successful play
 *
 * Lifecycle (called from GamePanel):
 *
 *   // 1. Keep the bar's player list in sync whenever GamePanel updates players:
 *   selectionBar.updatePlayers(id → nickname map);
 *
 *   // 2. Show when a card is clicked:
 *   selectionBar.show(cardId, cardName, cardType);
 *
 *   // 3. Hide explicitly if needed (e.g. turn ends):
 *   selectionBar.hide();
 *
 * Callback signature:
 *   (cardId, action, targetPlayerId)
 *   targetPlayerId is null for actions that don't need a target.
 *
 * Supported action strings (match GameSession.handlePlayCard switch):
 *   "PLAY_MONEY"    – deposit card into bank
 *   "PLAY_PROPERTY" – place card in property zone
 *   "PLAY_ACTION"   – play action card (no target)
 *   "PLAY_ACTION_TARGETED" – play action card with a specific target
 *   "PLAY_RENT"     – play rent card (targeted or all)
 *   "PLAY_RENT_WILD"– wild rent: requires exactly one target
 */
public class CardSelectionBar extends JPanel {

    // Layout constants
    private static final int BAR_HEIGHT        = 64;
    private static final int CORNER_RADIUS     = 14;
    private static final int BUTTON_H          = 34;
    private static final int BUTTON_ARC        = 8;

    // Colours
    private static final Color BG_COLOR        = new Color(25, 25, 35, 230);
    private static final Color BORDER_COLOR    = new Color(255, 215, 0, 180);
    private static final Color BTN_CONFIRM     = new Color(34, 139, 34);
    private static final Color BTN_BANK        = new Color(30, 100, 180);
    private static final Color BTN_CANCEL      = new Color(120, 40, 40);
    private static final Color BTN_TARGET_IDLE = new Color(60, 60, 80);
    private static final Color BTN_TARGET_SEL  = new Color(180, 130, 0);
    private static final Color TEXT_PRIMARY    = Color.WHITE;
    private static final Color TEXT_MUTED      = new Color(180, 180, 180);

    // Card types that require a target player
    // These are the action strings whose payload must include targetPlayerId.
    private static final java.util.Set<String> TARGET_REQUIRED_TYPES =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "RENT", "ACTION"   // cardType values from CardInfo
            ));

    // Cards that target ALL opponents (no explicit pick needed)
    private static final java.util.Set<String> TARGET_ALL_NAMES =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "Birthday", "Debt Collector"
                    // Wild Rent and Rent cards handled separately by color
            ));

    // State
    private String selectedCardId   = null;
    private String selectedCardName = null;
    private String selectedCardType = null;  // CardType enum name
    private String selectedTargetId = null;

    /** id → nickname for all opponents; updated by GamePanel. */
    private final Map<String, String> opponentMap = new LinkedHashMap<>();

    // Callback
    /**
     * Fired when the player confirms a play.
     * Parameters: (cardId, actionString, targetPlayerId-or-null)
     */
    private BiConsumer<String, String> playCallback;   // (cardId+"|"+action, targetId)

    // Child components
    private JLabel    cardNameLabel;
    private JLabel    cardTypeLabel;
    private JPanel    actionRow;       // primary action buttons
    private JPanel    targetRow;       // target-player buttons (shown when needed)
    private JButton   confirmButton;
    private JButton   bankButton;
    private JButton   cancelButton;
    private final Map<String, JButton> targetButtons = new LinkedHashMap<>();


    // Constructor
    public CardSelectionBar() {
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        setVisible(false);
        setPreferredSize(new Dimension(0, BAR_HEIGHT));

        buildUI();
    }


    // Public API
    /**
     * Register the callback invoked when the player confirms a play.
     *
     * @param callback (cardId, actionString, targetPlayerId-or-null)
     *
     * Example in GamePanel:
     *   cardSelectionBar.setPlayCallback((cardId, action, targetId) -> {
     *       JsonObject payload = new JsonObject();
     *       payload.addProperty("cardId", cardId);
     *       payload.addProperty("action", action);
     *       if (targetId != null) payload.addProperty("targetPlayerId", targetId);
     *       client.sendMessage(MessageProtocol.MessageType.PLAY_CARD, payload.toString());
     *       cardSelectionBar.hide();
     *   });
     */
    public void setPlayCallback(TriConsumer callback) {
        this.playCallback = null;         // clear old BiConsumer
        this.triCallback  = callback;
    }

    @FunctionalInterface
    public interface TriConsumer {
        void accept(String cardId, String action, String targetId);
    }

    private TriConsumer triCallback;

    /**
     * Keep the opponent list in sync.  Call this from GamePanel.updatePlayerPanels()
     * every time the server sends a GAME_STATE_UPDATE.
     *
     * @param idToNickname map of opponent playerId → nickname
     *                     (must NOT include the local player)
     */
    public void updatePlayers(Map<String, String> idToNickname) {
        opponentMap.clear();
        opponentMap.putAll(idToNickname);
        rebuildTargetButtons();
    }

    /**
     * Show the bar for a specific card.  Called from GamePanel when a
     * CardRenderer fires its PlayListener.
     *
     * @param cardId   server-side card ID
     * @param cardName display name (e.g. "Deal Breaker")
     * @param cardType CardType enum name: "MONEY" | "PROPERTY" | "ACTION" | "RENT"
     */
    public void show(String cardId, String cardName, String cardType) {
        this.selectedCardId   = cardId;
        this.selectedCardName = cardName;
        this.selectedCardType = cardType;
        this.selectedTargetId = null;

        cardNameLabel.setText(cardName);
        cardTypeLabel.setText(cardType);

        configureActionButtons(cardType, cardName);
        configureTargetRow(cardType, cardName);
        resetTargetSelection();

        setVisible(true);
        revalidate();
        repaint();
    }

    /** Hide and reset the bar. */
    public void hide() {
        selectedCardId   = null;
        selectedCardName = null;
        selectedCardType = null;
        selectedTargetId = null;
        resetTargetSelection();
        setVisible(false);
    }

    public boolean isShowing() {
        return isVisible();
    }

    // UI construction
    private void buildUI() {
        // Wrapper with padding so the rounded background has breathing room.
        JPanel inner = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                // Rounded background
                g2.setColor(BG_COLOR);
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
                // Gold border
                g2.setColor(BORDER_COLOR);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(
                        0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f,
                        CORNER_RADIUS, CORNER_RADIUS));
                g2.dispose();
            }
        };
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(8, 14, 8, 14));

        // Left: card info labels
        JPanel infoPanel = new JPanel(new BorderLayout(0, 2));
        infoPanel.setOpaque(false);
        infoPanel.setPreferredSize(new Dimension(160, 0));

        cardNameLabel = new JLabel("—");
        cardNameLabel.setForeground(TEXT_PRIMARY);
        cardNameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        cardTypeLabel = new JLabel("—");
        cardTypeLabel.setForeground(TEXT_MUTED);
        cardTypeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        infoPanel.add(cardNameLabel, BorderLayout.CENTER);
        infoPanel.add(cardTypeLabel, BorderLayout.SOUTH);

        // Center: action + target rows
        JPanel centrePanel = new JPanel(new BorderLayout(0, 4));
        centrePanel.setOpaque(false);

        actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionRow.setOpaque(false);

        confirmButton = makeButton("✓ Play",        BTN_CONFIRM);
        bankButton    = makeButton("🏦 Bank",        BTN_BANK);
        cancelButton  = makeButton("✕ Cancel",      BTN_CANCEL);

        confirmButton.addActionListener(e -> onConfirm());
        bankButton   .addActionListener(e -> onBank());
        cancelButton .addActionListener(e -> hide());

        actionRow.add(confirmButton);
        actionRow.add(bankButton);
        actionRow.add(cancelButton);

        targetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        targetRow.setOpaque(false);
        targetRow.setVisible(false);

        centrePanel.add(actionRow, BorderLayout.NORTH);
        centrePanel.add(targetRow, BorderLayout.SOUTH);

        inner.add(infoPanel,   BorderLayout.WEST);
        inner.add(centrePanel, BorderLayout.CENTER);

        add(inner, BorderLayout.CENTER);
    }

    // Button factory
    private JButton makeButton(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? getBackground() : getBackground().darker());
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), BUTTON_ARC, BUTTON_ARC));
                g2.setColor(getForeground());
                FontMetrics fm = g2.getFontMetrics();
                int tx = (getWidth()  - fm.stringWidth(getText())) / 2;
                int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("SansSerif", Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(90, BUTTON_H));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Hover: slightly brighter background
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            final Color base = bg;
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(base.brighter());
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(base);
            }
        });

        return btn;
    }


    // Dynamic configuration based on card type
    /**
     * Show/hide action buttons based on what the card can do.
     *
     * Rules:
     *  MONEY   → only "Bank" makes sense; hide "Play" (there's nothing else to do)
     *  PROPERTY→ "Play" (place in property zone) + "Bank" (use as money)
     *  ACTION  → "Play" + "Bank"; "Play" may need a target (handled in targetRow)
     *  RENT    → "Play" + "Bank"; wild rent needs a target
     */
    private void configureActionButtons(String cardType, String cardName) {
        switch (cardType) {
            case "MONEY":
                confirmButton.setVisible(false);
                bankButton.setVisible(true);
                break;
            case "PROPERTY":
                confirmButton.setText("✓ Place");
                confirmButton.setVisible(true);
                bankButton.setVisible(true);
                break;
            case "ACTION":
            case "RENT":
                confirmButton.setText("✓ Play");
                confirmButton.setVisible(true);
                bankButton.setVisible(true);
                break;
            default:
                confirmButton.setVisible(true);
                bankButton.setVisible(true);
        }
        actionRow.revalidate();
    }

    /**
     * Show the target-player row only for cards that need a target.
     * "Birthday" and multi-target rent cards target ALL, so no picker needed.
     */
    private void configureTargetRow(String cardType, String cardName) {
        boolean needsTargetPicker =
                ("ACTION".equals(cardType) && !isTargetAllCard(cardName))
                        || "RENT".equals(cardType);   // wild rent always needs a target;
        // regular rent shown for convenience

        targetRow.setVisible(needsTargetPicker && !opponentMap.isEmpty());

        if (needsTargetPicker) {
            JLabel prompt = new JLabel("Target: ");
            prompt.setForeground(TEXT_MUTED);
            prompt.setFont(new Font("SansSerif", Font.PLAIN, 11));
            targetRow.add(prompt);
        }
    }

    /** True for action cards that automatically affect all opponents. */
    private boolean isTargetAllCard(String cardName) {
        return TARGET_ALL_NAMES.contains(cardName);
    }


    // Target buttons
    /**
     * Rebuild target buttons whenever the opponent list changes.
     * Called by updatePlayers() and internally after show().
     */
    private void rebuildTargetButtons() {
        // Remove old target buttons (keep the "Target: " label at index 0 if present)
        targetButtons.values().forEach(targetRow::remove);
        targetButtons.clear();

        for (Map.Entry<String, String> entry : opponentMap.entrySet()) {
            String id       = entry.getKey();
            String nickname = entry.getValue();

            JButton btn = makeButton(nickname, BTN_TARGET_IDLE);
            btn.setPreferredSize(new Dimension(
                    Math.max(70, nickname.length() * 8 + 16), BUTTON_H));

            btn.addActionListener(e -> selectTarget(id, btn));
            targetButtons.put(id, btn);
            targetRow.add(btn);
        }

        targetRow.revalidate();
        targetRow.repaint();
    }

    /** Highlight selected target, clear others. */
    private void selectTarget(String playerId, JButton selectedBtn) {
        selectedTargetId = playerId;
        targetButtons.values().forEach(b -> b.setBackground(BTN_TARGET_IDLE));
        selectedBtn.setBackground(BTN_TARGET_SEL);

        // Enable confirm once a target is chosen (for cards that require it).
        updateConfirmState();
    }

    private void resetTargetSelection() {
        selectedTargetId = null;
        targetButtons.values().forEach(b -> b.setBackground(BTN_TARGET_IDLE));
        updateConfirmState();
    }

    /**
     * Confirm button should be disabled until a target is chosen for cards
     * that strictly need one (e.g. Wild Rent, Sly Deal, Deal Breaker, Forced Deal).
     */
    private void updateConfirmState() {
        if (!confirmButton.isVisible()) return;

        boolean needsTarget = targetRow.isVisible()
                && isStrictTargetRequired(selectedCardName);

        confirmButton.setEnabled(!needsTarget || selectedTargetId != null);
    }

    /** Cards where a target MUST be explicitly chosen before confirming. */
    private boolean isStrictTargetRequired(String cardName) {
        if (cardName == null) return false;
        switch (cardName) {
            case "Wild Rent":
            case "Sly Deal":
            case "Deal Breaker":
            case "Forced Deal":
            case "Debt Collector":
                return true;
            default:
                return false;
        }
    }

    // Action handlers
    private void onConfirm() {
        if (selectedCardId == null) return;

        // Validate: strict-target cards need a chosen target.
        if (isStrictTargetRequired(selectedCardName) && selectedTargetId == null) {
            cardNameLabel.setText("← pick a target first");
            cardNameLabel.setForeground(new Color(255, 100, 100));
            javax.swing.Timer reset = new javax.swing.Timer(1200, e -> {
                cardNameLabel.setText(selectedCardName);
                cardNameLabel.setForeground(TEXT_PRIMARY);
            });
            reset.setRepeats(false);
            reset.start();
            return;
        }

        String action = resolveAction();
        if (triCallback != null) {
            triCallback.accept(selectedCardId, action, selectedTargetId);
        }
        hide();
    }

    private void onBank() {
        if (selectedCardId == null) return;
        if (triCallback != null) {
            triCallback.accept(selectedCardId, "PLAY_MONEY", null);
        }
        hide();
    }

    /**
     * Map the current card type + name to the action string GameSession expects.
     */
    private String resolveAction() {
        switch (selectedCardType) {
            case "MONEY":    return "PLAY_MONEY";
            case "PROPERTY": return "PLAY_PROPERTY";
            case "RENT":     return "PLAY_RENT";
            case "ACTION":   return "PLAY_ACTION";
            default:         return "PLAY_MONEY";
        }
    }

    // Painting – transparent background with rounded pill shape
    @Override
    protected void paintComponent(Graphics g) {
        // Deliberately empty: background is painted by the inner JPanel.
    }
}
