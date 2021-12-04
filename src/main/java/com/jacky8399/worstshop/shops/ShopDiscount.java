package com.jacky8399.worstshop.shops;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;

public class ShopDiscount {

    @ConfigSerializable
    public record Entry(String name, @Nullable LocalDateTime expiry, double percentage,
                        ShopReference shop, Material material,
                        UUID player, String permission) {
        public boolean hasExpired() {
            return expiry != null && LocalDateTime.now().isAfter(expiry);
        }

        public boolean isApplicableTo(@NotNull Shop shop, @NotNull Material material, @NotNull Player player) {
            if (this.shop != null && !this.shop.refersTo(shop)) {
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

    public static final HashMap<ShopReference, Set<Entry>> BY_SHOP = new HashMap<>();
    public static final EnumMap<Material, Set<Entry>> BY_MATERIAL = new EnumMap<>(Material.class);
    public static final HashMap<UUID, Set<Entry>> BY_PLAYER = new HashMap<>();
    public static final Set<Entry> NO_CRITERIA = new HashSet<>();

    public static final HashMap<String, Entry> ALL_DISCOUNTS = new HashMap<>();

    public static void clearDiscounts() {
        ALL_DISCOUNTS.clear();
        BY_SHOP.clear();
        BY_PLAYER.clear();
        BY_MATERIAL.clear();
        NO_CRITERIA.clear();
    }

    public static List<Entry> findApplicableEntries(Shop shop, Material material, Player player) {
        Objects.requireNonNull(shop, "shop cannot be null");
        Objects.requireNonNull(material, "material cannot be null");
        Objects.requireNonNull(player, "player cannot be null");
        List<Entry> all = new ArrayList<>();
        Predicate<Entry> applicableCheck = entry -> entry.isApplicableTo(shop, material, player);
        // stale check
        Set<Entry> obsoleteDiscounts = new HashSet<>();
        Predicate<Entry> staleCheck = entry -> {
            if (entry.hasExpired()) {
                obsoleteDiscounts.add(entry);
                return false;
            }
            return true;
        };
        // combine both
        Predicate<Entry> check = applicableCheck.and(staleCheck);
        ShopReference shopRef = ShopReference.of(shop);
        if (BY_SHOP.containsKey(shopRef)) {
            BY_SHOP.get(shopRef).stream().filter(check).forEach(all::add);
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
        return entries.stream().mapToDouble(Entry::percentage).reduce(1, (d1, d2)->d1 * d2);
    }

    /**
     * Register the entry to the respective maps
     * @param entry entry
     */
    public static void addDiscountEntry(Entry entry) {
        boolean isNoCriteria = true;
        ALL_DISCOUNTS.put(entry.name, entry);
        if (entry.shop != null) {
            BY_SHOP.computeIfAbsent(entry.shop, key->new HashSet<>()).add(entry);
            isNoCriteria = false;
        }
        if (entry.material != null) {
            BY_MATERIAL.computeIfAbsent(entry.material, key->new HashSet<>()).add(entry);
            isNoCriteria = false;
        }
        if (entry.player != null) {
            BY_PLAYER.computeIfAbsent(entry.player, key->new HashSet<>()).add(entry);
            isNoCriteria = false;
        }
        // the entry cannot be discovered by other criteria
        if (isNoCriteria) {
            NO_CRITERIA.add(entry);
        }
    }

    public static void removeDiscountEntry(Entry entry) {
        ALL_DISCOUNTS.remove(entry.name);
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
