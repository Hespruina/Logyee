package top.zhrhello.logyee.bukkit.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;


public class LogyeePlayerRegisterEvent extends Event {
    private static final HandlerList handlers = new HandlerList();

    public HandlerList getHandlers(){
        return handlers;
    }

    public static HandlerList getHandlerList(){
        return handlers;
    }

    public LogyeePlayerRegisterEvent(Player player){
        this.player = player;
    }


    private Player player;

    public Player getPlayer(){
        return player;
    }


}
