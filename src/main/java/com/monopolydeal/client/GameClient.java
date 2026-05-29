package com.monopolydeal.client;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;
import com.monopolydeal.util.MessageProtocol;
import com.monopolydeal.model.GameConstants;
import com.monopolydeal.view.MainFrame;
import javax.swing.*;

public class GameClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String playerId;
    private boolean connected;
    private Consumer<String> messageHandler;

    public GameClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new PrintWriter(socket.getOutputStream(), true);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.connected = true;
        new Thread(this::listenForMessages).start();
    }

    private void listenForMessages() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                if (messageHandler != null) {
                    messageHandler.accept(message);
                }
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("Connection lost: " + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    public void sendMessage(MessageProtocol.MessageType type, String payload) {
        if (connected && out != null) {
            out.println(MessageProtocol.createMessage(type, payload));
        }
    }

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

    public boolean isConnected() {
        return connected;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String host = GameConstants.DEFAULT_HOST;
                int port = GameConstants.SERVER_PORT;

                if (args.length > 0 && args[0].equals("--client")) {
                    host = args.length > 1 ? args[1] : GameConstants.DEFAULT_HOST;
                    port = args.length > 2 ? Integer.parseInt(args[2]) : GameConstants.SERVER_PORT;
                } else if (args.length > 0) {
                    host = args[0];
                    port = args.length > 1 ? Integer.parseInt(args[1]) : GameConstants.SERVER_PORT;
                }

                GameClient client = new GameClient(host, port);
                MainFrame frame = new MainFrame(client);
                frame.setTitle("Monopoly Deal Cards Game - " + host + ":" + port);
                frame.setVisible(true);
            } catch (Exception e) {
                String host = args.length > 1 ? args[1] : GameConstants.DEFAULT_HOST;
                int port = args.length > 2 ? Integer.parseInt(args[2]) : GameConstants.SERVER_PORT;
                JOptionPane.showMessageDialog(null,
                        "Cannot connect to server at " + host + ":" + port +
                                "\nMake sure the server is running first.",
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}