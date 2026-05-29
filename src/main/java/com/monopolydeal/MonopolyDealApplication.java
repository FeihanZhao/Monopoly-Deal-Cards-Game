package com.monopolydeal;

import com.monopolydeal.client.*;
import com.monopolydeal.model.GameConstants;


import com.monopolydeal.view.*;

import com.monopolydeal.server.*;
import javax.swing.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Application main entry point.
 *
 * Supports three launch modes:
 * 1. Start server: java ... --server [port]
 * 2. Explicit client: java ... --client [host] [port]
 * 3. Default client: java ... [host] [port] (omit the --client flag)
 *
 * Command-line arguments:
 * - --server [port]: launch in server mode, listening on the specified port (default 8888)
 * - --client host port: launch in client mode, connecting to the specified host and port
 * - No arguments or only host/port: launch in client mode, connecting to localhost:8888
 *
 * Uses SwingUtilities.invokeLater to ensure Swing GUI initialization on the Event Dispatch Thread.
 */
public class MonopolyDealApplication {
    private static final Logger logger = LogManager.getLogger(MonopolyDealApplication.class);

    /**
     * Program entry point.
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--server")) {
            // Server mode: parse port number and start server
            int port = args.length > 1 ? parsePort(args[1]) : GameConstants.SERVER_PORT;
            startServer(port);
        } else {
            // Client mode: parse host and port and start client
            ClientConfig config = parseClientConfig(args);
            startClient(config.host, config.port);
        }
    }

    /**
     * Start the game server.
     * If startup fails, logs a fatal error and exits.
     * @param port listening port number
     */
    private static void startServer(int port) {
        GameServer server = new GameServer(port);
        try {
            server.start();
        } catch (Exception e) {
            logger.fatal("Failed to start server", e);
            System.exit(1);
        }
    }

    /**
     * Start the game client.
     * Creates a GameClient connection and displays the main window on the Swing Event Dispatch Thread.
     * @param host server host address
     * @param port server port number
     */
    private static void startClient(String host, int port) {
        SwingUtilities.invokeLater(() -> {
            try {
                GameClient client = new GameClient(host, port);
                MainFrame frame = new MainFrame(client);
                frame.setTitle("Monopoly Deal Cards Game - " + host + ":" + port);
                frame.setVisible(true);
            } catch (Exception e) {
                logger.error("Failed to connect to server", e);
                JOptionPane.showMessageDialog(null,
                        "Connection failed: " + e.getMessage(),
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * Parse client command-line arguments.
     * Supports two formats:
     * - --client host port (explicit client mode)
     * - host port (default client mode, omitting the --client flag)
     *
     * @param args command-line arguments
     * @return client configuration (host and port)
     */
    private static ClientConfig parseClientConfig(String[] args) {
        if (args.length > 0 && args[0].equals("--client")) {
            // Explicit client mode
            String host = args.length > 1 ? args[1] : GameConstants.DEFAULT_HOST;
            int port = args.length > 2 ? parsePort(args[2]) : GameConstants.SERVER_PORT;
            return new ClientConfig(host, port);
        }

        // Default client mode (omit --client flag)
        String host = args.length > 0 ? args[0] : GameConstants.DEFAULT_HOST;
        int port = args.length > 1 ? parsePort(args[1]) : GameConstants.SERVER_PORT;
        return new ClientConfig(host, port);
    }

    /**
     * Parse a port number string.
     * Falls back to the default port 8888 with a warning if the format is invalid.
     * @param value port number string
     * @return parsed port number (falls back to default if invalid)
     */
    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("Invalid port number '{}', falling back to {}", value, GameConstants.SERVER_PORT);
            return GameConstants.SERVER_PORT;
        }
    }

    /**
     * Client configuration inner class — encapsulates host and port info.
     */
    private static class ClientConfig {
        /** Server host address */
        private final String host;
        /** Server port number */
        private final int port;

        private ClientConfig(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
