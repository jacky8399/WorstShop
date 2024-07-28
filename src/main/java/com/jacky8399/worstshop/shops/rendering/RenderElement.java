package com.jacky8399.worstshop.shops.rendering;

import com.jacky8399.worstshop.shops.conditions.Condition;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

public record RenderElement(ShopElement owner,
                            Condition condition,
                            @Nullable
                            Collection<SlotPos> positions,
                            ItemStack stack,
                            PlaceholderContext context,
                            Consumer<InventoryClickEvent> handler,
                            Set<ShopRenderer.RenderingFlag> flags) {

    public RenderElement(ShopElement owner, Collection<SlotPos> positions, ItemStack stack, Consumer<InventoryClickEvent> handler, Set<ShopRenderer.RenderingFlag> flags) {
        this(owner, owner.condition, positions, stack,
                new PlaceholderContext(null, null, null, owner, null), handler, flags);
    }

    public RenderElement(ShopElement owner, Condition condition, Collection<SlotPos> positions, ItemStack stack, Consumer<InventoryClickEvent> handler, Set<ShopRenderer.RenderingFlag> flags) {
        this(owner, condition, positions, stack,
                new PlaceholderContext(null, null, null, owner, null), handler, flags);
    }

    public ItemStack actualStack(ShopRenderer renderer) {
        return context != PlaceholderContext.NO_CONTEXT ?
                Placeholders.setPlaceholders(stack, context.andThen(new PlaceholderContext(renderer))) :
                stack;
    }

    public RenderElement withCondition(Condition condition) {
        return new RenderElement(owner, condition, positions, stack, context, handler, flags);
    }

    public RenderElement withOwner(ShopElement owner, Condition condition, PlaceholderContext context) {
        return new RenderElement(owner, owner.condition, positions, stack, context, handler.andThen(owner::onClick), flags);
    }

    public RenderElement withOwner(ShopElement owner, PlaceholderContext context) {
        return new RenderElement(owner, owner.condition, positions, stack, context, handler.andThen(owner::onClick), flags);
    }

    public RenderElement withOwner(ShopElement owner) {
        return withOwner(owner, context);
    }

    /** returns a new RenderElement with all placeholders applied */
    public RenderElement withCalculatedContext(ShopRenderer renderer) {
        return new RenderElement(owner, condition, positions, actualStack(renderer), PlaceholderContext.NO_CONTEXT, handler.andThen(owner::onClick), flags);
    }

    public RenderElement withFlags(Set<ShopRenderer.RenderingFlag> flags) {
        return new RenderElement(owner, condition, positions, stack, context, handler, flags);
    }

    public ClickableItem clickableItem(ShopRenderer renderer) {
        return ClickableItem.of(actualStack(renderer), handler);
    }

}

