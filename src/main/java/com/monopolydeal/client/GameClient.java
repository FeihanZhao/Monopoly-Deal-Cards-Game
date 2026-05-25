package com.monopolydeal.client;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;
import com.monopolydeal.util.MessageProtocol;
import com.monopolydeal.model.GameConstants;
import com.monopolydeal.view.MainFrame;
import javax.swing.*;

/**
 * 游戏客户端 - 管理与游戏服务器的Socket连接和消息收发
 *
 * 客户端架构：
 * 1. 通过TCP Socket连接到游戏服务器
 * 2. 在后台线程中持续读取服务器发来的消息
 * 3. 通过消息处理器（Consumer<String>）将收到的消息转发给UI层处理
 * 4. 提供 sendMessage() 方法向服务器发送消息
 *
 * 使用方式：
 * - 创建 GameClient 实例，指定服务器地址和端口
 * - 调用 setMessageHandler() 注册消息处理器（通常由MainFrame设置）
 * - 调用 sendMessage() 发送操作请求
 *
 * 设计要点：
 * - 消息读取在独立的后台线程中进行，不阻塞Swing事件线程
 * - 使用 newline 作为消息分隔符（每行一条完整JSON消息）
 * - 连接断开时自动清理资源
 */
public class GameClient {
    /** 与服务器的Socket连接 */
    private Socket socket;
    /** 输出流 - 向服务器发送消息 */
    private PrintWriter out;
    /** 输入流 - 从服务器接收消息 */
    private BufferedReader in;
    /** 玩家ID（服务器分配，在首次ROOM_UPDATE消息中设置） */
    private String playerId;
    /** 是否已连接到服务器 */
    private boolean connected;
    /** 消息处理器 - 收到服务器消息时的回调函数 */
    private Consumer<String> messageHandler;

    /**
     * 构造函数 - 建立与游戏服务器的连接
     * 连接成功后立即启动后台线程开始监听消息
     *
     * @param host 服务器主机地址
     * @param port 服务器端口号
     * @throws IOException 如果连接失败
     */
    public GameClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.out = new PrintWriter(socket.getOutputStream(), true);  // autoFlush=true
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.connected = true;
        // 启动后台线程监听服务器消息
        new Thread(this::listenForMessages).start();
    }

    /**
     * 后台消息监听线程 - 循环读取服务器消息
     * 每收到一行消息就调用 messageHandler 回调处理
     * 连接断开时自动调用 disconnect() 清理资源
     */
    private void listenForMessages() {
        try {
            String message;
            while (connected && (message = in.readLine()) != null) {
                if (messageHandler != null) {
                    messageHandler.accept(message);  // 转发给UI层处理
                }
            }
        } catch (IOException e) {
            if (connected) {
                System.err.println("与服务器的连接已断开：" + e.getMessage());
            }
        } finally {
            disconnect();
        }
    }

    /**
     * 注册消息处理器
     * 通常由MainFrame在构造函数中调用，用于接收并路由服务器消息
     * @param handler 消息处理回调（参数为完整JSON消息字符串）
     */
    public void setMessageHandler(Consumer<String> handler) {
        this.messageHandler = handler;
    }

    /**
     * 向服务器发送消息
     * @param type 消息类型
     * @param payload 消息负载（JSON字符串）
     */
    public void sendMessage(MessageProtocol.MessageType type, String payload) {
        if (connected && out != null) {
            out.println(MessageProtocol.createMessage(type, payload));
        }
    }

    /** 断开与服务器的连接并清理资源 */
    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("关闭Socket时出错：" + e.getMessage());
        }
    }

    // ==================== Getters/Setters ====================

    /** 检查是否与服务器保持连接 */
    public boolean isConnected() {
        return connected;
    }

    /** 设置玩家ID（由MainFrame在收到ROOM_UPDATE后设置） */
    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    /** 获取玩家ID */
    public String getPlayerId() {
        return playerId;
    }

    /**
     * 独立启动客户端（不使用MonopolyDealApplication入口）
     * 用法：java com.monopolydeal.client.GameClient [host] [port]
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                String host = args.length > 0 ? args[0] : GameConstants.DEFAULT_HOST;
                int port = args.length > 1 ? Integer.parseInt(args[1]) : GameConstants.SERVER_PORT;

                GameClient client = new GameClient(host, port);
                MainFrame frame = new MainFrame(client);
                frame.setVisible(true);
            } catch (Exception e) {
                System.err.println("连接服务器失败：" + e.getMessage());
                JOptionPane.showMessageDialog(null,
                        "无法连接到服务器 " +
                                (args.length > 0 ? args[0] : "localhost") + ":" +
                                GameConstants.SERVER_PORT +
                                "\n请确保服务器已启动。",
                        "连接错误",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
