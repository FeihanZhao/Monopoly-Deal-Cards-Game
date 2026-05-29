package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.Random;
import com.monopolydeal.client.GameClient;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonElement;

public class LobbyPanel extends JPanel {
    private final GameClient client;
    private JTextField nicknameField;
    private JTextField roomCodeField;
    private JButton createRoomButton;
    private JButton joinRoomButton;
    private JButton readyButton;
    private JButton leaveButton;
    private JList<String> playerList;
    private DefaultListModel<String> playerListModel;
    private JLabel roomCodeLabel;
    private JLabel statusLabel;
    private JPanel roomPanel;
    private JPanel loginPanel;
    private boolean isInRoom;
    private boolean isReady;

    // ====== Animation ======
    private final Timer animTimer;
    private float animPhase = 0f;
    private final java.util.List<Particle> particles = new ArrayList<>();
    private final Random rand = new Random();
    private float titleGlowPhase = 0f;
    private float bgShift = 0f;

    // ====== Colors ======
    private static final Color GOLD_PRIMARY   = new Color(255, 215, 0);
    private static final Color GOLD_LIGHT     = new Color(255, 235, 120);
    private static final Color DARK_BG_A      = new Color(10, 8, 30);
    private static final Color DARK_BG_B      = new Color(25, 18, 50);
    private static final Color DARK_BG_C      = new Color(35, 25, 65);
    private static final Color PANEL_BG       = new Color(20, 18, 45, 200);
    private static final Color INPUT_BG       = new Color(40, 35, 75);
    private static final Color INPUT_BORDER   = new Color(120, 100, 200);
    private static final Color INPUT_FOCUS    = new Color(255, 215, 0, 120);
    private static final Color TEXT_WHITE     = new Color(240, 240, 255);
    private static final Color TEXT_MUTED     = new Color(180, 175, 210);

    // ====== Particle class ======
    private static class Particle {
        float x, y, vx, vy, size, alpha, life, maxLife;
        String symbol;

        Particle(float x, float y, Random r) {
            this.x = x;
            this.y = y;
            this.vx = (r.nextFloat() - 0.5f) * 0.5f;
            this.vy = -(r.nextFloat() * 1.2f + 0.3f);
            this.size = r.nextFloat() * 18 + 10;
            this.alpha = r.nextFloat() * 0.4f + 0.15f;
            this.maxLife = r.nextFloat() * 300 + 200;
            this.life = this.maxLife;
            String[] syms = {"$", "♠", "♦", "♣", "♥", "💰", "🏠", "★"};
            this.symbol = syms[r.nextInt(syms.length)];
        }

        boolean update() {
            x += vx;
            y += vy;
            life--;
            alpha = (life / maxLife) * 0.4f;
            return life > 0;
        }
    }

