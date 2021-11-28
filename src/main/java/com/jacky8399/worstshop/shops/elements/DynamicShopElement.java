package com.jacky8399.worstshop.shops.elements;

import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.shops.ParseContext;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.elements.dynamic.AnimationShopElement;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class DynamicShopElement extends ShopElement {
    public static DynamicShopElement fromYaml(Config config) {
        String preset = config.get("preset", String.class);
        @SuppressWarnings("SwitchStatementWithTooFewBranches")
        DynamicShopElement inst = switch (preset) {
            case "animation" -> new AnimationShopElement(config);
            default -> throw new ConfigException(preset + " is not a valid preset!", config, "preset");
        };
        inst.owner = ShopReference.of(ParseContext.findLatest(Shop.class));
        ParseContext.pushContext(inst);

        // don't pop context just yet
        return inst;
    }

    @Override
    public ItemStack createStack(Player player) {
        return ItemBuilder.of(Material.BEDROCK).name(ChatColor.DARK_RED + "DYNAMIC").build();
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
