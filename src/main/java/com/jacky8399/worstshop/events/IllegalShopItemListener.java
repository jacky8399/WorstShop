package com.jacky8399.worstshop.events;

import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public class IllegalShopItemListener implements Listener {
    private boolean checkItem(ItemStack stack) {
        if (StaticShopElement.isShopItem(stack)) {
            stack.setType(Material.AIR);
        }
        return false;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getClickedInventory() instanceof PlayerInventory) {
            boolean changed = checkItem(e.getCurrentItem()) | checkItem(e.getCursor());
            if (changed)
                ((Player) e.getWhoClicked()).updateInventory();
        }
    }

    @EventHandler
    public void onMoveItem(InventoryMoveItemEvent e) {
        if (e.getInitiator() instanceof PlayerInventory) {
            PlayerInventory inv = (PlayerInventory) e.getInitiator();
            boolean changed = checkItem(e.getItem());
            if (changed && inv.getHolder() != null) {
                ((Player) inv.getHolder()).updateInventory();
            }
        }
    }
}
