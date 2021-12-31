package com.jacky8399.worstshop.helper;

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

    @NotNull
    public static ItemStack removeSafetyKey(@NotNull ItemStack stack) {
        ItemStack clone = stack.clone();
        ItemMeta meta = clone.getItemMeta();
        meta.getPersistentDataContainer().remove(StaticShopElement.SAFETY_KEY);
        clone.setItemMeta(meta);
        return clone;
    }
}
