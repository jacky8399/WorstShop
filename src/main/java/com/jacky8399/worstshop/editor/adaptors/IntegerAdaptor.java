package com.jacky8399.worstshop.editor.adaptors;

import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.IllegalFormatException;

public class IntegerAdaptor extends TextAdaptor<Integer> {
    @Override
    public Integer unmarshal(String input) {
        return Integer.valueOf(input);
    }

    @Override
    public String marshal(Integer val) {
        return val.toString();
    }

    @Override
    public boolean validateInput(String input) {
        try {
            //noinspection ResultOfMethodCallIgnored
            Integer.parseInt(input);
            return super.validateInput(input);
        } catch (IllegalFormatException e) {
            return false;
        }
    }

    @Override
    public String getType() {
        return "integer";
    }

    @Override
    public ItemStack getRepresentation(Integer val, @Nullable String parentName, @Nullable String fieldName) {
        return ItemBuilder.of(Material.PAPER).name(EditorUtils.NAME_FORMAT.apply(fieldName))
                .lores(EditorUtils.VALUE_FORMAT.apply(Integer.toString(val)))
                .addLores(EditorUtils.getDesc(parentName, fieldName))
                .build();
    }
}
