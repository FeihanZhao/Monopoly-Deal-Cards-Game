package com.monopolydeal.server;

import java.io.*;
import java.net.*;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import java.util.UUID;

/**
 * 客户端处理器 - 负责单个客户端连接的所有消息收发
 *
 * 每个连接的客户端对应一个ClientHandler实例，运行在独立的线程中。
 * 实现Runnable接口，在run()方法中循环读取客户端发来的消息。
 *
 * 消息处理流程：
 * 1. 从Socket读取一行JSON消息
 * 2. 解析MessageType（CREATE_ROOM/JOIN_ROOM/PLAY_CARD等）
 * 3. 根据消息类型调用相应的处理方法
 * 4. 处理方法通过GameServer找到对应的GameRoom，委托给GameRoom或GameSession处理
 *
 * 支持的消息类型：
 * - 房间管理：CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, PLAYER_READY
 * - 游戏操作：PLAY_CARD, END_TURN
 * - 通信：CHAT_MESSAGE, PING
 */
public class ClientHandler implements Runnable {
    /** 客户端唯一标识符（由服务器在连接时分配） */
    private final String clientId;
    /** 客户端Socket连接 */
    private final Socket socket;
    /** 所属的游戏服务器 */
    private final GameServer server;
    /** 输出流 - 向客户端发送消息 */
    private PrintWriter out;
    /** 输入流 - 从客户端读取消息 */
    private BufferedReader in;
    /** 玩家昵称（在创建/加入房间时设定） */
    private String nickname;
    /** 当前所在的房间代码（null表示未加入任何房间） */
    private String currentRoom;

    /**
     * 构造函数
     * @param clientId 客户端唯一标识符
     * @param socket 客户端Socket连接
     * @param server 所属游戏服务器
     */
    public ClientHandler(String clientId, Socket socket, GameServer server) {
        this.clientId = clientId;
        this.socket = socket;
        this.server = server;
    }

