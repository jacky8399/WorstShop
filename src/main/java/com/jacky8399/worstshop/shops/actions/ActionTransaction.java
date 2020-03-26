package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.shops.wants.ShopWants;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;

public class ActionTransaction extends ActionShop {
    public ActionTransaction(Map<String, Object> yaml) {
        super(yaml);
    }

    public ActionTransaction(ShopWants cost, ShopWants reward) {
        super(cost, reward);
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        if (cost.canAfford(player)) {
            double refund = reward.grantOrRefund(player);
            cost.multiply(refund).grantOrRefund(player);
        }
    }
}
