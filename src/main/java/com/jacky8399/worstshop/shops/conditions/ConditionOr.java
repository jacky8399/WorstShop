package com.jacky8399.worstshop.shops.conditions;

import com.google.common.collect.Lists;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

public class ConditionOr extends Condition {
    private ArrayList<Condition> conditions;
    public ConditionOr(Condition... conditions) {
        this(Arrays.asList(conditions));
    }

    public ConditionOr(Collection<? extends Condition> conditions) {
        this.conditions = Lists.newArrayList();
        this.conditions.addAll(conditions);
    }

    @NotNull
    @Override
    public ConditionOr or(@NotNull Condition other) {
        if (other instanceof ConditionOr) {
            ArrayList<Condition> newConditions = Lists.newArrayList(conditions);
            newConditions.addAll(((ConditionOr) other).conditions);
            return new ConditionOr(newConditions);
        } else {
            ArrayList<Condition> newConditions = Lists.newArrayList(conditions);
            newConditions.add(other);
            return new ConditionOr(newConditions);
        }
    }

    @Override
    public boolean test(Player player) {
        return conditions.stream().anyMatch(c -> c.test(player));
    }
}
