# EasyTaskMgr 开发日志

本文件记录每一次功能增删、修 bug、改文件时做了什么、为什么这么做、以及具体实现方法。
新条目写在最上面（按日期倒序），方便回溯。

环境基线：Minecraft **26.3** / Fabric Loader **0.20.1** / Fabric API **0.165.0+26.3** / Loom **1.19.2** / Java **25**。

---

## 2026-10-31 · 第八轮：移除合成任务 + 列表滚动（仍为 beta.2）

### 1. 10:30 移除：合成任务功能

- **原因**：实际用起来不符合预期、触发条件鸡肋（要刚好从合成格取走成品），维护成本却不低。
- **删除内容**：
  - `TaskType.CRAFT` 枚举值与所有 `case CRAFT` 分支（`Task` / `ClientTasks` / `TaskHud` / 列表 / 编辑器）；
  - 合成计数的三个 mixin：`ResultSlotServerMixin`、`CraftingMenuMixin`、`InventoryMenuMixin`、客户端兜底 `ResultSlotMixin`；
  - `CraftEventS2C` 包与 `EasyTaskMgr#notifyCraft`、`ClientNet` 的接收逻辑、`easytaskmgr.mixins.json`；
  - 编辑器里的「合成」类型、数量/进度相关分支，以及 `type.craft` 文案。
- **兼容性**：旧任务文件里 `"type": "CRAFT"` 会被 Gson 解析成 `null`，`Task#normalize()` 自动回落为 `NORMAL`，不会崩档。
- **文件**：`task/TaskType.java`、`task/Task.java`、`client/ClientTasks.java`、`client/TaskHud.java`、
  `client/ui/TaskEditScreen.java`、`client/ui/TaskListScreen.java`、`client/ClientNet.java`、
  `EasyTaskMgr.java`、`i18n/I18n.java`、`fabric.mod.json`、`easytaskmgr.client.mixins.json`。

### 2. 10:40 修改：列表改为单页滚动

- **原因**：分页一次只能看 4 条，任务多时要来回翻页，不如直接滚。
- **实现**：
  - `TaskListScreen` 不再有页码和 `<` `>` 按钮，改为 `scrollOffset` + `visibleRows`
    （`visibleRows = max(3, (height - 58 - 46) / 34)`，按窗口高度自适应）；
  - 覆盖 `mouseScrolled`：光标在列表区域内时按滚轮方向移动一行，并夹在 `[0, total - visibleRows]`；
  - 右侧画一条滚动条（轨道 + 按比例定位的滑块），顶部显示 `1-4 / 共 12 条 · 滚轮滚动`；
  - 行内按钮（勾选 / 改 / ×）随 `scrollOffset` 重建，滚动后位置正确；
  - 空列表显示「还没有待办，按 B 添加」。
- **文件**：`client/ui/TaskListScreen.java`、`i18n/I18n.java`（新增 `list.range` / `list.scrollHint` / `list.empty`）。

### 3. 10:48 验证（UI 自动化）

- 沿用上一轮的临时调试入口（环境变量 `ETM_DEBUG_SCREEN=list`，进服自动打开列表），
  预置 12 条不同类型任务后截图：
  - 初始：`待办列表` + `1-4 / 共 12 条 · 滚轮滚动`，右侧滚动条在最上方；
  - 滚轮向下 4 格后：`5-8 / 共 12 条`，列表内容整体下移，滚动条滑块同步移动。
- 截图完成后已删除调试代码并重新构建，产物 `easytaskmgr-1.0.0-beta.2.jar`（122 KB）。
- 版本号保持 **1.0.0-beta.2**（本轮为同版本修订）。

---

## 2026-10-31 · 第七轮：beta.2 补丁（英文名、定时模式、游戏时间）

版本仍为 `1.0.0-beta.2`（09:20）。

### 1. 08:45 修复：英文模式下候选词仍是中文

- **原因**：物品名、成就标题来自**原版语言文件**，`Component#getString()` 永远按**游戏语言**解析；
  模组语言选了 English 但游戏是中文时，`getHoverName()` 依然返回「钻石」。
- **修法**：新增 `client/LangResources`：从资源包读取 `minecraft:lang/en_us.json`（以及 `zh_cn.json`）
  到内存 Map，按模组语言取文案；读不到时返回 null 由调用方回退。
  `Suggestions`（物品/成就候选）、`AdvancementCatalog`（成就标题/描述）、`AdvancementInfo`（方块名）改用它；
  `Language.apply()` 切换语言时清空这些缓存。
- **文件**：`client/LangResources.java`（新增）、`client/Suggestions.java`、
  `client/AdvancementCatalog.java`、`client/AdvancementInfo.java`、`client/Language.java`。

### 2. 08:50 修复：合成任务仍显示「容器计数」开关

- **原因**：`COLLECT` 与 `CRAFT` 共用同一个分支，按钮一起创建了。
- **修法**：按钮创建条件改为 `task.type == TaskType.COLLECT`；合成任务只看合成次数，不再有容器选项。
- **文件**：`client/ui/TaskEditScreen.java`。

### 3. 08:52 修复：定时按钮显示 `timer.on_join`

- **原因**：`TimerMode.label()` 用 `name().toLowerCase()` 拼 key，`ON_JOIN` 得到 `timer.on_join`，
  而实际注册的 key 是 `timer.join`。
- **修法**：在枚举里显式 switch 映射到 4 个 key；同时新增了新的模式 key。
- **文件**：`task/TimerMode.java`、`i18n/I18n.java`。

### 4. 08:55 新增：每次加入时提醒

- 新增 `TimerMode.EVERY_JOIN`：每次进入世界都提醒，**不自动完成**；
  原来的 `ON_JOIN` 保留「提醒一次后自动完成」的语义。
- `ClientTasks.onJoinWorld` 分别处理两种模式；HUD/列表显示「上线时 / 每次上线」。
- **文件**：`task/TimerMode.java`、`task/Task.java`、`client/ClientTasks.java`、`i18n/I18n.java`。

### 5. 08:58 新增：间隔提醒「至少 1 分钟」提示

- 编辑界面在 INTERVAL 模式的说明文字后追加 `edit.intervalHint`（「（至少 1 分钟）/ (min 1)」），
  输入解析依旧 `Math.max(1, ...)`。
