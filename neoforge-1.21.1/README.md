# EasyTaskMgr for NeoForge 1.21.1

这是 EasyTaskMgr 的 **NeoForge 1.21.1** 移植版，独立工程，不影响根目录的 Fabric 版。

| 项 | 值 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.233 |
| Java | 21 |
| 构建 | ModDevGradle 2.0.148 + Gradle 8.10.2 |

## 构建 / 运行

```bash
# Windows
set JAVA_HOME=<JDK 21>
gradlew.bat build

# 启动
gradlew.bat runServer
gradlew.bat runClient
```

产物：`build/libs/easytaskmgr-neoforge-1.0.1.jar`

## 当前状态

| 模块 | 状态 |
| --- | --- |
| 任务模型 / 优先级 / 类型 / CSV / i18n | ✅ 与 Fabric 版一致 |
| 网络包（共享操作、快照、进度、成就通知） | ✅ |
| 服务端共享任务（发布 / 认领 / 完成 / 共同完成 / 邀请 / 存档 / 批量提示） | ✅ `runServer` 实测可加载 |
| 客户端（HUD、键位、四个界面、自动检测） | ✅ 编译通过，⏳ 尚未在游戏内实际打开验证 |
| 成就候选自动补全 | ⏳ 未移植（服务端 `AdvancementDescriber` 待补） |
| HUD 拖拽定位 | ⏳ 暂用按钮调位置 |

## 与 Fabric 版的差异

- HUD 用 `RegisterGuiLayersEvent` + `LayeredDraw.Layer`，不用 Fabric 的 `HudElementRegistry`；
- HUD 点击用 `InputEvent.MouseButton.Pre` 拦截，不用 `MouseHandler` mixin；
- `/etm invite <玩家>` 是**服务端命令**（NeoForge 没有客户端命令），语义为「把我发布的全部特殊共享任务邀请给该玩家」；
- 1.21.1 客户端拿不到成就进度，成就类待办的完成由服务端 `AdvancementEvent.AdvancementEarnEvent` 通知客户端；
- 物品栏读取用 `inventory.items` + 副手 + 潜影盒 `DataComponents.CONTAINER`。

## 构建环境说明

`settings.gradle` 里优先使用 `https://neoforged.forgecdn.net/releases/`（官方 Maven 的 CDN），
并带一个本地 `local-maven/`：里面是用官方 1.21.1 版本清单生成的 `minecraft-dependencies:1.21.1` POM，
用于绕过某些网络下 `maven.neoforged.net` 不可达的问题。网络正常时它不会影响构建。

详细移植记录见 [PORTING-NOTES.md](PORTING-NOTES.md)。

## 客户端启动（本机工作区特殊网络环境）

本项目为了绕开不可达的 `maven.neoforged.net`，用了本地 `local-maven` 的
`minecraft-dependencies` POM。它的副作用是 **MDG 生成的启动参数里没有 LWJGL natives**，
直接 `runClient` 会报：

```
java.lang.UnsatisfiedLinkError: Failed to locate library: lwjgl.dll
```

解决办法（一次性，之后每次启动都带上这个环境变量即可）：

```powershell
# 1) 下载 LWJGL 3.3.3 的 natives（Maven Central 或 https://www.lwjgl.org/browse 同一批文件）
$mods = 'lwjgl','lwjgl-glfw','lwjgl-opengl','lwjgl-stb','lwjgl-tinyfd','lwjgl-openal','lwjgl-jemalloc','lwjgl-freetype'
$dir = "$PWD\build\lwjgl-natives"; New-Item -ItemType Directory -Force $dir | Out-Null
foreach ($m in $mods) {
    curl.exe -L -o "$env:TEMP\$m.jar" "https://repo1.maven.org/maven2/org/lwjgl/$m/3.3.3/$m-3.3.3-natives-windows.jar"
    & "$env:JAVA_HOME\bin\jar.exe" xf "$env:TEMP\$m.jar"   # 在 $dir 里执行
}

# 2) 启动时把 natives 目录告诉 JVM（Gradle 派生的子 JVM 会继承这个变量）
$env:JAVA_TOOL_OPTIONS = "-Djava.library.path=$dir -Dorg.lwjgl.librarypath=$dir"
.\gradlew.bat runClient
```

Linux / macOS 把 classifier 换成 `natives-linux` / `natives-macos`（Apple Silicon 用 `natives-macos-arm64`）。
网络正常、使用官方 Maven 的机器不需要这一步。

## 冒烟测试记录（2026-11-08）

- `gradlew build` ✅
- `gradlew runServer` ✅ 服务端加载、`Done`、共享存档读取正常
- `gradlew runClient` + 上面的 natives 配置 ✅ 客户端能启动并**成功进入服务器**
  （服务器日志 `Smoketest joined the game`），游戏内能打开待办列表界面
- 过程中发现并修复了一个真实问题：**客户端方向的 payload 通道只在客户端注册**，
  NeoForge 握手会报 `This channel is missing on the server side` 并把玩家踢掉；
  现已把 `share_snapshot` / `adv_earned` / `adv_data` 三个通道移到公共代码 `NeoPayloads` 注册