package top.zhrhello.logyee.bukkit.command;

import top.zhrhello.logyee.bukkit.Config;
import top.zhrhello.logyee.bukkit.Logyee;
import top.zhrhello.logyee.bukkit.Communication;
import top.zhrhello.logyee.bukkit.database.Cache;
import top.zhrhello.logyee.bukkit.database.SQL;
import top.zhrhello.logyee.bukkit.object.LoginPlayer;
import top.zhrhello.logyee.bukkit.object.LoginPlayerHelper;
import top.zhrhello.logyee.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * /logyee 管理员管理指令（主命令可缩写 /ly）
 *
 * 设计：顶层动词分为三类
 *   1) 直接指令：reload/re、rmplayer/rmp、setpwd/sp、tp
 *   2) set/see 动词 + 设置项（带短别名）：对某项配置进行设置或查看
 *
 * 所有配置项最终都落到 Config.Settings，并通过 Config.Settings.save() 落盘。
 */
public class CommandLogyee implements CommandExecutor, TabCompleter {

    private static final Locale LOCALE = Locale.ROOT;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        String verb = args[0].toLowerCase(LOCALE);

        // 直接指令（无需 set/see 动词）
        switch (verb) {
            case "reload":
            case "re":
                return doReload(sender);
            case "rmplayer":
            case "rmp":
                return doRemovePlayer(sender, subArgs(args, 1));
            case "setpwd":
            case "sp":
                return doSetPassword(sender, subArgs(args, 1));
            case "tp":
                return doTeleportSpawn(sender, subArgs(args, 1));
        }

        // set / see 动词 + 设置项
        if (verb.equals("set") || verb.equals("see")) {
            boolean isSet = verb.equals("set");
            if (args.length < 2) {
                sendHelp(sender);
                return true;
            }
            return handleSetting(sender, isSet, args[1].toLowerCase(LOCALE), subArgs(args, 2));
        }

