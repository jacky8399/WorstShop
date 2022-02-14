package com.jacky8399.worstshop.shops;

import com.google.common.io.Files;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Exceptions;
import fr.minuskube.inv.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permissible;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

public class ShopManager {
    public static final Map<String, Shop> SHOPS = new HashMap<>();

    /**
     * Groups all item shops by their item type for easier access
     */
    public static final Map<Material, List<ItemShop>> ITEM_SHOPS = new HashMap<>();

    /**
     * temporary variable for storing the shop currently being parsed
     */
    public static String currentShopId = null;
    public static Shop currentShop = null;

    /**
     * Default shops that will be generated
     */
    private static final String[] defaultShops = {"default.yml", "shops/base.yml", "shops/blocks.yml", "shops/misc.yml"};

    public static Optional<Shop> getShop(String id) {
        return Optional.ofNullable(SHOPS.get(id));
    }

    public static boolean checkPermsOnly(Permissible player, String shopName) {
        return player.hasPermission("worstshop.shops." + shopName) || player.hasPermission("worstshop.shops.*");
    }

    public static void closeAllShops() {
        InventoryManager manager = WorstShop.get().inventories;
        Bukkit.getOnlinePlayers().forEach(p -> manager.getInventory(p).ifPresent(inv -> inv.close(p)));
    }

    public static File getShopFile(String id) {
        // figure out extension name
        File shops = new File(WorstShop.get().getDataFolder(), "shops");
        if (new File(shops, id + ".yml").exists()) {
            return new File(shops, id + ".yml");
        } else {
            return new File(shops, id + ".yaml");
        }
    }

    public static void renameShop(Shop shop, String newId) {
        String oldId = shop.id;
        File shops = new File(WorstShop.get().getDataFolder(), "shops");
        File oldFile = getShopFile(oldId);
        File newFile = new File(shops, newId + ".yml");
        // ensure file is created??
        int slash = newId.lastIndexOf('/');
        if (slash != -1) {
            File newFileDirectory = new File(shops, newId.substring(0, slash));
            if (!newFileDirectory.mkdirs())
                WorstShop.get().logger.warning("Failed to rename shop " + oldId + " to " + newId + ": Unable to create directory");
        }
        try {
            // noinspection UnstableApiUsage
            Files.copy(oldFile, newFile);
            if (!oldFile.delete())
                WorstShop.get().logger.warning("Failed to rename shop " + oldId + " to " + newId + ": Unable to delete old file");

            renameAllOccurrences(shop, newId);
        } catch (IOException ignored) {}
    }

    private static void renameAllOccurrences(Shop shop, String newName) {
        String oldId = shop.id;
        // rename all ShopReferences
        // how convenient
        ShopReference reference = ShopReference.REFERENCES.remove(oldId);
        if (reference != null) {
            reference.id = newName;
            ShopReference.REFERENCES.put(newName, reference);
        }
        shop.id = newName;
        SHOPS.remove(oldId);
        SHOPS.put(newName, shop);
    }

    @SuppressWarnings({"ConstantConditions", "unchecked"})
    public static void loadDiscounts() {
        WorstShop plugin = WorstShop.get();

        ShopDiscount.clearDiscounts();
        File discountFile = new File(plugin.getDataFolder(), "discounts.yml");
        if (discountFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(discountFile);
            yaml.getList("discounts").stream()
                    .map(obj -> (Map<String, Object>) obj)
                    .map(ShopDiscount.Entry::fromMap)
                    .forEach(ShopDiscount::addDiscountEntry);
            if (ShopDiscount.ALL_DISCOUNTS.size() != 0)
                plugin.logger.info("Loaded " + ShopDiscount.ALL_DISCOUNTS.size() + " discounts");
        }
    }

    public static void saveDiscounts() {
        List<Map<String, Object>> discounts = new ArrayList<>();
        ShopDiscount.ALL_DISCOUNTS.values().stream()
                .filter(entry -> !entry.hasExpired())
                .map(ShopDiscount.Entry::toMap)
                .forEach(discounts::add);
        if (discounts.size() == 0)
            return;
        WorstShop.get().logger.info("Saving " + discounts.size() + " discounts");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("discounts", discounts);
        try {
            yaml.save(new File(WorstShop.get().getDataFolder(), "discounts.yml"));
        } catch (IOException e) {
            WorstShop.get().logger.severe("Failed to save discounts");
        }
    }

    public static void cleanUp() {
        try {
            closeAllShops();
        } catch (Throwable ignored) {}
        try {
            ShopCommands.removeAliases();
        } catch (Throwable ignored) {}
        try {
            ShopReference.REFERENCES.values().forEach(ShopReference::invalidate);
        } catch (Throwable ignored) {}
        SHOPS.clear();
        ITEM_SHOPS.clear();
    }

    @SuppressWarnings("ConstantConditions")
    private static List<File> listFilesRecursively(File path, List<File> list) {
        for (File file : path.listFiles()) {
            if (file.isDirectory()) {
                listFilesRecursively(file, list);
            } else {
                list.add(file);
            }
        }
        return list;
    }

    public static void loadShops() {
        WorstShop plugin = WorstShop.get();
        Logger logger = plugin.getLogger();
        logger.info("Loading shops");

        cleanUp();

        loadDiscounts();

        // walk through all shops
        File shops = new File(plugin.getDataFolder(), "shops");
        String shopsFolderPath = shops.getAbsolutePath();
        if (!shops.isDirectory()) {
            logger.info("/shops/ folder does not exist. Saving default shops.");
            if (shops.mkdirs()) {
                for (String defaultShop : defaultShops) {
                    File destFile = new File(shops, defaultShop);
                    // create the necessary folder structure
                    File parentFile = destFile.getParentFile();
                    if (parentFile != null)
                        parentFile.mkdirs();
                    try (InputStream in = plugin.getResource("examples/" + defaultShop);
                         FileOutputStream out = new FileOutputStream(destFile)) {
                        if (in == null) {
                            logger.warning("Couldn't load default shop " + defaultShop);
                            continue;
                        }
                        byte[] bytes = in.readAllBytes();
                        out.write(bytes);
                    } catch (IOException e) {
                        logger.warning("Couldn't save default shop " + defaultShop + ": " + e);
                    }
                }
            } else {
                logger.severe("Failed to create /shops/ folder.");
            }
        }

        if (shops.isDirectory()) {
            // iterate through shops
            for (File shopFile : listFilesRecursively(shops, new ArrayList<>())) {
                String shopPath = shopFile.getAbsolutePath();
                // Of course there's no method to get the extension
                String shopExt = shopPath.substring(shopPath.lastIndexOf('.') + 1);
                if (!("yml".equals(shopExt) || "yaml".equals(shopExt))) {
                    continue;
                }

                currentShopId = shopPath.substring(shopsFolderPath.length() + 1, shopPath.length() - shopExt.length() - 1)
                        .replace(File.separatorChar, '/');

                try {
                    Shop shop = Shop.fromYaml(currentShopId, shopFile);
                    if (SHOPS.put(currentShopId, shop) != null)
                        logger.warning("Overriding preexisting shop " + currentShopId);
                } catch (Exception e) {
                    RuntimeException wrapped = new RuntimeException("Loading shop " + currentShopId, e);
                    String id = Exceptions.logException(wrapped);
                    logger.severe("Unhandled exception while loading " + currentShopId + ".yml: " + e);
                    logger.severe("The exception has been logged as " + id);
                }
            }
            currentShop = null;
            currentShopId = null;

            // load commands
            ShopCommands.loadAliases();

            logger.info("Loaded " + SHOPS.size() + " shops");
        }
    }
}
