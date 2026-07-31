package top.zhrhello.logyee.bukkit.command;

import top.zhrhello.logyee.bukkit.Config;
import top.zhrhello.logyee.bukkit.database.Cache;
import top.zhrhello.logyee.bukkit.event.LogyeePlayerLoginEvent;
import top.zhrhello.logyee.bukkit.object.LoginPlayer;
import top.zhrhello.logyee.bukkit.object.LoginPlayerHelper;
import top.zhrhello.logyee.util.Crypt;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Objects;

public class CommandLogin implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String lable, String[] args){
        if (args.length == 0 || !(sender instanceof Player)) return false;
        Player player = (Player) sender;
        String name = player.getName();
        if (LoginPlayerHelper.isLogin(name)) {
            sender.sendMessage(Config.Language.LOGIN_REPEAT);
            return true;
        }
        LoginPlayer lp = Cache.getIgnoreCase(name);
        if (lp == null) {
            sender.sendMessage(Config.Language.LOGIN_NOREGISTER);
            return true;
        }
        if (Objects.equals(Crypt.encrypt(name, args[0]), lp.getPassword().trim())) {
            LoginPlayerHelper.add(lp);
            LogyeePlayerLoginEvent loginEvent = new LogyeePlayerLoginEvent(player, lp.getEmail(), LogyeePlayerLoginEvent.Result.SUCCESS);
            Bukkit.getServer().getPluginManager().callEvent(loginEvent);
            sender.sendMessage(Config.Language.LOGIN_SUCCESS);
            player.updateInventory();
            LoginPlayerHelper.recordCurrentIP(player, lp);
            if (Config.Settings.AfterLoginBack && Config.Settings.CanTpSpawnLocation) {
                Config.getOfflineLocation(player).ifPresent(player::teleport);
            }
        } else {
            sender.sendMessage(Config.Language.LOGIN_FAIL);
            LogyeePlayerLoginEvent loginEvent = new LogyeePlayerLoginEvent(player, lp.getEmail(), LogyeePlayerLoginEvent.Result.FAIL);
            Bukkit.getServer().getPluginManager().callEvent(loginEvent);
            if (Config.EmailVerify.Enable) {
                sender.sendMessage(Config.Language.LOGIN_FAIL_IF_FORGET);
            }
        }
        return true;
    }
}
