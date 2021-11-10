package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import java.util.HashMap;
import java.util.Optional;

public final class ConfigHelper {
    private ConfigHelper() {}

    public static <T extends Enum<T>> T parseEnum(String input, Class<T> clazz) {
        return Enum.valueOf(clazz, input.toUpperCase().replace(' ', '_'));
    }

    public static String translateString(String input) {
        return input != null ? ChatColor.translateAlternateColorCodes('&', input) : null;
    }

    public static String untranslateString(String input) {
        return input != null ? input.replace(ChatColor.COLOR_CHAR, '&') : null;
    }

    public static BaseComponent[] parseComponentString(String input) {
        try {
            return ComponentSerializer.parse(input);
        } catch (com.google.gson.JsonSyntaxException ex) {
            // probably just a plain string
            return TextComponent.fromLegacyText(translateString(input));
        }
    }

    public static HashMap<String, Object> parseVariables(Config variables) {
        HashMap<String, Object> var = new HashMap<>();
        variables.getKeys().forEach(key -> {
            Optional<Config> complexConfig = variables.tryFind(key, Config.class);
            if (complexConfig.isPresent()) {
                WorstShop.get().logger.warning("Unsupported variable type: " + complexConfig.get().get("type", String.class));
            } else {
                var.put(key, variables.get(key, Object.class));
            }
        });
        return var;
    }
}
