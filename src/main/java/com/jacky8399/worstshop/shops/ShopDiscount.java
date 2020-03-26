package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

public class ShopDiscount {

    public static class Entry {

        public Entry(LocalDateTime expiry, double percentage) {
            this.expiry = expiry;
            this.percentage = percentage;
        }

        public final LocalDateTime expiry;
        public final double percentage;
        public String shop;
        public Material material;
        public UUID player;
        public String permission;

        public boolean hasExpired() {
            return LocalDateTime.now().isAfter(expiry);
        }

        public boolean isApplicableTo(Shop shop, Material material, Player player) {
            if (this.shop != null && !this.shop.equals(shop.id)) {
                return false;
            }
            if (this.material != null && this.material != material) {
                return false;
            }
            if (this.player != null && !player.getUniqueId().equals(this.player)) {
                return false;
            }
            return this.permission == null || player.hasPermission(permission);
        }
    }

    public static final HashMap<String, List<Entry>> BY_SHOP = Maps.newHashMap();
    public static final EnumMap<Material, List<Entry>> BY_MATERIAL = Maps.newEnumMap(Material.class);
    public static final HashMap<UUID, List<Entry>> BY_PLAYER = Maps.newHashMap();
    public static final List<Entry> NO_CRITERIA = Lists.newArrayList();

    public static final List<Entry> ALL_DISCOUNTS = Lists.newArrayList();

    public static List<Entry> findApplicableEntries(Shop shop, Material material, Player player) {
        Objects.requireNonNull(shop, "shop cannot be null");
        Objects.requireNonNull(material, "material cannot be null");
        Objects.requireNonNull(player, "player cannot be null");
        List<Entry> all = Lists.newArrayList();
        Predicate<Entry> applicableCheck = entry -> entry.isApplicableTo(shop, material, player);
        // stale check
        List<Entry> obsoleteDiscounts = Lists.newArrayList();
        Predicate<Entry> staleCheck = entry -> {
            if (entry.hasExpired()) {
                obsoleteDiscounts.add(entry);
                return false;
            }
            return true;
        };
        // combine both
        Predicate<Entry> check = applicableCheck.and(staleCheck);
        if (BY_SHOP.containsKey(shop.id)) {
            BY_SHOP.get(shop.id).stream().filter(check).forEach(all::add);
        }
        if (BY_MATERIAL.containsKey(material)) {
            BY_MATERIAL.get(material).stream().filter(check).forEach(all::add);
        }
        if (BY_PLAYER.containsKey(player.getUniqueId())) {
            BY_PLAYER.get(player.getUniqueId()).stream().filter(check).forEach(all::add);
        }
        NO_CRITERIA.stream().filter(check).forEach(all::add);
        // remove stale discounts
        obsoleteDiscounts.forEach(ShopDiscount::removeDiscountEntry);
        return all;
    }

    /**
     * @return a number to be multiplied to the price
     */
    public static double calcFinalPrice(List<Entry> entries) {
        return entries.stream().mapToDouble(entry->entry.percentage).reduce(1, (d1, d2)->d1 * d2);
    }

    /**
     * Register the entry to the respective maps
     * @param entry entry
     */
    public static void addDiscountEntry(Entry entry) {
        boolean isNoCriteria = true;
        ALL_DISCOUNTS.add(entry);
        if (entry.shop != null) {
            BY_SHOP.computeIfAbsent(entry.shop, key->Lists.newArrayList()).add(entry);
            isNoCriteria = false;
        }
        if (entry.material != null) {
            BY_MATERIAL.computeIfAbsent(entry.material, key->Lists.newArrayList()).add(entry);
            isNoCriteria = false;
        }
        if (entry.player != null) {
            BY_PLAYER.computeIfAbsent(entry.player, key->Lists.newArrayList()).add(entry);
            isNoCriteria = false;
        }
        // the entry cannot be discovered by other criteria
        if (isNoCriteria) {
            NO_CRITERIA.add(entry);
        }
    }

    public static void removeDiscountEntry(Entry entry) {
        ALL_DISCOUNTS.remove(entry);
        boolean isNoCriteria = true;
        if (entry.shop != null) {
            BY_SHOP.get(entry.shop).remove(entry);
            isNoCriteria = false;
        }
        if (entry.material != null) {
            BY_MATERIAL.get(entry.material).remove(entry);
            isNoCriteria = false;
        }
        if (entry.player != null) {
            BY_PLAYER.get(entry.player).remove(entry);
            isNoCriteria = false;
        }
        // the entry cannot be discovered by other criteria
        if (isNoCriteria) {
            NO_CRITERIA.remove(entry);
        }
    }
}
