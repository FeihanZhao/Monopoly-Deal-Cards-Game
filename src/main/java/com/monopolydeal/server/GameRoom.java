package com.monopolydeal.server;

import java.util.*;
import java.util.concurrent.*;
import com.monopolydeal.model.*;
import com.monopolydeal.util.MessageProtocol;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.Gson;

/**
 * 游戏房间 - 管理一组玩家从大厅到游戏开始的过程
 *
 * 生命周期：
 * 1. 房主创建房间（CREATE_ROOM），自动成为第一名玩家
 * 2. 其他玩家通过房间代码加入（JOIN_ROOM），最多支持MAX_PLAYERS人
 * 3. 玩家在大厅中点击"准备"（PLAYER_READY）
 * 4. 当所有玩家（>=MIN_PLAYERS）都准备就绪，自动开始游戏
 * 5. 创建 GameSession 并调用 start() 进入游戏流程
 *
 * 状态广播：
 * - 每次玩家加入/离开/准备状态变化时，向所有房间成员广播 ROOM_UPDATE
 * - ROOM_UPDATE 包含房间代码、玩家列表及其准备状态
 */
public class GameRoom {
    /** 房间代码（6位大写字母数字） */
    private final String roomCode;
    /** 房主（房间创建者） */
    private final ClientHandler creator;
    /** 房间内的所有玩家 key=clientId, value=ClientHandler */
    private final Map<String, ClientHandler> players;
    /** 玩家的准备状态 key=clientId, value=是否已准备 */
    private final Map<String, Boolean> readyStates;
    /** 游戏会话（游戏开始后创建） */
    private GameSession gameSession;
    /** 游戏是否已开始 */
    private boolean gameStarted;
    /** Gson序列化器 */
    private final Gson gson;
    /** 所属服务器（用于销毁空房间） */
    private final GameServer server;

    /**
     * 构造函数 - 创建新房间，房主自动加入
     * @param roomCode 房间代码
     * @param creator 房主
     */
    public GameRoom(String roomCode, ClientHandler creator, GameServer server) {
        this.roomCode = roomCode;
        this.creator = creator;
        this.players = new ConcurrentHashMap<>();
        this.readyStates = new ConcurrentHashMap<>();
        this.gameStarted = false;
        this.gson = new Gson();
        this.server = server;
        addPlayer(creator);  // 房主自动加入
    }

    /**
     * 添加玩家到房间
     * @param player 要加入的玩家
     * @return true=加入成功，false=房间已满
     */
    public boolean addPlayer(ClientHandler player) {
        if (players.size() >= GameConstants.MAX_PLAYERS) {
            return false;  // 房间已满，拒绝加入
        }
        players.put(player.getClientId(), player);
        readyStates.put(player.getClientId(), false);  // 初始未准备
        return true;
    }

    /**
     * 从房间移除玩家
     * 如果游戏已开始，通知GameSession处理玩家断线
     * 如果房间变空，标记游戏结束
     *
     * @param clientId 要移除的玩家ID
     */
    public void removePlayer(String clientId) {
        players.remove(clientId);
        readyStates.remove(clientId);

        // 如果游戏已开始，通知GameSession处理断线
        if (gameStarted && gameSession != null) {
            gameSession.handlePlayerDisconnect(clientId);
        }

        // 如果房间空了，清理游戏状态
        if (players.isEmpty()) {
            gameStarted = false;
            gameSession = null;
            server.removeRoom(roomCode);
        }

        broadcastRoomUpdate();  // 通知剩余玩家房间状态变化
    }

    /**
     * 设置玩家的准备状态
     * 当所有玩家（>=MIN_PLAYERS）都准备就绪且游戏尚未开始时，自动启动游戏
     *
     * @param clientId 玩家ID
     * @param ready true=准备，false=取消准备
     */
    public void setPlayerReady(String clientId, boolean ready) {
        readyStates.put(clientId, ready);
        broadcastRoomUpdate();

        // 检查是否可以开始游戏：人数满足最低要求 且 所有人都已准备
        if (allPlayersReady() && players.size() >= GameConstants.MIN_PLAYERS && !gameStarted) {
            startGame();
        }
    }

    /** 检查是否所有玩家都已准备 */
    private boolean allPlayersReady() {
        return players.size() >= GameConstants.MIN_PLAYERS &&
                readyStates.values().stream().allMatch(Boolean::booleanValue);
    }

    /**
     * 开始游戏 - 创建Player列表，初始化GameSession
     * 玩家信息从ClientHandler中提取（ID和昵称）
     */
    private void startGame() {
        gameStarted = true;
        List<Player> playerList = new ArrayList<>();
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            Player player = new Player(entry.getKey(), entry.getValue().getNickname());
            playerList.add(player);
        }

        gameSession = new GameSession(this, playerList);
        gameSession.start();  // 启动游戏会话（发牌、开始第一回合）
    }

    /**
     * 向所有玩家广播房间状态更新
     * 包含房间代码、玩家列表、准备状态、房主标识
     */
    public void broadcastRoomUpdate() {
        JsonObject roomState = new JsonObject();
        roomState.addProperty("roomCode", roomCode);
        roomState.addProperty("playerCount", players.size());
        roomState.addProperty("maxPlayers", GameConstants.MAX_PLAYERS);
        roomState.addProperty("gameStarted", gameStarted);

        // 构建玩家信息数组
        JsonArray playerArray = new JsonArray();
        for (Map.Entry<String, ClientHandler> entry : players.entrySet()) {
            JsonObject playerInfo = new JsonObject();
            playerInfo.addProperty("id", entry.getKey());
            playerInfo.addProperty("nickname", entry.getValue().getNickname());
            playerInfo.addProperty("ready", readyStates.getOrDefault(entry.getKey(), false));
            playerInfo.addProperty("isCreator", entry.getKey().equals(creator.getClientId()));
            playerArray.add(playerInfo);
        }
        roomState.add("players", playerArray);

        System.out.println("广播房间更新：" + roomCode + "，玩家数：" + players.size());
        broadcast(MessageProtocol.MessageType.ROOM_UPDATE, roomState.toString());
    }

    /** 向房间内所有玩家广播消息 */
    public void broadcast(MessageProtocol.MessageType type, String payload) {
        for (ClientHandler player : players.values()) {
            player.sendMessage(type, payload);
        }
    }

    /** 向指定玩家发送消息 */
    public void sendToPlayer(String clientId, MessageProtocol.MessageType type, String payload) {
        ClientHandler player = players.get(clientId);
        if (player != null) {
            player.sendMessage(type, payload);
        }
    }

    // ==================== Getters ====================

    public String getRoomCode() { return roomCode; }
    public GameSession getGameSession() { return gameSession; }
    public Map<String, ClientHandler> getPlayers() { return players; }
}
