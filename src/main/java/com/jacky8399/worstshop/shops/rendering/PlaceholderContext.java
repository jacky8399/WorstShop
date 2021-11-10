package com.jacky8399.worstshop.shops.rendering;

import com.jacky8399.worstshop.helper.InventoryUtils;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record PlaceholderContext(@Nullable Player player,
                                 @Nullable Shop shop,
                                 @Nullable ShopRenderer renderer,
                                 @Nullable ShopElement element) {
    public PlaceholderContext(@NotNull ShopRenderer renderer) {
        this(renderer, null);
    }

    public PlaceholderContext(@NotNull ShopRenderer renderer, @Nullable ShopElement element) {
        this(renderer.player, renderer.shop, renderer, element);
    }


    public Object getVariable(String key) {
        if (element != null) {
            Object result = element.variables.get(key);
            if (result != null)
                return result;
        } else if (shop != null) {
            Object result = shop.getVariable(key);
            if (result != null)
                return result;
        }
        return null;
    }

    public static PlaceholderContext guessContext(@NotNull Player player) {
        ShopRenderer renderer = null;
        if (ShopRenderer.RENDERING != null && ShopRenderer.RENDERING.player == player) {
            renderer = ShopRenderer.RENDERING;
        }
        if (renderer == null) {
            SmartInventory contents = InventoryUtils.getInventory(player);
            if (contents != null && contents.getProvider() instanceof ShopRenderer invRenderer) {
                renderer = invRenderer;
            }
        }
        return new PlaceholderContext(player, renderer != null ? renderer.shop : null, renderer, null);
    }
}
