package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.InventoryCloseListener;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ActionOpen extends Action {
    boolean skipPermission = false;
    String shop;
    public ActionOpen(Config yaml) {
        super(yaml);
        yaml.find("ignore-permission", Boolean.class).ifPresent(bool -> skipPermission = bool);
        shop = yaml.get("shop", String.class);
    }

    // shortcut
    public ActionOpen(String input) {
        super(null);
        shop = input.trim();
        skipPermission = true;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        Shop shop = ShopManager.SHOPS.get(this.shop);
        if (shop == null)
            throw new IllegalArgumentException(this.shop + " does not exist");
        if (skipPermission || shop.canPlayerView(player)) {
            InventoryCloseListener.openSafely(player, shop.getInventory(player));
        }
    }
}
