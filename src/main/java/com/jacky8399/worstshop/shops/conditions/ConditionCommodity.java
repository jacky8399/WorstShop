package com.jacky8399.worstshop.shops.conditions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.commodity.Commodity;
import com.jacky8399.worstshop.shops.commodity.IUnaffordableCommodity;
import org.bukkit.entity.Player;

import java.util.Map;

public class ConditionCommodity extends Condition {
    public final Commodity want;
    public ConditionCommodity(Config config) {
        this(Commodity.fromMap(config));
        if (want instanceof IUnaffordableCommodity) {
            WorstShop.get().logger.warning("Using " + want.getClass().getSimpleName() + " makes the condition always fail!");
            WorstShop.get().logger.warning("Offending condition: " + ParseContext.getHierarchy());
        }
    }

    public ConditionCommodity(Commodity want) {
        this.want = want;
    }

    @Override
    public boolean test(Player player) {
        return want.canAfford(player);
    }

    @Override
    public String toString() {
        return want.getPlayerResult(null, Commodity.TransactionType.COST);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "commodity");
        want.toMap(map);
        return map;
    }

    @Override
    public int hashCode() {
        return want.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ConditionCommodity && ((ConditionCommodity) obj).want.equals(want);
    }
}
