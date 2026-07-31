package top.zhrhello.logyee.bukkit.task;

import top.zhrhello.logyee.bukkit.Config;
import top.zhrhello.logyee.bukkit.database.Cache;
import top.zhrhello.logyee.bukkit.object.LoginPlayerHelper;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;


public class TaskSendLoginMessage extends Task {

    @Override
    public void run(){
        if (!Cache.isLoaded) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!LoginPlayerHelper.isLogin(player.getName())) {
                if (!LoginPlayerHelper.isRegister(player.getName())) {
                    player.sendMessage(Config.Language.REGISTER_REQUEST);
                    continue;
                }
                player.sendMessage(Config.Language.LOGIN_REQUEST);

            }
        }
    }
}
