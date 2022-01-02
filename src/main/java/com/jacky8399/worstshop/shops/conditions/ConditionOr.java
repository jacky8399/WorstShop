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
        this.conditions = new ArrayList<>(conditions);
    }

    public void addCondition(@NotNull Condition condition) {
        this.conditions.add(condition);
    }

    public void mergeCondition(@NotNull Condition condition) {
        if (condition instanceof ConditionOr) {
            conditions.addAll(((ConditionOr) condition).conditions);
        } else {
            conditions.add(condition);
        }
    }

    public boolean removeCondition(@NotNull Condition condition) {
        return this.conditions.remove(condition);
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    @NotNull
    @Override
    public ConditionOr or(@NotNull Condition other) {
        ArrayList<Condition> newConditions = Lists.newArrayList(conditions);
        if (other instanceof ConditionOr) {
            newConditions.addAll(((ConditionOr) other).conditions);
        } else {
            newConditions.add(other);
        }
        return new ConditionOr(newConditions);
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
//        map.put("logic", "or");
//        map.put("conditions", conditions.stream().map(condition -> condition.toMap(new HashMap<>())).collect(Collectors.toList()));
        map.put("or", conditions.stream().map(Condition::toMapObject).collect(Collectors.toList()));
        return map;
    }

    @Override
    public int hashCode() {
        return conditions.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ConditionOr && ((ConditionOr) obj).conditions.equals(conditions);
    }
}
