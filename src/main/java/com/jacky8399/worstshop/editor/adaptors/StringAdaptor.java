package com.jacky8399.worstshop.editor.adaptors;

import com.jacky8399.worstshop.helper.ConfigHelper;
import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public class StringAdaptor extends TextAdaptor<String> {
    @Override
    public String unmarshal(String input) {
        return ConfigHelper.translateString(input);
    }

    @Override
    public String marshal(String val) {
        return ConfigHelper.untranslateString(val);
    }

    @Override
    public String getType() {
        return "string";
    }

    @Override
    public ItemStack getRepresentation(String val, @Nullable String parentName, @Nullable String fieldName) {
        return ItemBuilder.of(Material.NAME_TAG).name(EditorUtils.NAME_FORMAT.apply(fieldName))
                .lores(EditorUtils.VALUE_FORMAT.apply(val))
                .addLores(EditorUtils.getDesc(parentName, fieldName))
                .build();
    }
}
