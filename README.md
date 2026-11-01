# EasyTaskMgr

> 在 Minecraft 里「记性不好」的解药：按一下快捷键记下要做的事，右上角一直挂着；收集够了、成就到手、走到地方、到点了，它会主动提醒你；联机时还能把任务共享给队友一起完成。

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62b47a?style=flat-square)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.20.1-dbb69c?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-e76f00?style=flat-square)](https://adoptium.net/)
[![Version](https://img.shields.io/badge/version-1.0.0--rc4-blue?style=flat-square)](https://github.com/PulasanSTS/EasyTaskMgr/releases)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

---

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
2. 把 [Fabric API](https://modrinth.com/mod/fabric-api)（26.3 版本）和 `easytaskmgr-1.0.0-rc4.jar` 一起放进 `.minecraft/mods`；
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
┌ Todos 3 ──────────────────────────────────────┐
│ 1. 去矿洞挖铁                                  │
│    120, 45, -300 · 234m · 右前 40° · 4.3 m/s   │
│ 2. 收集钻石                                    │
│    Diamond 12/64                               │
│ [Normal] Steve · 一起挖远古残骸 [claimed] 1/2 done │
│    Ancient Debris 8/64                         │
└────────────────────────────────────────────────┘
```

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

<details>
<summary>English summary</summary>

**EasyTaskMgr** is a Fabric mod for Minecraft **26.3** that keeps a todo list in-game: press `B` to add a task,
see it in the top-right HUD (drag to reposition under `K`), and get notified when a condition is met —
collect N items, finish an advancement, reach coordinates (distance / direction / speed on the HUD),
or after a delay. Items and advancements are searched by localized name, with suggestions.

Tasks can be **shared** when the server also runs the mod: choose `Personal`, `Shared` or `Special`
(invite with `/etm invite <player>`), pick one of five completion rules — *any claimant*, *all claimants*,
*any incl. creator*, *all incl. creator*, or *combined count* (the amounts of all claimants **and** the creator are summed) —
and claim a task from the list or directly from the HUD. Claimed collect and coordinate tasks complete
automatically, every publish / claim / completion is announced in chat
(`[EasyTaskMgr] …` in aqua + green, localized per client), and shared tasks are saved to
`<world>/data/easytaskmgr_share.json` so they survive restarts.

Requires Java 25, Fabric Loader ≥ 0.20.1 and Fabric API for 26.3. Build with `./gradlew build`.

</details>
