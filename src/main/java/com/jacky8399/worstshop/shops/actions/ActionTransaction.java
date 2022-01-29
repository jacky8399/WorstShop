package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.PlayerPurchases;
import com.jacky8399.worstshop.shops.commodity.Commodity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

/**
 * Shop but no GUI is displayed.
 * Will try to maximize the size of the transaction but can be controlled with {@link ActionTransaction#maxPurchase}.
 */
public class ActionTransaction extends ActionShop {
    public ActionTransaction(Config yaml) {
        super(yaml);
    }

    public ActionTransaction(Commodity cost, Commodity reward, PlayerPurchases.RecordTemplate template, int purchaseLimit, int maxPurchase) {
        super(cost, reward, template, purchaseLimit, maxPurchase);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        super.toMap(map);
        // override parent preset
        map.put("preset", "transaction");
        return map;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        int maxPurchases = getMaxPurchase(player);
        doTransaction(player, maxPurchases);
    }
}
