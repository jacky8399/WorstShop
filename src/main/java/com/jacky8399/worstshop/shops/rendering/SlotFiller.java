package com.jacky8399.worstshop.shops.rendering;

import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.content.SlotPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface SlotFiller {
    @Nullable
    Collection<SlotPos> fill(ShopElement element, ShopRenderer renderer);
}
