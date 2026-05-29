package com.monopolydeal.model;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 玩家类 - 代表游戏中的一名玩家
 *
 * 每个玩家拥有：
 * - 基本信息：id（唯一标识）、nickname（昵称）、avatar（头像）、ready（准备状态）
 * - 手牌（hand）：持有的未打出的卡牌列表
 * - 银行（Bank）：存放已打出的金钱卡，用于支付
 * - 物业区（PropertyZone）：存放已打出的地产卡，按颜色分组
 * - 回合状态：playsUsed（已使用出牌数）、isActivePlayer（是否当前活跃玩家）
 * - 特殊状态：doubleRentActive（是否激活双倍租金效果）
 *
 * 设计要点：
 * - 使用 CopyOnWriteArrayList 存储手牌，支持并发安全遍历
 * - 银行和物业区都是组合对象，由Player持有
 * - equals/hashCode 基于玩家ID
 */
public class Player {
    /** 玩家唯一标识符（与网络连接的clientId对应） */
    private final String id;
    /** 玩家昵称（显示用） */
    private final String nickname;
    /** 玩家头像标识 */
    private String avatar;
    /** 是否已准备（在大厅中点击准备按钮） */
    private boolean ready;
    /** 是否已连接（网络连接状态） */
    private boolean connected;

    // ==================== 游戏状态 ====================

    /** 手牌列表 - 持有的未打出的卡牌 */
    private final List<Card> hand;
    /** 银行 - 存放已打出的金钱卡 */
    private final Bank bank;
    /** 物业区 - 存放已打出的地产卡 */
    private final PropertyZone propertyZone;
    /** 当前回合已使用的出牌数（每回合最多3次） */
    private int playsUsed;
    /** 是否为当前活跃玩家（轮到该玩家操作） */
    private boolean isActivePlayer;
    /** 双倍租金效果是否激活（下一次租金收费翻倍） */
    private boolean doubleRentActive;

    /**
     * 创建新玩家
     * @param id 唯一标识符（来自服务器分配的clientId）
     * @param nickname 玩家昵称
     */
    public Player(String id, String nickname) {
        this.id = id;
        this.nickname = nickname;
        this.ready = false;
        this.connected = true;
        this.hand = new CopyOnWriteArrayList<>();  // 线程安全列表
        this.bank = new Bank();
        this.propertyZone = new PropertyZone();
        this.playsUsed = 0;
        this.isActivePlayer = false;
        this.doubleRentActive = false;
    }

    // ==================== 基本信息 Getters/Setters ====================

    public String getId() { return id; }
    public String getNickname() { return nickname; }
    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public boolean isConnected() { return connected; }
    public void setConnected(boolean connected) { this.connected = connected; }

    // ==================== 手牌操作 ====================

    /** 获取手牌只读列表 */
    public List<Card> getHand() { return Collections.unmodifiableList(hand); }
    /** 获取手牌数量 */
    public int getHandCount() { return hand.size(); }

    /** 获取银行 */
    public Bank getBank() { return bank; }
    /** 获取物业区 */
    public PropertyZone getPropertyZone() { return propertyZone; }

    // ==================== 回合状态 ====================

    public int getPlaysUsed() { return playsUsed; }
    public void setPlaysUsed(int playsUsed) { this.playsUsed = playsUsed; }
    public boolean isActivePlayer() { return isActivePlayer; }
    public void setActivePlayer(boolean activePlayer) { isActivePlayer = activePlayer; }
    public boolean isDoubleRentActive() { return doubleRentActive; }
    public void setDoubleRentActive(boolean doubleRentActive) {
        this.doubleRentActive = doubleRentActive;
    }

    // ==================== 手牌管理方法 ====================

    /** 添加一张卡牌到手牌 */
    public void addCardToHand(Card card) {
        hand.add(card);
    }

    /** 根据卡牌对象从手牌中移除，移除失败返回null */
    public Card removeCardFromHand(Card card) {
        return hand.remove(card) ? card : null;
    }

    /** 根据索引从手牌中移除卡牌 */
    public Card removeCardFromHand(int index) {
        return hand.remove(index);
    }

    /** 检查手牌中是否有指定的卡牌（按对象比较） */
    public boolean hasCard(Card card) {
        return hand.contains(card);
    }

    /** 检查手牌中是否有指定ID的卡牌 */
    public boolean hasCardId(String cardId) {
        return hand.stream().anyMatch(c -> c.getId().equals(cardId));
    }

    /** 根据卡牌ID从手牌中查找卡牌 */
    public Card findCardById(String cardId) {
        return hand.stream()
                .filter(c -> c.getId().equals(cardId))
                .findFirst()
                .orElse(null);
    }

    // ==================== 游戏进度方法 ====================

    /** 获取完整地产组合数量（来自物业区统计） */
    public int getCompleteSetsCount() {
        return propertyZone.getCompleteSetsCount();
    }

    /** 每回合最大出牌数（固定为3） */
    public int getMaxPlays() {
        return GameConstants.MAX_PLAYS_PER_TURN;
    }

    /** 当前回合剩余可出牌次数 */
    public int getRemainingPlays() {
        return getMaxPlays() - playsUsed;
    }

    /** 当前回合是否还可以出牌 */
    public boolean canPlay() {
        return getRemainingPlays() > 0;
    }

    /** 已使用出牌数+1 */
    public void incrementPlaysUsed() {
        this.playsUsed++;
    }

    /**
     * 重置回合状态
     * 在新回合开始时调用，将出牌计数清零并清除双倍租金效果
     */
    public void resetTurnState() {
        setPlaysUsed(0);
        setDoubleRentActive(false);
    }

    /**
     * 是否需要弃牌
     * 回合结束时，手牌数量超过上限（7张）则需要弃牌
     * @return true=需要弃牌，false=不需要
     */
    public boolean needsToDiscard() {
        return hand.size() > GameConstants.MAX_HAND_SIZE;
    }

    // ==================== equals / hashCode ====================

    /** 基于玩家ID判断相等性 */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Player player = (Player) o;
        return Objects.equals(id, player.id);
    }

    /** 基于玩家ID计算哈希值 */
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
