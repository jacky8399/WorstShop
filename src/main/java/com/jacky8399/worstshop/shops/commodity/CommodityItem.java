package com.jacky8399.worstshop.shops.commodity;

import com.google.common.collect.Sets;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.elements.dynamic.AnimationShopElement;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
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

public class CommodityItem extends Commodity implements IFlexibleCommodity {

    // at least one of them is not null
    @Nullable
    ItemStack stack;
    @Nullable
    NamespacedKey itemTag;
    int tagAmount = 1;
    // never modify the stack directly
    public final double multiplier;
    public HashSet<ItemMatcher> itemMatchers = Sets.newHashSet(ItemMatcher.SIMILAR);

    public CommodityItem(Config config) {
        // parse itemstack
        String item = config.get("item", String.class);
        if (item.startsWith("#")) {
            stack = null;
            // tag
            itemTag = NamespacedKey.fromString(item.substring(1));
            tagAmount = config.find("amount", Integer.class).orElseGet(()->config.find("count", Integer.class).orElse(1));
        } else {
            stack = StaticShopElement.parseItemStack(config);
            itemTag = config.find("accepts", String.class)
                    .map(tag -> tag.startsWith("#") ? tag.substring(1) : tag)
                    .map(NamespacedKey::fromString)
                    .orElse(null);
        }

        multiplier = 1;
        config.findList("matches", String.class).ifPresent(matchers -> {
            itemMatchers.clear();
            matchers.stream()
                    .map(s -> s.toLowerCase(Locale.ROOT).replace(' ', '_'))
                    .map(ItemMatcher.ITEM_MATCHERS::get)
                    .forEach(itemMatchers::add);
        });
    }

    public CommodityItem(ItemStack stack) {
        this(stack, 1);
    }

    public CommodityItem(ItemStack stack, double multiplier) {
        this(stack, null, 1, 1);
    }

    public CommodityItem(ItemStack stack, NamespacedKey tag, int tagAmount, double multiplier) {
        this.stack = stack;
        this.itemTag = tag;
        this.tagAmount = tagAmount;
        this.multiplier = multiplier;
    }

    public CommodityItem(CommodityItem other) {
        this(other, 1);
    }

    public CommodityItem(CommodityItem other, double multiplier) {
        this.stack = other.stack != null ? other.stack.clone() : null;
        this.itemTag = other.itemTag;
        this.tagAmount = other.tagAmount;
        this.multiplier = other.multiplier * multiplier;
        setItemMatchers(other.itemMatchers);
    }

    public CommodityItem setItemMatchers(Collection<ItemMatcher> matchers) {
        itemMatchers.clear();
        itemMatchers.addAll(matchers);
        return this;
    }

    private Tag<Material> getTag() {
        if (itemTag == null)
            throw new IllegalStateException("Item tag is null! (stack: " + stack + ")");
        return Bukkit.getTag(Tag.REGISTRY_ITEMS, itemTag, Material.class);
    }

    public int getAmount() {
        return (int) ((stack != null ? stack.getAmount() : tagAmount) * multiplier);
    }

    @Override
    public ShopElement createElement(TransactionType position) {
        if (stack != null && (itemTag == null || position != TransactionType.COST)) {
            return position.createElement(stack.clone());
        } else {
            Tag<Material> tag = getTag();
            AnimationShopElement elem = new AnimationShopElement(1, tag.getValues().stream()
                    .map(mat -> StaticShopElement.fromStack(new ItemStack(mat, tagAmount)))
                    .collect(Collectors.toList())
            );
            return position.createElement(elem);
        }
    }

    @Override
    public boolean isElementDynamic() {
        return itemTag != null;
    }

    @Override
    public Commodity adjustForPlayer(Player player) {
        // parse placeholders
        return new CommodityItem(Placeholders.setPlaceholders(stack, player), itemTag, tagAmount, multiplier)
                .setItemMatchers(itemMatchers);
    }

    @Override
    public Commodity multiply(double multiplier) {
        return new CommodityItem(this, this.multiplier * multiplier);
    }

