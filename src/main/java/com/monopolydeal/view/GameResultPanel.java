package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * GameResultPanel
 *
 * A full-screen result panel shown after a game ends (win or draw),
 * replacing the previous JOptionPane dialogs in MainFrame.
 *
 * Added to MainFrame's CardLayout under the key "RESULT" and shown
 * via cardLayout.show(mainPanel, "RESULT").
 *
 * Two entry points depending on how the game ended:
 *
 *   // Normal win (GAME_OVER message):
 *   resultPanel.showWinner(payload);
 *
 *   // Draw / not enough players (GAME_DRAW message):
 *   resultPanel.showDraw(payload);
 *
 * The panel calls back to MainFrame when the player clicks
 * "Return to Lobby" via a Runnable passed in the constructor:
 *
 *   GameResultPanel resultPanel = new GameResultPanel(() -> {
 *       cardLayout.show(mainPanel, "LOBBY");
 *   });
 */
public class GameResultPanel extends JPanel {

    // Colours
    private static final Color BG_TOP        = new Color(10, 10, 20);
    private static final Color BG_BOTTOM     = new Color(20, 40, 20);
    private static final Color GOLD          = new Color(255, 215, 0);
    private static final Color SILVER        = new Color(192, 192, 192);
    private static final Color CARD_BG       = new Color(30, 30, 45, 220);
    private static final Color CARD_BORDER   = new Color(80, 80, 100);
    private static final Color TEXT_PRIMARY  = Color.WHITE;
    private static final Color TEXT_MUTED    = new Color(170, 170, 170);
    private static final Color BTN_LOBBY     = new Color(34, 139, 34);
    private static final Color BTN_HOVER     = new Color(46, 180, 46);

    // Child components
    private JLabel    titleLabel;
    private JLabel    subtitleLabel;
    private JLabel    winnerNameLabel;
    private JLabel    durationLabel;
    private JLabel    reasonLabel;          // shown only for draws
    private JPanel    statsPanel;           // player summary rows
    private JButton   lobbyButton;

    // Callback
    private final Runnable onReturnToLobby;

    // Result data (stored for repaint)
    private boolean isDraw      = false;
    private String  winnerName  = "";
    private String  duration    = "";
    private String  drawReason  = "";
    private final List<PlayerRow> playerRows = new ArrayList<>();

    // Holds display data for one player's summary row.
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


    // Constructor


    /**
     * @param onReturnToLobby called when the player clicks "Return to Lobby";
     *                        in MainFrame this should do
     *                        cardLayout.show(mainPanel, "LOBBY")
     */
    public GameResultPanel(Runnable onReturnToLobby) {
        this.onReturnToLobby = onReturnToLobby;
        setLayout(new GridBagLayout());
        setOpaque(true);
        buildUI();
    }


    // Public API  (called from MainFrame)


