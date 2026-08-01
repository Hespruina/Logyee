package top.zhrhello.logyee.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

import top.zhrhello.logyee.libs.bstats.charts.SingleLineChart;
import top.zhrhello.logyee.libs.bstats.velocity.Metrics;

import java.nio.file.Path;

/**
 * Velocity 端主类
 * <p>
 * 与 BungeeCord 端功能完全对等：通过 Socket 与 Bukkit 端通讯，
 * 在 Velocity 代理层拦截未登录玩家的非白名单指令。
 * 区别在于使用 Velocity 原生 {@link com.velocitypowered.api.event.command.CommandExecuteEvent}
 * 替代 BungeeCord 的 ChatEvent，从根源上解决 Snap 环境下 ChatEvent 失效的问题。
 */
@Plugin(
        id = "logyee-velocity",
        name = "Logyee-Velocity",
        version = "1.0",
        authors = {"zhrhello"}
)
public class PluginMain {

    private static PluginMain instance;

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Metrics.Factory metricsFactory;

    @Inject
    public PluginMain(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory, Metrics.Factory metricsFactory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.metricsFactory = metricsFactory;
        instance = this;
    }

    @Subscribe
    public void onInitialize(ProxyInitializeEvent event) {
        // bStats 统计
        int pluginId = 33059;
        Metrics metrics = metricsFactory.make(this, pluginId);

        // 代理下挂的子服数量
        metrics.addCustomChart(new SingleLineChart("sub_server_count", () -> proxy.getAllServers().size()));

        Config.load();
        proxy.getEventManager().register(this, new Listeners());
        logger.info("Config.Enable = {}, Host = {}, Port = {}", Config.Enable, Config.Host, Config.Port);
        if (Config.Enable) {
            logger.info("Starting Communication...");
            Communication.start();
        } else {
            logger.warn("Velocity integration is disabled in config");
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        Communication.stop();
    }

    public ProxyServer getProxy() {
        return proxy;
    }

    public Logger getLogger() {
        return logger;
    }

    public Path getDataDirectory() {
        return dataDirectory;
    }

    public static PluginMain getInstance() {
        return instance;
    }

    /**
     * 在 Velocity 调度器上异步执行任务
     */
    public static void runAsync(Runnable runnable) {
        instance.proxy.getScheduler().buildTask(instance, runnable).schedule();
    }
}
