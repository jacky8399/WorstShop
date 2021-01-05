package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.Config;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Optional;

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
        e.getWhoClicked().setItemOnCursor(current);
        if (unlimited) {
            e.setCurrentItem(Optional.ofNullable(current).map(ItemStack::clone).orElse(null));
        }
        if (e.getWhoClicked() instanceof Player) {
            ((Player) e.getWhoClicked()).updateInventory();
        }
    }
}
