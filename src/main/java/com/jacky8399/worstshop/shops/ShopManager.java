package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.jacky8399.worstshop.WorstShop;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.permissions.Permissible;

import java.io.File;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;

public class ShopManager {

    public static final HashMap<String, Shop> SHOPS = Maps.newHashMap();

    public static final EnumMap<Material, List<ItemShop>> ITEM_SHOPS = Maps.newEnumMap(Material.class);

    /**
     * temporary variable for storing the shop currently being parsed
     */
    public static String currentShop = null;

    public static boolean checkPerms(Permissible player, String shopName) {
        return player.hasPermission("worstshop.shops." + shopName);
    }

    public static boolean checkPerms(Permissible player, Shop shop) {
        if (shop == null)
            return false;
        return checkPerms(player, shop.id);
    }

    public static void cleanUp() {
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

        // walk through all shops
        File shops = plugin.getDataFolder().toPath().resolve("shops").toFile();
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

                currentShop = shopPath.substring(shopsFolderPath.length() + 1, shopPath.length() - shopExt.length() - 1).replace("\\", "/");
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(shop);

                SHOPS.put(currentShop, Shop.fromYaml(currentShop, yaml));
                plugin.logger.fine("Loaded " + currentShop + ".yml");
                count++;
            }
            currentShop = null;

            // load commands
            ShopCommands.loadAliases();

            plugin.logger.info("Loaded " + count + " shops");
        } else {
            shops.mkdirs();
            plugin.logger.info("/shops/ folder does not exist. Creating");
        }
    }

}
