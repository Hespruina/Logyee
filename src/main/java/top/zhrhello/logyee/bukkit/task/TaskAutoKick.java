package top.zhrhello.logyee.bukkit.task;

import top.zhrhello.logyee.bukkit.Config;
import top.zhrhello.logyee.bukkit.database.Cache;
import top.zhrhello.logyee.bukkit.object.LoginPlayerHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

public class TaskAutoKick extends Task {
    public Map<String, Long> playerJoinTime = new HashMap<>();

    @Override
    public void run(){
        if (!Cache.isLoaded || Config.Settings.AutoKick < 1) return;
        long autoKickMs = Config.Settings.AutoKick * 1000;
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerName = player.getName();
            if (!LoginPlayerHelper.isLogin(playerName)) {
                if (playerJoinTime.containsKey(playerName)) {
                    if (now - playerJoinTime.get(playerName) > autoKickMs) {
                        player.kickPlayer(Config.Language.AUTO_KICK.replace("{time}", Config.Settings.AutoKick + ""));
                    }
                } else {
                    playerJoinTime.put(playerName, now);
                }
            } else {
                playerJoinTime.remove(playerName);
            }
        }
    }
}
