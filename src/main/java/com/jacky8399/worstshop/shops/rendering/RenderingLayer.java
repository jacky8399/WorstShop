package com.jacky8399.worstshop.shops.rendering;

import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface RenderingLayer {
    Map<SlotPos, RenderElement> render(@Nullable ShopRenderer context, int page);


    record ElementInfo(ShopElement element, ItemStack raw) {
        @Override
        public String toString() {
            return "[" + element + "," + raw.getType() + "]";
        }
    }
}
