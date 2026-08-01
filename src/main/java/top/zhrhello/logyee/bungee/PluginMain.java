package top.zhrhello.logyee.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import top.zhrhello.logyee.libs.bstats.bungeecord.Metrics;
import top.zhrhello.logyee.libs.bstats.charts.SingleLineChart;

/**
 * Bungee Cord 主类
 */
public class PluginMain extends Plugin {
    public static PluginMain instance;

    @Override
    public void onEnable() {
        instance = this;

        // bStats 统计（plugin id 见 https://bstats.org/what-is-my-plugin-id ）
        // TODO: 确认这是 bStats 网站上本插件（BungeeCord 端）的真实 plugin id，33058 为示例默认值
        int pluginId = 33058;
        Metrics metrics = new Metrics(this, pluginId);

        // 代理下挂的子服数量
        metrics.addCustomChart(new SingleLineChart("sub_server_count", () -> getProxy().getServers().size()));

        Config.load();
        getProxy().getPluginManager().registerListener(this, new Listeners());
        getProxy().getPluginManager().registerCommand(this, new Commands("LogyeeBungee", "logyee.admin", "logyb"));
        getLogger().info("Config.Enable = " + Config.Enable + ", Host = " + Config.Host + ", Port = " + Config.Port);
        if (Config.Enable) {
            getLogger().info("Starting Communication...");
            Communication.start();
        } else {
            getLogger().warning("BungeeCord integration is disabled in config");
        }
    }

    @Override
    public void onDisable() {
        Communication.stop();
    }

    public static ScheduledTask runAsync(Runnable runnable) {
        return instance.getProxy().getScheduler().runAsync(instance, runnable);
    }

}
