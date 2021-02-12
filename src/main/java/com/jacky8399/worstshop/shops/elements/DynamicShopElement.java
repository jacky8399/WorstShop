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
        DynamicShopElement inst;
        String preset = config.get("preset", String.class);
        //noinspection SwitchStatementWithTooFewBranches
        switch (preset) {
            case "animation": {
                inst = new AnimationShopElement(config);
                break;
            }
            default:
                throw new ConfigException(preset + " is not a valid preset!", config, "preset");
        }
        inst.owner = ShopReference.of(ParseContext.findLatest(Shop.class));
        ParseContext.pushContext(inst);

        // don't pop context just yet
        return inst;
    }

    @Override
    public ItemStack createStack(Player player) {
        return ItemBuilder.of(Material.BEDROCK).name(ChatColor.DARK_RED + "DYNAMIC").build();
    }
}
