package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.InventoryUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

/**
 * Closes the current GUI, usually returning to the parent GUI
 */
public class ActionClose extends Action {
    public final boolean noParent;
    public ActionClose(Config yaml) {
        super(yaml);
        noParent = yaml.get("preset", String.class).equalsIgnoreCase("close");
    }

    public ActionClose(boolean noParent) {
        super(null);
        this.noParent = noParent;
    }

    public static void closeInv(Player player, boolean noParent) {
        Bukkit.getScheduler().runTaskLater(WorstShop.get(), () -> {
            if (noParent)
                InventoryUtils.closeWithoutParent(player);
            else
                player.closeInventory();
        }, 1);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        closeInv((Player) e.getWhoClicked(), noParent);
    }


    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", noParent ? "close" : "back");
        return map;
    }
}
