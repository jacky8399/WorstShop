package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.maxgamer.quickshop.api.QuickShopAPI;
import org.maxgamer.quickshop.api.ShopAPI;
import org.maxgamer.quickshop.event.ShopCreateEvent;
import org.maxgamer.quickshop.event.ShopDeleteEvent;
import org.maxgamer.quickshop.event.ShopItemChangeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActionPlayerShop extends Action {
    static {
        try {
            shopAPI = QuickShopAPI.getShopAPI();
            cache = new ShopCache();
        } catch (Throwable e) {
            throw new IllegalStateException("QuickShop is not loaded!", e);
        }
    }

    public ActionPlayerShop(Config yaml) {
        super(yaml);
    }

    @Override
    public void onClick(InventoryClickEvent e) {

    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "player_shop");
        return map;
    }

    private static ShopAPI shopAPI;
    private static ShopCache cache;
    private static class ShopCache implements Listener {
        ShopCache() {
            Bukkit.getScheduler().runTask(WorstShop.get(), this::refreshCache);
            Bukkit.getPluginManager().registerEvents(this, WorstShop.get());
        }

        HashMap<Material, List<org.maxgamer.quickshop.shop.Shop>> shops = new HashMap<>();

        public void refreshCache() {
            List<org.maxgamer.quickshop.shop.Shop> shops = QuickShopAPI.getShopAPI().getAllShops();
            for (org.maxgamer.quickshop.shop.Shop shop : shops) {
                ItemStack stack = shop.getItem();
                Material mat = stack.getType();
                List<org.maxgamer.quickshop.shop.Shop> shopz = this.shops.computeIfAbsent(mat, key->new ArrayList<>());
                shopz.add(shop);
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        void onShopItemChange(ShopItemChangeEvent e) {
            org.maxgamer.quickshop.shop.Shop shop = e.getShop();
            ItemStack oldStack = e.getOldItem(), newStack = e.getNewItem();
            List<org.maxgamer.quickshop.shop.Shop> shopz = shops.get(oldStack.getType()),
                    newShopz = shops.computeIfAbsent(newStack.getType(), key->new ArrayList<>());
            if (shopz != null) {
                shopz.remove(shop);
            }
            newShopz.add(shop);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        void onShopCreate(ShopCreateEvent e) {
            org.maxgamer.quickshop.shop.Shop shop = e.getShop();
            List<org.maxgamer.quickshop.shop.Shop> shopz = shops.computeIfAbsent(shop.getItem().getType(), key->new ArrayList<>());
            shopz.add(shop);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        void onShopDelete(ShopDeleteEvent e) {
            org.maxgamer.quickshop.shop.Shop shop = e.getShop();
            List<org.maxgamer.quickshop.shop.Shop> shopz = shops.get(shop.getItem().getType());
            shopz.remove(shop);
        }
    }
}
