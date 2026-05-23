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

public class ActionHistoryPanel extends JPanel {
    private final JTextArea historyArea;
    private final Map<String, Color> playerColors;
    private final Color[] colors = {
            new Color(255, 140, 100),
            new Color(100, 255, 140),
            new Color(100, 180, 255),
            new Color(255, 255, 100),
            new Color(255, 140, 255),
            new Color(100, 255, 255)
    };
    private int colorIndex;
    private final SimpleDateFormat timeFormat;

    public ActionHistoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 35, 42));
        setBorder(new TitledBorder(
                BorderFactory.createLineBorder(new Color(60, 65, 75)),
                "Action History",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(200, 200, 200)
        ));

        playerColors = new HashMap<>();
        colorIndex = 0;
        timeFormat = new SimpleDateFormat("HH:mm:ss");

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

    public void updateActions(JsonArray actions) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < actions.size(); i++) {
            JsonObject action = actions.get(i).getAsJsonObject();
            String playerId = action.has("playerId") ? action.get("playerId").getAsString() : "";
            String nickname = action.has("playerNickname") ? action.get("playerNickname").getAsString() : "Unknown";
            String actionType = action.has("actionType") ? action.get("actionType").getAsString() : "";
            String target = action.has("targetPlayer") && !action.get("targetPlayer").isJsonNull() ? action.get("targetPlayer").getAsString() : "";
            int amount = action.has("amount") ? action.get("amount").getAsInt() : 0;
            long timestamp = action.has("timestamp") ? action.get("timestamp").getAsLong() : System.currentTimeMillis();

            String timeStr = timeFormat.format(new Date(timestamp));

            String actionDesc = formatAction(actionType, target, amount);

            sb.append(String.format("[%s] %s: %s", timeStr, nickname, actionDesc));
            sb.append("\n");
        }

        historyArea.setText(sb.toString());
        historyArea.setCaretPosition(0);
    }

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
                return "Charged " + (target.isEmpty() ? "" : target + " ") + amount + "M rent";
            case "RENT_ALL":
                return "Charged all players " + amount + "M rent";
            case "DEBT_COLLECTOR":
                return "Collected " + amount + "M from " + target;
            case "BIRTHDAY":
                return "Everyone pays " + amount + "M";
            case "DEAL_BREAKER":
                return "Stole complete set from " + target;
            case "PASS_GO":
                return "Drew 2 extra cards";
            case "DOUBLE_RENT":
                return "Next rent doubled";
            case "HOUSE":
                return "Built a house";
            case "HOTEL":
                return "Built a hotel";
            case "FORCED_DEAL":
                return "Swapped property with " + target;
            case "SLY_DEAL":
                return "Stole property from " + target;
            case "JUST_SAY_NO":
                return "Played Just Say No";
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
                return "WON THE GAME!";
            case "DRAW_EXTRA":
                return "Drew 2 extra cards";
            default:
                return actionType;
        }
    }
}