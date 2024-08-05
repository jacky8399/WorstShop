package com.jacky8399.worstshop.events;

import com.jacky8399.worstshop.PluginConfig;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.PlayerInventory;

public class IllegalShopItemListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!PluginConfig.advancedProtection)
            return;
        if (e.getClickedInventory() instanceof PlayerInventory) {
            Player player = ((Player) e.getWhoClicked());
            if (e.getCurrentItem() != null && StaticShopElement.isShopItem(e.getCurrentItem())) {
                WorstShop.get().logger.warning("Shop item detected in " + player.getName() + "'s inventory, removing. (" + e.getCurrentItem() + ")");
                e.setCurrentItem(null);
                player.updateInventory();
            }
            if (StaticShopElement.isShopItem(e.getCursor())) {
                WorstShop.get().logger.warning("Shop item detected in " + player.getName() + "'s inventory, removing. (" + e.getCursor() + ")");
                player.setItemOnCursor(null);
                player.updateInventory();
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMoveItem(InventoryMoveItemEvent e) {
        if (!PluginConfig.advancedProtection)
            return;
        if (e.getInitiator() instanceof PlayerInventory inv) {
            if (StaticShopElement.isShopItem(e.getItem()) && inv.getHolder() != null) {
                WorstShop.get().logger.warning("Shop item detected in " + inv.getHolder() + "'s inventory, removing. (" + e.getItem() + ")");
                e.setCancelled(true);
                ((Player) inv.getHolder()).updateInventory();
            }
        }
    }
}
