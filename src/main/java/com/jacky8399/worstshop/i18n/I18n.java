package com.jacky8399.worstshop.i18n;

import co.aikar.commands.BukkitLocales;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.Locales;
import co.aikar.locales.LanguageTable;
import co.aikar.locales.LocaleManager;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.PaperHelper;
import com.jacky8399.worstshop.helper.TextUtils;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
    public static HashMap<String, YamlConfiguration> langs = new HashMap<>();

    private static String currentLang = "en";

    private static final HashMap<String, Translatable> translatables = new HashMap<>();
    private static final HashMap<String, ComponentTranslatable> componentTranslatables = new HashMap<>();

    public static void changeLang(String lang) {
        if (!langs.containsKey(lang)) {
            plugin.logger.severe("Failed to find lang/" + lang + ".yml, using bundled default values instead.");
            I18n.lang = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("en.yml")));
            currentLang = "en";
            translatables.values().forEach(Translatable::update);
            componentTranslatables.values().forEach(ComponentTranslatable::update);
            return;
        }
        I18n.lang = langs.get(lang);
        currentLang = lang;
        // refresh translatables
        translatables.values().forEach(Translatable::update);
        componentTranslatables.values().forEach(ComponentTranslatable::update);
    }

    static WorstShop plugin = WorstShop.get();
    @SuppressWarnings({"ConstantConditions", "ResultOfMethodCallIgnored"})
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
            File defaultEnglishFile = new File(langFolder, "en.yml");
            try {
                Files.copy(plugin.getResource("en.yml"), defaultEnglishFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.logger.severe("Failed to copy default en.yml");
                e.printStackTrace();
            }
        }
        for (File langFile : langFolder.listFiles()) {
            String fileName = langFile.getName();
            String localeName = fileName.substring(0, fileName.lastIndexOf('.'));
            try {
                Locale locale = Locale.of(localeName);
                plugin.commands.addSupportedLanguage(locale);
                locales.loadYamlLanguageFile(langFile, locale);
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(langFile);

                // if english save defaults
                if (localeName.equals("en")) {
                    YamlConfiguration yamlEnglish = YamlConfiguration.loadConfiguration(
                            new InputStreamReader(plugin.getResource("en.yml"))
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
        ItemMeta meta = stack.getItemMeta();
        return translate(Keys.ITEM_KEY, amount,
                meta.hasDisplayName() ? meta.getDisplayName() : PaperHelper.getItemName(stack));
    }

    @SuppressWarnings("ConstantConditions")
    public static String translate(String path, Object... args) {
        path = path.toLowerCase(Locale.ROOT);
        String unformatted = lang.getString(path);
        if (unformatted != null) {
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
                return ChatColor.RED + "" + path + " ( @ " + currentLang + ".yml): " + ex.toString();
            }
        }
        return path;
    }

    public static Component translateAsComponent(String path, Object... args) {
        return TextUtils.LEGACY_COMPONENT_SERIALIZER.deserialize(translate(path, args));
    }

    public static Component translateComponentArgs(String path, Component... components) {
        return new ComponentTranslatable(path).apply(components);
    }

    public static Translatable createTranslatable(String path) {
        return translatables.computeIfAbsent(path, Translatable::new);
    }

    public static ComponentTranslatable createComponentTranslatable(String path) {
        return componentTranslatables.computeIfAbsent(path, ComponentTranslatable::new);
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

    @SuppressWarnings("unchecked")
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
