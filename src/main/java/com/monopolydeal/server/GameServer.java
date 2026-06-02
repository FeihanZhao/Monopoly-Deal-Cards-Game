package com.monopolydeal.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.GameConstants;

/**
 * Game server — TCP server that accepts client connections and manages game rooms.
 *
 * Core of the server architecture, responsible for:
 * 1. Listening on a specified port and accepting client socket connections
 * 2. Creating a ClientHandler thread for each client to process messages
 * 3. Managing all game rooms (GameRoom) — creation, lookup, and teardown
 * 4. Using a thread pool to manage client connection threads
 *
 * Startup:
 * - java -cp target/classes com.monopolydeal.server.GameServer
 * - Or via MonopolyDealApplication with the --server flag
 */
public class GameServer {
    /** Server listening port */
    private final int port;
    /** Server socket */
    private ServerSocket serverSocket;
    /** Whether the server is running */
    private boolean running;
    /** Game rooms map: key=room code (6 uppercase chars), value=GameRoom */
    private final Map<String, GameRoom> rooms;
    /** Online clients map: key=clientId, value=ClientHandler */
    private final Map<String, ClientHandler> clients;
    /** Thread pool — cached thread pool for managing client connections */
    private final ExecutorService threadPool;

    /**
     * Constructor.
     * @param port listening port
     */
    public GameServer(int port) {
        this.port = port;
        this.rooms = new ConcurrentHashMap<>();  // Thread-safe HashMap
        this.clients = new ConcurrentHashMap<>();
        this.threadPool = Executors.newCachedThreadPool();  // Auto-scaling thread pool
    }

    /**
     * Start the server.
     * Begins listening on the port and continuously accepts client connections.
     * Each new client is assigned a UUID as clientId and starts an independent ClientHandler thread.
     *
     * @throws IOException if unable to bind the port
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("Game server started on port " + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();  // Block waiting for client connection
                String clientId = UUID.randomUUID().toString();  // Assign unique ID per client
                ClientHandler handler = new ClientHandler(clientId, clientSocket, this);
                clients.put(clientId, handler);
                threadPool.execute(handler);  // Run client handler in thread pool
                System.out.println("New client connected: " + clientId.substring(0, 8));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }
        }
    }

    /** Stop the server — close the ServerSocket and shut down the thread pool */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing ServerSocket: " + e.getMessage());
        }
        threadPool.shutdown();
    }

    /**
     * Create a new game room.
     * @param roomCode room code
     * @param creator room creator (host)
     * @return the newly created GameRoom
     */
    public GameRoom createRoom(String roomCode, ClientHandler creator) {
        GameRoom room = new GameRoom(roomCode, creator, this);
        rooms.put(roomCode, room);
        return room;
    }

    /** Get a game room by its room code */
    public GameRoom getRoom(String roomCode) {
        return rooms.get(roomCode);
    }

    /** Remove a game room (when there are no players left) */
    public void removeRoom(String roomCode) {
        rooms.remove(roomCode);
    }

    /** Remove a client connection record */
    public void removeClient(String clientId) {
        clients.remove(clientId);
    }

    /** Get a ClientHandler by client ID */
    public ClientHandler getClient(String clientId) {
        return clients.get(clientId);
    }

    /**
     * Standalone server startup (without using the MonopolyDealApplication entry point).
     */
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
