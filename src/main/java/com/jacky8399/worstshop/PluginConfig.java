package com.jacky8399.worstshop;

import org.bukkit.configuration.file.FileConfiguration;

public class PluginConfig {

    public static void reload() {
        FileConfiguration config = WorstShop.get().getConfig();
        advancedProtection = config.getBoolean("advanced-protection");
        defaultShop = config.getString("default-shop");
    }

    public static boolean advancedProtection = true;
    public static String defaultShop = "default";

}
