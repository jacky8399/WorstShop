package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.Config;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

public abstract class Action {

    //List<InventoryAction> triggerOnAction;
    EnumSet<ClickType> triggerOnClick = EnumSet.allOf(ClickType.class);
    public Action(Config yaml) {
        if (yaml == null) {
            return;
        }
        yaml.find("click", List.class, String.class).ifPresent(obj -> {
            triggerOnClick.clear();
            if (obj instanceof List<?>)
                ((List<String>) obj).forEach(this::matchShopTrigger);
            else if (obj instanceof String)
                matchShopTrigger((String) obj);
        });
    }

    private void matchShopTrigger(String input) {
        if (input.equals("*")) {
            triggerOnClick = EnumSet.allOf(ClickType.class);
        } else {
            ClickType type = ClickType.valueOf(input.replace(' ', '_').toUpperCase());
            if (!triggerOnClick.add(type))
                throw new IllegalStateException(type.name() + " is already bound to!");
        }
    }


    public static Action fromShorthand(String input) {
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

    public static Action fromYaml(Map<String, Object> map) {
        String preset = (String) map.get("preset");
        Config yaml = new Config(map);
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
                case "delay":
                    return new ActionDelay(yaml);
                case "book":
                    return new ActionBook(yaml);
                default:
                    throw new IllegalArgumentException(preset + " is not a valid preset!");
            }
        } else {
            return new ActionCustom(yaml);
        }
    }


    public boolean shouldTrigger(InventoryClickEvent e) {
        return triggerOnClick.contains(e.getClick());
    }

    public void influenceItem(Player player, final ItemStack readonlyStack, ItemStack stack) {

    }

    public abstract void onClick(InventoryClickEvent e);
}
