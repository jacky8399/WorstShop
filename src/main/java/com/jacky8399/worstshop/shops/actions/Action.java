package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionConstant;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Function;

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
        input = input.trim().toLowerCase(Locale.ROOT);
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
        String classSpecifier = input.substring(0, input.indexOf("!"))
                .replace(' ', '_').toLowerCase();
        String classArgument = input.substring(input.indexOf("!") + 1).trim();
        switch (classSpecifier) {
            case "item_shop":
                return new ActionItemShop(classArgument);
            case "open":
                return new ActionOpen(classArgument);
            default:
                return null;
        }
    }

    public static ActionCustom fromCommand(String command) {
        return new ActionCustom(Collections.singletonList(command));
    }

    public static final HashMap<String, Function<Config, Action>> PRESETS = new HashMap<>();

    static void registerPresets() {
        PRESETS.put("shop", ActionShop::new);
        PRESETS.put("transaction", ActionTransaction::new);
        PRESETS.put("item_shop", ActionItemShop::new);
        PRESETS.put("player_shop", ActionPlayerShop::new);
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
        PRESETS.put("commands", ActionCustom::new);
    }

    public static Action fromConfig(Config yaml) {
        if (!PRESETS.containsKey("shop"))
            registerPresets();

        Optional<String> presetOptional = yaml.find("preset", String.class);
        Action action = presetOptional.map(preset -> {
            if (preset.contains("!")) {
                Action result = fromShorthand(preset);
                if (result == null)
                    throw new ConfigException(preset + " is not a valid shorthand!", yaml, "preset");
                return result;
            }
            String presetName = preset.replace(' ', '_').toLowerCase(Locale.ROOT);
            Function<Config, Action> constructor = PRESETS.get(presetName);
            if (constructor == null)
                throw new ConfigException(preset + " is not a valid preset!", yaml, preset);
            return constructor.apply(yaml);
        }).orElseGet(() -> new ActionCustom(yaml));
        action.condition = yaml.find("condition", Config.class, String.class)
                .map(Condition::fromObject)
                .orElse(ConditionConstant.TRUE);
        return action;
    }


    public boolean shouldTrigger(InventoryClickEvent e) {
        return triggerOnClick.contains(e.getClick()) && condition.test((Player) e.getWhoClicked());
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
