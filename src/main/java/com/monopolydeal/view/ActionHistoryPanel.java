package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.text.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Action history panel - displays game action logs with colored player names
 *
 * Located in the right sidebar of the game panel, shows recent game actions as a text list.
 * Each player is assigned a consistent color so you can easily track who did what.
 *
 * Format: [HH:mm:ss] PlayerName: action description
 *                   ^^^^^^^^^^^ colored per-player
 */
public class ActionHistoryPanel extends JPanel {
    /** Styled text pane for colored text */
    private final JTextPane historyPane;
    /** Styled document for managing text styles */
    private final StyledDocument doc;
    /** Player color cache (consistent color per playerId) */
    private final Map<String, Color> playerColors;
    /** Player name cache (latest nickname per playerId) */
    private final Map<String, String> playerNames;
    /** Color rotation array — vibrant, distinct colors on dark background */
    private final Color[] colors = {
            new Color(255, 140, 100),  // warm orange
            new Color(100, 255, 140),  // emerald green
            new Color(100, 200, 255),  // sky blue
            new Color(255, 220, 80),   // bright yellow
            new Color(255, 130, 255),  // magenta
            new Color(80, 255, 255),   // cyan
            new Color(255, 170, 130),  // peach
            new Color(160, 255, 160),  // mint
    };
    /** Current color index for assigning colors to new players */
    private int colorIndex;
    /** Time formatter (HH:mm:ss) */
    private final SimpleDateFormat timeFormat;
    /** Base text color for description text */
    private static final Color TEXT_DESCRIPTION = new Color(200, 200, 210);
    /** Timestamp color (subtle) */
    private static final Color COLOR_TIME = new Color(130, 130, 150);
    /** Ongoing action (e.g. DRAW) gets a slightly dimmer color variant */
    private static final Color COLOR_DIM = new Color(160, 160, 170);

