# 交接文档：Foo's Client（mindustry-f-client）网络修复与构建流水线

> 新工作区：`E:\Game\mindustry-f-client`
> 生成时间：2026-11-05
> 状态：代码修复已提交并推送；CI 已跑通；带音频的 jar 已发布到 Release。

---

## 0. 一句话现状

F端（Foo's Client）的联机界面「无限刷新 / 无限重连 / 请求堆积」问题已修完并合入 `v8`，
构建流水线已改为**自动带音频**并产出 Release 资产 `mindustry-f-client.jar`（96.6 MB），
以后只要 `git merge upstream/v8 && git push` 就能拿到最新 F端 + 本地修复的带音频客户端。

---

## 1. 仓库与产物

| 项 | 值 |
|---|---|
| 新工作区 | `E:\Game\mindustry-f-client`（旧工作区 `C:\Users\LENOVO\Desktop\fun\Try\mindustry-client` 已清空，可删） |
| 工作分支 | `v8` |
| HEAD | `9cb88644e8 client: network loop/timeout fixes + audio build workflow`（已 push，== origin/v8） |
| `origin` | `https://github.com/Cynyun/mindustry-f-client.git`（F端 的 fork，public） |
| `upstream` | `https://github.com/mindustry-antigrief/mindustry-client.git`（F端 本体，只 fetch） |
| 基础提交 | `caab524 archash`（F端 v8 tip，构建版本 `160.7`） |
| 直接下载 | `https://github.com/Cynyun/mindustry-f-client/releases/download/latest-build/mindustry-f-client.jar` |
| Release 页面 | `https://github.com/Cynyun/mindustry-f-client/releases/tag/latest-build`（每次都覆盖同一个资产） |

CI 最近一次运行（提交 `9cb8864`）三项全部 success：`Build Client`、`Java Tests`、`Validate Gradle Wrapper`。

旧的一次性私有仓库 `Cynyun/temp`（单 commit 快照）已被本仓库取代，确认无用后可以在 GitHub 删除。

---

## 2. 背景：最初要修的问题

用户给的原始诊断文档（`网络问题坐标和修复建议.md`，已随旧工作区删除，内容存档于此）：

| # | 位置 | 性质 |
|---|---|---|
| 1 | `JoinDialog.reconnect()` | 真死循环：`Timer.schedule(task,1,1)` 每秒 ping，失败回调空实现、无次数上限 |
| 2 | `JoinDialog.refreshCommunity()` + `fetchServers()` | 失败回环：全部 URL 失败 → post `refreshCommunity` → 又从头 `fetchServers()` |
| 3 | `fetchServers()` 的 `Http.get(url)` | 无超时，连接可能挂很久 |
| 4 | `Vars.serverJsonURLs` | `raw.githubusercontent.com` / `cdn.jsdelivr.net` 在国内易被 RST |
| 5 | 社区列表每个地址都 ping | 默认 65 线程的 ping 执行器，刷新一次就是一次 ping 风暴 |
| 6 | `shown()` / F5 / `onResize` | 都会 `refreshAll()` 全量重刷 |
| 7 | `if(refreshes != cur) return;` | 只丢弃过期回调，不取消已发请求，重刷会叠加并发 |
| 8 | `defaultServers.addAll(...)` | 理论上会累积（实际因 `fetchedServers` 保护基本不可达） |

用户当时的结论：只做「不改代码」的临时措施（降 ping 线程数、关社区列表）能降低刷新频率、能看到部分服务器，但**仍然进不去**。

---

## 3. 已完成的代码修复（commit 9cb88644e8）

改动 4 个文件：`core/src/mindustry/ui/dialogs/JoinDialog.java`、`SettingsMenuDialog.java`、
`core/assets/bundles/bundle.properties`、`bundle_zh_CN.properties`。另外新增 1 个 workflow 文件。

### 3.1 重连（对应问题 1）

`JoinDialog.java:766` 起：

- 新增 `reconnectMaxTries = 10`、`reconnectTries`、`reconnectGeneration`、`reconnecting`。
- `reconnect()` 先 `cancelReconnect()`，再起一轮，最多 10 次 ping；超过就 `failReconnect()`（收起加载框 + `ui.showInfo("@reconnect.failed")`）。
- **同一时刻只允许 1 个 ping 在飞**：`Timer.schedule` 单次定时 → 回调里再调度下一次，彻底消除「每秒叠加一个 ping」。
- Timer 回调在 Timer 线程，真正的 `connect()` / UI 操作都 `Core.app.post` 回主线程。
- 用 `reconnectGeneration` 代际丢弃过期回调，重复点击/取消不会出现两个循环并存。

### 3.2 社区列表拉取（对应问题 2 / 3 / 7）

`JoinDialog.java:849` 起：