- **文件**：`client/ui/TaskEditScreen.java`、`i18n/I18n.java`。

### 6. 09:05 新增：游戏内时间提醒

- `TimerMode.GAME_TIME`，`Task` 增加 `gameHour / gameMinute / gameDayInterval / lastGameDay`。
- **换算**：MC 的 0 tick 对应 06:00，所以
  `target = ((hour - 6 + 24) % 24) * 1000 + minute * 1000 / 60`；
  每 tick 读 `level.getOverworldClockTime()`，取 `day = total / 24000`、`dayTime = total % 24000`，
  在 `[target, target + 40)` 窗口内、且 `day % gameDayInterval == 0`、且当天没触发过时提醒一次。
- **编辑界面**：选到「游戏内时间」后显示 时 / 分 / 每 N 天 三个输入框，外加
  「白天 06:00」「黑夜 19:00」两个预设按钮（直接写入 6:00 / 19:00）。
- **显示**：HUD 与列表显示「每天 06:00」「每 2 天 19:00」这类文案。
- **文件**：`task/TimerMode.java`、`task/Task.java`、`client/ClientTasks.java`、
  `client/TaskHud.java`、`client/ui/TaskEditScreen.java`、`i18n/I18n.java`。

### 7. 09:35 验证（UI 自动化）

- 直接模拟键盘在这台机器上不可靠（窗口焦点受 Windows 前台锁影响），改用**临时调试入口**：
  客户端读环境变量 `ETM_DEBUG_SCREEN=timer|collect|craft` 时，进服 3 秒后自动打开对应编辑器，
  截完图后已把这段调试代码删除并重新构建。
- 截图确认（游戏语言=中文、模组语言=English）：
  - **游戏内时间编辑器**：`Type: Timed reminder`、`Reminder: In-game time`（不再是 `timer.game_time`）、
    `In-game time: hour / minute / every N days`、6/0/1 三个输入框 + `Day 06:00` / `Night 19:00` 预设；
  - **收集编辑器**：物品名显示 `Diamond`（中文客户端下也是英文，说明 LangResources 生效），
    数量 64、`Containers: Off` 正常；
  - **合成编辑器**：物品名 `Diamond Pickaxe`，**没有**「容器计数」按钮，确认第 2 条修复。
- `gradlew build` 通过，产物 `easytaskmgr-1.0.0-beta.2.jar`（129 KB）。
- 未覆盖：游戏时间提醒的「到点触发」需要真实等待 06:00，逻辑已按 40 tick 窗口实现，建议实机挂一次确认。

---

## 2026-10-31 · 第六轮：1.0.0-beta.2 中英双语

版本号 `1.0.0-beta.2`（07:45）。

### 1. 07:15 新增：i18n 基础设施

- 新增 `i18n/I18n.java`：内置中英两套文案（`key → {zh, en}`），
  提供 `t(key)`、`t(key, args)` 和「指定语言」的 `t(lang, key, args)`。
- 语言枚举 `I18n.Lang`：`byCode()` 规则是「以 `zh` 开头 → 中文，其余 → 英文」。
- **为什么放在公共 source set**：`Task`、`TaskType`、成就描述器都要用；
  服务端生成成就条件时也要按「请求方语言」输出。

### 2. 07:18 新增：语言选择策略与配置开关

- `ModConfig` 新增 `language` 字段，取值 `auto` / `zh_cn` / `en_us`，默认 `auto`。
- 新增 `client/Language.java`：`auto` 时读 `Minecraft#getLanguageManager().getSelected()`，
  只有 `zh_cn` 走中文，其他语言（含未翻译语言）一律英文；语言变化时清空成就目录缓存并重新请求。
- 客户端启动时调用一次，每 20 tick 再检查一次（跟随游戏内切换语言）；
  设置界面里改语言立即生效。
- **文件**：`config/ModConfig.java`、`client/Language.java`、`client/EasyTaskMgrClient.java`。

### 3. 07:22 改造：所有界面文案走 I18n

- 枚举标签：`Priority#label` / `TaskType#label` / `TimerMode#label` 改为按 key 取文案。
- `Task#progressText`：「每 N 分钟 / 上线时 / 已到达」支持中英。
- `ClientTasks`：完成提示、附加提醒、定时提醒、距离提示全部走 I18n。
- `TaskHud`：标题、队友分组、交互提示、方向词（正前/右前/…）中英双语。
- 三个界面（快速添加 / 列表 / 编辑）与设置界面的按钮、标签、提示、报错全部替换。
- **踩坑**：`notify.done` 文案里本来就带 `✓`，代码里又拼了一次，出现「✓ ✓」，
  把文案改成不带勾的「已完成：%s / Completed: %s」。

### 4. 07:30 改造：成就条件按请求方语言生成

- `AdvancementDescriber` 所有方法增加 `I18n.Lang` 参数，文案改成 key（如 `adv.obtain`）。
- `AdvancementQueryC2S` 增加 `lang` 字段；客户端请求时带上当前语言；
  服务端按该语言生成成就条件文本（服务端自己的语言设置不再影响结果）。
- `AdvancementInfo` 的面板标题（标题/描述/前置成就/达成方式）也走 I18n。
- **文件**：`advancement/AdvancementDescriber.java`、`net/AdvancementQueryC2S.java`、
  `EasyTaskMgr.java`、`client/AdvancementCatalog.java`、`client/AdvancementInfo.java`。

### 5. 07:40 修复：英文界面下设置页排版

- **现象**：英文标签更长，`Background / Distance` 等按钮文字被截断，
  语言按钮和右侧 HUD 预览框重叠。
- **修法**：设置页改为紧凑布局（行高 20、按钮高 16、开关两列各 84 宽），
  语言按钮只显示「自动 / 简体中文 / English」并右移到 124px，
  六项开关分三行两列，底部按钮上移到 `height-34`，提示文字放到最底部。
- **文件**：`client/ui/ConfigScreen.java`。

### 6. 07:45 验证

- 配置 `language=en_us` 实测：HUD 显示 `Todos 2` / `Go to the mine` / `319m · behind 158°`，
  聊天栏提醒 `Reminder: Check the farm`，设置页全英文且无重叠。
