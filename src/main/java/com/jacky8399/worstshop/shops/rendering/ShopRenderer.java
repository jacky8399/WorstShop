package com.jacky8399.worstshop.shops.rendering;

import com.google.common.collect.ImmutableList;
import com.jacky8399.worstshop.helper.ItemUtils;
import com.jacky8399.worstshop.shops.Shop;
import com.jacky8399.worstshop.shops.ShopReference;
import com.jacky8399.worstshop.shops.elements.ShopElement;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotPos;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class ShopRenderer implements InventoryProvider, RenderingLayer {
    public final Shop shop;
    public final Player player;
    public ShopRenderer(Shop shop, Player player) {
        this.shop = shop;
        this.player = player;
    }

    public boolean debug = false;

    public HashMap<SlotPos, @Nullable RenderElement> outline = new HashMap<>();
    public List<RenderElement> toUpdateNextTick = new ArrayList<>();
    public List<RenderElement> paginationItems = new ArrayList<>();
    public List<RenderingLayer> backgrounds = new ArrayList<>();

    public void addShopBackground() {
        if (!shop.circularReferenceChecked)
            shop.checkCircularReference();
        if (shop.extendsFrom != ShopReference.empty()) {
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

    public void fillOutline(@NotNull ShopRenderer context) {
        initInventoryStructure();
        PlaceholderContext placeholder = new PlaceholderContext(context);
        for (ShopElement element : shop.elements) {
            List<RenderElement> items = element.getRenderElement(context, placeholder);
            for (RenderElement item : items) {
                if (!item.condition().test(context.player)) continue;
                Collection<SlotPos> slots1 = item.positions();
                if (slots1 != null) {
                    for (SlotPos slot : slots1) {
                        outline.put(slot, item);
                    }
                } else {
                    paginationItems.add(item);
                }
            }
        }
    }

    public int page = 0, lastPage = 0, maxPage = 1;

    // temporary
    @Nullable
    public static ShopRenderer RENDERING;

    @Override
    public Map<SlotPos, RenderElement> render(@NotNull ShopRenderer context, int page) {
        this.page = page;

        LinkedHashMap<SlotPos, RenderElement> elements = new LinkedHashMap<>();
        for (int row = 0; row < getRows(); row++) {
            for (int column = 0; column < getColumns(); column++) {
                elements.put(new SlotPos(row, column), null);
            }
        }
        // background layers
        addShopBackground();
        for (RenderingLayer layer : backgrounds) {
            Map<SlotPos, RenderElement> layerItems = layer.render(context, page);
            layerItems.forEach((pos, stack) -> {
                if (stack != null)
                    elements.put(pos, stack);
            });
        }

        BiFunction<SlotPos, RenderElement, Boolean> addToElements = (pos, element) -> {
            if (element == null)
                return false;
            elements.put(pos, element);
            return true;
        };

        fillOutline(context);
        outline.forEach(addToElements::apply);
        // pagination
        // find empty slots
        List<SlotPos> emptySlots = new ArrayList<>();
        for (Map.Entry<SlotPos, RenderElement> entry : elements.entrySet()) {
            if (entry.getValue() == null) {
                SlotPos key = entry.getKey();
                emptySlots.add(key);
            }
        }
        if (!emptySlots.isEmpty()) {
            ListIterator<SlotPos> emptySlotIterator = emptySlots.listIterator();
            this.maxPage = (int) Math.ceil((double) paginationItems.size() / emptySlots.size());

            int start = page * emptySlots.size(), end = Math.min(start + emptySlots.size(), paginationItems.size());
            if (start < end) {
                List<RenderElement> elementsDisplayed = paginationItems.subList(start, end);
                SlotPos slot = emptySlotIterator.next();
                for (RenderElement renderElement : elementsDisplayed) {
                    if (addToElements.apply(slot, renderElement) && emptySlotIterator.hasNext()) {
                        // only consume slot if condition test passes
                        slot = emptySlotIterator.next();
                    }
                }
            }
        }
        return elements;
    }

    public void apply(Player player, InventoryContents contents) {
        RENDERING = this;
        try {
            Map<SlotPos, RenderElement> result = render(this, page);
            result.forEach((var pos, @Nullable var info) -> {
                if (info == null || info.stack() == null) {
                    contents.set(pos, null); // to clear old pagination items
                    return;
                }
                contents.set(pos, info.clickableItem(this));
                // TODO do I really need to store the flags on each element??
                if (info.flags().contains(RenderingFlag.UPDATE_NEXT_TICK)) {
                    toUpdateNextTick.add(info);
                }
            });
        } catch (Exception e) {
            RuntimeException wrapped = new RuntimeException("Rendering shop " + shop.id, e);
            ClickableItem item = ItemUtils.getClickableErrorItem(wrapped);
            contents.fill(item);
        }
        RENDERING = null;
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
            if (++ticksElapsed >= shop.updateInterval) {
                ticksElapsed = 0;
                clearAll(contents);
                apply(player, contents);
                updated = true;
            }
            contents.setProperty("ticksSinceUpdate", ticksElapsed);
        }
        if (!updated && lastPage != page) {
            // update page number in placeholders
            lastPage = page;
            clearAll(contents);
            apply(player, contents);
            updated = true;
        }
        if (!updated) {
            Map<ShopElement, List<RenderElement>> ownerToItemMap = toUpdateNextTick.stream()
                    .collect(Collectors.groupingBy(RenderElement::owner, Collectors.toList()));

            PlaceholderContext placeholder = new PlaceholderContext(this);

            toUpdateNextTick.clear();

            boolean shouldRefreshPagination = false;
            for (Map.Entry<ShopElement, List<RenderElement>> entry : ownerToItemMap.entrySet()) {
                ShopElement owner = entry.getKey();
                List<RenderElement> items = entry.getValue();
                // remove old items
                for (RenderElement element : items) {
                    if (element.positions() != null) {
                        for (SlotPos pos : element.positions()) {
                            contents.set(pos, null);
                        }
                    } else {
                        shouldRefreshPagination = true;
                    }
                }
                if (!shouldRefreshPagination) {
                    List<RenderElement> newItems = owner.getRenderElement(this, placeholder);
                    for (RenderElement element : newItems) {
                        if (element.positions() != null) {
                            ClickableItem clickableItem = element.clickableItem(this);
                            for (SlotPos pos : element.positions()) {
                                contents.set(pos, clickableItem);
                            }
                            if (element.flags().contains(RenderingFlag.UPDATE_NEXT_TICK))
                                toUpdateNextTick.add(element);
                        } else {
                            shouldRefreshPagination = true;
                            break; // stop setting items since a full render is required anyway
                        }
                    }
                }
            }
            if (shouldRefreshPagination) {
                clearAll(contents);
                apply(player, contents);
            }
        }
    }

    public enum RenderingFlag {
        UPDATE_NEXT_TICK
    }

    private final HashMap<String, Object> properties = new HashMap<>();
    @SuppressWarnings("unchecked")
    public <T> T property(String key) {
        return (T) properties.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T property(String key, T def) {
        return (T) properties.getOrDefault(key, def);
    }

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    @Override
    public String toString() {
        return "ShopRenderer{shop=" + shop + ", player=" + player.getName() + "}";
    }
}
