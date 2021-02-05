package com.jacky8399.worstshop.shops.conditions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ConditionNot extends Condition {
    private final Condition condition;
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

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("logic", "not");
        map.put("condition", condition.toMap(new HashMap<>()));
        return map;
    }

    @Override
    public int hashCode() {
        return 31 * condition.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ConditionNot && ((ConditionNot) obj).condition.equals(condition);
    }
}
