package cc.baka9.catseedlogin.velocity;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Velocity 端配置加载。
 * <p>
 * 使用 SnakeYAML 解析 velocity.yml，字段与 BungeeCord 端 bungeecord.yml 完全一致。
 * SnakeYAML 被 shade 打包并 relocate 到 cc.baka9.catseedlogin.libs.snakeyaml，
 * 不会与服务器自带的 YAML 库冲突。
 */
public class Config {

    public static boolean Enable;
    public static String Host;
    public static int Port;
    public static String LoginServerName;
    public static String AuthKey;
    /**
     * 额外命令白名单（从配置文件读取），与默认白名单合并使用。
     */
    public static List<String> CommandWhitelist;

    @SuppressWarnings("unchecked")
    public static void load() {
        File dataFolder = PluginMain.getInstance().getDataDirectory().toFile();
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }

        String fileName = "velocity.yml";
        File configFile = new File(dataFolder, fileName);
        if (!configFile.exists()) {
            try (InputStream in = PluginMain.class.getResourceAsStream("/velocity-resources/velocity.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        try (InputStream in = Files.newInputStream(configFile.toPath())) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(in);
            if (config == null) {
                setDefaults();
                return;
            }
            Enable = getBoolean(config, "Enable", true);
            Host = getString(config, "Host", "127.0.0.1");
            Port = getInt(config, "Port", 2333);
            LoginServerName = getString(config, "LoginServerName", "lobby");
            AuthKey = getString(config, "AuthKey", "");

            Object whitelist = config.get("CommandWhitelist");
            if (whitelist instanceof List) {
                CommandWhitelist = new ArrayList<>();
                for (Object item : (List<?>) whitelist) {
                    if (item != null) {
                        CommandWhitelist.add(String.valueOf(item));
                    }
                }
            } else {
                CommandWhitelist = new ArrayList<>();
            }

            PluginMain.getInstance().getLogger().info("Host: {}", Host);
            PluginMain.getInstance().getLogger().info("Port: {}", Port);
            PluginMain.getInstance().getLogger().info("LoginServerName: {}", LoginServerName);
            PluginMain.getInstance().getLogger().info("CommandWhitelist: {}", CommandWhitelist);
        } catch (Exception e) {
            e.printStackTrace();
            setDefaults();
        }
    }

    private static void setDefaults() {
        Enable = true;
        Host = "127.0.0.1";
        Port = 2333;
        LoginServerName = "lobby";
        AuthKey = "";
        CommandWhitelist = new ArrayList<>();
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean def) {
        Object val = map.get(key);
        return val instanceof Boolean ? (Boolean) val : def;
    }

    private static String getString(Map<String, Object> map, String key, String def) {
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : def;
    }

    private static int getInt(Map<String, Object> map, String key, int def) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return def;
    }
}
