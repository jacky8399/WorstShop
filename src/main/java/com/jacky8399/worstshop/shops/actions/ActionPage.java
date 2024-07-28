package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigException;
import com.jacky8399.worstshop.shops.rendering.ShopRenderer;
import fr.minuskube.inv.content.InventoryContents;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.Optional;

/**
 * Sets the current page.
 */
public class ActionPage extends Action {
    int pageOffset;
    public ActionPage(Config yaml) {
        super(yaml);
        switch (yaml.get("preset", String.class).replace(' ', '_')) {
            case "previous_page" -> pageOffset = -1;
            case "next_page" -> pageOffset = 1;
            case "first_page" -> pageOffset = Integer.MIN_VALUE;
            case "last_page" -> pageOffset = Integer.MAX_VALUE;
            default -> throw new ConfigException("Invalid page preset", yaml, "preset");
        }
        yaml.find("pages", Integer.class).ifPresent(pageCount -> {
            if (Math.abs(pageOffset) == 1) {
                pageOffset *= pageCount;
            }
        });
    }

    public ActionPage(int page) {
        super(null);
        this.pageOffset = page;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        Optional<InventoryContents> contents = WorstShop.get().inventories.getContents(player);
        contents.ifPresent(c -> {
            ShopRenderer renderer = (ShopRenderer) c.inventory().getProvider();
            int currentPage = renderer.page;
            if (pageOffset < 0 && currentPage + pageOffset < 0) {
                return;
            } else if (pageOffset > 0) {
                int lastPage = renderer.maxPage;
                if (currentPage + pageOffset > lastPage - 1)
                    return;
            }
            renderer.page = (currentPage + pageOffset);
//            ((Shop) c.inventory().getProvider()).refreshItems(player, c, true, false);
        });
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", pageOffset > 0 ? "next page" : "previous page");
        if (Math.abs(pageOffset) != 1)
            map.put("pages", pageOffset);
        return map;
    }
}