- 新增 `fetchingServers`（在飞标记）与 `fetchGeneration`（代际）：过期响应、过期重试链直接丢弃。
- `fetchServers()` 先判 `fetchedServers || fetchingServers`，避免启动时 `loadServers()` 和 `shown()` 各拉一次。
- `refreshCommunity()`（`:472`）只有在「没取到 + 没在请求 + 上次没失败」时才发起 fetch，彻底切断「失败 → 刷新 → 再 fetch」的死循环。
- `Http.get(urls[index]).timeout(5000)`（`:893`）：5 秒连接/读取超时，挂死的连接不再卡住重试链。
- 失败后在错误行加「Refresh」按钮（`refetchCommunityServers()`，`:579`），人工重试会清掉失败标记。
- 成功/失败回调只在 `ui.join.isShown()` 时刷新 UI，避免后台空转 ping。

### 3.3 重复触发（对应问题 4）

`JoinDialog.java:154` 起：`onResize` 改为 **20 帧去抖**，拖拽窗口停稳后才 `setup()+refreshAll()`；
`Time.runTask` 的回调在 Timer 线程，内部 `Core.app.post(this::refreshAfterResize)` 回主线程；
`hide()` / `hide(Action)` 里取消待执行的刷新（注意：不能用 `hidden(...)`，那会覆盖 `BaseDialog` 自己的 hidden 回调）。

### 3.4 其它一致性修复

- `refreshCommunity()` 用 `int cur = ++refreshes`（`:472`），每次重建都是新代际，旧 ping 结果必然作废。
- `communityHosts` 的 ping 失败回调不再空实现，带代际判断。
- 「Use v8/BE server list」切换按钮：原来清空 `defaultServers` 但 `fetchedServers` 仍为 true，新结果被丢弃导致列表变空；现在会重置状态并强制重拉。
- 设置里 `communityservers` 开关：`JoinDialog.resetServerFetch()`（`:876`）重置拉取状态，关掉再打开能真的重新拉。
- 新增语言键 `reconnect.failed`（英文 + 简体中文）。

---

## 4. 构建流水线

### 4.1 新 workflow：`.github/workflows/build-client.yml`

| 步骤 | 说明 |
|---|---|
| 触发 | push 到 `v8` / `my-v8` / `master` / `main`，或手动 `Run workflow` |
| 环境 | ubuntu-latest + **JDK 17**（temurin）+ `gradle/actions/setup-gradle@v4` 缓存 |
| 构建 | `./gradlew desktop:dist --parallel --stacktrace` |
| 加音频 | `jar uf desktop/build/libs/Mindustry.jar -C core/assets sounds -C core/assets music`（与 F端 官方一致，约 +34 MB） |
| 改名 | 复制为 `dist/mindustry-f-client.jar`，并用 `jar tf \| grep -c '^(sounds\|music)/'` 校验，数量为 0 直接失败 |
| 产物 | `actions/upload-artifact@v4`（名为 `mindustry-f-client`）+ `softprops/action-gh-release@v2` 更新 tag `latest-build` 的 prerelease |

**为什么还要 Release**：GitHub 的 artifact 下载永远是 zip，拿不到裸 `.jar`；Release 资产才是可直接下载的文件。
所以 `mindustry-f-client.jar` 走 Release，artifact 只当备份。

**权限**：`permissions: contents: write`。若换仓库/组织后 Release 步骤 403，去
Settings → Actions → General → Workflow permissions 改成 **Read and write**。

**为什么 `desktop:dist` 默认没音频**：`desktop/build.gradle:56` 有 `exclude("sounds/", "music/")`，
官方发布时会用 `jar uf` 把音频补进去（旧 `tests.yml` 里那一步带 `if:` 限定官方仓库，所以在 fork 里被跳过，
这也是之前拿到「无声版」的原因）。

### 4.2 已有的其它 workflow

- `tests.yml`（Java Tests）：push 任意分支触发，跑 `tests:test` + `desktop:dist`，同样会出 artifact。它也能跑通，但会让每次 push 多构建一次；只想构建可以到 Actions 页面 Disable。
- `pr.yml`（PR Tests）、`release.yml`（只在 `v*` tag 触发，且要求 JDK 16，**不要用**）、`gradle-wrapper-validation.yml`（校验 wrapper，无害）。

---

## 5. 日常维护：怎么收 F端 更新

```powershell
cd E:\Game\mindustry-f-client
git fetch upstream                 # 若 git 报 TLS/schannel 错，见第 7 节
git switch v8
git merge upstream/v8              # 冲突热点：JoinDialog / SettingsMenuDialog / bundle*.properties / build-client.yml
# 解决冲突 → 提交
git push origin v8                 # 触发 CI，几分钟后 Release 里的 jar 自动更新
```

偏好线性历史可以 `git rebase upstream/v8` 再 `git push --force-with-lease origin v8`。

注意事项：

- **`archash` 会变**：F端 每次升级 Arc 都会改 `gradle.properties` 的 `archash`（当前 `e44aa77b82e7604ebb71d49a63f548de7084e69d`）。合并后如果 jitpack 还没生成对应产物，构建会失败，等几分钟重跑即可。已验证当前 hash 的 arc 全部模块（arc-core / backend-sdl / backend-headless / flabel / freetype / g3d / fx / arcnet / packer / natives-* 等）在 jitpack 返回 200。
- `gradlew` 必须是 `100755`（本仓库已是；workflow 里还有 `chmod +x gradlew` 兜底）。
- 你的改动尽量保持「小 commit、按主题拆分」，冲突范围就小。
- 如果这些修复被 F端 官方接受，就可以直接从本地分支删掉对应 commit，维护成本归零（建议试着给 F端 提 PR，`JoinDialog` 那套改动通用性强）。

