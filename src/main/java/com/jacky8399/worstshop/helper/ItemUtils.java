package com.jacky8399.worstshop.helper;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.ClickableItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Objects;
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
    private static final Pattern INVALID_MC_NAME_CHARS = Pattern.compile("[^A-Za-z0-9_]");
    public static final String SKULL_PROPERTY = "worstshop_skull";
    public static final boolean SKULL_DEBUG = false;
    public static PlayerProfile makeProfileExact(@Nullable UUID uuid, @Nullable String name) {
        String sanitizedName = name;
        if (name != null && !VALID_MC_NAME.matcher(name).matches()) {
            sanitizedName = INVALID_MC_NAME_CHARS.matcher(name).replaceAll("");
            if (sanitizedName.length() > 16)
                sanitizedName = sanitizedName.substring(0, 16);
            else if (sanitizedName.isEmpty())
                sanitizedName = "MHF_Question"; // ok
            uuid = UUID.randomUUID();
        }
        PlayerProfile profile = Bukkit.createProfileExact(uuid, sanitizedName);
        if (!Objects.equals(sanitizedName, name)) {
            profile.setProperty(new ProfileProperty(SKULL_PROPERTY, name));
            if (SKULL_DEBUG) {
                WorstShop.get().logger.info("Invalid name " + name + ", sanitized to " + sanitizedName + "\nProfile: " + profile);
            }
        }
        return profile;
    }

    public static PlayerProfile makeProfile(@Nullable UUID uuid, @Nullable String name) {
        String sanitizedName = name;
        if (name != null && !VALID_MC_NAME.matcher(name).matches()) {
            sanitizedName = INVALID_MC_NAME_CHARS.matcher(name).replaceAll("");
            if (sanitizedName.length() > 16)
                sanitizedName = sanitizedName.substring(0, 16);
            else if (sanitizedName.isEmpty())
                sanitizedName = "MHF_Question"; // ok
            uuid = UUID.randomUUID(); // must not look up texture if sanitized to preserve original skull value
        }
        PlayerProfile profile = Bukkit.createProfile(uuid, sanitizedName);
        if (!Objects.equals(sanitizedName, name)) {
            profile.setProperty(new ProfileProperty(SKULL_PROPERTY, name));
            if (SKULL_DEBUG) {
                WorstShop.get().logger.info("Invalid name " + name + ", sanitized to " + sanitizedName + "\nProfile: " + profile);
            }
        }
        return profile;
    }
}
