# 发布到 GitHub 的完整步骤

这份文档按「什么都没准备」的情况写，分两条路线：

- **路线 A：网页上传**（不需要装任何软件，适合只发一次）
- **路线 B：Git 命令行**（推荐，之后改代码、发版本都方便）

> 本机当前**没有安装 Git**（`git` 命令不存在）。路线 A 完全不需要它；
> 如果想用路线 B，需要你手动装一次 [Git for Windows](https://git-scm.com/download/win)。

---

## 0. 上传前检查

项目里已经准备好的东西：

| 文件 | 作用 |
| --- | --- |
| `README.md` | 仓库首页说明（功能、安装、用法、构建） |
| `LICENSE` | 许可证（当前 CC0-1.0） |
| `.gitignore` | 已经排除了 `build/`、`run/`、`.gradle/`、日志等 |
| `.github/workflows/build.yml` | 推上去后 GitHub 自动编译，产出 jar |
| `docs/DEVLOG.md` | 开发日志 |
| `gradlew` / `gradlew.bat` / `gradle/wrapper/` | 别人 clone 下来就能构建 |

**不要上传的目录**（`.gitignore` 已处理，网页上传时手动跳过）：

- `build/`（编译产物，几十 MB）
- `run/`（本地测试存档、日志、配置）
- `.gradle/`（Gradle 缓存）
- `*.log`

**要上传的 jar**：只有 `build/libs/easytaskmgr-1.0.0-beta.2.jar`（发布 Release 时单独上传，不用放进源码）。

---

## 路线 A：网页上传（不需要 Git）

1. 打开 https://github.com/new
2. 填写：
   - **Repository name**：`easytaskmgr`（小写，和 mod id 一致）
   - **Description**：`Minecraft 26.3 Fabric 模组：游戏内待办清单，支持物品/成就/合成/坐标触发提醒与多人共享`
   - 选择 **Public**（公开）
   - **不要**勾选 “Add a README file / .gitignore / license”（我们已经有文件了，勾了会冲突）
3. 点 **Create repository**。
4. 在新仓库页面点 **uploading an existing file**（或 `Add file → Upload files`）。
5. 把项目里**除 `build/`、`run/`、`.gradle/` 之外**的所有文件和文件夹拖进去：
   - `src/`、`gradle/`、`docs/`、`.github/`
   - `build.gradle`、`settings.gradle`、`gradle.properties`、`gradlew`、`gradlew.bat`
   - `README.md`、`LICENSE`、`.gitignore`、`.gitattributes`
6. 下方 Commit changes 填 `init: EasyTaskMgr 1.0.0-beta.2`，点 **Commit changes**。

> 网页上传对文件数量有限制（一次 100 个），本项目源码文件 35 个左右，够用。
> 如果提示文件太多，就分两次上传（先传根文件，再传 `src/`）。

---

## 路线 B：Git 命令行（推荐）

### B1. 安装 Git

下载安装 [Git for Windows](https://git-scm.com/download/win)，一路默认即可。
装完开一个新终端，确认：

```powershell
git --version
```

### B2. 配置身份（只需一次）

```powershell
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
```

### B3. 初始化并提交

在项目根目录（`E:\Project\miniMAP`）执行：

```powershell
git init
git branch -M main
git add .
git commit -m "init: EasyTaskMgr 1.0.0-beta.2"
```

`.gitignore` 会自动跳过 `build/`、`run/`、`.gradle/`，可以用 `git status` 确认没有把大文件加进去。

### B4. 关联远程仓库并推送

先在 GitHub 建一个**空仓库**（同路线 A 第 1-3 步，不要勾任何初始化文件），然后：

```powershell
git remote add origin https://github.com/<你的用户名>/easytaskmgr.git
git push -u origin main
```

推送时会弹浏览器登录 GitHub（推荐）或要求输入 Personal Access Token
（GitHub 已不支持密码推送：Settings → Developer settings → Personal access tokens → Tokens (classic) → 勾 `repo`）。

---

## 发布 Release（两种路线都一样）

1. 确认本地构建通过：

   ```powershell
   .\gradlew.bat build
   ```

   产物：`build/libs/easytaskmgr-1.0.0-beta.2.jar`

2. 打标签并推送（路线 B）：

   ```powershell
   git tag v1.0.0-beta.2
   git push origin v1.0.0-beta.2
   ```

   路线 A 可以跳过打标签，直接在网页建 Release 时填 tag。

3. 打开仓库 → 右侧 **Releases → Create a new release**：
   - **Choose a tag**：`v1.0.0-beta.2`（没有就新建）
   - **Release title**：`EasyTaskMgr 1.0.0-beta.2`
   - **Describe this release**：直接粘贴 `docs/release-notes/1.0.0-beta.2.zh-CN.md` 的内容
     （英文版用 `1.0.0-beta.2.en.md`；GitHub 的 Release 正文支持 Markdown）
   - **Attach binaries**：上传 `easytaskmgr-1.0.0-beta.2.jar`
   - 因为是测试版，勾选 **Set as a pre-release**
4. 点 **Publish release**。

> 建议同时在 Release 说明里写清楚：需要 Minecraft 26.3、Fabric Loader ≥ 0.20.1、Fabric API 对应版本；
> 多人服务器要共享待办与成就目录时，服务端也要装。

---

## 开源协议怎么选

当前仓库用的是 **CC0-1.0**（Fabric 官方模板默认，等于“放弃版权，随便用”）。常见选择对比：

| 协议 | 特点 | 适合 |
| --- | --- | --- |
| **MIT** | 最短、最宽松，保留版权声明即可 | 大多数模组，最省心（推荐） |
| **Apache-2.0** | 类似 MIT，额外有专利授权条款 | 想更正式一点 |
| **GPL-3.0** | 传染性：别人改了再发布也必须开源 | 希望衍生作品保持开源 |
| **CC0-1.0** | 放弃所有权利，连署名都不要求 | 示例代码、模板 |

**换协议只要三步**：

1. 用新协议全文替换 `LICENSE` 文件（从 https://choosealicense.com/ 复制）；
2. 改 `src/main/resources/fabric.mod.json` 里的 `"license": "CC0-1.0"` → 例如 `"MIT"`；
3. README 里的 License 段落同步改一下。

> 注意：协议一旦发布就很难收回，选之前想清楚；模组代码里如果引用了别人的 GPL 代码，就不能只用 MIT。

---

## 上传后可以做的三件事

1. **看 Actions 是否通过**：仓库顶部 Actions 标签，绿勾说明 `build.yml` 编译成功，
   可以在那次运行的 Artifacts 里下载 jar。
2. **加截图**：把游戏内截图放到 `docs/screenshots/`，在 README 的「HUD 预览」下面用
   `![HUD](docs/screenshots/hud.png)` 引用。
3. **写 Issue 模板**（可选）：`.github/ISSUE_TEMPLATE/bug_report.md`，让反馈带上版本、日志、复现步骤。