    /**
     * Constructor - creates dark theme styled text pane
     */
    public ActionHistoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 35, 42));

        // Titled border
        TitledBorder titledBorder = new TitledBorder(
                BorderFactory.createLineBorder(new Color(60, 65, 75)),
                "Action Log",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(200, 200, 200)
        );
        setBorder(titledBorder);

        playerColors = new HashMap<>();
        playerNames = new HashMap<>();
        colorIndex = 0;
        timeFormat = new SimpleDateFormat("HH:mm:ss");

        // Styled text pane — supports per-character colors
        historyPane = new JTextPane();
        historyPane.setEditable(false);
        historyPane.setBackground(new Color(30, 35, 42));
        historyPane.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        historyPane.setMargin(new Insets(5, 5, 5, 5));
        doc = historyPane.getStyledDocument();

        // Default paragraph style
        Style defaultStyle = StyleContext.getDefaultStyleContext()
                .getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setForeground(defaultStyle, TEXT_DESCRIPTION);
        StyleConstants.setFontSize(defaultStyle, 11);
        StyleConstants.setFontFamily(defaultStyle, "Segoe UI");

        JScrollPane scrollPane = new JScrollPane(historyPane);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(30, 35, 42));
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Updates action history display with colored player names
     *
     * @param actions action record JSON array
     */
    public void updateActions(JsonArray actions) {
        try {
            doc.remove(0, doc.getLength());

            for (int i = 0; i < actions.size(); i++) {
                JsonObject action = actions.get(i).getAsJsonObject();
                String playerId = action.has("playerId") ?
                        action.get("playerId").getAsString() : "";
                String nickname = action.has("playerNickname") ?
                        action.get("playerNickname").getAsString() : "Unknown";
                String actionType = action.has("actionType") ?
                        action.get("actionType").getAsString() : "";
                String target = action.has("targetPlayer") &&
                        !action.get("targetPlayer").isJsonNull() ?
                        action.get("targetPlayer").getAsString() : "";
                int amount = action.has("amount") ? action.get("amount").getAsInt() : 0;
                String details = action.has("details") && !action.get("details").isJsonNull() ?
                        action.get("details").getAsString() : "";
                long timestamp = action.has("timestamp") ?
                        action.get("timestamp").getAsLong() : System.currentTimeMillis();

                // Cache latest player nickname
                if (!playerId.isEmpty()) {
                    playerNames.put(playerId, nickname);
                }

                // Assign color for this player
                Color playerColor = getPlayerColor(playerId);

                // Format time
                String timeStr = timeFormat.format(new Date(timestamp));
                // Use details if available, otherwise fall back to formatAction
                String actionDesc = !details.isEmpty() ? details : formatAction(actionType, target, amount);

                // Build colored line
                // [time]
                appendStyled(String.format("[%s] ", timeStr), COLOR_TIME, false, false);
                // PlayerName
                appendStyled(nickname, playerColor, true, false);
                // : description
                appendStyled(": " + actionDesc + "\n", TEXT_DESCRIPTION, false, isHighlightAction(actionType));
            }

            // Auto-scroll to top (newest first)
            historyPane.setCaretPosition(0);
        } catch (BadLocationException e) {
            // Should not happen since we control the document
            historyPane.setText("Error updating action log");
        }
    }

    /** Append styled text to the document */
    private void appendStyled(String text, Color color, boolean bold, boolean highlight) throws BadLocationException {
        Style style = historyPane.addStyle("dynamic", null);
        StyleConstants.setForeground(style, color);
        StyleConstants.setFontFamily(style, "Segoe UI");
        StyleConstants.setFontSize(style, 11);
        StyleConstants.setBold(style, bold);
        if (highlight) {
            StyleConstants.setBackground(style, new Color(255, 215, 0, 20));
        }
        doc.insertString(doc.getLength(), text, style);
    }

    /** Whether this action type should have a subtle highlight */
    private boolean isHighlightAction(String actionType) {
        return "WINNER".equals(actionType) || "DEAL_BREAKER".equals(actionType)
                || "FORCED_DEAL".equals(actionType) || "SLY_DEAL".equals(actionType);
    }

    /**
     * Get or assign a consistent color for a player
     */
    private Color getPlayerColor(String playerId) {
        if (playerId == null || playerId.isEmpty()) return TEXT_DESCRIPTION;
        return playerColors.computeIfAbsent(playerId, k -> {
            Color c = colors[colorIndex % colors.length];
            colorIndex++;
            return c;
        });
    }

    /**
     * Converts action type to readable English description
     */
    private String formatAction(String actionType, String target, int amount) {
        switch (actionType) {
            case "DRAW":
                return "Drew cards";
            case "PLAY_MONEY":
                return "Played money card";
            case "PLAY_PROPERTY":
                return "Played property";
            case "PLAY_RENT":
                return "Played rent card";
            case "PLAY_ACTION":
                return "Played action card";
            case "RENT":
                return "Collected " + amount + "M rent from " + (target.isEmpty() ? "" : target);
            case "RENT_ALL":
                return "Collected " + amount + "M rent from all";
            case "DEBT_COLLECTOR":
                return "Collected " + amount + "M from " + target;
            case "BIRTHDAY":
                return "Collected " + amount + "M birthday gift";
            case "DEAL_BREAKER":
                return "Stole set from " + target;
            case "PASS_GO":
                return "Drew 2 extra cards (Pass Go)";
            case "DOUBLE_RENT":
                return "Double Rent activated";
            case "HOUSE":
                return "Built a house";
            case "HOTEL":
                return "Built a hotel";
            case "FORCED_DEAL":
                return "Swapped property with " + target;
            case "SLY_DEAL":
                return "Stole property from " + target;
            case "JUST_SAY_NO":
                return "Used Just Say No!";
            case "PAY":
                return "Paid " + amount + "M to " + target;
            case "PAYMENT_MADE":
                return "Paid " + amount + "M to " + target;
            case "PAYMENT_TIMEOUT":
                return "Auto-paid " + amount + "M to " + target;
            case "DISCARD":
                return "Discarded a card";
            case "END_TURN":
                return "Ended turn";
            case "DISCONNECT":
                return "Disconnected";
            case "RECONNECT":
                return "Reconnected";
            case "WINNER":
                return "★ Won the game! ★";
            case "DRAW_EXTRA":
                return "Drew 2 extra cards";
            case "FLIP_WILD":
                return "Changed wild property color";
            case "PASS_REACTION":
                return "Passed on Just Say No";
            case "ACTION_CANCELLED":
                return "Action was cancelled";
            case "ACTION_EXPIRED":
                return "Action expired";
            default:
                return actionType;
        }
    }
}
