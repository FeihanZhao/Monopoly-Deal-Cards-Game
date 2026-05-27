package com.monopolydeal.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.GameConstants;

public class GameServer {
    private final int port;
    private ServerSocket serverSocket;
    private boolean running;
    private final Map<String, GameRoom> rooms;
    private final Map<String, ClientHandler> clients;
    private final ExecutorService threadPool;

    public GameServer(int port) {
        this.port = port;
        this.rooms = new ConcurrentHashMap<>();
        this.clients = new ConcurrentHashMap<>();
        this.threadPool = Executors.newCachedThreadPool();
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Game Server started on port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                String clientId = UUID.randomUUID().toString();
                ClientHandler handler = new ClientHandler(clientId, clientSocket, this);
                clients.put(clientId, handler);
                threadPool.execute(handler);
                System.out.println("New client connected: " + clientId.substring(0, 8));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server socket: " + e.getMessage());
        }
        threadPool.shutdown();
    }

    public GameRoom createRoom(String roomCode, ClientHandler creator) {
        GameRoom room = new GameRoom(roomCode, creator);
        rooms.put(roomCode, room);
        return room;
    }

    public GameRoom getRoom(String roomCode) {
        return rooms.get(roomCode);
    }


    public void removeRoom(String roomCode) {
        rooms.remove(roomCode);
    }

    public void removeClient(String clientId) {
        clients.remove(clientId);
    }

    public ClientHandler getClient(String clientId) {
        return clients.get(clientId);
    }

    public static void main(String[] args) {
        GameServer server = new GameServer(GameConstants.SERVER_PORT);
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            System.exit(1);
        }
    }
}