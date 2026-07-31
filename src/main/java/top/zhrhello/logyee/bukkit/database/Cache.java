package top.zhrhello.logyee.bukkit.database;

import top.zhrhello.logyee.bukkit.Logyee;
import top.zhrhello.logyee.bukkit.object.LoginPlayer;

import java.util.*;

public class Cache {
    private static final Hashtable<String, LoginPlayer> PLAYER_HASHTABLE = new Hashtable<>();
    public static volatile boolean isLoaded = false;

    public static List<LoginPlayer> getAllLoginPlayer(){
        synchronized (PLAYER_HASHTABLE) {
            return new ArrayList<>(PLAYER_HASHTABLE.values());
        }

    }

    public static LoginPlayer getIgnoreCase(String name){

        return PLAYER_HASHTABLE.get(name.toLowerCase());
    }


    public static void refreshAll(){
        isLoaded = false;
        Logyee.instance.runTaskAsync(() -> {
            try {
                List<LoginPlayer> newCache = Logyee.sql.getAll();
                synchronized (PLAYER_HASHTABLE) {
                    PLAYER_HASHTABLE.clear();
                    newCache.forEach(p -> PLAYER_HASHTABLE.put(p.getName().toLowerCase(), p));
                }
                Logyee.instance.getLogger().info("缓存加载 " + PLAYER_HASHTABLE.size() + " 个数据");
                isLoaded = true;
            } catch (Exception e) {
                Logyee.instance.getLogger().warning("数据库错误,无法更新缓存!");
                e.printStackTrace();
            }
        });
    }

    public static void refresh(String name){
        Logyee.instance.runTaskAsync(() -> {
            try {
                LoginPlayer newLp = Logyee.sql.get(name);
                String key = name.toLowerCase();
                if (newLp != null) {
                    PLAYER_HASHTABLE.put(key, newLp);
                } else {
                    PLAYER_HASHTABLE.remove(key);
                }
                Logyee.instance.getLogger().info("缓存加载 " + PLAYER_HASHTABLE.size() + " 个数据");
            } catch (Exception e) {
                Logyee.instance.getLogger().warning("数据库错误,无法更新缓存!");
                e.printStackTrace();
            }
        });
    }
}