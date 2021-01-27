package com.jacky8399.worstshop.shops.elements;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.*;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StaticShopElement extends ShopElement {

    public static NamespacedKey SAFETY_KEY = new NamespacedKey(WorstShop.get(), "shop_item");

    public ItemStack rawStack;

    // for faster client load times
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
        Map<String, Object> yaml = config.getPrimitiveMap();
        // static parsing
        StaticShopElement inst = new StaticShopElement();

        if (yaml.containsKey("id")) {
            inst.id = (String) yaml.get("id");
        } else {
            // try to assign random id
            Shop shop = ParseContext.findLatest(Shop.class);
            if (shop != null) {
                inst.id = "index=" + (shop.staticElements.size() + shop.dynamicElements.size());
            }
        }

        inst.owner = ParseContext.findLatest(Shop.class);

        // push context earlier for error-handling
        ParseContext.pushContext(inst);

        inst.rawStack = parseItemStack(yaml);

        // die if null
        if (ItemUtils.isEmpty(inst.rawStack) && !((Boolean) yaml.getOrDefault("preserve-space", false)))
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
    public static final Set<String> SUPPORTED_ITEM_META_TYPES = ImmutableSet.<String>builder().add("UNSPECIFIC", "SKULL").build();
    public static boolean isItemMetaSupported(ItemMeta meta) {
        //noinspection SuspiciousMethodCalls
        return SUPPORTED_ITEM_META_TYPES.contains(meta.serialize().get("meta-type"));
    }

    @SuppressWarnings({"unchecked"})
    public static ItemStack parseItemStack(Map<String, Object> yaml) {
        try {
            Material material = Material.getMaterial(
                    ((String) yaml.get("item")).toUpperCase().replace(' ', '_')
            );
            if (material == null) {
                throw new IllegalStateException("Illegal material " + yaml.get("item"));
            }
            if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
                return null; // skip air
            }
            ItemBuilder is = ItemBuilder.of(material);
            is.amount(Math.max((int) yaml.getOrDefault("count", 1), 1));

            if (yaml.containsKey("item-meta")) {
                String itemMetaStr = (String) yaml.get("item-meta");
                ItemMeta decoded = deserializeBase64ItemMeta(itemMetaStr);
                ItemStack stack = is.build();
                stack.setItemMeta(decoded);
                // allow basic properties to further influence this ItemStack
                is = ItemBuilder.from(stack);
            }

            int damage = (int) yaml.getOrDefault("damage", 0);
            if (damage != 0) {
                is.meta(meta -> {
                    if (meta instanceof Damageable)
                        ((Damageable) meta).setDamage(damage);
                });
            }

            if (yaml.containsKey("custom-model-data")) {
                is.meta(meta -> meta.setCustomModelData(((Number) yaml.get("custom-model-data")).intValue()));
            }

            if (yaml.containsKey("enchants")) {
                Map<String, Object> enchants = (Map<String, Object>) yaml.get("enchants");
                Map<Enchantment, Integer> enchant = enchants.entrySet().stream().map(entry -> {
                    String ench = entry.getKey();
                    int level = ((Number) entry.getValue()).intValue();
                    Enchantment enchType = Enchantment.getByKey(NamespacedKey.minecraft(ench));
                    if (enchType != null) {
                        return Maps.immutableEntry(enchType, level);
                    }
                    return null;
                }).filter(Objects::nonNull).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                is.meta(meta -> enchant.forEach((ench, level) -> meta.addEnchant(ench, level, true)));
            }

            String displayName = ConfigHelper.translateString((String) yaml.get("name"));
            if (displayName != null) {
                is.name(displayName);
            }
            String locName = (String) yaml.get("loc-name");
            if (locName != null) {
                is.meta(meta -> meta.setLocalizedName(locName));
            }
            if (yaml.containsKey("lore")) {
                Object loreObj = yaml.get("lore");
                if (loreObj instanceof List<?>) {
                    List<String> lore = ((List<String>) loreObj).stream()
                            .map(ConfigHelper::translateString)
                            .collect(Collectors.toList());
                    is.lore(lore);
                } else {
                    // probably a string
                    is.lores(ConfigHelper.translateString(loreObj.toString()).split("\n"));
                }
            }
            if (yaml.containsKey("hide-flags")) {
                ItemFlag[] flags = ((List<String>) yaml.get("hide-flags")).stream()
                        .map(str -> !str.startsWith("hide") ? "HIDE_" + str : str)
                        .map(str -> str.toUpperCase().replace(' ', '_'))
                        .map(ItemFlag::valueOf)
                        .toArray(ItemFlag[]::new);
                is.meta(meta -> meta.addItemFlags(flags));
            }
            // skull
            if (yaml.containsKey("skull")) {
                String uuidOrName = yaml.get("skull").toString();
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
                        throw new IllegalArgumentException("skin can only be used on player heads!");
                    }
                });
            } else if (yaml.containsKey("skin")) {
                String skin = (String) yaml.get("skin");
                PaperHelper.GameProfile profile = PaperHelper.createProfile(UUID.randomUUID(), null);
                profile.setSkin(skin);
                is.meta(meta -> {
                    if (meta instanceof SkullMeta) {
                        PaperHelper.setSkullMetaProfile((SkullMeta) meta, profile);
                    } else {
                        throw new IllegalArgumentException("skin can only be used on player heads!");
                    }
                });
            }
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
        if (meta.hasEnchants()) {
            HashMap<String, Object> enchants = new HashMap<>();
            meta.getEnchants().forEach((ench, level)->{
                // strip namespace if minecraft:
                String key = ench.getKey().getNamespace().equals(NamespacedKey.MINECRAFT) ? ench.getKey().getKey() : ench.getKey().getNamespace();
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
        return map;
    }

    public ItemStack createPlaceholderStack(Player player) {
        if (!condition.test(player)) {
            return null;
        }

        // parse placeholders
        ItemStack stack = replacePlaceholders(player, this.rawStack);
        // try to apply cache
        if (skullCache != null && stack.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) stack.getItemMeta();
            PaperHelper.setSkullMetaProfile(meta, skullCache);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public ItemStack createStack(Player player) {

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
        PersistentDataContainer container = stack.getItemMeta().getPersistentDataContainer();
        return container.has(SAFETY_KEY, PersistentDataType.BYTE);
    }
}
