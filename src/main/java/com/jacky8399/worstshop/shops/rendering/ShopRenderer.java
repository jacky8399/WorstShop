package com.jacky8399.worstshop.shops.rendering;

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
import java.util.stream.Collectors;

public class ShopRenderer implements InventoryProvider, RenderingLayer {
    public final Shop shop;
    public final Player player;
    public ShopRenderer(Shop shop, Player player) {
        this.shop = shop;
        this.player = player;
    }

    public HashMap<SlotPos, @Nullable ShopElement> outline = new HashMap<>();
    public HashMap<ShopElement, List<SlotPos>> toUpdateNextTick = new HashMap<>();
    public List<ShopElement> paginationItems = new ArrayList<>();
    public List<RenderingLayer> backgrounds = new ArrayList<>();

    public void addShopBackground() {
        if (shop.extendsFrom != ShopReference.EMPTY) {
            ShopRenderer renderer = new ShopRenderer(shop.extendsFrom.get(), player);
            renderer.addShopBackground();
            backgrounds.add(renderer);
        }
    }

    public void add(ShopElement element) {
        paginationItems.add(element);
    }

    public int getRows() {
        return shop.rows;
    }

    public int getColumns() {
        return 9;
    }

    public void initInventoryStructure() {
        outline.clear();
        toUpdateNextTick.clear();
    }

    public void fillOutline() {
        initInventoryStructure();
        Consumer<ShopElement> addToOutline = element -> {
            SlotFiller filler = element.getFiller(this);
            Collection<SlotPos> slots = filler.fill(element, this);
            if (slots != null) {
                for (SlotPos slot : slots) {
                    outline.put(slot, element);
                }
            }
        };
        shop.staticElements.forEach(addToOutline);
        shop.dynamicElements.forEach(addToOutline);
    }

    public transient int page, maxPage;


    public Map<SlotPos, ElementInfo> render(int page) {
        LinkedHashMap<SlotPos, RenderingLayer.ElementInfo> elements = new LinkedHashMap<>();
        for (int row = 0; row < getRows(); row++) {
            for (int column = 0; column < getColumns(); column++) {
                elements.put(new SlotPos(row, column), null);
            }
        }
        // background layers
        addShopBackground();
        for (RenderingLayer layer : backgrounds) {
            Map<SlotPos, RenderingLayer.ElementInfo> layerItems = layer.render(page);
            layerItems.forEach((pos, stack) -> {
                if (stack != null)
                    elements.put(pos, stack);
            });
        }

        BiConsumer<SlotPos, ShopElement> addToElements = (pos, element) -> {
            if (element == null || !element.condition.test(player))
                return;
            ItemStack stack = element.createStack(player, this);
            if (stack == null)
                return;
            elements.put(pos, new RenderingLayer.ElementInfo(element, stack));
        };

        fillOutline();
        outline.forEach(addToElements);
        // pagination
        // find empty slots
        List<SlotPos> emptySlots = elements.entrySet()
                .stream()
                .filter(entry -> entry.getValue() == null)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
//        WorstShop.get().logger.info(elements.toString());
//        WorstShop.get().logger.info("Empty slots = " + emptySlots.size());
        if (emptySlots.size() != 0) {
            this.page = page;
            this.maxPage = (int) Math.ceil(((double) paginationItems.size()) / emptySlots.size());

            int start = page * emptySlots.size(), end = Math.min(start + emptySlots.size(), paginationItems.size());
            List<ShopElement> itemsDisplayed = paginationItems.subList(start, end);
            for (int i = 0; i < itemsDisplayed.size(); i++) {
                SlotPos slot = emptySlots.get(i);
                addToElements.accept(slot, itemsDisplayed.get(i));
            }
        }

        return elements;
    }

    public void apply(Player player, InventoryContents contents) {
        Map<SlotPos, RenderingLayer.ElementInfo> result = render(contents.pagination().getPage());
        result.forEach((var pos, @Nullable var info) -> {
            if (info == null || info.raw() == null)
                return;
            ItemStack stack = info.raw();
            ItemStack stackWithPlaceholders = StaticShopElement.replacePlaceholders(player, stack, shop, this);
            ClickableItem item = ClickableItem.of(stackWithPlaceholders, info.element()::onClick);
            contents.set(pos, item);
            if (info.element().getRenderingFlags(this, pos).contains(RenderingFlag.UPDATE_NEXT_TICK))
                toUpdateNextTick.computeIfAbsent(info.element(), ignored -> new ArrayList<>()).add(pos);
        });
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
                apply(player, contents);
                updated = true;
            }
            contents.setProperty("ticksSinceUpdate", ticksElapsed);
        }
        if (!updated && contents.pagination().getPage() != page) {
            apply(player, contents);
            updated = true;
        }
        if (!updated) {
            toUpdateNextTick.entrySet().removeIf(entry -> {
                ShopElement element = entry.getKey();
                List<SlotPos> posList = entry.getValue();
                posList.removeIf(pos -> {
                    ItemStack stack = element.createStack(player, this);
                    ItemStack stackWithPlaceholders = StaticShopElement.replacePlaceholders(player, stack, shop, this);
                    ClickableItem item = ClickableItem.of(stackWithPlaceholders, element::onClick);
                    contents.set(pos, item);

                    return !element.getRenderingFlags(this, pos).contains(RenderingFlag.UPDATE_NEXT_TICK);
                });
                return posList.size() == 0;
            });
        }
    }

    public enum RenderingFlag {
        UPDATE_NEXT_TICK
    }
}
