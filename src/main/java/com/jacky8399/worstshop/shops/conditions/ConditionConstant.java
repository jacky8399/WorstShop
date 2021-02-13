package com.jacky8399.worstshop.shops.conditions;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

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

    @Override
    public @NotNull Condition and(@NotNull Condition condition) {
        return value ? condition : FALSE;
    }

    @Override
    public @NotNull Condition negate() {
        return value ? FALSE : TRUE;
    }

    @Override
    public @NotNull Condition or(@NotNull Condition condition) {
        return value ? TRUE : condition;
    }

    @Override
    public String toString() {
        return Boolean.toString(value);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", Boolean.toString(value));
        return map;
    }

    @Override
    public int hashCode() {
        return Boolean.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ConditionConstant && ((ConditionConstant) obj).value == value;
    }
}
