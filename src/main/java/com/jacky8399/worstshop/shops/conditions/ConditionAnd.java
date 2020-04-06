package com.jacky8399.worstshop.shops.conditions;

import com.google.common.collect.Lists;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class ConditionAnd extends Condition {
    private ArrayList<Condition> conditions;
    public ConditionAnd(Condition... conditions) {
        this(Arrays.asList(conditions));
    }

    public ConditionAnd(Collection<? extends Condition> conditions) {
        this.conditions = Lists.newArrayList();
        this.conditions.addAll(conditions);
    }

    @Override
    public boolean test(Player player) {
        return conditions.stream().allMatch(c -> c.test(player));
    }
}
