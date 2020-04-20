package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.Pagination;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

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
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        Optional<InventoryContents> contents = WorstShop.get().inventories.getContents(player);
        contents.ifPresent(c-> {
            Pagination pagination = c.pagination();
            if ((pagination.isFirst() && pageOffset == -1) || (pagination.isLast() && pageOffset == 1))
                return;
            c.inventory().open(player, c.pagination().getPage() + pageOffset);
        });
    }
}
