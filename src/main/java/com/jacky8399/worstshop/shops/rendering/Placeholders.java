package com.jacky8399.worstshop.shops.rendering;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.Exceptions;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Placeholders {
    private static final WorstShop PLUGIN = WorstShop.get();
    public static final NamespacedKey ITEM_AMOUNT_KEY = new NamespacedKey(PLUGIN, "amount");
    public static final NamespacedKey ITEM_SKULL_OWNER_KEY = new NamespacedKey(PLUGIN, "skull_owner");

    public static final Pattern SHOP_VARIABLE_PATTERN = Pattern.compile("!([A-Za-z0-9_]+)!");


    public static String setPlaceholders(String input, @NotNull PlaceholderContext context) {
        if (context.additionalContext() != null)
            input = setPlaceholders(input, context.additionalContext());
//        String orig = input;

        if (context.player() != null)
            input = input.replace("{player}", context.player().getName());
        if (context.renderer() != null && input.indexOf('!') > -1) {
            input = input.replace("!page!", Integer.toString(context.renderer().page + 1))
                    .replace("!max_page!", Integer.toString(context.renderer().maxPage))
                    .replace("!shop!", context.renderer().shop.id);
        }
        // shop and element variables
        if (input.indexOf('!') > -1) {
            Matcher matcher = SHOP_VARIABLE_PATTERN.matcher(input);
            input = matcher.replaceAll(result -> {
                String varName = result.group(1);
                Object value = context.getVariable(varName);
                if (value != null) {
                    return value.toString();
                } else {
                    return result.group();
                }
            });
        }
        // PlaceholderAPI
        if (PLUGIN.placeholderAPI && context.player() != null) {
            try {
                input = PlaceholderAPI.setPlaceholders(context.player(), input);
            } catch (Exception e) {
                RuntimeException wrapped = new RuntimeException("Setting placeholders with PlaceholderAPI", e);
                Exceptions.logException(wrapped);
            }
        }
//        WorstShop.get().logger.info("[Placeholders] " + ConfigHelper.untranslateString(orig) + " -> " + input);
        return ConfigHelper.translateString(input);
    }

    public static ItemStack setPlaceholders(ItemStack stack, Player player) {
        return setPlaceholders(stack, PlaceholderContext.guessContext(player));
    }

    @Contract("null, _ -> null; !null, _ -> !null")
    public static ItemStack setPlaceholders(ItemStack stack, @NotNull PlaceholderContext context) {
        if (stack == null)
            return null;

        if (stack.getType() == Material.AIR || context == PlaceholderContext.NO_CONTEXT)
            return stack;
        stack = stack.clone();
        ItemMeta meta = stack.getItemMeta();
        boolean changed = false;
        // render item name, display name and lore with components
        if (meta.hasLore()) {
            // noinspection ConstantConditions
            List<Component> oldLore = meta.lore();
            List<Component> newLore = new ArrayList<>(oldLore.size());
            for (Component component : oldLore) {
                newLore.add(PlaceholderComponentRenderer.INSTANCE.render(component, context)
                        .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            }
            meta.lore(newLore);
            changed = true;
        }
        if (meta.hasDisplayName()) {
            meta.displayName(PlaceholderComponentRenderer.INSTANCE.render(meta.displayName(), context)
                    .decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE));
            changed = true;
        }
        if (meta.hasItemName()) {
            meta.itemName(PlaceholderComponentRenderer.INSTANCE.render(meta.itemName(), context));
            changed = true;
        }
        // check for variables in PDCs
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.get(ITEM_AMOUNT_KEY, PersistentDataType.STRING) instanceof String itemAmount) {
            itemAmount = setPlaceholders(itemAmount, context);
            int newAmount = 1;
            try {
                newAmount = (int) Float.parseFloat(itemAmount);
            } catch (NumberFormatException ignored) {}
            stack.setAmount(Math.max(1, Math.min(stack.getMaxStackSize(), newAmount)));
            pdc.remove(ITEM_AMOUNT_KEY);
            changed = true;
        }

        if (meta instanceof SkullMeta skullMeta &&
                pdc.get(ITEM_SKULL_OWNER_KEY, PersistentDataType.STRING) instanceof String skullOwner) {
            PlayerProfile result = null;
            if ("{player}".equals(skullOwner)) {
                result = Objects.requireNonNull(context.getPlayer()).getPlayerProfile();
            } else {
                skullOwner = setPlaceholders(skullOwner, context);
                Player player = Bukkit.getPlayerExact(skullOwner);
                if (player != null && player.isOnline()) {
                    result = player.getPlayerProfile();
                } else {
                    try {
                        result = Bukkit.createProfile(null, skullOwner);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            skullMeta.setPlayerProfile(result);
            pdc.remove(ITEM_SKULL_OWNER_KEY);
            changed = true;
        }
        if (changed) {
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
