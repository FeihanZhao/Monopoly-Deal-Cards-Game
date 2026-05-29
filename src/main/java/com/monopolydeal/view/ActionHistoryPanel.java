package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Action history panel — displays the game action log as a color-coded list.
 *
 * Each record is shown as a single line containing:
 * - Gray timestamp (HH:mm:ss)
 * - Colored player nickname (each player gets a fixed color)
 * - White action description
 *
 * Auto-scrolls to the latest record, but won't force-scroll when the user has manually
 * scrolled up to review history.
 */
public class ActionHistoryPanel extends JPanel {

    /** List data model */
    private final DefaultListModel<ActionEntry> listModel;
    /** Action record list */
    private final JList<ActionEntry> actionList;
    /** Player color cache (assigns colors by player nickname) */
    private final Map<String, Color> playerColors;
    /** Color wheel */
    private static final Color[] COLORS = {
            new Color(255, 140, 100),
            new Color(100, 255, 140),
            new Color(100, 180, 255),
            new Color(255, 215, 0),
            new Color(255, 140, 255),
            new Color(100, 255, 255),
    };
    private int colorIndex;

    /** Time formatter */
    private final SimpleDateFormat timeFormat;

    // ==================== Inner data class ====================

    /** A single action record */
    private static class ActionEntry {
        final String timeStr;
        final String nickname;
        final String actionDesc;

        ActionEntry(String timeStr, String nickname, String actionDesc) {
            this.timeStr   = timeStr;
            this.nickname  = nickname;
            this.actionDesc = actionDesc;
        }
    }

    // ==================== Custom renderer ====================

    /** Action record list item renderer — gray time + colored player name + white description */
    private class ActionRenderer extends JPanel implements ListCellRenderer<ActionEntry> {
        private final JLabel label;

        ActionRenderer() {
            setLayout(new BorderLayout());
            setOpaque(false);
            label = new JLabel();
            label.setOpaque(false);
            label.setFont(new Font("SansSerif", Font.PLAIN, 11));
            label.setBorder(new EmptyBorder(1, 4, 1, 4));
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ActionEntry> list, ActionEntry value, int index,
                boolean isSelected, boolean cellHasFocus) {

            if (value == null) {
                label.setText("");
                return this;
            }

            // Build color-coded text: time(gray) | nickname(colored) : action(white)
            Color playerColor = getPlayerColor(value.nickname);
            String html = String.format(
                    "<html><span style='color:#999999'>[%s]</span> "
                            + "<span style='color:rgb(%d,%d,%d)'>%s</span>"
                            + "<span style='color:#CCCCCC'>: %s</span></html>",
                    value.timeStr,
                    playerColor.getRed(), playerColor.getGreen(), playerColor.getBlue(),
                    escapeHtml(value.nickname),
                    escapeHtml(value.actionDesc));

            label.setText(html);
            setBackground(isSelected ? new Color(60, 70, 90) : null);

            return this;
        }

