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
| 客户端（HUD、键位、四个界面、自动检测） | ✅ 已实机验证（2026-11-08：进服、四个界面可打开，见下方测试记录） |
| 成就候选自动补全 | ✅ 基础版；⏳ 前置/条件详情待补 |
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

解决办法（一次性；把 natives 解压到固定目录，然后让 JVM 在启动时找到它们）：

### Windows（PowerShell）

```powershell
$mods = 'lwjgl','lwjgl-glfw','lwjgl-opengl','lwjgl-stb','lwjgl-tinyfd','lwjgl-openal','lwjgl-jemalloc','lwjgl-freetype'
$dir  = Join-Path $PWD 'build\lwjgl-natives'
New-Item -ItemType Directory -Force -Path $dir | Out-Null

# JAVA_HOME 没设置时退回 PATH 上的 jar
$jar = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\jar.exe' } else { 'jar.exe' }

foreach ($m in $mods) {
    $tmp = Join-Path $env:TEMP "$m-natives-windows.jar"
    curl.exe -L --fail -o $tmp "https://repo1.maven.org/maven2/org/lwjgl/$m/3.3.3/$m-3.3.3-natives-windows.jar"
    Push-Location $dir            # 关键：解压到 natives 目录，而不是当前目录
    try { & $jar xf $tmp } finally { Pop-Location }
}

# 官方 jar 里的 dll 在 windows/x64/org/lwjgl/... 子目录下，摊平到 $dir 根目录
Get-ChildItem $dir -Recurse -Filter *.dll | ForEach-Object {
    $target = Join-Path $dir $_.Name
    if ($_.FullName -ne $target) { Move-Item $_.FullName $target -Force }
}

# 路径加引号，避免项目路径里有空格时被拆开
$env:JAVA_TOOL_OPTIONS = "-Djava.library.path=`"$dir`" -Dorg.lwjgl.librarypath=`"$dir`""
.\gradlew.bat runClient
```

### Linux / macOS（bash）

```bash
mods="lwjgl lwjgl-glfw lwjgl-opengl lwjgl-stb lwjgl-tinyfd lwjgl-openal lwjgl-jemalloc lwjgl-freetype"
classifier="natives-linux"      # macOS: natives-macos；Apple Silicon: natives-macos-arm64
dir="$PWD/build/lwjgl-natives"
mkdir -p "$dir"

for m in $mods; do
    tmp="$(mktemp -d)/$m-$classifier.jar"
    curl -fL -o "$tmp" "https://repo1.maven.org/maven2/org/lwjgl/$m/3.3.3/$m-3.3.3-$classifier.jar"
    unzip -jo "$tmp" '*.so' '*.dylib' '*.dll' -d "$dir"   # -j 摊平到根目录
done

export JAVA_TOOL_OPTIONS="-Djava.library.path=\"$dir\" -Dorg.lwjgl.librarypath=\"$dir\""
./gradlew runClient
```

> 如果 `JAVA_TOOL_OPTIONS` 里的引号在你的 shell 里仍然被吞掉（路径带空格），
> 可以先用 `subst X: "$dir"`（Windows）或 `ln -s` 到无空格路径，再把 `-Djava.library.path` 指向它。

网络正常、使用官方 Maven 的机器不需要这一步。

## 功能对照（Fabric 版 ↔ NeoForge 版）

| 功能 | Fabric（26.3） | NeoForge（1.21.1） |
| --- | --- | --- |
| 快速添加 / 列表 / 编辑 / 设置四个界面 | ✅ | ✅ 已移植（界面排版还在打磨） |
| HUD：优先级染色、进度行、共享任务、闪烁 | ✅ | ✅ |
| HUD 交互模式（V 键点击完成/认领） | ✅ | ✅ |
| HUD 位置拖拽 | ✅ 设置页拖预览框 | ✅ 设置页拖预览框 |
| 收集 / 坐标 / 成就 / 定时四类任务 | ✅ | ✅（成就完成由服务端通知） |
| 物品 / 成就名候选补全 | ✅ 带分页与详情 | ✅ 基础版（最多 8 条、点击填入） |
| 共享任务：发布 / 认领 / 完成 / 撤回 / 邀请 | ✅ | ✅ `/etm invite` 为服务端命令 |
| 五种完成条件（含共同完成数量相加） | ✅ | ✅ 数量用服务端背包实测值校验 |
| 批量发布提示（>3 条合并） | ✅ | ✅ |
| CSV 导入导出（含共享列） | ✅ | ✅ |
| 中英文案 / 语言跟随 | ✅ | ✅ |
| 成就前置/条件详情面板 | ✅ | ⏳ 未移植（只做标题候选） |
| 列表「显示已完成」开关 | ✅ | ⏳ 目前始终显示已完成段 |
| 编辑界面按类型隐藏无关字段 | ✅ | ⏳ 目前全部字段常显 |

## 完整测试记录（2026-11-08，客户端 + 专用服务器）

- 启动：`runServer` → `Done`；`runClient`（带 `JAVA_TOOL_OPTIONS` natives）→ 进服成功（`Smoketest joined the game`）；
- 造数据：普通/收集/坐标/定时各一条 + 一条共享收集任务（共同完成）；
- 服务端日志确认共享任务发布成功：
  `Smoketest 发布共享任务 …（多人：一起收泥土 24，完成条件=共同完成（数量相加））`；
- 界面：列表、编辑、设置、快速添加四个界面都能打开并渲染按钮；
- 已知排版问题（下一轮修）：
  - 编辑界面的按钮文字被宽度截断（`Remind me when I joi…`）、`Completion: ?` 已在本轮改为默认「任一（含发布者）」；
  - 编辑界面未按类型隐藏无关输入框；
  - 列表缺「显示已完成」开关。