# MCP 回归测试

用 [Minecraft MCP](https://github.com/MakkCH/minecraft_mcp)（Fabric 26.3 客户端模组 + JsMacros）驱动真实客户端，
对 EasyTaskMgr 做端到端的自动断言：造任务、塞物品、点界面、截图、比对模组状态。

> 这是**可选**的开发工具，不参与构建，也不影响 `gradlew build` 的产物。

## 依赖

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| Minecraft MCP | `minecraft-mcp-26.3-*.jar` | 放进客户端运行目录的 `mods/`，启动后监听 `28473` |
| JsMacros Reloaded | `jsmacros-26.3-2.2.1-fabric.jar` | 同上，MCP 执行脚本用 |
| Node.js | ≥ 22 | 运行本目录的脚本 |

客户端模组会把端口与 token 写进 `<runDir>/config/minecraft_mcp.json`，脚本自动读取。
该文件含访问 token，已在 `.gitignore` 中排除。

## 一次性准备

```bash
# 客户端测试：把两个 jar 放进 run/mods
# 启动专用服务器前，把这两个 jar 移出 run/mods —— 它们都是客户端模组，
# 留在里面会让 runServer 因为找不到客户端类而启动失败
```

服务端首次启动需要 `run/eula.txt`（内容 `eula=true`，dev 服务器会自动生成，改成 true 即可）。
建议在 `run/server.properties` 里设 `online-mode=false`，两个客户端用不同用户名登录。

## 运行

```bash
# 单人：收集任务自动完成 + 截图
node tools/mcp-tests/single.mjs

# 双人：共享任务认领 → 完成（需要两个客户端，B 用独立目录 run-b）
node tools/mcp-tests/shared.mjs
```

双人用例的客户端配置可用环境变量覆盖：

```
MCP_A_RUN_DIR / MCP_B_RUN_DIR
MCP_A_PORT + MCP_A_TOKEN / MCP_B_PORT + MCP_B_TOKEN
```

## 输出

- 控制台：`ok` / `FAIL` 逐项断言，失败时退出码为 1；
- 截图：`tools/mcp-tests/out/*.png`（已 gitignore）。

## 用例覆盖

| 脚本 | 断言 |
| --- | --- |
| `single.mjs` | 桥接可用；任务创建后未完成；塞入 5 个钻石后 3 秒内自动完成；截图 |
| `shared.mjs` | A 发布共享任务；B 能看到并可认领；A 的快照显示认领者；B 完成后两端 `done` |

## 已知限制

- 只支持 Fabric 26.x（MCP 模组的支持范围），NeoForge 1.21.1 移植版无法用它测；
- `[AGENT]` 聊天里的中文会显示成 `????`，断言文案请用英文；
- 「共同完成（数量相加）」需要服务端背包里真的有物品（`/give` 需要 op），当前脚本先覆盖认领/完成路径，
  共同完成可在配好 op 的测试服上扩展。
