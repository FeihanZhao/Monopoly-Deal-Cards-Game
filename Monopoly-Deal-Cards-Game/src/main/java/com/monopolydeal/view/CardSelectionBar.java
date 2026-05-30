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
 * When the player clicks a card in their hand, this panel slides out above the hand area showing:
 * 1. Card name and type
 * 2. Action buttons (Play/Bank/Cancel)
 * 3. Target player selection row (for card types that need a target)
 * 4. Property card selection row (for Sly Deal / Forced Deal)
 */
public class CardSelectionBar extends JPanel {

    // ==================== UI constants ====================

    private static final int BASE_HEIGHT       = 90;
    private static final int PROP_ROW_HEIGHT   = 48;
    private static final int CORNER_RADIUS     = 14;
    private static final int BUTTON_H          = 32;
    private static final int BUTTON_ARC        = 10;

    private static final Color BG_COLOR        = new Color(22, 24, 34, 240);
    private static final Color BORDER_COLOR    = AppTheme.GOLD;
    private static final Color BTN_CONFIRM     = new Color(39, 174, 96);
    private static final Color BTN_BANK        = new Color(41, 128, 185);
    private static final Color BTN_CANCEL      = new Color(140, 40, 40);
    private static final Color BTN_TARGET_IDLE = new Color(55, 55, 75);
    private static final Color BTN_TARGET_SEL  = new Color(180, 130, 0);
    private static final Color BTN_PROP_IDLE   = new Color(35, 75, 45);
    private static final Color BTN_PROP_SEL    = new Color(60, 180, 80);

