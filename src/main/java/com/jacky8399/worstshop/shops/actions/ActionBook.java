package com.jacky8399.worstshop.shops.actions;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.Config;
import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.shops.rendering.PlaceholderContext;
import com.jacky8399.worstshop.shops.rendering.Placeholders;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Displays a book.
 */
public class ActionBook extends Action {
    public final ArrayList<String> pages;

    public ActionBook(Config yaml) {
        super(yaml);
        pages = new ArrayList<>(yaml.getList("pages", String.class));
    }

    @Override
    public void onClick(InventoryClickEvent e) {
        Player player = (Player) e.getWhoClicked();
        PlaceholderContext context = PlaceholderContext.guessContext(player);
        // book item
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle(player.getName());
        meta.setAuthor(player.getName());
        meta.spigot().setPages(pages.stream()
                .map(page -> Placeholders.setPlaceholders(page, context))
                .map(ConfigHelper::parseComponentString)
                .collect(Collectors.toList())
        );
        book.setItemMeta(meta);
        ActionClose.closeInv(player, true);
        Bukkit.getScheduler().runTaskLater(WorstShop.get(), () -> player.openBook(book), 1);
    }

    @Override
    public Map<String, Object> toMap(Map<String, Object> map) {
        map.put("preset", "book");
        map.put("pages", pages.stream().map(ComponentSerializer::toString).collect(Collectors.toList()));
        return map;
    }
}
