package top.zhrhello.logyee.bukkit;

import top.zhrhello.logyee.bukkit.object.LoginPlayerHelper;
import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

public class ProtocolLibListeners extends PacketAdapter {

    public static void enable(){
        ProtocolManager protocolManager = ProtocolLibrary.getProtocolManager();
        protocolManager.addPacketListener(new ProtocolLibListeners());
    }


    public ProtocolLibListeners(){
        super(Logyee.instance, ListenerPriority.HIGHEST,
                PacketType.Play.Server.SET_SLOT,
                PacketType.Play.Server.WINDOW_ITEMS
        );

    }

    @Override
    public void onPacketSending(PacketEvent event){
        PacketType packetType = event.getPacketType();
        if (packetType == PacketType.Play.Server.SET_SLOT || packetType == PacketType.Play.Server.WINDOW_ITEMS) {
            Player player = event.getPlayer();
            if (player == null || event.isPlayerTemporary()) return;
            PacketContainer packet = event.getPacket();
            if (packet.getIntegers().size() == 0) return;
            int windowId = packet.getIntegers().read(0);
            if (windowId == 0 && !LoginPlayerHelper.isLogin(player.getName())) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onPacketReceiving(PacketEvent event){
    }


}
