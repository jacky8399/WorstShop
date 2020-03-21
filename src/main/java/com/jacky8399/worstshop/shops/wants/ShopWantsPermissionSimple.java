package com.jacky8399.worstshop.shops.wants;

import com.jacky8399.worstshop.helper.PermStringHelper;
import org.bukkit.entity.Player;

import java.util.function.Predicate;

// simple wrapper for view-perm like permission predicates
// only for use in ShopCondition
public class ShopWantsPermissionSimple extends ShopWants {

    private Predicate<Player> perm;
    public ShopWantsPermissionSimple(String permString) {
        perm = PermStringHelper.parsePermString(permString);
    }

    @Override
    public boolean canMultiply() {
        return false;
    }

    @Override
    public boolean canAfford(Player player) {
        return perm.test(player);
    }

    @Override
    public double grantOrRefund(Player player) {
        return 0;
    }


}
