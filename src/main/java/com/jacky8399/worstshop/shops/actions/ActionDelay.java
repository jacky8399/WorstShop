package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.ImmutableList;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.helper.DateTimeUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Delays execution of actions contained.
 */
public class ActionDelay extends Action {
    public final int delay;
    public final List<Action> actions;
    public ActionDelay(Config yaml) {
        super(yaml);
        Object delayInput = yaml.get("delay", Integer.class, String.class);
        if (delayInput instanceof Integer num) {
            delay = num;
        } else {
            Duration duration = DateTimeUtils.parseTimeStr(delayInput.toString());
            delay = (int) (duration.getSeconds() * 20 + duration.getNano() / ChronoUnit.MILLIS.getDuration().getNano() / 50);
        }
        if (delay <= 0)
            throw new ConfigException("delay cannot be less than 0", yaml, "delay");
        actions = yaml.getList("actions", Config.class).stream()
                .map(Action::fromConfig)
                .collect(ImmutableList.toImmutableList());
    }

    public ActionDelay(int delay, List<Action> actions) {
        super(null);
        if (delay <= 0)
            throw new IllegalArgumentException("delay cannot be less than 0");
        this.delay = delay;
        this.actions = actions;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Bukkit.getScheduler().runTaskLater(WorstShop.get(), () ->
                actions.forEach(action -> {
                    if (action.shouldTrigger(e))
                        action.onClick(e);
                }), delay);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "delay");
        map.put("delay", delay);
        map.put("actions", actions.stream().map(action -> action.toMap(new HashMap<>())).collect(Collectors.toList()));
        return map;
    }
}
