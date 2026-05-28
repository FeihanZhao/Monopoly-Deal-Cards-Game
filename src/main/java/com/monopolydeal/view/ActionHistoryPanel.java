package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Action history panel - displays game action logs
 *
 * Located in the right sidebar of the game panel, shows recent game actions as a text list.
 * Each record contains:
 * - Timestamp (HH:mm:ss format)
 * - Player nickname
 * - Action description (e.g., "Drew 3 cards", "Collected 4M rent from all players")
 *
 * Supported action types include: draw, play, discard, rent collection, action card effects,
 * house/hotel construction, disconnect/reconnect, game win, etc.
 *
 * Display format example:
 * [14:32:15] Player A: Collected 4M rent from all players
 * [14:32:10] Player A: Played rent card
 * [14:32:05] Player A: Drew 3 cards
 */
public class ActionHistoryPanel extends JPanel {
    /** History text area */
    private final JTextArea historyArea;
    /** Player color cache (different colors per playerId) */
    private final Map<String, Color> playerColors;
    /** Color rotation array */
    private final Color[] colors = {
            new Color(255, 140, 100),  // warm orange
            new Color(100, 255, 140),  // emerald green
            new Color(100, 180, 255),  // sky blue
            new Color(255, 255, 100),  // bright yellow
            new Color(255, 140, 255),  // magenta
            new Color(100, 255, 255)   // cyan
    };
    /** Current color index (for assigning colors to new players) */
    private int colorIndex;
    /** Time formatter (HH:mm:ss) */
    private final SimpleDateFormat timeFormat;

    /**
     * Constructor - creates dark theme text area
     */
    public ActionHistoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 35, 42));
        // Titled border
        setBorder(new TitledBorder(
                BorderFactory.createLineBorder(new Color(60, 65, 75)),
                "Action Log",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(200, 200, 200)
        ));

        playerColors = new HashMap<>();
        colorIndex = 0;
        timeFormat = new SimpleDateFormat("HH:mm:ss");

        // Read-only text area
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setBackground(new Color(30, 35, 42));
        historyArea.setForeground(new Color(200, 200, 200));
        historyArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(historyArea);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(30, 35, 42));

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * Updates action history display
     * Parses each action record from JSON array and formats for display
     *
     * @param actions action record JSON array
     */
    public void updateActions(JsonArray actions) {
        StringBuilder sb = new StringBuilder();

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
            long timestamp = action.has("timestamp") ?
                    action.get("timestamp").getAsLong() : System.currentTimeMillis();

            // Format time
            String timeStr = timeFormat.format(new Date(timestamp));

            // Format as readable description
            String actionDesc = formatAction(actionType, target, amount);

            // Build output line: [time] nickname: action description
            sb.append(String.format("[%s] %s: %s", timeStr, nickname, actionDesc));
            sb.append("\n");
        }

        historyArea.setText(sb.toString());
        historyArea.setCaretPosition(0);  // Scroll to top
    }

    /**
     * Converts action type to readable English description
     *
     * @param actionType action type (e.g., "RENT", "DEAL_BREAKER", etc.)
     * @param target target player nickname
     * @param amount amount involved
     * @return formatted English description text
     */
    private String formatAction(String actionType, String target, int amount) {
        switch (actionType) {
            case "DRAW":
                return "Drew cards";
            case "PLAY_MONEY":
                return "Played money card";
            case "PLAY_PROPERTY":
                return "Played property card";
            case "PLAY_RENT":
                return "Played rent card";
            case "PLAY_ACTION":
                return "Played action card";
            case "RENT":
                return "Collected " + amount + "M rent from " + (target.isEmpty() ? "" : target);
            case "RENT_ALL":
                return "Collected " + amount + "M rent from all players";
            case "DEBT_COLLECTOR":
                return "Collected " + amount + "M from " + target;
            case "BIRTHDAY":
                return "Everyone paid " + amount + "M";
            case "DEAL_BREAKER":
                return "Stole complete property set from " + target;
            case "PASS_GO":
                return "Drew 2 extra cards";
            case "DOUBLE_RENT":
                return "Next rent doubled";
            case "HOUSE":
                return "Built a house";
            case "HOTEL":
                return "Built a hotel";
            case "FORCED_DEAL":
                return "Exchanged property card with " + target;
            case "SLY_DEAL":
                return "Stole property card from " + target;
            case "JUST_SAY_NO":
                return "Used Just Say No";
            case "PAY":
                return "Paid " + amount + "M to " + target;
            case "PARTIAL_PAY":
                return "Paid " + amount + "M (partial) to " + target;
            case "DISCARD":
                return "Discarded a card";
            case "END_TURN":
                return "Ended turn";
            case "DISCONNECT":
                return "Disconnected";
            case "RECONNECT":
                return "Reconnected";
            case "WINNER":
                return "Won the game!";
            case "DRAW_EXTRA":
                return "Drew 2 extra cards";
            default:
                return actionType;
        }
    }
}