- 设置里把语言切成「自动」（此时 MC 语言为中文）→ 界面立即变回中文，说明 auto 分支生效。
- `zh_cn` 强制模式与 `auto + 非中文` 的英文回退由同一套 `byCode` 逻辑覆盖。

### 7. 已知限制

- 物品名由 MC 自身语言决定（客户端是中文则显示中文物品名），模组只负责自己的界面文案；
- 语言切换后成就目录需要重新拉取，会有约 1 秒的空窗（服务端没装模组时回退本地数据）。

---

## 2026-10-30 · 第五轮：shift 合成、HUD 点击、文案与排版

### 1. 22:02 修复：shift 合成仍然不计数

- **分析**：`onTake` 在 shift 快速转移时不一定走「客户端/服务端可见的那条路径」，
  26.3 的 `AbstractContainerMenu` 里还多了一条 `safeClone` 分支。
- **修法**：
  - 新增 `CraftingMenuMixin`、`InventoryMenuMixin`：在 `quickMoveStack` 的 **RETURN** 处判断
    「索引 0 = 结果槽」，把移走的成品数量上报（工作台和背包 2x2 都覆盖）；
  - 新增 `EasyTaskMgr#notifyCraft`：按「玩家 + tick + 物品 + 数量」去重，
    这样 `onTake` 与 `quickMoveStack` 同时触发也只算一次；
  - 原有 `ResultSlotServerMixin` 改为调用同一个方法，逻辑收敛到一处。
- **文件**：`mixin/CraftingMenuMixin.java`、`mixin/InventoryMenuMixin.java`、
  `mixin/ResultSlotServerMixin.java`、`EasyTaskMgr.java`、`easytaskmgr.mixins.json`。

### 2. 22:06 修复：坐标任务的 z 输入框盖住半径输入框

- **原因**：x/y/z 各 90 宽 + 间距 6，z 占到 `x0+192..282`，而半径框从 `x0+240` 开始，
  两个控件重叠，半径只剩最右边 18px 可点。
- **修法**：x/y/z 改成各 70 宽（`x0 / +76 / +152`，结束于 222），半径独占 `x0+232..300` 的 68px。
- **文件**：`client/ui/TaskEditScreen.java`。

### 3. 22:08 修复：设置界面文字错位（截图 4/5）

- **现象**：底部提示「按住右侧 HUD 预览框拖动…」压在「重置位置 / 完成」按钮上；
  没有任务时预览框高度只按 1 行算，示例文字溢出到框外。
- **修法**：提示文字移到标题下方（y=26）；空任务时预览按 3 行高度计算。
- **文件**：`client/ui/ConfigScreen.java`。

### 4. 22:10 修改：暂时关闭「显示前置条件」开关（代码保留）

- 新增配置 `advancementRequirements`（默认 `false`）：
  关闭时编辑界面不创建该按钮、HUD 不追加「需要：…」行；
  解析代码（`AdvancementDescriber` / `AdvancementInfo` / 服务端目录）全部保留，
  以后把配置改成 `true` 就能恢复。
- **文件**：`config/ModConfig.java`、`client/ui/TaskEditScreen.java`、`client/TaskHud.java`。

### 5. 22:12 新增：坐标任务的方向箭头

- HUD 坐标行前面加 8 方向箭头（↑ ↗ → ↘ ↓ ↙ ← ↖），角度由「目标方位 − 玩家朝向」算出；
  设置项 `showDirectionArrow` 控制开关（设置界面里暂未加按钮，改配置文件即可）。
- **文件**：`client/TaskHud.java`、`config/ModConfig.java`。

### 6. 22:13 修改：定时提醒文案

- 原来直接把任务内容当提醒，现在统一成 `任务提醒：<内容>`，
  聊天栏和 HUD 顶部提示都用这个格式（上线提醒、间隔提醒都走同一处）。
- **文件**：`client/ClientTasks.java`。

### 7. 22:14 修改：附加提醒单独一行

- 完成时先发 `任务完成：<内容>（进度）`，如果任务有「附加提醒」，
  再单独发一行 `完成「<内容>」任务的附加提醒：<提醒文案>`。
- **文件**：`client/ClientTasks.java`。

### 8. 22:16 新增：在 HUD 上点击完成普通任务

- 交互设计：按 `V` 进入「HUD 交互」模式（鼠标释放、边框变黄、底部提示「点击任务即可完成」），
  点击普通任务行直接完成；再按 `V` 退出并重新锁定鼠标。模式状态持久化到配置 `hudInteractive`。
- **实现**：
  - `TaskHud` 渲染时把每个可点击任务行的屏幕矩形记到 `HITS`；
  - `MouseHandlerMixin` 在 `onButton` HEAD 拦截左键按下，命中 `HITS` 就完成并 `ci.cancel()`（防止顺手砍方块）；
  - `EasyTaskMgrClient` 里处理 `V` 键切换、每 tick 确保鼠标处于释放状态。
- **文件**：`client/TaskHud.java`、`client/mixin/MouseHandlerMixin.java`、
  `client/EasyTaskMgrClient.java`、`client/Keybinds.java`、`config/ModConfig.java`。

### 9. 22:20 本轮踩坑

- 又踩了一次 `mc.screen`：26.3 没有这个字段，必须用 `mc.gui.screen()`；
- `TaskHud` 构造函数写成了 `private`，导致 `new TaskHud()` 编译失败，改回 `public`；
- 交互模式一开始被「打开任意界面就自动关闭」的逻辑在标题界面就关掉了，
  改成只在 `screen == null` 时检查鼠标是否被重新抓取，不再主动关闭。

### 10. 22:24 验证

- 上线提醒实测输出 `[EasyTaskMgr] 任务提醒：上线看看农田`（新格式）；
- 坐标任务 HUD 显示 `↑ 120, 45, -300 · 327m · 正后 158°`（箭头 + 距离 + 方向）；
- HUD 交互模式实测：开启后边框变黄、出现提示行，点击「买点面包」后待办数从 2 变成 1，
  日志里能看到命中矩形 `[208,48]-[422,59]`；
- shift 合成：本轮改成 `quickMoveStack` 钩子后**尚未在实机完成一次 shift 合成**（自动化环境点不开合成界面），
  需要你手动验证一次，若仍不计数请把 `run/logs/latest.log` 发我。

---

## 2026-10-30 · 第四轮：beta 1.0 前的 7 个问题

