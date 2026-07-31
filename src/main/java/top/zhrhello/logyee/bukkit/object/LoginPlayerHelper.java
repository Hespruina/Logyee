package top.zhrhello.logyee.bukkit.object;

import top.zhrhello.logyee.bukkit.Logyee;
import top.zhrhello.logyee.bukkit.database.Cache;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.StructureModifier;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

public class LoginPlayerHelper {
    private static final Set<LoginPlayer> set = new HashSet<>();

    public static List<LoginPlayer> getList(){
        return new ArrayList<>(set);
    }

    public static void add(LoginPlayer lp){
        add(lp, true);
    }

    public static void add(LoginPlayer lp, boolean notifyBungee){
        synchronized (set) {
            set.add(lp);
        }
        if (notifyBungee) {
            top.zhrhello.logyee.bukkit.Communication.notifyPlayerLogin(lp.getName());
        }
    }

    public static void remove(LoginPlayer lp){
        remove(lp, true);
    }

    public static void remove(LoginPlayer lp, boolean notifyBungee){
        synchronized (set) {
            set.remove(lp);
        }
        if (notifyBungee) {
            top.zhrhello.logyee.bukkit.Communication.notifyPlayerLogout(lp.getName());
        }
    }

    public static void remove(String name){
        remove(name, true);
    }

    public static void remove(String name, boolean notifyBungee){
        synchronized (set) {
            for (LoginPlayer lp : set) {
                if (lp.getName().equals(name)) {
                    set.remove(lp);
                    break;
                }
            }
        }
        if (notifyBungee) {
            top.zhrhello.logyee.bukkit.Communication.notifyPlayerLogout(name);
        }
    }

    public static boolean isLogin(String name){
        synchronized (set) {
            for (LoginPlayer lp : set) {
                if (lp.getName().equals(name)) {
                    return true;
                }
            }
            return false;
        }
    }

    public static boolean isRegister(String name){

        return Cache.getIgnoreCase(name) != null;

    }

    // 记录登录IP
    public static void recordCurrentIP(Player player, LoginPlayer lp){
        String currentIp = player.getAddress().getAddress().getHostAddress();
        List<String> ipsList = lp.getIpsList();
        ipsList.add(currentIp);
        ipsList = ipsList.stream().distinct().collect(Collectors.toList());
        if (ipsList.size() > 5) {
            ipsList.remove(0);
        }
        lp.setIps(String.join(";", ipsList.toArray(new String[0])));
        Logyee.instance.runTaskAsync(() -> {
            try {
                Logyee.sql.edit(lp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ProtocolLib发包空背包
    public static void sendBlankInventoryPacket(Player player){
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        PacketContainer inventoryPacket = protocolManager.createPacket(PacketType.Play.Server.WINDOW_ITEMS);
        inventoryPacket.getIntegers().write(0, 0);
        int inventorySize = 45;

        ItemStack[] blankInventory = new ItemStack[inventorySize];
        Arrays.fill(blankInventory, new ItemStack(Material.AIR));


        StructureModifier<ItemStack[]> itemArrayModifier = inventoryPacket.getItemArrayModifier();
        if (itemArrayModifier.size() > 0) {
            itemArrayModifier.write(0, blankInventory);
        } else {

            StructureModifier<List<ItemStack>> itemListModifier = inventoryPacket.getItemListModifier();
            itemListModifier.write(0, Arrays.asList(blankInventory));
        }

        protocolManager.sendServerPacket(player, inventoryPacket, false);
    }
}
