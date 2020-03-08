package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

public class ActionOpen extends ShopAction {
    boolean skipPermission = false;
    String shop;
    public ActionOpen(Map<String, Object> yaml, Map<String, Object> yamlParent) {
        super(yaml);
        if (yaml.containsKey("ignore-permission")) {
            skipPermission = (boolean) yaml.get("ignore-permission");
        }
        shop = (String)yaml.get("shop");
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
        if (skipPermission || ShopManager.checkPerms(player, shop)) {
            shop.getInventory(player).open(player);
        }
    }
}
