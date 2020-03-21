package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.shops.wants.ShopWants;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A more robust condition system intended to replace view-perm.
 */
public class ShopCondition implements Predicate<Player> {
    private ArrayList<ShopWants> conditions = Lists.newArrayList();

    @Override
    public boolean test(Player player) {
        for (ShopWants cond : conditions) {
            if (!cond.canAfford(player)) {
                return false;
            }
        }
        return true;
    }

    public ShopCondition() {

    }

    public void add(ShopWants cond) {
        conditions.add(cond);
    }

    public void addAll(ShopWants... conds) {
        for (ShopWants cond : conds) {
            add(cond);
        }
    }

    public void addAllFromYaml(List<Map<String, Object>> yaml) {
        for (Map<String, Object> child : yaml) {
            if (child.containsKey("behavior")) {

            }
        }
    }
}
