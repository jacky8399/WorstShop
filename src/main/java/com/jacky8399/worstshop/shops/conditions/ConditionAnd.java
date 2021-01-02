package com.jacky8399.worstshop.shops.conditions;

import com.google.common.collect.Lists;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class ConditionAnd extends Condition {
    private final ArrayList<Condition> conditions;
    public ConditionAnd(Condition... conditions) {
        this(Arrays.asList(conditions));
    }

    public ConditionAnd(Collection<? extends Condition> conditions) {
        this.conditions = new ArrayList<>(conditions);
    }

    public void addCondition(@NotNull Condition condition) {
        this.conditions.add(condition);
    }

    public boolean removeCondition(@NotNull Condition condition) {
        return this.conditions.remove(condition);
    }

    @NotNull
    @Override
    public Condition and(@NotNull Condition condition) {
        if (condition instanceof ConditionAnd) {
            ArrayList<Condition> newCondition = Lists.newArrayList(conditions);
            newCondition.addAll(((ConditionAnd) condition).conditions);
            return new ConditionAnd(newCondition);
        }
        ArrayList<Condition> newCondition = Lists.newArrayList(conditions);
        newCondition.add(condition);
        return new ConditionAnd(newCondition);
    }

    @Override
    public boolean test(Player player) {
        return conditions.stream().allMatch(c -> c.test(player));
    }

    @Override
    public String toString() {
        return "(" + conditions.stream().map(Condition::toString).collect(Collectors.joining(" & ")) + ")";
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("logic", "and");
        map.put("conditions", conditions.stream().map(condition->condition.toMap(new HashMap<>())).collect(Collectors.toList()));
        return map;
    }
}
