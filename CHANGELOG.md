# 修改日志

> 项目：Monopoly Deal 卡牌游戏
> 分支：master
> 基准：Hasbro Monopoly Deal 官方规则（106 张游戏卡标准版）

---

## 第一部分：后端游戏规则修复（提交 8f7ecea）

> 审计日期：2026-05-26

### 修复概览

| 优先级 | 数量 | 类别 |
|--------|------|------|
| P0 严重 | 6 | 规则违反 / 游戏软锁 |
| P1 中等 | 6 | 逻辑缺陷 / 内存泄漏 / 代码清理 |
| P2 小 | 3 | 文档 / 日志 / 死代码激活 |

### P0 — 严重问题

1. **Sly Deal（偷袭）可偷取完整组合中的地产卡** — `GameSession.java`
   - 官方规则明确禁止从完整组合中偷取地产卡
   - `executeSlyDeal()` 新增完整组合校验；`playActionCard()` Sly Deal 自动选目标时无合法目标则返回 false

2. **房屋/酒店可建在火车站和公共事业上** — `PropertyZone.java`
   - 官方规则禁止在 Railroad（Black）和 Utility（Light Green）完整组合上建造
   - `canPlaceHouse()` 和 `canPlaceHotel()` 各新增颜色黑名单

3. **空手牌时只抽 2 张而非 5 张** — `GameSession.java`
   - 官方规则：回合开始时手牌为空应抽 5 张
   - `startNextTurn()` 根据 `getHandCount() == 0` 动态选择抽牌数，激活了此前未使用的 `GameConstants.EMPTY_HAND_DRAW_COUNT = 5`

4. **房屋/酒店租金加成数值错误** — `PropertyZone.java`
   - 官方规则 House +3M、Hotel +4M（印在卡牌上）
   - `getRentAmount()` 房屋系数修正为 *3，酒店固定值修正为 4

5. **双色租金卡服务端未自动选择最优颜色** — `CardColor.java`、`GameSession.java`
   - 双色租金（如 Green/Blue Rent）完全依赖客户端传单色，服务端不验证也不优化
   - `CardColor` 新增 `getComponentColors()` 方法；`playRentCard()` 对双色卡计算两个成分色的租金取 max

6. **抽牌堆耗尽导致游戏软锁** — `GameSession.java`
   - 使用 `Math.min(baseDraw, deck.getDrawPileSize())` 导致抽牌堆为 0 时 drawCount=0，弃牌堆回收永不触发
   - 删除 `Math.min` 限制，`drawMultiple()` 内部已有兜底回收逻辑

### P1 — 中等问题

7. **多目标行动（Rent/Birthday）阶段冲突** — `GameSession.java`
   - `resolveTopResolution()` 在付款进行中即推进下一个目标，导致付款和 JSN 阶段并行
   - 新增 `pendingMultiTargetResolution` 字段；付款完成后再推进下一个目标

8. **移除地产后建筑悬空** — `PropertyZone.java`
   - Forced Deal 等移除地产卡后，若组合不再完整，房屋/酒店数据残留
   - `removeProperty()` 移除成功后清理 `houseCount` 和 `hasHotel`

9. **CardColor 枚举中的 Purple 死代码** — 6 个文件
   - `PURPLE` 和 `PURPLE_ORANGE` 是官方游戏中不存在的颜色
   - 删除枚举常量及视图层所有紫色调色板引用

10. **空房间内存泄漏** — `GameRoom.java`、`GameServer.java`
    - `GameServer.removeRoom()` 存在但从未被调用，空房间永久留在 Map 中
    - `GameRoom` 新增 server 引用，`removePlayer()` 房间变空时调用 `server.removeRoom()`

11. **昵称无校验** — `ClientHandler.java`
    - 创建/加入房间时昵称不检查空字符串、长度、特殊字符
    - 新增 `isValidNickname()` 校验（1-12 字符，仅中英文数字下划线）

12. **executeRent() 兜底逻辑未同步** — `GameSession.java`
    - `_preCalculatedRent` 缺失时的兜底计算仍用旧逻辑（不处理双色租金）
    - 兜底路径与 `playRentCard()` 逻辑同步

### P2 — 小问题

13. **Javadoc 与实现不一致** — `PropertyZone.java`
    - 类级和 `getRentAmount()` 的 Javadoc 租金数值与实际代码不符
    - 更新为正确数值并标注 BLACK/LIGHT_GREEN 禁建

