package com.jacky8399.worstshop.editor;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * An adaptor that represents any object annotated with {@link Editable}. <br>
 *     It is recommended that implementations of this interface be used instead of implementing it yourself.
 */
public interface EditableAdaptor<T> {
    CompletableFuture<T> onInteract(Player player, T val, @Nullable String fieldName);
    ItemStack getRepresentation(T val, @Nullable String parentName, @Nullable String fieldName);
}
