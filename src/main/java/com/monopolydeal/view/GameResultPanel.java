package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Premium Game Result Panel with Neon Glow, Glassmorphism & Dynamic Light Effects
 * For Monopoly Deal Game End Screen (Victory / Draw)
 */
public class GameResultPanel extends JPanel {

    // ========================= PREMIUM COLOR SYSTEM =========================
    private static final Color BG_TOP         = new Color(8, 8, 18);
    private static final Color BG_BOTTOM      = new Color(12, 32, 18);
    private static final Color GOLD_PRIMARY   = new Color(255, 215, 0);
    private static final Color GOLD_GLOW      = new Color(255, 230, 100);
    private static final Color GOLD_NEON      = new Color(255, 220, 60);
    private static final Color SILVER_PRIMARY = new Color(210, 210, 225);
    private static final Color SILVER_GLOW    = new Color(235, 235, 245);
    private static final Color CARD_GLASS     = new Color(26, 28, 42, 230);
    private static final Color CARD_BORDER    = new Color(100, 100, 140, 160);
    private static final Color BORDER_GLOW    = new Color(140, 120, 200, 120);
    private static final Color TEXT_BRIGHT    = new Color(250, 250, 255);
    private static final Color TEXT_MUTED     = new Color(160, 160, 180);
    private static final Color BTN_MAIN       = new Color(42, 150, 60);
    private static final Color BTN_HOVER      = new Color(60, 190, 90);
    private static final Color BTN_NEON       = new Color(80, 220, 110);
    private static final Color GREEN_ACCENT   = new Color(80, 220, 120);
    private static final Color HIGHLIGHT_BG   = new Color(60, 50, 15, 180);

    // ========================= UI COMPONENTS =========================
    private JLabel    titleLabel;
    private JLabel    subtitleLabel;
    private JLabel    winnerNameLabel;
    private JLabel    durationLabel;
    private JLabel    reasonLabel;
    private JPanel    statsPanel;
    private JButton   lobbyButton;

    private final Runnable onReturnToLobby;

    private boolean  isDraw      = false;
    private String   winnerName  = "";
    private String   duration    = "";
    private String   drawReason  = "";
    private final List<PlayerRow> playerRows = new ArrayList<>();

    private static class PlayerRow {
        String nickname;
        int    completeSets;
        int    bankTotal;
        boolean isWinner;

        PlayerRow(String nickname, int completeSets, int bankTotal, boolean isWinner) {
            this.nickname     = nickname;
            this.completeSets = completeSets;
            this.bankTotal    = bankTotal;
            this.isWinner     = isWinner;
        }
    }

    // ========================= CONSTRUCTOR =========================
    public GameResultPanel(Runnable onReturnToLobby) {
        this.onReturnToLobby = onReturnToLobby;
        setLayout(new GridBagLayout());
        setOpaque(true);
        buildPremiumUI();
    }

