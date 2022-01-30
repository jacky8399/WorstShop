package com.jacky8399.worstshop.shops.elements;

import com.google.common.collect.ImmutableSet;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.Editable;
import com.jacky8399.worstshop.editor.Property;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import com.jacky8399.worstshop.shops.rendering.RenderElement;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Editable
public class StaticShopElement extends ShopElement {
    public static NamespacedKey SAFETY_KEY = new NamespacedKey(WorstShop.get(), "shop_item");

    @NotNull
    @Property
    public ItemStack rawStack = new ItemStack(Material.AIR);

    @Property
    public boolean async = false;
    @Nullable
    @Property
    public ItemStack asyncLoadingItem = null;
    public transient boolean hasRemindedAsync = false;

    // for faster client load times
    @Nullable
    private transient PaperHelper.GameProfile skullCache;

    public static StaticShopElement fromStack(ItemStack stack) {
        StaticShopElement inst = new StaticShopElement();
        if (stack != null)
            inst.rawStack = stack;
        inst.actions = Collections.emptyList();
        return inst;
    }

    private static final Pattern VALID_MC_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    public static ShopElement fromYaml(Config config) {
        // static parsing
        StaticShopElement inst = new StaticShopElement();

        inst.id = config.find("id", Object.class).map(Object::toString).orElseGet(() -> {
            // try to assign random id
            Shop shop = ParseContext.findLatest(Shop.class);
            if (shop != null) {
                return "index=" + shop.elements.size();
            }
            return "???";
        });

        inst.owner = ShopReference.of(ParseContext.findLatest(Shop.class));

        // push context earlier for error-handling
        ParseContext.pushContext(inst);

        ItemStack rawStack = parseItemStack(config);

        // die if null
        if (ItemUtils.isEmpty(rawStack) && !config.find("preserve-space", Boolean.class).orElse(false))
            return null;

        if (rawStack != null)
            inst.rawStack = rawStack;

        inst.async = config.find("async", Boolean.class).orElse(false);
        if (inst.async)
            inst.asyncLoadingItem = config.find("async-loading-item", Config.class)
                    .map(StaticShopElement::parseItemStack).orElse(null);

        // obtain the skull meta
        if (inst.rawStack.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) inst.rawStack.getItemMeta();
            PaperHelper.GameProfile profile = PaperHelper.getSkullMetaProfile(meta);
            if (profile != null && profile.getName() != null &&
                    VALID_MC_NAME.matcher(profile.getName()).matches() && !profile.hasSkin()) {
                profile.completeProfile().thenAccept(ignored -> inst.skullCache = profile);
            }
        }

