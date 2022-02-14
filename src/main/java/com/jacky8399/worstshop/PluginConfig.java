package com.jacky8399.worstshop;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.regex.Pattern;

public class PluginConfig {

    public static void reload() {
        FileConfiguration config = WorstShop.get().getConfig();
        advancedProtection = config.getBoolean("advanced-protection");
        defaultShop = config.getString("default-shop");
        String rgbRegexString = config.getString("rgb-regex");
        try {
            rgbRegex = Pattern.compile(rgbRegexString);
        } catch (Exception e) {
            rgbRegex = Pattern.compile("&#([A-Za-z0-9]{6})");
        }
    }

    public static boolean advancedProtection = true;
    public static String defaultShop = "default";
    public static Pattern rgbRegex = Pattern.compile("&#([A-Za-z0-9]{6})");

}
