package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

/**
 * Forces a redraw of the shop.
 */
public class ActionRefresh extends Action {
    public ActionRefresh(Config yaml) {
        super(yaml);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        WorstShop.get().inventories.getContents(player).ifPresent(contents -> {
            SmartInventory inv = contents.inventory();
            if (inv.getProvider() instanceof ShopRenderer renderer) {
                Bukkit.getScheduler().runTaskLater(WorstShop.get(), ()->renderer.apply(player, contents), 1);
            }
        });
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "refresh");
        return map;
    }
}
