package cc.baka9.catseedlogin.bukkit;

import cc.baka9.catseedlogin.bukkit.database.Cache;
import cc.baka9.catseedlogin.bukkit.object.LoginPlayer;
import cc.baka9.catseedlogin.bukkit.object.LoginPlayerHelper;
import cc.baka9.catseedlogin.util.Crypt;

public class CatSeedLoginAPI {
    public static boolean isLogin(String name){
        return LoginPlayerHelper.isLogin(name);
    }

    public static boolean isRegister(String name){
        return LoginPlayerHelper.isRegister(name);
    }

    /**
     * 验证玩家密码是否正确
     * @param name 玩家名
     * @param password 明文密码
     * @return true 表示密码正确，false 表示玩家未注册或密码错误
     */
    public static boolean verifyPassword(String name, String password){
        if (name == null || password == null) {
            return false;
        }
        // 检查玩家是否已注册
        LoginPlayer lp = Cache.getIgnoreCase(name);
        if (lp == null) {
            return false;
        }
        // 计算密码哈希并比对
        String encrypted = Crypt.encrypt(name, password);
        if (encrypted == null) {
            return false;
        }
        return encrypted.equals(lp.getPassword());
    }
}
