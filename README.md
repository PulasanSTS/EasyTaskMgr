# EasyTaskMgr

<details>
<summary>English</summary>

> The cure for a bad memory in Minecraft: hit a hotkey to jot down what you need to do, keep it pinned in the top-right corner, and get a proactive reminder once you've collected enough, unlocked an advancement, reached a location, or hit the right time. In multiplayer you can even share tasks with teammates and finish them together.

> This mod was developed by Deepseek-V4.1-Flash. Some features may still be untested — please report any bugs you run into.

## What problem does it solve

When you play a modpack, a technical/survival world, or multiplayer, you've probably run into this:

- "I'll go mine some diamonds in a bit" — and then you forget;
- "Remember to put the diamonds in the chest" — you've got the diamonds, but you're already miles away;
- "Remind me to check the farm in half an hour" — there's no such reminder in the game;
- In multiplayer, a teammate asks "keep an eye out for my elytra" — both of you keep your own note, and in the end nobody remembers.

EasyTaskMgr turns all of these into an in-game to-do list that **proactively reminds you when a condition is met**, and lets teammates claim and complete the same task together.

## Features

### 1. Basic todos: quick capture + always-on HUD

- Press `B` to open an input box, type a description, pick a priority, and hit Enter to add it;
- The top-right HUD keeps your current todos on screen at all times; long text wraps automatically, and both the width and the number of visible rows are configurable;
- **You can drag the HUD into place**: open settings with `K`, then hold and drag the preview box on the right to wherever you want;
- Four priority levels — Low / Normal / High / Urgent — distinguished by color, with higher priorities sorted to the top;
- Press `V` to enter HUD interaction mode: release the mouse and **click a task to mark it complete** (shared tasks can be clicked too — see below).

### 2. Advanced todos: condition-triggered reminders

| Type | Completion condition | Example |
| --- | --- | --- |
| Collect items | The item reaches a set quantity in your inventory (including the offhand and shulker box contents) | Collect 64 diamonds |
| Advancement | A specified advancement is achieved | Complete "Into Fire" |
| Go to coordinates | You come within a radius of the target point | Go to `120 45 -300`, radius 8 |
| Timed reminder | Remind once on login / every login / every N minutes / a fixed in-game time | Remind me after I join / every 30 minutes / every day at 06:00 |

Details:

- **Type by name**: items / advancements accept localized names (Chinese names work too), and the input box shows live suggestions below it (name + id); scroll to page through them and click to fill one in;
- **The HUD shows what you need**: collect tasks show `Diamond 0/64`, and advancement tasks show the advancement's display name rather than its registry name;
- Coordinate tasks show a **direction arrow, target coordinates, remaining distance, relative direction, and current speed** on the HUD;
- A task can carry an extra reminder line, shown on its **own line** when it completes: `Note for "XXX": YYY`;
- On completion / when the time is reached, you get a chat message + a sound, and the top of the HUD flashes;
- In-game time is judged against the world clock; for modpacks with a custom day length, set `ticksPerDay` to the actual value.

### 3. Multiplayer sharing (requires the mod on the server)

- Every task has three **visibility** settings: `Personal` / `Shared` / `Special`;
  - **Shared**: visible and claimable by all players, shown in blue on the HUD;
  - **Special**: visible only to the creator and the invited players; invite with `/etm invite <playername>` (case-insensitive);
- **Claim / complete**: click the circle on a row in the todo list, or click that row in HUD interaction mode — if it isn't claimed yet, the click claims it; if it's already claimed, click again to report completion;
- **Five completion rules** (five for collect tasks, four for the other types):

  | Completion rule | Participants | When the whole task is complete |
  | --- | --- | --- |
  | Any claimant | Claimants | Any one claimant completes it |
  | All claimants | Claimants | Every claimant completes it |
  | Any (incl. creator) | Claimants + creator | Any one participant completes it |
  | All (incl. creator) | Claimants + creator | The creator and every claimant complete it |
  | Combined count (collect only) | Claimants + creator | The highest quantity each participant has seen in their inventory is summed to reach the target (the creator counts too) |

