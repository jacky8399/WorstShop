package com.jacky8399.worstshop.shops.rendering;

import fr.minuskube.inv.content.SlotPos;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public interface RenderingLayer {
    Map<SlotPos, RenderElement> render(@Nullable ShopRenderer context, int page);
}
