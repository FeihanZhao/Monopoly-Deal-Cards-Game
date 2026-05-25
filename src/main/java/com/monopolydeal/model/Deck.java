package com.monopolydeal.model;

import java.util.*;

/**
 * 牌堆类 - 管理游戏中的抽牌堆和弃牌堆
 *
 * 负责卡牌的初始化、洗牌、抽牌、弃牌以及弃牌堆重洗复用。
 *
 * 牌堆初始组成（模拟实体《大富翁纸牌游戏》的标准牌组）：
 * - 金钱卡：1M×6, 2M×5, 3M×3, 4M×3, 5M×2, 10M×1 = 20张
 * - 地产卡：11种纯色×各2-4张 + 万能地产6张 = 约40张
 * - 租金卡：7种双色租金×2 + 万能租金×3 = 15张
 * - 行动卡：10种×各2-3张 = 约25张
 *
 * 当抽牌堆为空时，自动将弃牌堆洗牌后移入抽牌堆继续使用。
 */
public class Deck {
    /** 抽牌堆（栈结构，从顶部抽牌） */
    private final Stack<Card> drawPile;
    /** 弃牌堆（列表结构，弃牌后重新洗入抽牌堆） */
    private final List<Card> discardPile;

    /**
     * 构造函数 - 初始化牌组并洗牌
     * 按照官方规则预设所有卡牌的数量和类型
     */
    public Deck() {
        this.drawPile = new Stack<>();
        this.discardPile = new ArrayList<>();
        initializeDeck();  // 按规则创建所有卡牌
        shuffle();         // 初始洗牌
    }

    /**
     * 初始化完整的牌组
     * 按照《大富翁纸牌游戏》官方卡牌配置创建所有卡牌
     */
    private void initializeDeck() {
        // ===== 金钱卡 (20张) =====
        addCards(CardType.MONEY, CardColor.NONE, 1, 6);  // 1M  ×6张
        addCards(CardType.MONEY, CardColor.NONE, 2, 5);  // 2M  ×5张
        addCards(CardType.MONEY, CardColor.NONE, 3, 3);  // 3M  ×3张
        addCards(CardType.MONEY, CardColor.NONE, 4, 3);  // 4M  ×3张
        addCards(CardType.MONEY, CardColor.NONE, 5, 2);  // 5M  ×2张
        addCards(CardType.MONEY, CardColor.NONE, 10, 1); // 10M ×1张

        // ===== 纯色地产卡 =====
        // 每种颜色需要2-4张组成一套完整地产（与CardColor.setSize一致）
        addCards(CardType.PROPERTY, CardColor.BROWN, 0, 2);
        addCards(CardType.PROPERTY, CardColor.LIGHT_BLUE, 0, 3);
        addCards(CardType.PROPERTY, CardColor.PINK, 0, 3);
        addCards(CardType.PROPERTY, CardColor.ORANGE, 0, 3);
        addCards(CardType.PROPERTY, CardColor.RED, 0, 3);
        addCards(CardType.PROPERTY, CardColor.YELLOW, 0, 3);
        addCards(CardType.PROPERTY, CardColor.GREEN, 0, 3);
        addCards(CardType.PROPERTY, CardColor.BLUE, 0, 2);
        addCards(CardType.PROPERTY, CardColor.PURPLE, 0, 3);
        addCards(CardType.PROPERTY, CardColor.BLACK, 0, 4);
        addCards(CardType.PROPERTY, CardColor.LIGHT_GREEN, 0, 3);

        // ===== 万能地产卡 =====
        // 万能地产可以当作任意一种颜色的地产使用，由玩家在放置时选择
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 2, "Multi-Color Wild");       // 多彩万能 ×2
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Dark Blue/Green Wild");   // 蓝/绿万能
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Red/Yellow Wild");        // 红/黄万能
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Brown/Light Blue Wild");  // 棕/浅蓝万能
        addCards(CardType.PROPERTY, CardColor.WILD, 0, 1, "Orange/Pink Wild");       // 橙/粉万能

        // ===== 租金卡 (15张) =====
        // 双色租金卡可以针对两种颜色中任意一种收租（玩家需选择）
        addCards(CardType.RENT, CardColor.BROWN_LIGHT_BLUE, 0, 2, "Brown/Light Blue Rent");
        addCards(CardType.RENT, CardColor.PINK_ORANGE, 0, 2, "Pink/Orange Rent");
        addCards(CardType.RENT, CardColor.RED_YELLOW, 0, 2, "Red/Yellow Rent");
        addCards(CardType.RENT, CardColor.GREEN_BLUE, 0, 2, "Green/Blue Rent");
        addCards(CardType.RENT, CardColor.PURPLE_ORANGE, 0, 2, "Purple/Orange Rent");
        addCards(CardType.RENT, CardColor.BLACK_LIGHT_GREEN, 0, 2, "Black/Light Green Rent");
        addCards(CardType.RENT, CardColor.WILD, 0, 3, "Wild Rent");  // 万能租金 ×3

        // ===== 行动卡 (约25张) =====
        addCards(CardType.ACTION, CardColor.NONE, 0, 2, "Deal Breaker");     // 强行交易 ×2
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "Just Say No");      // 拒绝 ×3
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "Pass Go");          // 通过起点 ×3
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "Forced Deal");      // 强制交换 ×3
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "Sly Deal");         // 偷袭 ×3
        addCards(CardType.ACTION, CardColor.NONE, 0, 2, "Debt Collector");   // 债务收集者 ×2
        addCards(CardType.ACTION, CardColor.NONE, 0, 2, "Birthday");         // 生日 ×2
        addCards(CardType.ACTION, CardColor.NONE, 0, 2, "Double Rent");      // 双倍租金 ×2
        addCards(CardType.ACTION, CardColor.NONE, 0, 3, "House");            // 房屋 ×3
        addCards(CardType.ACTION, CardColor.NONE, 0, 2, "Hotel");            // 酒店 ×2
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
        Collections.shuffle(drawPile, new Random());
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
            Collections.shuffle(discardPile);
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
