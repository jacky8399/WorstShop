package com.jacky8399.worstshop.editor.adaptors;

import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.EnumSet;

public class EnumAdaptor<T extends Enum<T>> extends GUIAdaptor<T> {
    public EnumAdaptor(Class<T> clazz) {
        values = EnumSet.allOf(clazz);
    }

    private final EnumSet<T> values;

    @Override
    public Collection<? extends T> getValues() {
        return values;
    }

    @Override
    public ItemStack getRepresentation(T val, @Nullable String parentName, @Nullable String fieldName) {
        return ItemBuilder.of(Material.EMERALD_BLOCK)
                .name(EditorUtils.NAME_FORMAT.apply(fieldName))
                .lores(EditorUtils.VALUE_FORMAT.apply(val.name()))
                .build();
    }
}
