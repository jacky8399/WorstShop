package com.jacky8399.worstshop.shops;

import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import fr.minuskube.inv.content.SlotPos;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

public class ElementContext {
    public enum Stage {
        SKELETON,
        STATIC,
        DYNAMIC
    }

    SlotIterator slotIterator;
    Pagination pagination;
    InventoryContents contents;
    boolean hasDonePagination = false;
    ArrayList<ClickableItem> paginationItems;
    public final CompletableFuture<List<SlotPos>> paginationItemResults = new CompletableFuture<>();
    Stage stage;

    ElementContext(InventoryContents contents, Stage stage) {
        this.contents = contents;
        slotIterator = contents.newIterator(SlotIterator.Type.HORIZONTAL, 0, 0).allowOverride(false);
        pagination = contents.pagination();
        paginationItems = new ArrayList<>();
        this.stage = stage;
    }

    public int add(ClickableItem item) {
        paginationItems.add(item);
        return paginationItems.size() - 1;
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

    public Stage getStage() {
        return stage;
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

        List<SlotPos> pos = new ArrayList<>();
        for(ClickableItem item : pagination.getPageItems()) {
            slotIterator.next();
            pos.add(SlotPos.of(slotIterator.row(), slotIterator.column()));
            slotIterator.set(item);

            if(slotIterator.ended())
                break;
        }
        paginationItemResults.complete(pos);
    }
}
