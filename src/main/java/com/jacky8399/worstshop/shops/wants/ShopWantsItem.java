package com.jacky8399.worstshop.shops.wants;

import com.google.common.collect.Sets;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ShopWantsItem extends ShopWants implements IFlexibleShopWants {

    ItemStack stack;
    // never modify the stack directly
    public final double multiplier;
    public HashSet<ItemMatcher> itemMatchers = Sets.newHashSet(ItemMatcher.SIMILAR);

    public ShopWantsItem(Map<String, Object> yaml) {
        // parse itemstack
        this(StaticShopElement.parseItemStack(yaml), 1);
        if (yaml.containsKey("matches") /* not a typo */) {
            List<String> matchers = (List<String>) yaml.get("matches");
            itemMatchers.clear();
            matchers.stream().map(s -> s.toLowerCase().replace(' ', '_'))
                    .map(ItemMatcher.ITEM_MATCHERS::get).forEach(itemMatchers::add);
        }
    }

    public ShopWantsItem(ItemStack stack) {
        this(stack, 1);
    }

    public ShopWantsItem(ItemStack stack, double multiplier) {
        this.stack = stack;
        this.multiplier = multiplier;
    }

    public ShopWantsItem(ShopWantsItem other) {
        this(other, 1);
    }

    public ShopWantsItem(ShopWantsItem other, double multiplier) {
        this.stack = other.stack.clone();
        this.multiplier = other.multiplier * multiplier;
        setItemMatchers(other.itemMatchers);
    }

    public ShopWantsItem setItemMatchers(Collection<ItemMatcher> matchers) {
        itemMatchers.clear();
        itemMatchers.addAll(matchers);
        return this;
    }

    public int getAmount() {
        return (int) (stack.getAmount() * multiplier);
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        return position.createElement(stack.clone());
    }

    @Override
    public ShopWants adjustForPlayer(Player player) {
        // parse placeholders
        return new ShopWantsItem(StaticShopElement.replacePlaceholders(player, stack), multiplier)
                .setItemMatchers(itemMatchers);
    }

    @Override
    public ShopWants multiply(double multiplier) {
        return new ShopWantsItem(this, this.multiplier * multiplier);
    }

    @Override
    public boolean canAfford(Player player) {
        return canAfford(player.getInventory());
    }

    public boolean stackMatches(ItemStack stack) {
        if (stack == null)
            return false;
        for (ItemMatcher matcher : itemMatchers) {
            if (!matcher.test(this.stack, stack)) {
                return false;
            }
        }
        return true;
    }

    public boolean canAfford(Inventory inventory) {
        return getInventoryMatching(inventory) >= getAmount();
    }

    public boolean canAfford(ItemStack stack) {
        return stackMatches(stack) && stack.getAmount() >= getAmount();
    }

    // Override parent method for better performance
    @Override
    public int getMaximumMultiplier(Player player) {
        return getMaximumMultiplier(player.getInventory());
    }

    public int getMaximumMultiplier(Inventory inventory) {
        return Math.floorDiv(getInventoryMatching(inventory), getAmount());
    }

    public int getMaximumMultiplier(ItemStack stack) {
        return stackMatches(stack) ? Math.floorDiv(stack.getAmount(), getAmount()) : 0;
    }

    @Override
    public String getPlayerTrait(Player player) {
        return getInventoryMatchingFormatted(player.getInventory());
    }

    public String getInventoryMatchingFormatted(Inventory inventory) {
        return I18n.nameStack(stack, getInventoryMatching(inventory));
    }

    public int getInventoryMatching(Inventory inventory) {
        int amount = 0;
        for (ItemStack playerStack : inventory.getStorageContents()) {
            if (playerStack != null && stackMatches(playerStack)) {
                amount += playerStack.getAmount();
            }
        }
        return amount;
    }

    @Override
    public void deduct(Player player) {
        deduct(player.getInventory());
        player.updateInventory();
    }

    public void deduct(Inventory inventory) {
        int amount = getAmount();
        for (ItemStack playerStack : inventory.getStorageContents()) {
            if (stackMatches(playerStack)) {
                if (playerStack.getAmount() >= amount) {
                    // has more than enough
                    playerStack.setAmount(playerStack.getAmount() - amount);
                    break; // exit loop
                } else {
                    // does not have enough
                    amount -= playerStack.getAmount();
                    playerStack.setAmount(0);
                }
            }
        }
    }

    public void deduct(ItemStack stack) {
        stack.setAmount(stack.getAmount() - getAmount());
    }

    @Override
    public double grantOrRefund(Player player) {
        double refund = 0;
        ItemStack remaining = grant(player.getInventory());
        if (remaining != null) {
            refund = (double) remaining.getAmount() / (double) stack.getAmount(); // get original amount for correct refund
            player.sendMessage(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.transaction-inv-full"));
        }
        player.updateInventory();
        return refund;
    }

    public ItemStack grant(Inventory inventory) {
        ItemStack newIs = stack.clone();
        newIs.setAmount(getAmount());
        HashMap<Integer, ItemStack> unfit = inventory.addItem(newIs);
        return unfit.size() > 0 ? unfit.get(0) : null;
    }

    @Override
    public String getPlayerResult(Player player, TransactionType position) {
        return I18n.nameStack(stack, getAmount());
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "item");
        map.put("matches", itemMatchers.stream()
                .map(matcher -> matcher.name)
                .map(name -> name.toLowerCase(Locale.ROOT).replace('_', ' '))
                .collect(Collectors.toList()));
        StaticShopElement.serializeItemStack(stack, map);
        return map;
    }

    @SuppressWarnings({"StaticInitializerReferencesSubClass", "unused"})
    public static abstract class ItemMatcher implements BiPredicate<ItemStack, ItemStack> {
        public static final HashMap<String, ItemMatcher> ITEM_MATCHERS = new HashMap<>();

        public static final ItemMatcher EQUALITY = of("equality", ItemStack::equals);
        public static final ItemMatcher SIMILAR = of("similar", ItemStack::isSimilar);
        public static final ItemMatcher MATERIAL = of("material", (s1, s2) -> s1.getType() == s2.getType());
        public static final ItemMatcher DAMAGE = compareProperty("damage", Damageable.class, Damageable::getDamage);
        public static final ItemMatcher NAME = compareProperty("name", ItemMeta::hasDisplayName, ItemMeta::getDisplayName);
        public static final ItemMatcher LORE = compareProperty("lore", ItemMeta::hasLore, ItemMeta::getLore);
        public static final ItemMatcher ENCHANTS = compareProperty("enchants", ItemMeta::getEnchants);
        public static final ItemMatcher PLUGIN_DATA = compareProperty("plugin_data", ItemMeta::getPersistentDataContainer);
        public static final ItemMatcher SKULL = compareProperty("skull", SkullMeta.class, SkullMeta::getOwningPlayer);
        public final String name;

        public ItemMatcher(String name) {
            if (ITEM_MATCHERS.containsKey(name))
                throw new IllegalStateException(name + " already exists!");
            this.name = name;
            ITEM_MATCHERS.put(name, this);
        }

        public static ItemMatcherPredicate of(String name, BiPredicate<ItemStack, ItemStack> predicate) {
            return new ItemMatcherPredicate(name, predicate);
        }

        public static ItemMatcherPredicate ofMeta(String name, BiPredicate<ItemMeta, ItemMeta> predicate) {
            return ItemMatcher.of(name, (s1, s2) -> {
               ItemMeta m1 = s1.getItemMeta(), m2 = s2.getItemMeta();
               return predicate.test(m1, m2);
            });
        }

        public static <T> ItemMatcher compareProperty(String name, Function<ItemMeta, @Nullable T> mapper) {
            return ofMeta(name, (m1, m2) -> Objects.equals(mapper.apply(m1), mapper.apply(m2)));
        }

        public static <T> ItemMatcher compareProperty(String name, Predicate<ItemMeta> precondition, Function<ItemMeta, @Nullable T> mapper) {
            return ofMeta(name, (m1, m2) -> precondition.test(m1) == precondition.test(m2) &&
                    (!(precondition.test(m1)) || Objects.equals(mapper.apply(m1), mapper.apply(m2))));
        }

        public static <T, V> ItemMatcher compareProperty(String name, Class<T> metaClazz, Function<T, V> mapper) {
            return compareProperty(name, metaClazz::isInstance, mapper.compose(metaClazz::cast));
        }

    }

    public static class ItemMatcherPredicate extends ItemMatcher {
        public final BiPredicate<ItemStack, ItemStack> predicate;
        public ItemMatcherPredicate(String name, BiPredicate<ItemStack, ItemStack> predicate) {
            super(name);
            this.predicate = predicate;
        }

        @Override
        public boolean test(ItemStack stack, ItemStack stack2) {
            return predicate.test(stack, stack2);
        }
    }
}
