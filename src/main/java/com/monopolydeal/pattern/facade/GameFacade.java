package com.monopolydeal.pattern.facade;

import com.monopolydeal.model.*;
import com.monopolydeal.server.*;
import com.monopolydeal.pattern.singleton.*;
import com.monopolydeal.util.MessageProtocol;

public class GameFacade {
    private final GameManager gameManager;
    private GameServer server;
    private GameRoom currentRoom;

    public GameFacade() {
        this.gameManager = GameManager.getInstance();
    }

    public void startServer(int port) {
        server = new GameServer(port);
        new Thread(() -> {
            try {
                server.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public String createRoom(String playerName) {
        // Simplified; real implementation would use ClientHandler
        return "ROOM123";
    }

    public boolean joinRoom(String roomCode, String playerName) {
        // Simplified
        return true;
    }

    public void playCard(String roomCode, String cardId) {
        // Simplified
    }

    public void endTurn(String roomCode) {
        // Simplified
    }

    public GameState getGameState(String roomCode) {
        return gameManager.getCurrentState();
    }
}