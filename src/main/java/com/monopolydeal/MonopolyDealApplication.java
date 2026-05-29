package com.monopolydeal;

import com.monopolydeal.client.*;
import com.monopolydeal.model.GameConstants;


import com.monopolydeal.view.*;

import com.monopolydeal.server.*;
import javax.swing.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 应用程序主入口类
 *
 * 支持三种启动模式：
 * 1. 启动服务器：java ... --server [port]
 * 2. 显式客户端：java ... --client [host] [port]
 * 3. 默认客户端：java ... [host] [port]（省略--client标志）
 *
 * 命令行参数说明：
 * - --server [port]：以服务器模式启动，监听指定端口（默认8888）
 * - --client host port：以客户端模式启动，连接到指定主机和端口
 * - 无参数或仅host/port：以客户端模式启动，连接到localhost:8888
 *
 * 使用SwingUtilities.invokeLater确保Swing GUI在事件分发线程中初始化。
 */
public class MonopolyDealApplication {
    private static final Logger logger = LogManager.getLogger(MonopolyDealApplication.class);

    /**
     * 程序入口
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--server")) {
            // 服务器模式：解析端口号并启动服务器
            int port = args.length > 1 ? parsePort(args[1]) : GameConstants.SERVER_PORT;
            startServer(port);
        } else {
            // 客户端模式：解析主机和端口并启动客户端
            ClientConfig config = parseClientConfig(args);
            startClient(config.host, config.port);
        }
    }

    /**
     * 启动游戏服务器
     * 如果启动失败，记录致命错误并退出程序
     * @param port 监听端口号
     */
    private static void startServer(int port) {
        GameServer server = new GameServer(port);
        try {
            server.start();
        } catch (Exception e) {
            logger.fatal("启动服务器失败", e);
            System.exit(1);
        }
    }

    /**
     * 启动游戏客户端
     * 在Swing事件分发线程中创建GameClient连接并显示主窗口
     * @param host 服务器主机地址
     * @param port 服务器端口号
     */
    private static void startClient(String host, int port) {
        SwingUtilities.invokeLater(() -> {
            try {
                GameClient client = new GameClient(host, port);
                MainFrame frame = new MainFrame(client);
                frame.setTitle("Monopoly Deal Cards Game - " + host + ":" + port);
                frame.setVisible(true);
            } catch (Exception e) {
                logger.error("连接服务器失败", e);
                JOptionPane.showMessageDialog(null,
                        "Connection failed: " + e.getMessage(),
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    /**
     * 解析客户端命令行参数
     * 支持两种格式：
     * - --client host port（显式客户端模式）
     * - host port（默认客户端模式，省略--client标志）
     *
     * @param args 命令行参数
     * @return 客户端配置（主机和端口）
     */
    private static ClientConfig parseClientConfig(String[] args) {
        if (args.length > 0 && args[0].equals("--client")) {
            // 显式客户端模式
            String host = args.length > 1 ? args[1] : GameConstants.DEFAULT_HOST;
            int port = args.length > 2 ? parsePort(args[2]) : GameConstants.SERVER_PORT;
            return new ClientConfig(host, port);
        }

        // 默认客户端模式（省略--client标志）
        String host = args.length > 0 ? args[0] : GameConstants.DEFAULT_HOST;
        int port = args.length > 1 ? parsePort(args[1]) : GameConstants.SERVER_PORT;
        return new ClientConfig(host, port);
    }

    /**
     * 解析端口号字符串
     * 如果格式不正确，回退到默认端口8888并记录警告
     * @param value 端口号字符串
     * @return 解析后的端口号（无效时返回默认值）
     */
    private static int parsePort(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warn("无效的端口号 '{}'，回退到 {}", value, GameConstants.SERVER_PORT);
            return GameConstants.SERVER_PORT;
        }
    }

    /**
     * 客户端配置内部类 - 封装主机和端口信息
     */
    private static class ClientConfig {
        /** 服务器主机地址 */
        private final String host;
        /** 服务器端口号 */
        private final int port;

        private ClientConfig(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }
}
