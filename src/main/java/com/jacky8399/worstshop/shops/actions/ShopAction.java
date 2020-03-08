package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ShopAction {

    //List<InventoryAction> triggerOnAction;
    Set<ClickType> triggerOnClick;
    public ShopAction(Map<String, Object> yaml) {
        triggerOnClick = Sets.newHashSet();
        if (yaml == null) {
            matchShopTrigger("*");
            return;
        }
        if (yaml.containsKey("on")) {
            ((List<String>)yaml.get("on")).forEach(this::matchShopTrigger);
        } else {
            matchShopTrigger((String) yaml.getOrDefault("on", "*"));
        }
    }

    private static final ImmutableSet<ClickType> ALL_VALUES = ImmutableSet.copyOf(ClickType.values());
    private void matchShopTrigger(String input) {
        switch (input) {
            case "*":
            case "all":
            case "any":
                triggerOnClick = ALL_VALUES;
                break;
            default:
                triggerOnClick.add(ClickType.valueOf(input.replace(' ', '_').toUpperCase()));
                break;
        }
    }

    public static class Builder {
        Map<String, Object> yaml; // parent item
        public Builder(Map<String, Object> yaml) {
            this.yaml = yaml;
        }

        public ShopAction fromShorthand(String input) {
            String classSpecifier = input.substring(0, input.indexOf("!"))
                    .replace(' ', '_').toLowerCase();
            String classArgument = input.substring(input.indexOf("!") + 1);
            switch (classSpecifier) {
                case "item_shop":
                    return new ActionItemShop(classArgument.trim(), this.yaml);
                case "open":
                    return new ActionOpen(classArgument.trim());
            }
            return null;
        }

        public ActionCustom fromCommand(String command) {
            return new ActionCustom(Collections.singletonList(command));
        }

        public ShopAction fromYaml(Map<String, Object> yaml) {
            String preset = (String) yaml.get("preset");
            if (preset != null) {
                // ULTIMATE SHORTCUT
                if (preset.contains("!")) {
                    return fromShorthand(preset);
                }

                switch (preset.replace(' ', '_').toLowerCase()) {
                    case "shop":
                        return new ActionShop(yaml, this.yaml);
                    case "item_shop":
                        return new ActionItemShop(yaml, this.yaml);
                    case "take":
                        return new ActionTake(yaml);
                    case "open":
                        return new ActionOpen(yaml, this.yaml);
                    case "previous_page":
                    case "next_page":
                        return new ActionPage(yaml);
                    case "close":
                    case "back":
                        return new ActionClose(yaml);
                    case "refresh":
                        return new ActionRefresh(yaml);
                    default:
                        return new ActionCustom(yaml);
                }
            } else {
                return new ActionCustom(yaml);
            }
        }
    }



    public boolean shouldTrigger(InventoryClickEvent e) {
        return triggerOnClick.contains(e.getClick());
    }

    public void influenceItem(Player player, ItemStack readonlyStack, ItemStack stack) {

    }

    public void onClick(InventoryClickEvent e) {

    }
}
