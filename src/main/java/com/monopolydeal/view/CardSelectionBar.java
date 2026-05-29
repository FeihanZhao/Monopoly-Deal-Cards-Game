package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Card selection bar — a floating action bar that appears when a hand card is clicked.
 *
 * Replaces the previous JOptionPane-based interaction with a smoother game experience.
 * When the player clicks a card in their hand, this panel slides out above the hand area showing:
 * 1. Card name and type
 * 2. Action buttons (Play/Bank/Cancel)
 * 3. Target player selection row (for card types that need a target)
 * 4. Property card selection row (for Sly Deal / Forced Deal — appears after target selection)
 *
 * Supported card operations:
 * - Money cards → can only be banked ("Bank" button)
 * - Property cards → can be placed in property zone ("Place" button) or banked
 * - Action cards → can be played ("Play" button) or banked (target selection shown when needed)
 * - Rent cards → can be played or banked
 *
 * Card types requiring target selection: rent cards, specific action cards (Sly Deal, Forced Deal,
 * Deal Breaker, Debt Collector)
 *
 * Sly Deal flow: select target → property row appears with stealable cards → pick one → click Play
 * Forced Deal flow: select target → two property rows appear (yours + theirs) → pick one from each → click Play
 * Deal Breaker flow: select target → click Play (steals entire set, no property selection needed)
 *
 * Lifecycle (managed by GamePanel):
 * 1. updatePlayers() — sync player list
 * 2. updateOpponentProperties() — sync opponent property zone details
 * 3. show() — display the action bar
 * 4. dismiss() — hide the action bar
 */
public class CardSelectionBar extends JPanel {

    // ==================== UI constants ====================

    /** Action bar base height */
    private static final int BASE_HEIGHT       = 100;
    /** Extra height per property row */
    private static final int PROP_ROW_HEIGHT   = 50;
    /** Action bar corner radius */
    private static final int CORNER_RADIUS     = 14;
    /** Action button height */
    private static final int BUTTON_H          = 34;
    /** Action button corner radius */
    private static final int BUTTON_ARC        = 8;

    /** Background color (dark semi-transparent) */
    private static final Color BG_COLOR        = new Color(25, 25, 35, 230);
    /** Border color (gold) */
    private static final Color BORDER_COLOR    = AppTheme.GOLD;
    /** Confirm button color (green) */
    private static final Color BTN_CONFIRM     = new Color(34, 139, 34);
    /** Bank button color (blue) */
    private static final Color BTN_BANK        = new Color(30, 100, 180);
    /** Cancel button color (red) */
    private static final Color BTN_CANCEL      = AppTheme.RED_DARK;
    /** Target button default color */
    private static final Color BTN_TARGET_IDLE = AppTheme.PURPLE_ACCENT.darker();
    /** Target button selected color (gold) */
    private static final Color BTN_TARGET_SEL  = new Color(180, 130, 0);
    /** Property button color */
    private static final Color BTN_PROP_IDLE   = AppTheme.TABLE_GREEN;
    /** Property button selected color */
    private static final Color BTN_PROP_SEL    = AppTheme.GREEN_GLOW;
    /** Primary text color */
    private static final Color TEXT_PRIMARY    = AppTheme.TEXT_PRIMARY;
    /** Muted text color */
    private static final Color TEXT_MUTED      = AppTheme.TEXT_DIM;

