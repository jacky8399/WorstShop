package com.jacky8399.worstshop.shops.rendering;

import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public interface RenderingLayer {
    Map<SlotPos, RenderElement> render(int page);


    record ElementInfo(ShopElement element, ItemStack raw) {
        @Override
        public String toString() {
            return "[" + element + "," + raw.getType() + "]";
        }
    }
}