14. **GamePhase.DISCARD 从未被使用** — `GameSession.java`
    - 枚举定义了 `DISCARD` 阶段但 `forceEndTurn()` 从未设置
    - 弃牌循环前设置 `phase = GamePhase.DISCARD`

15. **未知行动类型静默失败** — `GameSession.java`
    - `mapActionNameToType()` 不匹配时返回 "UNKNOWN" 直接丢弃
    - 新增 `System.err` 警告日志和 `default` 分支错误日志

### 后端修复涉及文件

| 文件 | 修改次数 |
|------|---------|
| `GameSession.java` | 9 |
| `PropertyZone.java` | 5 |
| `CardColor.java` | 3 |
| `ClientHandler.java` | 3 |
| `GameRoom.java` | 4 |
| `GameServer.java` | 1 |
| `CardRenderer.java` | 2 |
| `GamePanel.java` | 2 |
| `PlayerPanel.java` | 1 |
| `PropertySetPanel.java` | 2 |

---

## 第二部分：前端 UI 重构（批次 1-5）

> 重构日期：2026-05-27
> 角色：高级架构师设计 + AI 执行 + 审查验证

### 批次 1 — 关键 Bug 修复 + CardSelectionBar 集成

**文件：** `LobbyPanel.java`、`MainFrame.java`、`GamePanel.java`、`CardRenderer.java`

| 修改 | 说明 |
|------|------|
| LobbyPanel 渐变按钮闭包 Bug | 颜色存储到 client properties，运行时通过 `updateGradientButton()` 修改，解决匿名类闭包捕获后无法变更的问题 |
| MainFrame 消息路由补全 | 新增 `REACTION_REQUIRED` → `gamePanel.handleReactionRequired()`、`PAYMENT_REQUIRED` → `gamePanel.handlePaymentRequired()` 路由 |
| GamePanel Just Say No 响应对话框 | 新增 `handleReactionRequired()`：模态 JOptionPane + `java.util.Timer` 倒计时，超时自动发送 PASS_REACTION |
| GamePanel 支付选择对话框 | 新增 `handlePaymentRequired()`：JList 多选 + 实时总额统计，确认后发送 SUBMIT_PAYMENT |
| CardRenderer JSON 数据存取 | 新增 `cardData` 字段和 getter，存储完整 JSON 数据供回调使用 |
| Bug 修复：handleReactionRequired 瞬间超时 | 初版使用非模态对话框 + Swing Timer，`setVisible(true)` 立即返回导致 PASS_REACTION 瞬间发送。修复为模态对话框 + `java.util.Timer` dispose 模式 |

### 批次 2 — 颜色集中管理（AppTheme）

**文件：** `AppTheme.java`（新建）、`CardRenderer.java`、`PropertySetPanel.java`、`PlayerPanel.java`、`GamePanel.java`

| 修改 | 说明 |
|------|------|
| AppTheme 新建 | 集中定义 `PROPERTY_COLORS`（10 种地产颜色）、`PROPERTY_GRADIENT_COLORS`（渐变终点色）、`WILD_COLOR_OPTIONS`（万能卡颜色映射）、品牌色/背景色/文字色/语义色 |
| CardRenderer 颜色去重 | 10 行硬编码 Palette 替换为 `AppTheme.PROPERTY_COLORS` 循环；渐变色使用 `PROPERTY_GRADIENT_COLORS` |
| PropertySetPanel 去重 | `SET_SIZES`（10 条删除）→ `getSetSize()` 委托 `CardColor.valueOf()`；`BG_COLORS`（10 条删除）→ `AppTheme.PROPERTY_COLORS` |
| PlayerPanel 去重 | `COLOR_MAP`（10 条删除）→ `AppTheme.PROPERTY_COLORS.getOrDefault()` |
| GamePanel 去重 | `WILD_COLOR_OPTIONS` 静态块删除 → `AppTheme.WILD_COLOR_OPTIONS` |
| 低风险修复：渐变色偏差 | CardRenderer 用 `bg.darker().darker()` 替代手工选色导致 BROWN/RED/LIGHT_GREEN 偏差，修复为 `AppTheme.PROPERTY_GRADIENT_COLORS` 精确匹配 |

### 批次 3 — TimerBarPanel 倒计时进度条集成

**文件：** `GamePanel.java`

