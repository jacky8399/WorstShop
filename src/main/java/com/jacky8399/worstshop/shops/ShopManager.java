package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ConfigHelper;
import fr.minuskube.inv.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.permissions.Permissible;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class ShopManager {
    public static final HashMap<String, Shop> SHOPS = Maps.newHashMap();

    public static final EnumMap<Material, List<ItemShop>> ITEM_SHOPS = Maps.newEnumMap(Material.class);

    /**
     * temporary variable for storing the shop currently being parsed
     */
    public static String currentShopId = null;
    public static Shop currentShop = null;

    public static Optional<Shop> getShop(String id) {
        return Optional.ofNullable(SHOPS.get(id));
    }

    public static boolean checkPermsOnly(Permissible player, String shopName) {
        return player.hasPermission("worstshop.shops." + shopName);
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
            newFileDirectory.mkdirs();
        }
        try {
            // noinspection UnstableApiUsage
            Files.copy(oldFile, newFile);
            oldFile.delete();

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

    public static void saveDiscounts() {
        List<ShopDiscount.Entry> discounts = ShopDiscount.ALL_DISCOUNTS.values().stream()
                .filter(entry -> !entry.hasExpired())
                .toList();
        WorstShop.get().logger.info("Saving " + discounts.size() + " discounts");
        YamlConfigurationLoader loader = ConfigHelper.createLoader(new File(WorstShop.get().getDataFolder(), "discounts.yml"));
        ConfigurationNode node = loader.createNode();
        try {
            node.node("discounts").setList(ShopDiscount.Entry.class, discounts);
            loader.save(node);
        } catch (ConfigurateException e) {
            WorstShop.get().logger.severe("Failed to save discounts: " + e);
        }
    }

    public static void cleanUp() {
        closeAllShops();
        ShopCommands.removeAliases();
        ShopReference.REFERENCES.values().forEach(ShopReference::invalidate);
        SHOPS.clear();
        ITEM_SHOPS.clear();
    }

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
        plugin.logger.info("Loading shops");

        cleanUp();

        // load discounts
        ShopDiscount.clearDiscounts();
        File discountFile = new File(plugin.getDataFolder(), "discounts.yml");
        if (discountFile.exists()) {
//            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(discountFile);
            YamlConfigurationLoader loader = ConfigHelper.createLoader(discountFile);
            CommentedConfigurationNode node;
            try {
                node = loader.load();
                ConfigurationNode inner = node.node("discounts");
                if (!inner.virtual()) {
                    inner.getList(ShopDiscount.Entry.class).forEach(ShopDiscount::addDiscountEntry);
                } else {
                    plugin.logger.severe("Invalid discounts.yml structure");
                }
            } catch (IOException e) {
                plugin.logger.severe("Failed to read discounts.yml: " + e);
            }
            plugin.logger.info("Loaded " + ShopDiscount.ALL_DISCOUNTS.size() + " discounts");
        }

        // walk through all shops
        File shops = new File(plugin.getDataFolder(), "shops");
        String shopsFolderPath = shops.getAbsolutePath();
        if (shops.exists() && shops.isDirectory()) {
            // iterate through shops
            int count = 0;
            for (File shop : listFilesRecursively(shops, Lists.newArrayList())) {
                String shopPath = shop.getAbsolutePath();
                String shopExt = shopPath.substring(shopPath.lastIndexOf('.') + 1);
                if (!("yml".equals(shopExt) || "yaml".equals(shopExt))) {
                    continue;
                }

                currentShopId = shopPath.substring(shopsFolderPath.length() + 1, shopPath.length() - shopExt.length() - 1).replace('\\', '/');

                SHOPS.put(currentShopId, Shop.fromYaml(currentShopId, shop));
                plugin.logger.fine("Loaded " + currentShopId + ".yml");
                count++;
            }
            currentShop = null;
            currentShopId = null;

            // load commands
            ShopCommands.loadAliases();

            plugin.logger.info("Loaded " + count + " shops");
        } else {
            shops.mkdirs();
            plugin.logger.info("/shops/ folder does not exist. Creating");
        }
    }

}
