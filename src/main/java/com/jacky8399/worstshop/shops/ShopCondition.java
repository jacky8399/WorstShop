package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.shops.wants.ShopWants;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;

/**
 * A more robust condition system intended to replace view-perm.
 */
public class ShopCondition implements Predicate<Player> {
    private ArrayList<Predicate<Player>> conditions = Lists.newArrayList();

    @Override
    public boolean test(Player player) {
        for (Predicate<Player> cond : conditions) {
            if (!cond.test(player)) {
                return false;
            }
        }
        return true;
    }

    public ShopCondition() {

    }

    public void add(Predicate<Player> cond) {
        conditions.add(cond);
    }

    public void addAll(Predicate<Player>... conds) {
        for (Predicate<Player> cond : conds) {
            add(cond);
        }
    }

    @SuppressWarnings("unchecked")
    public static Predicate<Player> parseFromYaml(Map<String, Object> yaml) {
        if (yaml.containsKey("logic")) {
            String logic = yaml.get("logic").toString();
            if (logic.equals("not")) {
                try {
                    Predicate<Player> negate = parseFromYaml((Map<String, Object>) yaml.get("condition"));
                    return negate.negate();
                } catch (ClassCastException | NullPointerException ex) {
                    throw new RuntimeException("'logic: not' must have an accompanying 'condition'", ex);
                }
            } else {
                try {
                    BinaryOperator<Predicate<Player>> accumulator = logic.equals("or") ? Predicate::or : Predicate::and;
                    List<Map<String, Object>> children = (List<Map<String, Object>>)
                            yaml.getOrDefault("conditions", yaml.get("condition")); // allow both
                    Optional<Predicate<Player>> result = children.stream().map(ShopCondition::parseFromYaml).reduce(accumulator);
                    return result.orElse(p -> true); // always true if no elements
                } catch (ClassCastException | NullPointerException ex) {
                    throw new RuntimeException("'logic: and/or' must have an accompanying 'conditions' list", ex);
                }
            }
        }
        return ShopWants.fromMap(yaml);
    }
}
