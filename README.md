# EasyTaskMgr

> 在 Minecraft 里「记性不好」的解药：按一下快捷键记下要做的事，右上角一直挂着；收集够了、成就到手、走到地方、到点了，它会主动提醒你。

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62b47a?style=flat-square)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.20.1-dbb69c?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-e76f00?style=flat-square)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-CC0--1.0-lightgrey?style=flat-square)](LICENSE)

---

## 它解决什么问题

玩整合包 / 生电 / 联机时，经常出现这种情况：

- 「等会儿要去挖点钻石」——然后就忘了；
- 「记得做一把钻石镐」——钻石挖到了，人已经跑远了；
- 「半小时后提醒我看一眼农场」——游戏里根本没有这种提醒；
- 联机时队友说「帮我留意下鞘翅」——两个人各记各的，最后谁也没记住。

EasyTaskMgr 把这些事变成游戏内的待办清单，并且**能在条件满足时主动提醒你**，而不是靠脑子记。

## 功能

### 1. 普通待办：快速记录 + HUD 常驻

- 按 `B` 弹出输入框，输入内容、选优先级，回车即添加；
- 右上角 HUD 常驻显示当前待办，一眼就能瞟到；
- **HUD 位置可以直接拖**：按 `K` 打开设置，按住右上角的预览框拖到想要的位置；
- 长文字自动换行，不会截断成省略号；面板宽度、显示条数都能调；
- 优先级分四档：低 / 普通 / 高 / 紧急，用颜色区分，排序时高优先级在前。

### 2. 高级待办：条件触发提醒

给任务设置完成条件，EasyTaskMgr 会自动盯着：

| 类型 | 完成条件 | 例子 |
| --- | --- | --- |
| 收集物品 | 背包内该物品达到指定数量（可含容器） | 收集 64 个钻石 |
| 合成物品 | 从创建任务起，合成该物品累计达到数量 | 合成 1 把钻石镐 |
| 完成成就 | 指定成就达成 | 完成「来硬的」 |
| 前往坐标 | 走到目标点半径内 | 前往 `120 45 -300` |
| 时间提醒 | 上线时提醒一次，或每隔 N 分钟提醒 | 上线后提醒我 / 每 30 分钟提醒一次 |

细节：

- **按名字输入**：物品 / 成就都支持中文名，输入框下面会实时给出候选（名字 + id），**滚轮翻页、点一下就填好**，不需要背 id；
- **容器计数**（收集任务可开关）：除了背包，还会统计你**打开过的箱子 / 末影箱**，以及**背包里潜影盒内部**的物品；
- **成就前置**（暂时关闭）：解析代码保留，把配置 `advancementRequirements` 改成 `true` 即可恢复；
- **中文优先**：成就名、条件里的物品名由客户端按自己的语言解析，英文服务端也不会变成 Diamond；
- 收集 / 合成类在 HUD 上实时显示 `32/64` 进度；
- 坐标类在 HUD 上显示**方向箭头、目标坐标、剩余距离、相对方向、当前速度**；
- 任务可以附加一句「提醒文案」，完成时会**单独一行**提示：`完成「XXX」任务的附加提醒：YYY`；
- 定时提醒统一显示为 `任务提醒：<内容>`；
- 完成 / 到点时有聊天栏提示 + 提示音，HUD 顶部也会闪一下。

### 3. 多人：待办共享

- 联机时把勾了「共享」的待办同步给同服队友，队友 HUD 上会多一段「队友」；
- 服务端只做轻量中转（缓存 + 转发），不需要装数据库或权限插件；
- 设置里可以一键关掉共享（关掉后既不发也不收）。

## 安装

