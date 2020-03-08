package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.Optional;

public class ActionClose extends ShopAction {

    boolean noParent;

    public ActionClose(Map<String, Object> yaml) {
        super(yaml);
        noParent = yaml.get("preset").equals("close");
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        Optional<InventoryContents> inv = WorstShop.get().inventories.getContents(player);

        inv.ifPresent(inventory->{
            inventory.setProperty("noParent", noParent);
            inventory.inventory().close(player);
        });
    }
}
