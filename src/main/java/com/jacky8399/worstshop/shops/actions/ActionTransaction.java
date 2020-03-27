package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.PurchaseRecords;
import com.jacky8399.worstshop.shops.wants.ShopWants;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

public class ActionTransaction extends ActionShop {
    public ActionTransaction(Map<String, Object> yaml) {
        super(yaml);
    }

    public ActionTransaction(ShopWants cost, ShopWants reward, PurchaseRecords.RecordTemplate template, int purchaseLimit) {
        super(cost, reward, template, purchaseLimit);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        int maxPurchases = getPlayerMaxPurchase(player);
        if (cost.canAfford(player) && maxPurchases >= 1) {
            double refund = reward.grantOrRefund(player);
            cost.multiply(refund).grantOrRefund(player);
        }
    }
}