    /** Card names that require a single target player */
    private static final java.util.Set<String> NEEDS_TARGET_NAMES =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "Sly Deal", "Forced Deal", "Deal Breaker",
                    "Debt Collector", "Wild Rent"
            ));

    /** Card names that need property card selection (not Deal Breaker — it steals a full set) */
    private static final java.util.Set<String> NEEDS_PROPERTY_SELECTION =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "Sly Deal", "Forced Deal"
            ));

    // ==================== State fields ====================

    /** Currently selected card ID */
    private String selectedCardId   = null;
    /** Currently selected card name */
    private String selectedCardName = null;
    /** Currently selected card type */
    private String selectedCardType = null;
    /** Selected target player ID */
    private String selectedTargetId = null;
    /** Selected target property card ID (Sly Deal) */
    private String selectedTargetCardId = null;
    /** Selected own property card ID (Forced Deal) */
    private String selectedMyPropertyId = null;
    /** Selected their property card ID (Forced Deal) */
    private String selectedTheirPropertyId = null;

    /** Opponent player map id → nickname (synced by GamePanel) */
    private final Map<String, String> opponentMap = new LinkedHashMap<>();
    /** Opponent property cards map playerId → list of {cardId, cardName, isInCompleteSet} */
    private final Map<String, List<String[]>> opponentPropertyMap = new LinkedHashMap<>();
    /** Own property cards list of {cardId, cardName} */
    private final List<String[]> myPropertyList = new ArrayList<>();

    /** Action confirm callback (cardId, action, jsonPayload) */
    private BiConsumer<String, String> playCallback;

    // ==================== Sub-components ====================

    /** Card name label */
    private JLabel    cardNameLabel;
    /** Card type label */
    private JLabel    cardTypeLabel;
    /** Inner content panel */
    private JPanel    innerPanel;
    /** Action button row */
    private JPanel    actionRow;
    /** Target player selection row */
    private JPanel    targetRow;
    /** Property selection row (Sly Deal: steal target; Forced Deal: my property) */
    private JPanel    myPropRow;
    /** Their property selection row (Forced Deal only) */
    private JPanel    theirPropRow;
    /** Confirm button (Play/Place) */
    private JButton   confirmButton;
    /** Bank button */
    private JButton   bankButton;
    /** Cancel button */
    private JButton   cancelButton;
    /** Target player button map id → button */
    private final Map<String, JButton> targetButtons = new LinkedHashMap<>();
    /** Property card button map (myPropRow) id → button */
    private final Map<String, JButton> myPropButtons = new LinkedHashMap<>();
    /** Property card button map (theirPropRow) id → button */
    private final Map<String, JButton> theirPropButtons = new LinkedHashMap<>();

    // ==================== Constructor ====================

    /** Constructor — build the action bar UI */
    public CardSelectionBar() {
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        buildUI();
        setVisible(false);
    }

    // ==================== Public API ====================

    /**
     * Set the action confirm callback.
     * Called as (cardId, action) — GamePanel reads property selections via getters.
     */
    public void setPlayCallback(BiConsumer<String, String> callback) {
        this.playCallback = callback;
    }

    /**
     * Sync the opponent player list.
     * Called by GamePanel on every GAME_STATE_UPDATE.
     */
    public void updatePlayers(Map<String, String> idToNickname) {
        opponentMap.clear();
        opponentMap.putAll(idToNickname);
        rebuildTargetButtons();
    }

    /**
     * Sync opponent property zone details.
     * Called by GamePanel on every GAME_STATE_UPDATE.
     * @param idToProperties map playerId → list of [cardId, cardName, isInCompleteSet]
     */
    public void updateOpponentProperties(Map<String, List<String[]>> idToProperties) {
        opponentPropertyMap.clear();
        opponentPropertyMap.putAll(idToProperties);
    }

    /**
     * Sync own property zone details.
     * @param properties list of [cardId, cardName]
     */
    public void updateMyProperties(List<String[]> properties) {
        myPropertyList.clear();
        myPropertyList.addAll(properties);
    }

    /**
     * Show the action bar for the specified card.
     */
    public void show(String cardId, String cardName, String cardType) {
        this.selectedCardId   = cardId;
        this.selectedCardName = cardName;
        this.selectedCardType = cardType;
        this.selectedTargetId = null;
        this.selectedTargetCardId = null;
        this.selectedMyPropertyId = null;
        this.selectedTheirPropertyId = null;

        cardNameLabel.setText(cardName);
        cardTypeLabel.setText(cardType);

        configureActionButtons(cardType, cardName);
        configureTargetRow(cardType, cardName);
        configurePropertyRows(cardName);
        resetTargetSelection();
        resetPropertySelections();
        updateHeight();

        setVisible(true);
        revalidate();
        repaint();
    }

    /** Hide and reset the action bar */
    public void dismiss() {
        if (!isVisible()) return;
        selectedCardId   = null;
        selectedCardName = null;
        selectedCardType = null;
        selectedTargetId = null;
        selectedTargetCardId = null;
        selectedMyPropertyId = null;
        selectedTheirPropertyId = null;
        setVisible(false);
    }

    // ==================== Getters for GamePanel ====================

    public String getSelectedTargetId() { return selectedTargetId; }
    public String getSelectedTargetCardId() { return selectedTargetCardId; }
    public String getSelectedMyPropertyId() { return selectedMyPropertyId; }
    public String getSelectedTheirPropertyId() { return selectedTheirPropertyId; }

    // ==================== UI construction ====================

    /** Build the internal layout of the action bar */
    private void buildUI() {
        innerPanel = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_COLOR);
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
                g2.setColor(BORDER_COLOR);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(
                        0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f,
                        CORNER_RADIUS, CORNER_RADIUS));
                g2.dispose();
            }
        };
        innerPanel.setOpaque(false);
        innerPanel.setBorder(new EmptyBorder(8, 14, 8, 14));

        // ===== Left: card info =====
        JPanel infoPanel = new JPanel(new BorderLayout(0, 2));
        infoPanel.setOpaque(false);
        infoPanel.setPreferredSize(new Dimension(160, 0));

        cardNameLabel = new JLabel("—");
        cardNameLabel.setForeground(TEXT_PRIMARY);
        cardNameLabel.setFont(new Font(AppTheme.FONT_MAIN, Font.BOLD, 14));

        cardTypeLabel = new JLabel("—");
        cardTypeLabel.setForeground(TEXT_MUTED);
        cardTypeLabel.setFont(new Font(AppTheme.FONT_MAIN, Font.PLAIN, 11));

        infoPanel.add(cardNameLabel, BorderLayout.CENTER);
        infoPanel.add(cardTypeLabel, BorderLayout.SOUTH);

        // ===== Center: action buttons + target row + property rows =====
        JPanel centrePanel = new JPanel();
        centrePanel.setLayout(new BoxLayout(centrePanel, BoxLayout.Y_AXIS));
        centrePanel.setOpaque(false);

        // Action button row
        actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionRow.setOpaque(false);

        confirmButton = makeButton(" Play",   BTN_CONFIRM);
        bankButton    = makeButton(" Bank",   BTN_BANK);
        cancelButton  = makeButton(" Cancel", BTN_CANCEL);

        confirmButton.addActionListener(e -> onConfirm());
        bankButton   .addActionListener(e -> onBank());
        cancelButton .addActionListener(e -> dismiss());

        actionRow.add(confirmButton);
        actionRow.add(bankButton);
        actionRow.add(cancelButton);

        // Target selection row
        targetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        targetRow.setOpaque(false);
        targetRow.setVisible(false);

        JLabel targetLabel = new JLabel("Target: ");
        targetLabel.setForeground(TEXT_MUTED);
        targetLabel.setFont(new Font(AppTheme.FONT_MAIN, Font.PLAIN, 11));
        targetRow.add(targetLabel);

        // My property selection row (Forced Deal: own card to give; Sly Deal: not used)
        myPropRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        myPropRow.setOpaque(false);
        myPropRow.setVisible(false);

        // Their property selection row (Sly Deal: card to steal; Forced Deal: their card)
        theirPropRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        theirPropRow.setOpaque(false);
        theirPropRow.setVisible(false);

        centrePanel.add(actionRow);
        centrePanel.add(targetRow);
        centrePanel.add(myPropRow);
        centrePanel.add(theirPropRow);

        innerPanel.add(infoPanel,   BorderLayout.WEST);
        innerPanel.add(centrePanel, BorderLayout.CENTER);

        add(innerPanel, BorderLayout.CENTER);
    }

    /** Create a uniformly styled button */
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
        btn.setFont(new Font(AppTheme.FONT_MAIN, Font.BOLD, 12));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(90, BUTTON_H));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

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

    // ==================== Dynamic height ====================

    private void updateHeight() {
        int rows = 1; // action row always visible
        if (targetRow.isVisible()) rows++;
        if (myPropRow.isVisible()) rows++;
        if (theirPropRow.isVisible()) rows++;
        int h = BASE_HEIGHT + (rows - 1) * PROP_ROW_HEIGHT;
        setPreferredSize(new Dimension(0, h));
    }

    // ==================== Dynamic button configuration ====================

    private void configureActionButtons(String cardType, String cardName) {
        switch (cardType) {
            case "MONEY":
                confirmButton.setVisible(false);
                bankButton.setVisible(true);
                break;
            case "PROPERTY":
                confirmButton.setText(" Place");
                confirmButton.setVisible(true);
                bankButton.setVisible(true);
                break;
            case "ACTION":
            case "RENT":
                confirmButton.setText(" Play");
                confirmButton.setVisible(true);
                bankButton.setVisible(true);
                break;
            default:
                confirmButton.setVisible(true);
                bankButton.setVisible(true);
        }
        actionRow.revalidate();
    }

    private void configureTargetRow(String cardType, String cardName) {
        boolean needsTargetPicker = NEEDS_TARGET_NAMES.contains(cardName);
        targetRow.setVisible(needsTargetPicker && !opponentMap.isEmpty());
    }

    /**
     * Configure property selection row visibility and labels based on card name.
     */
    private void configurePropertyRows(String cardName) {
        if (!NEEDS_PROPERTY_SELECTION.contains(cardName)) {
            myPropRow.setVisible(false);
            theirPropRow.setVisible(false);
            return;
        }

        if (cardName.contains("Forced Deal")) {
            // Forced Deal: player picks one of their own and one of the target's
            rebuildMyPropertyButtons();
            myPropRow.setVisible(!myPropertyList.isEmpty());
            theirPropRow.setVisible(false); // shown after target selection
        } else if (cardName.contains("Sly Deal")) {
            // Sly Deal: player only picks target's property (shown after target selection)
            myPropRow.setVisible(false);
            theirPropRow.setVisible(false); // shown after target selection
        }
    }

    // ==================== Target selection ====================

    private void rebuildTargetButtons() {
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

    private void selectTarget(String playerId, JButton selectedBtn) {
        selectedTargetId = playerId;
        targetButtons.values().forEach(b -> b.setBackground(BTN_TARGET_IDLE));
        selectedBtn.setBackground(BTN_TARGET_SEL);

        // After target selection, update property rows
        if (NEEDS_PROPERTY_SELECTION.contains(selectedCardName)) {
            rebuildPropertyRows(playerId);
        }
        updateHeight();
        updateConfirmState();
        revalidate();
        repaint();
    }

    // ==================== Property selection ====================

    /** Rebuild property rows after target selection */
    private void rebuildPropertyRows(String targetPlayerId) {
        if (selectedCardName == null) return;

        if (selectedCardName.contains("Sly Deal")) {
            // Show stealable properties from target (exclude complete sets)
            theirPropButtons.values().forEach(theirPropRow::remove);
            theirPropButtons.clear();

            List<String[]> props = opponentPropertyMap.get(targetPlayerId);
            if (props != null) {
                boolean first = true;
                for (String[] p : props) {
                    boolean inCompleteSet = p.length > 2 && "true".equals(p[2]);
                    if (inCompleteSet) continue; // Can't steal from complete sets

                    if (first) {
                        // Add label
                        JLabel lbl = new JLabel("Steal: ");
                        lbl.setForeground(TEXT_MUTED);
                        lbl.setFont(new Font(AppTheme.FONT_MAIN, Font.PLAIN, 11));
                        theirPropRow.add(lbl);
                        first = false;
                    }
                    JButton btn = makePropertyButton(p[0], p[1], false);
                    btn.addActionListener(e -> selectTheirProperty(p[0], btn));
                    theirPropButtons.put(p[0], btn);
                    theirPropRow.add(btn);
                }
            }

            boolean hasProps = !theirPropButtons.isEmpty();
            if (!hasProps) {
                JLabel lbl = new JLabel("(no stealable properties)");
                lbl.setForeground(new Color(255, 120, 120));
                lbl.setFont(new Font(AppTheme.FONT_MAIN, Font.ITALIC, 11));
                theirPropRow.add(lbl);
            }
            theirPropRow.setVisible(true);
            theirPropRow.revalidate();
            theirPropRow.repaint();

        } else if (selectedCardName.contains("Forced Deal")) {
            // Show their properties
            theirPropButtons.values().forEach(theirPropRow::remove);
            theirPropButtons.clear();

            List<String[]> props = opponentPropertyMap.get(targetPlayerId);
            if (props != null) {
                boolean first = true;
                for (String[] p : props) {
                    if (first) {
                        JLabel lbl = new JLabel("Their: ");
                        lbl.setForeground(TEXT_MUTED);
                        lbl.setFont(new Font(AppTheme.FONT_MAIN, Font.PLAIN, 11));
                        theirPropRow.add(lbl);
                        first = false;
                    }
                    JButton btn = makePropertyButton(p[0], p[1], false);
                    btn.addActionListener(e -> selectTheirProperty(p[0], btn));
                    theirPropButtons.put(p[0], btn);
                    theirPropRow.add(btn);
                }
            }
            theirPropRow.setVisible(!theirPropButtons.isEmpty());
            theirPropRow.revalidate();
            theirPropRow.repaint();
        }
    }

    /** Rebuild own property buttons (Forced Deal) */
    private void rebuildMyPropertyButtons() {
        myPropButtons.values().forEach(myPropRow::remove);
        myPropButtons.clear();

        boolean first = true;
        for (String[] p : myPropertyList) {
            if (first) {
                JLabel lbl = new JLabel("Yours: ");
                lbl.setForeground(TEXT_MUTED);
                lbl.setFont(new Font(AppTheme.FONT_MAIN, Font.PLAIN, 11));
                myPropRow.add(lbl);
                first = false;
            }
            JButton btn = makePropertyButton(p[0], p[1], true);
            btn.addActionListener(e -> selectMyProperty(p[0], btn));
            myPropButtons.put(p[0], btn);
            myPropRow.add(btn);
        }
        myPropRow.revalidate();
        myPropRow.repaint();
    }

    private JButton makePropertyButton(String cardId, String cardName, boolean isOwn) {
        Color bg = isOwn ? new Color(30, 60, 100) : BTN_PROP_IDLE;
        JButton btn = makeButton(cardName, bg);
        btn.setPreferredSize(new Dimension(
                Math.max(70, cardName.length() * 7 + 20), BUTTON_H));
        return btn;
    }

    private void selectMyProperty(String cardId, JButton btn) {
        selectedMyPropertyId = cardId;
        myPropButtons.values().forEach(b -> b.setBackground(new Color(30, 60, 100)));
        btn.setBackground(BTN_PROP_SEL);
        updateConfirmState();
    }

    private void selectTheirProperty(String cardId, JButton btn) {
        if (selectedCardName != null && selectedCardName.contains("Sly Deal")) {
            selectedTargetCardId = cardId;
        } else {
            selectedTheirPropertyId = cardId;
        }
        theirPropButtons.values().forEach(b -> b.setBackground(BTN_PROP_IDLE));
        btn.setBackground(BTN_PROP_SEL);
        updateConfirmState();
    }

    private void resetTargetSelection() {
        selectedTargetId = null;
        targetButtons.values().forEach(b -> b.setBackground(BTN_TARGET_IDLE));
        theirPropRow.setVisible(false);
        updateHeight();
        updateConfirmState();
    }

    private void resetPropertySelections() {
        selectedTargetCardId = null;
        selectedMyPropertyId = null;
        selectedTheirPropertyId = null;
    }

    // ==================== Confirm state ====================

    private void updateConfirmState() {
        if (confirmButton == null || !confirmButton.isVisible()) return;

        boolean needsTarget = targetRow.isVisible()
                && isStrictTargetRequired(selectedCardName);

        if (needsTarget && selectedTargetId == null) {
            confirmButton.setEnabled(false);
            return;
        }

        // Check property selection requirements
        if (selectedCardName != null && selectedCardName.contains("Sly Deal")) {
            // Sly Deal: must select a property if target has stealable ones
            boolean hasProps = theirPropRow.isVisible() && !theirPropButtons.isEmpty();
            confirmButton.setEnabled(!hasProps || selectedTargetCardId != null);
        } else if (selectedCardName != null && selectedCardName.contains("Forced Deal")) {
            // Forced Deal: must select one from each side
            boolean needMy = myPropRow.isVisible() && !myPropButtons.isEmpty();
            boolean needTheir = theirPropRow.isVisible() && !theirPropButtons.isEmpty();
            boolean myOk = !needMy || selectedMyPropertyId != null;
            boolean theirOk = !needTheir || selectedTheirPropertyId != null;
            confirmButton.setEnabled(myOk && theirOk);
        } else {
            confirmButton.setEnabled(true);
        }
    }

    private boolean isStrictTargetRequired(String cardName) {
        return cardName != null && NEEDS_TARGET_NAMES.contains(cardName);
    }

    // ==================== Action handling ====================

    private void onConfirm() {
        if (selectedCardId == null) return;

        if (isStrictTargetRequired(selectedCardName) && selectedTargetId == null) {
            flashMessage("Select a target first");
            return;
        }

        // Validate property selections
        if (selectedCardName != null && selectedCardName.contains("Sly Deal")) {
            boolean hasProps = theirPropRow.isVisible() && !theirPropButtons.isEmpty();
            if (hasProps && selectedTargetCardId == null) {
                flashMessage("Select a property to steal");
                return;
            }
        }
        if (selectedCardName != null && selectedCardName.contains("Forced Deal")) {
            if (myPropRow.isVisible() && !myPropButtons.isEmpty()
                    && selectedMyPropertyId == null) {
                flashMessage("Select your property to trade");
                return;
            }
            if (theirPropRow.isVisible() && !theirPropButtons.isEmpty()
                    && selectedTheirPropertyId == null) {
                flashMessage("Select their property to trade");
                return;
            }
        }

        if (playCallback != null) {
            playCallback.accept(selectedCardId, resolveAction());
        }
        dismiss();
    }

    private void flashMessage(String msg) {
        cardNameLabel.setText(msg);
        cardNameLabel.setForeground(new Color(255, 100, 100));
        javax.swing.Timer reset = new javax.swing.Timer(1200, e -> {
            cardNameLabel.setText(selectedCardName);
            cardNameLabel.setForeground(TEXT_PRIMARY);
        });
        reset.setRepeats(false);
        reset.start();
    }

    private void onBank() {
        if (selectedCardId == null) return;
        if (playCallback != null) {
            playCallback.accept(selectedCardId, "PLAY_MONEY");
        }
        dismiss();
    }

    private String resolveAction() {
        switch (selectedCardType) {
            case "MONEY":    return "PLAY_MONEY";
            case "PROPERTY": return "PLAY_PROPERTY";
            case "RENT":     return "PLAY_RENT";
            case "ACTION":   return "PLAY_ACTION";
            default:         return "PLAY_MONEY";
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Transparent background; drawing handled by inner panel
    }
}
