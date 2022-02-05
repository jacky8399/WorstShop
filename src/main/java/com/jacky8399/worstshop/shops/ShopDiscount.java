package com.jacky8399.worstshop.shops;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Consumer;

public class ShopDiscount {

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

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", name);
            if (expiry != null)
                map.put("expiry", expiry.toEpochSecond(ZoneOffset.UTC)); // doesn't matter, will be read as UTC too
            map.put("percentage", percentage);
            if (shop != null)
                map.put("shop", shop.id);
            if (material != null)
                map.put("material", material.name());
            if (player != null)
                map.put("player", player.toString());
            if (permission != null)
                map.put("permission", permission);
            return map;
        }

        public static Entry fromMap(Map<String, Object> map) {
            String name = (String) map.get("name");
            Number expiry = ((Number) map.get("expiry"));
            LocalDateTime expiryDateTime = expiry != null && expiry.longValue() != -1 ?
                    LocalDateTime.ofEpochSecond(expiry.longValue(),0 ,ZoneOffset.UTC) : null;
            double percentage = ((Number) map.get("percentage")).doubleValue();
            ShopReference shop = map.containsKey("shop") ? ShopReference.of((String) map.get("shop")) : null;
            Material material = map.containsKey("material") ? Material.getMaterial((String) map.get("material")) : null;
            UUID player = map.containsKey("player") ? UUID.fromString((String) map.get("player")) : null;
            String permission = map.containsKey("permission") ? (String) map.get("permission") : null;
            return new Entry(name, expiryDateTime, percentage, shop, material, player, permission);
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

    public static Collection<Entry> findApplicableEntries(Shop shop, Material material, Player player) {
        Objects.requireNonNull(shop, "shop cannot be null");
        Objects.requireNonNull(material, "material cannot be null");
        Objects.requireNonNull(player, "player cannot be null");
        Set<Entry> all = new HashSet<>();
        Set<Entry> obsoleteDiscounts = new HashSet<>();
        Consumer<Entry> checkAndAdd = entry -> {
            if (!entry.isApplicableTo(shop, material, player))
                return;
            if (entry.hasExpired()) {
                obsoleteDiscounts.add(entry);
                return;
            }
            all.add(entry);
        };
        ShopReference shopRef = ShopReference.of(shop);
        Set<Entry> byShop = BY_SHOP.get(shopRef);
        if (byShop != null) {
            byShop.forEach(checkAndAdd);
        }
        Set<Entry> byMaterial = BY_MATERIAL.get(material);
        if (byMaterial != null) {
            byMaterial.forEach(checkAndAdd);
        }
        Set<Entry> byPlayer = BY_PLAYER.get(player.getUniqueId());
        if (byPlayer != null) {
            byPlayer.forEach(checkAndAdd);
        }
        NO_CRITERIA.forEach(checkAndAdd);
        // remove stale discounts
        obsoleteDiscounts.forEach(ShopDiscount::removeDiscountEntry);
        return all;
    }

    /**
     * @return a number to be multiplied to the price
     */
    public static double calcFinalPrice(Collection<Entry> entries) {
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
