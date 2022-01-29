package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.InventoryUtils;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

/**
 * Opens a shop, usually ignoring the view condition of the shop.
 */
public class ActionOpen extends Action {
    public transient boolean isShorthand = false;
    boolean skipCondition = true;
    ShopReference shop;
    public ActionOpen(Config yaml) {
        super(yaml);
        skipCondition = yaml.find("ignore-condition", Boolean.class)
                .or(()->yaml.find("ignore-permission", Boolean.class))
                .orElse(true);
        shop = ShopReference.of(yaml.get("shop", String.class));
    }

    // shortcut
    public ActionOpen(String input) {
        super(null);
        shop = ShopReference.of(input);
        skipCondition = true;
        isShorthand = true;
    }

    public ActionOpen(ShopReference shop, boolean ignoreCondition) {
        super(null);
        this.shop = shop;
        this.skipCondition = ignoreCondition;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        Shop shop = this.shop.get();
        if (skipCondition || shop.canPlayerView(player)) {
            InventoryUtils.openSafely(player, shop.getInventory(player));
        }
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "open");
        map.put("shop", shop.id);
        if (!skipCondition)
            map.put("ignore-condition", false);
        return map;
    }
}
