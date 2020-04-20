package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import fr.minuskube.inv.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ActionClose extends Action {

    boolean noParent;

    public ActionClose(Config yaml) {
        super(yaml);
        noParent = yaml.get("preset", String.class).equalsIgnoreCase("close");
    }

    private static final InventoryManager MANAGER = WorstShop.get().inventories;
    public static void closeInv(Player player, boolean noParent) {
        MANAGER.getContents(player).ifPresent(inventory -> {
            inventory.setProperty("noParent", noParent);
            Bukkit.getScheduler().runTaskLater(WorstShop.get(), ()->inventory.inventory().close(player), 1);
        });
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        closeInv((Player) e.getWhoClicked(), noParent);
    }
}