    /** Card names that require a single target player */
    private static final java.util.Set<String> NEEDS_TARGET_NAMES =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "Sly Deal", "Forced Deal", "Deal Breaker",
                    "Debt Collector", "Wild Rent"
            ));

    /** Card names that need property card selection */
    private static final java.util.Set<String> NEEDS_PROPERTY_SELECTION =
            new java.util.HashSet<>(java.util.Arrays.asList(
                    "Sly Deal", "Forced Deal"
            ));

    // ==================== State fields ====================

    private String selectedCardId   = null;
    private String selectedCardName = null;
    private String selectedCardType = null;
    private String selectedTargetId = null;
    private String selectedTargetCardId = null;
    private String selectedMyPropertyId = null;
    private String selectedTheirPropertyId = null;

    private final Map<String, String> opponentMap = new LinkedHashMap<>();
    private final Map<String, List<String[]>> opponentPropertyMap = new LinkedHashMap<>();
    private final List<String[]> myPropertyList = new ArrayList<>();

    private BiConsumer<String, String> playCallback;

    // ==================== Sub-components ====================

    private JLabel    cardNameLabel;
    private JLabel    cardTypeLabel;
    private JPanel    innerPanel;
    private JPanel    actionRow;
    private JPanel    targetRow;
    private JPanel    myPropRow;
    private JPanel    theirPropRow;
    private JButton   confirmButton;
    private JButton   bankButton;
    private JButton   cancelButton;
    private final Map<String, JButton> targetButtons = new LinkedHashMap<>();
    private final Map<String, JButton> myPropButtons = new LinkedHashMap<>();
    private final Map<String, JButton> theirPropButtons = new LinkedHashMap<>();

    // ==================== Constructor ====================

    public CardSelectionBar() {
        setLayout(new BorderLayout(0, 0));
        setOpaque(false);
        buildUI();
        setVisible(false);
    }

    // ==================== Public API ====================

    public void setPlayCallback(BiConsumer<String, String> callback) {
        this.playCallback = callback;
    }

    public void updatePlayers(Map<String, String> idToNickname) {
        opponentMap.clear();
        opponentMap.putAll(idToNickname);
        rebuildTargetButtons();
    }

    public void updateOpponentProperties(Map<String, List<String[]>> idToProperties) {
        opponentPropertyMap.clear();
        opponentPropertyMap.putAll(idToProperties);
    }

    public void updateMyProperties(List<String[]> properties) {
        myPropertyList.clear();
        myPropertyList.addAll(properties);
    }

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

    // ==================== Getters ====================

    public String getSelectedTargetId() { return selectedTargetId; }
    public String getSelectedTargetCardId() { return selectedTargetCardId; }
    public String getSelectedMyPropertyId() { return selectedMyPropertyId; }
    public String getSelectedTheirPropertyId() { return selectedTheirPropertyId; }

    // ==================== UI construction ====================

    private void buildUI() {
        innerPanel = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                // Background with rounded corners
                g2.setColor(BG_COLOR);
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
                // Top gold border accent
                g2.setColor(new Color(255, 215, 0, 60));
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), 3, CORNER_RADIUS, CORNER_RADIUS));
                // Full border outline
                g2.setColor(BORDER_COLOR);
                g2.setStroke(new BasicStroke(1f));
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
        infoPanel.setPreferredSize(new Dimension(150, 0));

        cardNameLabel = new JLabel("—");
        cardNameLabel.setForeground(Color.WHITE);
        cardNameLabel.setFont(new Font("SansSerif", Font.BOLD, 14));

        cardTypeLabel = new JLabel("—");
        cardTypeLabel.setForeground(AppTheme.TEXT_DIM);
        cardTypeLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        infoPanel.add(cardNameLabel, BorderLayout.CENTER);
        infoPanel.add(cardTypeLabel, BorderLayout.SOUTH);

        // ===== Center: action buttons + target row + property rows =====
        JPanel centrePanel = new JPanel();
        centrePanel.setLayout(new BoxLayout(centrePanel, BoxLayout.Y_AXIS));
        centrePanel.setOpaque(false);

        // Action button row
        actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actionRow.setOpaque(false);

        confirmButton = makeButton("Play",   BTN_CONFIRM);
        bankButton    = makeButton("Bank",   BTN_BANK);
        cancelButton  = makeButton("Cancel", BTN_CANCEL);

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

        JLabel targetLabel = new JLabel("Target:");
        targetLabel.setForeground(AppTheme.TEXT_DIM);
        targetLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        targetRow.add(targetLabel);

        // My property selection row
        myPropRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        myPropRow.setOpaque(false);
        myPropRow.setVisible(false);

        // Their property selection row
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

    /** Create a styled action button with hover effects */
    private JButton makeButton(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                // Button background with brightness for enabled state
                Color base = isEnabled() ? getBackground() : getBackground().darker().darker();
                if (getModel().isRollover() && isEnabled()) {
                    base = base.brighter();
                }
                g2.setColor(base);
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), BUTTON_ARC, BUTTON_ARC));
                // Button inner highlight
                g2.setColor(new Color(255, 255, 255, 20));
                g2.fill(new RoundRectangle2D.Float(
                        2, 2, getWidth() - 4, getHeight() / 3, BUTTON_ARC, BUTTON_ARC));
                // Text
                g2.setColor(getForeground());
                g2.setFont(getFont());
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
        btn.setPreferredSize(new Dimension(85, BUTTON_H));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        return btn;
    }

    // ==================== Dynamic height ====================

    private void updateHeight() {
        int rows = 1;
        if (targetRow.isVisible()) rows++;
        if (myPropRow.isVisible()) rows++;
        if (theirPropRow.isVisible()) rows++;
        int h = BASE_HEIGHT + (rows - 1) * PROP_ROW_HEIGHT;
        setPreferredSize(new Dimension(0, h));
    }

    // ==================== Dynamic configuration ====================

    private void configureActionButtons(String cardType, String cardName) {
        switch (cardType) {
            case "MONEY":
                confirmButton.setVisible(false);
                bankButton.setVisible(true);
                break;
            case "PROPERTY":
                confirmButton.setText("Place");
                confirmButton.setVisible(true);
                bankButton.setVisible(true);
                break;
            case "ACTION":
            case "RENT":
                confirmButton.setText("Play");
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

    private void configurePropertyRows(String cardName) {
        if (!NEEDS_PROPERTY_SELECTION.contains(cardName)) {
            myPropRow.setVisible(false);
            theirPropRow.setVisible(false);
            return;
        }

        if (cardName.contains("Forced Deal")) {
            rebuildMyPropertyButtons();
            myPropRow.setVisible(!myPropertyList.isEmpty());
            theirPropRow.setVisible(false);
        } else if (cardName.contains("Sly Deal")) {
            myPropRow.setVisible(false);
            theirPropRow.setVisible(false);
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
                    Math.max(70, nickname.length() * 9 + 16), BUTTON_H));
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

        if (NEEDS_PROPERTY_SELECTION.contains(selectedCardName)) {
            rebuildPropertyRows(playerId);
        }
        updateHeight();
        updateConfirmState();
        revalidate();
        repaint();
    }

    // ==================== Property selection ====================

    private void rebuildPropertyRows(String targetPlayerId) {
        if (selectedCardName == null) return;

        if (selectedCardName.contains("Sly Deal")) {
            theirPropButtons.values().forEach(theirPropRow::remove);
            theirPropButtons.clear();
            theirPropRow.removeAll();

            List<String[]> props = opponentPropertyMap.get(targetPlayerId);
            JLabel stealLabel = new JLabel("Steal:");
            stealLabel.setForeground(AppTheme.TEXT_DIM);
            stealLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
            theirPropRow.add(stealLabel);

            if (props != null) {
                for (String[] p : props) {
                    boolean inCompleteSet = p.length > 2 && "true".equals(p[2]);
                    if (inCompleteSet) continue;

                    JButton btn = makePropertyButton(p[0], p[1], false);
                    btn.addActionListener(e -> selectTheirProperty(p[0], btn));
                    theirPropButtons.put(p[0], btn);
                    theirPropRow.add(btn);
                }
            }

            if (theirPropButtons.isEmpty()) {
                JLabel lbl = new JLabel("(no stealable properties)");
                lbl.setForeground(new Color(255, 120, 120));
                lbl.setFont(new Font("SansSerif", Font.ITALIC, 11));
                theirPropRow.add(lbl);
            }
            theirPropRow.setVisible(true);
            theirPropRow.revalidate();
            theirPropRow.repaint();

        } else if (selectedCardName.contains("Forced Deal")) {
            theirPropButtons.values().forEach(theirPropRow::remove);
            theirPropButtons.clear();
            theirPropRow.removeAll();

            JLabel theirLabel = new JLabel("Take:");
            theirLabel.setForeground(AppTheme.TEXT_DIM);
            theirLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
            theirPropRow.add(theirLabel);

            List<String[]> props = opponentPropertyMap.get(targetPlayerId);
            if (props != null) {
                for (String[] p : props) {
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

    private void rebuildMyPropertyButtons() {
        myPropButtons.values().forEach(myPropRow::remove);
        myPropButtons.clear();
        myPropRow.removeAll();

        JLabel yoursLabel = new JLabel("Give:");
        yoursLabel.setForeground(AppTheme.TEXT_DIM);
        yoursLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        myPropRow.add(yoursLabel);

        for (String[] p : myPropertyList) {
            JButton btn = makePropertyButton(p[0], p[1], true);
            btn.addActionListener(e -> selectMyProperty(p[0], btn));
            myPropButtons.put(p[0], btn);
            myPropRow.add(btn);
        }
        myPropRow.revalidate();
        myPropRow.repaint();
    }

    private JButton makePropertyButton(String cardId, String cardName, boolean isOwn) {
        Color bg = isOwn ? new Color(30, 55, 95) : BTN_PROP_IDLE;
        JButton btn = makeButton(cardName, bg);
        btn.setPreferredSize(new Dimension(
                Math.max(70, cardName.length() * 8 + 20), BUTTON_H));
        return btn;
    }

    private void selectMyProperty(String cardId, JButton btn) {
        selectedMyPropertyId = cardId;
        myPropButtons.values().forEach(b -> b.setBackground(new Color(30, 55, 95)));
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

        if (selectedCardName != null && selectedCardName.contains("Sly Deal")) {
            boolean hasProps = theirPropRow.isVisible() && !theirPropButtons.isEmpty();
            confirmButton.setEnabled(!hasProps || selectedTargetCardId != null);
        } else if (selectedCardName != null && selectedCardName.contains("Forced Deal")) {
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
            cardNameLabel.setForeground(Color.WHITE);
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
        // Transparent background
    }
}
