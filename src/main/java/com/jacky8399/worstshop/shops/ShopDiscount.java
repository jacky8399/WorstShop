package com.jacky8399.worstshop.shops;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Predicate;

public class ShopDiscount {

    public static class Entry {

        public Entry(String name, LocalDateTime expiry, double percentage) {
            this.expiry = expiry;
            this.percentage = percentage;
        }
        public String name;
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

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Entry)) {
                return false;
            }
            Entry other = (Entry) obj;
            return expiry.equals(other.expiry) && percentage == other.percentage &&
                    Objects.equals(shop, other.shop) && Objects.equals(material, other.material) &&
                    Objects.equals(player, other.player) && Objects.equals(permission, other.permission);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = Maps.newHashMap();
            map.put("name", name);
            map.put("expiry", expiry.toEpochSecond(ZoneOffset.UTC)); // doesn't matter, will be read as UTC too
            map.put("percentage", percentage);
            if (shop != null)
                map.put("shop", shop);
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
            int expiry = ((Number) map.get("expiry")).intValue();
            double percentage = ((Number) map.get("percentage")).doubleValue();
            Entry entry = new Entry(name, LocalDateTime.ofEpochSecond(expiry, 0, ZoneOffset.UTC), percentage);
            if (map.containsKey("shop"))
                entry.shop = (String) map.get("shop");
            if (map.containsKey("material"))
                entry.material = Material.getMaterial((String) map.get("material"));
            if (map.containsKey("player"))
                entry.player = UUID.fromString((String) map.get("player"));
            if (map.containsKey("permission"))
                entry.permission = (String) map.get("permission");
            return entry;
        }
    }

    public static final HashMap<String, Set<Entry>> BY_SHOP = Maps.newHashMap();
    public static final EnumMap<Material, Set<Entry>> BY_MATERIAL = Maps.newEnumMap(Material.class);
    public static final HashMap<UUID, Set<Entry>> BY_PLAYER = Maps.newHashMap();
    public static final Set<Entry> NO_CRITERIA = Sets.newHashSet();

    public static final HashMap<String, Entry> ALL_DISCOUNTS = Maps.newHashMap();

    public static List<Entry> findApplicableEntries(Shop shop, Material material, Player player) {
        Objects.requireNonNull(shop, "shop cannot be null");
        Objects.requireNonNull(material, "material cannot be null");
        Objects.requireNonNull(player, "player cannot be null");
        List<Entry> all = Lists.newArrayList();
        Predicate<Entry> applicableCheck = entry -> entry.isApplicableTo(shop, material, player);
        // stale check
        Set<Entry> obsoleteDiscounts = Sets.newHashSet();
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
        ALL_DISCOUNTS.put(entry.name, entry);
        if (entry.shop != null) {
            BY_SHOP.computeIfAbsent(entry.shop, key->Sets.newHashSet()).add(entry);
            isNoCriteria = false;
        }
        if (entry.material != null) {
            BY_MATERIAL.computeIfAbsent(entry.material, key->Sets.newHashSet()).add(entry);
            isNoCriteria = false;
        }
        if (entry.player != null) {
            BY_PLAYER.computeIfAbsent(entry.player, key->Sets.newHashSet()).add(entry);
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