    /**
     * 线程主方法 - 循环读取客户端消息
     * 当连接断开或发生IO异常时退出循环并断开连接
     */
    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);  // autoFlush=true

            String message;
            while ((message = in.readLine()) != null) {
                handleMessage(message);  // 逐行处理JSON消息
            }
        } catch (IOException e) {
            System.out.println("客户端 " + clientId.substring(0, 8) + " 已断开连接");
        } finally {
            disconnect();  // 确保资源清理
        }
    }

    /**
     * 处理单条JSON消息 - 解析消息类型并分派到对应处理方法
     * @param jsonMessage 完整的JSON消息字符串（格式：{"type":"MESSAGE_TYPE","payload":{...}}）
     */
    private void handleMessage(String jsonMessage) {
        try {
            MessageProtocol.MessageType type = MessageProtocol.getType(jsonMessage);
            JsonObject payload = JsonParser.parseString(MessageProtocol.getPayload(jsonMessage)).getAsJsonObject();

            switch (type) {
                case CREATE_ROOM:
                    handleCreateRoom(payload);     // 创建房间请求
                    break;
                case JOIN_ROOM:
                    handleJoinRoom(payload);       // 加入房间请求
                    break;
                case LEAVE_ROOM:
                    handleLeaveRoom();             // 离开房间请求
                    break;
                case PLAYER_READY:
                    handlePlayerReady(payload);    // 玩家准备状态切换
                    break;
                case PLAY_CARD:
                    handlePlayCard(payload);       // 出牌请求
                    break;
                case END_TURN:
                    handleEndTurn();               // 结束回合请求
                    break;
                case FLIP_WILD_CARD:
                    handleFlipWildCard(payload);   // 切换万能地产颜色
                    break;
                case SUBMIT_PAYMENT:
                    handleSubmitPayment(payload);  // 提交支付卡牌选择
                    break;
                case PLAY_JUST_SAY_NO:
                    handlePlayJustSayNo(payload);  // 打出 Just Say No 拒绝行动
                    break;
                case PASS_REACTION:
                    handlePassReaction(payload);   // 放弃响应（不打 Just Say No）
                    break;
                case PING:
                    sendMessage(MessageProtocol.MessageType.PONG, "{}");
                    break;
                case CHAT_MESSAGE:
                    handleChatMessage(payload);
                    break;
                default:
                    System.out.println("未知消息类型：" + type);
            }
        } catch (Exception e) {
            System.err.println("处理消息时出错：" + e.getMessage());
            e.printStackTrace();
            sendError("内部服务器错误：" + e.getMessage());
        }
    }

    /** 处理创建房间请求 - 生成6位房间代码，创建GameRoom */
    private void handleCreateRoom(JsonObject payload) {
        String rawNickname = payload.get("nickname").getAsString();
        if (!isValidNickname(rawNickname)) {
            sendError("昵称无效：长度需为1-12个字符，不能包含特殊字符");
            return;
        }
        this.nickname = rawNickname.trim();
        String roomCode = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        GameRoom room = server.createRoom(roomCode, this);
        this.currentRoom = roomCode;
        System.out.println("房间已创建：" + roomCode + "，创建者：" + nickname);
        room.broadcastRoomUpdate();  // 通知房间内所有玩家房间状态更新
    }

    /** 处理加入房间请求 - 通过房间代码查找并加入现有房间 */
    private void handleJoinRoom(JsonObject payload) {
        String rawNickname = payload.get("nickname").getAsString();
        if (!isValidNickname(rawNickname)) {
            sendError("昵称无效：长度需为1-12个字符，不能包含特殊字符");
            return;
        }
        this.nickname = rawNickname.trim();
        String roomCode = payload.get("roomCode").getAsString();
        GameRoom room = server.getRoom(roomCode);

        if (room == null) {
            sendError("未找到该房间");
            return;
        }

        if (!room.addPlayer(this)) {
            sendError("房间已满");
            return;
        }

        this.currentRoom = roomCode;
        System.out.println(nickname + " 加入了房间：" + roomCode);
        room.broadcastRoomUpdate();
    }

    /** 处理离开房间请求 - 从房间中移除自己并通知其他玩家 */
    private void handleLeaveRoom() {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.removePlayer(clientId);
            }
            currentRoom = null;
        }
    }

    /** 处理玩家准备状态切换 - 在大厅中标记"准备"或"取消准备" */
    private void handlePlayerReady(JsonObject payload) {
        boolean ready = payload.get("ready").getAsBoolean();
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.setPlayerReady(clientId, ready);
            }
        }
    }

    /** 处理出牌请求 - 委托给GameSession执行游戏逻辑 */
    private void handlePlayCard(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handlePlayCard(clientId, payload);
            }
        }
    }

    /** 处理结束回合请求 - 委托给GameSession执行 */
    private void handleEndTurn() {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().endTurn(clientId);
            }
        }
    }

    /** 处理切换万能地产颜色请求 - 委托给GameSession，不消耗出牌次数 */
    private void handleFlipWildCard(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                String cardId = payload.get("cardId").getAsString();
                String newColor = payload.get("color").getAsString();
                room.getGameSession().handleFlipWildCard(clientId, cardId, newColor);
            }
        }
    }

    /** 处理打出 Just Say No — 委托给 GameSession */
    private void handlePlayJustSayNo(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handlePlayJustSayNo(clientId, payload);
            }
        }
    }

    /** 处理放弃响应 — 委托给 GameSession */
    private void handlePassReaction(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handlePassReaction(clientId);
            }
        }
    }

    /** 处理提交支付请求 - 委托给GameSession执行支付校验和转账 */
    private void handleSubmitPayment(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null && room.getGameSession() != null) {
                room.getGameSession().handleSubmitPayment(clientId, payload);
            }
        }
    }

    /** 处理聊天消息 - 向房间内所有玩家广播 */
    private void handleChatMessage(JsonObject payload) {
        if (currentRoom != null) {
            GameRoom room = server.getRoom(currentRoom);
            if (room != null) {
                room.broadcast(MessageProtocol.MessageType.CHAT_MESSAGE, payload.toString());
            }
        }
    }

    /**
     * 向此客户端发送一条消息
     * @param type 消息类型
     * @param payload 消息负载（JSON字符串）
     */
    public void sendMessage(MessageProtocol.MessageType type, String payload) {
        if (out != null) {
            String message = MessageProtocol.createMessage(type, payload);
            out.println(message);
            out.flush();
        }
    }

    /** 发送错误消息给客户端 */
    private void sendError(String errorMessage) {
        JsonObject error = new JsonObject();
        error.addProperty("message", errorMessage);
        sendMessage(MessageProtocol.MessageType.ERROR, error.toString());
    }

    // ==================== Getters/Setters ====================

    public String getClientId() { return clientId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getCurrentRoom() { return currentRoom; }

    /** 断开连接 - 离开房间、从服务器移除并关闭Socket */
    public void disconnect() {
        handleLeaveRoom();
        server.removeClient(clientId);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // 关闭Socket时的异常可以忽略
        }
    }

    /** 校验昵称合法性：非空、长度1-12、仅允许中英文数字下划线 */
    private boolean isValidNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) return false;
        String trimmed = nickname.trim();
        if (trimmed.length() < 1 || trimmed.length() > 12) return false;
        return trimmed.matches("[\\u4e00-\\u9fa5a-zA-Z0-9_]+");
    }
}