| 修改 | 说明 |
|------|------|
| 数字标签 → 可视化进度条 | `JLabel timerLabel` 替换为 `TimerBarPanel timerBarPanel`（水平进度条 + 渐变填充 + 10s 警告红色过渡） |
| 倒计时逻辑简化 | `secondsRemaining` 字段删除，`startCountdown()` / `stopCountdown()` 委托给 TimerBarPanel 的 `start()` / `tick()` / `setInactive()` |

### 批次 4 — CardViewModel 解耦

**文件：** `CardViewModel.java`（新建）、`CardRenderer.java`、`GamePanel.java`

| 修改 | 说明 |
|------|------|
| CardViewModel 新建 | 纯 POJO 数据载体（cardId, cardName, cardType, color, value），附带 `isWild()`、`isMoney()` 等便捷方法 |
| CardRenderer 解耦 | `cardData`（JsonObject）→ `viewModel`（CardViewModel），删除 `import com.google.gson.JsonObject` |
| GamePanel 适配 | `updateLocalHand()` 从 JSON 解析构建 CardViewModel；所有回调通过 getter 访问数据，不再依赖 JsonObject |

### 批次 5 — UX 优化

**文件：** `WrapLayout.java`（新建）、`GamePanel.java`、`ActionHistoryPanel.java`

| 修改 | 说明 |
|------|------|
| WrapLayout 新建 | 扩展 FlowLayout，子组件在容器宽度不足时自动换行；实现 `preferredLayoutSize()`、`minimumLayoutSize()`、`layoutContainer()` |
| 手牌区自动换行 | `createHandPanel()` 中 FlowLayout → WrapLayout，水平滚动条 NEVER → 垂直 AS_NEEDED |
| ActionHistoryPanel 重写 | JTextArea + StringBuilder → JList\<ActionEntry\> + DefaultListModel + 自定义 ListCellRenderer；彩色 HTML 渲染（灰色时间戳 + 彩色昵称 + 白色动作文本）；智能自动滚动（用户手动上滚时保持位置） |

### 前端重构涉及文件

| 文件 | 状态 |
|------|------|
| `AppTheme.java` | 新建 |
| `CardViewModel.java` | 新建 |
| `WrapLayout.java` | 新建 |
| `CardRenderer.java` | 修改（渐进：+cardData → +AppTheme → +CardViewModel） |
| `GamePanel.java` | 修改（渐进：+Reaction UI +Payment UI +CardSelectionBar +TimerBar +CardViewModel +WrapLayout） |
| `LobbyPanel.java` | 修改（渐变按钮闭包修复） |
| `MainFrame.java` | 修改（消息路由补全） |
| `PlayerPanel.java` | 修改（颜色映射去重） |
| `PropertySetPanel.java` | 修改（SET_SIZES + BG_COLORS 去重） |
| `ActionHistoryPanel.java` | 重写（JTextArea → JList） |

---

## 第三部分：弃牌选择功能（批次 6-7）

> 实现日期：2026-05-27
> 功能：回合结束弃牌阶段，玩家可自由选择要弃掉的卡牌，15 秒倒计时，超时自动弃牌

### 改造前

`forceEndTurn()` 直接调用 `activePlayer.removeCardFromHand(0)` 从手牌开头强制弃牌，玩家无法选择保留哪张卡牌，无倒计时提示。

### 改造后

完整的客户端-服务端交互流程：

```
回合结束 / 超时
  → forceEndTurn()
    → needsToDiscard() == true
      → startDiscardPhase()
        → 发送 DISCARD_REQUIRED 给客户端（含手牌列表 + 需弃数量 + 超时秒数）
        → 启动 15s 超时定时器
        → 等待 SUBMIT_DISCARD 回复
          ├─ 客户端选择卡牌确认 → handleSubmitDiscard() → finalizeEndTurn()
          └─ 15s 超时 → 从 hand[0] 自动弃牌 → finalizeEndTurn()
    → needsToDiscard() == false
      → finalizeEndTurn()
```

### 批次 6 — 服务端协议 + 等待逻辑

**文件：** `MessageProtocol.java`、`GameSession.java`、`ClientHandler.java`

