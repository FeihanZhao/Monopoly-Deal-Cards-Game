package com.monopolydeal;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;
import com.monopolydeal.model.*;

public class LocalGameFrame extends JFrame {
    private List<Player> players;
    private Player currentPlayer;
    private Deck deck;
    private int currentPlayerIndex;
    private int playsRemaining;
    private GamePhase phase;

    private JLabel phaseLabel;
    private JLabel playerLabel;
    private JLabel playsLabel;
    private JLabel deckLabel;
    private JPanel handPanel;
    private JPanel otherPlayersPanel;
    private JTextArea logArea;

    public LocalGameFrame() {
        setTitle("Monopoly Deal - Local Test Mode");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        initGame();
        initUI();
        updateDisplay();
    }

    private void initGame() {
        deck = new Deck();
        players = new ArrayList<>();
        players.add(new Player("p1", "You"));
        players.add(new Player("p2", "Bot Alice"));
        players.add(new Player("p3", "Bot Bob"));
        for (Player p : players) {
            List<Card> hand = deck.drawMultiple(GameConstants.INITIAL_HAND_SIZE);
            hand.forEach(p::addCardToHand);
        }
        currentPlayerIndex = 0;
        currentPlayer = players.get(0);
        currentPlayer.setActivePlayer(true);
        playsRemaining = GameConstants.MAX_PLAYS_PER_TURN;
        phase = GamePhase.PLAY;
    }

