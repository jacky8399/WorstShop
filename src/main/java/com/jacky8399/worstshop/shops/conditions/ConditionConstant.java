package com.jacky8399.worstshop.shops.conditions;

import org.bukkit.entity.Player;

public class ConditionConstant extends Condition {
    public final boolean value;
    private ConditionConstant(boolean v) {
        value = v;
    }

    public static final ConditionConstant TRUE = new ConditionConstant(true);
    public static final ConditionConstant FALSE = new ConditionConstant(false);

    public static ConditionConstant valueOf(boolean constant) {
        return constant ? TRUE : FALSE;
    }

    @Override
    public boolean test(Player player) {
        return value;
    }
}