- **Automatic detection**: claimed collect tasks count your inventory every second, and claimed coordinate tasks compute the distance — both report completion on their own once the target is met;
  in `Combined count` mode the client reports the quantity it sees every second, and the server accumulates the historical maximum per player;
- **Chat messages**: publish / claim / complete / partial completion are broadcast to the players who can **see that task**,
  in a unified format `[EasyTaskMgr] xxx shared a task: …` (cyan prefix + green body, localized to each client's language);
- **Server-side persistence**: shared tasks are saved in the world save, so they survive a server restart;
- A creator can `×` withdraw their own shared task from the list;
- **Advancement and timed-reminder tasks cannot be shared**: advancement progress can't be auto-detected, and a shared reminder would neither fire for the claimants nor be safe from abuse.

### 4. Language

- By default it **follows the Minecraft language**: only Simplified Chinese uses Chinese; every other language falls back to English;
- You can also force "Auto / Simplified Chinese / English" in the settings screen (`K`), effective immediately;
- The UI, chat messages, server broadcasts, advancement conditions, and direction words are all bilingual;
- Server broadcasts are sent as Minecraft translation components, so Chinese and English players on the same server each see their own language.

## Installation

1. Install **Fabric Loader** (≥ 0.20.1) for **Minecraft 26.3**;
2. Put [Fabric API](https://modrinth.com/mod/fabric-api) (the 26.3 build) and `easytaskmgr-1.0.1.jar` together into `.minecraft/mods`;
3. Launch the game — if you see "Todos" in the top-right corner, the installation worked.

> **Multiplayer servers**: installing the mod on the client alone gives you basic todos and reminders; to enable shared tasks, **the server must also have** this mod
> (the advancement suggestion list also depends on the server). Use the **same version** on the server and the client: the client warns you in chat if it detects an older server version.

## Usage

| Hotkey | Function |
| --- | --- |
| `B` | Quick-add a todo (description + priority) |
| `N` | Open the todo list (check to complete / edit / delete / new / scroll) |
| `K` | Open settings (drag the HUD position, display options, language, sound) |
| `H` | Temporarily hide / show the HUD |
| `V` | HUD interaction mode: release the mouse, click a task to complete it, press `V` again to exit |

Hotkeys can be rebound under "Options → Controls → Key Binds".

### Creating an advanced todo

1. Press `N` to open the list → click "New";
2. The "Type" button cycles through: Normal / Collect items / Advancement / Go to coordinates / Timed reminder — **only the input fields the current type needs are shown**;
3. Fill in by type:
   - Collect: type an item name (e.g. "Diamond"), pick it from the suggestions, then enter a quantity;
   - Advancement: type an advancement name (e.g. "Into Fire"), pick it from the suggestions;
   - Coordinate: enter `x / y / z` and an arrival radius;
   - Time: choose "Once on login / Every login / Every N minutes / In-game time"; in-game time can use the preset buttons or HH:MM + every N days;
4. To share it with others, use the "Visibility" button to pick `Shared` or `Special`, then the "Completion" button to choose how completion is judged;
5. Enter an "Extra note" — the line you want to see when it completes (may be left blank);
6. Save.

### HUD example

```
Todos 3
1. Go mine iron in the mines
   120, 45, -300 · 234m · front-right 40° · 4.3 m/s
2. Collect diamonds
   Diamond 12/64
[Normal] Steve · Ancient debris run [claimed] 1/2 done
   Ancient Debris 8/64
```

### Importing / exporting tasks (CSV)

- Press `N` to open the task list; the right end of the bottom row has **Export CSV** and **Import CSV**, using the fixed file `.minecraft/config/easytaskmgr-tasks.csv`;
- The exported header is `text, type, target, count, x, y, z, radius, timerMode, intervalMinutes, gameHour, gameMinute, gameDayInterval, priority, done, remindText, visibility, shareMode`;
- You can edit the table in Excel or a text editor and click "Import CSV" — the rows are **appended** as tasks (each gets a fresh id, so importing the same file twice does not overwrite anything);
- Fields containing commas, quotes or line breaks are handled correctly; rows with empty text or a missing `target` for collect / advancement tasks are skipped, and the result message tells you how many were ignored;
- Two extra columns at the end drive **shared tasks**: `visibility` (`0` personal / `1` shared / `2` special) and `shareMode` (`0` any claimant / `1` all claimants / `2` any incl. creator / `3` all incl. creator / `4` combined count);
  rows with `visibility > 0` are published to the server instead of being saved locally (the server must run the same mod version);
  advancement / timed-reminder rows are downgraded to local tasks and counted in the result message;
- When one import publishes more than 3 shared tasks, the chat only gets a single summary line
  (`xxx shared A、B、C … (N tasks in total)`) instead of one line per task.

## Configuration & data

| File | Contents |
| --- | --- |
| `.minecraft/config/easytaskmgr.json` | Client config (HUD, display options, language, sound) |
| `.minecraft/config/easytaskmgr-tasks.json` | Personal todo list (incl. progress; can be backed up / migrated directly) |
| `<world>/data/easytaskmgr_share.json` | Server-side shared tasks (auto-saved, format version 2, old files migrated automatically) |

Main fields of `easytaskmgr.json`:

| Field | Description |
| --- | --- |
| `hudXPercent` / `hudYPercent` | Position of the HUD's right edge / top edge (as a percentage) |
| `hudMaxWidth` | Max HUD width (pixels); long text wraps to this width |
| `hudCompactLimit` | Max number of rows to show on the HUD |
| `hudBackground` / `hudHidden` | Whether to draw a translucent background / whether the HUD is currently hidden |
| `showTeammates` | Whether to show shared tasks on the HUD |
| `showDistance` / `showSpeed` / `showDirectionArrow` | Display options for coordinate tasks |
| `hudInteractive` | Whether the HUD is in interaction mode (toggled with `V`) |
| `ticksPerDay` | How many ticks make one in-game day (default 24000; adjust if a modpack changed the day/night cycle) |
| `language` | `auto` (follow the game) / `zh_cn` / `en_us` |
| `soundEnabled` | Whether to play a sound on completion / reminder |

> `shareEnabled` (the old global sharing toggle) has been removed; sharing is now controlled by each task's visibility.
> A leftover `shareEnabled` field in the config file is ignored.

## Building from source

Requires **JDK 25** (Minecraft 26.3's class file version is 69).

```bash
git clone https://github.com/PulasanSTS/EasyTaskMgr.git
cd EasyTaskMgr

# Build
./gradlew build          # Windows: gradlew.bat build

# Launch the client / server with the mod
./gradlew runClient
./gradlew runServer
```

The artifact is at `build/libs/easytaskmgr-<version>.jar`.

> Note for users in mainland China: `settings.gradle` / `build.gradle` default to the Fabric Maven mirror (`repository.hanbings.io`),
> and the Gradle distribution uses a Tencent Cloud mirror. With a stable direct connection you can switch back to the official addresses.

## Project structure

```
src/main/java/com/easytaskmgr/
├── EasyTaskMgr.java            Common entry: network packet registration, shared-task handling, advancement-catalog relay
├── share/
│   ├── SharedTasks.java        Server-side shared-task table: visibility, claiming, five completion rules, chat messages
│   └── ShareStore.java         Shared-task persistence (world/data/easytaskmgr_share.json, format version 2)
├── task/                       Task model, priority, type, timer modes, JSON repository
├── config/ModConfig.java       Client config
├── i18n/I18n.java              Built-in Chinese/English strings
├── advancement/                Advancement condition describers (server-side parsing)
└── net/                        ShareActionC2S / ShareProgressC2S / ShareSnapshotS2C / advancement query

src/client/java/com/easytaskmgr/client/
├── EasyTaskMgrClient.java      Client entry: HUD, keybinds, tick, auto-detection, commands
├── TaskHud.java                Top-right HUD (shared-task rows, priority prefix, click-to-complete)
├── ClientTasks.java            Condition detection, completion checks, item-name / advancement-name resolution
├── ClientShared.java           Shared-task cache, claim/complete, quantity reporting, auto-detection
├── ClientNet.java              Compatibility shim (the old shared-sync interface)
├── Keybinds.java               Keybinds
├── Notifier.java               Chat messages + sound
├── Suggestions.java            Item / advancement name search
├── AdvancementCatalog.java     Advancement catalog (server-delivered + localized)
├── AdvancementInfo.java        Advancement prerequisites and a summary of how they're earned
├── Language.java / LangResources.java  Language following and language-file loading
├── TextWrap.java               Pixel-width-based text wrapping
├── mixin/MouseHandlerMixin.java Mouse handling in HUD interaction mode
└── ui/                         The Quick-add / List / Edit / Settings screens
```

## Compatibility & known limitations

- Supports **Minecraft 26.3 + Fabric** only; no Forge / NeoForge version has been made;
- Collect tasks count the **inventory (incl. offhand) and the contents of shulker boxes inside the inventory**, but not containers you have merely opened (that feature was removed in 1.0.0-rc1; the code is kept on a branch);
- Advancement and timed-reminder tasks cannot be shared (the editor shows why);
- The advancement catalog depends on the mod being on the server: connecting from a pure client to a vanilla server shows only the advancements already synced locally;
- The advancement-prerequisites UI is disabled (the parsing code is kept on the `feature/advancement-ui` branch);
- Shared snapshots are still broadcast in full, which costs more bandwidth with many players; `Combined count` quantities are reported once per second, so progress may lag on a poor connection;
- Changing a shared task's completion rule clears its recorded progress and quantities;
- Shared tasks are stored only on the server; "delete" on the client only removes a local copy and does not withdraw the task.

## Roadmap

- [ ] Command interface: `/etm add <text>`, `/etm list`
- [ ] Withdrawal notifications for shared tasks
- [ ] A "count from zero" mode for collect tasks
- [ ] Expand advancement prerequisites into a full condition tree
- [ ] Better multiplayer collaboration

## Changelog

- Changes for each version are listed in [Releases](https://github.com/PulasanSTS/EasyTaskMgr/releases).

## License

[MIT](LICENSE) — free to use, modify, and distribute, as long as the copyright and license notices are retained.

</details>

> 在 Minecraft 里「记性不好」的解药：按一下快捷键记下要做的事，右上角一直挂着；收集够了、成就到手、走到地方、到点了，它会主动提醒你；联机时还能把任务共享给队友一起完成。

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62b47a?style=flat-square)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.20.1-dbb69c?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-e76f00?style=flat-square)](https://adoptium.net/)
[![Version](https://img.shields.io/badge/version-1.0.1-blue?style=flat-square)](https://github.com/PulasanSTS/EasyTaskMgr/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

---
>此模组由Deepseek-V4.1-Flash开发，可能会有一些未被我测试到的bug，如有遇到请多多反馈

## 它解决什么问题

玩整合包 / 生电 / 联机时，经常出现这种情况：

- 「等会儿要去挖点钻石」——然后就忘了；
- 「记得把钻石放进箱子」——钻石挖到了，人已经跑远了；
- 「半小时后提醒我看一眼农场」——游戏里根本没有这种提醒；
- 联机时队友说「帮我留意下鞘翅」——两个人各记各的，最后谁也没记住。

EasyTaskMgr 把这些事变成游戏内的待办清单，**在条件满足时主动提醒你**，并且能让队友认领、共同完成同一条任务。

## 功能

### 1. 普通待办：快速记录 + HUD 常驻

- 按 `B` 弹出输入框，输入内容、选优先级，回车即添加；
- 右上角 HUD 常驻显示当前待办，长文字自动换行，宽度和显示条数都能调；
- **HUD 位置可以直接拖**：`K` 打开设置，按住右侧的预览框拖到想要的位置；
- 优先级四档：低 / 普通 / 高 / 紧急，用颜色区分，高优先级排在前面；
- `V` 打开 HUD 交互模式：鼠标释放，**点一下任务即可完成**（共享任务也能点，见下）。

### 2. 高级待办：条件触发提醒

| 类型 | 完成条件 | 例子 |
| --- | --- | --- |
| 收集物品 | 背包内该物品达到指定数量（含副手与潜影盒内容） | 收集 64 个钻石 |
| 完成成就 | 指定成就达成 | 完成「与火共舞」 |
| 前往坐标 | 走到目标点半径内 | 前往 `120 45 -300`，半径 8 |
| 时间提醒 | 上线提醒一次 / 每次上线 / 每隔 N 分钟 / 游戏内固定时间 | 上线后提醒我 / 每 30 分钟 / 每天 06:00 |

细节：

- **按名字输入**：物品 / 成就支持中文名，输入框下方实时给出候选（名字 + id），滚轮翻页、点一下即填；
- **HUD 显示要什么**：收集任务显示 `Diamond 0/64`，成就任务显示成就名而不是注册名；
- 坐标任务在 HUD 上显示**方向箭头、目标坐标、剩余距离、相对方向、当前速度**；
- 任务可以附加一句提醒文案，完成时会**单独一行**提示：`完成「XXX」任务的附加提醒：YYY`；
- 完成 / 到点时有聊天栏提示 + 提示音，HUD 顶部也会闪一下；
- 游戏内时间按世界时钟判断；改过昼夜长度的整合包把 `ticksPerDay` 调成实际值即可。

### 3. 多人共享（服务端也装模组时）

- 每条任务有三档**可见性**：`仅个人` / `共享` / `特殊共享`；
  - **共享**：所有玩家可见、可认领，HUD 上用蓝色显示；
  - **特殊共享**：只有发布者和被邀请者可见，用 `/etm invite <玩家名>` 邀请（不区分大小写）；
- **认领 / 完成**：在待办列表点该行圆圈，或在 HUD 交互模式里点这一行——未认领先认领，已认领再点上报完成；
- **完成条件五档**（收集任务五档，其它类型四档）：

  | 完成条件 | 参与者 | 什么时候整条完成 |
  | --- | --- | --- |
  | 任一认领者 | 认领者 | 任意一个认领者完成 |
  | 所有认领者 | 认领者 | 所有认领者都完成 |
  | 任一（含发布者） | 认领者 + 发布者 | 任意一人完成 |
  | 所有（含发布者） | 认领者 + 发布者 | 发布者和所有认领者都完成 |
  | 共同完成（仅收集） | 认领者 + 发布者 | 各参与者背包里见过的最大数量相加达到目标（发布者也计入） |

- **自动判定**：已认领的收集任务每秒数一遍背包、坐标任务算距离，达标自动上报；
  「共同完成」模式下客户端每秒上报自己看到的数量，由服务端累加；
- **聊天提示**：发布 / 认领 / 完成 / 阶段性完成都会广播给「能看见这条任务」的玩家，
  格式统一为 `[EasyTaskMgr] xxx shared a task: …`（青色前缀 + 绿色正文，随客户端语言本地化）；
- **服务端存档**：共享任务保存在世界存档里，重启服务器不会丢失；
- 发布者可以在列表里 `×` 撤回自己的共享任务；
- **成就任务与时间提醒任务不支持共享**：成就进度无法自动判定，共享的时间提醒既不会提醒认领者也有被滥用的风险。

### 4. 语言

- 默认**跟随 Minecraft 语言**：只有简体中文使用中文，其他语言一律英文；
- 也可以在设置界面（`K`）里强制选择「自动 / 简体中文 / English」，改完立即生效；
- 界面、聊天提示、服务端广播、成就条件、方向词都是双语的；
- 服务端广播用 Minecraft 翻译组件下发，中英文玩家在同一个服务器里各看各的语言。

## 安装

1. 安装 **Minecraft 26.3** 对应的 **Fabric Loader**（≥ 0.20.1）；
2. 把 [Fabric API](https://modrinth.com/mod/fabric-api)（26.3 版本）和 `easytaskmgr-1.0.1.jar` 一起放进 `.minecraft/mods`；
3. 启动游戏，右上角出现「待办清单」即安装成功。

> **多人服务器**：客户端装即可用普通待办与提醒功能；要让共享任务可用，**服务端也要装**本模组
> （成就候选列表同样依赖服务端）。服务端与客户端请使用**同一版本**：客户端发现服务端是旧版时会在聊天栏提示。

## 使用

| 快捷键 | 功能 |
| --- | --- |
| `B` | 快速添加待办（内容 + 优先级） |
| `N` | 打开待办列表（勾选完成 / 编辑 / 删除 / 新建 / 滚轮滚动） |
| `K` | 打开设置（HUD 位置拖拽、显示项、语言、音效） |
| `H` | 临时隐藏 / 显示 HUD |
| `V` | HUD 交互模式：鼠标释放，点击任务即可完成，再按 `V` 退出 |

快捷键可以在「选项 → 控制 → 按键绑定」里改。

### 新建一条高级待办

1. `N` 打开列表 → 点「新增」；
2. 「类型」按钮循环切换：普通 / 收集 / 成就 / 坐标 / 时间，**只有当前类型需要的输入框才会出现**；
3. 按类型填写：
   - 收集：输入物品名（如「钻石」），从候选里选，再填数量；
   - 成就：输入成就名（如「与火共舞」），从候选里选；
   - 坐标：填 `x / y / z` 和到达半径；
   - 时间：选「上线一次 / 每次上线 / 每隔 N 分钟 / 游戏内时间」，游戏内时间可用预设按钮或填 HH:MM + 每 N 天；
4. 要共享给别人时，用「可见性」按钮选 `共享` 或 `特殊共享`，再用「完成条件」按钮选判定方式；
5. 「附加提醒」写一句完成时想看到的话（可留空）；
6. 保存。

### HUD 示例

```
待办清单 3
1. 去矿洞挖铁
   120, 45, -300 · 234m · 右前 40° · 4.3 m/s
2. 收集钻石
   Diamond 12/64
[普通] Steve · 一起挖远古残骸 [已认领] 1/2 已完成
   远古残骸 8/64
```

### 任务导入 / 导出（CSV）

- 按 `N` 打开待办列表，底排最右侧是 **导出 CSV** / **导入 CSV** 两个按钮，文件固定是 `.minecraft/config/easytaskmgr-tasks.csv`；
- 导出的表头：`text, type, target, count, x, y, z, radius, timerMode, intervalMinutes, gameHour, gameMinute, gameDayInterval, priority, done, remindText, visibility, shareMode`；
- 表格可以用 Excel / 记事本直接改，改完点「导入 CSV」就会把行**追加**成任务（每条都会分配新 id，重复导入不会互相覆盖）；
- 字段里带逗号、引号、换行也能正确解析；收集 / 成就任务缺 `target` 或文本为空的行会被跳过，并在提示里告诉你有几行没导入；
- 末尾两列决定**共享任务**：`visibility`（`0` 仅个人 / `1` 共享 / `2` 特殊共享）、`shareMode`（`0` 任一认领者 / `1` 所有认领者 / `2` 任一含发布者 / `3` 所有含发布者 / `4` 共同完成）；
  `visibility > 0` 的行会直接发布到服务端而不是存本地（需要服务端装同一版本模组），
  成就 / 时间提醒类型的行会降级为本地任务并在提示里计数；
- 一次导入发布的共享任务超过 3 条时，聊天栏只发一条汇总（`xxx 创建了 A、B、C 等 N 条共享任务`），不会刷屏。

## 配置与数据

| 文件 | 内容 |
| --- | --- |
| `.minecraft/config/easytaskmgr.json` | 客户端配置（HUD、显示项、语言、音效） |
| `.minecraft/config/easytaskmgr-tasks.json` | 个人任务列表（含进度，可直接备份 / 迁移） |
| `<存档>/data/easytaskmgr_share.json` | 服务端的共享任务（自动保存，版本 2，旧档自动迁移） |

`easytaskmgr.json` 主要字段：

| 字段 | 说明 |
| --- | --- |
| `hudXPercent` / `hudYPercent` | HUD 右边缘 / 上边缘位置（百分比） |
| `hudMaxWidth` | HUD 最大宽度（像素），长文本按这个宽度换行 |
| `hudCompactLimit` | HUD 最多显示几条 |
| `hudBackground` / `hudHidden` | 是否画半透明背景 / 当前是否隐藏 |
| `showTeammates` | 是否在 HUD 显示共享任务 |
| `showDistance` / `showSpeed` / `showDirectionArrow` | 坐标任务的显示项 |
| `hudInteractive` | 是否处于 HUD 交互模式（`V` 切换） |
| `ticksPerDay` | 一个游戏日多少 tick（默认 24000，整合包改过昼夜周期时调整） |
| `language` | `auto`（跟随游戏）/ `zh_cn` / `en_us` |
| `soundEnabled` | 完成 / 提醒时是否播放音效 |

> 旧版本里的 `shareEnabled`（共享总开关）已删除，共享现在由每条任务的可见性控制；
> 配置文件里残留该字段会被忽略。

## 从源码构建

需要 **JDK 25**（Minecraft 26.3 的 class 文件版本是 69）。

```bash
git clone https://github.com/PulasanSTS/EasyTaskMgr.git
cd EasyTaskMgr

# 构建
./gradlew build          # Windows: gradlew.bat build

# 启动带模组的客户端 / 服务端
./gradlew runClient
./gradlew runServer
```

产物在 `build/libs/easytaskmgr-<version>.jar`。

> 国内网络提示：`settings.gradle` / `build.gradle` 默认走 Fabric Maven 镜像（`repository.hanbings.io`），
> Gradle 发行包走腾讯云镜像。网络直连正常时可以换回官方地址。

## 项目结构

```
src/main/java/com/easytaskmgr/
├── EasyTaskMgr.java            公共入口：网络包注册、共享任务处理、成就目录中转
├── share/
│   ├── SharedTasks.java        服务端共享任务表：可见性、认领、五档完成条件、聊天提示
│   └── ShareStore.java         共享任务存档（world/data/easytaskmgr_share.json，版本 2）
├── task/                       任务模型、优先级、类型、定时模式、JSON 仓库
├── config/ModConfig.java       客户端配置
├── i18n/I18n.java              内置中英文案
├── advancement/                成就条件描述器（服务端解析用）
└── net/                        ShareActionC2S / ShareProgressC2S / ShareSnapshotS2C / 成就查询

src/client/java/com/easytaskmgr/client/
├── EasyTaskMgrClient.java      客户端入口：HUD、按键、tick、自动判定、命令
├── TaskHud.java                右上角 HUD（含共享任务行、优先级前缀、点击完成）
├── ClientTasks.java            条件检测、完成判定、物品名 / 成就名解析
├── ClientShared.java           共享任务缓存、认领 / 完成、数量上报、自动判定
├── ClientNet.java              兼容壳（旧的共享同步接口）
├── Keybinds.java               快捷键
├── Notifier.java               聊天提示 + 音效
├── Suggestions.java            物品 / 成就的中文名搜索
├── AdvancementCatalog.java     成就目录（服务端下发 + 本地化）
├── AdvancementInfo.java        成就前置与达成方式摘要
├── Language.java / LangResources.java  语言跟随与语言文件读取
├── TextWrap.java               按像素宽度换行
├── mixin/MouseHandlerMixin.java HUD 交互模式下的鼠标处理
└── ui/                         快速添加 / 列表 / 编辑 / 设置 四个界面
```

## 兼容性与已知限制

- 仅支持 **Minecraft 26.3 + Fabric**，未做 Forge / NeoForge 版本；
- 收集任务统计**背包（含副手）与背包里潜影盒的内容**，不再统计打开过的容器（该功能在 1.0.0-rc1 移除，代码保留在分支）；
- 成就任务与时间提醒任务不支持共享（编辑器里会提示原因）；
- 成就目录依赖服务端装模组：纯客户端连原版服务器时只能看到本地已同步的成就；
- 成就前置条件 UI 已关闭（解析代码保留在 `feature/advancement-ui` 分支）；
- 共享快照仍是全量广播，人数很多时流量偏大；共同完成的数量每秒上报一次，网络差时进度会稍慢；
- 切换共享任务的完成条件会清空已记录的进度与数量；
- 共享任务只在服务端保存，客户端「删除」只删本地副本，不会撤回。

## Roadmap

- [ ] 指令接口：`/etm add <text>`、`/etm list`
- [ ] 共享任务的撤回通知
- [ ] 收集任务支持「从零开始计数」模式
- [ ] 成就前置条件展开成完整条件树

## 版本记录

- 各版本的改动见 [Releases](https://github.com/PulasanSTS/EasyTaskMgr/releases)；
- 每轮改动的实现思路与踩坑记录保存在开发者的 `docs/DEVLOG.md`（不随仓库发布）。

## 许可证

[MIT](LICENSE) — 可自由使用、修改、分发，保留版权与许可声明即可。

---
