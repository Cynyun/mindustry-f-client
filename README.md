![Logo](foo.png)

[![Build Client](https://github.com/Cynyun/mindustry-f-client/actions/workflows/build-client.yml/badge.svg?branch=v8)](https://github.com/Cynyun/mindustry-f-client/actions/workflows/build-client.yml)
[![Download](https://img.shields.io/badge/download-mindustry--f--client.jar-17cf48)](https://github.com/Cynyun/mindustry-f-client/releases/download/latest-build/mindustry-f-client.jar)
[![Upstream](https://img.shields.io/badge/upstream-Foo's%20Client-7289DA)](https://github.com/mindustry-antigrief/mindustry-client)

# Foo's Client · 个人定制版

这是 [Foo's Client](https://github.com/mindustry-antigrief/mindustry-client)（下称 **F端**）的 fork，基于其 `v8` 分支，在上游的基础上做了一些个人修改。游戏本体来自 [Anuken/Mindustry](https://github.com/Anuken/Mindustry)，客户端代码的版权与许可仍归上游所有。

**English** — A personal fork of [Foo's Client](https://github.com/mindustry-antigrief/mindustry-client) based on its `v8` branch, with some personal modifications on top of upstream. Prebuilt jars are published to the [`latest-build` release](https://github.com/Cynyun/mindustry-f-client/releases/tag/latest-build). Upstream docs: [mindustry-antigrief/mindustry-client](https://github.com/mindustry-antigrief/mindustry-client#readme).

> 本仓库不是 F端 官方版本。上游的安装器 / 更新器不会更新到这里的构建；想用本仓库的版本，请下载下面 Release 里的 jar。

## 下载

| 文件 | 说明 |
|---|---|
| [mindustry-f-client.jar](https://github.com/Cynyun/mindustry-f-client/releases/download/latest-build/mindustry-f-client.jar) | 每次 push 到 `v8` 自动构建，已内置 `sounds/` 与 `music/`（约 96 MB），对应 F端 `v8` / 构建号 `160.7` |

GitHub Actions 里上传的 artifact 永远是 zip，想要可以直接运行的 `.jar` 请用上面的 Release 链接。旧构建都在 [Release 页面](https://github.com/Cynyun/mindustry-f-client/releases) 的 `latest-build` tag 下覆盖更新。

## 安装

下载后按平台放到位：

### Windows（Steam）

- **快速试用**：把 jar 和本仓库根目录的 [`steam_appid.txt`](steam_appid.txt) 放到同一个文件夹，先启动 Steam 并登录，然后双击 jar。
- **装进 Steam**：用管理员权限打开 CMD，执行（会删掉 `Mindustry.exe` 并换成指向 `cmd.exe` 的符号链接，介意的话先备份）：

  ```bat
  cd /d "C:\Program Files (x86)\Steam\steamapps\common\Mindustry"
  del Mindustry.exe
  mklink Mindustry.exe C:\Windows\System32\cmd.exe
  curl -L -o jre\client.jar https://github.com/Cynyun/mindustry-f-client/releases/download/latest-build/mindustry-f-client.jar
  ```

  然后在 Steam 里把该游戏的启动选项设为 `/c java -jar "%cd%\jre\client.jar"`。

### macOS

打开游戏安装目录，右键 `Mindustry.app` → `Show Package Contents` → `Contents/Resources`，用下载的 jar 替换其中的 `desktop.jar`。

### Linux

```bash
java -jar mindustry-f-client.jar
```

### Android

CI 不产出 APK，需要自己编译，步骤见上游 README 的 Android 章节（`gradlew android:assembleDebug`）。

## 本地构建

需要 [JDK 17](https://adoptium.net/temurin/releases/?os=any&arch=any&version=17)，其它版本不行。

```bash
# 运行
gradlew desktop:run        # Linux/macOS 用 ./gradlew

# 打包
gradlew desktop:dist

# 补音频（desktop:dist 默认不含音频）
jar uf desktop/build/libs/Mindustry.jar -C core/assets sounds -C core/assets music
```

产物在 `desktop/build/libs/Mindustry.jar`。Android 构建见上游 README，`gradlew android:assembleDebug` 会生成未签名 APK 到 `android/build/outputs/apk`。

### 常见问题

- **`Permission denied` / `Command not found`（mac/Linux）**：先 `chmod +x ./gradlew`。
- **`mindustry.gen` 包在哪**：构建时生成，仓库里没有源码，也不要手改。
- **首次构建很慢**：Gradle 要下载依赖，耐心等。
- **能当服务端用吗**：不能，客户端只做客户端。

## 同步上游

本仓库的 `origin` 是自己的 fork，`upstream` 指向 F端 本体：

```bash
git fetch upstream
git switch v8
git merge upstream/v8
# 解决冲突 → 提交
git push origin v8            # 触发 CI，几分钟后 Release 里的 jar 自动更新
```

- 冲突热点：`JoinDialog.java`、`SettingsMenuDialog.java`、`bundle*.properties`、`build-client.yml`。
- `gradle.properties` 里的 `archash` 会随 F端 升级 Arc 而变化；合并后若 jitpack 还没生成对应的 arc 依赖，等几分钟重跑 CI 即可。
- 想保持线性历史可以 `git rebase upstream/v8` 后 `git push --force-with-lease origin v8`。

## 反馈

- **本仓库的 Issues 是关闭的**：GitHub 的 fork 默认不带 Issues，这里也没有手动打开，所以页面上没有 Issues 标签。
- **通用 bug / 功能建议**：发给上游 —— [Discord](https://discord.gg/yp9ZW7j) 或上游的 [issues](https://github.com/mindustry-antigrief/mindustry-client/issues)；哪些 issue 会被直接关掉见上游的 [ISSUES.md](ISSUES.md)。
- **只和本 fork 有关的问题**：先在 Discord 找我；以后若要收 issue，在仓库 Settings → General → Features 里勾上 Issues 即可。

## 致谢与许可

- 游戏本体：[Anuken/Mindustry](https://github.com/Anuken/Mindustry)
- 客户端上游：[mindustry-antigrief/mindustry-client](https://github.com/mindustry-antigrief/mindustry-client)（F端 功能更新日志见 [core/assets/changelog](core/assets/changelog)，社区 [Discord](https://discord.gg/yp9ZW7j)）
- 本仓库与其修改同样以 [GPL-3.0](LICENSE) 发布；上游维护的文档（[ISSUES.md](ISSUES.md)、[TRANSLATING.md](TRANSLATING.md)）仍适用。
