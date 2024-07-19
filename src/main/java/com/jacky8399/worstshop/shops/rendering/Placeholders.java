package com.jacky8399.worstshop.shops.rendering;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.Exceptions;
import com.jacky8399.worstshop.helper.ItemUtils;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Placeholders {
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
//        WorstShop.get().logger.info("[Placeholders] " + ConfigHelper.untranslateString(orig) + " -> " + input);
        return ConfigHelper.translateString(input);
    }

    public static ItemStack setPlaceholders(ItemStack stack, Player player) {
        return setPlaceholders(stack, PlaceholderContext.guessContext(player));
    }

    @SuppressWarnings("deprecation")
    @Contract("null, _ -> null; !null, _ -> !null")
    public static ItemStack setPlaceholders(ItemStack stack, @NotNull PlaceholderContext context) {
        if (stack == null)
            return null;

        stack = stack.clone();
        if (stack.getType() == Material.AIR || context == PlaceholderContext.NO_CONTEXT)
            return stack;

        ItemMeta meta = stack.getItemMeta();
        if (meta.hasLore()) {
            // noinspection ConstantConditions
            meta.setLore(meta.getLore().stream()
                    .map(context::apply)
                    .toList()
            );
        }
        if (meta.hasDisplayName()) {
            meta.setDisplayName(context.apply(meta.getDisplayName()));
        }
        // wow why didn't I think of this
        // check for player skull
        if (meta instanceof SkullMeta skullMeta) {
            PlayerProfile profile = skullMeta.getPlayerProfile();
            if (profile != null) {
                PlayerProfile result = (PlayerProfile) profile.clone();
                Player player = context.getPlayer();
                var skullProperty = profile.getProperties().stream().filter(property -> property.getName().equals(ItemUtils.SKULL_PROPERTY)).findAny();
                if (skullProperty.isPresent()) {
                    String profileName = skullProperty.get().getValue();
                    if ("{player}".equals(profileName) && player != null) {
                        result = player.getPlayerProfile();
                    } else if (profileName.contains("%") || profileName.contains("!")) {
                        // replace placeholders too
                        String newName = context.apply(profileName);
                        // check if the name is that of an online player
                        Player newPlayer = Bukkit.getPlayer(newName);
                        if (newPlayer != null && newPlayer.isOnline()) {
                            result = newPlayer.getPlayerProfile();
                        } else if (!newName.isEmpty()) {
                            result = ItemUtils.makeProfile(null, newName);
                            result.clearProperties();
                        }
                    }
                }
                result.removeProperty(ItemUtils.SKULL_PROPERTY);
                skullMeta.setPlayerProfile(result);
                if (ItemUtils.SKULL_DEBUG) {
                    var lore = new ArrayList<>(meta.hasLore() ? meta.getLore() : List.of());
                    lore.add("name: " + result.getName());
                    lore.add(ItemUtils.SKULL_PROPERTY + ": " + skullProperty.map(ProfileProperty::getValue).orElse("*not present*"));
                    lore.add("result: " + result);
                    meta.setLore(lore);
                    WorstShop.get().logger.info("[Placeholder] Original profile: " + profile + "\nReplaced: " + result);
                }
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }
}