        sendHelp(sender);
        return true;
    }

    // ------------------------------------------------------------------
    // set / see 分发
    // ------------------------------------------------------------------

    private boolean handleSetting(CommandSender sender, boolean isSet, String target, String[] rest) {
        switch (target) {
            case "cmdwhitelist":
            case "cw":
                return handleCommandWhiteList(sender, isSet, rest);

            case "ipreg":
            case "ir":
                return handleIntSetting(sender, isSet, rest,
                        () -> Config.Settings.IpRegisterCountLimit,
                        v -> Config.Settings.IpRegisterCountLimit = v,
                        "相同ip注册限制数量");

            case "ipjoin":
            case "ij":
                return handleIntSetting(sender, isSet, rest,
                        () -> Config.Settings.IpCountLimit,
                        v -> Config.Settings.IpCountLimit = v,
                        "相同ip登录限制数量");

            case "name":
            case "nm":
                return handleNameLength(sender, isSet, rest);

            case "reconnect":
            case "re":
                return handleLongSetting(sender, isSet, rest,
                        () -> Config.Settings.ReenterInterval,
                        v -> Config.Settings.ReenterInterval = v,
                        "离开服务器重新进入的间隔限制");

            case "autokick":
            case "ak":
                return handleAutoKick(sender, isSet, rest);

            case "spawn":
                return handleSpawn(sender, isSet, rest);

            case "chineseid":
            case "cnid":
                return handleBoolSetting(sender, isSet, rest,
                        () -> Config.Settings.LimitChineseID,
                        v -> Config.Settings.LimitChineseID = v,
                        "限制中文游戏名");

            case "nodamage":
            case "nd":
                return handleBoolSetting(sender, isSet, rest,
                        () -> Config.Settings.BeforeLoginNoDamage,
                        v -> Config.Settings.BeforeLoginNoDamage = v,
                        "登陆之前不受到伤害");

            case "alb":
            case "afterloginback":
                return handleBoolSetting(sender, isSet, rest,
                        () -> Config.Settings.AfterLoginBack,
                        v -> Config.Settings.AfterLoginBack = v,
                        "登陆之后返回退出地点");

            case "forceloginlocation":
            case "fsl":
                return handleBoolSetting(sender, isSet, rest,
                        () -> Config.Settings.CanTpSpawnLocation,
                        v -> Config.Settings.CanTpSpawnLocation = v,
                        "登录之前强制在登陆地点");

            case "deathrecord":
            case "dr":
                return handleBoolSetting(sender, isSet, rest,
                        () -> Config.Settings.DeathStateQuitRecordLocation,
                        v -> Config.Settings.DeathStateQuitRecordLocation = v,
                        "死亡状态退出游戏记录退出位置");

            default:
                sender.sendMessage("§c未知的设置项: " + target);
                sendHelp(sender);
                return true;
        }
    }

    // ------------------------------------------------------------------
    // 设置项处理器
    // ------------------------------------------------------------------

    private boolean handleCommandWhiteList(CommandSender sender, boolean isSet, String[] rest) {
        if (!isSet) {
            sender.sendMessage("§e登录前允许执行的指令 (正则):");
            if (Config.Settings.CommandWhiteList.isEmpty()) {
                sender.sendMessage("§8(空)");
            } else {
                for (String s : Config.Settings.CommandWhiteList) {
                    sender.sendMessage("§7- " + s);
                }
            }
            return true;
        }
        if (rest.length < 1) {
            sender.sendMessage("§c用法: /logyee set cmdwhitelist|cw add|rm [指令]");
            return true;
        }
        String action = rest[0].toLowerCase(LOCALE);
        if (!action.equals("add") && !action.equals("rm")) {
            sender.sendMessage("§c请使用 add 或 rm");
            return true;
        }
        String cmd = String.join(" ", subArgs(rest, 1)).trim();
        if (cmd.isEmpty()) {
            sender.sendMessage("§c指令不能为空!");
            return true;
        }
        if (action.equals("add")) {
            try {
                Pattern.compile(cmd);
            } catch (PatternSyntaxException e) {
                sender.sendMessage("§c正则表达式语法错误: " + e.getMessage());
                return true;
            }
            if (Config.Settings.CommandWhiteList.contains(cmd)) {
                sender.sendMessage("§c已经存在 " + cmd);
            } else {
                Config.Settings.CommandWhiteList.add(cmd);
                Config.Settings.save();
                sender.sendMessage("§e已添加登录前可执行指令 (正则): §a" + cmd);
            }
        } else {
            if (Config.Settings.CommandWhiteList.remove(cmd)) {
                Config.Settings.save();
                sender.sendMessage("§e已删除登录前可执行指令: " + cmd);
            } else {
                sender.sendMessage("§c不存在 " + cmd);
            }
        }
        return true;
    }

    private boolean handleIntSetting(CommandSender sender, boolean isSet, String[] rest,
                                     IntSupplier getter, IntConsumer setter, String label) {
        if (!isSet) {
            sender.sendMessage("§e" + label + ": §a" + getter.getAsInt());
            return true;
        }
        if (rest.length < 1) {
            sender.sendMessage("§c用法: /logyee set <项> [数量]");
            return true;
        }
        try {
            int v = Integer.parseInt(rest[0]);
            setter.accept(v);
            Config.Settings.save();
            sender.sendMessage("§e已设置 " + label + " 为 §a" + v);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c请输入一个数字");
        }
        return true;
    }

    private boolean handleLongSetting(CommandSender sender, boolean isSet, String[] rest,
                                      LongSupplier getter, LongConsumer setter, String label) {
        if (!isSet) {
            sender.sendMessage("§e" + label + ": §a" + getter.getAsLong() + "tick");
            return true;
        }
        if (rest.length < 1) {
            sender.sendMessage("§c用法: /logyee set <项> [间隔]");
            return true;
        }
        try {
            long v = Long.parseLong(rest[0]);
            setter.accept(v);
            Config.Settings.save();
            sender.sendMessage("§e已设置 " + label + " 为 §a" + v + "tick");
        } catch (NumberFormatException e) {
            sender.sendMessage("§c请输入一个数字");
        }
        return true;
    }

    private boolean handleAutoKick(CommandSender sender, boolean isSet, String[] rest) {
        if (!isSet) {
            if (Config.Settings.AutoKick > 0) {
                sender.sendMessage("§e未登录自动踢出累计时间: §a" + Config.Settings.AutoKick + "秒");
            } else {
                sender.sendMessage("§e未登录自动踢出: §8已关闭");
            }
            return true;
        }
        if (rest.length < 1) {
            sender.sendMessage("§c用法: /logyee set autokick|ak [秒数]");
            return true;
        }
        try {
            int v = Integer.parseInt(rest[0]);
            if (v < 1) {
                Config.Settings.AutoKick = 0;
                sender.sendMessage("§e已关闭未登录自动踢出");
            } else {
                Config.Settings.AutoKick = v;
                sender.sendMessage("§e已设置未登录自动踢出累计时间为 §a" + v + "秒");
            }
            Config.Settings.save();
        } catch (NumberFormatException e) {
            sender.sendMessage("§c请输入一个数字");
        }
        return true;
    }

    private boolean handleBoolSetting(CommandSender sender, boolean isSet, String[] rest,
                                      BooleanSupplier getter, Consumer<Boolean> setter, String label) {
        if (!isSet) {
            sender.sendMessage("§e" + label + ": " + onOff(getter.getAsBoolean()));
            return true;
        }
        if (rest.length < 1) {
            sender.sendMessage("§c用法: /logyee set <项> on/off");
            return true;
        }
        Boolean val = parseOnOff(rest[0]);
        if (val == null) {
            sender.sendMessage("§c请使用 on 或 off");
            return true;
        }
        setter.accept(val);
        Config.Settings.save();
        sender.sendMessage("§e已" + (val ? "开启" : "关闭") + label);
        return true;
    }

    private boolean handleNameLength(CommandSender sender, boolean isSet, String[] rest) {
        if (!isSet) {
            sender.sendMessage("§e游戏名长度限制: 最小 §a" + Config.Settings.MinLengthID
                    + " §e最大 §a" + Config.Settings.MaxLengthID);
            return true;
        }
        if (rest.length < 2) {
            sender.sendMessage("§c用法: /logyee set name|nm least|long [值]");
            return true;
        }
        String which = rest[0].toLowerCase(LOCALE);
        int val;
        try {
            val = Integer.parseInt(rest[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§c请输入数字");
            return true;
        }
        if (which.equals("least")) {
            if (val < 1) {
                sender.sendMessage("§c最小长度至少为 1");
                return true;
            }
            if (val > Config.Settings.MaxLengthID) {
                sender.sendMessage("§c最小长度不能大于最大长度");
                return true;
            }
            Config.Settings.MinLengthID = val;
        } else if (which.equals("long")) {
            if (val < Config.Settings.MinLengthID) {
                sender.sendMessage("§c最大长度不能小于最小长度");
                return true;
            }
            if (val > 16) {
                sender.sendMessage("§c最大长度不能超过 16");
                return true;
            }
            Config.Settings.MaxLengthID = val;
        } else {
            sender.sendMessage("§c请使用 least 或 long");
            return true;
        }
        Config.Settings.save();
        sender.sendMessage("§e已设置游戏名" + (which.equals("least") ? "最小" : "最大") + "长度为 §a" + val);
        return true;
    }

    private boolean handleSpawn(CommandSender sender, boolean isSet, String[] rest) {
        if (!isSet) {
            Location loc = currentSpawn();
            sender.sendMessage("§e当前登录地点: §a" + loc.getWorld().getName()
                    + " §ex:§a" + loc.getBlockX()
                    + " §ey:§a" + loc.getBlockY()
                    + " §ez:§a" + loc.getBlockZ());
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c不能在控制台使用这个指令");
            return true;
        }
        Config.Settings.SpawnLocation = ((Player) sender).getLocation();
        Config.Settings.save();
        sender.sendMessage("§e已设置玩家登陆坐标为你站着的位置");
        return true;
    }

    // ------------------------------------------------------------------
    // 直接指令
    // ------------------------------------------------------------------

    private boolean doTeleportSpawn(CommandSender sender, String[] rest) {
        if (rest.length < 1 || !rest[0].equalsIgnoreCase("Spawn")) {
            sender.sendMessage("§c用法: /logyee tp Spawn");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c不能在控制台使用这个指令");
            return true;
        }
        ((Player) sender).teleport(currentSpawn());
        sender.sendMessage("§e已将你传送到登录地点");
        return true;
    }

    private boolean doRemovePlayer(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§c用法: /logyee rmplayer|rmp [玩家名]");
            return true;
        }
        String name = args[0];
        LoginPlayer lp = Cache.getIgnoreCase(name);
        if (lp != null) {
            Logyee.instance.runTaskAsync(() -> {
                try {
                    Logyee.sql.del(lp.getName());
                    LoginPlayerHelper.remove(lp);
                    Bukkit.getScheduler().runTask(Logyee.instance, () -> {
                        sender.sendMessage("§e已删除账户 §a" + lp.getName());
                        Player p = Bukkit.getPlayerExact(lp.getName());
                        if (p != null && p.isOnline()) {
                            p.kickPlayer("§c你的账户已被删除!");
                        }
                    });
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Logyee.instance, () -> sender.sendMessage("§c数据库异常!"));
                    e.printStackTrace();
                }
            });
        } else {
            sender.sendMessage(String.format("§c账户 §a%s §c不存在", name));
        }
        return true;
    }

    private boolean doSetPassword(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§c用法: /logyee setpwd|sp [玩家名] [密码]");
            return true;
        }
        String name = args[0], pwd = args[1];
        if (!Util.passwordIsDifficulty(pwd)) {
            sender.sendMessage("§c密码必须是6~16位之间的数字和字母组成");
            return true;
        }
        sender.sendMessage("§e设置中..");
        Logyee.instance.runTaskAsync(() -> {
            LoginPlayer lp = Cache.getIgnoreCase(name);
            if (lp == null) {
                lp = new LoginPlayer(name, pwd);
                lp.crypt();
                try {
                    Logyee.sql.add(lp);
                    Bukkit.getScheduler().runTask(Logyee.instance, () -> sender.sendMessage("§a指定账户不存在,现已注册.."));
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Logyee.instance, () -> sender.sendMessage("§c数据库异常!"));
                    e.printStackTrace();
                }
            } else {
                final LoginPlayer finalLp = lp;
                LoginPlayer updated = new LoginPlayer(finalLp.getName(), pwd);
                updated.crypt();
                updated.setEmail(finalLp.getEmail());
                updated.setIps(finalLp.getIps());
                try {
                    Logyee.sql.edit(updated);
                    LoginPlayerHelper.remove(finalLp);
                    Bukkit.getScheduler().runTask(Logyee.instance, () -> {
                        sender.sendMessage(String.join(" ", "§a玩家", finalLp.getName(), "密码已设置"));
                        Player p = Bukkit.getPlayerExact(finalLp.getName());
                        if (p != null && p.isOnline()) {
                            p.sendMessage("§c密码已被管理员重新设置,请重新登录");
                            if (Config.Settings.CanTpSpawnLocation) {
                                p.teleport(Config.Settings.SpawnLocation);
                                if (Logyee.loadProtocolLib) {
                                    LoginPlayerHelper.sendBlankInventoryPacket(p);
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    Bukkit.getScheduler().runTask(Logyee.instance, () -> sender.sendMessage("§c数据库异常!"));
                    e.printStackTrace();
                }
            }
        });
        return true;
    }

    private boolean doReload(CommandSender sender) {
        Config.reload();
        SQL.RW_LOCK.writeLock().lock();
        try {
            if (Logyee.sql != null) {
                Logyee.sql.close();
            }
            Logyee.sql = Config.MySQL.Enable ? new top.zhrhello.logyee.bukkit.database.MySQL(Logyee.instance)
                    : new top.zhrhello.logyee.bukkit.database.SQLite(Logyee.instance);
            Logyee.sql.init();
            Cache.refreshAll();
        } catch (Exception e) {
            Logyee.instance.getLogger().warning("§c加载数据库时出错");
            e.printStackTrace();
        } finally {
            SQL.RW_LOCK.writeLock().unlock();
        }

        Communication.socketServerStopAsync();
        if (Config.BungeeCord.Enable) {
            Communication.socketServerStartAsync();
        }
        sender.sendMessage("配置已重载!");
        return true;
    }

    // ------------------------------------------------------------------
    // 辅助方法
    // ------------------------------------------------------------------

    private static Location currentSpawn() {
        Location l = Config.Settings.SpawnLocation;
        if (l != null) return l;
        return Bukkit.getWorlds().isEmpty() ? new Location(null, 0, 0, 0)
                : Bukkit.getWorlds().get(0).getSpawnLocation();
    }

    private static Boolean parseOnOff(String s) {
        switch (s.toLowerCase(LOCALE)) {
            case "on":
            case "true":
            case "1":
            case "yes":
                return Boolean.TRUE;
            case "off":
            case "false":
            case "0":
            case "no":
                return Boolean.FALSE;
            default:
                return null;
        }
    }

    private static String onOff(boolean b) {
        return b ? "§a开启" : "§8关闭";
    }

    private static String[] subArgs(String[] args, int from) {
        if (from >= args.length) return new String[0];
        String[] n = new String[args.length - from];
        System.arraycopy(args, from, n, 0, n.length);
        return n;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§d§lLogyee 管理指令 (主命令可缩写 /ly):");
        sender.sendMessage("§a/logyee set/see cmdwhitelist|cw add|rm [指令] §9登录前允许执行的指令(支持正则)");
        sender.sendMessage("§a/logyee set/see ipreg|ir [数量] §9相同ip注册限制(默认2)");
        sender.sendMessage("§a/logyee set/see ipjoin|ij [数量] §9相同ip登录限制(默认2)");
        sender.sendMessage("§a/logyee set/see name|nm least|long [值] §9游戏名最小/最大长度(默认2/15)");
        sender.sendMessage("§a/logyee set/see reconnect|re [间隔] §9离开服务器重进间隔tick(默认60)");
        sender.sendMessage("§a/logyee set/see autokick|ak [秒数] §9自动踢出未登录(默认120,<1关闭)");
        sender.sendMessage("§a/logyee set/see/tp Spawn §9登录地点(默认world出生点)");
        sender.sendMessage("§a/logyee set/see chineseid|cnid on/off §9限制中文游戏名(默认开)");
        sender.sendMessage("§a/logyee set/see nodamage|nd on/off §9登录前免伤(默认开)");
        sender.sendMessage("§a/logyee set/see alb|afterloginback on/off §9登录后回退出点(默认开)");
        sender.sendMessage("§a/logyee set/see forceloginlocation|fsl on/off §9登录前强制在登录点(默认开)");
        sender.sendMessage("§a/logyee set/see deathrecord|dr on/off §9死亡退出记录位置(默认开)");
        sender.sendMessage("§e/logyee rmplayer|rmp [玩家名] §9管理员强制删除账户");
        sender.sendMessage("§e/logyee setpwd|sp [玩家名] [密码] §9管理员强制设置密码");
        sender.sendMessage("§3/logyee reload|re §9重载配置文件");
    }

    // ------------------------------------------------------------------
    // Tab 补全
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> res = new ArrayList<>();
        if (args.length == 1) {
            addMatching(res, new String[]{"set", "see", "tp", "reload", "re", "rmplayer", "rmp", "setpwd", "sp"}, args[0]);
            return res;
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("tp")) {
                addMatching(res, new String[]{"Spawn"}, args[1]);
                return res;
            }
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("see")) {
                addMatching(res, new String[]{
                        "cmdwhitelist", "cw", "ipreg", "ir", "ipjoin", "ij", "name", "nm",
                        "reconnect", "re", "autokick", "ak", "Spawn", "chineseid", "cnid",
                        "nodamage", "nd", "alb", "afterloginback", "forceloginlocation", "fsl",
                        "deathrecord", "dr"
                }, args[1]);
                return res;
            }
            return res;
        }
        if (args.length == 3) {
            String t = args[1].toLowerCase(LOCALE);
            if (args[0].equalsIgnoreCase("set")) {
                if (t.equals("cmdwhitelist") || t.equals("cw")) {
                    addMatching(res, new String[]{"add", "rm"}, args[2]);
                } else if (t.equals("name") || t.equals("nm")) {
                    addMatching(res, new String[]{"least", "long"}, args[2]);
                } else if (isOnOffSetting(t)) {
                    addMatching(res, new String[]{"on", "off"}, args[2]);
                }
            }
            return res;
        }
        return res;
    }

    private static boolean isOnOffSetting(String t) {
        return t.equals("chineseid") || t.equals("cnid")
                || t.equals("nodamage") || t.equals("nd")
                || t.equals("alb") || t.equals("afterloginback")
                || t.equals("forceloginlocation") || t.equals("fsl")
                || t.equals("deathrecord") || t.equals("dr");
    }

    private static void addMatching(List<String> out, String[] candidates, String prefix) {
        String p = prefix.toLowerCase(LOCALE);
        for (String c : candidates) {
            if (c.startsWith(p)) out.add(c);
        }
    }
}
