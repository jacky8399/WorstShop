package com.jacky8399.worstshop.shops.conditions;

import com.google.common.collect.Lists;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ConditionOr extends Condition {
    private final ArrayList<Condition> conditions;
    public ConditionOr(Condition... conditions) {
        this(Arrays.asList(conditions));
    }

    public ConditionOr(Collection<? extends Condition> conditions) {
        this.conditions = Lists.newArrayList();
        this.conditions.addAll(conditions);
    }

    public void addCondition(@NotNull Condition condition) {
        this.conditions.add(condition);
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
    public String toString() {
        return "(" + conditions.stream().map(Condition::toString).collect(Collectors.joining(" | ")) + ")";
    }

    @Override
    public boolean test(Player player) {
        return conditions.stream().anyMatch(c -> c.test(player));
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("logic", "or");
        map.put("conditions", conditions.stream().map(condition -> condition.toMap(new HashMap<>())).collect(Collectors.toList()));
        return map;
    }
}
