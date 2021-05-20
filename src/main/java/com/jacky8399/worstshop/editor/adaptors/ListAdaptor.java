package com.jacky8399.worstshop.editor.adaptors;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.EditableAdaptor;
import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.InventoryCloseListener;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.helper.ItemUtils;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.Pagination;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ListAdaptor<T extends Collection<?>> implements EditableAdaptor<T> {
    private Supplier<@NotNull ?> supplier;

    public ListAdaptor() {
    }

    public void setFactory(@Nullable Class<? extends Supplier<@NotNull ?>> clazz) {
        if (clazz == null) {
            supplier = null;
        } else {
            try {
                supplier = clazz.getConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                supplier = null;
            }
        }
    }

    @Override
    public CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName) {
        if (val == null)
            return CompletableFuture.completedFuture(null);

        int rows = val.size() / 9 + 2;
        boolean hasPages = rows > 6;

        SmartInventory parent = WorstShop.get().inventories.getInventory(player).orElse(null);
        class Inventory implements InventoryProvider {
            boolean shouldRefresh = false;
            boolean removalMode = false;

            ClickableItem createItem(Object obj, int idx) {
                Class<?> clazz = obj.getClass();
                EditableAdaptor adaptor = EditorUtils.findAdaptorForClass((Class) clazz, obj);
                if (adaptor == null) // no adaptor; probably can't edit
                    return ItemBuilder.of(Material.BARRIER).name(EditorUtils.NAME_FORMAT.apply(clazz.getSimpleName())).toEmptyClickable();
                String name = (idx != -1 ? idx + ": " : "") + obj;
                return ClickableItem.of(
                        adaptor.getRepresentation(obj, fieldName, name),
                        e -> adaptor.onInteract(player, obj, name)
                                .thenAccept(newValue -> {
                                    if (val.equals(newValue))
                                        return;
                                    try {
                                        if (idx != -1) {
                                            ((List) val).set(idx, newValue);
                                        } else {
                                            val.remove(obj);
                                            ((Collection) val).add(newValue);
                                        }
                                    } catch (Exception ex) {
                                        RuntimeException wrapped = new RuntimeException("Failed to update value for field " + fieldName, ex);
                                        e.setCurrentItem(ItemUtils.getErrorItem(wrapped));
                                    }
                                    shouldRefresh = true;
                                })
                );
            }

            void refresh(InventoryContents contents) {
                // clear all
                contents.fill(null);
                // header
                contents.fillRow(0, ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(ChatColor.BLACK.toString()).toEmptyClickable());
                Pagination pagination = contents.pagination();
                if (hasPages && !pagination.isFirst())
                    contents.set(0, 0, ItemBuilder.of(Material.ARROW).name(EditorUtils.translate("gui.prev-page"))
                            .toClickable(e -> {
                                if (e.isRightClick())
                                    pagination.first();
                                else
                                    pagination.page(pagination.getPage() - 1);
                                shouldRefresh = true;
                            })
                    );
                if (hasPages && !pagination.isLast())
                    contents.set(0, 8, ItemBuilder.of(Material.ARROW).name(EditorUtils.translate("gui.next-page"))
                            .toClickable(e -> {
                                if (e.isRightClick())
                                    pagination.last();
                                else
                                    pagination.page(pagination.getPage() + 1);
                                shouldRefresh = true;
                            })
                    );
                // append item
                boolean create = supplier != null;
                contents.set(0, 4, ItemBuilder.of(create ? Material.WRITABLE_BOOK : Material.BOOK)
                        .name(EditorUtils.translate(create ? "list.new" : "list.new-unsupported"))
                        .toClickable(e -> {
                            if (!create) return;
                            boolean success = false;
                            try {
                                success = ((Collection) val).add(supplier.get());
                            } catch (Exception ex) {
                                RuntimeException wrapped = new RuntimeException("Failed to add to collection", ex);
                                e.setCurrentItem(ItemUtils.getErrorItem(wrapped));
                            }
                            if (success) {
                                shouldRefresh = true;
                            }
                        })
                );
                // mark for removal
                contents.set(0, 5, ItemBuilder.of(removalMode ? Material.STRUCTURE_VOID : Material.BARRIER)
                        .name(EditorUtils.translate(removalMode ? "list.delete-end" : "list.delete"))
                        .toClickable(e -> {
                            removalMode = !removalMode;
                            shouldRefresh = true;
                        })
                );

                ClickableItem[] items;
                if (val instanceof List) {
                    items = new ClickableItem[val.size()];
                    for (ListIterator<?> iterator = ((List<?>) val).listIterator(); iterator.hasNext(); ) {
                        int idx = iterator.nextIndex();
                        Object next = iterator.next();
                        items[idx] = createItem(next, idx);
                    }
                } else {
                    items = ((Collection<?>) val).stream().map(item -> createItem(item, -1)).toArray(ClickableItem[]::new);
                }
                SlotIterator iterator = contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 0);
                pagination.setItems(items).setItemsPerPage(45).addToIterator(iterator);
            }

            @Override
            public void init(Player player, InventoryContents contents) {
                refresh(contents);
            }

            @Override
            public void update(Player player, InventoryContents contents) {
                if (shouldRefresh)
                    refresh(contents);
            }
        }

        SmartInventory toOpen = WorstShop.buildGui("worstshop:editor_list_adaptor")
                .provider(new Inventory()).size(6, 9)
                .parent(parent).title(fieldName).build();
        InventoryCloseListener.openSafely(player, toOpen);
        return CompletableFuture.completedFuture(val);
    }

    @Override
    public ItemStack getRepresentation(T val, @Nullable String parentName, @Nullable String fieldName) {
        return ItemBuilder.of(Material.CHEST).name(EditorUtils.NAME_FORMAT.apply(fieldName))
                .lores(EditorUtils.translate("list.size", val != null ? val.size() : "null"), EditorUtils.translate("gui.edit"))
                .addLores(EditorUtils.getDesc(parentName, fieldName))
                .build();
    }
}
