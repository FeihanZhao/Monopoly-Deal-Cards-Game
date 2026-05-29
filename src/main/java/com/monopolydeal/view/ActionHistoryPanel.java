package com.monopolydeal.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.*;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 行动历史面板 — 以颜色编码列表展示游戏操作日志
 *
 * 每条记录显示为一行，包含：
 * - 灰色时间戳（HH:mm:ss）
 * - 彩色玩家昵称（每个玩家分配固定颜色）
 * - 白色操作描述
 *
 * 自动滚动到最新记录，但当用户主动上滚查看历史时不强制跳回。
 */
public class ActionHistoryPanel extends JPanel {

    /** 列表数据模型 */
    private final DefaultListModel<ActionEntry> listModel;
    /** 行动记录列表 */
    private final JList<ActionEntry> actionList;
    /** 玩家颜色缓存（按玩家昵称分配颜色） */
    private final Map<String, Color> playerColors;
    /** 颜色轮盘 */
    private static final Color[] COLORS = {
            new Color(255, 140, 100),
            new Color(100, 255, 140),
            new Color(100, 180, 255),
            new Color(255, 215, 0),
            new Color(255, 140, 255),
            new Color(100, 255, 255),
    };
    private int colorIndex;

    /** 时间格式化器 */
    private final SimpleDateFormat timeFormat;

    // ==================== 内部数据类 ====================

    /** 单条行动记录 */
    private static class ActionEntry {
        final String timeStr;
        final String nickname;
        final String actionDesc;

        ActionEntry(String timeStr, String nickname, String actionDesc) {
            this.timeStr   = timeStr;
            this.nickname  = nickname;
            this.actionDesc = actionDesc;
        }
    }

    // ==================== 自定义渲染器 ====================

    /** 行动记录列表项渲染器 — 时间灰色 + 玩家名彩色 + 描述白色 */
    private class ActionRenderer extends JPanel implements ListCellRenderer<ActionEntry> {
        private final JLabel label;

