package com.jacky8399.worstshop.editor.adaptors;

import com.jacky8399.worstshop.editor.EditableAdaptor;
import com.jacky8399.worstshop.editor.Representation;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

public class CustomRepresentationAdaptor<T> implements EditableAdaptor<T> {
    public final EditableAdaptor<T> internal;
    public final Material material;

    public CustomRepresentationAdaptor(EditableAdaptor<T> original, Representation repr) {
        internal = original;
        material = repr.value();
    }

    @Override
    public CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName) {
        return internal.onInteract(player, val, fieldName);
    }

    @Override
    public ItemStack getRepresentation(T val, @Nullable String parentName, @Nullable String fieldName) {
        ItemStack stack = internal.getRepresentation(val, parentName, fieldName);
        ItemStack ret = new ItemStack(material);
        ret.setAmount(stack.getAmount());
        ret.setItemMeta(Bukkit.getItemFactory().asMetaFor(stack.getItemMeta(), material));
        return ret;
    }
}
