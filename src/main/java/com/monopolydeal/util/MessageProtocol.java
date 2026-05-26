package com.monopolydeal.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 消息协议类 - 定义客户端与服务器之间的通信协议
 *
 * 所有网络通信都使用JSON格式，每条消息的结构为：
 * {"type": "MESSAGE_TYPE", "payload": {...}}
 *
 * 消息类型枚举 MessageType 包含了所有支持的通信类型，分为以下几类：
 * - 房间管理：CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, ROOM_UPDATE, PLAYER_READY
 * - 游戏流程：START_GAME, GAME_STATE_UPDATE, DRAW_CARDS, PLAY_CARD, END_TURN
 * - 行动卡：PLAY_DEBT_COLLECTOR, PLAY_BIRTHDAY, PLAY_DEAL_BREAKER 等
 * - 系统消息：ERROR, PING, PONG, DISCONNECT, CHAT_MESSAGE
 *
 * 工具方法：
 * - createMessage()：构建JSON格式的消息
 * - getType()：从JSON消息中提取消息类型
 * - getPayload()：从JSON消息中提取负载数据
 */
public class MessageProtocol {
    /** Gson序列化器实例 */
    private static final Gson gson = new GsonBuilder().create();

    /**
     * 消息类型枚举 - 定义客户端与服务器之间所有可能的通信类型
     */
    public enum MessageType {
        // ===== 房间管理 =====
        CREATE_ROOM,       // 客户端请求创建房间
        JOIN_ROOM,         // 客户端请求加入房间
        LEAVE_ROOM,        // 客户端请求离开房间
        ROOM_UPDATE,       // 服务器广播房间状态更新
        PLAYER_JOINED,     // 服务器通知有玩家加入
        PLAYER_LEFT,       // 服务器通知有玩家离开
        PLAYER_READY,      // 客户端设置/取消准备状态

        // ===== 游戏流程 =====
        START_GAME,        // 服务器通知游戏开始
        GAME_STATE_UPDATE, // 服务器广播游戏状态更新（核心消息，包含完整GameState）
        GAME_STARTED,      // 服务器通知游戏已开始
        TURN_STARTED,      // 服务器通知新回合开始
        DRAW_CARDS,        // 服务器通知抽牌结果
        PLAY_CARD,         // 客户端请求出牌
        DISCARD_CARD,      // 客户端请求弃牌
        END_TURN,          // 客户端请求结束回合
        TURN_TIMEOUT,      // 服务器通知回合超时
        PLACE_PROPERTY,    // 客户端请求放置地产卡
        PLACE_MONEY,       // 客户端请求放置金钱卡
        FLIP_WILD_CARD,    // 客户端请求切换万能地产颜色（不消耗出牌次数）

        // ===== 具体行动类型 =====
        PLAY_RENT,              // 客户端使用租金卡
        PLAY_DEBT_COLLECTOR,    // 使用债务收集者
        PLAY_BIRTHDAY,          // 使用生日卡
        PLAY_DEAL_BREAKER,      // 使用强行交易
        PLAY_FORCED_DEAL,       // 使用强制交换
        PLAY_SLY_DEAL,          // 使用偷袭
        PLAY_DOUBLE_RENT,       // 使用双倍租金
        PLAY_HOUSE,             // 使用房屋卡
        PLAY_HOTEL,             // 使用酒店卡
        PLAY_JUST_SAY_NO,       // 使用拒绝卡

        // ===== 决议链 =====
        REACTION_REQUIRED,  // 服务器通知玩家可以打 Just Say No 响应
        PASS_REACTION,      // 客户端表示放弃响应（不打 Just Say No）

        // ===== 行动结果 =====
        ACTION_RESULT,     // 服务器返回行动执行结果
        PAYMENT_REQUIRED,  // 服务器要求玩家支付
        SUBMIT_PAYMENT,    // 客户端提交支付（选择要支付的金钱卡）
        PAYMENT_MADE,      // 服务器通知支付完成
        CARD_DRAWN,        // 服务器通知卡牌被抽取
        CARD_PLAYED,       // 服务器通知卡牌被打出
        CARD_DISCARDED,    // 服务器通知卡牌被弃掉

        // ===== 游戏结果 =====
        GAME_OVER,         // 服务器通知游戏结束（有玩家获胜）
        PLAYER_WON,        // 服务器通知玩家获胜
        GAME_DRAW,         // 服务器通知游戏平局（玩家不足）

        // ===== 系统消息 =====
        ERROR,             // 服务器返回错误信息
        INVALID_ACTION,    // 服务器通知操作无效
        CHAT_MESSAGE,      // 聊天消息（客户端到服务器广播）
        PING,              // 心跳请求
        PONG,              // 心跳回应
        DISCONNECT         // 服务器通知玩家断线
    }

    /**
     * 创建JSON格式的消息
     * @param type 消息类型
     * @param payload 消息负载（JSON字符串）
     * @return 完整的JSON消息字符串，格式：{"type":"...","payload":{...}}
     */
    public static String createMessage(MessageType type, String payload) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type.name());
        message.add("payload", JsonParser.parseString(payload));
        return gson.toJson(message);
    }

    /**
     * 创建JSON格式的消息（自动序列化对象为JSON）
     * @param type 消息类型
     * @param payload 消息负载对象（将自动转为JSON）
     * @return 完整的JSON消息字符串
     */
    public static String createMessage(MessageType type, Object payload) {
        JsonObject message = new JsonObject();
        message.addProperty("type", type.name());
        message.add("payload", gson.toJsonTree(payload));
        return gson.toJson(message);
    }

    /**
     * 从JSON消息字符串中提取消息类型
     * @param jsonMessage 完整的JSON消息字符串
     * @return 解析后的MessageType枚举值
     */
    public static MessageType getType(String jsonMessage) {
        JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
        return MessageType.valueOf(obj.get("type").getAsString());
    }

    /**
     * 从JSON消息字符串中提取负载数据（返回JSON字符串）
     * @param jsonMessage 完整的JSON消息字符串
     * @return 负载部分的JSON字符串
     */
    public static String getPayload(String jsonMessage) {
        JsonObject obj = JsonParser.parseString(jsonMessage).getAsJsonObject();
        return obj.get("payload").toString();
    }

    /**
     * 从JSON消息字符串中提取负载数据并反序列化为指定类型
     * @param jsonMessage 完整的JSON消息字符串
     * @param clazz 目标Java类型
     * @param <T> 泛型类型
     * @return 反序列化后的对象实例
     */
    public static <T> T getPayload(String jsonMessage, Class<T> clazz) {
        String payload = getPayload(jsonMessage);
        return gson.fromJson(payload, clazz);
    }
}
