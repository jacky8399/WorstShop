package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.I18n;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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
}