版本号改为 `1.0.0-beta.1`（21:40）。

### 1. 21:12 修复：候选点了没反应（并加滚动）

- **根因**：26.3 的 `MouseButtonEvent#button()` 左键返回 **1**（不是 GLFW 的 0），
  我原来写 `event.button() == 0`，判断永远不成立 → 点候选毫无反应。
- **修法**：改成 `event.button() <= 1`（兼容两种约定），同时把候选做成浮层：
  最多显示 6 条、滚轮翻页（`mouseScrolled`）、右上角显示 `1-6 / 48` 计数、上下箭头提示。
- **文件**：`client/ui/TaskEditScreen.java`。

### 2. 21:15 修复：编辑界面文字重叠

- **现象**：「附加提醒」标签和下面的「前置条件」正文叠在同一行。
- **修法**：开启前置条件时不再画「附加提醒」标签（同时把输入框 `visible=false`），
  条件面板从 y=148 开始，先画一行标题「前置条件 / 达成方式」再逐行显示。
- **文件**：`client/ui/TaskEditScreen.java`。

### 3. 21:18 修复：成就候选不全 / 混进配方

- **根因（重要）**：26.3 的服务端只把「玩家有进度」的成就同步给客户端，
  联机时客户端往往只有 `recipes/root` 两条——所以候选里全是配方，真成就一个都没有。
- **修法**：新增一套「成就目录」协议：
  - C2S `AdvancementQueryC2S(id)`（id 为空 = 要列表，否则要详情）；
  - S2C `AdvancementDataS2C(json)`；
  - 服务端从 `ServerAdvancementManager#getAllAdvancements()` 取全量，过滤 `recipes/` 和无 display 的条目；
  - 客户端 `AdvancementCatalog`：进服拉一次列表（本次实测 126 条），用到某个成就时再按需拉详情；
  - 服务端没装模组时，客户端回退到本地 `ClientAdvancements`。
- **文件**：`net/AdvancementQueryC2S.java`、`net/AdvancementDataS2C.java`、`EasyTaskMgr.java`、
  `client/AdvancementCatalog.java`、`client/Suggestions.java`。

### 4. 21:22 修复：成就名显示英文

- **根因**：服务端 `Component#getString()` 用服务端语言（通常是 en_us），
  所以「钻石」在英文服务端会变成 `Diamond`。
- **修法**：服务端只发**翻译键**（`TranslatableContents#getKey()`）和物品/方块 **id 占位符**，
  客户端用 `Component.translatable(key).getString()` 和 `Suggestions.nameOfItem(id)` 现场翻译；
  条件文本里的 `{{item:minecraft:diamond}}` 在 `AdvancementInfo#localize` 里替换成中文名。
- **文件**：`advancement/AdvancementDescriber.java`、`client/AdvancementCatalog.java`、
  `client/AdvancementInfo.java`、`EasyTaskMgr.java`。

### 5. 21:30 修复：设置界面拖不动 HUD

- **根因**：同第 1 条（左键 == 1），`mouseClicked` 直接返回 false，后续 `mouseDragged` 不会触发。
- **修法**：修正按键判断；拖拽命中区域改成「和 HUD 等大的矩形」（`hudRect()` 与 HUD 同一套锚点算法），
  按住预览框拖动时实时换算 `hudXPercent/hudYPercent` 并落盘。
- **实测**：从 99%/2% 拖到 75%/25% 成功，标签同步更新。
- **文件**：`client/ui/ConfigScreen.java`。

### 6. 21:34 修复：设置界面文字错位 / 宽度调了没反应

- **现象**：`HUD 宽度 220p` 被「宽度」按钮压住；「最多显示 N 条」不显示；
  调宽度后 HUD 没变化。
- **原因**：
  1. 设置界面用 `cx ± 262` 定位，小窗口（GUI 427px）直接跑到屏幕外；
  2. HUD 面板宽度是 `min(maxWidth+14, 文本宽+14)`，短文本时宽度由文本决定，调 maxWidth 看不出变化。
- **修法**：
  - 设置界面改成左列布局：标签 x=16、数值 x=90、按钮 x=128/166，完全不和右侧 HUD 预览重叠；
  - HUD 面板宽度改为**固定** `min(hudMaxWidth+14, 屏幕宽-8)`，调宽度立刻可见（实测 200 → 260 变宽）。
- **文件**：`client/ui/ConfigScreen.java`、`client/TaskHud.java`。

### 7. 21:38 修复：前置条件不能显示 / 识别不出「获得钻石」

- **原因**：
  1. HUD 没画条件行；
  2. 条件解析只输出触发器类型，没抽物品/数量；
  3. 成就 id 不带命名空间时（如 `adventure/trim_...`）解析失败。
- **修法**：
  - `AdvancementDescriber` 解析 `InventoryChangeTrigger` 的 `ItemPredicate`
    （物品 + `count().min()`）、`ConsumeItemTrigger`、`EnterBlockTrigger`、`RecipeCraftedTrigger` 等，
    输出「获得 1 个 钻石」；id 自动补 `minecraft:` 前缀并支持按路径兜底匹配；
  - HUD 对 `showRequirements` 的成就任务追加「需要：…」行（最多两条，自动换行）；
  - 编辑器下方的条件面板同样来自这套解析（服务端详情 + 本地兜底）。
- **实测**：HUD 显示「需要：获得 钻石」「需要：获得 铁锭」「需要：获得 烈焰棒」。

### 8. 21:45 本轮踩坑

- 用 PowerShell 移动字符串块给 `ConfigScreen` 调绘制顺序时，`IndexOf` 命中了 `init()` 里的同名锚点，
  把代码插进了错误位置，文件被改坏；直接恢复备份文件重来。
  **教训**：字符串手术前先确认锚点唯一（比如带上方法名或完整语句）。
- 恢复 `ConfigScreen2.java` 备份时把「左键 <=1」的修复和按钮宽度改动一起丢了，
  重新打补丁后才 build 通过。**教训**：改动要落在唯一来源上，别在“备份/生成文件”之间来回覆盖。

### 9. 已知限制（beta 1.0）

