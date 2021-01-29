package com.jacky8399.worstshop.events;

import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.PlayerInventory;

public class IllegalShopItemListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() instanceof PlayerInventory) {
            Player player = ((Player) e.getWhoClicked());
            if (e.getCurrentItem() != null && StaticShopElement.isShopItem(e.getCurrentItem())) {
                e.setCurrentItem(null);
                player.updateInventory();
            }
            if (e.getCursor() != null && StaticShopElement.isShopItem(e.getCursor())) {
                player.setItemOnCursor(null);
                player.updateInventory();
            }
        }
    }

    @EventHandler
    public void onMoveItem(InventoryMoveItemEvent e) {
        if (e.getInitiator() instanceof PlayerInventory) {
            PlayerInventory inv = (PlayerInventory) e.getInitiator();
            if (StaticShopElement.isShopItem(e.getItem()) && inv.getHolder() != null) {
                e.setCancelled(true);
                ((Player) inv.getHolder()).updateInventory();
            }
        }
    }
}
