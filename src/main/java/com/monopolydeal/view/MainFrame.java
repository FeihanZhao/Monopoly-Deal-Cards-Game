package com.monopolydeal.view;

import javax.swing.*;
import java.awt.*;
import com.monopolydeal.client.GameClient;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 主窗口 - 游戏客户端的主界面容器
 *
 * 使用 CardLayout 管理两个主要面板：
 * 1. LobbyPanel（大厅面板）- 创建/加入房间、准备
 * 2. GamePanel（游戏面板）- 实际的游戏界面
 *
 * 作为客户端消息处理的中转站，负责：
 * - 注册为GameClient的消息处理器
 * - 根据消息类型（ROOM_UPDATE/GAME_STATE_UPDATE/GAME_OVER/ERROR）将消息路由到对应面板
 * - 自动切换显示面板（从大厅切换到游戏，或游戏结束后返回大厅）
 *
 * UI配置：
 * - 默认窗口大小：1280×800
 * - 最小窗口大小：1024×768
 * - 点击关闭按钮时退出程序（EXIT_ON_CLOSE）
 */
public class MainFrame extends JFrame {
    /** 游戏客户端连接 */
    private final GameClient client;
    /** 卡片布局管理器（用于切换大厅/游戏面板） */
    private CardLayout cardLayout;
    /** 主面板（包含所有子面板的容器） */
    private JPanel mainPanel;
    /** 大厅面板 */
    private LobbyPanel lobbyPanel;
    /** 游戏面板 */
    private GamePanel gamePanel;
    /** 本地玩家ID（在收到服务器消息后设置） */
    private String localPlayerId;

    /**
     * 构造函数 - 初始化UI并设置消息处理器
     * @param client 已连接的GameClient实例
     */
    public MainFrame(GameClient client) {
        this.client = client;
        this.localPlayerId = null;
        initializeUI();         // 构建Swing界面
        setupMessageHandler();  // 注册消息处理回调
    }

    /**
     * 初始化Swing UI界面
     * 创建CardLayout容器，加入LobbyPanel和GamePanel，默认显示大厅
     */
    private void initializeUI() {
        setTitle("Monopoly Deal Cards Game");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);  // 关闭窗口时退出程序
        setSize(1280, 800);
        setMinimumSize(new Dimension(1024, 768));
        setLocationRelativeTo(null);  // 窗口居中显示

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        lobbyPanel = new LobbyPanel(client);   // 大厅面板
        gamePanel = new GamePanel(client);     // 游戏面板

        mainPanel.add(lobbyPanel, "LOBBY");
        mainPanel.add(gamePanel, "GAME");
        add(mainPanel);

        cardLayout.show(mainPanel, "LOBBY");  // 默认显示大厅
    }

    /**
     * 设置消息处理器 - 注册到GameClient以接收服务器消息
     *
     * 消息路由规则：
     * - ROOM_UPDATE → 转发给LobbyPanel更新房间状态
     * - GAME_STATE_UPDATE → 切换到游戏面板并更新游戏状态
     * - GAME_OVER → 显示获胜信息并返回大厅
     * - ERROR → 弹出错误对话框
     *
     * 所有UI操作都通过SwingUtilities.invokeLater确保在EDT线程执行
     */
    private void setupMessageHandler() {
        client.setMessageHandler(message -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    MessageProtocol.MessageType type = MessageProtocol.getType(message);
                    String payload = MessageProtocol.getPayload(message);

                    switch (type) {
                        case ROOM_UPDATE:
                            // 大厅面板更新房间信息（玩家列表、准备状态等）
                            lobbyPanel.updateRoom(payload);
                            break;
                        case GAME_STATE_UPDATE:
                            // 切换到游戏面板并更新游戏状态
                            cardLayout.show(mainPanel, "GAME");
                            gamePanel.updateGameState(payload);
                            break;
                        case GAME_OVER:
                            // 游戏结束，显示获胜者并返回大厅
                            handleGameOver(payload);
                            break;
                        case ERROR:
                            // 显示错误消息
                            handleError(payload);
                            break;
                        default:
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("处理消息时出错：" + e.getMessage());
                }
            });
        });
    }

    /**
     * 处理游戏结束消息 - 弹出获胜信息对话框并返回大厅
     * @param payload GAME_OVER消息的JSON负载，包含winnerNickname等字段
     */
    private void handleGameOver(String payload) {
        try {
            JsonObject result = JsonParser.parseString(payload).getAsJsonObject();
            String winnerNickname = result.get("winnerNickname").getAsString();
            JOptionPane.showMessageDialog(this, "游戏结束！\n获胜者：" + winnerNickname);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "游戏结束！");
        }
        cardLayout.show(mainPanel, "LOBBY");  // 返回大厅
    }

    /**
     * 处理错误消息 - 弹出错误对话框
     * @param payload ERROR消息的JSON负载，包含message字段
     */
    private void handleError(String payload) {
        try {
            JsonObject error = JsonParser.parseString(payload).getAsJsonObject();
            String errorMessage = error.has("message") ?
                    error.get("message").getAsString() : "未知错误";
            JOptionPane.showMessageDialog(this, errorMessage, "错误", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "发生了一个错误", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }
}
