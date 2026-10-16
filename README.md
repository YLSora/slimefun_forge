# Slimefun Forge 1.0.0

将粘液科技带到 Minecraft Forge 的全功能原生实现模组，基于 Slimefun RC-37，由 **MinazukiSora** 移植。

通过游戏内的粘液科技指南，你可以查阅配方、解锁研究，逐步制作机器、搭建能源和物流网络，并使用工具、装备与魔法物品。

## 安装

- Minecraft **1.20.1**
- Forge **47.4 及以上**
- Java **17及以上**
- 开发Java **17**

将 `slimefun-forge-1.20.1-1.0.0.jar` 放入游戏的 `mods` 文件夹。多人游戏需要客户端和服务端安装相同版本。

## 如何开始

首次进入世界默认会获得粘液科技指南书，手持右键即可打开。也可以使用：

- `/sf guide`：领取指南书。
- `/sf open_guide`：直接打开指南。
- `/sf search 关键词`：搜索物品。

按照指南中的配方制作物品，消耗经验等级解锁研究，再逐步发展自动化生产。

## 世界配置与备份

Slimefun 的世界配置统一位于 `<存档>/serverconfig/slimefun_forge/`，包含 `slimefun-server.toml` 和各功能的 `slimefun-*-server.toml`。旧目录中的配置不再读取；有自定义设置的存档需要将设置应用到新目录中的对应文件。

`slimefun-storage-server.toml` 的 `backup-data` 默认关闭。开启后，每次正常退出会同步压缩整个世界的数据到 `<存档>/slimefun-backups/`，保留最近 20 份，退出耗时会随存档大小增长。Minecraft 正常保存和 Slimefun 的定时检查点不依赖这个备份开关。

## 从源码构建

在 `Slimefun_forge` 目录中，将 `JAVA_HOME` 指向 Java 17，然后运行：

```powershell
.\gradlew.bat build
```

构建产物位于本目录下的 `build/libs/`。游戏使用 `slimefun-forge-1.20.1-1.0.0.jar`；带 `-sources` 的 JAR 供开发者阅读源码。

项目缓存位于 `.gradle/`，开发运行目录为 `run/client/` 和 `run/server/`。附属模板和示例分别使用自身目录下的 `build/`、`.gradle/` 和 `run/`。

## AI声明

本移植模组由GPT 6 Astra、GPT 6.1 Sol、Deepseek v4.1 Flash完成。代码99.99%由AI开发，所以不保证稳定性和还原性:D

## 开发附属

阅读 [Addon API 文档](ADDON_API.md)，从[空白模板](addons/template/)开始，或参考[独立示例](addons/example/)。附属使用 Forge 原生 API，需要针对本模组开发，原版 Bukkit 附属不能直接加载。

## 致谢

采用 [GPL-3.0-only](LICENSE) 许可。原项目作者及其他贡献者见 [AUTHORS.md](AUTHORS.md) 和 [CREDITS.txt](CREDITS.txt)。

## 声明

本模组不公开发表，本仓库仅限个人学习参考和存放，禁止用于商业或分发用途。如需转载或使用请联系原作
