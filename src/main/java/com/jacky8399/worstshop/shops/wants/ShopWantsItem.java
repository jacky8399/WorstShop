package com.jacky8399.worstshop.shops.wants;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class ShopWantsItem extends ShopWants implements IFlexibleShopWants {

    ItemStack stack;
    // never modify the stack directly
    public final double multiplier;
    public HashSet<ItemMatcher> itemMatchers = Sets.newHashSet(SIMILAR);

    public ShopWantsItem(Map<String, Object> yaml) {
        // parse itemstack
        this(StaticShopElement.parseItemStack(yaml), 1);
        if (yaml.containsKey("matches") /* not a typo */) {
            List<String> matchers = (List<String>) yaml.get("matches");
            itemMatchers.clear();
            matchers.stream().map(s -> s.toLowerCase().replace(' ', '_'))
                    .map(ITEM_MATCHERS::get).forEach(itemMatchers::add);
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
    public ItemStack createStack() {
        return stack.clone();
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
            if (!matcher.apply(this.stack, stack)) {
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
    public String getPlayerResult(Player player, ElementPosition position) {
        return I18n.nameStack(stack, getAmount());
    }

    private static <T> ItemMatcher compareProperty(Function<ItemMeta, @Nullable T> mapper) {
        return (s1, s2) -> {
            ItemMeta m1 = s1.getItemMeta(), m2 = s2.getItemMeta();
            return Objects.equals(mapper.apply(m1), mapper.apply(m2));
        };
    }
    private static <T> ItemMatcher compareProperty(Predicate<ItemMeta> precondition, Function<ItemMeta, @Nullable T> mapper) {
        return (s1, s2) -> {
            ItemMeta m1 = s1.getItemMeta(), m2 = s2.getItemMeta();
            return precondition.test(m1) == precondition.test(m2) &&
                    (!(precondition.test(m1)) || Objects.equals(mapper.apply(m1), mapper.apply(m2)));
        };
    }

    public static final ItemMatcher SIMILAR = ItemStack::isSimilar;
    public static final ItemMatcher MATERIAL = (s1, s2) -> s1.getType() == s2.getType();
    public static final ItemMatcher DAMAGE = compareProperty(Damageable.class::isInstance, meta->((Damageable) meta).getDamage());
    public static final ItemMatcher NAME = compareProperty(ItemMeta::hasDisplayName, ItemMeta::getDisplayName);
    public static final ItemMatcher LORE = compareProperty(ItemMeta::hasLore, ItemMeta::getLore);
    public static final ItemMatcher ENCHANTS = compareProperty(ItemMeta::getEnchants);
    public static final ItemMatcher PLUGIN_DATA = compareProperty(ItemMeta::getPersistentDataContainer);

    public static final HashMap<String, ItemMatcher> ITEM_MATCHERS;

    static {
        ITEM_MATCHERS = Maps.newHashMap();
        ITEM_MATCHERS.put("similar", SIMILAR);
        ITEM_MATCHERS.put("material", MATERIAL);
        ITEM_MATCHERS.put("damage", DAMAGE);
        ITEM_MATCHERS.put("name", NAME);
        ITEM_MATCHERS.put("lore", LORE);
        ITEM_MATCHERS.put("enchants", ENCHANTS);
        ITEM_MATCHERS.put("plugin_data", PLUGIN_DATA);
    }

    public interface ItemMatcher extends BiFunction<ItemStack, ItemStack, Boolean> { }
}
