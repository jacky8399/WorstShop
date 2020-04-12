package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.Optional;

public class ActionClose extends Action {

    boolean noParent;

    public ActionClose(Map<String, Object> yaml) {
        super(yaml);
        noParent = yaml.get("preset").equals("close");
    }

    public static void closeInv(Player player, boolean noParent) {
        Optional<InventoryContents> inv = WorstShop.get().inventories.getContents(player);

        inv.ifPresent(inventory->{
            inventory.setProperty("noParent", noParent);
            Bukkit.getScheduler().runTaskLater(WorstShop.get(), ()->inventory.inventory().close(player), 1);
        });
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        closeInv((Player) e.getWhoClicked(), noParent);
    }
}
