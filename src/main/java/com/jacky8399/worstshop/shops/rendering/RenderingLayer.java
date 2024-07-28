package com.jacky8399.worstshop.shops.rendering;

import fr.minuskube.inv.content.SlotPos;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface RenderingLayer {
    Map<SlotPos, RenderElement> render(@NotNull ShopRenderer context, int page);
}
