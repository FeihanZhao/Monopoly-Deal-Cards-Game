package com.monopolydeal.model;

import java.util.*;

/**
 * 物业区类 - 管理玩家的地产卡展示区域
 *
 * 每个玩家拥有一个物业区（PropertyZone），用于放置和管理已打出的地产卡。
 * 地产卡按颜色分组存放，相同颜色的地产卡自动归入同一组。
 *
 * 核心概念：
 * - 完整地产组合（Complete Set）：当某颜色组中的卡牌数量 >= 该颜色所需的setSize时
 * - 房屋（House）：可在完整组合上建造，每栋+3M租金加成，最多1栋（不可建在Black/LightGreen上）
 * - 酒店（Hotel）：在有房屋的完整组合上方可建造，+4M租金加成（不可建在Black/LightGreen上）
 *
 * 胜利条件：拥有3个完整地产组合（集齐3套不同颜色的完整地产）。
 */
public class PropertyZone {
    /** 按颜色分组的物业映射表 key=颜色, value=该颜色的地产卡列表 */
    private final Map<CardColor, List<Card>> propertyGroups;
    /** 所有地产卡的扁平列表 */
    private final List<Card> allProperties;
    /** 每种颜色的房屋数量 key=颜色, value=房屋数量(0-4) */
    private final Map<CardColor, Integer> houseCount;
    /** 每种颜色是否有酒店 key=颜色, value=true=有酒店 */
    private final Map<CardColor, Boolean> hasHotel;

    /**
     * 构造函数 - 初始化空物业区
     * 为每种纯地产颜色预先创建空的分组和房屋/酒店状态映射
     */
    public PropertyZone() {
        this.propertyGroups = new LinkedHashMap<>();  // 保持插入顺序
        this.allProperties = new ArrayList<>();
        this.houseCount = new HashMap<>();
        this.hasHotel = new HashMap<>();
        for (CardColor color : CardColor.values()) {
            if (color.isPropertyColor()) {
                propertyGroups.put(color, new ArrayList<>());
                houseCount.put(color, 0);     // 初始房屋数=0
                hasHotel.put(color, false);   // 初始无酒店
            }
        }
    }

    /**
     * 将一张地产卡添加到物业区
     * 根据卡牌的有效颜色（对于万能地产卡，由玩家选定的wildColor决定）自动分组
     *
     * @param propertyCard 必须是地产卡类型
     * @throws IllegalArgumentException 如果传入的不是地产卡
     */
    public void addProperty(Card propertyCard) {
        if (!propertyCard.isPropertyCard()) {
            throw new IllegalArgumentException("只有地产卡可以添加到物业区");
        }
        CardColor effectiveColor = propertyCard.getEffectiveColor();
        // 如果该颜色分组尚不存在（万能地产卡选了之前没有的颜色），动态创建
        if (!propertyGroups.containsKey(effectiveColor)) {
            propertyGroups.put(effectiveColor, new ArrayList<>());
            houseCount.putIfAbsent(effectiveColor, 0);
            hasHotel.putIfAbsent(effectiveColor, false);
        }
        propertyGroups.get(effectiveColor).add(propertyCard);
        allProperties.add(propertyCard);
    }

    /**
     * 从物业区移除一张地产卡
     * @param propertyCard 要移除的地产卡
     * @return true=移除成功，false=未找到该卡
     */
    public boolean removeProperty(Card propertyCard) {
        CardColor effectiveColor = propertyCard.getEffectiveColor();
        List<Card> group = propertyGroups.get(effectiveColor);
        if (group != null && group.remove(propertyCard)) {
            allProperties.remove(propertyCard);
            // 如果移除后该组合不再完整，自动拆除建筑
            if (getPropertyCount(effectiveColor) < effectiveColor.getSetSize()) {
                houseCount.put(effectiveColor, 0);
                hasHotel.put(effectiveColor, false);
            }
            return true;
        }
        return false;
    }

    /**
     * 获取指定颜色的地产卡列表（只读）
     * @param color 地产颜色
     * @return 该颜色的地产卡列表
     */
    public List<Card> getPropertiesByColor(CardColor color) {
        return Collections.unmodifiableList(propertyGroups.getOrDefault(color, Collections.emptyList()));
    }

    /**
     * 获取指定颜色的地产卡数量
     * @param color 地产颜色
     * @return 该颜色的卡牌数量
     */
    public int getPropertyCount(CardColor color) {
        List<Card> group = propertyGroups.get(color);
        return group != null ? group.size() : 0;
    }

    /** 获取所有颜色分组的地产卡映射表（只读） */
    public Map<CardColor, List<Card>> getAllPropertyGroups() {
        return Collections.unmodifiableMap(propertyGroups);
    }

    /**
     * 获取所有已完成的完整地产组合的颜色列表
     * 当某颜色组的卡牌数 >= 该颜色所需数量（setSize）时，该组合为"完整"
     *
     * @return 完整组合的颜色列表
     */
    public List<CardColor> getCompleteSets() {
        List<CardColor> completeSets = new ArrayList<>();
        for (Map.Entry<CardColor, List<Card>> entry : propertyGroups.entrySet()) {
            CardColor color = entry.getKey();
            int count = entry.getValue().size();
            int required = color.getSetSize();
            if (required > 0 && count >= required) {
                completeSets.add(color);
            }
        }
        return completeSets;
    }

