package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Function;

/**
 * Represents an action that will occur when a player clicks on the associated ShopElement.
 */
public abstract class Action implements Cloneable {
    EnumSet<ClickType> triggerOnClick = EnumSet.allOf(ClickType.class);
    Condition condition = ConditionConstant.TRUE;

    public Action(Config yaml) {
        if (yaml == null) {
            return;
        }
        Optional<String> click = yaml.tryFind("click", String.class);
        if (click.isPresent()) {
            triggerOnClick = matchShopTrigger(click.get(), true);
        } else {
            // don't allow '*' here
            yaml.findList("click", String.class).ifPresent(list -> list.stream()
                    .flatMap(trigger -> {
                        try {
                            return matchShopTrigger(trigger, false).stream();
                        } catch (IllegalArgumentException e) {
                            throw new ConfigException("Can't find click type " + trigger, yaml, "click");
                        }
                    })
                    .forEach(triggerOnClick::add)
            );
        }
    }

    private EnumSet<ClickType> matchShopTrigger(String input, boolean canUseWildcard) {
        input = input.trim().replace(' ', '_').toLowerCase(Locale.ROOT);
        switch (input) {
            case "*":
                if (canUseWildcard)
                    return EnumSet.allOf(ClickType.class);
                return EnumSet.noneOf(ClickType.class);
            case "left_click":
                return EnumSet.of(ClickType.LEFT, ClickType.SHIFT_LEFT);
            case "right_click":
                return EnumSet.of(ClickType.RIGHT, ClickType.SHIFT_RIGHT);
            case "shift_click":
                return EnumSet.of(ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT);
            case "middle_click":
                return EnumSet.of(ClickType.MIDDLE, ClickType.CREATIVE);
        }
        return EnumSet.of(ConfigHelper.parseEnum(input, ClickType.class));
    }


    public static Action fromShorthand(String input) {
        input = input.trim();
        String[] split = input.split("!", 2);
        String classSpecifier = split[0].replace(' ', '_').toLowerCase();
        String classArgument = split[1].trim();
        return switch (classSpecifier) {
            case "item_shop" -> new ActionItemShop(classArgument);
            case "player_shop" -> ActionPlayerShopFallback.makeShorthand(classArgument);
            case "open" -> new ActionOpen(classArgument);
            default -> throw new IllegalArgumentException("Invalid shorthand " + classSpecifier);
        };
    }

    public static ActionCommand fromCommand(String command) {
        return new ActionCommand(Collections.singletonList(command));
    }

    public static final HashMap<String, Function<Config, Action>> PRESETS = new HashMap<>();

    static void registerPresets() {
        PRESETS.put("shop", ActionShop::new);
        PRESETS.put("transaction", ActionTransaction::new);
        PRESETS.put("item_shop", ActionItemShop::new);
        PRESETS.put("player_shop", ActionPlayerShopFallback::make);
        PRESETS.put("take", ActionTake::new);
        PRESETS.put("open", ActionOpen::new);
        PRESETS.put("previous_page", ActionPage::new);
        PRESETS.put("next_page", ActionPage::new);
        PRESETS.put("first_page", ActionPage::new);
        PRESETS.put("last_page", ActionPage::new);
        PRESETS.put("close", ActionClose::new);
        PRESETS.put("back", ActionClose::new);
        PRESETS.put("refresh", ActionRefresh::new);
        PRESETS.put("delay", ActionDelay::new);
        PRESETS.put("book", ActionBook::new);
        PRESETS.put("commands", ActionCommand::new);
    }

    public static Action fromConfig(Config yaml) {
        if (!PRESETS.containsKey("shop"))
            registerPresets();

        Optional<String> presetOptional = yaml.find("preset", String.class);
        Action action = presetOptional.map(preset -> {
            if (preset.contains("!")) {
                return fromShorthand(preset);
            }
            String presetName = preset.replace(' ', '_').toLowerCase(Locale.ROOT);
            Function<Config, Action> constructor = PRESETS.get(presetName);
            if (constructor == null)
                throw new ConfigException(preset + " is not a valid preset!", yaml, preset);
            return constructor.apply(yaml);
        }).orElseGet(() -> new ActionCommand(yaml));
        action.condition = yaml.find("condition", Condition.class).orElse(ConditionConstant.TRUE);
        return action;
    }

    public boolean shouldTrigger(InventoryClickEvent e) {
        return triggerOnClick.contains(e.getClick()) && condition.test((Player) e.getWhoClicked());
    }

    /**
     * Influence the final displayed item stack, like adding price tags to the item
     * @param player Player
     * @param readonlyStack Original item stack
     * @param builder Actual item stack that will be displayed
     */
    public void influenceItem(Player player, final ItemStack readonlyStack, ItemBuilder builder) {

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
