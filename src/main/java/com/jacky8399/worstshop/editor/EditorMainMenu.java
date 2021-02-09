package com.jacky8399.worstshop.editor;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.helper.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public class EditorMainMenu implements InventoryProvider {
    private static final String I18N_KEY = I18n.Keys.MESSAGES_KEY + "editor.main.";

    public static SmartInventory getInventory() {
        return WorstShop.buildGui("worstshop:editor_main")
                .provider(new EditorMainMenu())
                .size(3, 9)
                .title(I18n.translate(I18N_KEY + "title"))
                .build();
    }

    @Override
    public void init(Player player, InventoryContents contents) {
        contents.fill(ClickableItem.empty(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(ChatColor.BLACK.toString()).build()));
        // check for permissions
        if (player.hasPermission("worstshop.editor.create")) {
            contents.set(1, 3, ClickableItem.of(
                    ItemBuilder.of(Material.WRITABLE_BOOK).name(I18N_KEY + "create").build(),
                    e -> {
                        // TODO
                    }
            ));
        }
        if (player.hasPermission("worstshop.editor.modify")) {
            contents.set(1, 5, ClickableItem.of(
                    ItemBuilder.of(Material.KNOWLEDGE_BOOK).name(I18N_KEY + "modify").build(),
                    e -> {
                        // TODO
                    }
            ));
        }
    }

    @Override
    public void update(Player player, InventoryContents contents) {

    }
}
