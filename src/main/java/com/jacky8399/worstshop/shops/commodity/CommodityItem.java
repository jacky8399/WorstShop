package com.jacky8399.worstshop.shops.commodity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.elements.dynamic.AnimationShopElement;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CommodityItem extends Commodity implements IFlexibleCommodity {

    @NotNull
    ItemStack stack;
    /**
     * Additional Materials/NamespacedKeys that would be accepted as cost
     */
    @NotNull
    ImmutableList<NamespacedKey> accepted;
    int amount;
    // never modify the stack directly
    public final double multiplier;
    public HashSet<ItemMatcher> itemMatchers = Sets.newHashSet(ItemMatcher.SIMILAR);

    public static final Function<String, NamespacedKey> STRING_TO_KEY =
            key -> NamespacedKey.fromString((key.startsWith("#") ? key.substring(1) : key).replace(' ', '_'));
    public static final Function<NamespacedKey, String> KEY_TO_STRING =
            key -> key.getNamespace().equals(NamespacedKey.MINECRAFT) ? key.getKey() : key.toString();
    public CommodityItem(Config config) {
        // parse item stack
        String item = config.get("item", String.class);
        if (item.startsWith("#")) {
            // tag
            NamespacedKey key = NamespacedKey.fromString(item.substring(1));
            if (key == null)
                throw new ConfigException(item.substring(1) + " is not a valid namespaced key!", config, "item");
            accepted = ImmutableList.of(key);
            stack = new ItemStack(Material.AIR);
        } else {
            ItemStack stack = StaticShopElement.parseItemStack(config);
            if (stack != null)
                this.stack = ItemUtils.removeSafetyKey(stack);
            else
                this.stack = new ItemStack(Material.AIR);

            Optional<NamespacedKey> acceptedString = config.tryFind("accepts", String.class).map(STRING_TO_KEY);
            if (acceptedString.isPresent()) {
                accepted = ImmutableList.of(acceptedString.get());
            } else {
                Optional<List<String>> acceptedList = config.findList("accepts", String.class);
                if (acceptedList.isPresent())
                    accepted = acceptedList.get().stream().map(STRING_TO_KEY)
                            .filter(Objects::nonNull)
                            .collect(ImmutableList.toImmutableList());
                else
                    accepted = ImmutableList.of();
            }
        }
        amount = config.find("amount", Integer.class).orElseGet(()->config.find("count", Integer.class).orElse(1));

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
        this(stack, ImmutableList.of(), stack.getAmount(), 1);
    }

    public CommodityItem(@NotNull ItemStack stack, @NotNull List<NamespacedKey> accepted, int amount, double multiplier) {
        this.stack = stack;
        this.accepted = ImmutableList.copyOf(accepted);
        this.amount = amount;
        this.multiplier = multiplier;
    }

    public CommodityItem(CommodityItem other) {
        this(other, 1);
    }

    public CommodityItem(CommodityItem other, double multiplier) {
        this.stack = other.stack.clone();
        this.accepted = ImmutableList.copyOf(other.accepted);
        this.amount = other.amount;
        this.multiplier = other.multiplier * multiplier;
        setItemMatchers(other.itemMatchers);
    }

    public CommodityItem setItemMatchers(Collection<ItemMatcher> matchers) {
        itemMatchers.clear();
        itemMatchers.addAll(matchers);
        return this;
    }

    @NotNull
    public Set<Material> getExtraAcceptedItems() {
        EnumSet<Material> materials = EnumSet.noneOf(Material.class);
        for (NamespacedKey key : accepted) {
            Material mat = Registry.MATERIAL.get(key);
            if (mat != null) {
                materials.add(mat);
            } else {
                Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
                if (tag != null) {
                    materials.addAll(tag.getValues());
                }
            }
        }
        return materials.size() == 0 ? Collections.emptySet() : materials;
    }

    public int getAmount() {
        return (int) (amount * multiplier);
    }

    @SuppressWarnings({"ConstantConditions", "deprecation"})
    private void ensureStackSize(@NotNull ItemStack stack) {
        if (amount > stack.getMaxStackSize()) {
            stack.setAmount(1);
            ItemMeta meta = stack.getItemMeta();
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add(I18n.translate(I18n.Keys.ITEM_KEY, "", amount));
            meta.setLore(lore);
            stack.setItemMeta(meta);
        } else {
            stack.setAmount(amount);
        }
    }

    private transient ShopElement displayElem;
    @Override
    public ShopElement createElement(TransactionType position) {
        if (displayElem == null) {
            Set<Material> acceptedItems = getExtraAcceptedItems();
            // only display one item if reward
            if (position != TransactionType.COST) {
                ItemStack actualStack;
                if (stack.getType() != Material.AIR)
                    actualStack = stack.clone();
                else
                    actualStack = new ItemStack(acceptedItems.iterator().next());
                ensureStackSize(actualStack);
                displayElem = position.createElement(actualStack);
            } else {
                List<StaticShopElement> displayedItems = new ArrayList<>(acceptedItems.size() + 1);
                if (stack.getType() != Material.AIR) {
                    ItemStack actualStack = stack.clone();
                    ensureStackSize(actualStack);
                    displayedItems.add(StaticShopElement.fromStack(actualStack));
                }
                for (Material mat : acceptedItems) {
                    ItemStack stack = new ItemStack(mat, amount);
                    ensureStackSize(stack);
                    displayedItems.add(StaticShopElement.fromStack(stack));
                }

                AnimationShopElement elem = new AnimationShopElement(1, displayedItems);
                displayElem = position.createElement(elem);
            }
        }
        return displayElem;
    }

    @Override
    public boolean isElementDynamic() {
        return accepted.size() != 0;
    }

    @Override
    public Commodity adjustForPlayer(Player player) {
        // parse placeholders
        return new CommodityItem(Placeholders.setPlaceholders(stack, player), accepted, amount, multiplier)
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

    public boolean stackMatches(@Nullable ItemStack stack, @NotNull Set<Material> cache) {
        if (stack == null)
            return false;
        if (cache.contains(stack.getType()))
            return true;
        for (ItemMatcher matcher : itemMatchers) {
            if (!matcher.test(this.stack, stack)) {
                return false;
            }
        }
        return true;
    }

    public boolean stackMatches(ItemStack stack) {
        return stackMatches(stack, getExtraAcceptedItems());
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
        return stack.getType() != Material.AIR ? stack.getType().getMaxStackSize() * 36 : 64 * 36;
    }

    @Override
    public String getPlayerTrait(Player player) {
        return getInventoryMatchingFormatted(player.getInventory());
    }

    public String getInventoryMatchingFormatted(Inventory inventory) {
        StringJoiner joiner = new StringJoiner("\n");

        Map<ItemStack, Integer> matching = new HashMap<>();
        Set<Material> cache = getExtraAcceptedItems();
        for (ItemStack playerStack : inventory.getContents()) {
            if (playerStack != null && stackMatches(playerStack, cache)) {
                ItemStack one = playerStack.clone();
                one.setAmount(1);
                matching.merge(one, playerStack.getAmount(), Integer::sum);
            }
        }
        if (matching.size() == 0) {
            return stack.getType() != Material.AIR ?
                    I18n.nameStack(stack, 0) :
                    I18n.translate(I18n.Keys.ITEM_KEY, amount, PaperHelper.getItemName(new ItemStack(cache.iterator().next())));
        }
        matching.forEach((is, amount) -> joiner.add(I18n.nameStack(is, amount)));
        return joiner.toString();
    }

    public int getInventoryMatching(Inventory inventory) {
        int amount = 0;
        Set<Material> cache = getExtraAcceptedItems();
        for (ItemStack playerStack : inventory.getContents()) {
            if (playerStack != null && stackMatches(playerStack, cache)) {
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
            refund = (double) remaining / amount;
            player.sendMessage(I18n.translate(I18n.Keys.MESSAGES_KEY + "shops.transaction-inv-full"));
        }
        player.updateInventory();
        return refund;
    }

    public int grant(Inventory inventory) {
        ItemStack stack = this.stack;
        if (stack.getType() == Material.AIR) {
            // find first item
            stack = new ItemStack(getExtraAcceptedItems().iterator().next(), amount);
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
        return I18n.nameStack(stack, amount);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "item");
        map.put("matches", itemMatchers.stream()
                .map(matcher -> matcher.name)
                .map(name -> name.toLowerCase(Locale.ROOT).replace('_', ' '))
                .collect(Collectors.toList()));
        if (stack.getType() != Material.AIR) {
            StaticShopElement.serializeItemStack(stack, map);
            if (accepted.size() != 0)
                map.put("accepts", accepted.stream().map(KEY_TO_STRING).collect(Collectors.toList()));
        } else {
            map.put("item", "#" + KEY_TO_STRING.apply(accepted.get(0)));
        }
        if (amount != 1)
            map.put("amount", amount);
        return map;
    }

    @Override
    public int hashCode() {
        return Objects.hash(stack, itemMatchers, multiplier);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof CommodityItem other))
            return false;
        return other.multiplier == multiplier &&
                Objects.equals(other.stack, stack) &&
                Objects.equals(other.accepted, accepted) &&
                other.amount == amount &&
                other.itemMatchers.equals(itemMatchers);
    }

    @Override
    public String toString() {
        ItemStack fakeStack = stack.clone();
        fakeStack.setAmount(1);
        return "[give/take " + fakeStack + "x" + getAmount() +
                (accepted.size() != 0 ?
                        "accepting extra [" + accepted.stream()
                                .map(NamespacedKey::asString)
                                .collect(Collectors.joining(",")) + "]" :
                        "") +
                // only display non default item matchers
                (itemMatchers.size() != 0 && !(itemMatchers.size() == 1 && itemMatchers.contains(ItemMatcher.SIMILAR)) ?
                        " by matching " + itemMatchers.stream()
                                .map(matcher -> matcher.name)
                                .collect(Collectors.joining(", ")) :
                        ""
                ) +
                "]";
    }

    @SuppressWarnings({"StaticInitializerReferencesSubClass", "unused", "deprecation"})
    public static abstract class ItemMatcher implements BiPredicate<ItemStack, ItemStack> {
        public static final HashMap<String, ItemMatcher> ITEM_MATCHERS = new HashMap<>();

        public static final ItemMatcher EQUALITY = of("equality", ItemStack::equals);
        public static final ItemMatcher SIMILAR = of("similar", ItemStack::isSimilar);
        public static final ItemMatcher MATERIAL = of("material", (s1, s2) -> s1.getType() == s2.getType());
        public static final ItemMatcher DAMAGE = compareProperty("damage", Damageable.class, Damageable::getDamage);
        public static final ItemMatcher NAME = compareProperty("name", ItemMeta::hasDisplayName, ItemMeta::getDisplayName);
        public static final ItemMatcher LORE = compareProperty("lore", ItemMeta::hasLore, ItemMeta::getLore);
        public static final ItemMatcher CUSTOM_MODEL_DATA = compareProperty("custom_model_data", ItemMeta::hasCustomModelData, ItemMeta::getCustomModelData);
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
