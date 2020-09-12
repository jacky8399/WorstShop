package com.jacky8399.worstshop.shops.elements;

import com.google.common.collect.Maps;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.helper.PaperHelper;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.actions.Action;
import com.jacky8399.worstshop.shops.actions.IParentElementReader;
import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.conditions.ConditionAnd;
import com.jacky8399.worstshop.shops.conditions.ConditionPermission;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.yaml.snakeyaml.Yaml;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class StaticShopElement extends ShopElement {

    public ItemStack rawStack;
    public Condition condition;
    public List<Action> actions;

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
    @SuppressWarnings("unchecked")
    public static ShopElement fromYaml(Map<String, Object> yaml) {
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

        ConditionAnd instCondition = new ConditionAnd();
        // Permissions
        if (yaml.containsKey("view-perm")) {
            instCondition.addCondition(ConditionPermission.fromPermString((String) yaml.get("view-perm")));
        }

        if (yaml.containsKey("condition")) {
            instCondition.addCondition(Condition.fromMap((Map<String, Object>) yaml.get("condition")));
        }

        // Action parsing
        inst.actions = ((List<?>) yaml.getOrDefault("actions", Collections.emptyList())).stream()
                .map(obj -> obj instanceof Map ?
                        Action.fromYaml((Map<String, Object>) obj) :
                        Action.fromCommand(obj.toString())).filter(Objects::nonNull).collect(Collectors.toList());

        inst.actions.stream().filter(action -> action instanceof IParentElementReader)
                .forEach(action -> ((IParentElementReader) action).readElement(inst));

        ParseContext.popContext();
        return inst;
    }

    public static ItemMeta deserializeBase64ItemMeta(String base64) {
        String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
        // Bukkit API what the fuck
        // Do not use Bukkit YamlConfiguration as it converts embedded Maps into MemorySections,
        // which SerializableItemMeta (also from Bukkit API) does NOT like
        Map<String, Object> map = (new Yaml()).load(decoded);
        return (ItemMeta) ConfigurationSerialization.deserializeObject(map, ConfigurationSerialization.getClassByAlias("ItemMeta"));
    }

    public static String serializeBase64ItemMeta(ItemMeta meta) {
        Map<String, Object> map = meta.serialize();
        YamlConfiguration temp = new YamlConfiguration();
        temp.addDefaults(map);
        temp.options().copyDefaults(true);
        return new String(Base64.getEncoder().encode(temp.saveToString().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
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
                return stack;
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
                    is.lores(ConfigHelper.translateString(loreObj.toString()));
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

    public ItemStack createPlaceholderStack(Player player) {
        if (condition != null && !condition.test(player)) {
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

        return actualStack;
    }

    public static ItemStack replacePlaceholders(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return stack;

        stack = stack.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta.hasLore()) {
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
                    PaperHelper.GameProfile newProfile = PaperHelper.createProfile(null, newName);
                    PaperHelper.setSkullMetaProfile(skullMeta, newProfile);
                }
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        for (Action action : actions) {
            if (action.shouldTrigger(e)) {
                action.onClick(e);
            }
        }
    }
}
