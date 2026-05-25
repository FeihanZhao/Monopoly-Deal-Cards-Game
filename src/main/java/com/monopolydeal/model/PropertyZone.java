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
 * - 房屋（House）：可在完整组合上建造，每栋+1租金加成，最多4栋
 * - 酒店（Hotel）：在4栋房屋的基础上方可建造，+3租金加成
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
     * 3. 房屋数量未达到4栋上限
     *
     * @param color 目标颜色
     * @return true=可以建造，false=不可建造
     */
    public boolean canPlaceHouse(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;  // 必须是完整组合
        if (hasHotel.getOrDefault(color, false)) return false; // 已有酒店不能再建房屋
        return houseCount.getOrDefault(color, 0) < 4;           // 最多4栋房屋
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
        houseCount.merge(color, 1, Integer::sum);  // 房屋数+1
    }

    /**
     * 检查是否可以在指定颜色的完整组合上建造酒店
     * 条件：
     * 1. 该颜色必须是完整组合
     * 2. 该颜色尚未建造酒店
     * 3. 房屋数量已达4栋（酒店需要先有4栋房屋）
     *
     * @param color 目标颜色
     * @return true=可以建造，false=不可建造
     */
    public boolean canPlaceHotel(CardColor color) {
        if (!getCompleteSets().contains(color)) return false;  // 必须是完整组合
        if (hasHotel.getOrDefault(color, false)) return false; // 不能重复建酒店
        return houseCount.getOrDefault(color, 0) >= 4;          // 需要4栋房屋作为前置
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
     * 租金 = 基础租金 + 房屋加成（每栋+1M） + 酒店加成（+3M）
     *
     * @param color 地产颜色
     * @return 应收租金金额（单位：M/百万）
     */
    public int getRentAmount(CardColor color) {
        int baseRent = color.getRentAmount(getPropertyCount(color));  // 基础租金（根据持有数计算）
        int houseBonus = houseCount.getOrDefault(color, 0) * 1;       // 每栋房屋+1M
        int hotelBonus = hasHotel.getOrDefault(color, false) ? 3 : 0; // 酒店+3M
        return baseRent + houseBonus + hotelBonus;
    }

    /** 清空物业区（移除所有地产卡、房屋和酒店） */
    public void clear() {
        propertyGroups.values().forEach(List::clear);
        allProperties.clear();
        houseCount.replaceAll((k, v) -> 0);
        hasHotel.replaceAll((k, v) -> false);
    }
}
