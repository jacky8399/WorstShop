package com.jacky8399.worstshop.helper;

import com.google.common.collect.Lists;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ConfigHelper {
    @Deprecated
    public static List<ConfigurationSection> getConfigList(ConfigurationSection config, String path) {
        if (!config.isList(path)) return Collections.emptyList();

        LinkedList<ConfigurationSection> list = Lists.newLinkedList();
        for (Object thing : config.getList(path)) {
            if (thing instanceof Map) {
                MemoryConfiguration mem = new MemoryConfiguration();
                mem.addDefaults((Map<String, Object>) thing);

                list.add(mem);
            }
        }
        return list;
    }

    public static <T extends Enum<T>> T parseEnum(String input, Class<T> clazz) {
        return Enum.valueOf(clazz, input.toUpperCase().replace(' ', '_'));
    }

    public static String translateString(String input) {
        return input != null ? ChatColor.translateAlternateColorCodes('&', input) : null;
    }
}
