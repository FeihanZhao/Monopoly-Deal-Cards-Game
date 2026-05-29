package com.monopolydeal.model;

import java.util.*;

/**
 * 牌堆类 - 管理游戏中的抽牌堆和弃牌堆
 *
 * 负责卡牌的初始化、洗牌、抽牌、弃牌以及弃牌堆重洗复用。
 *
 * 牌堆初始组成为官方 106 张正版配置：
 * - 货币卡 20 张：10M×1, 5M×2, 4M×3, 3M×3, 2M×5, 1M×6
 * - 标准房产卡 28 张：棕×2, 浅蓝×3, 粉×3, 橙×3, 红×3, 黄×3, 绿×3, 深蓝×2, 车站×4, 公用事业×2
 * - 万能房产卡 11 张：多种双色/十色组合
 * - 租金卡 13 张：双色×10(1M each) + 全能×3(3M each)
 * - 行动卡 34 张：全部带面值，可存入银行
 *
 * 当抽牌堆为空时，自动将弃牌堆洗牌后移入抽牌堆继续使用。
 */
public class Deck {
    /** 抽牌堆（栈结构，从顶部抽牌） */
    private final Stack<Card> drawPile;
    /** 弃牌堆（列表结构，弃牌后重新洗入抽牌堆） */
    private final List<Card> discardPile;
    /** 共享随机数生成器 */
    private final Random random;

    /**
     * 构造函数 - 初始化牌组并洗牌
     * 按照官方规则预设所有卡牌的数量和类型
     */
    public Deck() {
        this.drawPile = new Stack<>();
        this.discardPile = new ArrayList<>();
        this.random = new Random();
        initializeDeck();  // 按规则创建所有卡牌
        shuffle();         // 初始洗牌
    }

    /**
     * 初始化完整的 106 张正版牌组
     *
     * 卡牌组成（严格按官方配置）：
     * - 货币卡 20 张：10M×1, 5M×2, 4M×3, 3M×3, 2M×5, 1M×6
     * - 标准房产卡 28 张：棕×2, 浅蓝×3, 粉×3, 橙×3, 红×3, 黄×3, 绿×3, 深蓝×2, 车站×4, 公用事业×2
     * - 万能房产卡 11 张：深蓝/绿×1, 浅蓝/棕×1, 粉/橙×2, 红/黄×2, 车站/公用事业×1, 绿/车站×1, 浅蓝/车站×1, 十色全能×2
     * - 租金卡 13 张：双色租金×10 (1M each), 全能租金×3 (3M each)
     * - 行动卡 34 张：Deal Breaker 5M×2, Just Say No 4M×3, Sly Deal 3M×3, Forced Deal 3M×3, Debt Collector 3M×3, Birthday 2M×3, Pass Go 1M×10, Double Rent 1M×2, House 3M×3, Hotel 4M×2
     */
    private void initializeDeck() {
        // ===== 货币卡 (20张) =====
        addCards(CardType.MONEY, CardColor.NONE, 10, 1);
        addCards(CardType.MONEY, CardColor.NONE, 5, 2);
        addCards(CardType.MONEY, CardColor.NONE, 4, 3);
        addCards(CardType.MONEY, CardColor.NONE, 3, 3);
        addCards(CardType.MONEY, CardColor.NONE, 2, 5);
        addCards(CardType.MONEY, CardColor.NONE, 1, 6);

        // ===== 标准房产卡 (28张) =====
        addCards(CardType.PROPERTY, CardColor.BROWN, 0, 2);
        addCards(CardType.PROPERTY, CardColor.LIGHT_BLUE, 0, 3);
        addCards(CardType.PROPERTY, CardColor.PINK, 0, 3);
        addCards(CardType.PROPERTY, CardColor.ORANGE, 0, 3);
        addCards(CardType.PROPERTY, CardColor.RED, 0, 3);
        addCards(CardType.PROPERTY, CardColor.YELLOW, 0, 3);
        addCards(CardType.PROPERTY, CardColor.GREEN, 0, 3);
        addCards(CardType.PROPERTY, CardColor.BLUE, 0, 2);
        addCards(CardType.PROPERTY, CardColor.BLACK, 0, 4, "Railroad Property");
        addCards(CardType.PROPERTY, CardColor.LIGHT_GREEN, 0, 2, "Utility Property");

        // ===== 万能房产卡 (11张) =====
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Dark Blue/Green Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Brown/Light Blue Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Orange/Pink Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Red/Yellow Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Railroad/Utility Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Green/Railroad Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Light Blue/Railroad Wild");
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Multi-Color Wild");

        // ===== 租金卡 (13张，全部带面值) =====
        addCards(CardType.RENT, CardColor.BROWN_LIGHT_BLUE, 1, 2, "Brown/Light Blue Rent");
        addCards(CardType.RENT, CardColor.PINK_ORANGE, 1, 2, "Pink/Orange Rent");
        addCards(CardType.RENT, CardColor.RED_YELLOW, 1, 2, "Red/Yellow Rent");
        addCards(CardType.RENT, CardColor.GREEN_BLUE, 1, 2, "Green/Blue Rent");
        addCards(CardType.RENT, CardColor.BLACK_LIGHT_GREEN, 1, 2, "Railroad/Utility Rent");
        addCards(CardType.RENT, CardColor.WILD, 3, 3, "Wild Rent");

        // ===== 行动卡 (34张，全部带面值) =====
        addCards(CardType.ACTION, CardColor.NONE, 5, 2, "Deal Breaker");
        addCards(CardType.ACTION, CardColor.NONE, 4, 3, "Just Say No");
        addCards(CardType.ACTION, CardColor.NONE, 3, 3, "Sly Deal");
        addCards(CardType.ACTION, CardColor.NONE, 3, 3, "Forced Deal");
        addCards(CardType.ACTION, CardColor.NONE, 3, 3, "Debt Collector");
        addCards(CardType.ACTION, CardColor.NONE, 2, 3, "Birthday");
        addCards(CardType.ACTION, CardColor.NONE, 1, 10, "Pass Go");
        addCards(CardType.ACTION, CardColor.NONE, 1, 2, "Double Rent");
        addCards(CardType.ACTION, CardColor.NONE, 3, 3, "House");
        addCards(CardType.ACTION, CardColor.NONE, 4, 2, "Hotel");

        // 验证牌组总数 = 106
        int total = drawPile.size();
        if (total != 106) {
            throw new IllegalStateException(
                "Deck init error: expected 106, generated " + total);
        }
    }

