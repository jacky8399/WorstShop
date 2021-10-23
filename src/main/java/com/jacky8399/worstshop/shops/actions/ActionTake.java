package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

public class ActionTake extends Action {
    boolean unlimited;
    public ActionTake(Config yaml) {
        super(yaml);
        unlimited = yaml.find("unlimited", Boolean.class).orElse(false);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "take");
        if (unlimited)
            map.put("unlimited", true);
        return map;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        e.setResult(Event.Result.ALLOW);
        ItemStack current = e.getCurrentItem();
        if (current != null) {
            ItemMeta meta = current.getItemMeta();
            meta.getPersistentDataContainer().remove(StaticShopElement.SAFETY_KEY);
            current.setItemMeta(meta);
        }
        Player player = (Player) e.getWhoClicked();
        ItemStack cursor = player.getItemOnCursor();
        if (cursor.getType() != Material.AIR) {
            player.getInventory().addItem(cursor);
        }
        player.setItemOnCursor(current);
        if (unlimited) {
            e.setCurrentItem(current != null ? current.clone() : null);
        }
        player.updateInventory();
    }
}
