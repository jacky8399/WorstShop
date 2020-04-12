package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ConfigHelper;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ActionBook extends Action {
    public final ArrayList<BaseComponent[]> pages;

    public ActionBook(Map<String, Object> yaml) {
        super(yaml);
        pages = Lists.newArrayList();
        for (String page : (List<String>) yaml.get("pages")) {
            pages.add(ConfigHelper.parseComponentString(page));
        }
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        // book item
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.spigot().setPages(pages);
        ActionClose.closeInv(player, true);
        Bukkit.getScheduler().runTaskLater(WorstShop.get(), () -> player.openBook(book), 1);
    }
}
