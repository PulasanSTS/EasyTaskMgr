# EasyTaskMgr · NeoForge 1.21.1 移植说明

目标：把 Fabric 版（Minecraft 26.3）的完整功能移植到 **NeoForge 21.1.233 + Minecraft 1.21.1 + Java 21**。
本目录是**独立工程**，不参与 `miniMAP` 的 Fabric 构建，也**没有推送 GitHub**。

## 当前状态

| 项 | 状态 |
| --- | --- |
| 工程骨架（Gradle 8.10.2 wrapper、ModDevGradle 2.0.148、NeoForge 21.1.233、toolchain 21） | ✅ 已建 |
| 与 Minecraft 无关的核心：`Task` / `TaskType` / `Priority` / `TimerMode` / `TaskCsv` / `I18n` | ✅ 已移植 |
| **`gradlew build`** | ✅ **成功**（`build/libs/easytaskmgr-neoforge-1.0.1.jar`，12m13s，含 Minecraft 反编译与 NeoForge 补丁） |
| 服务端：网络 payload、SharedTasks、ShareStore、ModConfig、TaskStore、`/etm invite`、资源 | ✅ 已移植，`runServer` 实际加载通过 |
| 客户端：ClientShared、ClientTasks、HUD（GUI Layer）、四个界面、键位、Notifier、成就查询 | ⏳ 待移植 |

## 阻塞原因（本机网络）

1. `maven.neoforged.net` 被 DNS 污染：本机解析到 `108.160.165.11`（Dropbox 段），真实解析是 `143.244.51.x / 89.187.187.x`（CDN77）；
2. 即使把 hosts 指到真实 IP，TLS 握手也会被 reset（`curl: (28)` / `Connection reset`），HTTP 同样不通；
3. 换用 USTC 的 bmclapi 镜像（`https://cmcc.mirrors.ustc.edu.cn/bmclapi/`）只能拿到部分构件：
   - ✅ `neoforge-21.1.233-universal.jar`（3.5 MB）、`neoform-1.21.1-20240808.144430.zip`（1.6 MB）
   - ❌ `neoform-runtime:2.0.31`（模组开发插件 `createMinecraftArtifacts` 必需）→ 404
4. 因此 Gradle 在 `:createMinecraftArtifacts` 阶段失败：

```
Could not resolve net.neoforged:neoform-runtime:2.0.31
  > Could not GET 'https://maven.neoforged.net/releases/net/neoforged/neoform-runtime/2.0.31/neoform-runtime-2.0.31.pom'
    > Got socket exception during request. It might be caused by SSL misconfiguration
      > Connection reset
```

**要真正编译/运行这个工程，需要能访问 `maven.neoforged.net`**（代理/VPN），
然后在 `build.gradle` 或 `gradle.properties` 里给 Gradle 配代理即可：

```properties
# gradle.properties（本机代理端口按实际改）
systemProp.http.proxyHost=127.0.0.1
systemProp.http.proxyPort=7890
systemProp.https.proxyHost=127.0.0.1
systemProp.https.proxyPort=7890
```

## 本机构建方法（绕开 maven.neoforged.net）

本机 DNS 污染 + TLS 拦截导致 `maven.neoforged.net` 不可用，用两步绕开：

1. **仓库换 CDN**：`settings.gradle` 里用 `dependencyResolutionManagement` + `RepositoriesMode.PREFER_SETTINGS`，
   只保留 `https://neoforged.forgecdn.net/releases/`（官方 Maven 的 CDN）、`libraries.minecraft.net`、`mavenCentral()`；
   这个 CDN 有 neoforge、neoform、neoform-runtime、mergetool、vineflower 等，但没有 `minecraft-dependencies`；
2. **本地补 POM**：`local-maven/net/neoforged/minecraft-dependencies/1.21.1/minecraft-dependencies-1.21.1.pom`
   是用官方 1.21.1 版本清单（`piston-meta` 的 `1.21.1.json` 里 97 个 library）生成的等价 POM，
   放在 `settings.gradle` 的第一个仓库里，MDG 就能继续走完 NeoForm 流程。

> 换到网络正常的机器时，可以把这两个 workaround 去掉，直接用官方仓库。
## 目录结构（当前）

```
neoforge-1.21.1/
├── build.gradle              # MDG 2.0.148 + NeoForge 21.1.233 + Java 21
├── settings.gradle
├── gradle.properties
├── gradle/wrapper/           # Gradle 8.10.2（腾讯云镜像分发）
├── gradlew / gradlew.bat
└── src/main/java/com/easytaskmgr/
    ├── task/                 # ✅ Task / TaskType / Priority / TimerMode / TaskCsv
    └── i18n/I18n.java        # ✅ 内置中英文案
```

## API 映射表（26.3 / Fabric → 1.21.1 / NeoForge）

