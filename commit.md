fix: 修复代码审查中25项必修Bug

基于 issues.md 完整修复清单，涵盖 Bukkit（17项）、BungeeCord（4项）、
Velocity（3项 + 跨端同步）三个平台。

## Bukkit 端（17项）

### 并发与线程安全
- Listeners.java: 已在线拒绝后立即return，避免继续执行IP计数；
  getAddress()空值检查；使用ArrayList快照代替直接遍历在线玩家；
  IpCountLimit<=0 时跳过计数
- CommandChangePassword.java: catch块内sender.sendMessage跨线程调度到主线程
- CommandLogyee.java: delPlayer异步块中两处sender.sendMessage调度到主线程；
  setPwd中sender.sendMessage同样调度到主线程
- CommandRegister.java: runTaskAsync前捕获IP和UUID，所有Bukkit API调用
  移入主线程回调

### 防御性编程
- ProtocolLibListeners.java: player空值/临时玩家提前return；
  读windowId前校验packet.getIntegers().size()>0
- CommandRegister.java: 添加 instanceof Player 校验
- SQLite.java: mkdir()→mkdirs()，创建失败return null不再递归
- LoginPlayerHelper.java: getAddress()空值return；
  IP列表while循环删除最旧项（修复差一错误）

### 数据完整性与状态管理
- CommandBindEmail.java: EmailCode.removeByName移到在线检查外部；
  catch块sender.sendMessage调度到主线程
- CommandChangePassword.java: 创建LoginPlayer副本，先edit持久化再删除原缓存
- CommandLogyee.java: setPwd中创建副本先edit再改缓存；
  Bukkit.getPlayer→getPlayerExact
- CommandResetPassword.java: 改密码前保存原哈希，DB失败时恢复

### 输入验证
- CommandLogyee.java: commandWhiteListAdd中Pattern.compile捕获
  PatternSyntaxException并提示；正则长度>256拒绝

### 资源管理
- CommandLogyee.java: reload前调用Logyee.sql.close()关闭旧连接
- SQL.java: 新增close()方法；getAll()/getLikeByIp()用try-finally
  确保ResultSet和PreparedStatement关闭

### 功能正确性
- LoginPlayerHelper.java: sendBlankInventoryPacket inventorySize 45→46，
  覆盖副手槽（槽45）

### 逻辑缺陷
- CommandBindEmail.java: 未知子命令发送用法提示并return false

## BungeeCord 端（4项）

- Communication.java: closeSocket()中完成所有PENDING_RESULTS Future
  并清空集合；CONNECT_RESULT解析捕获NumberFormatException
- Config.java: mkdir()→mkdirs()；getResourceAsStream空值检查
- Listeners.java: 登录态列表List→Set，玩家名统一小写规范化
- Listeners.java: onPreLogin拆分同步/异步，runAsync执行CONNECT请求，
  registerIntent/completeIntent避免阻塞事件线程；取消原因非空

## Velocity 端（3项 + 跨端同步）

- Communication.java: 与Bungee端同步修复closeSocket Future完成
  和CONNECT_RESULT parseInt防御（#27/#28跨端）
- Listeners.java: 登录态List→Set+小写规范化（#31跨端）；
  onCommand CONNECT成功分支移除executeAsync重派发，
  改为提示玩家重新输入（#34）；
  onPreLogin返回EventTask.async()异步执行CONNECT，
  非空拒绝消息（#35）
