package top.zhrhello.logyee.bukkit.command;

import top.zhrhello.logyee.bukkit.Logyee;
import top.zhrhello.logyee.bukkit.Config;
import top.zhrhello.logyee.bukkit.database.Cache;
import top.zhrhello.logyee.bukkit.event.LogyeePlayerRegisterEvent;
import top.zhrhello.logyee.bukkit.object.LoginPlayer;
import top.zhrhello.logyee.bukkit.object.LoginPlayerHelper;
import top.zhrhello.logyee.util.Util;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

public class CommandRegister implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String lable, String[] args){
        if (args.length != 2 || !(sender instanceof Player)) return false;
        Player player = (Player) sender;
        String name = sender.getName();
        if (LoginPlayerHelper.isLogin(name)) {
            sender.sendMessage(Config.Language.REGISTER_AFTER_LOGIN_ALREADY);
            return true;
        }
        if (LoginPlayerHelper.isRegister(name)) {
            sender.sendMessage(Config.Language.REGISTER_BEFORE_LOGIN_ALREADY);
            return true;
        }
        if (!args[0].equals(args[1])) {
            sender.sendMessage(Config.Language.REGISTER_PASSWORD_CONFIRM_FAIL);
            return true;
        }
        if (!Util.passwordIsDifficulty(args[0])) {
            sender.sendMessage(Config.Language.COMMON_PASSWORD_SO_SIMPLE);
            return true;
        }
        if (!Cache.isLoaded) {
            return true;
        }
        sender.sendMessage("§e注册中..");
        final String currentIp;
        try {
            if (player.getAddress() == null) {
                sender.sendMessage("§c无法获取你的IP地址!");
                return true;
            }
            currentIp = player.getAddress().getAddress().getHostAddress();
        } catch (Exception e) {
            sender.sendMessage("§c无法获取你的IP地址!");
            return true;
        }
        final java.util.UUID playerUuid = player.getUniqueId();
        Logyee.instance.runTaskAsync(() -> {
            try {
                List<LoginPlayer> LoginPlayerListlikeByIp = Logyee.sql.getLikeByIp(currentIp);
                if (LoginPlayerListlikeByIp.size() >= Config.Settings.IpRegisterCountLimit) {
                    Bukkit.getScheduler().runTask(Logyee.instance, () ->
                            sender.sendMessage(Config.Language.REGISTER_MORE
                                    .replace("{count}", String.valueOf(LoginPlayerListlikeByIp.size()))
                                    .replace("{accounts}", String.join(", ", LoginPlayerListlikeByIp.stream().map(LoginPlayer::getName).toArray(String[]::new)))));
                } else {
                    LoginPlayer lp = new LoginPlayer(name, args[0]);
                    lp.crypt();
                    Logyee.sql.add(lp);
                    Bukkit.getScheduler().runTask(Logyee.instance, () -> {
                        Player syncPlayer = Bukkit.getPlayer(playerUuid);
                        if (syncPlayer != null && syncPlayer.isOnline()) {
                            LoginPlayerHelper.add(lp);
                            LogyeePlayerRegisterEvent event = new LogyeePlayerRegisterEvent(syncPlayer);
                            Bukkit.getServer().getPluginManager().callEvent(event);
                            syncPlayer.sendMessage(Config.Language.REGISTER_SUCCESS);
                            syncPlayer.updateInventory();
                            LoginPlayerHelper.recordCurrentIP(syncPlayer, lp);
                        }
                    });
                }


            } catch (Exception e) {
                e.printStackTrace();
                Bukkit.getScheduler().runTask(Logyee.instance, () -> sender.sendMessage("§c服务器内部错误!"));
            }
        });
        return true;

    }
}
