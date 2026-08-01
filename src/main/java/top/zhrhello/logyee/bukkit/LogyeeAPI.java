package top.zhrhello.logyee.bukkit;

import top.zhrhello.logyee.bukkit.database.Cache;
import top.zhrhello.logyee.bukkit.object.LoginPlayer;
import top.zhrhello.logyee.bukkit.object.LoginPlayerHelper;
import top.zhrhello.logyee.util.Crypt;

public class LogyeeAPI {
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

    /**
     * 标记玩家为已登录状态（恢复登录态，用于跨线换服后重建 Logyee 登录态）
     * <p>内部调用 LoginPlayerHelper.add(lp, true)，notifyBungee=true 会自动通过
     * Communication.notifyPlayerLogin 发送 PLAYER_LOGIN 给 velocity 端。
     * <p>不触发 LogyeePlayerLoginEvent，不记录 IP，不传送，不发消息——
     * 这是"恢复登录态"而非"新登录"。
     *
     * @param name 玩家名（大小写不敏感）
     * @return 0=成功（已写入并通知）; 1=未注册（Cache 无此玩家）; 2=已登录（幂等，无需操作）
     */
    public static int markLoggedIn(String name) {
        Logyee.instance.getLogger().info("[LogyeeAPI] markLoggedIn called: player=" + name);
        if (name == null || name.isEmpty()) {
            Logyee.instance.getLogger().info("[LogyeeAPI] markLoggedIn: name null/empty → 返回 1");
            return 1;
        }
        // 幂等检查：已登录则不重复通知，避免重复发 PLAYER_LOGIN
        if (LoginPlayerHelper.isLogin(name)) {
            Logyee.instance.getLogger().info("[LogyeeAPI] markLoggedIn: " + name + " 已登录(幂等) → 返回 2");
            return 2;
        }
        LoginPlayer lp = Cache.getIgnoreCase(name);
        if (lp == null) {
            Logyee.instance.getLogger().info("[LogyeeAPI] markLoggedIn: " + name + " 未注册 → 返回 1");
            return 1;
        }
        // notifyBungee=true → Communication.notifyPlayerLogin → PLAYER_LOGIN 给 velocity
        LoginPlayerHelper.add(lp, true);
        Logyee.instance.getLogger().info("[LogyeeAPI] markLoggedIn: " + name + " 已写入 set, notifyBungee=true → 返回 0");
        return 0;
    }
}
