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
            int port = args.length > 1 ? parsePort(args[1]) : GameConstants.SERVER_PORT;
            startServer(port);
        } else {
            ClientConfig config = parseClientConfig(args);
            startClient(config.host, config.port);
        }
    }

    private static void startServer(int port) {
        GameServer server = new GameServer(port);
        try {
            server.start();
        } catch (Exception e) {
            logger.fatal("Failed to start server", e);
            System.exit(1);
        }
    }

    private static void startClient(String host, int port) {
        SwingUtilities.invokeLater(() -> {
            try {
                GameClient client = new GameClient(host, port);
                MainFrame frame = new MainFrame(client);
                frame.setTitle("Monopoly Deal Cards Game - " + host + ":" + port);
                frame.setVisible(true);
            } catch (Exception e) {
                logger.error("Failed to connect to server", e);
                JOptionPane.showMessageDialog(null, "Failed to connect to server: " + e.getMessage(),
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static ClientConfig parseClientConfig(String[] args) {
        if (args.length > 0 && args[0].equals("--client")) {
            String host = args.length > 1 ? args[1] : GameConstants.DEFAULT_HOST;
            int port = args.length > 2 ? parsePort(args[2]) : GameConstants.SERVER_PORT;
            return new ClientConfig(host, port);
        }

        String host = args.length > 0 ? args[0] : GameConstants.DEFAULT_HOST;
        int port = args.length > 1 ? parsePort(args[1]) : GameConstants.SERVER_PORT;
        return new ClientConfig(host, port);
    }

    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid port '{}', falling back to {}", value, GameConstants.SERVER_PORT);
            return GameConstants.SERVER_PORT;
        }
    }

    private static class ClientConfig {
        private final String host;
        private final int port;

        private ClientConfig(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}