    // ========================= PUBLIC API =========================
    public void showWinner(String jsonPayload) {
        try {
            JsonObject result = JsonParser.parseString(jsonPayload).getAsJsonObject();
            winnerName = result.has("winnerNickname") ? result.get("winnerNickname").getAsString() : "Champion";
            duration   = result.has("gameDuration") ? result.get("gameDuration").getAsString() : "--:--";
            isDraw     = false;

            playerRows.clear();
            if (result.has("players")) {
                JsonArray arr = result.getAsJsonArray("players");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject p = arr.get(i).getAsJsonObject();
                    String nick = p.has("nickname") ? p.get("nickname").getAsString() : "Player";
                    int sets = p.has("completeSets") ? p.get("completeSets").getAsInt() : 0;
                    int bank = p.has("bankTotal") ? p.get("bankTotal").getAsInt() : 0;
                    playerRows.add(new PlayerRow(nick, sets, bank, nick.equals(winnerName)));
                }
            }
            refresh();
        } catch (Exception e) {
            showFallback("VICTORY!");
        }
    }

    public void showDraw(String jsonPayload) {
        try {
            JsonObject result = JsonParser.parseString(jsonPayload).getAsJsonObject();
            drawReason = result.has("reason") ? result.get("reason").getAsString() : "Game ended in a draw";
            isDraw     = true;
            playerRows.clear();
            refresh();
        } catch (Exception e) {
            showFallback("GAME DRAW");
        }
    }

    // ========================= PREMIUM UI BUILDER =========================
    private void buildPremiumUI() {
        JPanel card = new JPanel(new BorderLayout(0, 20)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 28, 28);
                g2.setColor(CARD_GLASS);
                g2.fill(shape);

                g2.setStroke(new BasicStroke(2.0f));
                g2.setColor(CARD_BORDER);
                g2.draw(shape);

                g2.setStroke(new BasicStroke(1.0f));
                g2.setColor(BORDER_GLOW);
                g2.draw(new RoundRectangle2D.Float(2,2,getWidth()-4,getHeight()-4,26,26));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(48, 56, 48, 56));
        card.setPreferredSize(new Dimension(580, 520));

        // TOP PANEL
        JPanel topPanel = new JPanel(new BorderLayout(0, 10));
        topPanel.setOpaque(false);

        titleLabel = new JLabel("GAME OVER", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 40));
        titleLabel.setForeground(GOLD_PRIMARY);

        subtitleLabel = new JLabel("", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        subtitleLabel.setForeground(TEXT_MUTED);

        topPanel.add(titleLabel, BorderLayout.NORTH);
        topPanel.add(subtitleLabel, BorderLayout.SOUTH);

        // MIDDLE INFO
        JPanel midPanel = new JPanel(new BorderLayout(0, 14));
        midPanel.setOpaque(false);

        winnerNameLabel = new JLabel("", SwingConstants.CENTER);
        winnerNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 32));
        winnerNameLabel.setForeground(TEXT_BRIGHT);

        durationLabel = new JLabel("", SwingConstants.CENTER);
        durationLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        durationLabel.setForeground(TEXT_MUTED);

        reasonLabel = new JLabel("", SwingConstants.CENTER);
        reasonLabel.setFont(new Font("Segoe UI", Font.ITALIC, 15));
        reasonLabel.setForeground(SILVER_GLOW);

        midPanel.add(winnerNameLabel, BorderLayout.NORTH);
        midPanel.add(durationLabel, BorderLayout.CENTER);
        midPanel.add(reasonLabel, BorderLayout.SOUTH);

        // STATS
        statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setOpaque(false);

        // BUTTON
        lobbyButton = makePremiumButton("Return to Lobby");
        lobbyButton.addActionListener(e -> {
            if (onReturnToLobby != null) onReturnToLobby.run();
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnRow.setOpaque(false);
        btnRow.add(lobbyButton);

        card.add(topPanel, BorderLayout.NORTH);
        card.add(midPanel, BorderLayout.CENTER);
        card.add(statsPanel, BorderLayout.SOUTH);

        JPanel wrapper = new JPanel(new BorderLayout(0, 22));
        wrapper.setOpaque(false);
        wrapper.add(card, BorderLayout.CENTER);
        wrapper.add(btnRow, BorderLayout.SOUTH);

        add(wrapper);
    }

    // ========================= REFRESH DISPLAY =========================
    private void refresh() {
        if (isDraw) {
            titleLabel.setText("GAME DRAW");
            titleLabel.setForeground(SILVER_PRIMARY);
            subtitleLabel.setText("The match has ended in a tie");
            winnerNameLabel.setText("");
            durationLabel.setText("");
            reasonLabel.setText("Reason: " + drawReason);
            reasonLabel.setVisible(true);
        } else {
            titleLabel.setText("VICTORY!");
            titleLabel.setForeground(GOLD_NEON);
            subtitleLabel.setText("A winner has been crowned!");
            winnerNameLabel.setText(winnerName);
            durationLabel.setText("Game Duration: " + duration);
            reasonLabel.setVisible(false);
        }

        statsPanel.removeAll();
        if (!playerRows.isEmpty()) {
            JLabel header = new JLabel("FINAL STANDINGS", SwingConstants.CENTER);
            header.setFont(new Font("Segoe UI", Font.BOLD, 15));
            header.setForeground(TEXT_MUTED);
            header.setAlignmentX(CENTER_ALIGNMENT);
            header.setBorder(new EmptyBorder(12, 0, 10, 0));
            statsPanel.add(header);

            for (PlayerRow row : playerRows) {
                statsPanel.add(buildPremiumPlayerRow(row));
                statsPanel.add(Box.createVerticalStrut(6));
            }
        }

        statsPanel.revalidate();
        statsPanel.repaint();
        revalidate();
        repaint();
    }

    // ========================= PLAYER ROW =========================
    private JPanel buildPremiumPlayerRow(PlayerRow row) {
        JPanel panel = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bg = row.isWinner ? HIGHLIGHT_BG : new Color(36, 38, 54, 170);
                RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0,0,getWidth(),getHeight(),12,12);
                g2.setColor(bg);
                g2.fill(shape);

                if (row.isWinner) {
                    g2.setStroke(new BasicStroke(1.8f));
                    g2.setColor(new Color(255,215,0,140));
                    g2.draw(shape);
                } else {
                    g2.setStroke(new BasicStroke(1.0f));
                    g2.setColor(new Color(80,80,110,100));
                    g2.draw(shape);
                }
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(10, 16, 10, 16));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        panel.setAlignmentX(LEFT_ALIGNMENT);

        String icon = row.isWinner ? "🏆 " : "▫️ ";
        JLabel nameLabel = new JLabel(icon + row.nickname);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        nameLabel.setForeground(row.isWinner ? GOLD_GLOW : TEXT_BRIGHT);

        JPanel rightInfo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 18, 0));
        rightInfo.setOpaque(false);

        JLabel setsLabel = new JLabel("Sets: " + row.completeSets + "/3");
        setsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        setsLabel.setForeground(GREEN_ACCENT);

        JLabel bankLabel = new JLabel("Bank: $" + row.bankTotal + "M");
        bankLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        bankLabel.setForeground(GOLD_GLOW);

        rightInfo.add(setsLabel);
        rightInfo.add(bankLabel);

        panel.add(nameLabel, BorderLayout.WEST);
        panel.add(rightInfo, BorderLayout.EAST);

        return panel;
    }

    private void showFallback(String message) {
        isDraw = false;
        winnerName = message;
        duration = "";
        playerRows.clear();
        refresh();
    }

    // ========================= BACKGROUND GRADIENT =========================
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        LinearGradientPaint bgGradient = new LinearGradientPaint(
                0, 0, 0, getHeight(),
                new float[]{0.0f, 1.0f},
                new Color[]{BG_TOP, BG_BOTTOM}
        );
        g2.setPaint(bgGradient);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }

    // ========================= PREMIUM BUTTON =========================
    private JButton makePremiumButton(String text) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int w = getWidth(), h = getHeight();
                RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0,0,w,h,16,16);

                if (getModel().isPressed()) {
                    g2.setColor(BTN_MAIN.darker());
                } else if (getModel().isRollover()) {
                    g2.setPaint(new GradientPaint(0,0, BTN_HOVER, 0,h, BTN_MAIN));
                } else {
                    g2.setPaint(new GradientPaint(0,0, BTN_MAIN, 0,h, BTN_MAIN.darker()));
                }
                g2.fill(shape);

                g2.setStroke(new BasicStroke(1.8f));
                g2.setColor(new Color(255,255,255,35));
                g2.draw(shape);

                if (getModel().isRollover()) {
                    g2.setColor(new Color(255,255,255,20));
                    g2.fill(new RoundRectangle2D.Float(4,4,w-8,h/2-2,12,12));
                }

                g2.setColor(TEXT_BRIGHT);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 15));
                FontMetrics fm = g2.getFontMetrics();
                int tx = (w - fm.stringWidth(getText())) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);
                g2.dispose();
            }
        };
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(210, 50));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }
}
