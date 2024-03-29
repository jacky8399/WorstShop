package com.jacky8399.worstshop.shops;

import com.jacky8399.worstshop.editor.Adaptor;
import com.jacky8399.worstshop.editor.Editable;
import com.jacky8399.worstshop.editor.adaptors.GUIAdaptor;
import com.jacky8399.worstshop.helper.EditorUtils;
import com.jacky8399.worstshop.helper.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Editable
@Adaptor(ShopReference.Adaptor.class)
public class ShopReference {
    public static ShopReference empty() {
        return Empty.INSTANCE;
    }

    public static final HashMap<String, ShopReference> REFERENCES = new HashMap<>();
    // basically final
    public String id;
    private Shop ref;
    private ShopReference(String id) {
        this(id, ShopManager.SHOPS.get(id));
    }

    private ShopReference(String id, @Nullable Shop shop) {
        this.id = id;
        ref = shop;
    }

    @NotNull
    public static ShopReference of(String id) {
        return REFERENCES.computeIfAbsent(id, ShopReference::new);
    }

    @NotNull
    public static ShopReference of(@Nullable Shop shop) {
        return shop == null ? empty() : REFERENCES.computeIfAbsent(shop.id, ignored->new ShopReference(shop.id, shop));
    }

    public Optional<Shop> find() {
        if (ref == null)
            ref = ShopManager.SHOPS.get(id);
        return Optional.ofNullable(ref);
    }

    @NotNull
    public Shop get() throws IllegalStateException {
        if (ref == null)
            ref = ShopManager.SHOPS.get(id);
        // if still null
        if (ref == null)
            throw new IllegalStateException("Can't find a shop by the name " + id + "!");
        return ref;
    }

    public boolean refersTo(Shop shop) {
        if (ref == null)
            ref = ShopManager.SHOPS.get(id);
        return ref == shop;
    }

    public void invalidate() {
        ref = null;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ShopReference && ((ShopReference) obj).id.equals(id);
    }

    private static class Empty extends ShopReference {
        public static final Empty INSTANCE = new Empty();
        private Empty() {
            super(null);
        }

        @Override
        public Optional<Shop> find() {
            return Optional.empty();
        }

        @NotNull
        @Override
        public Shop get() {
            throw new IllegalStateException("Empty reference!");
        }

        @Override
        public boolean refersTo(Shop shop) {
            return shop == null;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Empty;
        }

        @Override
        public int hashCode() {
            return 0;
        }
    }

    static class Adaptor extends GUIAdaptor<ShopReference> {
        public Adaptor() {}

        @Override
        public Collection<? extends ShopReference> getValues() {
            return Stream.concat(ShopManager.SHOPS.keySet().stream().map(ShopReference::of), Stream.of(empty()))
                    .sorted(Comparator.comparing(ref -> ref instanceof Empty ? "?" : ref.id))
                    .collect(Collectors.toList());
        }

        @Override
        public ItemStack getRepresentation(ShopReference val, @Nullable String parentName, @Nullable String fieldName) {
            if (fieldName != null)
                return ItemBuilder.of(Material.EMERALD_BLOCK)
                        .name(EditorUtils.NAME_FORMAT.apply(fieldName))
                        .lores(EditorUtils.VALUE_FORMAT.apply(val instanceof Empty ? "?" : val.id))
                        .addLores(EditorUtils.getDesc(parentName, fieldName))
                        .build();
            return ItemBuilder.of(Material.EMERALD_BLOCK).name(ChatColor.GREEN + val.id).build();
        }
    }
}
