package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.*;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ActionHistoryPanel extends JPanel {
    private final JTextArea historyArea;
    private final Map<String, Color> playerColors;
    private final Color[] colors = {
            new Color(255, 100, 100),
            new Color(100, 255, 100),
            new Color(100, 100, 255),
            new Color(255, 255, 100),
            new Color(255, 100, 255)
    };
    private int colorIndex;

    public ActionHistoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(40, 40, 40));
        setBorder(new TitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Action History",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 12),
                Color.WHITE
        ));

        playerColors = new HashMap<>();
        colorIndex = 0;

        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setBackground(new Color(40, 40, 40));
        historyArea.setForeground(Color.WHITE);
        historyArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(historyArea);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(40, 40, 40));

        add(scrollPane, BorderLayout.CENTER);
    }

    public void updateActions(JsonArray actions) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < actions.size(); i++) {
            JsonObject action = actions.get(i).getAsJsonObject();
            String playerId = action.get("playerId").getAsString();
            String nickname = action.get("playerNickname").getAsString();
            String actionType = action.get("actionType").getAsString();
            String target = action.has("targetPlayer") ? action.get("targetPlayer").getAsString() : "";
            int amount = action.has("amount") ? action.get("amount").getAsInt() : 0;

            Color playerColor = getPlayerColor(playerId);

            sb.append(String.format("[%s] %s: %s",
                    formatTime(action), nickname, formatAction(actionType, target, amount)));
            sb.append("\n");
        }

        historyArea.setText(sb.toString());
    }

    private Color getPlayerColor(String playerId) {
        return playerColors.computeIfAbsent(playerId, k -> {
            Color color = colors[colorIndex % colors.length];
            colorIndex++;
            return color;
        });
    }

    private String formatTime(JsonObject action) {
        return "00:00";
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
            case "END_TURN":
                return "Ended turn";
            case "PAY":
                return "Paid " + amount + "M to " + target;
            case "DISCONNECT":
                return "Disconnected";
            case "RECONNECT":
                return "Reconnected";
            case "WINNER":
                return "Won the game!";
            default:
                return actionType;
        }
    }
}