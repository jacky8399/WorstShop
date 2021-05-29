package com.jacky8399.worstshop.shops;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;

import java.util.ArrayList;
import java.util.function.BiFunction;

public class ElementPopulationContext {
    public enum Stage {

    }

    SlotIterator slotIterator;
    Pagination pagination;
    InventoryContents contents;
    boolean hasDonePagination = false;
    ArrayList<ClickableItem> paginationItems;

    ElementPopulationContext(InventoryContents contents) {
        this.contents = contents;
        slotIterator = contents.newIterator(SlotIterator.Type.HORIZONTAL, 0, 0).allowOverride(false);
        pagination = contents.pagination();
        paginationItems = new ArrayList<>();
    }

    public void add(ClickableItem item) {
        paginationItems.add(item);
    }

    public void forEachRemaining(BiFunction<Integer, Integer, ClickableItem> supplier) {
        doPaginationNow();
        while (!slotIterator.ended()) {
            slotIterator.next();
            ClickableItem next = supplier.apply(slotIterator.row(), slotIterator.column());
            slotIterator.set(next);
        }
    }

    public InventoryContents getPrimitive() {
        return contents;
    }

    void doPaginationNow() {
        if (hasDonePagination)
            return;
        hasDonePagination = true;
        pagination.setItems(paginationItems.toArray(new ClickableItem[0]));
        int emptySlots = 0;
        for (ClickableItem[] arr : contents.all()) {
            for (ClickableItem item : arr) {
                if (item == null)
                    emptySlots++;
            }
        }
//        WorstShop.get().logger.info("Empty slots: " + emptySlots + ", items: " + paginationItems.size());
        if (emptySlots != 0)
            pagination.setItemsPerPage(emptySlots);
        pagination.addToIterator(slotIterator);
    }
}
