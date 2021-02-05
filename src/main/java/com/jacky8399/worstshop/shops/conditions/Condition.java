package com.jacky8399.worstshop.shops.conditions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.shops.ParseContext;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

public abstract class Condition implements Predicate<Player> {
    public static Condition fromMap(Config yaml) {
        Optional<String> logicOptional = yaml.find("logic", String.class);
        if (logicOptional.isPresent()) {
            String logic = logicOptional.get();
            if (logic.equals("not")) {
                try {
                    Condition negate = fromMap(yaml.get("condition", Config.class));
                    return negate.negate();
                } catch (ConfigException ex) {
                    throw new RuntimeException("Logical NOT condition must have an accompanying 'condition'", ex);
                }
            } else {
                try {
                    BinaryOperator<Condition> accumulator = logic.equals("or") ? Condition::or : Condition::and;
                    List<? extends Config> children = yaml.getList("conditions", Config.class);
                    Optional<Condition> result = children.stream().map(Condition::fromMap).reduce(accumulator);
                    return result.orElse(ConditionConstant.TRUE); // always true if no elements
                } catch (ConfigException ex) {
                    throw new RuntimeException("Logical " + logic.toUpperCase() + " condition must have an accompanying 'conditions' list", ex);
                }
            }
        }
        Optional<Object> preset = yaml.find("preset", String.class, Boolean.class);
        if (!preset.isPresent()) {
            // compatibility with ShopWants
            WorstShop.get().logger.warning("Using cost & rewards as conditions is deprecated. Please add 'preset: commodity' before it.");
            WorstShop.get().logger.warning("Offending condition is in " + ParseContext.getHierarchy());
            return new ConditionShopWants(yaml);
        }
        // thanks YAML
        if (preset.get() instanceof Boolean) {
             return ConditionConstant.valueOf((Boolean) preset.get());
        }

        switch ((String) preset.get()) {
            case "commodity":
                return new ConditionShopWants(yaml);
            case "placeholder":
                return new ConditionPlaceholder(yaml);
            case "permission":
                return ConditionPermission.fromPermString(yaml.get("permission", String.class));
            case "true":
            case "false":
                return ConditionConstant.valueOf(Boolean.parseBoolean((String) preset.get()));
            default:
                throw new IllegalArgumentException("Unknown condition preset " + preset);
        }
    }

    public abstract Map<String, Object> toMap(Map<String, Object> map);

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