---

## 6. 验证情况与遗留事项

**已验证**

- CI 三次全绿（`Build Client` / `Java Tests` / `Validate Gradle Wrapper`）。
- Release 资产 `mindustry-f-client.jar` 96.6 MB（= 普通 dist + sounds + music），workflow 内的音频条目校验也通过（数量 > 0 才继续）。
- 改动文件曾在旧工作区用 `javac --release 17` 对 `core/src` 全量做过类型检查（当时用外部 jar 当 arc classpath），`JoinDialog.java` / `SettingsMenuDialog.java` 0 error；其余报错是缺注解处理器/kotlin 依赖造成的既有噪音。

**未验证 / 可选后续**

1. 游戏内实际行为（只在构建层面验证过）——建议用 Release 的 jar 实测：进联机界面看是否不再刷屏、断线重连是否 10 次后停止、错误行是否有 Refresh 按钮。
2. 其它 HTTP 调用点没加超时（`ModBrowserDialog`、`SchematicBrowserDialog`、`BeControl`、`SteamAdmin`），文档里只是「建议统一补上」，本次未动。
3. 没改「Ping执行器线程数」默认 65（文档里它只是临时措施）。
4. 「点服务器仍然连不上」的根因未处理：`connect()` 是先 ping 通才真正 TCP 连接（`JoinDialog.java` 的 `connect()`），被墙/丢包时 ping 失败就直接弹错。可选后续：ping 失败后允许直连一次，或给 `serverJsonURLs` 加国内可达镜像兜底（现在只是加了超时，域名被 RST 仍然拉不到列表）。
5. 未向上游 F端 提 PR。

---

## 7. 这台机器的环境坑（重要）

1. **Windows TLS 在受控 shell 里是坏的**：`curl` / `.NET`（Invoke-WebRequest）/ git 的 schannel 后端都会报
   `SEC_E_NO_CREDENTIALS`。**JVM 自己的 TLS 正常**（用 Java 的 HttpClient 测 jitpack / maven central 全部 200）。
   - git 需要走 OpenSSL 后端：`git -c http.sslBackend=openssl fetch ...` 或设环境变量 `GIT_SSL_BACKEND=openssl`。
2. **文件沙箱**：DSH 只能写当前工作区（`workspace-write`）。写工作区外路径会报
   `[sandbox: file access denied under workspace-write mode]`，需要单次提权（`danger-full-access`）并在提示里说明原因。
   另外，工作区目录需要「低完整性」标记（DSH 自动加上），若某个目录是从别处拷进来的、完全不可写：
   `icacls "<目录>" /setintegritylevel (OI)(CI)Low`（回滚成 `Medium`）。
3. **家目录里有一个老 git 仓库**：`C:\Users\LENOVO\.git`（HEAD 是 2026-08-01 的「重命名项目名字」，
   只跟踪 35 个文件）。在任何没有自己 `.git` 的子目录里跑 `git -C` 会向上找到它，
   看到的是整个 `C:\Users\LENOVO` 的 status —— 别在那里误提交/误删。
4. `core.autocrlf=true`：提交进库的是 LF，工作区是 CRLF，属于正常，不要为此改配置。
5. 构建产物 `core/assets/version.properties`、`core/assets/locales`、`*/build/**` 都是被忽略的，不会弄脏仓库。

---

## 8. 关键文件索引

| 文件 | 作用 |
|---|---|
| `core/src/mindustry/ui/dialogs/JoinDialog.java` | 联机界面：重连、社区列表拉取、ping、resize 去抖（本次主要修改处） |
| `core/src/mindustry/ui/dialogs/SettingsMenuDialog.java` | `communityservers` 开关联动 `resetServerFetch()` |
| `core/assets/bundles/bundle.properties` / `bundle_zh_CN.properties` | `reconnect.failed` 文案 |
| `.github/workflows/build-client.yml` | 新的构建/带音频/发布流程 |
| `desktop/build.gradle` | `desktop:dist` 任务，含 `exclude("sounds/","music/")` |
| `build.gradle` / `settings.gradle` / `gradle.properties` | 模块与依赖（arc 走 jitpack，`archash` 控制版本） |
| `README.md` | 官方构建说明（`gradlew desktop:dist` → `desktop/build/libs/Mindustry.jar`） |

---

## 9. 速查命令

```powershell
# 构建（本地）
./gradlew desktop:dist
# 加音频（构建后）
jar uf desktop/build/libs/Mindustry.jar -C core/assets sounds -C core/assets music

# 收更新
git fetch upstream; git switch v8; git merge upstream/v8; git push origin v8

# 看 CI / 下载
# https://github.com/Cynyun/mindustry-f-client/actions
# https://github.com/Cynyun/mindustry-f-client/releases/tag/latest-build
```
