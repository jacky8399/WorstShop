package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.i18n.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.LinkedHashMap;
import java.util.Map;

public class ActionPlayerShopFallback extends Action {

    static final boolean HAS_QUICK_SHOP;
    static {
        boolean hasQuickShop = false;
        try {
            Class.forName("com.ghostchu.quickshop.api.event.ShopDeleteEvent");
            hasQuickShop = true;
        } catch (ClassNotFoundException ex) {
            WorstShop.get().logger.warning("QuickShop not found. All player_shops will fallback to shops if possible.");
        }
        HAS_QUICK_SHOP = hasQuickShop;
    }

    public static ActionPlayerShopFallback make(Config config) {
        if (HAS_QUICK_SHOP) {
            return new ActionPlayerShop(config);
        } else {
            return new ActionPlayerShopFallback(config);
        }
    }

    public static ActionPlayerShopFallback makeShorthand(String shorthand) {
        if (HAS_QUICK_SHOP) {
            return new ActionPlayerShop(shorthand);
        } else {
            return new ActionPlayerShopFallback(shorthand);
        }
    }

    ShopElement parentElement;
    ActionItemShop fallback;
    public ActionPlayerShopFallback(Config yaml) {
        super(yaml);
        ShopElement element = ParseContext.findLatest(ShopElement.class);
        if (element == null)
            throw new IllegalStateException("Couldn't find parent element! Not in parse context?");
        parentElement = element.clone();
        fallback = yaml.find("fallback", Config.class).map(ActionItemShop::new).orElse(null);
    }

    public ActionPlayerShopFallback(String shorthand) {
        super(null);
        ShopElement element = ParseContext.findLatest(ShopElement.class);
        if (element == null)
            throw new IllegalStateException("Couldn't find parent element! Not in parse context?");
        parentElement = element.clone();
        fallback = new ActionItemShop(shorthand);
    }

    public static final String I18N_KEY = "worstshop.messages.shops.player-shop.";
    public static final NamespacedKey NO_OFFERS_MARKER = new NamespacedKey(WorstShop.get(), "no_offers_marker");
    @Override
    public void onClick(InventoryClickEvent e) {
        boolean isBuying = e.getClick().isLeftClick();
        if (fallback != null && (isBuying ? fallback.buyPrice : fallback.sellPrice) != 0) {
            fallback.onClick(e);
        } else {
            // hack to display message temporarily without too much of a mess
            Inventory bukkitInv = e.getClickedInventory();
            int clickedSlot = e.getSlot();
            ItemStack[] currentItem = {e.getCurrentItem()};
            String itemName = I18n.translate(I18N_KEY + "no-offers-message");
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(WorstShop.get(), () -> {
                ItemStack slotItem = bukkitInv.getItem(clickedSlot);
                if (slotItem != null && !slotItem.getItemMeta().getPersistentDataContainer()
                        .has(NO_OFFERS_MARKER, PersistentDataType.BYTE)) {
                    currentItem[0] = slotItem;
                    bukkitInv.setItem(clickedSlot, ItemBuilder.of(Material.BARRIER)
                            .name(itemName)
                            .meta(meta -> meta.getPersistentDataContainer()
                                    .set(NO_OFFERS_MARKER, PersistentDataType.BYTE, (byte) 1))
                            .build());
                }
            }, 0, 5);
            Bukkit.getScheduler().runTaskLater(WorstShop.get(), () -> {
                task.cancel();
                if (!currentItem[0].getItemMeta().getPersistentDataContainer()
                        .has(NO_OFFERS_MARKER, PersistentDataType.BYTE))
                    bukkitInv.setItem(clickedSlot, currentItem[0]);
            }, 20);
        }
    }

    @Override
    public void influenceItem(Player player, ItemStack readonlyStack, ItemStack stack) {
        if (fallback != null)
            fallback.influenceItem(player, readonlyStack, stack);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        if (fallback != null) {
            map.put("fallback", fallback.toMap(new LinkedHashMap<>()));
        }
        return map;
    }
}
