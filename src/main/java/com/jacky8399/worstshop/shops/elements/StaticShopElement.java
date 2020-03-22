package com.jacky8399.worstshop.shops.elements;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.helper.PaperHelper;
import com.jacky8399.worstshop.shops.ShopCondition;
import com.jacky8399.worstshop.shops.actions.IParentElementReader;
import com.jacky8399.worstshop.shops.actions.ShopAction;
import com.jacky8399.worstshop.shops.wants.ShopWantsPermissionSimple;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class StaticShopElement extends ShopElement {

    public ItemStack stack;
    public ShopCondition condition = new ShopCondition();
    public List<ShopAction> actions;

    public static StaticShopElement fromStack(ItemStack stack) {
        StaticShopElement inst = new StaticShopElement();

        inst.stack = stack;

        inst.actions = Collections.emptyList();

        return inst;
    }

    @SuppressWarnings("unchecked")
    public static ShopElement fromYaml(Map<String, Object> yaml) {
        // static parsing
        StaticShopElement inst = new StaticShopElement();

        inst.stack = parseItemStack(yaml);

        // die if null
        if (ItemUtils.isEmpty(inst.stack))
            return null;

        // Permissions
        if (yaml.containsKey("view-perm")) {
             inst.condition.add(new ShopWantsPermissionSimple(yaml.get("view-perm").toString()));
        }

        if (yaml.containsKey("condition")) {
            inst.condition.add(ShopCondition.parseFromYaml((Map<String, Object>) yaml.get("condition")));
        }

        // Action parsing
        ShopAction.Builder actionsBuilder = new ShopAction.Builder(yaml);
        inst.actions = ((List<?>)yaml.getOrDefault("actions", Collections.emptyList()))
                .stream().map(obj -> {
                    if (obj instanceof Map) {
                        return actionsBuilder.fromYaml((Map<String, Object>) obj);
                    } else {
                        return actionsBuilder.fromCommand(((Object) obj).toString());
                    }
                }).filter(Objects::nonNull).collect(Collectors.toList());

        inst.actions.stream().filter(action->action instanceof IParentElementReader)
                .forEach(action->((IParentElementReader) action).readElement(inst));

        return inst;
    }

    public static ItemMeta deserializeBase64ItemMeta(String base64) {
        String decoded = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);

        YamlConfiguration temp = new YamlConfiguration();
        return (ItemMeta) ConfigurationSerialization.deserializeObject(temp.getValues(false), ItemStack.class);
    }

    public static String serializeBase64ItemMeta(ItemMeta meta) {
        Map<String, Object> map = meta.serialize();
        YamlConfiguration temp = new YamlConfiguration();
        temp.addDefaults(map);
        temp.options().copyDefaults(true);
        return new String(Base64.getEncoder().encode(temp.saveToString().getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

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
                is.meta(meta-> {
                    if (meta instanceof Damageable)
                        ((Damageable) meta).setDamage(damage);
                });
            }

            if (yaml.containsKey("custom-model-data")) {
                is.meta(meta->meta.setCustomModelData(((Number) yaml.get("custom-model-data")).intValue()));
            }

            String displayName = ConfigHelper.translateString((String) yaml.get("name"));
            if (displayName != null) {
                is.name(displayName);
            }

            String locName = (String) yaml.get("loc-name");
            if (locName != null) {
                is.meta(meta->meta.setLocalizedName(locName));
            }

            if (yaml.containsKey("lore")) {
                List<String> lore = ((List<String>) yaml.getOrDefault("lore", Collections.emptyList()))
                        .stream().map(ConfigHelper::translateString).collect(Collectors.toList());
                is.lore(lore);
            }
            // skull
            if (yaml.containsKey("skull")) {
                if (yaml.get("skull") instanceof String) {
                    String uuidOrName = (String) yaml.get("skull");
                        UUID uuid = null;
                        try {
                            uuid = UUID.fromString(uuidOrName);
                            uuidOrName = null; // make name null
                        } catch (Exception ex) {
                            // not uuid
                        }
                        final UUID finalUuid = uuid;
                        final String finalName = uuidOrName;
                        is.meta(meta -> {
                            if (meta instanceof SkullMeta) {
                                PaperHelper.setSkullMetaProfile((SkullMeta) meta,
                                        PaperHelper.createProfile(finalUuid, finalName));
                            }
                        });
                }
            }
            return is.build();
        } catch (Exception ex) {
            return null;
        }
    }



    @Override
    public ItemStack createStack(Player player) {

        // perm checks
        if (!condition.test(player)) {
            return null;
        }

        if (stack == null)
            return null;


        // parse placeholders
        final ItemStack readonlyStack = getPlaceholderStack(player, stack);
        final ItemStack actualStack = readonlyStack.clone();

        // let actions influence item
        actions.forEach(action->action.influenceItem(player, readonlyStack.clone(), actualStack));


        return actualStack;
    }

    public static ItemStack getPlaceholderStack(Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR)
            return stack;

        stack = stack.clone();
        ItemMeta meta = stack.getItemMeta();
        if (meta.hasLore()) {
            meta.setLore(
                    meta.getLore().stream().map(lore-> I18n.doPlaceholders(player, lore))
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
            if (profile != null && profile.getName() != null && profile.getName().equals("{player}")) {
                skullMeta.setOwningPlayer(player);
            }
        }
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        super.onClick(e);
        for (ShopAction action : actions) {
            if (action.shouldTrigger(e)) {
                action.onClick(e);
            }
        }
    }
}
