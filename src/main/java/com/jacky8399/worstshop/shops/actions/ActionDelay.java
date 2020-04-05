package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import org.bukkit.Bukkit;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ActionDelay extends Action {
    int delay = 0;
    List<Action> actions;
    public ActionDelay(Map<String, Object> yaml) {
        super(yaml);
        delay = ((Number) yaml.get("delay")).intValue();
        actions = ((List<Map<String, Object>>) yaml.get("actions")).stream()
                .map(Action::fromYaml).collect(Collectors.toList());
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