- 成就目录依赖服务端装模组；纯客户端连原版服务器时只能看到本地已同步的成就；
- 成就目录在进服时拉一次，之后新增的数据包成就要重连才会出现；
- 设置界面拖动时，HUD 预览会盖住左侧文字（松手即恢复）；
- 候选浮层展开时会盖住下面的开关按钮（选中后自动收起）。

---

## 2026-10-30 · 第三轮：界面返工 + 4 个 bug + 5 个新功能

### 0. 先记一次事故（20:12）

用 PowerShell 批量把 `X.java.new` 改名成 `X.java` 时，`Move-Item -Force $dest $src` 的参数写反了，
结果把**新代码覆盖成了旧代码**（9 个文件），只能按记录重写一遍。

- **教训**：批量改名/移动不要用脚本猜参数顺序；逐个 `Move-Item 源 目标` 并先 `Test-Path` 目标。
- 之后改成「先写到 `.java` 新路径（原文件不存在时 write 直接创建）」，不再走中间文件。

### 1. 修复：新建 / 编辑界面文字与输入框重叠（20:00）

- **现象**：截图里 `目标（物品 id / 成就 id）`、`数量 / 半径`、`间隔分钟` 这些标签压在了输入框上。
- **原因**：标签和输入框用的是同一行 y 坐标，只差了 0-2px，字高 9px 自然叠在一起。
- **修法**：重写 `TaskEditScreen` 的布局函数 `layout()`：标签在上一行（`rowTarget - 12`），
  输入框在 `rowTarget`，每个区块间距 24-26px；「保存 / 取消」固定在底部。
- **涉及文件**：`client/ui/TaskEditScreen.java`。

### 2. 修复：长文本用省略号 → 改成自动换行（20:03）

- **新增** `client/TextWrap.java`：按像素宽度逐字累加换行，遇到空格优先在空格处断，
  中文按字符断；提供 `wrap(font, text, maxWidth)` 与单行 `clip()`（只在明确只要一行的地方用）。
- **HUD**：标题、细节行、队友行、完成提示全部走 `TextWrap.wrap`，面板宽度取 `hudMaxWidth`
  （新配置项，默认 200px，设置界面可 ±20 调整）。
- **列表界面**：每条任务最多两行，行高 34px，超出才显示省略。
- **涉及文件**：`client/TextWrap.java`（新增）、`client/TaskHud.java`、`client/ui/TaskListScreen.java`、
  `config/ModConfig.java`（`hudMaxWidth`）。

### 3. 修复：改 HUD 位置时 HUD「变大」（20:08）

- **现象**：点 X/Y 按钮调位置，HUD 的边框会突然扩到半个屏幕。
- **原因**：`GuiGraphicsExtractor#outline(x, y, w, h, color)` 的参数是**宽高**而不是右下角坐标，
  我传的是 `(left, top, right, bottom)`，于是画出一个 `right × bottom` 那么大的框。
  反编译确认：`outline` 内部就是 `fill(x, y, x + width, y + 1, color)`。
- **修法**：新增 `TaskHud.frame(...)`，用四条 `fill` 手画边框，彻底避开这个参数歧义；
  HUD 与设置预览框都改用它。
- **涉及文件**：`client/TaskHud.java`、`client/ui/ConfigScreen.java`。

### 4. 修复：shift 快速合成不计数（20:16）

- **原因**：shift 一键取走成品时，客户端本地菜单不一定执行 `ResultSlot#onTake`，
  只挂客户端钩子会漏计（普通点取则正常）。
- **修法**：改成**服务端权威计数**：
  - 新增主 source set 的 `mixin/ResultSlotServerMixin`（`easytaskmgr.mixins.json`），
    在服务端 `ResultSlot#onTake` 时把「物品 id + 数量」打包；
  - 新增 `net/CraftEventS2C` 包，只发给该玩家；
  - 客户端 `ClientNet` 收到后调用 `ClientTasks.onCrafted(id, count)` 累加；
  - 客户端原有的 `ResultSlotMixin` 只在「服务端没装模组」时兜底（`ClientNet.serverHasMod()` 判断），
    避免单机/联机重复计数。
- **涉及文件**：`mixin/ResultSlotServerMixin.java`（新增）、`net/CraftEventS2C.java`（新增）、
  `EasyTaskMgr.java`、`client/ClientNet.java`、`client/ClientTasks.java`、`client/mixin/ResultSlotMixin.java`、
  `resources/easytaskmgr.mixins.json`（新增）、`fabric.mod.json`。

### 5. 新功能：按类型显示输入框（20:20）

- 编辑界面改成「按类型动态建控件」：
  - 普通：只有内容 / 优先级 / 附加提醒；
  - 收集、合成：物品名 + 数量 +（收集）容器计数开关；
  - 成就：成就名 + 显示前置条件开关；
  - 坐标：x / y / z + 半径；
  - 时间：提醒方式 + 间隔分钟。
- 切换类型时先把当前输入写回 `Task`（`captureFields()`）再重建控件，编辑内容不会丢。
- **涉及文件**：`client/ui/TaskEditScreen.java`。

### 6. 新功能：HUD 拖拽定位（20:24）

- 设置界面里画一个和 HUD 同尺寸的预览框（同一套锚点算法），
  在框内按下鼠标即可拖动，实时换算成 `hudXPercent` / `hudYPercent` 并保存。
- 用 `Screen#mouseClicked / mouseDragged / mouseReleased`（26.3 的参数是 `MouseButtonEvent`）。
- 原来的 ±2% 按钮保留，方便微调；另外加了「HUD 宽度」「条数」按钮。
- **涉及文件**：`client/ui/ConfigScreen.java`。

### 7. 新功能：容器 / 末影箱 / 潜影盒计数（20:27）

- `Task` 新增 `includeContainers`（任务级开关，编辑界面可切）与 `containerCount`（运行期，持久化）。
- 计数规则：
  - 背包（含副手）始终统计；
  - 打开过箱子 / 末影箱 / 任意容器界面时，扫描 `AbstractContainerMenu.slots`，
    把该容器里目标物品数量取最大值记进 `containerCount`（反复开关不会让数字抖动）；
  - 背包里的潜影盒读 `DataComponents.CONTAINER` 组件，把内部物品也算进去。
- **涉及文件**：`task/Task.java`、`client/ClientTasks.java`、`client/ui/TaskEditScreen.java`。

### 8. 新功能：按中文名搜索物品 / 成就（20:31）