    /**
     * 批量添加相同属性的卡牌到抽牌堆
     * @param type 卡牌类型
     * @param color 卡牌颜色
     * @param value 金钱面值
     * @param count 添加数量
     */
    private void addCards(CardType type, CardColor color, int value, int count) {
        addCards(type, color, value, count, null);
    }

    /**
     * 批量添加相同属性的卡牌（支持自定义名称）
     * 每张卡牌使用UUID前8位作为唯一ID
     *
     * @param type 卡牌类型
     * @param color 卡牌颜色
     * @param value 金钱面值
     * @param count 添加数量
     * @param customName 自定义名称（null则自动生成）
     */
    private void addCards(CardType type, CardColor color, int value, int count, String customName) {
        for (int i = 0; i < count; i++) {
            String id = UUID.randomUUID().toString().substring(0, 8);
            String name = customName != null ? customName :
                    (type == CardType.MONEY ? value + "M" : color.getName() + " " + type.getDisplayName());
            drawPile.push(new Card(id, type, name, value, color, name));
        }
    }

    /** 洗牌 - 使用随机打乱算法重新排列抽牌堆 */
    public void shuffle() {
        Collections.shuffle(drawPile, random);
    }

    /**
     * 从抽牌堆顶部抽取一张卡牌
     * 如果抽牌堆为空，自动将弃牌堆洗牌后移入抽牌堆再抽
     * @return 抽到的卡牌（牌堆完全为空时返回null）
     */
    public Card draw() {
        if (drawPile.isEmpty()) {
            reshuffleDiscardPile();  // 弃牌堆回收
        }
        return drawPile.isEmpty() ? null : drawPile.pop();
    }

    /**
     * 从抽牌堆顶部批量抽取卡牌
     * @param count 抽取数量
     * @return 抽到的卡牌列表（实际数量可能少于count，取决于牌堆剩余数）
     */
    public List<Card> drawMultiple(int count) {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Card card = draw();
            if (card != null) {
                cards.add(card);
            }
        }
        return cards;
    }

    /** 将一张卡牌放入弃牌堆 */
    public void discard(Card card) {
        discardPile.add(card);
    }

    /** 批量将卡牌放入弃牌堆 */
    public void discardMultiple(List<Card> cards) {
        discardPile.addAll(cards);
    }

    /**
     * 弃牌堆回收 - 当抽牌堆为空时触发
     * 将弃牌堆洗牌后全部移入抽牌堆，清空弃牌堆
     * 这样保证了游戏过程中不会出现无牌可抽的情况
     */
    private void reshuffleDiscardPile() {
        if (!discardPile.isEmpty()) {
            Collections.shuffle(discardPile, random);
            drawPile.addAll(discardPile);
            discardPile.clear();
        }
    }

    // ==================== 查询方法 ====================

    /** 获取抽牌堆当前数量 */
    public int getDrawPileSize() {
        return drawPile.size();
    }

    /** 获取弃牌堆当前数量 */
    public int getDiscardPileSize() {
        return discardPile.size();
    }

    /** 检查牌堆是否完全为空（抽牌堆和弃牌堆都空） */
    public boolean isEmpty() {
        return drawPile.isEmpty() && discardPile.isEmpty();
    }
}
