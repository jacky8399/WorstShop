package com.jacky8399.worstshop.shops.elements;

import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.Shop;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class DynamicShopElement extends ShopElement {
    public static DynamicShopElement fromYaml(Map<String, Object> yaml, Shop parent) {
        DynamicShopElement inst = new DynamicShopElement();
        inst.owner = parent;
        // TODO parse dynamic items
        return inst;
    }

    @Override
    public ItemStack createStack(Player player) {
        return ItemBuilder.of(Material.BARRIER).name(ChatColor.DARK_RED + "DYNAMIC").build();
    }
}
