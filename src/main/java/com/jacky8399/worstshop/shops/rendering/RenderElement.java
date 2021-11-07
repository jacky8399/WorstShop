package com.jacky8399.worstshop.shops.rendering;

import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.function.Consumer;

public final class RenderElement {
    private final ShopElement owner;
    private final Collection<SlotPos> positions;
    private final ItemStack stack;
    private final Consumer<InventoryClickEvent> handler;
    private final EnumSet<ShopRenderer.RenderingFlag> flags;

    public RenderElement(ShopElement owner, Collection<SlotPos> positions, ItemStack stack, Consumer<InventoryClickEvent> handler, EnumSet<ShopRenderer.RenderingFlag> flags) {
        this.owner = owner;
        this.positions = positions;
        this.stack = stack;
        this.handler = handler;
        this.flags = flags;
    }

    public ShopElement owner() {
        return owner;
    }

    public Collection<SlotPos> positions() {
        return positions;
    }

    public ItemStack stack() {
        return stack;
    }

    public Consumer<InventoryClickEvent> handler() {
        return handler;
    }

    public EnumSet<ShopRenderer.RenderingFlag> flags() {
        return flags;
    }

    public void update() {

    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (RenderElement) obj;
        return Objects.equals(this.owner, that.owner) &&
                Objects.equals(this.positions, that.positions) &&
                Objects.equals(this.stack, that.stack) &&
                Objects.equals(this.handler, that.handler) &&
                Objects.equals(this.flags, that.flags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, positions, stack, handler);
    }

    @Override
    public String toString() {
        return "RenderElement[" +
                "owner=" + owner + ", " +
                "positions=" + positions + ", " +
                "stack=" + stack + ", " +
                "handler=" + handler + ']';
    }

}

