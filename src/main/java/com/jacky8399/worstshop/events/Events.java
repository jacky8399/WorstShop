package com.jacky8399.worstshop.events;

import com.jacky8399.worstshop.WorstShop;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

public class Events {

    public static void registerEvents() {
        register(new ShopAliasListener());
        register(new IllegalShopItemListener());
    }

    private static final WorstShop plugin = WorstShop.get();
    private static void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

}
