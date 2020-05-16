package com.jacky8399.worstshop.shops.conditions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.shops.ShopManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Predicate;

public abstract class Condition implements Predicate<Player> {
    public static Condition fromMap(Map<String, Object> yaml) {
        String preset = (String) yaml.get("preset");
        if (preset == null) {
            // compatibility with ShopWants
            WorstShop.get().logger.warning("Using cost & rewards as conditions is deprecated. Please add 'preset: commodity' before it.");
            WorstShop.get().logger.warning("Offending condition is in " + ShopManager.currentShopId);
            return new ConditionShopWants(yaml);
        }
        switch (preset) {
            case "commodity":
                return new ConditionShopWants(yaml);
            case "placeholder":
                return new ConditionPlaceholder(yaml);
            case "permission":
                return ConditionPermission.fromPermString((String) yaml.get("permission"));
            case "true":
            case "false":
                return ConditionConstant.valueOf(Boolean.parseBoolean(preset));
            default:
                throw new IllegalArgumentException("Unknown condition preset " + preset);
        }
    }

    @NotNull
    @Override
    public Predicate<Player> and(@NotNull Predicate<? super Player> other) {
        return other instanceof Condition ? and((Condition) other) : Predicate.super.and(other);
    }

    @NotNull
    public Condition and(@NotNull Condition condition) {
        return new ConditionAnd(this, condition);
    }

    @NotNull
    @Override
    public Condition negate() {
        return new ConditionNot(this);
    }

    @NotNull
    @Override
    public Predicate<Player> or(@NotNull Predicate<? super Player> other) {
        return other instanceof Condition ? or((Condition) other) : Predicate.super.or(other);
    }

    @NotNull
    public Condition or(@NotNull Condition condition) {
        return new ConditionOr(this, condition);
    }
}
