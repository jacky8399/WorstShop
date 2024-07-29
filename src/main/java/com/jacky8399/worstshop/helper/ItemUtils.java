package com.jacky8399.worstshop.helper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import fr.minuskube.inv.ClickableItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;
import java.util.regex.Pattern;

public class ItemUtils {
    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getAmount() == 0 || isAir(stack.getType());
    }

    public static final EnumSet<Material> AIR = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);
    public static boolean isAir(Material material) {
        return AIR.contains(material);
    }

    public static ItemStack getErrorItem(@Nullable Exception ex) {
        String hash = ex != null ? "error #" + Exceptions.logException(ex) : "unknown error";
        return ItemBuilder.of(Material.BEDROCK)
                .name(I18n.translate("worstshop.errors.error-element.name"))
                .lores(I18n.translate("worstshop.errors.error-element.lore", hash))
                .build();
    }

    public static ClickableItem getClickableErrorItem(@Nullable Exception ex) {
        String err = Exceptions.logException(ex);
        String lore = ex != null ? "error #" + err : "unknown error";
        return ItemBuilder.of(Material.BEDROCK)
                .name(I18n.translate("worstshop.errors.error-element.name"))
                .lores(I18n.translate("worstshop.errors.error-element.lore", lore))
                .toClickable(e -> {
                    Player player = (Player) e.getWhoClicked();
                    if (player.hasPermission("worstshop.log.error")) {
                        Bukkit.getScheduler().runTask(WorstShop.get(), () -> {
                            InventoryUtils.closeWithoutParent(player);
                            player.chat("/worstshop log error show " + err);
                        });
                    }
                });
    }

    public static ItemMeta removeSafetyKey(@NotNull ItemMeta meta) {
        meta.getPersistentDataContainer().remove(StaticShopElement.SAFETY_KEY);
        return meta;
    }

    @NotNull
    public static ItemStack removeSafetyKey(@NotNull ItemStack stack) {
        ItemStack clone = stack.clone();
        clone.setItemMeta(removeSafetyKey(clone.getItemMeta()));
        return clone;
    }


    private static final Pattern VALID_MC_NAME = Pattern.compile("^[A-Za-z0-9_]{1,16}$");

    public static void handleSkullOwner(SkullMeta skullMeta, String uuidOrName) {
        UUID uuid = null;
        try {
            uuid = UUID.fromString(uuidOrName);
            uuidOrName = null;
        } catch (IllegalArgumentException ignored) {}

        if (uuidOrName != null) {
            if (VALID_MC_NAME.matcher(uuidOrName).matches()) {
                try {
                    // Bukkit.createProfile will fetch an online player's profile if possible
                    PlayerProfile profile = Bukkit.createProfile(uuid, uuidOrName);
                    skullMeta.setPlayerProfile(profile);
                    return;
                } catch (IllegalArgumentException ignored) {}
            }
            // probably contains placeholders, defer
            skullMeta.getPersistentDataContainer().set(Placeholders.ITEM_SKULL_OWNER_KEY, PersistentDataType.STRING, uuidOrName);
        }
    }

}
