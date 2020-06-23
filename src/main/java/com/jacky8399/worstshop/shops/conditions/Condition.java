package com.jacky8399.worstshop.shops.conditions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.shops.ParseContext;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

public abstract class Condition implements Predicate<Player> {
    @SuppressWarnings("unchecked")
    public static Condition fromMap(Map<String, Object> yaml) {
        if (yaml.containsKey("logic")) {
            String logic = yaml.get("logic").toString();
            if (logic.equals("not")) {
                try {
                    Condition negate = fromMap((Map<String, Object>) yaml.get("condition"));
                    return negate.negate();
                } catch (ClassCastException | NullPointerException ex) {
                    throw new RuntimeException("Logical NOT condition must have an accompanying 'condition'", ex);
                }
            } else {
                try {
                    BinaryOperator<Condition> accumulator = logic.equals("or") ? Condition::or : Condition::and;
                    List<Map<String, Object>> children = (List<Map<String, Object>>) yaml.get("conditions");
                    Optional<Condition> result = children.stream().map(Condition::fromMap).reduce(accumulator);
                    return result.orElse(ConditionConstant.TRUE); // always true if no elements
                } catch (ClassCastException | NullPointerException ex) {
                    throw new RuntimeException("Logical " + logic.toUpperCase() + " condition must have an accompanying 'conditions' list", ex);
                }
            }
        }
        String preset = yaml.get("preset").toString();
        if (preset == null) {
            // compatibility with ShopWants
            WorstShop.get().logger.warning("Using cost & rewards as conditions is deprecated. Please add 'preset: commodity' before it.");
            WorstShop.get().logger.warning("Offending condition is in " + ParseContext.getHierarchy());
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