- 新增 `client/Suggestions.java`：
  - 物品：遍历 `BuiltInRegistries.ITEM`，用 `new ItemStack(item).getHoverName()` 取中文名；
  - 成就：遍历 `ClientAdvancements.progress()` 的 holder，用 `DisplayInfo#title()` 取标题；
  - `search()` 按名字 / id 模糊匹配，名字前缀优先；
  - `resolveItem()` / `resolveAdvancement()`：用户输入名字 → 存进任务时转成规范 id。
- 编辑界面在输入框下方显示最多 4 条候选，鼠标点击即填入（点击只更新输入框，不重建控件，避免丢焦点）。
- **涉及文件**：`client/Suggestions.java`（新增）、`client/ui/TaskEditScreen.java`。

### 9. 新功能：成就前置条件展示（20:34）

- 新增 `client/AdvancementInfo.java`：从 `ClientAdvancements` 取目标成就，输出
  「标题 / 描述 / 前置成就 / 达成方式」四类信息；
  达成方式按 `Criterion#trigger()` 的类名映射成中文（收集物品、击杀生物、吃东西、进入方块、
  到达位置、等待时间、交易、附魔、钓鱼……），未知类型回退到去掉 `Trigger` 的类名。
- 编辑界面「显示前置条件」开关打开后，在表单下方按 10px 行高、自动换行地列出这些信息，
  超出可视区域自动截断。
- **涉及文件**：`client/AdvancementInfo.java`（新增）、`client/ui/TaskEditScreen.java`。

### 10. 本轮验证

- `gradlew build` 通过，产物 `easytaskmgr-0.1.0.jar`（86 KB）；
- 启动测试服 + 客户端联机，HUD 每帧渲染无异常（含长文本换行、容器任务、成就任务）；
- ### 10. 修复：列表页最后一行被底部按钮压住（20:40）

- **现象**：第 5 条任务的第二行文字和左下角「清空已完成」重叠。
- **原因**：每页 5 条 × 34px 从 y=58 起排，最后一行落在 y=194~228，而底部按钮在 y=180~212，必然重叠。
- **修法**：每页改成 4 条；「清空已完成」从按钮上方挪到底部工具条最右侧，
  底部一排变成 `< > 显示已完成 新增 设置 关闭 清空已完成`。
- **涉及文件**：`client/ui/TaskListScreen.java`。

### 11. 修复：换行把「4.」单独挤成一行（20:38）

- **现象**：HUD 里长任务的第一行只有一个「4.」，正文被推到下一行。
- **原因**：`TextWrap` 遇到空格就断行，「4. 」后面正好是空格，于是从那里断开。
- **修法**：只有当空格前的内容宽度超过 `maxWidth / 2` 时才允许在空格处断行，
  否则继续按字断行；中文长句因此能铺满整行。
- **涉及文件**：`client/TextWrap.java`。

### 12. 修复：编辑界面仍然偏挤（20:48）

- 第一版重排后仍有 3-4px 级别的小重叠（类型按钮压住「物品名」标签、开关压住「附加提醒」标签）。
- **修法**：把纵向分区写成固定常量：内容 44-72 / 类型·优先级 72-90 / 目标 92-122 /
  开关 128-146 / 附加提醒 148-178 / 按钮 190-210，全部落在 240px 高度内；
  候选列表改成「浮层」——画在控件之后、带背景和边框，不额外占高度；
  显示成就前置条件时自动隐藏「附加提醒」输入框，把空间让给条件文本。
- **涉及文件**：`client/ui/TaskEditScreen.java`。

### 13. 本轮验证（20:55）

用「本地测试服 + 开发客户端」跑真实画面，并用窗口截图核对：

- **HUD**：4 条任务正常渲染，长任务自动换成两行（修好了「4.」单独一行的问题），
  坐标任务的 `120, 45, -300 · 334m · 左后 139° · 0.0 m/s` 正常显示，
  边框是 1px 细线（确认 `outline` 参数问题已解决）。
- **列表界面**：4 条/页，最后一行不再和「清空已完成」重叠，底部工具条排成
  `< > 显示已完成 新增 设置 关闭 清空已完成`。
- **编辑界面**：分别截图确认了「普通」「收集物品」「完成成就」三种类型的布局：
  标签都在输入框上方、没有重叠；「收集物品」下输入 `diamond` 会弹出
  `Diamond / Diamond Axe / Diamond Boots / Diamond Chestplate` 四条候选（名字 + id）。
- **顺手修的 bug**：切换类型后候选列表会残留上一个类型的条目（截图发现），
  已在 `init()` 里清空 `suggestions`。
- **未能自动验证**：shift 合成。自动化环境只能注入键鼠、没法稳定点开工作台完成一次合成，
  这轮只确认了服务端钩子随服务器启动正常加载（没有注入报错），需要你手动合成一次确认计数。

### 14. 已知限制（截至本轮）

- 编辑界面的「前置条件」文本最多显示 4 行，超出部分截断；要看全文可以把 GUI 缩放调小；
- 候选列表是浮层，展开时会盖住下面的「容器计数 / 显示前置条件」按钮，选完就恢复；
- 容器计数是「见过的最大值」累计，不区分是哪个箱子：同一种物品分散在多个箱子时取最大单箱数量；
- 成就前置条件只做「前置 + 触发器类型」的文字摘要，不解析具体的物品 / 生物条件参数。

---

## 2026-10-29 · 第二轮：联机验证 + 空值兜底

### 1. 联机共享的端到端验证

- **测试环境**：本机 `runServer`（平坦世界、关闭正版验证与白名单）+ 两个 `runClient`
  （`--quickPlayMultiplayer 127.0.0.1:25565 --username Alice/Bob`）。
- **结果**：
  - Alice 先进服，服务端日志 `收到 Alice 的共享待办（1706 字节），转发给 0 名队友`；
  - Bob 进服后立刻收到 Alice 的缓存列表：`收到队友 Alice 的 5 条共享待办`
    （JOIN 补发链路生效）；
  - Bob 上报后服务端 `转发给 1 名队友`，Alice 端 `收到队友 Bob 的 5 条共享待办`。
- **为什么是 5 条不是 6 条**：那 6 条测试任务里有一条「上线先看农田」是上线提醒，
  进服时已经自动完成，而 `shared()` 只同步未完成任务——行为符合预期。