    private void initUI() {
        setLayout(new BorderLayout());
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        topBar.setBackground(new Color(30, 30, 30));
        phaseLabel = new JLabel("Phase: PLAY");
        phaseLabel.setForeground(Color.WHITE);
        phaseLabel.setFont(new Font("Arial", Font.BOLD, 16));
        playerLabel = new JLabel("Turn: You");
        playerLabel.setForeground(new Color(255, 215, 0));
        playerLabel.setFont(new Font("Arial", Font.BOLD, 16));
        playsLabel = new JLabel("Plays left: 3");
        playsLabel.setForeground(Color.WHITE);
        playsLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        deckLabel = new JLabel("Deck: " + deck.getDrawPileSize());
        deckLabel.setForeground(Color.WHITE);
        deckLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        JButton endTurnBtn = new JButton("End Turn");
        endTurnBtn.setBackground(new Color(178, 34, 34));
        endTurnBtn.setForeground(Color.WHITE);
        endTurnBtn.setFont(new Font("Arial", Font.BOLD, 14));
        endTurnBtn.addActionListener(e -> endTurn());
        JButton drawBtn = new JButton("Draw Card");
        drawBtn.setBackground(new Color(46, 139, 87));
        drawBtn.setForeground(Color.WHITE);
        drawBtn.setFont(new Font("Arial", Font.BOLD, 14));
        drawBtn.addActionListener(e -> drawCard());
        topBar.add(phaseLabel);
        topBar.add(playerLabel);
        topBar.add(playsLabel);
        topBar.add(deckLabel);
        topBar.add(drawBtn);
        topBar.add(endTurnBtn);
        add(topBar, BorderLayout.NORTH);
        otherPlayersPanel = new JPanel();
        otherPlayersPanel.setLayout(new BoxLayout(otherPlayersPanel, BoxLayout.Y_AXIS));
        otherPlayersPanel.setBackground(new Color(20, 60, 30));
        otherPlayersPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        JScrollPane otherScroll = new JScrollPane(otherPlayersPanel);
        otherScroll.setBorder(null);
        otherScroll.setPreferredSize(new Dimension(0, 200));
        add(otherScroll, BorderLayout.CENTER);
        handPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        handPanel.setBackground(new Color(30, 30, 30));
        handPanel.setBorder(new TitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Your Hand",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14), Color.WHITE));
        handPanel.setPreferredSize(new Dimension(0, 200));
        JScrollPane handScroll = new JScrollPane(handPanel);
        handScroll.setBorder(null);
        add(handScroll, BorderLayout.SOUTH);
        logArea = new JTextArea(10, 30);
        logArea.setEditable(false);
        logArea.setBackground(new Color(40, 40, 40));
        logArea.setForeground(Color.WHITE);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(new TitledBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                "Game Log",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 14), Color.WHITE));
        logScroll.setPreferredSize(new Dimension(300, 0));
        add(logScroll, BorderLayout.EAST);
    }

    private void updateDisplay() {
        playerLabel.setText("Turn: " + currentPlayer.getNickname());
        playsLabel.setText("Plays left: " + playsRemaining);
        deckLabel.setText("Deck: " + deck.getDrawPileSize());
        otherPlayersPanel.removeAll();
        for (Player p : players) {
            if (p != currentPlayer) {
                JPanel playerCard = createPlayerCard(p);
                otherPlayersPanel.add(playerCard);
                otherPlayersPanel.add(Box.createVerticalStrut(5));
            }
        }
        otherPlayersPanel.revalidate();
        otherPlayersPanel.repaint();
        handPanel.removeAll();
        if (currentPlayer == players.get(0)) {
            for (Card card : currentPlayer.getHand()) {
                JButton cardBtn = createCardButton(card);
                handPanel.add(cardBtn);
            }
        } else {
            for (int i = 0; i < currentPlayer.getHandCount(); i++) {
                JLabel cardBack = new JLabel("?");
                cardBack.setPreferredSize(new Dimension(100, 140));
                cardBack.setBackground(Color.DARK_GRAY);
                cardBack.setOpaque(true);
                cardBack.setHorizontalAlignment(SwingConstants.CENTER);
                cardBack.setForeground(Color.WHITE);
                cardBack.setFont(new Font("Arial", Font.BOLD, 24));
                cardBack.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
                handPanel.add(cardBack);
            }
        }
        handPanel.revalidate();
        handPanel.repaint();
    }

    private JPanel createPlayerCard(Player player) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(50, 50, 50));
        panel.setBorder(new LineBorder(new Color(100, 100, 100), 1));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 5));
        infoPanel.setBackground(new Color(50, 50, 50));
        JLabel nameLabel = new JLabel(player.getNickname());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 14));
        JLabel handLabel = new JLabel("Hand: " + player.getHandCount());
        handLabel.setForeground(Color.LIGHT_GRAY);
        JLabel bankLabel = new JLabel("Bank: " + player.getBank().getTotal() + "M");
        bankLabel.setForeground(new Color(255, 215, 0));
        JLabel setLabel = new JLabel("Sets: " + player.getCompleteSetsCount());
        setLabel.setForeground(new Color(100, 255, 100));
        StringBuilder props = new StringBuilder("Properties: ");
        for (Map.Entry<CardColor, List<Card>> entry : player.getPropertyZone().getAllPropertyGroups().entrySet()) {
            if (!entry.getValue().isEmpty()) {
                props.append(entry.getKey().getName()).append("(").append(entry.getValue().size()).append(") ");
            }
        }
        JLabel propLabel = new JLabel(props.toString());
        propLabel.setForeground(new Color(135, 206, 235));
        infoPanel.add(nameLabel);
        infoPanel.add(handLabel);
        infoPanel.add(bankLabel);
        infoPanel.add(setLabel);
        infoPanel.add(propLabel);
        panel.add(infoPanel, BorderLayout.CENTER);
        return panel;
    }

    private JButton createCardButton(Card card) {
        JButton btn = new JButton();
        btn.setLayout(new BorderLayout());
        btn.setPreferredSize(new Dimension(100, 140));
        btn.setMaximumSize(new Dimension(100, 140));
        Color bgColor = getCardBgColor(card.getType());
        btn.setBackground(bgColor);
        btn.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
        String valueStr = card.getValue() > 0 ? " (" + card.getValue() + "M)" : "";
        JLabel nameLabel = new JLabel("<html><center>" + card.getName() + valueStr + "</center></html>");
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Arial", Font.BOLD, 10));
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel typeLabel = new JLabel(card.getType().getDisplayName());
        typeLabel.setForeground(Color.WHITE);
        typeLabel.setFont(new Font("Arial", Font.PLAIN, 9));
        typeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        if (card.isWildProperty()) {
            JLabel wildLabel = new JLabel("WILD");
            wildLabel.setForeground(new Color(255, 255, 0));
            wildLabel.setFont(new Font("Arial", Font.BOLD, 9));
            wildLabel.setHorizontalAlignment(SwingConstants.CENTER);
            btn.add(wildLabel, BorderLayout.NORTH);
        }
        btn.add(nameLabel, BorderLayout.CENTER);
        btn.add(typeLabel, BorderLayout.SOUTH);
        btn.addActionListener(e -> playCard(card));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setLocation(btn.getX(), btn.getY() - 5);
                btn.setBorder(BorderFactory.createLineBorder(Color.YELLOW, 2));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setLocation(btn.getX(), btn.getY() + 5);
                btn.setBorder(BorderFactory.createLineBorder(Color.BLACK, 1));
            }
        });
        return btn;
    }

    private Color getCardBgColor(CardType type) {
        switch (type) {
            case MONEY: return new Color(34, 139, 34);
            case PROPERTY: return new Color(70, 130, 180);
            case RENT: return new Color(255, 140, 0);
            case ACTION: return new Color(218, 165, 32);
            default: return Color.GRAY;
        }
    }

    private void drawCard() {
        if (currentPlayer != players.get(0)) {
            JOptionPane.showMessageDialog(this, "Not your turn!");
            return;
        }
        Card drawn = deck.draw();
        if (drawn != null) {
            currentPlayer.addCardToHand(drawn);
            log("You drew: " + drawn.getName());
            updateDisplay();
        } else {
            log("Deck is empty!");
        }
    }

    private void playCard(Card card) {
        if (playsRemaining <= 0) {
            JOptionPane.showMessageDialog(this, "No plays remaining! End your turn.");
            return;
        }
        if (currentPlayer != players.get(0)) {
            JOptionPane.showMessageDialog(this, "Not your turn!");
            return;
        }
        currentPlayer.removeCardFromHand(card);
        switch (card.getType()) {
            case MONEY:
                currentPlayer.getBank().deposit(card);
                log("You deposited: " + card.getName() + " | Bank total: " + currentPlayer.getBank().getTotal() + "M");
                break;
            case PROPERTY:
                if (card.isWildProperty()) {
                    CardColor[] colors = Arrays.stream(CardColor.values())
                            .filter(CardColor::isPropertyColor)
                            .toArray(CardColor[]::new);
                    CardColor chosen = (CardColor) JOptionPane.showInputDialog(
                            this, "Choose color for wild property:", "Wild Property",
                            JOptionPane.QUESTION_MESSAGE, null, colors, colors[0]);
                    if (chosen != null) {
                        card.setWildColor(chosen);
                    }
                }
                currentPlayer.getPropertyZone().addProperty(card);
                log("You placed property: " + card.getName() + " | Sets: " + currentPlayer.getCompleteSetsCount());
                if (currentPlayer.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
                    JOptionPane.showMessageDialog(this, "YOU WIN! " + GameConstants.WINNING_COMPLETE_SETS + " complete sets!");
                    log("*** GAME OVER - YOU WIN! ***");
                }
                break;
            case RENT:
                deck.discard(card);
                log("You played rent card: " + card.getName());
                for (Player p : players) {
                    if (p != currentPlayer) {
                        int rentAmount = 2;
                        try {
                            List<Card> payment = p.getBank().removeCards(rentAmount);
                            payment.forEach(m -> currentPlayer.getBank().deposit(m));
                            log("Collected " + rentAmount + "M rent from " + p.getNickname());
                        } catch (Bank.InsufficientFundsException e) {
                            log(p.getNickname() + " cannot pay " + rentAmount + "M!");
                        }
                    }
                }
                break;
            case ACTION:
                deck.discard(card);
                log("You played action card: " + card.getName());
                handleActionCard(card);
                break;
        }
        playsRemaining--;
        updateDisplay();
        if (playsRemaining <= 0) {
            javax.swing.Timer timer = new javax.swing.Timer(500, e ->
                    JOptionPane.showMessageDialog(LocalGameFrame.this, "No more plays. Click End Turn to finish."));
            timer.setRepeats(false);
            timer.start();
        }
    }

    private void handleActionCard(Card card) {
        String name = card.getName();
        if (name.contains("Debt Collector")) {
            Player target = selectTargetPlayer();
            if (target != null) {
                try {
                    List<Card> payment = target.getBank().removeCards(GameConstants.DEBT_COLLECTOR_AMOUNT);
                    payment.forEach(m -> currentPlayer.getBank().deposit(m));
                    log("Collected " + GameConstants.DEBT_COLLECTOR_AMOUNT + "M from " + target.getNickname());
                } catch (Bank.InsufficientFundsException e) {
                    log(target.getNickname() + " cannot pay!");
                }
            }
        } else if (name.contains("Birthday")) {
            for (Player p : players) {
                if (p != currentPlayer) {
                    try {
                        List<Card> payment = p.getBank().removeCards(GameConstants.BIRTHDAY_AMOUNT);
                        payment.forEach(m -> currentPlayer.getBank().deposit(m));
                        log("Birthday! Collected " + GameConstants.BIRTHDAY_AMOUNT + "M from " + p.getNickname());
                    } catch (Bank.InsufficientFundsException e) {
                        log(p.getNickname() + " cannot pay birthday!");
                    }
                }
            }
        } else if (name.contains("Pass Go") || name.contains("Deal Breaker")) {
            List<Card> drawn = deck.drawMultiple(2);
            drawn.forEach(currentPlayer::addCardToHand);
            log("Drew 2 extra cards!");
        } else if (name.contains("Sly Deal")) {
            Player target = selectTargetPlayer();
            if (target != null) {
                Card stolen = stealProperty(target);
                if (stolen != null) {
                    currentPlayer.getPropertyZone().addProperty(stolen);
                    log("Stole " + stolen.getName() + " from " + target.getNickname());
                }
            }
        }
    }

    private Player selectTargetPlayer() {
        List<Player> targets = new ArrayList<>();
        for (Player p : players) {
            if (p != currentPlayer) {
                targets.add(p);
            }
        }
        return (Player) JOptionPane.showInputDialog(
                this, "Select target player:", "Target",
                JOptionPane.QUESTION_MESSAGE, null,
                targets.toArray(), targets.get(0));
    }

    private Card stealProperty(Player target) {
        List<Card> props = new ArrayList<>();
        for (List<Card> group : target.getPropertyZone().getAllPropertyGroups().values()) {
            props.addAll(group);
        }
        if (props.isEmpty()) return null;
        Card stolen = (Card) JOptionPane.showInputDialog(
                this, "Choose property to steal:", "Sly Deal",
                JOptionPane.QUESTION_MESSAGE, null,
                props.toArray(), props.get(0));
        if (stolen != null) {
            target.getPropertyZone().removeProperty(stolen);
        }
        return stolen;
    }

    private void endTurn() {
        while (currentPlayer.needsToDiscard() && !currentPlayer.getHand().isEmpty()) {
            Card discarded = currentPlayer.removeCardFromHand(0);
            deck.discard(discarded);
            log(currentPlayer.getNickname() + " discarded: " + discarded.getName());
        }
        currentPlayer.setActivePlayer(false);
        log("--- " + currentPlayer.getNickname() + " ended turn ---");
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        currentPlayer = players.get(currentPlayerIndex);
        currentPlayer.setActivePlayer(true);
        currentPlayer.resetTurnState();
        int drawCount = Math.min(GameConstants.DRAW_COUNT, deck.getDrawPileSize());
        List<Card> drawn = deck.drawMultiple(drawCount);
        drawn.forEach(currentPlayer::addCardToHand);
        log(currentPlayer.getNickname() + " drew " + drawn.size() + " cards");
        playsRemaining = GameConstants.MAX_PLAYS_PER_TURN;
        phase = GamePhase.PLAY;
        updateDisplay();
        if (currentPlayer != players.get(0)) {
            javax.swing.Timer timer = new javax.swing.Timer(1500, e -> botTurn());
            timer.setRepeats(false);
            timer.start();
        }
    }

    private void botTurn() {
        Player bot = currentPlayer;
        log("--- " + bot.getNickname() + "'s turn ---");
        List<Card> botHand = new ArrayList<>(bot.getHand());
        int plays = 0;
        for (Card card : botHand) {
            if (plays >= GameConstants.MAX_PLAYS_PER_TURN) break;
            bot.removeCardFromHand(card);
            if (card.isMoneyCard()) {
                bot.getBank().deposit(card);
                log(bot.getNickname() + " deposited " + card.getValue() + "M");
            } else if (card.isPropertyCard()) {
                bot.getPropertyZone().addProperty(card);
                log(bot.getNickname() + " placed property: " + card.getName());
                if (bot.getCompleteSetsCount() >= GameConstants.WINNING_COMPLETE_SETS) {
                    log("*** " + bot.getNickname() + " WINS! ***");
                    updateDisplay();
                    JOptionPane.showMessageDialog(this, bot.getNickname() + " wins with " + GameConstants.WINNING_COMPLETE_SETS + " complete sets!");
                    return;
                }
            } else {
                deck.discard(card);
                log(bot.getNickname() + " played: " + card.getName());
            }
            plays++;
        }
        playsRemaining = 0;
        updateDisplay();
        javax.swing.Timer timer = new javax.swing.Timer(1000, e -> endTurn());
        timer.setRepeats(false);
        timer.start();
    }

    private void log(String msg) {
        logArea.append(msg + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
}