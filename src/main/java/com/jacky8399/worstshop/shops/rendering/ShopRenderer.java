package com.jacky8399.worstshop.shops.rendering;

import com.google.common.collect.ImmutableList;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import com.jacky8399.worstshop.shops.elements.StaticShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ShopRenderer implements InventoryProvider, RenderingLayer {
    public final Shop shop;
    public final Player player;
    public ShopRenderer(Shop shop, Player player) {
        this.shop = shop;
        this.player = player;
    }

    public HashMap<SlotPos, @Nullable RenderElement> outline = new HashMap<>();
    public List<RenderElement> toUpdateNextTick = new ArrayList<>();
//    public HashMap<RenderElement, List<SlotPos>> toUpdateNextTick = new HashMap<>();
    public List<RenderElement> paginationItems = new ArrayList<>();
    public List<RenderingLayer> backgrounds = new ArrayList<>();

    public void addShopBackground() {
        if (shop.extendsFrom != ShopReference.EMPTY) {
            ShopRenderer renderer = new ShopRenderer(shop.extendsFrom.get(), player);
            renderer.addShopBackground();
            backgrounds.add(renderer);
        }
    }

    public int getRows() {
        return shop.rows;
    }

    public int getColumns() {
        return 9;
    }

    private ImmutableList<SlotPos> slots;
    public ImmutableList<SlotPos> getSlots() {
        if (slots == null) {
            ImmutableList.Builder<SlotPos> builder = ImmutableList.builder();
            for (int i = 0; i < getRows(); i++) {
                for (int j = 0; j < getColumns(); j++) {
                    builder.add(new SlotPos(i, j));
                }
            }
            slots = builder.build();
        }
        return slots;
    }

    public void initInventoryStructure() {
        outline.clear();
        toUpdateNextTick.clear();
        paginationItems.clear();
    }

    public void fillOutline() {
        initInventoryStructure();
        Consumer<ShopElement> addToOutline = element -> {
            List<RenderElement> items = element.getRenderElement(this);
            for (RenderElement item : items) {
                Collection<SlotPos> slots = item.positions();
                if (slots != null) {
                    for (SlotPos slot : slots) {
                        outline.put(slot, item);
                    }
                } else {
                    paginationItems.add(item);
                }
            }
        };
        shop.staticElements.forEach(addToOutline);
        shop.dynamicElements.forEach(addToOutline);
    }

    public transient int page, maxPage;


    public Map<SlotPos, RenderElement> render(int page) {
        LinkedHashMap<SlotPos, RenderElement> elements = new LinkedHashMap<>();
        for (int row = 0; row < getRows(); row++) {
            for (int column = 0; column < getColumns(); column++) {
                elements.put(new SlotPos(row, column), null);
            }
        }
        // background layers
        addShopBackground();
        for (RenderingLayer layer : backgrounds) {
            Map<SlotPos, RenderElement> layerItems = layer.render(page);
            layerItems.forEach((pos, stack) -> {
                if (stack != null)
                    elements.put(pos, stack);
            });
        }

        BiConsumer<SlotPos, RenderElement> addToElements = (pos, element) -> {
            if (element == null || !element.owner().condition.test(player))
                return;
            elements.put(pos, element);
        };

        fillOutline();
        outline.forEach(addToElements);
        // pagination
        // find empty slots
        List<SlotPos> emptySlots = new ArrayList<>();
        for (Map.Entry<SlotPos, RenderElement> entry : elements.entrySet()) {
            if (entry.getValue() == null) {
                SlotPos key = entry.getKey();
                emptySlots.add(key);
            }
        }
        if (emptySlots.size() != 0) {
            this.page = page;
            this.maxPage = (int) Math.ceil(((double) paginationItems.size()) / emptySlots.size());

            int start = page * emptySlots.size(), end = Math.min(start + emptySlots.size(), paginationItems.size());
            if (start < end) {
                List<RenderElement> elementsDisplayed = paginationItems.subList(start, end);
                for (int i = 0; i < elementsDisplayed.size(); i++) {
                    SlotPos slot = emptySlots.get(i);
                    addToElements.accept(slot, elementsDisplayed.get(i));
                }
            }
        }

        return elements;
    }

    public void apply(Player player, InventoryContents contents) {
        Map<SlotPos, RenderElement> result = render(contents.pagination().getPage());
        result.forEach((var pos, @Nullable var info) -> {
            if (info == null || info.stack() == null) {
                contents.set(pos, null); // to clear old pagination items
                return;
            }
            ItemStack stack = info.stack();
            ItemStack stackWithPlaceholders = StaticShopElement.replacePlaceholders(player, stack, shop, this);
            ClickableItem item = ClickableItem.of(stackWithPlaceholders, info.handler());
            contents.set(pos, item);
            if (info.flags().contains(RenderingFlag.UPDATE_NEXT_TICK))
                toUpdateNextTick.add(info);
        });
    }

    public static void clearAll(InventoryContents contents) {
//        contents.fill(null);
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        apply(player, contents);
    }

    @Override
    public void update(Player player, InventoryContents contents) {
        boolean updated = false;
        // shop global refresh
        if (shop.updateInterval != 0) {
            int ticksElapsed = contents.property("ticksSinceUpdate", 0);
            if (++ticksElapsed == shop.updateInterval) {
                clearAll(contents);
                apply(player, contents);
                updated = true;
            }
            contents.setProperty("ticksSinceUpdate", ticksElapsed);
        }
        if (!updated && contents.pagination().getPage() != page) {
            clearAll(contents);
            apply(player, contents);
            updated = true;
        }
        if (!updated) {
            toUpdateNextTick.removeIf(renderElement -> {
                renderElement.update();

                ItemStack stackWithPlaceholders = StaticShopElement.replacePlaceholders(player, renderElement.stack(), shop, this);
                ClickableItem item = ClickableItem.of(stackWithPlaceholders, renderElement.handler());
                Collection<SlotPos> positions = renderElement.positions();
                for (SlotPos pos : positions)
                    contents.set(pos, item);
                return !renderElement.flags().contains(RenderingFlag.UPDATE_NEXT_TICK);
            });
        }
    }

    public enum RenderingFlag {
        UPDATE_NEXT_TICK
    }
}
