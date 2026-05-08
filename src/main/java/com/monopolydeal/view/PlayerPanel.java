package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import com.google.gson.JsonObject;

public class PlayerPanel extends JPanel {
    private final String playerId;

    private JLabel nicknameLabel;
    private JLabel statusLabel;
    private JLabel handCountLabel;
    private JLabel bankTotalLabel;
    private JLabel completeSetsLabel;
    private JLabel playsLabel;
    private JPanel propertyPanel;

    public PlayerPanel(String playerId) {
        this.playerId = playerId;

        setLayout(new BorderLayout());
        setBackground(new Color(50, 50, 50));
        setBorder(new LineBorder(new Color(100, 100, 100), 1));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        createLeftPanel();
        createCenterPanel();
        createRightPanel();
    }

    private void createLeftPanel() {
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(new Color(50, 50, 50));
        leftPanel.setBorder(new EmptyBorder(5, 10, 5, 10));
        leftPanel.setPreferredSize(new Dimension(200, 0));

        nicknameLabel = new JLabel("Player");
        nicknameLabel.setForeground(Color.WHITE);
        nicknameLabel.setFont(new Font("Arial", Font.BOLD, 14));

        statusLabel = new JLabel("");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 10));

        leftPanel.add(nicknameLabel, BorderLayout.NORTH);
        leftPanel.add(statusLabel, BorderLayout.SOUTH);

        add(leftPanel, BorderLayout.WEST);
    }

    private void createCenterPanel() {
        JPanel centerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        centerPanel.setBackground(new Color(50, 50, 50));

        handCountLabel = new JLabel("Hand: 0");
        handCountLabel.setForeground(Color.LIGHT_GRAY);

        bankTotalLabel = new JLabel("Bank: 0M");
        bankTotalLabel.setForeground(new Color(255, 215, 0));

        completeSetsLabel = new JLabel("Sets: 0");
        completeSetsLabel.setForeground(new Color(100, 255, 100));

        playsLabel = new JLabel("Plays: 0");
        playsLabel.setForeground(Color.LIGHT_GRAY);

        centerPanel.add(handCountLabel);
        centerPanel.add(bankTotalLabel);
        centerPanel.add(completeSetsLabel);
        centerPanel.add(playsLabel);

        add(centerPanel, BorderLayout.CENTER);
    }

    private void createRightPanel() {
        propertyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        propertyPanel.setBackground(new Color(50, 50, 50));
        propertyPanel.setPreferredSize(new Dimension(300, 0));

        add(propertyPanel, BorderLayout.EAST);
    }

    public void updateFromJson(JsonObject data) {
        String nickname = data.get("nickname").getAsString();
        boolean isActive = data.get("isActive").getAsBoolean();
        boolean isConnected = data.get("connected").getAsBoolean();
        int handCount = data.get("handCount").getAsInt();
        int bankTotal = data.get("bankTotal").getAsInt();
        int completeSets = data.get("completeSets").getAsInt();
        int remainingPlays = data.has("remainingPlays") ? data.get("remainingPlays").getAsInt() : 0;

        nicknameLabel.setText(nickname);

        if (!isConnected) {
            statusLabel.setText("Disconnected");
            statusLabel.setForeground(Color.RED);
            setBackground(new Color(80, 30, 30));
        } else if (isActive) {
            statusLabel.setText("Current Turn");
            statusLabel.setForeground(new Color(255, 215, 0));
            setBackground(new Color(60, 60, 40));
        } else {
            statusLabel.setText("");
            setBackground(new Color(50, 50, 50));
        }

        handCountLabel.setText("Hand: " + handCount);
        bankTotalLabel.setText("Bank: " + bankTotal + "M");
        completeSetsLabel.setText("Sets: " + completeSets);
        playsLabel.setText("Plays left: " + remainingPlays);

        setBorder(isActive ?
                new LineBorder(new Color(255, 215, 0), 2) :
                new LineBorder(new Color(100, 100, 100), 1));
    }

    public String getPlayerId() {
        return playerId;
    }
}