        /** Escape HTML special characters */
        private String escapeHtml(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    // ==================== Constructor ====================

    public ActionHistoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 35, 42));
        setBorder(new TitledBorder(
                BorderFactory.createLineBorder(new Color(60, 65, 75)),
                "Action Log",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12),
                new Color(200, 200, 200)
        ));

        playerColors = new HashMap<>();
        colorIndex   = 0;
        timeFormat   = new SimpleDateFormat("HH:mm:ss");

        listModel  = new DefaultListModel<>();
        actionList = new JList<>(listModel);
        actionList.setCellRenderer(new ActionRenderer());
        actionList.setBackground(new Color(30, 35, 42));
        actionList.setSelectionBackground(new Color(60, 70, 90));
        actionList.setSelectionForeground(Color.WHITE);
        actionList.setFixedCellHeight(18);
        actionList.setVisibleRowCount(0);

        JScrollPane scrollPane = new JScrollPane(actionList);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(30, 35, 42));

        add(scrollPane, BorderLayout.CENTER);
    }

    // ==================== Public API ====================

    /**
     * Update action history — called by GamePanel on every GAME_STATE_UPDATE.
     * @param actions action record JSON array
     */
    public void updateActions(JsonArray actions) {
        // Determine whether the user is currently viewing history (not at bottom = scrolled up)
        boolean autoScroll = isScrolledToBottom();

        listModel.clear();

        for (int i = 0; i < actions.size(); i++) {
            JsonObject action = actions.get(i).getAsJsonObject();
            String nickname = action.has("playerNickname")
                    ? action.get("playerNickname").getAsString() : "Unknown";
            String actionType = action.has("actionType")
                    ? action.get("actionType").getAsString() : "";
            String target = action.has("targetPlayer") && !action.get("targetPlayer").isJsonNull()
                    ? action.get("targetPlayer").getAsString() : "";
            int amount = action.has("amount") ? action.get("amount").getAsInt() : 0;
            long timestamp = action.has("timestamp")
                    ? action.get("timestamp").getAsLong() : System.currentTimeMillis();

            String timeStr = timeFormat.format(new Date(timestamp));
            String actionDesc = formatAction(actionType, target, amount);

            listModel.addElement(new ActionEntry(timeStr, nickname, actionDesc));
        }

        // Auto-scroll to bottom (only if user was already at bottom)
        if (autoScroll && listModel.getSize() > 0) {
            actionList.ensureIndexIsVisible(listModel.getSize() - 1);
        }
    }

    /** Check whether the scrollbar is at the bottom */
    private boolean isScrolledToBottom() {
        JScrollPane sp = null;
        Container parent = actionList.getParent();
        while (parent != null) {
            if (parent instanceof JScrollPane) { sp = (JScrollPane) parent; break; }
            parent = parent.getParent();
        }
        if (sp == null) return true;
        JScrollBar bar = sp.getVerticalScrollBar();
        int current = bar.getValue();
        int extent  = bar.getVisibleAmount();
        int max     = bar.getMaximum();
        return current + extent >= max - extent;
    }

    // ==================== Player color assignment ====================

    private Color getPlayerColor(String nickname) {
        return playerColors.computeIfAbsent(nickname, k -> {
            Color c = COLORS[colorIndex % COLORS.length];
            colorIndex++;
            return c;
        });
    }

    // ==================== Action description formatting ====================

    private String formatAction(String actionType, String target, int amount) {
        switch (actionType) {
            case "DRAW":          return "drew cards";
            case "PLAY_MONEY":    return "played money card";
            case "PLAY_PROPERTY": return "played property card";
            case "PLAY_RENT":     return "played rent card";
            case "PLAY_ACTION":   return "played action card";
            case "RENT":          return "charged " + (target.isEmpty() ? "" : target + " ") + amount + "M rent";
            case "RENT_ALL":      return "charged all players " + amount + "M rent";
            case "DEBT_COLLECTOR": return "collected " + amount + "M from " + target;
            case "BIRTHDAY":       return "everyone pays " + amount + "M";
            case "DEAL_BREAKER":   return "stole " + target + "'s complete property set";
            case "PASS_GO":        return "drew 2 extra cards";
            case "DOUBLE_RENT":    return "next rent is doubled";
            case "HOUSE":          return "built a house";
            case "HOTEL":          return "built a hotel";
            case "FORCED_DEAL":    return "swapped property with " + target;
            case "SLY_DEAL":       return "stole " + target + "'s property card";
            case "JUST_SAY_NO":    return "used Just Say No";
            case "PAY":            return "paid " + amount + "M to " + target;
            case "PARTIAL_PAY":    return "paid " + amount + "M (partial) to " + target;
            case "DISCARD":        return "discarded a card";
            case "END_TURN":       return "ended turn";
            case "DISCONNECT":     return "disconnected";
            case "RECONNECT":      return "reconnected";
            case "WINNER":         return "won the game!";
            case "DRAW_EXTRA":     return "drew 2 extra cards";
            default:               return actionType;
        }
    }
}
