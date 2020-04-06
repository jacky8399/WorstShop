package com.jacky8399.worstshop.shops.conditions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ConditionNot extends Condition {
    private Condition condition;
    public ConditionNot(Condition condition) {
        this.condition = condition;
    }

    @NotNull
    @Override
    public Condition negate() {
        return condition;
    }

    @Override
    public boolean test(Player player) {
        return !condition.test(player);
    }

    @Override
    public String toString() {
        return "!" + condition;
    }
}
