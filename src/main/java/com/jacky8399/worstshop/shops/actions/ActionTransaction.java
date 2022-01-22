package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.PlayerPurchaseRecords;
import com.jacky8399.worstshop.shops.commodity.Commodity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

public class ActionTransaction extends ActionShop {
    public ActionTransaction(Config yaml) {
        super(yaml);
    }

    public ActionTransaction(Commodity cost, Commodity reward, PlayerPurchaseRecords.RecordTemplate template, int purchaseLimit) {
        super(cost, reward, template, purchaseLimit);
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
        int maxPurchases = getPlayerMaxPurchase(player);
        doTransaction(player, maxPurchases);
    }
}
