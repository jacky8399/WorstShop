package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.jacky8399.worstshop.WorstShop;
import fr.minuskube.inv.InventoryManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permissible;

import java.io.File;
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

    public static void cleanUp() {
        closeAllShops();
        ShopCommands.removeAliases();
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
        File discountFile = new File(plugin.getDataFolder(), "discounts.yml");
        if (discountFile.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(discountFile);
            yaml.getList("discounts").forEach(obj -> {

            });
        }

        // walk through all shops
        File shops = new File(plugin.getDataFolder(), "shops");
        String shopsFolderPath = shops.getAbsolutePath();
        if (shops.exists() && shops.isDirectory()) {
            // iterate thru shops
            int count = 0;
            for (File shop : listFilesRecursively(shops, Lists.newArrayList())) {
                String shopPath = shop.getAbsolutePath();
                String shopExt = Files.getFileExtension(shopPath);
                if (!("yml".equals(shopExt) || "yaml".equals(shopExt))) {
                    continue;
                }

                currentShopId = shopPath.substring(shopsFolderPath.length() + 1, shopPath.length() - shopExt.length() - 1).replace('\\', '/');
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(shop);

                SHOPS.put(currentShopId, Shop.fromYaml(currentShopId, yaml));
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
