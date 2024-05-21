package com.jacky8399.worstshop.events;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class Events implements Listener {

    public static void registerEvents() {
//        register(new ShopAliasListener());
        register(new IllegalShopItemListener());
        register(new Events());
    }

    private static final WorstShop plugin = WorstShop.get();
    private static void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(WorstShop.get(), () -> {
            for (Shop shop : ShopManager.SHOPS.values()) {
                if (shop.openOnJoin.test(player) && shop.condition.test(player)) {
                    shop.getInventory(player).open(player);
                }
            }
        }, 1);
    }

}
