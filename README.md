# Logyee

> 一款轻量、安全、跨代理的 Minecraft 登录插件（Auth / Login 插件）。

Logyee 是一个以账号安全为核心的登录注册插件，**同一个 JAR 文件同时支持 Bukkit / Spigot / Paper 服务端、BungeeCord 代理端、Velocity 代理端**三大平台。它在一个插件中集成了账号注册、登录、改密、邮箱找回密码，以及一套完整的「登录前行为限制 + 跨代理登录态同步 + 防绕过指令防火墙」安全体系。

本项目基于 [CatSeedLogin](https://github.com/CatSeed/CatSeedLogin) 二次开发，相关许可声明见文末 [许可证](#许可证)。

---

## 目录

- [核心特性](#核心特性)
- [支持的平台与版本](#支持的平台与版本)
- [工作原理（架构）](#工作原理架构)
- [快速安装](#快速安装)
- [配置文件详解](#配置文件详解)
  - [Bukkit 端 settings.yml](#1-bukkit-端-settingsyml)
  - [数据库连接 sql.yml](#2-数据库连接-sqlyml)
  - [邮箱服务 emailVerify.yml](#3-邮箱服务-emailverifyyml)
  - [Bukkit ↔ 代理通讯 bungeecord.yml](#4-bukkit--代理通讯-bungeecordyml)
  - [代理端 bungeecord.yml / velocity.yml](#5-代理端-bungeecordyml--velocityyml)
  - [语言文件 language.yml](#6-语言文件-languageyml)
- [玩家指令](#玩家指令)
- [管理员指令（/logyee、/ly）](#管理员指令logyee小写字)
- [安全机制](#安全机制)
- [数据库](#数据库)
- [开发者 API](#开发者-api)
- [构建项目](#构建项目)
- [许可证](#许可证)

---

## 核心特性

| 特性 | 说明 |
| --- | --- |
| **三端合一** | 单个 JAR 同时兼容 Bukkit 服务端、BungeeCord 代理、Velocity 代理；各平台只加载自身代码，互不影响。 |
| **账号体系** | 邮箱式注册、登录、修改密码、邮箱找回密码，全程 SHA‑512 加盐哈希存储。 |
| **密码强度校验** | 强制 6–16 位、必须同时包含字母与数字，杜绝弱口令。 |
| **登录前全面限制** | 未登录玩家无法移动、聊天、交互、打开/点击背包、丢弃/拾取物品、攻击、传送、执行非白名单指令。 |
| **登录前免伤** | 可配置未登录期间玩家不受任何伤害（`BeforeLoginNoDamage`）。 |
| **隐藏背包** | 安装 ProtocolLib 后，登录前通过发包隐藏玩家物品栏，防止被窥探。 |
| **跨代理登录态同步** | 通过 Socket 长连接，服务端（Bukkit）与代理端（Bungee/Velocity）实时同步登录/登出状态，切服无需重复登录。 |
| **防重复登录** | 同一账号已登录时，禁止从其他地方再次连接（代理端 + 服务端双重校验）。 |
| **指令防火墙（Anti‑Bypass）** | 未登录玩家只允许执行白名单指令（支持正则）；**代理指令（如 `/ban`）和非白名单指令一律拦截**，无法绕过权限直接进入子服。 |
| **Velocity 原生拦截** | Velocity 端使用原生 `CommandExecuteEvent`（而非 Bungee 的 `ChatEvent`），从根本上解决 Snap 等环境下 `ChatEvent` 失效导致指令绕过的问题。 |
| **IP 限制** | 同一 IP 注册账号数量上限（`IpRegisterCountLimit`）与同 IP 在线账号数量上限（`IpCountLimit`）均可配置。 |
| **自动踢出** | 未在限定时间内登录的玩家会被自动踢出（`AutoKick`），防止占位与卡服。 |
| **登录回退点** | 登录后自动传送回上一次退出的位置（`AfterLoginBack`）。 |
| **大小写防护** | 登录名与注册名大小写必须一致，防止大小写冒用他人账号。 |
| **多数据库** | 默认内置 SQLite，亦可一键切换到 MySQL。 |
| **可自定义语言** | 所有提示信息集中在 `language.yml`，支持颜色代码自定义。 |
| **开发者扩展** | 提供 `LogyeeAPI` 与 `LogyeePlayerLoginEvent` / `LogyeePlayerRegisterEvent` 事件。 |

---

## 支持的平台与版本

| 组件 | 平台 | 技术要求 |
| --- | --- | --- |
| 服务端 | Bukkit / Spigot / Paper（api‑version 1.13+，兼容 1.13 及以上） | Java 17 |
| 代理 · BungeeCord | BungeeCord（基于 `bungeecord-api` 1.16‑R0.4） | Java 17 |
| 代理 · Velocity | Velocity（基于 `velocity-api` 3.3.0） | Java 17 |
| 可选依赖 | ProtocolLib（用于登录前隐藏背包） | 服务端侧可选 |

> 编译要求：**Java 17 + Maven 3**（见 [构建项目](#构建项目)）。

---

## 工作原理（架构）

Logyee 采用「**服务端为 Socket 服务端，代理端为 Socket 客户端**」的长连接架构：

```
        ┌─────────────────────────┐
        │   Bukkit 服务端 (Logyee) │
        │   运行 Socket Server      │
        │   账号数据 / 登录逻辑核心 │
        └───────────┬─────────────┘
                    │  长连接 (TCP)
        ┌───────────┴─────────────┐
        │  BungeeCord / Velocity   │
        │  运行 Socket Client      │
        │   拦截未登录玩家指令      │
        └─────────────────────────┘
```

通讯协议（明文、行分隔）如下：

| 方向 | 指令 | 含义 |
| --- | --- | --- |
| 代理 → 服务端 | `CONNECT <玩家名>` | 查询某玩家是否已登录 |
| 服务端 → 代理 | `CONNECT_RESULT <玩家名> <0\|1>` | 0=未登录，1=已登录 |
| 服务端 → 代理 | `PLAYER_LOGIN <玩家名>` | 玩家登录，通知代理标记登录态 |
| 服务端 → 代理 | `PLAYER_LOGOUT <玩家名>` | 玩家登出，通知代理清除登录态 |
| 代理 → 服务端 | `KEEP_LOGGED_IN <玩家> <时间戳> <签名>` | 切服回到登录服时维持登录态（带 MD5 签名校验） |
| 双向 | `PING <时间戳>` / `PONG <时间戳>` | 心跳保活（30s 一次，60s 超时重连） |

- **登录态同步**：玩家在子服登录后，服务端主动推送 `PLAYER_LOGIN`，代理端将其加入「已登录集合」。此后该玩家在代理层的命令不再被拦截。
- **切服免重登**：玩家切回登录服（`LoginServerName`，默认 `lobby`）时，代理端若已是登录态，会发送 `KEEP_LOGGED_IN` 让服务端恢复其登录态。
- **防重复登录**：玩家进入代理前，代理端会同时检查自身登录态与子服登录态（`CONNECT`），只要有一处已登录即拒绝连接。
- **断线自愈**：代理端内置自动重连与心跳超时检测，服务端端口断开后会持续重连。

---

## 快速安装

### 仅服务端（单机 / 无代理）

1. 将编译好的 `Logyee.jar` 放入服务端的 `plugins/` 目录。
2. 启动服务器，插件会在 `plugins/Logyee/` 下生成配置文件。
3. 按需修改 `plugins/Logyee/settings.yml`、`sql.yml`、`emailVerify.yml`。
4. 使用 `/logyee reload` 或重启服务器使配置生效。

### 服务端 + 代理（BungeeCord / Velocity）

1. **服务端**：将 `Logyee.jar` 放入子服（登录服及所有需要保护的子服）的 `plugins/`，并确保 `plugins/Logyee/bungeecord.yml` 中 `Enable: true`，填写 `Host` / `Port` 与一致的 `AuthKey`。
2. **代理端**：将**同一个** `Logyee.jar` 放入 BungeeCord 的 `plugins/` 或 Velocity 的 `plugins/`。
   - BungeeCord：配置文件为 `plugins/Logyee-Bungee/bungeecord.yml`（由插件从 `bungee-resources/bungeecord.yml` 生成）。
   - Velocity：配置文件为 `plugins/logyee-velocity/velocity.yml`（由插件从 `velocity-resources/velocity.yml` 生成）。
3. 代理端配置中同样设置 `Enable: true`、`Host`、`Port`、`LoginServerName`、`AuthKey`（与服务端 `bungeecord.yml` 保持一致）。
4. 重启代理与子服，观察日志中出现 `Connected to Bukkit <Host>:<Port>` 即表示通讯建立。

> ⚠️ 服务端 `bungeecord.yml` 中的 `Host`/`Port` 是 **Socket 监听地址（服务端侧）**；代理端 `bungeecord.yml` / `velocity.yml` 中的 `Host`/`Port` 是**代理连向服务端的地址**。两端 `AuthKey` 必须相同。

---

## 配置文件详解

### 1. Bukkit 端 settings.yml

这是最常用的配置文件，控制所有行为开关与限制。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `IpRegisterCountLimit` | `2` | 同一 IP 最多可注册的账号数量。 |
| `IpCountLimit` | `2` | 同一 IP 同时在线的最大账号数（≤0 表示不限制）。 |
| `LimitChineseID` | `true` | 是否限制游戏名只能由数字、字母、下划线组成（禁止中文名）。 |
| `MinLengthID` | `2` | 游戏名最小长度（至少 1，且不能大于最大长度）。 |
| `MaxLengthID` | `15` | 游戏名最大长度（不超过 16）。 |
| `BeforeLoginNoDamage` | `true` | 登录前玩家是否免疫一切伤害。 |
| `ReenterInterval` | `60` | 玩家退出后重新进入、被视为「重连而非新登录」的时间间隔（单位：tick）。 |
| `AfterLoginBack` | `true` | 登录后是否传送回上次退出的位置。 |
| `CanTpSpawnLocation` | `true` | 登录前是否强制把玩家限制在登录点（禁止乱跑）。 |
| `AutoKick` | `120` | 未登录玩家的自动踢出时间（秒，<1 表示关闭）。 |
| `DeathStateQuitRecordLocation` | `true` | 玩家在死亡状态下退出时是否记录退出位置（防止利用死亡退出刷地点）。 |
| `CommandWhiteList` | 见下 | Bukkit 侧登录前允许执行的指令（**正则表达式**，匹配整条消息或指令名）。 |

默认 `CommandWhiteList` 内容（正则）：

```
- /(?i)l(ogin)?(\z| .*)        # 允许 /login、/l
- /(?i)reg(ister)?(\z| .*)     # 允许 /register、/reg
- /(?i)resetpassword?(\z| .*)  # 允许 /resetpassword、/repw
- /(?i)repw?(\z| .*)           # 允许 /repw
- /(?i)worldedit cui           # 允许 WorldEdit 的 cui 指令
```

> 提示：绝大多数玩家指令已在代理端内置白名单中，这里一般不需要改动。

### 2. 数据库连接 sql.yml

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `MySQL.Enable` | `false` | 是否使用 MySQL（false 时使用内置 SQLite）。 |
| `MySQL.Host` | `127.0.0.1` | MySQL 主机地址。 |
| `MySQL.Port` | `3306` | MySQL 端口。 |
| `MySQL.Database` | `databaseName` | 数据库名。 |
| `MySQL.User` | `root` | 用户名。 |
| `MySQL.Password` | `root` | 密码。 |

> 使用 MySQL 时，切换后会在启动时自动建表。

### 3. 邮箱服务 emailVerify.yml

开启邮箱找回密码功能的前提。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `Enable` | `false` | 是否启用邮箱功能（`/bindemail`、`/resetpassword` 依赖此项）。 |
| `EmailAccount` | `763737569@qq.com` | 发件邮箱账号。 |
| `EmailPassword` | `123456` | 发件邮箱授权码/密码。 |
| `EmailSmtpHost` | `smtp.qq.com` | SMTP 服务器地址。 |
| `EmailSmtpPort` | `465` | SMTP 端口。 |
| `SSLAuthVerify` | `true` | 是否启用 SSL。 |
| `FromPersonal` | `xxx服务器` | 收件人看到的发件人名称。 |

### 4. Bukkit ↔ 代理通讯 bungeecord.yml

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `Enable` | `false` | 服务端是否启动 Socket 监听（接入代理时改为 `true`）。 |
| `Host` | `127.0.0.1` | Socket 监听地址（服务端侧）。 |
| `Port` | `2333` | Socket 监听端口。 |
| `AuthKey` | `""` | 通讯签名密钥，**务必与代理端一致**。 |

### 5. 代理端 bungeecord.yml / velocity.yml

> BungeeCord 实际路径为 `plugins/Logyee-Bungee/bungeecord.yml`；Velocity 为 `plugins/logyee-velocity/velocity.yml`。两者的字段完全相同。

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `Enable` | `true` | 代理端是否启用跨服登录同步。 |
| `Host` | `127.0.0.1` | 代理连向服务端 Socket 的地址。 |
| `Port` | `2333` | 代理连向服务端 Socket 的端口。 |
| `LoginServerName` | `lobby` | 登录服在代理中的服务器名（切服回此服时维持登录态）。 |
| `AuthKey` | `""` | 通讯签名密钥，须与服务端一致。 |
| `CommandWhitelist` | `[]` | 除内置白名单外，额外允许未登录玩家执行的指令（不区分大小写、不带 `/`）。 |

**内置命令白名单**（无需手动添加，硬编码在代理端）：

```
login, l, register, reg, bindemail, bdmail, resetpassword, repw, changepassword, changepw
```

### 6. 语言文件 language.yml

所有玩家可见提示均在此文件，使用 `&` 颜色代码（如 `&a` 绿色、`&c` 红色）。修改后 `/logyee reload` 即可生效。常用键：`LOGIN_REQUEST`（提示登录）、`REGISTER_REQUEST`（提示注册）、`LOGIN_SUCCESS`、`LOGIN_FAIL`、`REGISTER_SUCCESS`、`AUTO_KICK` 等。

---

## 玩家指令

| 指令 | 别名 | 用途 | 说明 |
| --- | --- | --- | --- |
| `/login <密码>` | `/l` | 登录 | 账号已注册后输入密码登录。 |
| `/register <密码> <重复密码>` | `/reg` | 注册 | 两次密码需一致，且满足强度规则；受同 IP 注册数量限制。 |
| `/changepassword <旧密码> <新密码> <重复新密码>` | `/changepw` | 修改密码 | 需已登录，新密码需满足强度规则。 |
| `/bindemail set <邮箱>` | `/bdmail` | 绑定邮箱（第一步） | 向邮箱发送验证码；需先开启邮箱功能且已登录。 |
| `/bindemail verify <验证码>` | `/bdmail` | 绑定邮箱（第二步） | 校验验证码完成绑定，用于后续找回密码。 |
| `/resetpassword forget` | `/repw` | 申请找回密码 | 向已绑定邮箱发送重置验证码（有效期 20 分钟）。 |
| `/resetpassword re <验证码> <新密码>` | `/repw` | 重置密码 | 校验验证码后设置新密码，需满足强度规则。 |

**密码强度规则**：长度 6–16 位，且必须同时包含英文字母和数字（纯数字或纯字母会被拒绝）。

**邮箱验证码**：绑定与找回两类验证码默认有效期 20 分钟，过期需重新申请。

---

## 管理员指令（/logyee、/ly）

主命令 `/logyee`（可缩写为 `/ly`、`/logy`），需要权限节点 `logyee.command.logyee`。所有 `set` 改动会自动保存到 `settings.yml`。

### 设置项（set / see 动词）

| 用法 | 默认值 | 说明 |
| --- | --- | --- |
| `/logyee set/see cmdwhitelist\|cw add\|rm <指令>` | — | 登录前允许执行的指令（支持正则），可增删查看。 |
| `/logyee set/see ipreg\|ir <数量>` | `2` | 同一 IP 注册账号数量限制。 |
| `/logyee set/see ipjoin\|ij <数量>` | `2` | 同一 IP 同时在线账号限制（<1 不限制）。 |
| `/logyee set/see name\|nm least\|long <值>` | `2` / `15` | 游戏名最小 / 最大长度。 |
| `/logyee set/see reconnect\|re <间隔>` | `60` | 重连判定间隔（tick）。 |
| `/logyee set/see autokick\|ak <秒数>` | `120` | 未登录自动踢出时间（<1 关闭）。 |
| `/logyee set/see/tp Spawn` | 世界出生点 | 设置 / 查看 / 传送到登录点。 |
| `/logyee set/see chineseid\|cnid on/off` | `true` | 限制中文游戏名。 |
| `/logyee set/see nodamage\|nd on/off` | `true` | 登录前免伤。 |
| `/logyee set/see alb\|afterloginback on/off` | `true` | 登录后返回退出点。 |
| `/logyee set/see forceloginlocation\|fsl on/off` | `true` | 登录前强制在登录点。 |
| `/logyee set/see deathrecord\|dr on/off` | `true` | 死亡退出记录位置。 |

### 直接指令

| 用法 | 说明 |
| --- | --- |
| `/logyee rmplayer\|rmp <玩家名>` | 强制删除某账号（在线则一并踢出）。 |
| `/logyee setpwd\|sp <玩家名> <密码>` | 强制设置/重置某玩家密码（账号不存在会自动注册）。 |
| `/logyee reload\|re` | 重新加载配置文件、重建数据库连接与 Socket 监听。 |

> 所有 `set` 命令改动即时落盘；修改数据库相关或通讯开关后建议用 `/logyee reload`。

---

## 安全机制

1. **登录前行为封锁（Bukkit 侧）**：监听 `PlayerMove`、`PlayerChat`、`PlayerInteract`、`InventoryOpen/Click`、`PlayerDropItem`、`EntityPickupItem`、`PlayerTeleport`、`EntityDamageByEntity` 等事件，未登录玩家一律取消，仅放行登录白名单指令。
2. **指令白名单（双保险）**：
   - 服务端 `settings.yml` 的 `CommandWhiteList`（正则）。
   - 代理端内置白名单 + `bungeecord.yml`/`velocity.yml` 的 `CommandWhitelist`。
   - 未登录玩家**只能**执行白名单指令，包括 Bungee/Velocity 的代理指令（如 `/ban`）也被拦截，无法绕过进入子服。
3. **隐藏背包**：检测到 ProtocolLib 后，登录前拦截 `SET_SLOT` / `WINDOW_ITEMS` 发包，玩家看不到物品栏内容；重置密码/改密后还会主动发送空背包包刷新。
4. **大小写防护**：`AsyncPlayerPreLoginEvent` 校验登录名大小写与注册名完全一致，不一致直接拒绝。
5. **防重复登录**：进入代理前同时校验代理端登录态与子服登录态（`CONNECT`），已登录则拒绝新连接。
6. **密码存储安全**：密码经 `Crypt.encrypt(name, password)` 使用 SHA‑512（含玩家名与固定盐）哈希后入库，明文密码不落盘。
7. **通讯鉴权**：代理 → 服务端的 `KEEP_LOGGED_IN` 使用 `MD5(name + 时间戳 + AuthKey)` 签名，防止伪造维持登录态。

---

## 数据库

- **默认 SQLite**：无需任何配置，数据保存在服务端插件目录的数据库文件中（账号表 `accounts`）。
- **可选 MySQL**：在 `sql.yml` 中将 `MySQL.Enable` 改为 `true` 并填写连接信息即可切换，适合多子服共享同一账号库。

账号表 `accounts` 结构：

| 字段 | 说明 |
| --- | --- |
| `name` | 玩家名（主键标识，大小写敏感存储）。 |
| `password` | SHA‑512 加盐密码哈希。 |
| `email` | 绑定的邮箱（可为空）。 |
| `ips` | 历史登录 IP 列表（分号分隔，最多保留 5 个）。 |
| `lastAction` | 最近一次操作时间戳。 |

> 插件在内存中维护一份账号缓存（`Cache`），启动与每次改动后刷新，保证查询性能。

---

## 开发者 API

`top.zhrhello.logyee.bukkit.LogyeeAPI` 提供静态方法供其他插件调用：

| 方法 | 返回值 / 含义 |
| --- | --- |
| `isLogin(String name)` | 玩家当前是否已登录。 |
| `isRegister(String name)` | 玩家是否已注册。 |
| `verifyPassword(String name, String password)` | 校验明文密码是否正确（true=正确）。 |
| `markLoggedIn(String name)` | **恢复登录态**（用于跨线换服后重建登录态，不触发登录事件、不记录 IP、不传送）。返回 `0`=成功、`1`=未注册、`2`=已登录（幂等）。 |

事件（位于 `top.zhrhello.logyee.bukkit.event`）：

- `LogyeePlayerLoginEvent` — 玩家登录成功/失败时触发，携带玩家、邮箱与结果。
- `LogyeePlayerRegisterEvent` — 玩家注册成功时触发。

---

## 构建项目

环境要求：**JDK 17**、**Maven 3**。

```bash
# 克隆或进入 Logyee 项目目录
cd Logyee

# 编译并打包（自动执行 shade， relocated SnakeYAML 到 top.zhrhello.logyee.libs.snakeyaml）
mvn clean package
```

构建产物位于 `target/Logyee-1.0.jar`，可直接放入对应服务端 / 代理端的 `plugins/` 目录。

依赖说明：
- 编译期依赖（provided）：SpigotAPI 1.12.2、BungeeCord API、Velocity API、ProtocolLib、Lombok。
- 打包进 JAR：SQLite JDBC、MySQL Connector、JavaMail、SnakeYAML（已 relocate）。
- 已排除：JUnit、各平台 API 与 JDBC 驱动（由运行环境提供），避免冲突。

---

## 许可证

本项目为基于 [CatSeedLogin](https://github.com/CatSeed/CatSeedLogin) 的衍生作品（上游项目）。

- **上游原许可证**：CatSeedLogin 原始采用 **MIT License**，其原始许可证文本保留在仓库的 **`LICENSE_OLD`** 文件中。
- **本项目现许可证**：Logyee 现以 **Apache License 2.0** 发布，完整许可证文本见仓库的 **`LICENSE`** 文件。

使用、修改或分发本作品时，请同时遵守 `LICENSE`（Apache 2.0）的要求，并保留 `LICENSE_OLD` 中对上游 CatSeedLogin（MIT License, Copyright (c) 2021 CatSeed）的原始版权与许可声明。
