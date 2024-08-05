package com.jacky8399.worstshop.editor.adaptors;

import com.jacky8399.worstshop.i18n.I18n;
import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.EditableAdaptor;
import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * Opens a interactive GUI allowing users to pick from a list of values when clicked.
 *
 * @param <T> the adaptor type
 */
public abstract class GUIAdaptor<T> implements EditableAdaptor<T> {
    class Inventory implements InventoryProvider {
        private T value;

        SmartInventory getInventory(@Nullable SmartInventory parent, T val, @Nullable String fieldName) {
            value = val;
            // estimate container size
            int rows = (int) Math.min(6, Math.ceil(getValues().size() / 9f) + 1);
            return WorstShop.buildGui("worstshop:editor_gui_adaptor")
                    .title(I18n.translate(EditorUtils.I18N_KEY + "gui.title", fieldName != null ? fieldName : "???"))
                    .size(rows, 9).provider(this).parent(parent).build();
        }

        @Override
        public void init(Player player, InventoryContents contents) {
            // header
            contents.fillRow(0, ClickableItem.empty(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(ChatColor.BLACK.toString()).build()));

            // items
            ClickableItem[] items = getValues().stream()
                    .map(val -> {
                        ItemBuilder repr = ItemBuilder.from(getRepresentation(val, null, null));
                        if (val == value) {
                            repr.meta(meta -> {
                                meta.addEnchant(Enchantment.BINDING_CURSE, 1, true);
                                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                            }).addLores(I18n.translate(EditorUtils.I18N_KEY + "gui.current"));
                        } else {
                            repr.addLores(I18n.translate(EditorUtils.I18N_KEY + "gui.set"));
                        }
                        return ClickableItem.of(repr.build(), e -> {
                            future.complete(val);
                            Bukkit.getScheduler().runTask(WorstShop.get(), (Runnable) e.getWhoClicked()::closeInventory);
                        });
                    })
                    .toArray(ClickableItem[]::new);

            contents.pagination().setItems(items).setItemsPerPage(45)
                    .addToIterator(contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 0));
        }

        @Override
        public void update(Player player, InventoryContents contents) {
        }
    }

    private CompletableFuture<T> future;

    @Override
    public CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName) {
        future = new CompletableFuture<>();

        SmartInventory parent = WorstShop.get().inventories.getInventory(player).orElse(null);
        new Inventory().getInventory(parent, val, fieldName).open(player);
        return future;
    }

    public abstract Collection<? extends T> getValues();
}