        // don't pop context just yet
        return inst;
    }

    public static ItemMeta deserializeBase64ItemMeta(String base64) {
        String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        // Bukkit API what the fuck
        // Do not use Bukkit YamlConfiguration as it converts embedded Maps into MemorySections,
        // which SerializableItemMeta (also from Bukkit API) does NOT like
        Map<String, Object> map = (new Yaml()).load(decoded);
        //noinspection ConstantConditions
        return (ItemMeta) ConfigurationSerialization.deserializeObject(map, ConfigurationSerialization.getClassByAlias("ItemMeta"));
    }

    public static String serializeBase64ItemMeta(ItemMeta meta) {
        Map<String, Object> map = meta.serialize();
        YamlConfiguration temp = new YamlConfiguration();
        temp.addDefaults(map);
        temp.options().copyDefaults(true);
        return new String(Base64.getEncoder().encode(temp.saveToString().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    // values are from CraftMetaItem.SerializableMeta.classMap
    public static final Set<String> SUPPORTED_ITEM_META_TYPES = ImmutableSet.<String>builder().add("UNSPECIFIC", "SKULL", "ENCHANTED").build();

    public static boolean isItemMetaSupported(ItemMeta meta) {
        //noinspection SuspiciousMethodCalls
        return SUPPORTED_ITEM_META_TYPES.contains(meta.serialize().get("meta-type"));
    }

    public static ItemStack parseItemStack(Config yaml) {
        try {
            Material material = Material.matchMaterial(yaml.get("item", String.class).replace(' ', '_'));
            if (material == null) {
                throw new IllegalStateException("Illegal material " + yaml.get("item", String.class));
            } else if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                return null; // skip air
            }
            ItemBuilder is = ItemBuilder.of(material);
            int amount = yaml.find("amount", Integer.class).orElseGet(()->yaml.find("count", Integer.class).orElse(1));
            is.amount(Math.min(Math.max(amount, 1), material.getMaxStackSize()));

            Optional<String> itemMetaString = yaml.find("item-meta", String.class);
            if (itemMetaString.isPresent()) {
                ItemMeta decoded = deserializeBase64ItemMeta(itemMetaString.get());
                is.meta(decoded);
                // allow basic properties to further influence this ItemStack
            }
            yaml.find("damage", Integer.class).ifPresent(damage -> {
                if (damage != 0)
                    is.meta(meta -> {
                        if (meta instanceof Damageable)
                            ((Damageable) meta).setDamage(damage);
                    });
            });

            yaml.find("custom-model-data", Integer.class).ifPresent(customModelData ->
                    is.meta(meta -> meta.setCustomModelData(customModelData))
            );

            yaml.find("enchants", Config.class).ifPresent(enchants ->
                    is.meta(meta -> {
                        // support enchanted books
                        BiConsumer<Enchantment, Integer> consumer = meta instanceof EnchantmentStorageMeta ?
                                (e, i) -> ((EnchantmentStorageMeta) meta).addStoredEnchant(e, i, true) :
                                (e, i) -> meta.addEnchant(e, i, true);

                        enchants.getPrimitiveMap().forEach((ench, level) -> {
                            Enchantment enchType = Enchantment.getByKey(NamespacedKey.minecraft(ench));
                            if (enchType == null)
                                throw new ConfigException(ench + " is not a valid enchant!", enchants);
                            if (!(level instanceof Number))
                                throw new ConfigException("Expected level to be a number", enchants, ench);
                            consumer.accept(enchType, ((Number) level).intValue());
                        });
                    })
            );

            yaml.find("name", String.class).ifPresent(is::name);

            yaml.find("loc-name", String.class).ifPresent(locName -> is.meta(meta -> meta.setLocalizedName(locName)));

            yaml.find("lore", List.class, String.class).ifPresent(loreObj -> {
                if (loreObj instanceof List<?>) {
                    List<String> lore = new ArrayList<>(yaml.getList("lore", String.class));
                    is.lore(lore);
                } else {
                    is.lores(loreObj.toString().split("\n"));
                }
            });

            yaml.find("unbreakable", Boolean.class).ifPresent(bool -> is.meta(meta -> meta.setUnbreakable(bool)));

            yaml.findList("hide-flags", String.class).ifPresent(flags -> {
                ItemFlag[] itemFlags = flags.stream()
                        .map(flag -> !flag.startsWith("HIDE") ? "HIDE_" + flag : flag)
                        .map(flag -> ConfigHelper.parseEnum(flag, ItemFlag.class))
                        .toArray(ItemFlag[]::new);
                is.meta(meta -> meta.addItemFlags(itemFlags));
            });

            // skull
            yaml.find("skull", String.class).ifPresent(uuidOrName -> {
                UUID uuid = null;
                try {
                    uuid = UUID.fromString(uuidOrName);
                    uuidOrName = null; // make name null
                } catch (IllegalArgumentException ignored) {
                }
                final UUID finalUuid = uuid;
                final String finalName = uuidOrName;
                is.meta(meta -> {
                    if (meta instanceof SkullMeta skullMeta) {
                        PaperHelper.setSkullMetaProfile(skullMeta, PaperHelper.createProfile(finalUuid, finalName));
                    } else {
                        throw new IllegalArgumentException("skull can only be used on player heads!");
                    }
                });
            });
            yaml.find("skin", String.class).ifPresent(skin -> {
                PaperHelper.GameProfile profile = PaperHelper.createProfile(UUID.randomUUID(), null);
                profile.setSkin(skin);
                is.meta(meta -> {
                    if (meta instanceof SkullMeta) {
                        PaperHelper.setSkullMetaProfile((SkullMeta) meta, profile);
                    } else {
                        throw new IllegalArgumentException("skin can only be used on player heads!");
                    }
                });
            });
            return is.build();
        } catch (Exception ex) {
            throw new IllegalArgumentException(ex);
        }
    }

    /**
     * Serializes an ItemStack.
     * <p>
     * This method always attempts to use the simplest representation.
     */
    @SuppressWarnings("ConstantConditions")
    public static Map<String, Object> serializeItemStack(ItemStack stack, Map<String, Object> map) {
        map.put("item", stack.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        if (stack.getAmount() != 1)
            map.put("amount", stack.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof Damageable damageable) {
            if (damageable.hasDamage()) {
                map.put("damage", damageable.getDamage());
            }
        }
        if (meta.hasCustomModelData()) {
            map.put("custom-model-data", meta.getCustomModelData());
        }
        if (meta.hasEnchants() || (meta instanceof EnchantmentStorageMeta && ((EnchantmentStorageMeta) meta).hasStoredEnchants())) {
            HashMap<String, Object> enchants = new HashMap<>();
            Map<Enchantment, Integer> realEnchants = meta instanceof EnchantmentStorageMeta ? ((EnchantmentStorageMeta) meta).getStoredEnchants() : meta.getEnchants();
            realEnchants.forEach((ench, level) -> {
                // strip namespace if minecraft:
                String key = ench.getKey().getNamespace().equals(NamespacedKey.MINECRAFT) ? ench.getKey().getKey() : ench.getKey().toString();
                enchants.put(key, level);
            });
            map.put("enchants", enchants);
        }
        if (meta.hasDisplayName()) {
            map.put("name", ConfigHelper.untranslateString(meta.getDisplayName()));
        }
        if (meta.hasLocalizedName()) {
            map.put("loc-name", meta.getLocalizedName());
        }
        if (meta.hasLore()) {
            map.put("lore", meta.getLore().stream().map(ConfigHelper::untranslateString).collect(Collectors.toList()));
        }
        if (meta.getItemFlags().size() != 0) {
            map.put("hide-flags", meta.getItemFlags().stream().map(ItemFlag::name)
                    .map(str -> str.substring("HIDE_".length())) // strip hide
                    .map(str -> str.toLowerCase().replace('_', ' ')) // to lowercase
                    .collect(Collectors.toList()));
        }
        if (meta instanceof SkullMeta skullMeta) {
            PaperHelper.GameProfile profile = PaperHelper.getSkullMetaProfile(skullMeta);
            if (profile != null) {
                if (profile.hasSkin()) {
                    map.put("skin", profile.getSkin());
                }
                String nameOrUUID = profile.getName();
                if (nameOrUUID == null)
                    nameOrUUID = profile.getUUID().toString();
                map.put("skull", nameOrUUID);
            }
        }

        // magic string
        // will be included when meta is complex
        // (contains custom data / is of an unsupported ItemMeta class / has attribute modifiers (WIP))
        if (!isItemMetaSupported(meta) || !meta.getPersistentDataContainer().isEmpty() || meta.getAttributeModifiers() != null)
            map.put("item-meta", serializeBase64ItemMeta(meta));
        return map;
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        super.toMap(map);
        if (ItemUtils.isEmpty(rawStack))
            map.put("preserve-space", true);
        else
            serializeItemStack(rawStack, map);
        if (async)
            map.put("async", true);
        if (asyncLoadingItem != null)
            map.put("async-loading-item", serializeItemStack(asyncLoadingItem, new HashMap<>()));
        return map;
    }

    /**
     * Applies placeholders to the {@link StaticShopElement#rawStack}
     * @param player The target player
     * @return The resultant item stack
     */
    public ItemStack createPlaceholderStack(Player player) {
        if (!condition.test(player)) {
            return null;
        }

        // parse placeholders
        long start = System.currentTimeMillis();
        ItemStack stack = Placeholders.setPlaceholders(this.rawStack, player);
        long end = System.currentTimeMillis();
//        if (!async && !hasRemindedAsync && end - start > 500) {
//            WorstShop.get().logger.warning("Placeholders took " + (end - start) + "ms. Consider making this element async. (" + id + "@" + owner.id + ")\n" +
//                    "Note that some placeholders may stop working when used async.");
//            hasRemindedAsync = true;
//        }
        // try to apply cache
        if (skullCache != null && stack.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) stack.getItemMeta();
            PaperHelper.setSkullMetaProfile(meta, skullCache);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static final ItemStack ASYNC_PLACEHOLDER = ItemBuilder.of(Material.BEDROCK)
            .name("" + ChatColor.RED + ChatColor.BOLD + "...").build();
    // use a map to prevent conflicts
    private static final Map<Player, ItemStack> asyncItemCache = Collections.synchronizedMap(new WeakHashMap<>());

    private List<RenderElement> getAsyncPlaceholderElement(ShopRenderer renderer, PlaceholderContext placeholder) {
        ItemStack toReturn = asyncLoadingItem != null ?
                Placeholders.setPlaceholders(asyncLoadingItem, placeholder) :
                ASYNC_PLACEHOLDER.clone();
        ItemMeta meta = toReturn.getItemMeta();
        meta.getPersistentDataContainer().set(SAFETY_KEY, PersistentDataType.BYTE, (byte) 1);
        toReturn.setItemMeta(meta);
        return Collections.singletonList(new RenderElement(this, getFiller(renderer).fill(this, renderer),
                toReturn, getClickHandler(renderer), DYNAMIC_FLAGS));
    }

    @Override
    public List<RenderElement> getRenderElement(ShopRenderer renderer, PlaceholderContext placeholder) {
        if (async) {
            Player player = renderer.player;
            synchronized (asyncItemCache) {
                if (asyncItemCache.containsKey(player)) {
                    ItemStack stack = asyncItemCache.remove(player);
                    if (stack != null) {
                        return Collections.singletonList(
                                new RenderElement(this, getFiller(renderer).fill(this, renderer), stack,
                                        PlaceholderContext.NO_CONTEXT, getClickHandler(renderer), STATIC_FLAGS)
                        );
                    } else {
                        return getAsyncPlaceholderElement(renderer, placeholder);
                    }
                } else {
                    asyncItemCache.put(player, null);
                    // schedule task
                    Bukkit.getScheduler().runTaskAsynchronously(WorstShop.get(), () -> {
                        ItemStack stack = createStack(renderer);
                        asyncItemCache.put(player, stack);
                    });
                    return getAsyncPlaceholderElement(renderer, placeholder);
                }
            }
        }

        return super.getRenderElement(renderer, placeholder);
    }

    @Override
    public ItemStack createStack(ShopRenderer renderer) {
        Player player = renderer.player;

        final ItemStack readonlyStack = rawStack.clone();
        final ItemStack actualStack = readonlyStack.clone();

        // let actions influence item
        actions.forEach(action -> action.influenceItem(player, readonlyStack.clone(), actualStack));

        // put unique identifier
        ItemMeta meta = actualStack.getItemMeta();
        meta.getPersistentDataContainer().set(SAFETY_KEY, PersistentDataType.BYTE, (byte) 1);
        actualStack.setItemMeta(meta);

        return actualStack;
    }

    public static boolean isShopItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return false;
        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();
        return container.has(SAFETY_KEY, PersistentDataType.BYTE);
    }

    @Override
    public String toString() {
        return "static " + super.toString() + "(stack=" + rawStack.getType() + ")";
    }

    @Override
    public int hashCode() {
        return rawStack.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StaticShopElement other))
            return false;
        return other.rawStack.equals(rawStack) && Objects.equals(other.skullCache, skullCache) && other.async == async;
    }

    @Override
    public StaticShopElement clone() {
        StaticShopElement element = (StaticShopElement) super.clone();
        element.rawStack = rawStack.clone();
        if (asyncLoadingItem != null)
            element.asyncLoadingItem = asyncLoadingItem.clone();
        return element;
    }
}
