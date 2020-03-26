package com.jacky8399.worstshop.shops.actions;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public class ShopAction {

    //List<InventoryAction> triggerOnAction;
    EnumSet<ClickType> triggerOnClick;
    public ShopAction(Map<String, Object> yaml) {
        triggerOnClick = EnumSet.noneOf(ClickType.class);
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

    private void matchShopTrigger(String input) {
        switch (input) {
            case "*":
            case "all":
            case "any":
                triggerOnClick = EnumSet.allOf(ClickType.class);
                break;
            default:
                triggerOnClick.add(ClickType.valueOf(input.replace(' ', '_').toUpperCase()));
                break;
        }
    }


    public static ShopAction fromShorthand(String input) {
        String classSpecifier = input.substring(0, input.indexOf("!"))
                .replace(' ', '_').toLowerCase();
        String classArgument = input.substring(input.indexOf("!") + 1);
        switch (classSpecifier) {
            case "item_shop":
                return new ActionItemShop(classArgument.trim());
            case "open":
                return new ActionOpen(classArgument.trim());
        }
        return null;
    }

    public static ActionCustom fromCommand(String command) {
        return new ActionCustom(Collections.singletonList(command));
    }

    public static ShopAction fromYaml(Map<String, Object> yaml) {
        String preset = (String) yaml.get("preset");
        if (preset != null) {
            // ULTIMATE SHORTCUT
            if (preset.contains("!")) {
                return fromShorthand(preset);
            }

            switch (preset.replace(' ', '_').toLowerCase()) {
                case "shop":
                    return new ActionShop(yaml);
                case "transaction":
                    return new ActionTransaction(yaml);
                case "item_shop":
                    return new ActionItemShop(yaml);
                case "take":
                    return new ActionTake(yaml);
                case "open":
                    return new ActionOpen(yaml);
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


    public boolean shouldTrigger(InventoryClickEvent e) {
        return triggerOnClick.contains(e.getClick());
    }

    public void influenceItem(Player player, ItemStack readonlyStack, ItemStack stack) {

    }

    public void onClick(InventoryClickEvent e) {

    }
}
