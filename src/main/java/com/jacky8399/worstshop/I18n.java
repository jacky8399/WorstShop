package com.jacky8399.worstshop;

import co.aikar.commands.BukkitLocales;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.Locales;
import co.aikar.locales.LanguageTable;
import co.aikar.locales.LocaleManager;
import com.google.common.collect.Maps;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.PaperHelper;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.libs.org.apache.commons.io.FileUtils;
import org.bukkit.craftbukkit.libs.org.apache.commons.io.FilenameUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class I18n {

    public static class Keys {
        public static final String MESSAGES_KEY = "worstshop.messages.";
        public static final String ITEM_KEY = MESSAGES_KEY + "shops.wants.items";
    }

    public static YamlConfiguration lang;
    public static HashMap<String, YamlConfiguration> langs = Maps.newHashMap();

    private static String currentLang = "en";

    public static void changeLang(String lang) {
        if (langs.containsKey(lang)) {
            I18n.lang = langs.get(lang);
        } else
            throw new IllegalArgumentException(lang);
    }

    static WorstShop plugin = WorstShop.get();
    public static void loadLang() {
        BukkitLocales locales = plugin.commands.getLocales();

        plugin.logger.info("Loading locales");

        I18n.lang = null;
        langs.clear();

        File langFolder = plugin.getDataFolder().toPath().resolve("lang").toFile();
        if (!langFolder.exists() || !langFolder.isDirectory()) {
            // create folder
            langFolder.mkdirs();
            // copy files
            try {
                FileUtils.copyInputStreamToFile(plugin.getResource("en.yml"), new File(langFolder, "en.yml"));
            } catch (IOException e) { }
        }
        for (File langFile : langFolder.listFiles()) {
            String localeName = FilenameUtils.getBaseName(langFile.getPath());
            try {
                Locale locale = new Locale(localeName);
                plugin.commands.addSupportedLanguage(locale);
                locales.loadYamlLanguageFile(langFile, locale);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(langFile);

                // if english save defaults
                if (localeName.equals("en")) {
                    YamlConfiguration yamlEnglish = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(WorstShop.get().getResource("en.yml"))
                    );
                    yaml.options().copyDefaults(true);
                    yaml.setDefaults(yamlEnglish);
                    yaml.save(langFile);
                    plugin.logger.info("Saving en.yml locale");
                }

                langs.put(localeName, yaml);
            } catch (IOException | InvalidConfigurationException e) {
                plugin.logger.severe("Invalid localisation file " + localeName);
                e.printStackTrace();
            } catch (Exception e) {
                plugin.logger.severe("Error loading localisation file " + localeName);
                plugin.logger.severe("Skipping");
                e.printStackTrace();
            }
        }

        plugin.logger.info("Loaded " + langs.size() + " locales");

        // refresh
        changeLang("en");
    }

    public static String nameStack(ItemStack stack) {
        return nameStack(stack, stack.getAmount());
    }
    public static String nameStack(ItemStack stack, int amount) {
        return translate(Keys.ITEM_KEY, amount,
                stack.getItemMeta().hasDisplayName() ? stack.getItemMeta().getDisplayName() : PaperHelper.getItemName(stack));
    }

    public static String translate(String path, Object... args) {
        path = path.toLowerCase();
        if (lang.isString(path)) {
            String unformatted = lang.getString(path);
            try {
                String formatted;
                if (args.length == 0) // shortcut
                    formatted = unformatted;
                else if (args.length == 1)
                    formatted = unformatted.replace("{0}", String.valueOf(args[0]));
                else
                    formatted = MessageFormat.format(unformatted, args);
                return ConfigHelper.translateString(formatted);
            } catch (Exception ex) {
                return ChatColor.RED + "" + path + ": " + ex.toString();
            }
        }
        return path;
    }

    public static String translate(String path, Player player, Object... args) {
        return doPlaceholders(player, translate(path, args));
    }

    public static String doPlaceholders(@NotNull Player player, String input) {
        return (plugin.placeholderAPI ? PlaceholderAPI.setPlaceholders(player, input) : input)
                .replace("{player}", player.getName());
    }

    private static final Field FIELD_LANGUAGE_TABLES;
    private static final Field FIELD_LOCALE_MANAGER;

    static {
        try {
            FIELD_LANGUAGE_TABLES = LocaleManager.class.getDeclaredField("tables");
            FIELD_LANGUAGE_TABLES.setAccessible(true);
            FIELD_LOCALE_MANAGER = Locales.class.getDeclaredField("localeManager");
            FIELD_LOCALE_MANAGER.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            throw new IllegalStateException("Language tables not found");
        }
    }

    public static void shutdown() {
        try {
            // Clear I18n language table
            LocaleManager<CommandIssuer> localeManager = (LocaleManager<CommandIssuer>) FIELD_LOCALE_MANAGER.get(WorstShop.get().commands.getLocales());
            Map<Locale, LanguageTable> languageTables = (Map<Locale, LanguageTable>) FIELD_LANGUAGE_TABLES.get(localeManager);
            languageTables.clear();
        } catch (IllegalAccessException ex) {
            WorstShop.get().logger.severe("Failed to clear language tables:");
            ex.printStackTrace();
        }
    }
}
