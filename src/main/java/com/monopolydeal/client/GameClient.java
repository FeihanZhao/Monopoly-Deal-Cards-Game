package com.monopolydeal.client;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;
import com.monopolydeal.util.MessageProtocol;
import com.monopolydeal.model.GameConstants;
import com.monopolydeal.view.MainFrame;
import javax.swing.*;

/**
 * Game client — manages the socket connection to the game server and message sending/receiving.
 *
 * Client architecture:
 * 1. Connects to the game server via TCP socket
 * 2. Continuously reads server messages on a background thread
 * 3. Forwards received messages to the UI layer via a message handler (Consumer<String>)
 * 4. Provides sendMessage() method to send messages to the server
 *
 * Usage:
 * - Create a GameClient instance with the server address and port
 * - Call setMessageHandler() to register a message handler (typically set by MainFrame)
 * - Call sendMessage() to send action requests
 *
 * Design notes:
 * - Message reading runs on a separate background thread to avoid blocking the Swing event thread
 * - Uses newline as the message delimiter (one complete JSON message per line)
 * - Resources are automatically cleaned up on disconnect
 */
public class GameClient {
    /** Socket connection to the server */
    protected Socket socket;
    /** Output stream — sends messages to the server */
    protected PrintWriter out;
    /** Input stream — receives messages from the server */
    protected BufferedReader in;
    /** Player ID (assigned by the server; set from the first ROOM_UPDATE message) */
    protected String playerId;
    /** Whether the client is connected to the server */
    protected boolean connected;
    /** Message handler — callback invoked when a server message is received */
    protected Consumer<String> messageHandler;

    /**
     * Constructor — establishes a connection to the game server.
     * Starts a background thread to listen for messages immediately upon successful connection.
     *
     * @param host server host address
     * @param port server port number
     * @throws IOException if the connection fails
     */
    /** Protected no-arg constructor for testing — does not open a socket connection */
    protected GameClient() {
        this.socket = null;
        this.out = null;
        this.in = null;
        this.connected = false;
    }

    public GameClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new PrintWriter(socket.getOutputStream(), true);  // autoFlush=true
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.connected = true;
        // Start background thread to listen for server messages
        new Thread(this::listenForMessages).start();
    }

    /**
     * Background message listener thread — loops reading server messages.
     * Calls messageHandler.accept() for each line of message received.
     * Automatically calls disconnect() on connection loss.
     */
    private void listenForMessages() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                if (messageHandler != null) {
                    messageHandler.accept(message);  // Forward to UI layer
                }
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("Connection to server lost: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    /**
     * Register a message handler.
     * Typically called by MainFrame in its constructor to receive and route server messages.
     * @param handler message handling callback (parameter is the full JSON message string)
     */
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /**
     * Send a message to the server.
     * @param type message type
     * @param payload message payload (JSON string)
     */
    public void sendMessage(MessageProtocol.MessageType type, String payload) {
        if (connected && out != null) {
            out.println(MessageProtocol.createMessage(type, payload));
        }
    }

    /** Disconnect from the server and clean up resources */
    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing socket: " + e.getMessage());
        }
    }

    // ==================== Getters/Setters ====================

    /** Check whether the client is connected to the server */
    public boolean isConnected() {
        return connected;
    }

    /** Set the player ID (called by MainFrame after receiving ROOM_UPDATE) */
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    /** Get the player ID */
    public String getPlayerId() {
        return playerId;
    }

    /**
     * Standalone client startup (without using the MonopolyDealApplication entry point).
     * Usage: java com.monopolydeal.client.GameClient [host] [port]
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String host = GameConstants.DEFAULT_HOST;
                int port = GameConstants.SERVER_PORT;

                if (args.length >= 1) {
                    if (args[0].equals("--client")) {
                        host = args.length >= 2 ? args[1] : GameConstants.DEFAULT_HOST;
                        port = args.length >= 3 ? Integer.parseInt(args[2]) : GameConstants.SERVER_PORT;
                    } else {
                        host = args[0];
                        port = args.length >= 2 ? Integer.parseInt(args[1]) : GameConstants.SERVER_PORT;
                    }
                }

                System.out.println("Connecting to " + host + ":" + port);
                GameClient client = new GameClient(host, port);
                MainFrame frame = new MainFrame(client);
                frame.setTitle("Monopoly Deal Cards Game - " + host + ":" + port);
                frame.setVisible(true);
            } catch (Exception e) {
                String host = args.length >= 2 ? args[1] : "unknown";
                String port = args.length >= 3 ? args[2] : String.valueOf(GameConstants.SERVER_PORT);
                JOptionPane.showMessageDialog(null,
                        "Cannot connect to server at " + host + ":" + port +
                                "\nMake sure the server is running first.",
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

}
