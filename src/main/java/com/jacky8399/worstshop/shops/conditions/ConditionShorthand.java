package com.jacky8399.worstshop.shops.conditions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ConditionShorthand extends Condition {
    public final String shorthand;
    public final Condition inner;
    public ConditionShorthand(String shorthand, Condition inner) {
        this.shorthand = shorthand;
        this.inner = inner;
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        return inner.toMap(map);
    }

    @Override
    public @NotNull Condition and(@NotNull Condition condition) {
        return inner.and(condition);
    }

    @Override
    public @NotNull Condition negate() {
        return inner.negate();
    }

    @Override
    public @NotNull Condition or(@NotNull Condition condition) {
        return inner.or(condition);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof ConditionShorthand shorthand && shorthand.inner.equals(inner)) || inner.equals(obj);
    }

    @Override
    public String toString() {
        return inner.toString();
    }

    @Override
    public boolean test(Player player) {
        return inner.test(player);
    }
}