    /**
     * Populate and show a win result.
     * Expects the GAME_OVER JSON payload from the server:
     * {
     *   "winnerId":       "...",
     *   "winnerNickname": "Alice",
     *   "gameDuration":   "04:32",
     *   "completeSets":   3,
     *   "players": [                      // optional — show summary if present
     *     { "nickname": "Alice", "completeSets": 3, "bankTotal": 14 },
     *     ...
     *   ]
     * }
     */
    public void showWinner(String jsonPayload) {
        try {
            JsonObject result = JsonParser.parseString(jsonPayload).getAsJsonObject();
            winnerName = result.has("winnerNickname")
                    ? result.get("winnerNickname").getAsString() : "Unknown";
            duration   = result.has("gameDuration")
                    ? result.get("gameDuration").getAsString() : "--:--";
            isDraw     = false;

            playerRows.clear();
            if (result.has("players")) {
                JsonArray arr = result.getAsJsonArray("players");
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject p   = arr.get(i).getAsJsonObject();
                    String nick    = p.has("nickname")     ? p.get("nickname").getAsString()     : "?";
                    int    sets    = p.has("completeSets") ? p.get("completeSets").getAsInt()    : 0;
                    int    bank    = p.has("bankTotal")    ? p.get("bankTotal").getAsInt()        : 0;
                    boolean winner = nick.equals(winnerName);
                    playerRows.add(new PlayerRow(nick, sets, bank, winner));
                }
            }

            refresh();
        } catch (Exception e) {
            showFallback("Game Over!");
        }
    }

    // Populate and show a draw result.
    // Expects the GAME_DRAW JSON payload: { "reason": "Not enough players" }

    public void showDraw(String jsonPayload) {
        try {
            JsonObject result = JsonParser.parseString(jsonPayload).getAsJsonObject();
            drawReason = result.has("reason")
                    ? result.get("reason").getAsString() : "Game ended in a draw";
            isDraw     = true;
            playerRows.clear();
            refresh();
        } catch (Exception e) {
            showFallback("Game ended in a draw.");
        }
    }


    // UI construction


    private void buildUI() {
        // Central card panel
        JPanel card = new JPanel(new BorderLayout(0, 16)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD_BG);
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), 20, 20));
                g2.setColor(CARD_BORDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.draw(new RoundRectangle2D.Float(
                        0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, 20, 20));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(36, 48, 36, 48));
        card.setPreferredSize(new Dimension(520, 460));

        // Top: trophy + title
        JPanel topPanel = new JPanel(new BorderLayout(0, 8));
        topPanel.setOpaque(false);

        titleLabel = new JLabel("🏆 GAME OVER", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 32));
        titleLabel.setForeground(GOLD);

        subtitleLabel = new JLabel("", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        subtitleLabel.setForeground(TEXT_MUTED);

        topPanel.add(titleLabel,    BorderLayout.NORTH);
        topPanel.add(subtitleLabel, BorderLayout.SOUTH);

        // Middle: winner name + duration + draw reason
        JPanel midPanel = new JPanel(new BorderLayout(0, 10));
        midPanel.setOpaque(false);

        winnerNameLabel = new JLabel("", SwingConstants.CENTER);
        winnerNameLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
        winnerNameLabel.setForeground(TEXT_PRIMARY);

        durationLabel = new JLabel("", SwingConstants.CENTER);
        durationLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        durationLabel.setForeground(TEXT_MUTED);

        reasonLabel = new JLabel("", SwingConstants.CENTER);
        reasonLabel.setFont(new Font("SansSerif", Font.ITALIC, 13));
        reasonLabel.setForeground(SILVER);

        midPanel.add(winnerNameLabel, BorderLayout.NORTH);
        midPanel.add(durationLabel,   BorderLayout.CENTER);
        midPanel.add(reasonLabel,     BorderLayout.SOUTH);

        // Stats: player summary rows
        statsPanel = new JPanel();
        statsPanel.setLayout(new BoxLayout(statsPanel, BoxLayout.Y_AXIS));
        statsPanel.setOpaque(false);

        // Bottom: lobby button
        lobbyButton = makeButton("Return to Lobby", BTN_LOBBY, BTN_HOVER);
        lobbyButton.addActionListener(e -> {
            if (onReturnToLobby != null) onReturnToLobby.run();
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnRow.setOpaque(false);
        btnRow.add(lobbyButton);

        card.add(topPanel,   BorderLayout.NORTH);
        card.add(midPanel,   BorderLayout.CENTER);
        card.add(statsPanel, BorderLayout.SOUTH);

        // Wrap everything in a vertical box so the button sits below the card
        JPanel wrapper = new JPanel(new BorderLayout(0, 16));
        wrapper.setOpaque(false);
        wrapper.add(card,   BorderLayout.CENTER);
        wrapper.add(btnRow, BorderLayout.SOUTH);

        add(wrapper);
    }


    // Refresh displayed data

    private void refresh() {
        if (isDraw) {
            titleLabel.setText("🤝 GAME DRAW");
            titleLabel.setForeground(SILVER);
            subtitleLabel.setText("The game has ended without a winner.");
            winnerNameLabel.setText("");
            durationLabel.setText("");
            reasonLabel.setText("Reason: " + drawReason);
            reasonLabel.setVisible(true);
        } else {
            titleLabel.setText("🏆 GAME OVER");
            titleLabel.setForeground(GOLD);
            subtitleLabel.setText("Congratulations to the winner!");
            winnerNameLabel.setText(winnerName);
            durationLabel.setText("Game duration: " + duration);
            reasonLabel.setVisible(false);
        }

        // Rebuild player summary rows
        statsPanel.removeAll();
        if (!playerRows.isEmpty()) {
            JLabel header = new JLabel("Final Standings", SwingConstants.CENTER);
            header.setFont(new Font("SansSerif", Font.BOLD, 13));
            header.setForeground(TEXT_MUTED);
            header.setAlignmentX(CENTER_ALIGNMENT);
            header.setBorder(new EmptyBorder(8, 0, 6, 0));
            statsPanel.add(header);

            for (PlayerRow row : playerRows) {
                statsPanel.add(buildPlayerRow(row));
                statsPanel.add(Box.createVerticalStrut(4));
            }
        }

        statsPanel.revalidate();
        statsPanel.repaint();
        revalidate();
        repaint();
    }

    // Build one player summary row.
    private JPanel buildPlayerRow(PlayerRow row) {
        JPanel panel = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = row.isWinner
                        ? new Color(80, 65, 0, 160)
                        : new Color(40, 40, 55, 160);
                g2.setColor(bg);
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), 8, 8));
                if (row.isWinner) {
                    g2.setColor(new Color(255, 215, 0, 120));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.draw(new RoundRectangle2D.Float(
                            0.75f, 0.75f, getWidth() - 1.5f, getHeight() - 1.5f, 8, 8));
                }
                g2.dispose();
            }
        };
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(6, 12, 6, 12));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        panel.setAlignmentX(LEFT_ALIGNMENT);

        // Trophy icon for winner
        String icon = row.isWinner ? "🏆 " : "   ";
        JLabel nameLabel = new JLabel(icon + row.nickname);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        nameLabel.setForeground(row.isWinner ? GOLD : TEXT_PRIMARY);

        JPanel rightInfo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        rightInfo.setOpaque(false);

        JLabel setsLabel = new JLabel("Sets: " + row.completeSets + "/3");
        setsLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        setsLabel.setForeground(new Color(100, 220, 100));

        JLabel bankLabel = new JLabel("Bank: $" + row.bankTotal + "M");
        bankLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        bankLabel.setForeground(new Color(255, 215, 0));

        rightInfo.add(setsLabel);
        rightInfo.add(bankLabel);

        panel.add(nameLabel,  BorderLayout.WEST);
        panel.add(rightInfo,  BorderLayout.EAST);

        return panel;
    }

    private void showFallback(String message) {
        isDraw = false;
        winnerName = message;
        duration   = "";
        playerRows.clear();
        refresh();
    }


    // Background gradient


    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(
                0, 0,           BG_TOP,
                0, getHeight(), BG_BOTTOM));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }


    // Button factory


    private JButton makeButton(String text, Color bg, Color hover) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fill(new RoundRectangle2D.Float(
                        0, 0, getWidth(), getHeight(), 10, 10));
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
        btn.setFont(new Font("SansSerif", Font.BOLD, 14));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setPreferredSize(new Dimension(180, 42));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(hover);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(bg);
            }
        });
        return btn;
    }
}