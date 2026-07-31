package cc.baka9.catseedlogin.bungee;

import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.Connection;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.*;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bungee Cord 监听事件类
 */
public class Listeners implements Listener {

    private static final List<String> loggedInPlayerList = new ArrayList<>();

    /**
     * 默认命令白名单：插件 Bukkit 端自带的登录、注册、忘记密码等指令及其别名。
     * 未登录玩家只能执行这些指令，其他所有指令（包括 Bungee 代理指令）一律拦截。
     */
    private static final Set<String> DEFAULT_COMMAND_WHITELIST = new HashSet<>(Arrays.asList(
            "login", "l",
            "register", "reg",
            "bindemail", "bdmail",
            "resetpassword", "repw",
            "changepassword", "changepw"
    ));

    private static void log(String message) {
        PluginMain.instance.getLogger().info("[Listener] " + message);
    }

    public static void markLoggedIn(String playerName) {
        synchronized (loggedInPlayerList) {
            if (!loggedInPlayerList.contains(playerName)) {
                loggedInPlayerList.add(playerName);
                log("markLoggedIn: " + playerName + " added to list");
            }
        }
    }

    public static void markLoggedOut(String playerName) {
        synchronized (loggedInPlayerList) {
            loggedInPlayerList.remove(playerName);
            log("markLoggedOut: " + playerName + " removed from list");
        }
    }

    public static boolean isLoggedIn(String playerName) {
        synchronized (loggedInPlayerList) {
            return loggedInPlayerList.contains(playerName);
        }
    }

    /**
     * 检查指令名是否在白名单中（默认白名单 + 配置文件中的额外白名单）。
     */
    private static boolean isCommandWhitelisted(String commandName) {
        String lower = commandName.toLowerCase();
        if (DEFAULT_COMMAND_WHITELIST.contains(lower)) {
            return true;
        }
        if (Config.CommandWhitelist != null) {
            for (String entry : Config.CommandWhitelist) {
                if (entry != null && entry.toLowerCase().equals(lower)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 登录之前只能执行白名单指令（登录、注册、忘记密码等），
     * 禁止执行其他所有指令——包括 Bungee 代理指令（如 /ban）和转发到子服的指令。
     * 这防止了未登录玩家通过 Bungee 端指令绕过权限的严重安全问题。
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(ChatEvent event) {
        Connection sender = event.getSender();
        if (!(sender instanceof ProxiedPlayer)) return;

        String message = event.getMessage();
        // 只拦截指令（以 / 开头的消息），不拦截普通聊天
        if (!message.startsWith("/")) return;

        ProxiedPlayer proxiedPlayer = (ProxiedPlayer) sender;
        String playerName = proxiedPlayer.getName();

        // 已登录玩家放行所有指令
        if (isLoggedIn(playerName)) return;

        // 提取指令名（去掉 / 后的第一个词）
        String commandBody = message.substring(1).trim();
        String commandName = commandBody.split("\\s+")[0].toLowerCase();
        // 去掉命名空间前缀（如 minecraft:ban -> ban）
        int colonIndex = commandName.indexOf(':');
        if (colonIndex > 0) {
            commandName = commandName.substring(colonIndex + 1);
        }

        // 白名单内的指令放行（如 /login、/register 等）
        if (isCommandWhitelisted(commandName)) return;

        // 非白名单指令且未登录 —— 拦截
        event.setCancelled(true);
        log("onChat: blocked command '" + message + "' from " + playerName
                + " (not logged in, not in whitelist)");
        proxiedPlayer.sendMessage(new TextComponent("§c请先登录后再执行此指令！"));

        // 异步向 Bukkit 查询登录状态，防止玩家刚在子服登录但 Bungee 尚未同步的情况
        final boolean isProxyCommand = event.isProxyCommand();
        PluginMain.runAsync(() -> {
            try {
                int result = Communication.sendConnectRequest(playerName);
                log("onChat: CONNECT result for " + playerName + " = " + result);
                if (result == 1) {
                    markLoggedIn(playerName);
                    // 代理指令（Bungee 指令）重新派发
                    if (isProxyCommand) {
                        PluginMain.instance.getProxy().getPluginManager()
                                .dispatchCommand(proxiedPlayer, message.substring(1));
                    }
                    // 非代理指令（转发到子服的指令）需要玩家重新输入，
                    // 因为无法在 Bungee 层面直接重发到子服
                }
            } catch (Exception e) {
                log("onChat: error during CONNECT request for " + playerName + ": " + e.getMessage());
            }
        });
    }

    /**
     * 玩家切换到登录服务之后，如果bc端是已登录的状态，就使用bc端的登录状态去更新子服的登录状态，
     * 避免使玩家每次切换到登录服时需要重新进行登录
     */
    @EventHandler
    public void onServerConnected(ServerConnectedEvent event) {
        if (event.getServer().getInfo().getName().equals(Config.LoginServerName)) {
            ProxiedPlayer player = event.getPlayer();
            String playerName = player.getName();

            PluginMain.runAsync(() -> {
                boolean loggedIn = isLoggedIn(playerName);
                log("onServerConnected: " + playerName + " on login server, loggedIn=" + loggedIn);
                if (loggedIn) {
                    Communication.sendKeepLoggedInRequest(playerName);
                }

            });
        }

    }

    /**
     * 玩家离线时，删除玩家在bc端的登录状态
     */
    @EventHandler
    public void onPlayerDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String playerName = player.getName();
        log("onPlayerDisconnect: " + playerName);
        markLoggedOut(playerName);
    }

    /**
     * 玩家在登录之前，检查bc端和子服的登录状态，如果是其中一项是已登录，则禁止连接
     */
    @EventHandler
    public void onPreLogin(PreLoginEvent event) {
        String playerName = event.getConnection().getName();
        boolean loggedIn = isLoggedIn(playerName);
        log("onPreLogin: " + playerName + ", loggedIn=" + loggedIn);
        if (loggedIn) {
            log("onPreLogin: rejecting " + playerName + " (already logged in on Bungee)");
            event.setCancelReason(new TextComponent(""));
            event.setCancelled(true);
            return;
        }
        int result = Communication.sendConnectRequest(playerName);
        log("onPreLogin: CONNECT result for " + playerName + " = " + result);
        if (result == 1) {
            log("onPreLogin: rejecting " + playerName + " (already logged in on Bukkit)");
            event.setCancelReason(new TextComponent(""));
            event.setCancelled(true);
        }

    }


}