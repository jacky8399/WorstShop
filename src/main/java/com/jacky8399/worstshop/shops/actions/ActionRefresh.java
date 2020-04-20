package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.shops.Shop;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ActionRefresh extends Action {
    public ActionRefresh(Config yaml) {
        super(yaml);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        WorstShop.get().inventories.getContents(player).ifPresent(contents -> {
            SmartInventory inv = contents.inventory();
            if (inv.getProvider() instanceof Shop) {
                ((Shop) inv.getProvider()).refreshItems(player, contents, true);
            }
        });
    }
}