    @Override
    public boolean canAfford(Player player) {
        return canAfford(player.getInventory());
    }

    public boolean stackMatches(ItemStack stack) {
        if (stack == null)
            return false;
        if (this.stack == null)
            return getTag().isTagged(stack.getType());
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
        return getInventoryMatching(inventory) / getAmount();
    }

    public int getMaximumMultiplier(ItemStack stack) {
        return stackMatches(stack) ? Math.floorDiv(stack.getAmount(), getAmount()) : 0;
    }

    @Override
    public int getMaximumPurchase(Player player) {
        return stack != null ? stack.getType().getMaxStackSize() * 36 : 64 * 36;
    }

    @Override
    public String getPlayerTrait(Player player) {
        return getInventoryMatchingFormatted(player.getInventory());
    }

    public String getInventoryMatchingFormatted(Inventory inventory) {
        int matching = getInventoryMatching(inventory);
        return stack != null ?
                I18n.nameStack(stack, matching) :
                I18n.translate(I18n.Keys.ITEM_KEY, matching, "#" + itemTag.asString());
    }

    public int getInventoryMatching(Inventory inventory) {
        int amount = 0;
        for (ItemStack playerStack : inventory.getContents()) {
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
        for (ItemStack playerStack : inventory.getContents()) {
            if (playerStack != null && stackMatches(playerStack)) {
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
        int remaining = grant(player.getInventory());
        if (remaining != -1) {
            // get original amount for correct refund
            refund = (double) remaining / (double) (stack != null ? stack.getAmount() : tagAmount);
            player.sendMessage(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.transaction-inv-full"));
        }
        player.updateInventory();
        return refund;
    }

    public int grant(Inventory inventory) {
        ItemStack stack = this.stack;
        if (stack == null) {
            // find first item
            stack = new ItemStack(getTag().getValues().iterator().next(), tagAmount);
        }
        // split into correct stack sizes
        int amount = getAmount(), maxStackSize = stack.getType().getMaxStackSize();
        if (maxStackSize <= 0) maxStackSize = 64;
        List<ItemStack> stacks = new ArrayList<>();
        while (amount > 0) {
            int stackAmount = Math.min(amount, maxStackSize);
            amount -= stackAmount;
            ItemStack s = stack.clone();
            s.setAmount(stackAmount);
            stacks.add(s);
            if (stacks.size() >= 54) {
                // ...why would you buy so many items
                break;
            }
        }
        HashMap<Integer, ItemStack> unfit = inventory.addItem(stacks.toArray(new ItemStack[0]));
        return unfit.size() > 0 ? amount + unfit.values().stream().mapToInt(ItemStack::getAmount).sum() : -1;
    }

    @Override
    public String getPlayerResult(Player player, TransactionType position) {
        int amount = getAmount();
        return stack != null ?
                I18n.nameStack(stack, amount) :
                I18n.translate(I18n.Keys.ITEM_KEY, amount, "#" + itemTag.asString());
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "item");
        map.put("matches", itemMatchers.stream()
                .map(matcher -> matcher.name)
                .map(name -> name.toLowerCase(Locale.ROOT).replace('_', ' '))
                .collect(Collectors.toList()));
        if (stack != null) {
            StaticShopElement.serializeItemStack(stack, map);
            if (itemTag != null)
                map.put("accepts", itemTag.asString());
        } else {
            map.put("item", "#" + itemTag.asString());
            if (tagAmount != 1)
                map.put("amount", tagAmount);
        }
        return map;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stack, itemMatchers, multiplier);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CommodityItem))
            return false;
        CommodityItem other = (CommodityItem) obj;
        return other.multiplier == multiplier && other.stack.equals(stack) && other.itemMatchers.equals(itemMatchers);
    }

    @Override
    public String toString() {
        return "[give/take " + getPlayerResult(null, null) +
                (itemMatchers.size() != 0 ?
                        "by matching " + itemMatchers.stream()
                                .map(matcher -> matcher.name)
                                .collect(Collectors.joining(", ")) :
                        ""
                ) +
                "]";
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
