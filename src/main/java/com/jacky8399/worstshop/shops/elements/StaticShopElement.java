package com.jacky8399.worstshop.shops.elements;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.ElementPopulationContext;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import fr.minuskube.inv.content.InventoryContents;
import me.clip.placeholderapi.PlaceholderAPI;
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
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StaticShopElement extends ShopElement {

    public static NamespacedKey SAFETY_KEY = new NamespacedKey(WorstShop.get(), "shop_item");

    public ItemStack rawStack;

    public boolean async = false;
    @Nullable
    public ItemStack asyncLoadingItem = null;
    public transient boolean hasRemindedAsync = false;

    // for faster client load times
    @Nullable
    private PaperHelper.GameProfile skullCache;

    public static final PaperHelper.GameProfile VIEWER_SKULL = PaperHelper.createProfile(null, "{player}");

    public static StaticShopElement fromStack(ItemStack stack) {
        StaticShopElement inst = new StaticShopElement();
        inst.rawStack = stack;
        inst.actions = Collections.emptyList();
        return inst;
    }

    private static final Pattern VALID_MC_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    public static ShopElement fromYaml(Config config) {
        // static parsing
        StaticShopElement inst = new StaticShopElement();

        inst.id = config.find("id", Object.class).map(Object::toString).orElseGet(()-> {
            // try to assign random id
            Shop shop = ParseContext.findLatest(Shop.class);
            if (shop != null) {
                return "index=" + (shop.staticElements.size() + shop.dynamicElements.size());
            }
            return "???";
        });

        inst.owner = ShopReference.of(ParseContext.findLatest(Shop.class));

        // push context earlier for error-handling
        ParseContext.pushContext(inst);

        inst.rawStack = parseItemStack(config);
        inst.async = config.find("async", Boolean.class).orElse(false);
        if (inst.async)
            inst.asyncLoadingItem = config.find("async-loading-item", Config.class)
                    .map(StaticShopElement::parseItemStack).orElse(null);

        // die if null
        if (ItemUtils.isEmpty(inst.rawStack) && !config.find("preserve-space", Boolean.class).orElse(false))
            return null;

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
            Material material = Material.getMaterial(yaml.get("item", String.class).toUpperCase(Locale.US).replace(' ', '_'));
            if (material == null) {
                throw new IllegalStateException("Illegal material " + yaml.get("item", String.class));
            }
            if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                return null; // skip air
            }
            ItemBuilder is = ItemBuilder.of(material);
            int amount = yaml.find("count", Integer.class).orElseGet(()->yaml.find("amount", Integer.class).orElse(1));
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
                        Consumer<Map.Entry<Enchantment, Integer>> consumer = meta instanceof EnchantmentStorageMeta ?
                                entry -> ((EnchantmentStorageMeta) meta).addStoredEnchant(entry.getKey(), entry.getValue(), true) :
                                entry -> meta.addEnchant(entry.getKey(), entry.getValue(), true);

                        enchants.getPrimitiveMap().entrySet().stream()
                                .map(entry -> {
                                    String ench = entry.getKey();
                                    int level = ((Number) entry.getValue()).intValue();
                                    Enchantment enchType = Enchantment.getByKey(NamespacedKey.minecraft(ench));
                                    if (enchType == null)
                                        throw new ConfigException(ench + " is not a valid enchant!", enchants);
                                    return Maps.immutableEntry(enchType, level);
                                })
                                .forEach(consumer);
                    })
            );

            yaml.find("name", String.class).map(ConfigHelper::translateString).ifPresent(is::name);

            yaml.find("loc-name", String.class).ifPresent(locName -> is.meta(meta -> meta.setLocalizedName(locName)));

            yaml.find("lore", List.class, String.class).ifPresent(loreObj -> {
                if (loreObj instanceof List<?>) {
                    List<String> lore = yaml.getList("lore", String.class).stream()
                            .map(ConfigHelper::translateString)
                            .collect(Collectors.toList());
                    is.lore(lore);
                } else {
                    is.lores(ConfigHelper.translateString(loreObj.toString()).split("\n"));
                }
            });

            yaml.findList("hide-flags", String.class).ifPresent(flags -> {
                ItemFlag[] itemFlags = flags.stream()
                        .map(flag -> !flag.startsWith("HIDE_") ? "HIDE_" + flag : flag)
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
                } catch (IllegalArgumentException ignored) { }
                final UUID finalUuid = uuid;
                final String finalName = uuidOrName;
                is.meta(meta -> {
                    if (meta instanceof SkullMeta) {
                        if ("{player}".equals(finalName)) {
                            PaperHelper.setSkullMetaProfile((SkullMeta) meta, VIEWER_SKULL);
                        } else {
                            PaperHelper.setSkullMetaProfile((SkullMeta) meta,
                                    PaperHelper.createProfile(finalUuid, finalName));
                        }
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
            map.put("count", stack.getAmount());
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof Damageable) {
            Damageable damageable = (Damageable) meta;
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
            realEnchants.forEach((ench, level)->{
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
        if (meta instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta) meta;
            PaperHelper.GameProfile profile = PaperHelper.getSkullMetaProfile(skullMeta);
            if (profile != null) {
                if (profile.skinNotLoaded) {
                    map.put("skin", profile.getSkin());
                } else {
                    map.put("skull", profile.getName());
                }
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

    @Override
    public void populateItems(Player player, InventoryContents contents, ElementPopulationContext pagination) {
        super.populateItems(player, contents, pagination);

        if (async) {
            Bukkit.getScheduler().runTaskAsynchronously(WorstShop.get(), ()->{
                try {
                    asyncHack.put(player, createStack(player));
                    if (!player.isOnline())
                        return;
                    Bukkit.getScheduler().runTask(WorstShop.get(), ()->{
                        try {
                            super.populateItems(player, contents, pagination);
                        } catch (Exception e) {
                            RuntimeException wrapped = new RuntimeException("An error occurred while replacing asynchronous item for " + player.getName() + " (" + id + "@" + owner.id + ")");
                            asyncHack.put(player, ItemUtils.getErrorItem(wrapped));
                        }
                    });
                } catch (Exception e) {
                    // error reporting
                    RuntimeException wrapped = new RuntimeException("An error occurred while fetching asynchronous item for " + player.getName() + " (" + id + "@" + owner.id + ")");
                    asyncHack.put(player, ItemUtils.getErrorItem(wrapped));
                }
            });
        }
    }

    public ItemStack createPlaceholderStack(Player player) {
        if (!condition.test(player)) {
            return null;
        }

        // parse placeholders
        long start = System.currentTimeMillis();
        ItemStack stack = replacePlaceholders(player, this.rawStack);
        long end = System.currentTimeMillis();
        if (!async && !hasRemindedAsync && end - start > 500) {
            WorstShop.get().logger.warning("Placeholders took " + (end - start) + "ms. Consider making this item async. (" + id + "@" + owner.id + ")");
            hasRemindedAsync = true;
        }
        // try to apply cache
        if (skullCache != null && stack.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) stack.getItemMeta();
            PaperHelper.setSkullMetaProfile(meta, skullCache);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public static final ItemStack ASYNC_PLACEHOLDER = ItemBuilder.of(Material.BEDROCK)
            .name(""+ChatColor.RED + ChatColor.BOLD + "...").build();
    // use a map to prevent conflicts
    private static final WeakHashMap<Player, ItemStack> asyncHack = new WeakHashMap<>();
    @Override
    public ItemStack createStack(Player player) {
        if (async) {
            ItemStack asyncHackResult = asyncHack.remove(player);
            if (asyncHackResult != null) {
                return asyncHackResult;
            } else if (Bukkit.isPrimaryThread()) {
                return (asyncLoadingItem != null ? asyncLoadingItem : ASYNC_PLACEHOLDER).clone();
            }
        }


        final ItemStack readonlyStack = createPlaceholderStack(player);

        if (readonlyStack == null)
            return null;
        final ItemStack actualStack = readonlyStack.clone();

        // let actions influence item
        actions.forEach(action -> action.influenceItem(player, readonlyStack.clone(), actualStack));

        // put unique identifier
        ItemMeta meta = actualStack.getItemMeta();
        meta.getPersistentDataContainer().set(SAFETY_KEY, PersistentDataType.BYTE, (byte) 1);
        actualStack.setItemMeta(meta);

        return actualStack;
    }

    public static ItemStack replacePlaceholders(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return stack;

        stack = stack.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta.hasLore()) {
            // noinspection ConstantConditions
            meta.setLore(meta.getLore().stream()
                    .map(lore -> I18n.doPlaceholders(player, lore))
                    .collect(Collectors.toList())
            );
        }
        if (meta.hasDisplayName()) {
            meta.setDisplayName(I18n.doPlaceholders(player, meta.getDisplayName()));
        }
        // wow why didn't I think of this
        // check for player skull
        if (meta instanceof SkullMeta) {
            SkullMeta skullMeta = (SkullMeta) meta;
            PaperHelper.GameProfile profile = PaperHelper.getSkullMetaProfile(skullMeta);
            if (profile != null) {
                if (profile.equals(VIEWER_SKULL)) {
                    skullMeta.setOwningPlayer(player);
                } else if (profile.getName() != null && profile.getName().contains("%") && WorstShop.get().placeholderAPI) {
                    // replace placeholders too
                    String newName = PlaceholderAPI.setPlaceholders(player, profile.getName());
                    // check if the name is that of a online player
                    Player newPlayer = Bukkit.getPlayer(newName);
                    if (newPlayer != null && newPlayer.isOnline()) {
                        skullMeta.setOwningPlayer(newPlayer);
                    } else if (newName.length() != 0) {
                        PaperHelper.GameProfile newProfile = PaperHelper.createProfile(null, newName);
                        PaperHelper.setSkullMetaProfile(skullMeta, newProfile);
                    }
                }
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    public static boolean isShopItem(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return false;
        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();
        return container.has(SAFETY_KEY, PersistentDataType.BYTE);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StaticShopElement))
            return false;
        StaticShopElement other = (StaticShopElement) obj;
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