### 2. 为了排查加的两行日志（保留）

- `EasyTaskMgr.java`：收到 C2S 后打印「收到 X 的共享待办（N 字节），转发给 M 名队友」；
- `ClientNet.java`：收到 S2C 后打印「收到队友 X 的 N 条共享待办」。
- 这两行在联机出问题时能直接定位是「没发出去」「服务端没转发」还是「客户端没收到」。

### 3. 修复：任务字段的空值兜底

- **问题**：Gson 反序列化时，如果 JSON 里显式写了 `"remindText": null`，
  字段默认值 `""` 不会生效，`Task#progressText()`、`TaskHud#detail()` 里的 `isBlank()` 会 NPE。
  手工编辑任务文件或老版本数据迁移时很容易踩到。
- **修法**：`Task#normalize()` 统一把 null 字符串补成 `""`、枚举补默认值、
  数量/半径/间隔夹到合法范围；在 `TaskStore#load()` 和 `ClientNet#receive()` 两处入口调用。
- **改动文件**：`task/Task.java`、`task/TaskStore.java`、`client/ClientNet.java`。

### 4. 清理

- 删掉测试用的 `runclient-*.log`、`runserver.log` 和 `run/config/easytaskmgr*.json` 种子数据；
- 重新 `gradlew build`，产物 `build/libs/easytaskmgr-0.1.0.jar`（66 KB）确认包含
  公共类、客户端类、mixin、语言文件和图标。

---

## 2026-10-29 · 第一轮：从零搭出可用版本

### 1. 工程改名（minimap → easytaskmgr）

- **改动文件**：`settings.gradle`、`gradle.properties`、`build.gradle`、`fabric.mod.json`、`src/**` 目录结构。
- **做法**：`rootProject.name = 'easytaskmgr'`，`group=com.easytaskmgr`，Java 包从 `com.minimap` 迁到 `com.easytaskmgr`；
  `build.gradle` 的 `loom.mods` 块同步改名，`fabric.mod.json` 的 `id`、入口类、mixin 配置一起换掉。
- **踩坑**：用 PowerShell 的 `Set-Content` 改带中文注释的 `build.gradle` 时写成了 ANSI 编码，Groovy 解析直接报
  `Unexpected input`。**结论：所有含中文的文本文件一律用 UTF-8（无 BOM）写入**，之后改为用编辑器工具写文件。

### 2. 核心数据模型

- **新增文件**：
  - `task/Task.java`：一条待办的全部字段（文本、优先级、类型、目标、数量、坐标、计时、进度、共享标记）。
  - `task/Priority.java`：低 / 普通 / 高 / 紧急，带权重与颜色。
  - `task/TaskType.java`：普通 / 收集 / 合成 / 成就 / 坐标 / 时间。
  - `task/TimerMode.java`：上线提醒 / 间隔提醒。
- **实现方法**：全部用 Gson 友好的字段（基本类型 + 枚举 + String），不做自定义序列化器，
  这样 `Task[]` 直接 `toJson/fromJson` 就能落盘和走网络。运行期字段（`progress`、`triggered`、`nextTriggerAt`）
  也持久化，重启游戏后进度不丢。

- **新增文件**：`task/TaskStore.java`
  - 单例仓库，持有 `List<Task>`；`load()/save()/saveIfDirty()` 读写 `config/easytaskmgr-tasks.json`；
  - `active()` 按「优先级降序 + 创建时间升序」排序，HUD 和列表共用同一套排序；
  - `shared()` 返回未完成且开启共享的任务副本，供网络层序列化；
  - 写盘用 `dirty` 标记 + 客户端 5 秒节流，避免每 tick 都碰磁盘。

- **新增文件**：`config/ModConfig.java`
  - HUD 位置（屏幕百分比）、背景开关、显示条数、队友开关、距离/速度开关、音效、共享总开关、HUD 隐藏；
  - 读写 `config/easytaskmgr.json`，读取时做范围钳制，坏值不会把界面搞崩。

### 3. 网络层与多人共享

- **新增文件**：`net/ShareTasksC2S.java`、`net/SharedTasksS2C.java`、`EasyTaskMgr.java`
- **实现方法**：
  - 自定义包体用「JSON 字符串」承载任务列表，`StreamCodec.of` 手写 `writeUtf/readUtf`，字段扩展不用改协议；
  - `EasyTaskMgr.onInitialize()` 里 `PayloadTypeRegistry.serverboundPlay()/clientboundPlay()` 注册两个包；
  - 服务端只做中转：收到 C2S 后缓存「玩家 UUID → 最新列表」，并转发给其他在线玩家；
  - 新玩家进服时，`ServerPlayConnectionEvents.JOIN` 把已缓存的列表补发给他；
  - 玩家退出时广播一份空列表，队友 HUD 立刻清掉这个人。
  - 客户端 `client/ClientNet.java` 负责发送（内容变化才发）和接收（按 owner 分组存 `LinkedHashMap`）。
- **设计取舍**：没有做服务端持久化，也没有做权限控制——目标是「联机时互相看得见」，服务端不装数据也能用。
  真正的隐私开关在客户端设置里（关掉就不再发送/接收）。

### 4. HUD

- **新增文件**：`client/TaskHud.java`（`HudElement` 实现）
- **实现方法**：
  - `HudElementRegistry.addLast(id, new TaskHud())` 挂到 HUD 最后，保证画在血条/物品栏之上；
  - 位置用「屏幕百分比」：`hudXPercent` 是面板**右边缘**位置（99 ≈ 贴右），`hudYPercent` 是上边缘，
    换分辨率/窗口大小都不会跑偏；
  - 面板宽度按最长一行文字算，向左生长（右上角固定，符合「右上角常驻」的要求）；
  - 每条待办一行，颜色取优先级色；第二行是细节：收集/合成进度、成就 id、计时模式、坐标（距离+方向+速度）；
  - 队友的待办单独一段，显示 `名字 · 内容`；
  - 顶部还会临时显示 4.5 秒的「✓ 完成任务 / + 新增」提示（`ClientTasks.toast`）。
