package com.jacky8399.worstshop.shops.conditions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

/**
 * Represents a player predicate
 */
public abstract class Condition implements Predicate<Player> {
    public static Condition fromShorthand(String string) {
        if (ConditionPlaceholder.SHORTHAND_PATTERN.matcher(string).matches()) {
            return new ConditionShorthand(string, ConditionPlaceholder.fromShorthand(string));
        }
        return new ConditionShorthand(string, ConditionPermission.fromPermString(string));
    }

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
                    List<Condition> children = yaml.getList("conditions", Condition.class);
                    Optional<Condition> result = children.stream().reduce(accumulator);
                    return result.orElse(ConditionConstant.TRUE); // always true if no elements
                } catch (ConfigException ex) {
                    throw new RuntimeException("Logical " + logic.toUpperCase() + " condition must have an accompanying 'conditions' list", ex);
                }
            }
        } else if (yaml.has("not") || yaml.has("and") || yaml.has("or")) {
            Optional<Condition> notOptional = yaml.find("not", Condition.class);
            if (notOptional.isPresent()) {
                try {
                    Condition negate = notOptional.get();
                    return negate.negate();
                } catch (IllegalArgumentException ex) {
                    throw new ConfigException("Invalid 'not' block", yaml, "not", ex);
                }
            } else {
                try {
                    List<Condition> listOptional = yaml.getList(yaml.has("and") ? "and" : "or", Condition.class);
                    BinaryOperator<Condition> accumulator = yaml.has("and") ? Condition::and : Condition::or;
                    return listOptional.stream().reduce(accumulator).orElse(ConditionConstant.TRUE);
                } catch (IllegalArgumentException ex) {
                    String block = yaml.has("and") ? "and" : "or";
                    throw new ConfigException("Invalid '" + block + "'", yaml, block, ex);
                }
            }
        }
        Optional<Object> preset = yaml.find("preset", String.class, Boolean.class);
        if (preset.isEmpty()) {
            // compatibility with Commodity
            WorstShop.get().logger.warning("Using cost & rewards as conditions is deprecated. Please add 'preset: commodity' before it.");
            WorstShop.get().logger.warning("Offending condition is in " + yaml.getPath());
            return new ConditionCommodity(yaml);
        }
        // thanks YAML
        if (preset.get() instanceof Boolean bool) {
             return ConditionConstant.valueOf(bool);
        }

        return switch ((String) preset.get()) {
            case "commodity" -> new ConditionCommodity(yaml);
            case "placeholder" -> new ConditionPlaceholder(yaml);
            case "permission" -> new ConditionPermission(yaml.get("permission", String.class));
            case "true", "false" -> ConditionConstant.valueOf(Boolean.parseBoolean((String) preset.get()));
            default -> throw new IllegalArgumentException("Unknown condition preset " + preset.get());
        };
    }

    public abstract Map<String, Object> toMap(Map<String, Object> map);

    public final Object toMapObject() {
        if (this instanceof ConditionShorthand shorthand) {
            return shorthand.shorthand;
        } else {
            return toMap(new HashMap<>());
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