1. 安装 **Minecraft 26.3** 对应的 **Fabric Loader**（≥ 0.20.1）；
2. 把 [Fabric API](https://modrinth.com/mod/fabric-api)（26.3 版本）和 `easytaskmgr-x.y.z.jar` 一起放进 `.minecraft/mods`；
3. 启动游戏，右上角出现「待办清单」即安装成功。

> 多人服务器：客户端装即可用大部分功能；**服务端也装**时，成就候选列表和队友待办共享才完整
> （26.3 的服务端默认只把「有进度」的成就同步给客户端）。

## 使用

| 快捷键 | 功能 |
| --- | --- |
| `B` | 快速添加待办（内容 + 优先级） |
| `N` | 打开待办列表（勾选完成 / 编辑 / 删除 / 新建 / 翻页） |
| `K` | 打开设置（HUD 位置拖拽、显示项、音效、共享） |
| `H` | 临时隐藏 / 显示 HUD |
| `V` | HUD 交互模式：鼠标释放，**点击普通任务即可完成**，再按 `V` 退出 |

快捷键可以在「选项 → 控制 → 按键绑定」里改。

### 新建一条高级待办

1. `N` 打开列表 → 点「新增」；
2. 「类型」按钮循环切换：普通 / 收集 / 合成 / 成就 / 坐标 / 时间，**只有当前类型需要的输入框才会出现**；
3. 按类型填写：
   - 收集、合成：输入物品名（如「钻石」），从候选里选；
   - 成就：输入成就名（如「来硬的」），从候选里选；
   - 坐标：填 `x / y / z` 和到达半径；
   - 时间：选「上线后提醒」或「每隔一段时间提醒」，填间隔分钟；
4. 「附加提醒」写一句完成时想看到的话（可留空）；
5. 保存。

### HUD 预览

```
┌ 待办清单 3 ────────────────────────────────┐
│ ✓ 完成：收集一组钻石（64/64）                │
│ 1. 去矿洞挖铁                               │
│    120, 45, -300 · 234m · 右前 40° · 4.3 m/s │
│ 2. 做一把钻石镐                             │
│    0/1                                      │
│ 3. 上线先看农田                             │
│    上线后提醒                               │
└ 队友 ──────────────────────────────────────┘
  Steve · 收集鞘翅 3/5
```

## 配置

配置和任务都存在 `.minecraft/config/` 下：

| 文件 | 内容 |
| --- | --- |
| `easytaskmgr.json` | HUD 位置 / 宽度 / 显示项 / 音效 / 共享开关 |
| `easytaskmgr-tasks.json` | 任务列表（含进度，可直接备份、迁移） |

`easytaskmgr.json` 主要字段：

```json
{
  "hudXPercent": 99,
  "hudYPercent": 2,
  "hudMaxWidth": 200,
  "hudBackground": true,
  "hudCompactLimit": 8,
  "showTeammates": true,
  "showDistance": true,
  "showSpeed": true,
  "soundEnabled": true,
  "shareEnabled": true
}
```

| 字段 | 说明 |
| --- | --- |
| `hudXPercent` / `hudYPercent` | HUD 右边缘 / 上边缘位置（百分比） |
| `hudMaxWidth` | HUD 最大宽度（像素），长文本按这个宽度换行 |
| `hudCompactLimit` | HUD 最多显示几条 |
| `shareEnabled` | 多人共享总开关 |

## 从源码构建

需要 **JDK 25**（Minecraft 26.3 的 class 版本是 69）。

```bash
git clone https://github.com/<your-name>/easytaskmgr.git
cd easytaskmgr

# 构建
./gradlew build          # Windows: gradlew.bat build

# 启动带模组的客户端
./gradlew runClient

# 启动带模组的服务端
./gradlew runServer
```

产物在 `build/libs/easytaskmgr-<version>.jar`。

> 国内网络提示：仓库里的 `settings.gradle` / `build.gradle` 默认走 Fabric Maven 的镜像
> （`repository.hanbings.io`），Gradle 发行包走腾讯云镜像。网络直连正常时可以换回官方地址。

## 项目结构

```
src/main/java/com/easytaskmgr/
├── EasyTaskMgr.java           公共入口 + 服务端共享 / 合成事件中转
├── task/                      任务模型、优先级、类型、仓库（JSON 持久化）
├── config/ModConfig.java      客户端配置
├── mixin/                     服务端合成钩子（ResultSlotServerMixin）
└── net/                       自定义包：共享上报 / 转发 / 合成事件

src/client/java/com/easytaskmgr/client/
├── EasyTaskMgrClient.java     客户端入口：HUD、按键、tick、联机接收
├── Keybinds.java              快捷键
├── ClientTasks.java           条件检测、容器统计、完成判定
├── ClientNet.java             共享同步 + 合成事件接收
├── Suggestions.java           物品 / 成就的中文名搜索
├── AdvancementInfo.java       成就前置与达成方式摘要
├── TextWrap.java              按像素宽度换行
├── Notifier.java              聊天提示 + 音效
├── TaskHud.java               右上角 HUD
├── mixin/ResultSlotMixin.java 客户端合成兜底钩子
└── ui/                        快速添加 / 列表 / 编辑 / 设置 四个界面
```

## 兼容性与已知限制

- 仅支持 **Minecraft 26.3 + Fabric**，未做 Forge / NeoForge 版本；
- 收集任务默认只算背包（含副手）；勾选「容器计数」后会加上**打开过的容器**和**背包里潜影盒**里的数量，
  容器部分按「见过的最大值」累计，不区分箱子；
- 合成任务统计「取走合成结果」，由服务端权威计数，普通合成和 shift 合成都会记录；
- 成就目录依赖服务端装模组：纯客户端连原版服务器时，只能看到本地已同步的成就；
- 成就前置条件 UI 暂时关闭（配置项 `advancementRequirements`），解析代码与协议都保留；
- 成就目录在进服时拉取一次，之后新增的数据包成就要重连才出现；
- 条件解析覆盖常见触发器（收集 / 吃 / 进入方块 / 合成 / 交易 / 附魔 / 击杀 / 换维度等），未知触发器显示类型名；
- 多人共享是「在线可见」，不落库；服务器重启后客户端会自动重新上报。

## Roadmap

- [ ] 每个任务的重复规则（每天 / 每次上线）
- [ ] 指令接口：`/etm add <text>`、`/etm list`
- [ ] 共享任务的「认领 / 完成」回执，队友那边同步划掉
- [ ] 收集任务支持「从零开始计数」模式
- [ ] 成就前置条件展开成完整条件树

## 发布到 GitHub

从建仓库、上传源码到发 Release 的完整步骤（含协议选择、是否必须装 Git）见
[docs/GITHUB.md](docs/GITHUB.md)。

## 开发日志

每一步改动、实现思路和踩坑记录见 [docs/DEVLOG.md](docs/DEVLOG.md)。

## 许可证

[CC0-1.0](LICENSE) — 随便用、随便改，不需要署名。

---

<details>
<summary>English summary</summary>

**EasyTaskMgr** is a Fabric mod for Minecraft **26.3** that keeps a todo list in-game: press `B` to add a task,
see it in the top-right HUD (drag to reposition under `K`), and get notified when a condition is met —
collect N items (optionally counting opened containers and shulker boxes), craft an item, finish an advancement
(with prerequisite hints), reach coordinates (distance/direction/speed on the HUD), or after a delay.
Items and advancements are searched by localized name with suggestions. Shared tasks are relayed by the server
so teammates can see each other's todos.

Requires Java 25, Fabric Loader ≥ 0.20.1 and Fabric API for 26.3. Build with `./gradlew build`.

</details>