- **方向算法**：`atan2(-dx, dz)` 得到世界方位角，减去玩家 `getYRot()` 并归一化到 ±180°，
  结果负值在左、正值在右，再映射成「正前/右前/右侧/右后/正后/左后/左侧/左前」。
  速度取 `player.getDeltaMovement().horizontalDistance() * 20`（格/秒）。

### 5. 快捷键与输入界面

- **新增文件**：`client/Keybinds.java`、`client/ui/QuickAddScreen.java`
- **实现方法**：
  - 按键：B 快速添加、N 列表、K 设置、H 隐藏 HUD；用 `KeyMappingHelper.registerKeyMapping` 注册，
    每 tick 用 `consumeClick()` 取一次，避免连发；
  - 快速添加界面：一个 `EditBox` + 优先级按钮 + 「添加」按钮，回车直接提交，Esc 取消；
  - 回车判断用 `InputConstants.KEY_RETURN`（26.3 里 `KeyEvent.key()` 返回的就是这个键码），
    不用 LWJGL 常量，少一层依赖。

### 6. 列表 / 编辑 / 设置界面

- **新增文件**：`client/ui/TaskListScreen.java`
  - 每页 6 条，支持勾选完成、编辑、删除、翻页、显示已完成、清空已完成、新建、进设置；
  - 用 `rebuildWidgets()` 在数据变化后重建控件，避免手工维护控件与数据的一致性。
- **新增文件**：`client/ui/TaskEditScreen.java`
  - 所有字段平铺（内容、类型、目标、数量/半径、坐标、提醒方式、间隔、附加文案），
    不同类型在底部给一行说明文字，省掉动态显隐逻辑；
  - 保存时按类型校验：收集/合成必须有物品 id，成就必须有成就 id，坐标解析失败则保留原值。
- **新增文件**：`client/ui/ConfigScreen.java`
  - 位置 X/Y 用按钮 ±2% 调整，右边实时画一个预览框；
  - 背景、距离、速度、队友、音效、共享、显示条数都能就地切换，改动立即 `save()`。

### 7. 条件检测与提醒

- **新增文件**：`client/ClientTasks.java`、`client/Notifier.java`
- **实现方法**：
  - 每 tick 遍历未完成任务，按类型分派：
    - **收集**：统计背包（含副手）里目标物品总数，达到数量即完成；进度实时写回 `progress`；
    - **合成**：`ResultSlotMixin` 在玩家取走合成结果时累加 `progress`，达标完成；
    - **成就**：`ClientPacketListener.getAdvancements()` → `progress().get(holder).isDone()`；
    - **坐标**：水平距离 ≤ `radius` 即完成，HUD 持续显示方向/距离/速度；
    - **时间**：上线提醒在 `ClientPlayConnectionEvents.JOIN` 时触发一次并自动完成；
      间隔提醒每 N 分钟弹一次，不自动完成；
  - 完成时 `TaskStore.complete()` + 聊天栏提示 + 提示音（`EXPERIENCE_ORB_PICKUP`）+ HUD toast；
  - 提醒文案支持任务自定义的 `remindText`（比如「收集了 1 组钻石 → 提醒我去做钻石装备」）。

- **新增文件**：`client/mixin/ResultSlotMixin.java` + `easytaskmgr.client.mixins.json`
  - `@Inject(method = "onTake", at = @At("HEAD"))`，只在 `player.level().isClientSide()` 时统计，
    避免单机存档里客户端/服务端各记一次。

### 8. 26.3 的 API 适配（踩坑记录）

Minecraft 26.3 的 GUI 层改动很大，记录几个关键点，后面改代码不用再翻字节码：

| 事项 | 26.3 的写法 |
| --- | --- |
| 屏幕渲染 | `Screen#extractRenderState(GuiGraphicsExtractor, int, int, float)`，父类只画控件、不画背景，背景要自己 `fill` |
| 当前屏幕 | `Minecraft.gui.screen()` / `gui.setScreen(screen)`；`Minecraft.setScreenAndShow(screen)` 会强制渲染一帧 |
| 文字/矩形 | `GuiGraphicsExtractor#text(font, text, x, y, color)`、`fill`、`outline`、`centeredText` |
| 键盘类型 | `InputConstants.Type.KEYBOARD`（不再是 `KEYSYM`），键码用 `InputConstants.KEY_*` |
| 网络注册 | `PayloadTypeRegistry.serverboundPlay()/clientboundPlay()`（不再是 `playC2S/playS2C`） |
| 玩家名 | `Player#getName().getString()`（`GameProfile#getName()` 已不可用） |
| 背包遍历 | `Inventory#getNonEquipmentItems()` |
| 合成钩子 | `ResultSlot#onTake(Player, ItemStack)` |

### 9. 资源文件

- `assets/easytaskmgr/icon.png`：64×64 的清单图标（脚本生成，避免二进制资产缺失导致 mod 列表警告）；
- `assets/easytaskmgr/lang/zh_cn.json`、`en_us.json`：快捷键与分类的翻译键。

### 10. 本轮验证

- `gradlew build` 通过，产出 `build/libs/easytaskmgr-0.1.0.jar`；
- `gradlew runClient` 能进游戏，mod 正常加载（`Loading 52 mods`）；
- 起了一个本地测试服（平坦世界），客户端 `--quickPlayMultiplayer` 直连成功，
  日志出现 `[EasyTaskMgr] 上线先看农田` —— 说明「上线提醒」链路（JOIN 事件 → 任务匹配 → 聊天提示）是通的。

### 11. 已知限制 / 下一轮待办

- 多人共享目前只做了「同服玩家互相可见」，没有做持久化（服务器重启后缓存清空，玩家重新上报即恢复）；
- 合成任务只统计「取走合成结果」这一动作，工作台批量 shift 合成会按实际取走数量累加，但「漏斗自动合成」不算；
  （`ResultSlotMixin` 的注入本身已验证：`ResultSlot` 在玩家进服创建背包 2x2 合成格时就会加载，
  联机测试没有出现注入失败导致的崩溃；但「真的合成一次并看到进度 +1」还需要下一轮手动点一次工作台）
- HUD 还没做拖拽定位，只能用设置里的按钮微调百分比；
- 收集任务统计的是背包绝对数量，不统计末影箱/容器；
- 指令（`/etm add ...`）没做，纯 GUI + 快捷键；
- 尚未做单元测试，主要靠启动游戏 + 联机跑通验证。
