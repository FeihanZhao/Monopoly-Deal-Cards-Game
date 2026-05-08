package com.monopolydeal;

import com.monopolydeal.client.*;
import com.monopolydeal.model.GameConstants;
import com.monopolydeal.view.*;
import com.monopolydeal.server.*;
import javax.swing.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MonopolyDealApplication {
    private static final Logger logger = LogManager.getLogger(MonopolyDealApplication.class);

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--server")) {
            startServer();
        } else {
            startClient();
        }
    }

    private static void startServer() {
        GameServer server = new GameServer(GameConstants.SERVER_PORT);
        try {
            server.start();
        } catch (Exception e) {
            logger.fatal("Failed to start server", e);
            System.exit(1);
        }
    }

    private static void startClient() {
        SwingUtilities.invokeLater(() -> {
            try {
                GameClient client = new GameClient("localhost", GameConstants.SERVER_PORT);
                MainFrame frame = new MainFrame(client);
                frame.setVisible(true);
            } catch (Exception e) {
                logger.error("Failed to connect to server", e);
                JOptionPane.showMessageDialog(null, "Failed to connect to server: " + e.getMessage(),
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}