package com.jacky8399.worstshop.shops.actions;

import com.google.common.collect.Lists;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigHelper;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;

public class ActionBook extends Action {
    public final ArrayList<BaseComponent[]> pages;

    public ActionBook(Config yaml) {
        super(yaml);
        pages = Lists.newArrayList();
        for (String page : yaml.getList("pages", String.class)) {
            pages.add(ConfigHelper.parseComponentString(page));
        }
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        // book item
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(player.getName());
        meta.setAuthor(player.getName());
        meta.spigot().setPages(pages);
        book.setItemMeta(meta);
        ActionClose.closeInv(player, true);
        Bukkit.getScheduler().runTaskLater(WorstShop.get(), () -> player.openBook(book), 1);
    }
}
