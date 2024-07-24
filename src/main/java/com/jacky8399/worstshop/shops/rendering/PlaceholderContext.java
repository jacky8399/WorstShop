package com.jacky8399.worstshop.shops.rendering;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.InventoryUtils;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.SmartInventory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.StringJoiner;
import java.util.function.UnaryOperator;

public record PlaceholderContext(@Nullable Player player,
                                 @Nullable Shop shop,
                                 @Nullable ShopRenderer renderer,
                                 @Nullable ShopElement element,
                                 @Nullable PlaceholderContext additionalContext)
    implements UnaryOperator<String> {
    public PlaceholderContext(@NotNull ShopRenderer renderer) {
        this(renderer, null);
    }

    public PlaceholderContext(@NotNull ShopRenderer renderer, @Nullable ShopElement element) {
        this(renderer.player, renderer.shop, renderer, element, null);
    }

    public static final PlaceholderContext NO_CONTEXT = new PlaceholderContext(null, null, null, null, null);

    public Object getVariable(String key) {
        if (renderer != null && renderer.debug)
            WorstShop.get().logger.info("Looking for " + key + " in " + this);
        if (element != null) {
            Object result = element.getVariable(key, this);
            if (result != null)
                return result;
        }
        if (shop != null) {
            Object result = shop.getVariable(key);
            if (result != null)
                return result;
        }
        if (additionalContext != null) {
            return additionalContext.getVariable(key);
        }
        return null;
    }

    public PlaceholderContext withElement(@NotNull ShopElement element) {
        return new PlaceholderContext(null, null, null, element, this);
    }

    public PlaceholderContext andThen(@NotNull PlaceholderContext context) {
        return new PlaceholderContext(player, shop, renderer, element,
                additionalContext != null ? additionalContext.andThen(context) : context
        );
    }

    @Nullable
    public Player getPlayer() {
        if (player != null)
            return player;
        return additionalContext != null ? additionalContext.getPlayer() : null;
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
        return new PlaceholderContext(player, renderer != null ? renderer.shop : null, renderer, null, null);
    }

    @Override
    public String toString() {
        StringJoiner builder = new StringJoiner(",", "PlaceholderContext{", "}");
        if (player != null)
            builder.add("player=" + player.getName());
        if (shop != null)
            builder.add("shop=" + shop.id);
        if (renderer != null)
            builder.add("renderer=" + renderer);
        if (element != null)
            builder.add("element=" + element);
        if (additionalContext != null)
            builder.add("additional=" + additionalContext);
        return builder.toString();
    }

    @Override
    public String apply(String input) {
        return Placeholders.setPlaceholders(input, this);
    }
}
