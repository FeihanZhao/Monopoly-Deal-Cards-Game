# 后端逻辑修复记录

> 审计 & 修复日期：2026-05-26  
> 基准规则：Hasbro Monopoly Deal 官方规则（106 张游戏卡标准版）

---

## 修复概览

| 优先级 | 数量 | 类别 |
|--------|------|------|
| 🛑 P0 严重 | 6 | 规则违反 / 游戏软锁 |
| 🟡 P1 中等 | 6 | 逻辑缺陷 / 内存泄漏 / 代码清理 |
| 🔵 P2 小 | 3 | 文档 / 日志 / 死代码激活 |

---

## P0 — 严重问题

### 1. Sly Deal（偷袭）可偷取完整组合中的地产卡

- **文件**：`GameSession.java`
- **违规**：官方规则明确禁止从完整组合中偷取地产卡
- **修改**：
  - `executeSlyDeal()` 新增校验：目标卡牌有效颜色在目标的完整组合列表中则拒绝
  - `playActionCard()` Sly Deal 自动选目标：删除 fallback 到任意地产的循环，无合法目标时返回 false
- **行号**：`executeSlyDeal` L992-994、`playActionCard` L489-519

### 2. 房屋/酒店可建在火车站和公共事业上

- **文件**：`PropertyZone.java`
- **违规**：官方规则禁止在 Railroad（Black）和 Utility（Light Green）完整组合上建造
- **修改**：`canPlaceHouse()` 和 `canPlaceHotel()` 各新增颜色黑名单一行
- **行号**：L148、L177

### 3. 空手牌时只抽 2 张而非 5 张

- **文件**：`GameSession.java`
- **违规**：官方规则规定回合开始时手牌为空应抽 5 张
- **修改**：`startNextTurn()` 根据 `getHandCount() == 0` 动态选择抽牌数
- **相关常量**：`GameConstants.EMPTY_HAND_DRAW_COUNT = 5`（此前已定义但从未使用）
- **行号**：L135-139

### 4. 房屋/酒店租金加成数值错误

- **文件**：`PropertyZone.java`
- **违规**：官方规则 House +3M、Hotel +4M（印在卡牌上）
- **修改**：`getRentAmount()` 房屋系数 `*1` → `*3`，酒店固定值 `3` → `4`
- **行号**：L203-204

### 5. 双色租金卡服务端未自动选择最优颜色

- **文件**：`CardColor.java`、`GameSession.java`
- **问题**：双色租金（如 Green/Blue Rent）完全依赖客户端传单色，服务端不验证也不优化
- **修改**：
  - `CardColor` 新增 `getComponentColors()` 方法，5 组双色枚举映射到成分颜色
  - `playRentCard()` 对双色卡计算两个成分色的租金取 `Math.max`，自动选最优
  - `executeRent()` 兜底逻辑同步
- **行号**：`CardColor` L138-147、`GameSession` L323-345、L936-962

### 6. 抽牌堆耗尽导致游戏软锁

- **文件**：`GameSession.java`
- **问题**：`Math.min(baseDraw, deck.getDrawPileSize())` —— 抽牌堆为 0 时 drawCount=0，`drawMultiple(0)` 不调用 `draw()`，弃牌堆回收 `reshuffleDiscardPile()` 永不触发，此后所有玩家抽 0 张牌
- **修改**：删除 `Math.min` 限制，改为 `int drawCount = baseDraw`，`drawMultiple()` 内部已有兜底
- **行号**：L141-142

---

## P1 — 中等问题

### 7. 多目标行动（Rent/Birthday）阶段冲突

- **文件**：`GameSession.java`
- **问题**：`resolveTopResolution()` 执行完 `executeDeferredAction`（→`WAITING_FOR_PAYMENT`）后立即调用 `continueMultiTargetResolution`（→`pushResolution` 覆盖为 `WAITING_FOR_REACTION`），付款和下一个 JSN 阶段并行
- **修改**：
  - 新增 `pendingMultiTargetResolution` 字段暂存多目标决议
  - 新增 `hasRemainingTargets()` 辅助方法
  - `resolveTopResolution()` 不再立即推进，改为暂存
  - `clearPendingPayment()` 付款完成后检查暂存并推进下一个目标
- **行号**：L64、L800-803、L829-835、L1208-1216

### 8. 移除地产后建筑悬空

