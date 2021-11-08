package com.jacky8399.worstshop.shops.rendering;

import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.EnumSet;
import java.util.function.Consumer;

public record RenderElement(ShopElement owner,
                            Collection<SlotPos> positions,
                            ItemStack stack,
                            boolean shouldReplacePlaceholders,
                            Consumer<InventoryClickEvent> handler,
                            EnumSet<ShopRenderer.RenderingFlag> flags) {

    public RenderElement(ShopElement owner, Collection<SlotPos> positions, ItemStack stack, Consumer<InventoryClickEvent> handler, EnumSet<ShopRenderer.RenderingFlag> flags) {
        this(owner, positions, stack, true, handler, flags);
    }

    public ItemStack actualStack(ShopRenderer renderer) {
        return shouldReplacePlaceholders ?
                StaticShopElement.replacePlaceholders(renderer.player, stack, renderer.shop, renderer) :
                stack;
    }

    public ClickableItem clickableItem(ShopRenderer renderer) {
        return ClickableItem.of(actualStack(renderer), handler);
    }

}

