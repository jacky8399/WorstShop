package com.jacky8399.worstshop.helper;

import fr.minuskube.inv.content.Pagination;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

public class PaginationHelper {

    private static final Field ITEMS;
    private static final Field ITEMS_PER_PAGE;

    public static int getItems(Pagination pagination) {
        try {
            return Array.getLength(ITEMS.get(pagination));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public static int getItemsPerPage(Pagination pagination) {
        try {
            return ITEMS_PER_PAGE.getInt(pagination);
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    public static int getLastPage(Pagination pagination) {
        int items = getItems(pagination);
        int perPage = getItemsPerPage(pagination);
//        WorstShop.get().logger.info(items + "/" + perPage);
        return (int) Math.ceil((float) items / perPage);
    }

    static {
        try {
            ITEMS = Pagination.Impl.class.getDeclaredField("items");
            ITEMS.setAccessible(true);
            ITEMS_PER_PAGE = Pagination.Impl.class.getDeclaredField("itemsPerPage");
            ITEMS_PER_PAGE.setAccessible(true);
        } catch (Exception e) {
            throw new Error(e);
        }
    }
}
