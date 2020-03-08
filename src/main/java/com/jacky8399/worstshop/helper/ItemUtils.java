package com.jacky8399.worstshop.helper;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;

public class ItemUtils {
    public static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getAmount() == 0 || isAir(stack.getType());
    }

    public static final EnumSet<Material> AIR = EnumSet.of(Material.AIR, Material.CAVE_AIR, Material.VOID_AIR);
    public static boolean isAir(Material material) {
        return AIR.contains(material);
    }
}
