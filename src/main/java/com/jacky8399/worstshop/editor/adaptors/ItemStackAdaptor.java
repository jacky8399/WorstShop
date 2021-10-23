package com.jacky8399.worstshop.editor.adaptors;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.EditableAdaptor;
import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.InventoryUtils;
import com.jacky8399.worstshop.helper.ItemBuilder;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class ItemStackAdaptor implements EditableAdaptor<ItemStack> {
    @Override
    public CompletableFuture<ItemStack> onInteract(Player player, ItemStack val, @Nullable String fieldName) {
        CompletableFuture<ItemStack> future = new CompletableFuture<>();
        SmartInventory parent = WorstShop.get().inventories.getInventory(player).orElse(null);
        class Inventory implements InventoryProvider {
            ItemStack stack = val;
            void setDropItem(InventoryContents contents) {
                contents.set(1, 4, ClickableItem.of(stack, e -> {
                    ItemStack cursor = e.getCursor();
                    if (cursor != null) {
                        stack = cursor.clone();
                        setDropItem(contents);
                    } else {
                        Bukkit.getScheduler().runTask(WorstShop.get(),
                                () -> e.getWhoClicked().setItemOnCursor(stack.clone()));
                    }
                }));
            }

            @Override
            public void init(Player player, InventoryContents contents) {
                // header
                contents.fill(ClickableItem.empty(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(ChatColor.BLACK.toString()).build()));
                // droppable slot
                setDropItem(contents);
                // hint
                contents.set(2, 4, ItemBuilder.of(Material.LIME_CONCRETE)
                        .name(EditorUtils.translate("item-stack.confirm"))
                        .toClickable(e -> {
                            future.complete(stack);
                            Bukkit.getScheduler().runTask(WorstShop.get(),
                                    () -> e.getWhoClicked().closeInventory());
                        })
                );
            }

            @Override
            public void update(Player player, InventoryContents contents) {
            }
        }

        SmartInventory toOpen = WorstShop.buildGui("worstshop:editor_editable_adaptor")
                .provider(new Inventory()).size(3, 9)
                .parent(parent).title(EditorUtils.NAME_FORMAT.apply(fieldName)).build();
        InventoryUtils.openSafely(player, toOpen);
        // doesn't change the object
        return future;
    }

    @Override
    public ItemStack getRepresentation(ItemStack val, @Nullable String parentName, @Nullable String fieldName) {
        return ItemBuilder.of(Material.ITEM_FRAME).name(EditorUtils.NAME_FORMAT.apply(fieldName))
                .lores(EditorUtils.translate("gui.edit"))
                .addLores(EditorUtils.getDesc(parentName, fieldName))
                .build();
    }
}
