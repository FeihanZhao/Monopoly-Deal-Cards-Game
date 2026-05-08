package com.monopolydeal.pattern.command;

import com.monopolydeal.model.Card;
import com.monopolydeal.model.Player;

public class PlayCardCommand implements Command {
    private final Player player;
    private final Card card;
    private final String action;

    public PlayCardCommand(Player player, Card card, String action) {
        this.player = player;
        this.card = card;
        this.action = action;
    }

    @Override
    public void execute() {
        player.removeCardFromHand(card);
        // Handle specific action
        switch (action) {
            case "PLAY_MONEY":
                player.getBank().deposit(card);
                break;
            case "PLAY_PROPERTY":
                player.getPropertyZone().addProperty(card);
                break;
        }
        player.incrementPlaysUsed();
    }

    @Override
    public void undo() {
        player.addCardToHand(card);
        player.setPlaysUsed(player.getPlaysUsed() - 1);
        // Reverse action
    }

    @Override
    public String getDescription() {
        return player.getNickname() + " plays " + card.getName();
    }
}
