package top.zhrhello.logyee.bungee;

import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Config {

    public static boolean Enable;
    public static String Host;
    public static int Port;
    public static String LoginServerName;
    public static String AuthKey;
    /**
     * 额外命令白名单（从配置文件读取），与默认白名单合并使用。
     * 未登录玩家只能执行白名单中的指令。
     */
    public static List<String> CommandWhitelist;

    public static void load() {

        File dataFolder = PluginMain.instance.getDataFolder();

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        String fileName = "bungeecord.yml";
        File configFile = new File(dataFolder, fileName);
        if (!configFile.exists()) {
            try (InputStream in = PluginMain.instance.getResourceAsStream("bungee-resources/bungeecord.yml")) {
                if (in == null) {
                    PluginMain.instance.getLogger().warning("Default bungeecord.yml not found in resources!");
                } else {
                    Files.copy(in, configFile.toPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        ConfigurationProvider configurationProvider = ConfigurationProvider.getProvider(YamlConfiguration.class);
        try {
            Configuration config = configurationProvider.load(configFile);
            Enable = config.getBoolean("Enable");
            Host = config.getString("Host");
            Port = config.getInt("Port");
            LoginServerName = config.getString("LoginServerName");
            AuthKey = config.getString("AuthKey");
            CommandWhitelist = config.getStringList("CommandWhitelist");
            if (CommandWhitelist == null) {
                CommandWhitelist = new ArrayList<>();
            }
            PluginMain.instance.getLogger().info("Host:" + Host);
            PluginMain.instance.getLogger().info("Port:" + Port);
            PluginMain.instance.getLogger().info("LoginServerName:" + LoginServerName);
            PluginMain.instance.getLogger().info("CommandWhitelist:" + CommandWhitelist);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }


}