        ActionRenderer() {
            setLayout(new BorderLayout());
            setOpaque(false);
            label = new JLabel();
            label.setOpaque(false);
            label.setFont(new Font("SansSerif", Font.PLAIN, 11));
            label.setBorder(new EmptyBorder(1, 4, 1, 4));
            add(label, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(
                JList<? extends ActionEntry> list, ActionEntry value, int index,
                boolean isSelected, boolean cellHasFocus) {

            if (value == null) {
                label.setText("");
                return this;
            }

            // 构建带颜色标注的文本：时间(灰) | 昵称(彩色) : 操作(白)
            Color playerColor = getPlayerColor(value.nickname);
            String html = String.format(
                    "<html><span style='color:#999999'>[%s]</span> "
                            + "<span style='color:rgb(%d,%d,%d)'>%s</span>"
                            + "<span style='color:#CCCCCC'>: %s</span></html>",
                    value.timeStr,
                    playerColor.getRed(), playerColor.getGreen(), playerColor.getBlue(),
                    escapeHtml(value.nickname),
                    escapeHtml(value.actionDesc));

            label.setText(html);
            setBackground(isSelected ? new Color(60, 70, 90) : null);

            return this;
        }

        /** 转义 HTML 特殊字符 */
        private String escapeHtml(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    // ==================== 构造函数 ====================

    public ActionHistoryPanel() {
        setLayout(new BorderLayout());
        setBackground(new Color(30, 35, 42));
        setBorder(new TitledBorder(
                BorderFactory.createLineBorder(new Color(60, 65, 75)),
                "行动记录",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 12),
                new Color(200, 200, 200)
        ));

        playerColors = new HashMap<>();
        colorIndex   = 0;
        timeFormat   = new SimpleDateFormat("HH:mm:ss");

        listModel  = new DefaultListModel<>();
        actionList = new JList<>(listModel);
        actionList.setCellRenderer(new ActionRenderer());
        actionList.setBackground(new Color(30, 35, 42));
        actionList.setSelectionBackground(new Color(60, 70, 90));
        actionList.setSelectionForeground(Color.WHITE);
        actionList.setFixedCellHeight(18);
        actionList.setVisibleRowCount(0);

        JScrollPane scrollPane = new JScrollPane(actionList);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(new Color(30, 35, 42));

        add(scrollPane, BorderLayout.CENTER);
    }

    // ==================== 公共 API ====================

    /**
     * 更新行动历史 — 由 GamePanel 在每次 GAME_STATE_UPDATE 中调用
     * @param actions 行动记录 JSON 数组（格式不变）
     */
    public void updateActions(JsonArray actions) {
        // 判断用户是否正在查看历史（不在底部 = 用户上滚）
        boolean autoScroll = isScrolledToBottom();

        listModel.clear();

        for (int i = 0; i < actions.size(); i++) {
            JsonObject action = actions.get(i).getAsJsonObject();
            String nickname = action.has("playerNickname")
                    ? action.get("playerNickname").getAsString() : "未知";
            String actionType = action.has("actionType")
                    ? action.get("actionType").getAsString() : "";
            String target = action.has("targetPlayer") && !action.get("targetPlayer").isJsonNull()
                    ? action.get("targetPlayer").getAsString() : "";
            int amount = action.has("amount") ? action.get("amount").getAsInt() : 0;
            long timestamp = action.has("timestamp")
                    ? action.get("timestamp").getAsLong() : System.currentTimeMillis();

            String timeStr = timeFormat.format(new Date(timestamp));
            String actionDesc = formatAction(actionType, target, amount);

            listModel.addElement(new ActionEntry(timeStr, nickname, actionDesc));
        }

        // 自动滚到底部（仅当用户之前已在底部）
        if (autoScroll && listModel.getSize() > 0) {
            actionList.ensureIndexIsVisible(listModel.getSize() - 1);
        }
    }

    /** 检测滚动条是否在底部 */
    private boolean isScrolledToBottom() {
        JScrollPane sp = null;
        Container parent = actionList.getParent();
        while (parent != null) {
            if (parent instanceof JScrollPane) { sp = (JScrollPane) parent; break; }
            parent = parent.getParent();
        }
        if (sp == null) return true;
        JScrollBar bar = sp.getVerticalScrollBar();
        int current = bar.getValue();
        int extent  = bar.getVisibleAmount();
        int max     = bar.getMaximum();
        return current + extent >= max - extent;
    }

    // ==================== 玩家颜色分配 ====================

    private Color getPlayerColor(String nickname) {
        return playerColors.computeIfAbsent(nickname, k -> {
            Color c = COLORS[colorIndex % COLORS.length];
            colorIndex++;
            return c;
        });
    }

    // ==================== 操作描述格式化（与旧版一致） ====================

    private String formatAction(String actionType, String target, int amount) {
        switch (actionType) {
            case "DRAW":          return "抽了牌";
            case "PLAY_MONEY":    return "打出了金钱卡";
            case "PLAY_PROPERTY": return "打出了地产卡";
            case "PLAY_RENT":     return "打出了租金卡";
            case "PLAY_ACTION":   return "打出了行动卡";
            case "RENT":          return "向 " + (target.isEmpty() ? "" : target + " ") + "收取了 " + amount + "M 租金";
            case "RENT_ALL":      return "向所有玩家收取了 " + amount + "M 租金";
            case "DEBT_COLLECTOR": return "从 " + target + " 收取了 " + amount + "M";
            case "BIRTHDAY":       return "所有人各支付 " + amount + "M";
            case "DEAL_BREAKER":   return "偷取了 " + target + " 的完整地产组合";
            case "PASS_GO":        return "额外抽了2张牌";
            case "DOUBLE_RENT":    return "下次租金翻倍";
            case "HOUSE":          return "建造了一栋房屋";
            case "HOTEL":          return "建造了一座酒店";
            case "FORCED_DEAL":    return "与 " + target + " 交换了地产卡";
            case "SLY_DEAL":       return "偷取了 " + target + " 的地产卡";
            case "JUST_SAY_NO":    return "使用了拒绝卡";
            case "PAY":            return "支付了 " + amount + "M 给 " + target;
            case "PARTIAL_PAY":    return "支付了 " + amount + "M（部分）给 " + target;
            case "DISCARD":        return "弃掉了一张牌";
            case "END_TURN":       return "回合结束";
            case "DISCONNECT":     return "断开了连接";
            case "RECONNECT":      return "重新连接";
            case "WINNER":         return "赢得了游戏！";
            case "DRAW_EXTRA":     return "额外抽了2张牌";
            default:               return actionType;
        }
    }
}
