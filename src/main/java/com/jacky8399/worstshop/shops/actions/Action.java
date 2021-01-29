package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigHelper;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public abstract class Action implements Cloneable {
    EnumSet<ClickType> triggerOnClick = EnumSet.allOf(ClickType.class);
    public Action(Config yaml) {
        if (yaml == null) {
            return;
        }
        Optional<String> click = yaml.tryFind("click", String.class);
        if (click.isPresent()) {
            triggerOnClick = matchShopTrigger(click.get());
        } else {
            // don't allow '*' here
            yaml.findList("click", ClickType.class).ifPresent(triggerOnClick::addAll);
        }
    }

    private EnumSet<ClickType> matchShopTrigger(String input) {
        if (input.equals("*")) {
            return EnumSet.allOf(ClickType.class);
        } else {
            return EnumSet.of(ConfigHelper.parseEnum(input, ClickType.class));
        }
    }


    public static Action fromShorthand(String input) {
        String classSpecifier = input.substring(0, input.indexOf("!"))
                .replace(' ', '_').toLowerCase();
        String classArgument = input.substring(input.indexOf("!") + 1).trim();
        switch (classSpecifier) {
            case "item_shop":
                return new ActionItemShop(classArgument);
            case "open":
                return new ActionOpen(classArgument);
            default:
                throw new IllegalArgumentException(classSpecifier + " is not a valid shorthand!");
        }
    }

    public static ActionCustom fromCommand(String command) {
        return new ActionCustom(Collections.singletonList(command));
    }

    public static Action fromConfig(Config yaml) {
        Optional<String> presetOptional = yaml.find("preset", String.class);
        return presetOptional.map(preset -> {
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
        }).orElseGet(() -> new ActionCustom(yaml));
    }


    public boolean shouldTrigger(InventoryClickEvent e) {
        return triggerOnClick.contains(e.getClick());
    }

    public void influenceItem(Player player, final ItemStack readonlyStack, ItemStack stack) {

    }

    public abstract void onClick(InventoryClickEvent e);

    public abstract Map<String, Object> toMap(Map<String, Object> map);

    public Action clone() {
        try {
            return (Action) super.clone();
        } catch (CloneNotSupportedException ex) {
            return null;
        }
    }
}
