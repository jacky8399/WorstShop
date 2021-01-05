package com.jacky8399.worstshop.shops;

import com.jacky8399.worstshop.editor.Adaptor;
import com.jacky8399.worstshop.editor.DefaultAdaptors;
import com.jacky8399.worstshop.editor.Editable;
import com.jacky8399.worstshop.helper.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.stream.Collectors;

@Editable
@Adaptor(ShopReference.Adaptor.class)
public class ShopReference {
    public static final Empty EMPTY = new Empty();

    public static final HashMap<String, ShopReference> REFERENCES = new HashMap<>();
    /**
     * Please don't modify this unless you are trying to rename the shop0
     */
    public String id;
    private Shop ref;
    private ShopReference(String id) {
        this(id, ShopManager.SHOPS.get(id));
    }

    private ShopReference(String id, @Nullable Shop shop) {
        this.id = id;
        ref = shop;
    }

    public static ShopReference of(String id) {
        return REFERENCES.computeIfAbsent(id, ShopReference::new);
    }

    public static ShopReference of(Shop shop) {
        return REFERENCES.computeIfAbsent(shop.id, ignored->new ShopReference(shop.id, shop));
    }

    public Optional<Shop> find() {
        if (ref == null)
            ref = ShopManager.SHOPS.get(id);
        return Optional.ofNullable(ref);
    }

    @NotNull
    public Shop get() {
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

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ShopReference && ((ShopReference) obj).id.equals(id);
    }

    private static class Empty extends ShopReference {
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
    }

    static class Adaptor extends DefaultAdaptors.GUIAdaptor<ShopReference> {
        public Adaptor() {}

        @Override
        public Collection<? extends ShopReference> getValues() {
            return ShopManager.SHOPS.keySet().stream()
                    .map(ShopReference::of)
                    .collect(Collectors.toList());
        }

        @Override
        public ItemStack getRepresentation(ShopReference val, @Nullable String fieldName) {
            if (fieldName != null)
                return ItemBuilder.of(Material.EMERALD_BLOCK)
                        .name(DefaultAdaptors.NAME_FORMAT.apply(fieldName))
                        .lores(DefaultAdaptors.VALUE_FORMAT.apply(val == EMPTY ? "?" : val.id)).build();
            return ItemBuilder.of(Material.EMERALD_BLOCK).name(ChatColor.GREEN + val.id).build();
        }
    }
}
