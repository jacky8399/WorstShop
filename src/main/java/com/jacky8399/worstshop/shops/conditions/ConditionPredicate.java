package com.jacky8399.worstshop.shops.conditions;

import org.bukkit.entity.Player;

import java.util.function.Predicate;

public class ConditionPredicate extends Condition {
    private Predicate<Player> predicate;
    public ConditionPredicate(Predicate<Player> playerPredicate) {
        predicate = playerPredicate;
    }

    @Override
    public boolean test(Player player) {
        return predicate.test(player);
    }
}
