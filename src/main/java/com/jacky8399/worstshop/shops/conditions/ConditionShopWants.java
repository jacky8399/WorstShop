package com.jacky8399.worstshop.shops.conditions;

import com.jacky8399.worstshop.shops.wants.ShopWants;
import org.bukkit.entity.Player;

import java.util.Map;

public class ConditionShopWants extends Condition {
    public final ShopWants want;
    public ConditionShopWants(Map<String, Object> yaml) {
        want = ShopWants.fromMap(yaml);
    }

    public ConditionShopWants(ShopWants want) {
        this.want = want;
    }

    @Override
    public boolean test(Player player) {
        return want.canAfford(player);
    }
}