    public LobbyPanel(GameClient client) {
        this.client = client;
        this.isInRoom = false;
        this.isReady = false;
        setLayout(new BorderLayout());
        setBackground(DARK_BG_A);

        createLoginPanel();
        createRoomPanel();
        add(loginPanel, BorderLayout.CENTER);

        // Animation timer — runs at 30fps
        animTimer = new Timer(33, e -> {
            animPhase += 0.04f;
            titleGlowPhase += 0.05f;
            bgShift += 0.003f;
            // Spawn particles
            if (particles.size() < 25 && rand.nextInt(3) == 0) {
                particles.add(new Particle(
                    rand.nextInt(getWidth() + 200) - 100,
                    getHeight() + 20,
                    rand
                ));
            }
            particles.removeIf(p -> !p.update());
            repaint();
        });
        animTimer.start();

        // Stop animation when hidden
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (!isShowing()) {
                    animTimer.stop();
                } else if (!animTimer.isRunning()) {
                    animTimer.start();
                }
            }
        });
    }

    // ================================================================
    // LOGIN PANEL — gorgeous landing screen
    // ================================================================

    private void createLoginPanel() {
        loginPanel = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth(), h = getHeight();

                // — animated gradient background —
                float t = bgShift;
                Color c1 = interpolateColor(DARK_BG_A, new Color(15, 10, 40), (float)Math.sin(t) * 0.5f + 0.5f);
                Color c2 = interpolateColor(DARK_BG_B, new Color(40, 25, 70), (float)Math.sin(t + 1.5f) * 0.5f + 0.5f);
                Color c3 = interpolateColor(DARK_BG_C, new Color(50, 30, 80), (float)Math.sin(t + 3f) * 0.5f + 0.5f);

                GradientPaint grad = new GradientPaint(0, 0, c1, w * 0.6f, h * 0.4f, c2, true);
                g2.setPaint(grad);
                g2.fillRect(0, 0, w, h);

                // radial accent glow
                RadialGradientPaint glow = new RadialGradientPaint(
                    w / 2f, h * 0.35f, Math.max(w, h) * 0.6f,
                    new float[]{0f, 0.6f, 1f},
                    new Color[]{new Color(100, 70, 200, 40), new Color(60, 40, 140, 10), new Color(0, 0, 0, 0)}
                );
                g2.setPaint(glow);
                g2.fillRect(0, 0, w, h);

                // — particles —
                for (Particle p : particles) {
                    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alpha));
                    g2.setColor(new Color(255, 215, 0));
                    g2.setFont(getFont().deriveFont(p.size));
                    FontMetrics fm = g2.getFontMetrics();
                    int sw = fm.stringWidth(p.symbol);
                    g2.drawString(p.symbol, (int)p.x - sw / 2, (int)p.y);
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

                // — decorative corner diamonds —
                g2.setColor(new Color(255, 215, 0, 25));
                float ds = 60;
                // top-left
                g2.fillPolygon(new int[]{20, 20 + (int)ds, 20}, new int[]{20, 20, 20 + (int)ds}, 3);
                // top-right
                g2.fillPolygon(new int[]{w - 20, w - 20 - (int)ds, w - 20}, new int[]{20, 20, 20 + (int)ds}, 3);
                // bottom-left
                g2.fillPolygon(new int[]{20, 20 + (int)ds, 20}, new int[]{h - 20, h - 20, h - 20 - (int)ds}, 3);
                // bottom-right
                g2.fillPolygon(new int[]{w - 20, w - 20 - (int)ds, w - 20}, new int[]{h - 20, h - 20, h - 20 - (int)ds}, 3);

                g2.dispose();
            }
        };
        loginPanel.setBorder(new EmptyBorder(50, 80, 50, 80));
        loginPanel.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 15, 10, 15);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;

        // ---- Title (custom painted) ----
        JLabel titleLabel = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                String text = "MONOPOLY DEAL";
                Font font = new Font("Segoe UI", Font.BOLD, 54);
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(text);
                int x = (getWidth() - tw) / 2;
                int y = getHeight() / 2 + fm.getAscent() / 3;

                // glow pulse
                float pulse = (float)Math.sin(titleGlowPhase) * 0.3f + 0.7f;

                // outer glow
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.12f * pulse));
                for (int i = 0; i < 8; i++) {
                    g2.setColor(GOLD_PRIMARY);
                    g2.drawString(text, x + (int)(Math.sin(titleGlowPhase + i * 0.8) * 4),
                                          y + (int)(Math.cos(titleGlowPhase + i * 0.8) * 4));
                }

                // main gold gradient text
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                GradientPaint titleGrad = new GradientPaint(
                    x, y - 20, GOLD_LIGHT,
                    x + tw, y + 20, GOLD_PRIMARY
                );
                g2.setPaint(titleGrad);
                g2.drawString(text, x, y);

                // shine highlight
                float shineX = (float)Math.sin(titleGlowPhase * 0.7f) * tw * 0.6f + tw * 0.2f;
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f * pulse));
                g2.setColor(Color.WHITE);
                g2.setFont(font.deriveFont(Font.PLAIN));
                g2.drawString(text, x + (int)shineX, y - 4);

                g2.dispose();
            }
        };
        titleLabel.setPreferredSize(new Dimension(500, 80));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(5, 15, 0, 15);
        loginPanel.add(titleLabel, gbc);

        // ---- Subtitle ----
        JLabel subtitleLabel = new JLabel("✦  The Trading Card Game  ✦", SwingConstants.CENTER);
        subtitleLabel.setFont(new Font("Segoe UI", Font.ITALIC, 20));
        subtitleLabel.setForeground(new Color(200, 190, 255, 200));
        gbc.gridy = 1;
        gbc.insets = new Insets(5, 15, 20, 15);
        loginPanel.add(subtitleLabel, gbc);

        // ---- Divider line ----
        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                int w = getWidth();
                GradientPaint gp = new GradientPaint(0, 0, new Color(255, 215, 0, 0),
                    w / 2f, 0, new Color(255, 215, 0, 80), true);
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, getHeight());
                g2.dispose();
            }
        };
        sep.setPreferredSize(new Dimension(400, 2));
        sep.setMaximumSize(new Dimension(400, 2));
        gbc.gridy = 2;
        gbc.insets = new Insets(0, 15, 20, 15);
        loginPanel.add(sep, gbc);

        // ---- Nickname ----
        gbc.gridy = 3;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(8, 15, 8, 15);
        JLabel nickLabel = new JLabel("Player Name");
        nickLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        nickLabel.setForeground(TEXT_MUTED);
        loginPanel.add(nickLabel, gbc);

        gbc.gridx = 1;
        nicknameField = new JTextField(20);
        nicknameField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        nicknameField.setText("Player" + (int)(Math.random() * 1000));
        styleTextField(nicknameField);
        loginPanel.add(nicknameField, gbc);

        // ---- Room Code ----
        gbc.gridx = 0;
        gbc.gridy = 4;
        JLabel roomLabel = new JLabel("Room Code");
        roomLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        roomLabel.setForeground(TEXT_MUTED);
        loginPanel.add(roomLabel, gbc);

        gbc.gridx = 1;
        roomCodeField = new JTextField(20);
        roomCodeField.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        roomCodeField.setToolTipText("Leave empty to create, or enter a code to join");
        styleTextField(roomCodeField);
        // Auto-uppercase room code
        roomCodeField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                char c = e.getKeyChar();
                if (Character.isLowerCase(c)) {
                    e.setKeyChar(Character.toUpperCase(c));
                }
            }
        });
        loginPanel.add(roomCodeField, gbc);

        // ---- Buttons ----
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(20, 15, 10, 15);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0));
        buttonRow.setOpaque(false);

        createRoomButton = createLuxuryButton("CREATE ROOM", new Color(255, 180, 0), new Color(180, 120, 0));
        joinRoomButton = createLuxuryButton("JOIN ROOM", new Color(100, 140, 255), new Color(60, 80, 180));

        createRoomButton.addActionListener(e -> createRoom());
        joinRoomButton.addActionListener(e -> joinRoom());

        buttonRow.add(createRoomButton);
        buttonRow.add(joinRoomButton);
        loginPanel.add(buttonRow, gbc);

        // ---- Status ----
        gbc.gridy = 6;
        gbc.insets = new Insets(5, 15, 10, 15);
        statusLabel = new JLabel("Enter a name to begin", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        statusLabel.setForeground(TEXT_MUTED);
        loginPanel.add(statusLabel, gbc);
    }

    // ================================================================
    // ROOM PANEL — waiting lobby
    // ================================================================

    private void createRoomPanel() {
        roomPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int w = getWidth(), h = getHeight();
                float t = bgShift;
                Color c1 = interpolateColor(new Color(15, 12, 35), new Color(20, 15, 50), (float)Math.sin(t) * 0.5f + 0.5f);
                Color c2 = interpolateColor(new Color(35, 25, 60), new Color(45, 30, 75), (float)Math.sin(t + 2f) * 0.5f + 0.5f);

                GradientPaint gp = new GradientPaint(0, 0, c1, w, h, c2);
                g2.setPaint(gp);
                g2.fillRect(0, 0, w, h);

                // particles in room too
                for (Particle p : particles) {
                    if (p.y < h + 30) {
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alpha * 0.3f));
                        g2.setColor(new Color(255, 215, 0));
                        g2.setFont(getFont().deriveFont(p.size * 0.6f));
                        g2.drawString(p.symbol, (int)p.x, (int)p.y);
                    }
                }
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                g2.dispose();
            }
        };
        roomPanel.setOpaque(false);
        roomPanel.setBorder(new EmptyBorder(30, 40, 30, 40));

        // ---- Top bar ----
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);

        roomCodeLabel = new JLabel("Room: ———");
        roomCodeLabel.setFont(new Font("Segoe UI", Font.BOLD, 30));
        roomCodeLabel.setForeground(GOLD_PRIMARY);
        topBar.add(roomCodeLabel, BorderLayout.WEST);

        JPanel topButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 0));
        topButtons.setOpaque(false);

        readyButton = createLuxuryButton("✓ READY", new Color(46, 204, 113), new Color(30, 140, 80));
        readyButton.addActionListener(e -> toggleReady());

        leaveButton = createLuxuryButton("✕ LEAVE", new Color(231, 76, 60), new Color(160, 40, 30));
        leaveButton.addActionListener(e -> leaveRoom());

        topButtons.add(readyButton);
        topButtons.add(leaveButton);
        topBar.add(topButtons, BorderLayout.EAST);

        roomPanel.add(topBar, BorderLayout.NORTH);

        // ---- Player list ----
        playerListModel = new DefaultListModel<>();
        playerList = new JList<>(playerListModel) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(30, 25, 60, 180));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                super.paintComponent(g);
                g2.dispose();
            }
        };
        playerList.setOpaque(false);
        playerList.setBackground(new Color(0, 0, 0, 0));
        playerList.setForeground(TEXT_WHITE);
        playerList.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        playerList.setFixedCellHeight(56);
        playerList.setSelectionBackground(new Color(100, 80, 200, 120));
        playerList.setSelectionForeground(Color.WHITE);
        playerList.setCellRenderer(new PlayerCellRenderer());

        JScrollPane scrollPane = new JScrollPane(playerList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        // decorative border on scrollpane
        JPanel scrollWrapper = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(120, 100, 200, 80));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.setColor(new Color(255, 215, 0, 20));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 16, 16);
                g2.dispose();
            }
        };
        scrollWrapper.setOpaque(false);
        scrollWrapper.setBorder(new EmptyBorder(2, 2, 2, 2));
        scrollWrapper.add(scrollPane, BorderLayout.CENTER);

        roomPanel.add(scrollWrapper, BorderLayout.CENTER);

        // ---- Bottom status ----
        JLabel roomHint = new JLabel("Press Ready when all players are in", SwingConstants.CENTER);
        roomHint.setFont(new Font("Segoe UI", Font.ITALIC, 14));
        roomHint.setForeground(TEXT_MUTED);
        roomHint.setBorder(new EmptyBorder(10, 0, 0, 0));
        roomPanel.add(roomHint, BorderLayout.SOUTH);
    }

    // ================================================================
    // LUXURY BUTTON with animated glow
    // ================================================================

    private JButton createLuxuryButton(String text, Color main, Color dark) {
        JButton btn = new JButton(text) {
            private float hoverAnim = 0f;
            private boolean hovering = false;
            private final Timer hoverTimer = new Timer(16, null);

            {
                hoverTimer.addActionListener(e -> {
                    if (hovering) hoverAnim = Math.min(1, hoverAnim + 0.12f);
                    else hoverAnim = Math.max(0, hoverAnim - 0.08f);
                    repaint();
                    if ((hovering && hoverAnim >= 1) || (!hovering && hoverAnim <= 0))
                        hoverTimer.stop();
                });

                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hovering = true;
                        if (!hoverTimer.isRunning()) hoverTimer.start();
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    }
                    @Override
                    public void mouseExited(MouseEvent e) {
                        hovering = false;
                        if (!hoverTimer.isRunning()) hoverTimer.start();
                        setCursor(Cursor.getDefaultCursor());
                    }
                });
            }

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                int w = getWidth(), h = getHeight();
                int arc = 22;

                // Shadow
                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillRoundRect(2, 3, w - 2, h - 2, arc, arc);

                // Body gradient
                float t = hoverAnim;
                Color c1 = interpolateColor(dark, main, t);
                Color c2 = interpolateColor(darker(dark), darker(main), t);
                GradientPaint gp = new GradientPaint(0, 0, c1, 0, h, c2);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, w, h, arc, arc);

                // Border glow
                g2.setColor(new Color(255, 255, 255, (int)(25 + t * 35)));
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, w - 1, h - 1, arc, arc);

                // Top shine
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f + t * 0.1f));
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(4, 2, w - 8, h / 2 - 4, arc - 4, arc - 4);

                // Text
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
                g2.setColor(Color.WHITE);
                Font font = new Font("Segoe UI", Font.BOLD, 15);
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics();
                int tw = fm.stringWidth(getText());
                int tx = (w - tw) / 2;
                int ty = (h + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), tx, ty);

                g2.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(190, 52);
            }

            @Override
            public Dimension getMinimumSize() {
                return new Dimension(140, 44);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ================================================================
    // STYLED TEXT FIELD
    // ================================================================

    private void styleTextField(JTextField field) {
        field.setBackground(INPUT_BG);
        field.setForeground(TEXT_WHITE);
        field.setCaretColor(GOLD_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(INPUT_BORDER, 2, true),
            BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));
        field.setOpaque(true);
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(GOLD_PRIMARY, 2, true),
                    BorderFactory.createEmptyBorder(10, 14, 10, 14)
                ));
            }
            @Override
            public void focusLost(FocusEvent e) {
                field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(INPUT_BORDER, 2, true),
                    BorderFactory.createEmptyBorder(10, 14, 10, 14)
                ));
            }
        });
    }

    // ================================================================
    // PLAYER LIST RENDERER
    // ================================================================

    private static class PlayerCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            JPanel panel = new JPanel(new BorderLayout());
            panel.setOpaque(false);
            panel.setBorder(new EmptyBorder(8, 12, 8, 12));

            String text = value.toString();
            boolean isHost = text.contains("[Host]");
            boolean isReady = text.contains("[Ready]");
            boolean isNotReady = text.contains("[Not Ready]");
            String cleanName = text.replace(" [Host]", "").replace(" [Ready]", "").replace(" [Not Ready]", "");

            // Avatar circle
            JLabel avatar = new JLabel("👤", SwingConstants.CENTER);
            avatar.setFont(new Font("Segoe UI", Font.PLAIN, 22));
            avatar.setPreferredSize(new Dimension(42, 42));
            avatar.setOpaque(true);
            avatar.setBackground(isSelected ? new Color(100, 80, 200, 160)
                                            : new Color(60, 50, 120, 120));
            avatar.setBorder(BorderFactory.createLineBorder(
                isReady ? new Color(46, 204, 113) : new Color(150, 140, 200, 100), 2));

            // Name
            JLabel nameLabel = new JLabel(cleanName);
            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
            nameLabel.setForeground(isSelected ? Color.WHITE : TEXT_WHITE);

            // Badges
            String badges = "";
            if (isHost) badges += "  [HOST]";
            if (isReady) badges += "  ✓ READY";
            else if (isNotReady) badges += "  ◯ WAITING";

            JLabel badgeLabel = new JLabel(badges);
            badgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            badgeLabel.setForeground(isHost ? GOLD_PRIMARY :
                                     isReady ? new Color(46, 204, 113) : TEXT_MUTED);

            JPanel textPanel = new JPanel(new BorderLayout(8, 0));
            textPanel.setOpaque(false);
            textPanel.add(nameLabel, BorderLayout.WEST);
            textPanel.add(badgeLabel, BorderLayout.EAST);

            panel.add(avatar, BorderLayout.WEST);
            panel.add(textPanel, BorderLayout.CENTER);

            if (isSelected) {
                panel.setBackground(new Color(100, 80, 200, 80));
                panel.setOpaque(true);
            }

            return panel;
        }
    }

    // ================================================================
    // ACTION HANDLERS (unchanged interface)
    // ================================================================

    private void createRoom() {
        String nickname = nicknameField.getText().trim();
        if (nickname.isEmpty()) {
            setStatus("Please enter a nickname", Color.RED);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        client.sendMessage(MessageProtocol.MessageType.CREATE_ROOM, payload.toString());
        setStatus("Creating room...", new Color(255, 200, 0));
    }

    private void joinRoom() {
        String nickname = nicknameField.getText().trim();
        String roomCode = roomCodeField.getText().trim().toUpperCase();
        if (nickname.isEmpty()) {
            setStatus("Please enter a nickname", Color.RED);
            return;
        }
        if (roomCode.isEmpty()) {
            setStatus("Please enter a room code", Color.RED);
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("nickname", nickname);
        payload.addProperty("roomCode", roomCode);
        client.sendMessage(MessageProtocol.MessageType.JOIN_ROOM, payload.toString());
        setStatus("Joining room...", new Color(255, 200, 0));
    }

    private void toggleReady() {
        isReady = !isReady;
        if (isReady) {
            readyButton = createLuxuryButton("✕ CANCEL", new Color(231, 76, 60), new Color(160, 40, 30));
            readyButton.addActionListener(e -> toggleReady());
        } else {
            readyButton = createLuxuryButton("✓ READY", new Color(46, 204, 113), new Color(30, 140, 80));
            readyButton.addActionListener(e -> toggleReady());
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("ready", isReady);
        client.sendMessage(MessageProtocol.MessageType.PLAYER_READY, payload.toString());
        refreshTopBar();
    }

    private void refreshTopBar() {
        if (roomPanel != null && roomPanel.getComponentCount() > 0) {
            Component top = roomPanel.getComponent(0);
            if (top instanceof JPanel) {
                JPanel topBar = (JPanel) top;
                if (topBar.getComponentCount() > 1) {
                    JPanel btnPanel = (JPanel) topBar.getComponent(1);
                    btnPanel.removeAll();
                    btnPanel.add(readyButton);
                    btnPanel.add(leaveButton);
                    btnPanel.revalidate();
                    btnPanel.repaint();
                }
            }
        }
    }

    private void leaveRoom() {
        client.sendMessage(MessageProtocol.MessageType.LEAVE_ROOM, "{}");
        isInRoom = false;
        isReady = false;
        readyButton = createLuxuryButton("✓ READY", new Color(46, 204, 113), new Color(30, 140, 80));
        readyButton.addActionListener(e -> toggleReady());
        showLoginPanel();
    }

    // ================================================================
    // PUBLIC UPDATE METHODS
    // ================================================================

    public void updateRoom(String jsonPayload) {
        SwingUtilities.invokeLater(() -> {
            try {
                JsonObject payload = JsonParser.parseString(jsonPayload).getAsJsonObject();
                String roomCode = payload.get("roomCode").getAsString();
                roomCodeLabel.setText("Room:  " + roomCode);
                JsonArray players = payload.getAsJsonArray("players");
                playerListModel.clear();
                for (JsonElement elem : players) {
                    JsonObject player = elem.getAsJsonObject();
                    String nickname = player.get("nickname").getAsString();
                    boolean ready = player.get("ready").getAsBoolean();
                    boolean isCreator = player.get("isCreator").getAsBoolean();
                    String displayText = nickname;
                    if (isCreator) displayText += " [Host]";
                    displayText += ready ? " [Ready]" : " [Not Ready]";
                    playerListModel.addElement(displayText);
                }
                if (!isInRoom) {
                    isInRoom = true;
                    showRoomPanel();
                }
                setStatus("Room: " + roomCode + " | Players: " + players.size(), new Color(46, 204, 113));
            } catch (Exception e) {
                setStatus("Failed to update room info", Color.RED);
            }
        });
    }

    private void showRoomPanel() {
        remove(loginPanel);
        add(roomPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void showLoginPanel() {
        if (roomPanel != null && roomPanel.getParent() == this) {
            remove(roomPanel);
        }
        add(loginPanel, BorderLayout.CENTER);
        revalidate();
        repaint();
        setStatus("Enter a name to begin", TEXT_MUTED);
    }

    private void setStatus(String message, Color color) {
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setForeground(color);
        }
    }

    // ================================================================
    // COLOR UTILITIES
    // ================================================================

    private static Color interpolateColor(Color a, Color b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        int r = (int)(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = (int)(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        int al = (int)(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t);
        return new Color(r, g, bl, al);
    }

    private static Color darker(Color c) {
        return new Color(
            Math.max(0, c.getRed() - 60),
            Math.max(0, c.getGreen() - 60),
            Math.max(0, c.getBlue() - 60),
            c.getAlpha()
        );
    }
}
