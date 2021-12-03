package com.jacky8399.worstshop.shops.rendering;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Exceptions;
import com.jacky8399.worstshop.helper.PaperHelper;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Placeholders {
    public static final Pattern SHOP_VARIABLE_PATTERN = Pattern.compile("!([A-Za-z0-9_]+)!");

    public static String setPlaceholders(String input, @NotNull PlaceholderContext context) {
        if (context.player() != null)
            input = input.replace("{player}", context.player().getName());
        if (context.renderer() != null && (input.contains("!page!") || input.contains("!max_page!")))
            input = input.replace("!page!", Integer.toString(context.renderer().page + 1))
                    .replace("!max_page!", Integer.toString(context.renderer().maxPage + 1));
        // shop and element variables
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
        // PlaceholderAPI
        if (WorstShop.get().placeholderAPI && context.player() != null) {
            try {
                input = PlaceholderAPI.setPlaceholders(context.player(), input);
            } catch (Exception e) {
                RuntimeException wrapped = new RuntimeException("Setting placeholders with PlaceholderAPI", e);
                Exceptions.logException(wrapped);
            }
        }
        return input;
    }

    public static ItemStack setPlaceholders(ItemStack stack, Player player) {
        return setPlaceholders(stack, PlaceholderContext.guessContext(player));
    }

    @Contract("null, _ -> null; !null, _ -> !null")
    public static ItemStack setPlaceholders(ItemStack stack, @NotNull PlaceholderContext context) {
        if (stack == null || stack.getType() == Material.AIR || context == PlaceholderContext.NO_CONTEXT)
            return stack;

        UnaryOperator<String> placeholderReplacer = str -> setPlaceholders(str, context);
        stack = stack.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta.hasLore()) {
            // noinspection ConstantConditions
            meta.setLore(meta.getLore().stream()
                    .map(placeholderReplacer)
                    .collect(Collectors.toList())
            );
        }
        if (meta.hasDisplayName()) {
            meta.setDisplayName(placeholderReplacer.apply(meta.getDisplayName()));
        }
        // wow why didn't I think of this
        // check for player skull
        if (meta instanceof SkullMeta skullMeta) {
            PaperHelper.GameProfile profile = PaperHelper.getSkullMetaProfile(skullMeta);
            if (profile != null) {
                if (profile.equals(StaticShopElement.VIEWER_SKULL) && context.player() != null) {
                    skullMeta.setOwningPlayer(context.player());
                } else if (profile.getName() != null && (profile.getName().contains("%") || profile.getName().contains("!"))) {
                    // replace placeholders too
                    String newName = placeholderReplacer.apply(profile.getName());
                    // check if the name is that of an online player
                    Player newPlayer = Bukkit.getPlayer(newName);
                    if (newPlayer != null && newPlayer.isOnline()) {
                        skullMeta.setOwningPlayer(newPlayer);
                    } else if (newName.length() != 0) {
                        PaperHelper.GameProfile newProfile = PaperHelper.createProfile(null, newName);
                        PaperHelper.setSkullMetaProfile(skullMeta, newProfile);
                    }
                }
            }
        }
        stack.setItemMeta(meta);

        if (context.additionalContext() != null) {
            return Placeholders.setPlaceholders(stack, context.additionalContext());
        }
        return stack;
    }
}