- **文件**：`PropertyZone.java`
- **问题**：Forced Deal 等移除地产卡后，若组合不再完整，房屋/酒店数据残留
- **修改**：`removeProperty()` 移除成功后检查组合完整性，不完整则清理 `houseCount` 和 `hasHotel`
- **行号**：L78-82

### 9. CardColor 枚举中的 Purple 死代码

- **文件**：`CardColor.java`、`CardRenderer.java`、`GamePanel.java`、`PlayerPanel.java`、`PropertySetPanel.java`
- **问题**：`PURPLE` 和 `PURPLE_ORANGE` 是官方游戏中不存在的颜色，枚举中有定义但无卡牌使用
- **修改**：删除枚举常量及所有视图层的紫色调色板/颜色映射引用（共 6 个文件）
- **影响**：全项目 grep `PURPLE` 零残留

### 10. 空房间内存泄漏

- **文件**：`GameRoom.java`、`GameServer.java`
- **问题**：`GameServer.removeRoom()` 方法存在但从未被调用，空房间永久留在 Map 中
- **修改**：
  - `GameRoom` 新增 `server` 字段和构造函数参数
  - `removePlayer()` 房间变空时调用 `server.removeRoom(roomCode)`
  - `GameServer.createRoom()` 传入 `this`

### 11. 昵称无校验

- **文件**：`ClientHandler.java`
- **问题**：创建/加入房间时昵称直接从 payload 取值，不检查空字符串、长度、特殊字符
- **修改**：新增 `isValidNickname()` 校验方法，限制 1-12 字符、仅允许中英文数字下划线；`handleCreateRoom()` 和 `handleJoinRoom()` 调用之，无效则返回错误
- **行号**：L135-140、L150-155、L308-314

### 12. executeRent() 兜底逻辑未同步

- **文件**：`GameSession.java`
- **问题**：`executeRent()` 中 `_preCalculatedRent` 缺失时的兜底计算仍用旧逻辑（不处理双色租金、不取 max）
- **修改**：兜底路径与 `playRentCard()` 逻辑同步，同样调用 `getComponentColors()` 取 max
- **行号**：L936-962

---

## P2 — 小问题

### 13. Javadoc 与实现不一致

- **文件**：`PropertyZone.java`
- **问题**：类级 Javadoc 写"每栋+1"、"最多4栋"；`getRentAmount()` Javadoc 写"+1M"、"+3M"——与实际代码不符
- **修改**：更新为"每栋+3M"、"+4M"、"最多1栋"，并标注 BLACK/LIGHT_GREEN 禁建

### 14. GamePhase.DISCARD 从未被使用

- **文件**：`GameSession.java`
- **问题**：枚举定义了 `DISCARD` 阶段但 `forceEndTurn()` 从未设置
- **修改**：在 `forceEndTurn()` 弃牌循环前设置 `phase = GamePhase.DISCARD`（需要弃牌时）

### 15. 未知行动类型静默失败

- **文件**：`GameSession.java`
- **问题**：`mapActionNameToType()` 不匹配时返回 "UNKNOWN"，后续 `executeDeferredAction()` 无匹配 case 直接丢弃
- **修改**：`mapActionNameToType()` 返回前输出 `System.err` 警告；`executeDeferredAction()` 新增 `default` 分支输出错误日志

---

## 涉及文件清单

| 文件 | 修改次数 | 修改类型 |
|------|---------|---------|
| `GameSession.java` | 9 | 规则修复 / 软锁修复 / 阶段冲突 / 日志 |
| `PropertyZone.java` | 5 | 建筑限制 / 租金修正 / 建筑清理 / Javadoc |
| `CardColor.java` | 3 | 双色分解 / 死代码清理 |
| `ClientHandler.java` | 3 | 昵称校验 |
| `GameRoom.java` | 4 | 内存泄漏修复 |
| `GameServer.java` | 1 | 内存泄漏修复 |
| `CardRenderer.java` | 2 | Purple 清理 |
| `GamePanel.java` | 2 | Purple 清理 |
| `PlayerPanel.java` | 1 | Purple 清理 |
| `PropertySetPanel.java` | 2 | Purple 清理 |

---

## 未修复的已知项

| 问题 | 原因 |
|------|------|
| 回合结束弃牌不给玩家选择 | 需要全栈（客户端 UI + 服务端协议）联调，纯后端无法独立完成 |
| Deal Breaker 自动选第一个完整组合 | 需客户端提供颜色选择 UI，当前简化设计可接受 |
| 支付只能从银行不能用地产卡 | 架构简化，需扩展协议和客户端支付选卡界面 |
