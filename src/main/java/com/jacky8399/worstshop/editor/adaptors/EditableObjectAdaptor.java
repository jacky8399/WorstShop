package com.jacky8399.worstshop.editor.adaptors;

import com.jacky8399.worstshop.WorstShop;
import com.jacky8399.worstshop.editor.Editable;
import com.jacky8399.worstshop.editor.EditableAdaptor;
import com.jacky8399.worstshop.editor.Property;
import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.InventoryCloseListener;
import com.jacky8399.worstshop.helper.ItemBuilder;
import com.jacky8399.worstshop.helper.ItemUtils;
import fr.minuskube.inv.ClickableItem;
import fr.minuskube.inv.SmartInventory;
import fr.minuskube.inv.content.InventoryContents;
import fr.minuskube.inv.content.InventoryProvider;
import fr.minuskube.inv.content.SlotIterator;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class EditableObjectAdaptor<T> implements EditableAdaptor<T> {
    private final Class<T> clazz;
    private final String name;
    private final List<Field> properties;

    public EditableObjectAdaptor(Class<T> clazz) {
        this.clazz = clazz;
        name = clazz.getAnnotation(Editable.class).value();
        properties = Arrays.stream(clazz.getFields())
                .filter(field -> field.isAnnotationPresent(Property.class))
                .sorted(Comparator.comparing(Field::getName))
                .collect(Collectors.toList());
    }

    @Override
    public CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName) {
        int rows = Math.min(6, properties.size() / 9 + 2);

        SmartInventory parent = WorstShop.get().inventories.getInventory(player).orElse(null);
        class Inventory implements InventoryProvider {
            boolean shouldRefresh = false;

            // helper
            @SuppressWarnings("unchecked")
            private <TValue> ClickableItem createItemForField(Field field) {
                String fieldName = field.getName();
                EditableAdaptor<TValue> adaptor = EditorUtils.findAdaptorForField(val, field);
                if (adaptor == null) // no adaptor; probably can't edit
                    return ClickableItem.empty(
                            ItemBuilder.of(Material.BARRIER).name(EditorUtils.NAME_FORMAT.apply(fieldName)).build()
                    );
                TValue value;
                try {
                    value = (TValue) field.get(val);
                } catch (IllegalAccessException e) {
                    RuntimeException wrapped = new RuntimeException("Failed to obtain value for field " + fieldName, e);
                    // error item
                    return ClickableItem.empty(ItemUtils.getErrorItem(wrapped));
                }
                return ClickableItem.of(
                        adaptor.getRepresentation(value, name, fieldName),
                        e -> adaptor.onInteract(player, value, fieldName)
                                .thenAccept(newValue -> {
                                    try {
                                        field.set(val, newValue);
                                    } catch (IllegalAccessException ex) {
                                        RuntimeException wrapped = new RuntimeException("Failed to update value for field " + fieldName, ex);
                                        e.setCurrentItem(ItemUtils.getErrorItem(wrapped));
                                    }
                                    shouldRefresh = true;
                                })
                );
            }

            public void refresh(InventoryContents contents) {
                ClickableItem[] items = properties.stream()
                        .map(this::createItemForField)
                        .toArray(ClickableItem[]::new);
                contents.pagination().setItems(items).setItemsPerPage(45).addToIterator(contents.newIterator(SlotIterator.Type.HORIZONTAL, 1, 0).allowOverride(true));
            }

            @Override
            public void init(Player player, InventoryContents contents) {
                // header
                contents.fillRow(0, ClickableItem.empty(ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE).name(ChatColor.BLACK.toString()).build()));
                // properties
                refresh(contents);
                // custom
                EditableObjectAdaptor.this.init(player, contents);
            }

            @Override
            public void update(Player player, InventoryContents contents) {
                EditableObjectAdaptor.this.update(player, contents, shouldRefresh);
                if (shouldRefresh) {
                    refresh(contents);
                    shouldRefresh = false;
                }
            }
        }

        SmartInventory toOpen = WorstShop.buildGui("worstshop:editor_editable_adaptor")
                .provider(new Inventory()).size(rows, 9)
                .parent(parent).title(getTitle()).build();
        InventoryCloseListener.openSafely(player, toOpen);
        // doesn't change the object
        return CompletableFuture.completedFuture(val);
    }

    public void init(Player player, InventoryContents contents) {
    }

    public void update(Player player, InventoryContents contents, boolean isRefreshingProperties) {
    }

    @Override
    public ItemStack getRepresentation(T val, @Nullable String parentName, @Nullable String fieldName) {
        return ItemBuilder.of(Material.WRITABLE_BOOK).name(EditorUtils.NAME_FORMAT.apply(fieldName))
                .lores(EditorUtils.translate("gui.edit"))
                .addLores(EditorUtils.getDesc(parentName, fieldName))
                .build();
    }

    protected String getTitle() {
        return EditorUtils.NAME_FORMAT.apply(clazz.getSimpleName());
    }
}