| Fabric 26.3 | NeoForge 1.21.1 |
| --- | --- |
| `ModInitializer` / `ClientModInitializer` | `@Mod("easytaskmgr")` 类，构造器里挂 mod bus / game bus |
| `PayloadTypeRegistry.serverboundPlay()` | `RegisterPayloadHandlersEvent` → `registrar("1").playToServer(...)` |
| `PayloadTypeRegistry.clientboundPlay()` | 同上 `.playToClient(...)` |
| `ServerPlayNetworking.send(player, payload)` | `PacketDistributor.sendToPlayer(player, payload)` |
| `ClientPlayNetworking.send(payload)` | `PacketDistributor.sendToServer(payload)` |
| `ServerPlayNetworking.canSend` | 用 `player.connection` 的通道判断，或发送时 try/catch |
| `HudElementRegistry.addLast` | `RegisterGuiLayersEvent` + `LayeredDraw.Layer`（1.21.1） |
| `KeyMappingHelper.registerKeyMapping` | `RegisterKeyMappingsEvent#register`，`KeyMapping` 分类参数是 `String` |
| `ClientCommandRegistrationCallback` | `RegisterCommandsEvent`（服务端命令 `/etm invite`） |
| `ClientTickEvents.END_CLIENT_TICK` | `ClientTickEvent.Post` |
| `ServerLifecycleEvents.SERVER_STARTED/STOPPING` | `ServerStartedEvent` / `ServerStoppingEvent` |
| `ServerPlayConnectionEvents.JOIN` | `PlayerEvent.PlayerLoggedInEvent` |
| `FabricLoader#getConfigDir()` | `FMLPaths.CONFIGDIR.get()` |
| `Identifier` | `ResourceLocation`（1.21.1 已有 `fromNamespaceAndPath`） |
| `Screen#extractRenderState(GuiGraphicsExtractor,…)` | `Screen#render(GuiGraphics, int, int, float)` |
| `graphics.text(...)` / `centeredText(...)` | `graphics.drawString(font,…)` / `drawCenteredString(...)` |
| `mc.gui.screen()` / `mc.setScreenAndShow(s)` | `mc.screen` / `mc.setScreen(s)` |
| `MouseButtonEvent` | `mouseClicked(double, double, int)` |
| `Inventory#getNonEquipmentItems()` | `player.getInventory().items` + `getOffhandItem()` |
| `Level#getOverworldClockTime()` | `level.getDayTime()` |
| `ClientAdvancements#get(id)` | `ClientAdvancementManager#get(ResourceLocation)`，标题用 `holder.value().display().get().getTitle()` |
| `Advancement#name(holder)` | 无此方法，改用 `DisplayInfo#getTitle()` |
| `Component.translatable` / `ChatFormatting` | 相同 |
| `ItemContainerContents` / `DataComponents.CONTAINER` | 相同 |
| `server.getWorldPath(LevelResource.ROOT)` | 相同 |

## 待移植文件清单（按依赖顺序）

1. `config/ModConfig.java`（`FMLPaths.CONFIGDIR`）、`task/TaskStore.java`
2. `net/`：`ShareActionC2S` / `ShareProgressC2S` / `ShareSnapshotS2C`（`StreamCodec` 写法基本不变）+ `NetworkHandler`
3. `share/SharedTasks.java` / `share/ShareStore.java`（服务端逻辑几乎原样，去掉 Fabric 网络 API）
4. `EasyTaskMgrNeo.java`：注册 payload、命令 `/etm invite`、tick、登录事件
5. `client/`：`TaskHud`（GUI Layer）、`ClientTasks`、`ClientShared`、`Keybinds`、`Notifier`、`Suggestions`、`LangResources`、`AdvancementCatalog`、`TextWrap`
6. `client/ui/`：`QuickAddScreen`、`TaskListScreen`、`TaskEditScreen`、`ConfigScreen`
7. `resources`：`META-INF/neoforge.mods.toml`、`pack.mcmeta`（pack_format 34）、`assets/easytaskmgr/lang/*.json`、`icon.png`

## 移植时的注意点

- NeoForge 1.21.1 用 Mojang 官方映射，方法名与 26.3 明显不同，界面代码需要逐个改 API（不是复制粘贴）；
- `HUD 点击完成` 在 Fabric 版靠 `MouseHandler` mixin；NeoForge 版优先用 `InputEvent.MouseButton.Pre`，若不可用再保留 mixin；
- `/etm invite` 在 NeoForge 版改成**服务端命令**（客户端命令在 NeoForge 里不存在），用 `RegisterCommandsEvent` + 权限校验；
- 配置文件仍走自己的 `config/easytaskmgr.json`，不引入 `ModConfigSpec`，便于与 Fabric 版保持一致。
