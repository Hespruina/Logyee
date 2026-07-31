package cc.baka9.catseedlogin.velocity;

import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Velocity 端事件监听器
 * <p>
 * 核心区别：使用 Velocity 原生 {@link CommandExecuteEvent} 拦截指令，
 * 该事件在所有命令执行路径上触发（代理命令 + 转发到后端的命令），
 * 不依赖 BungeeCord 的 ChatEvent，从根本上解决 Snap 环境下的结构性失效。
 */
public class Listeners {

    private static final List<String> loggedInPlayerList = new ArrayList<>();

    /**
     * 默认命令白名单：插件 Bukkit 端自带的登录、注册、忘记密码等指令及其别名。
     */
    private static final Set<String> DEFAULT_COMMAND_WHITELIST = new HashSet<>(Arrays.asList(
            "login", "l",
            "register", "reg",
            "bindemail", "bdmail",
            "resetpassword", "repw",
            "changepassword", "changepw"
    ));

    private static void log(String message) {
        PluginMain.getInstance().getLogger().info("[Listener] " + message);
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
     * 【核心拦截】登录之前只能执行白名单指令。
     * <p>
     * 使用 Velocity 原生 {@link CommandExecuteEvent}，该事件对代理命令和
     * 转发到后端服务器的命令都会触发，不存在 BungeeCord ChatEvent 的
     * isProxyCommand() 盲区问题。
     * <p>
     * PostOrder.FIRST 确保在所有其他插件之前拦截。
     */
    @Subscribe(order = PostOrder.FIRST)
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;

        Player player = (Player) event.getCommandSource();
        String playerName = player.getUsername();

        // 已登录玩家放行所有指令
        if (isLoggedIn(playerName)) return;

        // Velocity 的 CommandExecuteEvent.getCommand() 不含前导 /
        String command = event.getCommand();
        String commandName = command.trim().split("\\s+")[0].toLowerCase();
        // 去掉命名空间前缀（如 minecraft:ban -> ban）
        int colonIndex = commandName.indexOf(':');
        if (colonIndex > 0) {
            commandName = commandName.substring(colonIndex + 1);
        }

        // 白名单内的指令放行
        if (isCommandWhitelisted(commandName)) return;

        // 非白名单指令且未登录 —— 拦截
        event.setResult(CommandExecuteEvent.CommandResult.denied());
        log("onCommand: blocked '" + command + "' from " + playerName
                + " (not logged in, not in whitelist)");
        player.sendMessage(Component.text("\u00a7c\u8bf7\u5148\u767b\u5f55\u540e\u518d\u6267\u884c\u6b64\u6307\u4ee4\uff01"));

        // 异步向 Bukkit 查询登录状态，防止玩家刚在子服登录但 Velocity 尚未同步
        final String originalCommand = command;
        PluginMain.runAsync(() -> {
            try {
                int result = Communication.sendConnectRequest(playerName);
                log("onCommand: CONNECT result for " + playerName + " = " + result);
                if (result == 1) {
                    markLoggedIn(playerName);
                    // 重新执行被拦截的指令
                    // CommandExecuteEvent.denied() 会阻止原始执行，
                    // 确认登录后通过命令管理器重新派发
                    PluginMain.getInstance().getProxy().getCommandManager()
                            .executeAsync(player, originalCommand);
                }
            } catch (Exception e) {
                log("onCommand: error during CONNECT request for " + playerName + ": " + e.getMessage());
            }
        });
    }

    /**
     * 玩家切换到登录服后，如果 Velocity 端是已登录状态，
     * 使用该状态更新子服的登录状态，避免每次切服重新登录。
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        if (event.getServer().getServerInfo().getName().equals(Config.LoginServerName)) {
            String playerName = event.getPlayer().getUsername();
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
     * 玩家离线时，清除 Velocity 端的登录状态。
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        String playerName = event.getPlayer().getUsername();
        log("onDisconnect: " + playerName);
        markLoggedOut(playerName);
    }

    /**
     * 玩家进入代理前，检查 Velocity 端和子服的登录状态，
     * 如果其中一项是已登录，则拒绝连接（防止重复登录）。
     */
    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String playerName = event.getUsername();
        boolean loggedIn = isLoggedIn(playerName);
        log("onPreLogin: " + playerName + ", loggedIn=" + loggedIn);
        if (loggedIn) {
            log("onPreLogin: rejecting " + playerName + " (already logged in on Velocity)");
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.empty()));
            return;
        }
        int result = Communication.sendConnectRequest(playerName);
        log("onPreLogin: CONNECT result for " + playerName + " = " + result);
        if (result == 1) {
            log("onPreLogin: rejecting " + playerName + " (already logged in on Bukkit)");
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(Component.empty()));
        }
    }
}
