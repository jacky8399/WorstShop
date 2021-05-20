package com.jacky8399.worstshop.editor.adaptors;

import com.jacky8399.worstshop.I18n;
import com.jacky8399.worstshop.editor.EditableAdaptor;
import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class BooleanAdaptor implements EditableAdaptor<Boolean> {
    @Override
    public CompletableFuture<Boolean> onInteract(Player player, Boolean val, @Nullable String fieldName) {
        return CompletableFuture.completedFuture(!val);
    }

    @Override
    public ItemStack getRepresentation(Boolean val, @Nullable String parentName, @Nullable String fieldName) {
        ItemBuilder builder = val ?
                ItemBuilder.of(Material.GREEN_CONCRETE)
                        .lores(EditorUtils.VALUE_FORMAT.apply(I18n.translate(EditorUtils.I18N_KEY + "boolean.true"))) :
                ItemBuilder.of(Material.RED_CONCRETE)
                        .lores(EditorUtils.VALUE_FORMAT.apply(I18n.translate(EditorUtils.I18N_KEY + "boolean.false")));
        return builder
                .name(EditorUtils.NAME_FORMAT.apply(fieldName))
                .addLores(I18n.translate(EditorUtils.I18N_KEY + "boolean.toggle"))
                .build();
    }
}
