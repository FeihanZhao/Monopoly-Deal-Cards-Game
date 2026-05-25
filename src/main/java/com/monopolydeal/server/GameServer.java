package com.monopolydeal.server;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.GameConstants;

/**
 * 游戏服务器 - TCP服务器，接受客户端连接并管理游戏房间
 *
 * 服务端架构核心，负责：
 * 1. 监听指定端口，接受客户端Socket连接
 * 2. 为每个客户端创建 ClientHandler 线程处理消息
 * 3. 管理所有游戏房间（GameRoom）的创建、查询和销毁
 * 4. 使用线程池管理客户端连接线程
 *
 * 启动方式：
 * - java -cp target/classes com.monopolydeal.server.GameServer
 * - 或通过 MonopolyDealApplication 的 --server 参数启动
 */
public class GameServer {
    /** 服务器监听端口号 */
    private final int port;
    /** 服务器Socket */
    private ServerSocket serverSocket;
    /** 服务器是否正在运行 */
    private boolean running;
    /** 游戏房间映射表 key=房间代码(6位大写), value=GameRoom */
    private final Map<String, GameRoom> rooms;
    /** 在线客户端映射表 key=clientId, value=ClientHandler */
    private final Map<String, ClientHandler> clients;
    /** 线程池 - 使用缓存线程池管理客户端连接 */
    private final ExecutorService threadPool;

    /**
     * 构造函数
     * @param port 监听端口号
     */
    public GameServer(int port) {
        this.port = port;
        this.rooms = new ConcurrentHashMap<>();  // 线程安全的HashMap
        this.clients = new ConcurrentHashMap<>();
        this.threadPool = Executors.newCachedThreadPool();  // 自动扩缩容的线程池
    }

    /**
     * 启动服务器
     * 开始监听端口并持续接受客户端连接
     * 每个新连接的客户端会分配一个UUID作为clientId，并启动独立的ClientHandler线程
     *
     * @throws IOException 如果无法绑定端口
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("游戏服务器已启动，端口：" + port);

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();  // 阻塞等待客户端连接
                String clientId = UUID.randomUUID().toString();  // 为每个客户端分配唯一ID
                ClientHandler handler = new ClientHandler(clientId, clientSocket, this);
                clients.put(clientId, handler);
                threadPool.execute(handler);  // 在线程池中运行客户端处理器
                System.out.println("新客户端已连接：" + clientId.substring(0, 8));
            } catch (IOException e) {
                if (running) {
                    System.err.println("接受客户端连接时出错：" + e.getMessage());
                }
            }
        }
    }

    /** 停止服务器，关闭ServerSocket并关闭线程池 */
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("关闭ServerSocket时出错：" + e.getMessage());
        }
        threadPool.shutdown();
    }

    /**
     * 创建新游戏房间
     * @param roomCode 房间代码
     * @param creator 房间创建者（房主）
     * @return 新创建的GameRoom
     */
    public GameRoom createRoom(String roomCode, ClientHandler creator) {
        GameRoom room = new GameRoom(roomCode, creator);
        rooms.put(roomCode, room);
        return room;
    }

    /** 根据房间代码获取游戏房间 */
    public GameRoom getRoom(String roomCode) {
        return rooms.get(roomCode);
    }

    /** 移除游戏房间（当房间内没有玩家时） */
    public void removeRoom(String roomCode) {
        rooms.remove(roomCode);
    }

    /** 移除客户端连接记录 */
    public void removeClient(String clientId) {
        clients.remove(clientId);
    }

    /** 根据客户端ID获取ClientHandler */
    public ClientHandler getClient(String clientId) {
        return clients.get(clientId);
    }

    /**
     * 单独启动服务器（不使用MonopolyDealApplication入口）
     */
    public static void main(String[] args) {
        GameServer server = new GameServer(GameConstants.SERVER_PORT);
        try {
            server.start();
        } catch (IOException e) {
            System.err.println("启动服务器失败：" + e.getMessage());
            System.exit(1);
        }
    }
}
