package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.Pagination;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.Optional;

public class ActionPage extends Action {
    int pageOffset;
    public ActionPage(Config yaml) {
        super(yaml);
        if (yaml.get("preset", String.class).replace(' ', '_')
                .equalsIgnoreCase("previous_page"))
            pageOffset = -1;
        else
            pageOffset = 1;
        yaml.find("pages", Integer.class).ifPresent(pageCount -> pageOffset *= Math.abs(pageCount));
    }

    public ActionPage(int page) {
        super(null);
        this.pageOffset = page;
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        Optional<InventoryContents> contents = WorstShop.get().inventories.getContents(player);
        contents.ifPresent(c-> {
            Pagination pagination = c.pagination();
            int currentPage = pagination.getPage();
            if (pageOffset < 0 && currentPage + pageOffset < 0) {
                return;
            } else if (pageOffset > 0) {
                // HACK: make SmartInv figure out last page for us
                int lastPage = pagination.last().getPage();
                // set the correct page
                pagination.page(currentPage);
                if (currentPage + pageOffset >= lastPage - 1)
                    return;
            }
            c.inventory().open(player, c.pagination().getPage() + pageOffset);
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