    /** 获取完整地产组合的数量 */
    public int getCompleteSetsCount() {
        return getCompleteSets().size();
    }

    /**
     * 检查是否可以在指定颜色的完整组合上建造房屋
     * 条件：
     * 1. 该颜色必须是完整组合
     * 2. 该颜色尚未建造酒店
     * 3. 房屋数量未达到上限（MAX_HOUSES_PER_SET）
     *
     * @param color 目标颜色
     * @return true=可以建造，false=不可建造
     */
    public boolean canPlaceHouse(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;
        if (color == CardColor.BLACK || color == CardColor.LIGHT_GREEN) return false;
        if (hasHotel.getOrDefault(color, false)) return false;
        return houseCount.getOrDefault(color, 0) < GameConstants.MAX_HOUSES_PER_SET;
    }

    /**
     * 在指定颜色的完整组合上建造一栋房屋
     * @param color 目标颜色
     * @throws IllegalStateException 如果不满足建造条件
     */
    public void addHouse(CardColor color) {
        if (!canPlaceHouse(color)) {
            throw new IllegalStateException("无法在 " + color.getName() + " 上建造房屋");
        }
        houseCount.merge(color, 1, Integer::sum);
    }

    /**
     * 检查是否可以在指定颜色的完整组合上建造酒店
     * 条件：
     * 1. 该颜色必须是完整组合
     * 2. 该颜色尚未建造酒店
     * 3. 房屋数量已达上限（必须先有房子才能建旅馆）
     *
     * @param color 目标颜色
     * @return true=可以建造，false=不可建造
     */
    public boolean canPlaceHotel(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;
        if (color == CardColor.BLACK || color == CardColor.LIGHT_GREEN) return false;
        if (hasHotel.getOrDefault(color, false)) return false;
        return houseCount.getOrDefault(color, 0) >= GameConstants.MAX_HOUSES_PER_SET;
    }

    /**
     * 在指定颜色的完整组合上建造酒店
     * @param color 目标颜色
     * @throws IllegalStateException 如果不满足建造条件
     */
    public void addHotel(CardColor color) {
        if (!canPlaceHotel(color)) {
            throw new IllegalStateException("无法在 " + color.getName() + " 上建造酒店");
        }
        hasHotel.put(color, true);
    }

    /**
     * 计算指定颜色的租金收入
     * 租金 = 基础租金 + 房屋加成（每栋+3M） + 酒店加成（+4M）
     *
     * @param color 地产颜色
     * @return 应收租金金额（单位：M/百万）
     */
    public int getRentAmount(CardColor color) {
        int baseRent = color.getRentAmount(getPropertyCount(color));  // 基础租金（根据持有数计算）
        int houseBonus = houseCount.getOrDefault(color, 0) * 3;       // 每栋房屋+3M
        int hotelBonus = hasHotel.getOrDefault(color, false) ? 4 : 0; // 酒店+4M
        return baseRent + houseBonus + hotelBonus;
    }

    /**
     * 切换万能地产卡的生效颜色（安全硬化版）
     *
     * 三步安全校验：
     * 1. 目标颜色必须是有效的纯地产颜色
     * 2. 该万能卡必须允许切换到目标颜色（双色卡不能冒充十色卡）
     * 3. 原颜色分组如有房屋/酒店，移除该卡后必须仍保持完整（防止违建悬空）
     *
     * 防御性编程：不在迭代中直接 remove，先定位再操作，杜绝 CME 风险。
     *
     * @param cardId 万能地产卡的唯一ID
     * @param newColor 新的地产颜色
     * @return true=切换成功，false=校验不通过
     */
    public boolean changeWildCardColor(String cardId, CardColor newColor) {
        if (!newColor.isPropertyColor()) return false;

        // 阶段1：安全查找目标卡牌（只读遍历，不做修改）
        Card targetCard = null;
        CardColor oldColor = null;

        for (Map.Entry<CardColor, List<Card>> entry : propertyGroups.entrySet()) {
            for (Card card : entry.getValue()) {
                if (card.getId().equals(cardId) && card.isWildProperty()) {
                    targetCard = card;
                    oldColor = entry.getKey();
                    break;
                }
            }
            if (targetCard != null) break;
        }

        if (targetCard == null) return false;

        // 阶段2：校验双色合法性
        if (!targetCard.isColorAllowed(newColor)) return false;

        // 阶段3：校验违建风险 —— 如果旧组有建筑，移除本卡后必须仍满足完整套条件
        if (hasBuildings(oldColor)) {
            int remaining = getPropertyCount(oldColor) - 1;
            if (remaining < oldColor.getSetSize()) {
                return false; // 拒绝：会导致房屋/酒店悬空在非完整套牌上
            }
        }

        // 阶段4：安全执行阵营转移
        propertyGroups.get(oldColor).remove(targetCard);
        allProperties.remove(targetCard);
        targetCard.setWildColor(newColor);
        addProperty(targetCard);
        return true;
    }

    /** 检查指定颜色分组是否建有房屋或酒店 */
    private boolean hasBuildings(CardColor color) {
        return houseCount.getOrDefault(color, 0) > 0
                || hasHotel.getOrDefault(color, false);
    }

    /** 清空物业区（移除所有地产卡、房屋和酒店） */
    public void clear() {
        propertyGroups.values().forEach(List::clear);
        allProperties.clear();
        houseCount.replaceAll((k, v) -> 0);
        hasHotel.replaceAll((k, v) -> false);
    }
}
