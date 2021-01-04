package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.stream.Collectors;

public class ActionDelay extends Action {
    int delay = 0;
    List<Action> actions;
    public ActionDelay(Config yaml) {
        super(yaml);
        delay = yaml.get("delay", Number.class).intValue();
        actions = yaml.getList("actions", Config.class).stream()
                .map(Action::fromConfig)
                .collect(Collectors.toList());
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Bukkit.getScheduler().runTaskLater(WorstShop.get(), () ->
                actions.forEach(action -> {
                    if (action.shouldTrigger(e))
                        action.onClick(e);
                }), delay);
    }
}
