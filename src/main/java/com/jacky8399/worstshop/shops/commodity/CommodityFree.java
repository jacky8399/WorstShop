package com.jacky8399.worstshop.shops.commodity;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public class CommodityFree extends Commodity {
    public CommodityFree() {
        super();
    }

    public static final CommodityFree INSTANCE = new CommodityFree();

    @Override
    public boolean canAfford(Player player) {
        return true;
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        return position.createElement(ItemBuilder.of(Material.BARRIER)
                .name(I18n.translate("worstshop.messages.shops.wants.free")).build());
    }

    @Override
    public int getMaximumMultiplier(Player player) {
        return Integer.MAX_VALUE;
    }

    @Override
    public int getMaximumPurchase(Player player) {
        return Integer.MAX_VALUE;
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "free");
        return map;
    }

    @Override
    public String toString() {
        return "[free]";
    }
}
