package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 行动历史面板 - 显示游戏中的操作日志
 *
 * 位于游戏面板的右侧栏，以文本列表形式展示最近的游戏操作记录。
 * 每条记录包含：
 * - 时间戳（HH:mm:ss格式）
 * - 执行玩家昵称
 * - 操作描述（如"抽了3张牌"、"向所有玩家收取4M租金"）
 *
 * 支持的操作类型包括：抽牌、出牌、弃牌、收租、行动卡效果、
 * 房屋/酒店建造、断线/重连、游戏获胜等。
 *
 * 显示格式示例：
 * [14:32:15] 玩家A: 向所有玩家收取 4M 租金
 * [14:32:10] 玩家A: 使用了租金卡
 * [14:32:05] 玩家A: 抽了 3 张牌
 */
public class ActionHistoryPanel extends JPanel {
    /** 历史记录文本区域 */
    private final JTextArea historyArea;
    /** 玩家颜色缓存（按playerId分配不同颜色） */
    private final Map<String, Color> playerColors;
    /** 颜色轮换数组 */
    private final Color[] colors = {
            new Color(255, 140, 100),  // 暖橙色
            new Color(100, 255, 140),  // 翠绿色
            new Color(100, 180, 255),  // 天蓝色
            new Color(255, 255, 100),  // 亮黄色
            new Color(255, 140, 255),  // 品红色
            new Color(100, 255, 255)   // 青色
    };
    /** 当前颜色索引（用于为新玩家分配颜色） */
    private int colorIndex;
    /** 时间格式化器（HH:mm:ss） */
    private final SimpleDateFormat timeFormat;

    /**
     * 构造函数 - 创建深色主题的文本区域
     */
    public ActionHistoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 35, 42));
        // 带标题边框
        setBorder(new TitledBorder(
                BorderFactory.createLineBorder(new Color(60, 65, 75)),
                "行动记录",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12),
                new Color(200, 200, 200)
        ));

        playerColors = new HashMap<>();
        colorIndex = 0;
        timeFormat = new SimpleDateFormat("HH:mm:ss");

        // 只读文本区域
        historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setBackground(new Color(30, 35, 42));
        historyArea.setForeground(new Color(200, 200, 200));
        historyArea.setFont(new Font("Monospaced", Font.PLAIN, 11));
        historyArea.setLineWrap(true);      // 自动换行
        historyArea.setWrapStyleWord(true); // 按单词换行

        JScrollPane scrollPane = new JScrollPane(historyArea);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(30, 35, 42));

        add(scrollPane, BorderLayout.CENTER);
    }

    /**
     * 更新行动历史显示
     * 从JSON数组中解析每条行动记录并格式化显示
     *
     * @param actions 行动记录JSON数组
     */
    public void updateActions(JsonArray actions) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < actions.size(); i++) {
            JsonObject action = actions.get(i).getAsJsonObject();
            String playerId = action.has("playerId") ?
                    action.get("playerId").getAsString() : "";
            String nickname = action.has("playerNickname") ?
                    action.get("playerNickname").getAsString() : "未知";
            String actionType = action.has("actionType") ?
                    action.get("actionType").getAsString() : "";
            String target = action.has("targetPlayer") &&
                    !action.get("targetPlayer").isJsonNull() ?
                    action.get("targetPlayer").getAsString() : "";
            int amount = action.has("amount") ? action.get("amount").getAsInt() : 0;
            long timestamp = action.has("timestamp") ?
                    action.get("timestamp").getAsLong() : System.currentTimeMillis();

            // 格式化时间
            String timeStr = timeFormat.format(new Date(timestamp));

            // 格式化为可读描述
            String actionDesc = formatAction(actionType, target, amount);

            // 构建输出行：[时间] 昵称: 操作描述
            sb.append(String.format("[%s] %s: %s", timeStr, nickname, actionDesc));
            sb.append("\n");
        }

        historyArea.setText(sb.toString());
        historyArea.setCaretPosition(0);  // 滚动到顶部
    }

    /**
     * 将操作类型转换为可读的中文描述
     *
     * @param actionType 操作类型（如"RENT"、"DEAL_BREAKER"等）
     * @param target 目标玩家昵称
     * @param amount 涉及金额
     * @return 格式化的中文描述文本
     */
    private String formatAction(String actionType, String target, int amount) {
        switch (actionType) {
            case "DRAW":
                return "抽了牌";
            case "PLAY_MONEY":
                return "打出了金钱卡";
            case "PLAY_PROPERTY":
                return "打出了地产卡";
            case "PLAY_RENT":
                return "打出了租金卡";
            case "PLAY_ACTION":
                return "打出了行动卡";
            case "RENT":
                return "向 " + (target.isEmpty() ? "" : target + " ") + "收取了 " + amount + "M 租金";
            case "RENT_ALL":
                return "向所有玩家收取了 " + amount + "M 租金";
            case "DEBT_COLLECTOR":
                return "从 " + target + " 收取了 " + amount + "M";
            case "BIRTHDAY":
                return "所有人各支付 " + amount + "M";
            case "DEAL_BREAKER":
                return "偷取了 " + target + " 的完整地产组合";
            case "PASS_GO":
                return "额外抽了2张牌";
            case "DOUBLE_RENT":
                return "下次租金翻倍";
            case "HOUSE":
                return "建造了一栋房屋";
            case "HOTEL":
                return "建造了一座酒店";
            case "FORCED_DEAL":
                return "与 " + target + " 交换了地产卡";
            case "SLY_DEAL":
                return "偷取了 " + target + " 的地产卡";
            case "JUST_SAY_NO":
                return "使用了拒绝卡";
            case "PAY":
                return "支付了 " + amount + "M 给 " + target;
            case "PARTIAL_PAY":
                return "支付了 " + amount + "M（部分）给 " + target;
            case "DISCARD":
                return "弃掉了一张牌";
            case "END_TURN":
                return "回合结束";
            case "DISCONNECT":
                return "断开了连接";
            case "RECONNECT":
                return "重新连接";
            case "WINNER":
                return "赢得了游戏！";
            case "DRAW_EXTRA":
                return "额外抽了2张牌";
            default:
                return actionType;
        }
    }
}
