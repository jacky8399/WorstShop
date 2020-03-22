package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopManager;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.Optional;

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
        InventoryContents contents = WorstShop.get().inventories.getContents(player).orElseThrow(()->new IllegalStateException(player.getName() + " is not in shop inventory?"));
        Shop shop = ShopManager.SHOPS.get(this.shop);
        if (shop == null)
            throw new IllegalArgumentException(this.shop + " does not exist");
        if (skipPermission || shop.canPlayerView(player)) {
            contents.setProperty("skipOnce", true);
            shop.getInventory(player).open(player);
        }
    }
}