| 修改 | 说明 |
|------|------|
| MessageProtocol 新增枚举 | `DISCARD_REQUIRED`（服务端 → 客户端）、`SUBMIT_DISCARD`（客户端 → 服务端） |
| GameSession 新增字段 | `ScheduledFuture<?> discardTimeoutTask` — 弃牌超时定时器句柄 |
| forceEndTurn() 重构 | 删除自动弃牌块（18 行），改为判断 `needsToDiscard()` → `startDiscardPhase()` 或 `finalizeEndTurn()` |
| startDiscardPhase() 新增 | 构建手牌 JSON、发送 DISCARD_REQUIRED、设置 phase = DISCARD、启动 15s 超时定时器 |
| handleSubmitDiscard() 新增 | 校验权限和阶段 → 按 cardId 列表移除卡牌 → 兜底补齐 → `finalizeEndTurn()` |
| finalizeEndTurn() 新增 | 取消弃牌定时器 → 清除活跃玩家 → 记录 END_TURN → 广播 → 1.5s 后下一回合 |
| ClientHandler 路由 | 新增 `SUBMIT_DISCARD` case 分支 + `handleSubmitDiscard()` 处理方法 |

**JSON 协议：**

DISCARD_REQUIRED（服务端 → 客户端）：
```json
{
  "handCards": [{"cardId":"...", "cardName":"租金卡", "cardType":"RENT", "color":"RED", "value":0}],
  "discardCount": 2,
  "timeoutSeconds": 15
}
```

SUBMIT_DISCARD（客户端 → 服务端）：
```json
{"cardIds": ["card-id-1", "card-id-2"]}
```

### 批次 7 — 客户端弃牌选择 UI

**文件：** `MainFrame.java`、`GamePanel.java`

| 修改 | 说明 |
|------|------|
| MainFrame 消息路由 | 新增 `DISCARD_REQUIRED` → `gamePanel.handleDiscardRequired(payload)` |
| handleDiscardRequired() 新增 | JList 多选手牌 + 倒计时标签（每秒更新）+ 已选/需弃实时统计；`java.util.Timer` 超时 dispose 模态对话框；确认发送 SUBMIT_DISCARD，超时/取消发送空数组由服务端兜底 |

### 安全设计

| 场景 | 处理 |
|------|------|
| 客户端少选卡牌 | 服务端 `while (needsToDiscard())` 从 hand[0] 补齐 |
| 客户端提交不存在的 cardId | `Set<String>` 匹配，无效 ID 被忽略 |
| 客户端断线/不响应 | 15s 后超时自动弃牌 |
| 双重触发 | `handleSubmitDiscard()` 和 `finalizeEndTurn()` 均取消 `discardTimeoutTask` |
| 线程安全 | `forceEndTurn()` / `handleSubmitDiscard()` 均 `synchronized`；超时回调使用 `synchronized (GameSession.this)` |

### 弃牌功能涉及文件

| 文件 | 改动类型 |
|------|----------|
| `MessageProtocol.java` | +2 枚举值 |
| `GameSession.java` | 重构 forceEndTurn() + 新增 3 方法 + 1 字段（+118 行 / -18 行） |
| `ClientHandler.java` | +1 case + 1 方法 |
| `MainFrame.java` | +1 case |
| `GamePanel.java` | +1 方法 `handleDiscardRequired()`（~95 行） |

---

## 全部修改文件统计

| 文件 | 涉及批次 | 说明 |
|------|----------|------|
| `AppTheme.java` | 批次 2 | **新建** — 颜色常量集中管理 |
| `CardViewModel.java` | 批次 4 | **新建** — CardRenderer 数据解耦 |
| `WrapLayout.java` | 批次 5 | **新建** — 手牌自动换行布局 |
| `CardRenderer.java` | 批次 1, 2, 4 | CardData → AppTheme → CardViewModel |
| `GamePanel.java` | 批次 1, 2, 3, 4, 5, 7 | 消息处理 + UI 组件 + 弃牌对话框 |
| `LobbyPanel.java` | 批次 1 | 渐变按钮闭包修复 |
| `MainFrame.java` | 批次 1, 7 | 消息路由补全（REACTION + PAYMENT + DISCARD） |
| `PlayerPanel.java` | 批次 2 | 颜色映射去重 |
| `PropertySetPanel.java` | 批次 2 | SET_SIZES + BG_COLORS 去重 |
| `ActionHistoryPanel.java` | 批次 5 | JTextArea → JList 重写 |
| `MessageProtocol.java` | 批次 6 | +DISCARD_REQUIRED + SUBMIT_DISCARD |
| `GameSession.java` | 批次 6 | 弃牌等待逻辑（forceEndTurn 重构 + 3 新方法） |
| `ClientHandler.java` | 批次 6 | SUBMIT_DISCARD 路由 |

**总计：13 个文件（3 新建 + 10 修改），约 900 行新增，580 行删除。**
